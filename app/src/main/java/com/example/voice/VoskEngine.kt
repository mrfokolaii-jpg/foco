package com.example.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * تشخیص گفتار آفلاین فارسی با Vosk:
 *  - کلمه بیداری «فوکو» (گوش دادن سبک با گرامر محدود)
 *  - تشخیص کامل یک جمله بدون اینترنت و بدون سرویس گوگل
 * مدل کوچک فارسی (حدود ۵۰ مگابایت) یک بار دانلود و در حافظه داخلی اپ ذخیره می‌شود.
 */
object VoskEngine {

    private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-fa-0.42.zip"
    private const val MODEL_DIR = "vosk-model-fa"
    private const val SAMPLE_RATE = 16000f

    /** صورت‌های مختلفی که تشخیص‌دهنده ممکن است برای «فوکو» بنویسد. */
    val WAKE_WORDS = listOf("فوکو", "فوکوس", "فکو", "فوکا", "فوکی", "پوکو", "فو کو")

    private var model: Model? = null
    private var service: SpeechService? = null
    private val main = Handler(Looper.getMainLooper())

    fun modelDir(context: Context): File = File(context.filesDir, MODEL_DIR)

    fun isModelReady(context: Context): Boolean {
        val dir = modelDir(context)
        return dir.isDirectory && (File(dir, "am").exists() || File(dir, "conf").exists())
    }

    /** دانلود و باز کردن مدل. onProgress درصد ۰ تا ۱۰۰ را می‌دهد. */
    suspend fun downloadModel(context: Context, onProgress: (Int) -> Unit = {}): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build()
            val response = client.newCall(Request.Builder().url(MODEL_URL).build()).execute()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("دانلود مدل ناموفق بود (${response.code})"))
            val body = response.body ?: return@withContext Result.failure(Exception("فایل مدل خالی بود"))
            val total = body.contentLength().coerceAtLeast(1L)
            val tmp = File(context.cacheDir, "vosk-fa.zip")
            body.byteStream().use { input ->
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    var lastPct = -1
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        done += read
                        val pct = ((done * 100) / total).toInt().coerceIn(0, 100)
                        if (pct != lastPct) { lastPct = pct; main.post { onProgress(pct) } }
                    }
                }
            }
            val target = modelDir(context)
            target.deleteRecursively()
            target.mkdirs()
            ZipInputStream(tmp.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    // پوشه اول داخل zip (مثل vosk-model-small-fa-0.42/) را حذف کن
                    val rel = entry.name.substringAfter('/', "")
                    if (rel.isNotBlank()) {
                        val outFile = File(target, rel)
                        if (!outFile.canonicalPath.startsWith(target.canonicalPath)) throw SecurityException("مسیر نامعتبر در فایل مدل")
                        if (entry.isDirectory) outFile.mkdirs() else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { zip.copyTo(it) }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            tmp.delete()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun ensureModel(context: Context): Model? {
        model?.let { return it }
        if (!isModelReady(context)) return null
        return try {
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            Model(modelDir(context).absolutePath).also { model = it }
        } catch (_: Exception) { null }
    }

    fun isListening(): Boolean = service != null

    fun stop() {
        try { service?.stop() } catch (_: Exception) {}
        try { service?.shutdown() } catch (_: Exception) {}
        service = null
    }

    /**
     * گوش دادن دائمی سبک برای کلمه بیداری. با شنیدن «فوکو» onWake صدا زده می‌شود
     * و گوش دادن متوقف می‌شود (بعد از مکالمه دوباره startWakeWord را صدا بزن).
     */
    fun startWakeWord(context: Context, onWake: () -> Unit, onError: (String) -> Unit = {}): Boolean {
        val m = ensureModel(context) ?: run { onError("مدل آفلاین دانلود نشده است"); return false }
        stop()
        return try {
            // بدون گرامر: ممکن است «فوکو» در واژگان مدل نباشد، پس متن کامل را بررسی می‌کنیم
            val rec = Recognizer(m, SAMPLE_RATE)
            val s = SpeechService(rec, SAMPLE_RATE)
            service = s
            s.startListening(object : RecognitionListener {
                private fun check(hyp: String?) {
                    val text = parseText(hyp)
                    if (WAKE_WORDS.any { text.contains(it) }) {
                        stop()
                        onWake()
                    }
                }
                override fun onPartialResult(hypothesis: String?) {
                    val partial = try { JSONObject(hypothesis ?: "{}").optString("partial") } catch (_: Exception) { "" }
                    if (WAKE_WORDS.any { partial.contains(it) }) { stop(); onWake() }
                }
                override fun onResult(hypothesis: String?) = check(hypothesis)
                override fun onFinalResult(hypothesis: String?) = check(hypothesis)
                override fun onError(exception: Exception?) { stop(); onError(exception?.localizedMessage ?: "خطای میکروفون") }
                override fun onTimeout() {}
            })
            true
        } catch (e: Exception) {
            stop(); onError(e.localizedMessage ?: "خطا"); false
        }
    }

    /**
     * تشخیص یک جمله کامل به صورت آفلاین. وقتی کاربر ساکت شد onResult با متن (یا خالی) صدا زده می‌شود.
     */
    fun recognizeOnce(
        context: Context,
        onPartial: (String) -> Unit = {},
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ): Boolean {
        val m = ensureModel(context) ?: run { onError("مدل آفلاین دانلود نشده است"); return false }
        stop()
        return try {
            val rec = Recognizer(m, SAMPLE_RATE)
            val s = SpeechService(rec, SAMPLE_RATE)
            service = s
            var delivered = false
            fun deliver(text: String) {
                if (delivered) return
                delivered = true
                stop()
                onResult(text)
            }
            s.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    val partial = try { JSONObject(hypothesis ?: "{}").optString("partial") } catch (_: Exception) { "" }
                    if (partial.isNotBlank()) onPartial(partial)
                }
                // Vosk بعد از سکوت یک نتیجه کامل می‌دهد؛ اولین جمله غیرخالی را برگردان
                override fun onResult(hypothesis: String?) {
                    val text = parseText(hypothesis)
                    if (text.isNotBlank()) deliver(text)
                }
                override fun onFinalResult(hypothesis: String?) = deliver(parseText(hypothesis))
                override fun onError(exception: Exception?) {
                    if (!delivered) { delivered = true; stop(); onError(exception?.localizedMessage ?: "خطای میکروفون") }
                }
                override fun onTimeout() = deliver("")
            }, 12000)
            true
        } catch (e: Exception) {
            stop(); onError(e.localizedMessage ?: "خطا"); false
        }
    }

    private fun parseText(hyp: String?): String =
        try { JSONObject(hyp ?: "{}").optString("text").trim() } catch (_: Exception) { "" }
}
