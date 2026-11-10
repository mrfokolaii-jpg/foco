package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Base64
import com.example.data.local.ApiKeyStore
import com.example.data.local.VoicePrefs
import com.example.data.remote.ProviderHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * صدای طبیعی فارسی با مدل‌های هوش مصنوعی:
 *  - اگر کلید جمنای داری: مدل TTS جمنای (gemini-2.5-flash-preview-tts)
 *  - وگرنه اگر کلید OpenAI داری: gpt-4o-mini-tts
 * خروجی در یک فایل موقت ذخیره و با MediaPlayer پخش می‌شود.
 * اگر هیچ‌کدام نبود یا خطا داد، null برمی‌گرداند تا صدای معمولی گوشی استفاده شود.
 */
object NaturalTts {

    private var player: MediaPlayer? = null

    fun isAvailable(keyStore: ApiKeyStore): Boolean = geminiKey(keyStore) != null || openAiKey(keyStore) != null

    private fun geminiKey(ks: ApiKeyStore): String? = when {
        ks.getBaseUrl().contains("generativelanguage", true) && ks.getHuggingFaceKey().isNotBlank() -> ks.getHuggingFaceKey()
        ks.getBaseUrl2().contains("generativelanguage", true) && ks.getApiKey2().isNotBlank() -> ks.getApiKey2()
        else -> null
    }

    private fun openAiKey(ks: ApiKeyStore): Pair<String, String>? = when {
        ks.getBaseUrl2().contains("api.openai.com", true) && ks.getApiKey2().isNotBlank() -> ks.getBaseUrl2().trimEnd('/') to ks.getApiKey2()
        ks.getBaseUrl().contains("api.openai.com", true) && ks.getHuggingFaceKey().isNotBlank() -> ks.getBaseUrl().trimEnd('/') to ks.getHuggingFaceKey()
        else -> null
    }

    /** ساخت فایل صوتی برای متن. */
    suspend fun synthesize(context: Context, text: String): File? = withContext(Dispatchers.IO) {
        val ks = ApiKeyStore(context)
        val voice = VoicePrefs(context).naturalVoiceName
        try {
            geminiKey(ks)?.let { key -> return@withContext geminiTts(context, key, text, voice) }
            openAiKey(ks)?.let { (base, key) -> return@withContext openAiTts(context, base, key, text) }
            null
        } catch (_: Exception) { null }
    }

    private fun geminiTts(context: Context, key: String, text: String, voice: String): File? {
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text)))))
            put("generationConfig", JSONObject().apply {
                put("responseModalities", JSONArray().put("AUDIO"))
                put("speechConfig", JSONObject().put("voiceConfig",
                    JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voice))))
            })
        }
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-preview-tts:generateContent")
            .addHeader("x-goog-api-key", key)
            .post(body.toString().toRequestBody(ProviderHttp.JSON))
            .build()
        ProviderHttp.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val json = JSONObject(resp.body?.string().orEmpty())
            val parts = json.optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts") ?: return null
            for (i in 0 until parts.length()) {
                val inline = parts.optJSONObject(i)?.optJSONObject("inlineData") ?: continue
                val pcm = Base64.decode(inline.optString("data"), Base64.DEFAULT)
                val rate = Regex("rate=(\\d+)").find(inline.optString("mimeType"))?.groupValues?.get(1)?.toIntOrNull() ?: 24000
                val file = File(context.cacheDir, "foko_tts_${System.nanoTime()}.wav")
                writeWav(file, pcm, rate)
                return file
            }
        }
        return null
    }

    private fun openAiTts(context: Context, base: String, key: String, text: String): File? {
        val body = JSONObject().apply {
            put("model", "gpt-4o-mini-tts")
            put("voice", "alloy")
            put("input", text)
            put("response_format", "mp3")
        }
        val req = Request.Builder()
            .url("$base/audio/speech")
            .addHeader("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody(ProviderHttp.JSON))
            .build()
        ProviderHttp.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val bytes = resp.body?.bytes() ?: return null
            val file = File(context.cacheDir, "foko_tts_${System.nanoTime()}.mp3")
            file.writeBytes(bytes)
            return file
        }
    }

    private fun writeWav(file: File, pcm: ByteArray, sampleRate: Int) {
        val channels = 1
        val bits = 16
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(channels.toShort())
            putInt(sampleRate); putInt(sampleRate * channels * bits / 8)
            putShort((channels * bits / 8).toShort()); putShort(bits.toShort())
            put("data".toByteArray()); putInt(pcm.size)
        }
        FileOutputStream(file).use { it.write(header.array()); it.write(pcm) }
    }

    /** پخش فایل. onDone بعد از پایان یا خطا صدا زده می‌شود. */
    fun play(file: File, onDone: () -> Unit) {
        stop()
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener { stop(); file.delete(); onDone() }
            mp.setOnErrorListener { _, _, _ -> stop(); file.delete(); onDone(); true }
            mp.prepare()
            mp.start()
            player = mp
        } catch (_: Exception) {
            stop(); onDone()
        }
    }

    fun isPlaying(): Boolean = try { player?.isPlaying == true } catch (_: Exception) { false }

    fun stop() {
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
    }
}
