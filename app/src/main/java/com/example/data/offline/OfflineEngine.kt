package com.example.data.offline

import android.os.Build
import com.example.data.model.ChatMessage
import com.example.data.model.MessageSender
import com.example.data.remote.ApiResult
import com.example.data.remote.ChatApiResponse
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaModel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * اجرای مدل هوش مصنوعی روی خودِ گوشی — بدون اینترنت، بدون کلید، بدون حساب.
 *
 * موتور، llama.cpp است (کتابخانهٔ `dev.ffmpegkit-maintained:llama-android`) که فایل‌های
 * GGUF را اجرا می‌کند؛ همین فرمتِ مدل‌های DeepSeek، Qwen، Llama و Gemma است.
 *
 * نکته‌های مهمی که باید صادقانه به کاربر گفته شود:
 *   • فقط پردازندهٔ گوشی کار می‌کند (بدون GPU) → پاسخ کندتر از سرویس‌های ابری است.
 *   • کتابخانه فقط برای arm64 ساخته شده؛ روی گوشی‌های قدیمی‌تر اجرا نمی‌شود.
 *   • یک مدل بزرگ‌تر از حافظهٔ آزادِ گوشی بارگذاری نمی‌شود؛ مدل کوچک‌تر Q4 لازم است.
 */
object OfflineEngine {

    /** یک مدل در هر لحظه؛ خود llama.cpp می‌گوید یک نمونه thread-safe نیست. */
    private val lock = Mutex()

    @Volatile
    private var loaded: LlamaModel? = null

    @Volatile
    private var loadedPath: String = ""

    /** آخرین خطای واقعی موتور، برای نمایش به کاربر. */
    @Volatile
    var lastError: String = ""
        private set

    @Volatile
    var isLoading: Boolean = false
        private set

    /**
     * کتابخانه فقط `arm64-v8a` دارد. روی گوشی ۳۲بیتی یا شبیه‌ساز x86 اجرا نمی‌شود و
     * باید پیش از هر تلاشی این را بررسی کنیم تا اپ کرش نکند.
     */
    fun isDeviceSupported(): Boolean =
        Build.SUPPORTED_ABIS.any { it.equals("arm64-v8a", ignoreCase = true) }

    fun isLoaded(): Boolean = loaded?.isLoaded == true

    fun loadedModelPath(): String = loadedPath

    /** اطلاعات پردازنده از زبان خود llama.cpp — برای تشخیص مشکلات اجرا. */
    fun systemInfo(): String = runCatching { Llama.getSystemInfo() }.getOrDefault("")

    private fun defaultConfig(): LlamaConfig = LlamaConfig(
        contextSize = 2048,
        // تعداد هسته‌ها را کم نگه می‌داریم تا گوشی هنگام پاسخ‌دهی یخ نکند.
        threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6),
        gpuLayers = 0,
        temperature = 0.7f,
        topP = 0.9f,
        topK = 40,
        seed = -1
    )

    /** بارگذاری مدل (اگر همان مدل از قبل بارگذاری شده باشد، کاری نمی‌کند). */
    suspend fun loadModel(path: String): ApiResult<String> = lock.withLock { ensureLoaded(path) }

    /** آزاد کردن حافظهٔ مدل. */
    fun release() {
        runCatching { loaded?.let { Llama.releaseModel(it) } }
        loaded = null
        loadedPath = ""
    }

    private fun ensureLoaded(path: String): ApiResult<String> {
        if (!isDeviceSupported()) {
            return ApiResult.Error(
                "اجرای مدل آفلاین روی این گوشی پشتیبانی نمی‌شود (فقط گوشی‌های arm64)."
            )
        }
        if (path.isBlank()) return ApiResult.Error("هیچ مدل آفلاینی انتخاب نشده است.")
        val file = File(path)
        if (!file.exists()) return ApiResult.Error("فایل مدل پیدا نشد: $path")

        val current = loaded
        if (current != null && current.isLoaded && loadedPath == path) {
            return ApiResult.Success("مدل از قبل بارگذاری شده است")
        }

        isLoading = true
        // مدل قبلی آزاد می‌شود؛ ماندن دو مدل روی هم یعنی تمام شدن حافظه.
        runCatching { loaded?.let { Llama.releaseModel(it) } }
        loaded = null
        loadedPath = ""

        return try {
            val model = Llama.loadModel(modelPath = path, config = defaultConfig())
            loaded = model
            loadedPath = path
            lastError = ""
            ApiResult.Success("مدل با موفقیت روی گوشی بارگذاری شد")
        } catch (error: Throwable) {
            lastError = error.message ?: error.toString()
            ApiResult.Error(friendlyLoadError(error, file))
        } finally {
            isLoading = false
        }
    }

    /**
     * پیام خطای قابل‌فهم. خطای کتابخانه انگلیسی و مبهم است؛ کاربر باید بداند دقیقاً
     * چه کار کند.
     */
    private fun friendlyLoadError(error: Throwable, file: File): String {
        val raw = (error.message ?: error.toString()).take(300)
        val sizeGb = file.length() / (1024.0 * 1024.0 * 1024.0)
        val hint = when {
            !file.name.lowercase().endsWith(".gguf") ->
                "پسوند فایل «.gguf» نیست؛ مدل باید یک فایل GGUF باشد."
            sizeGb > 3.0 ->
                "این فایل ${"%.1f".format(sizeGb)} گیگابایت است و برای حافظهٔ گوشی تو بزرگ است. یک نسخهٔ کوچک‌تر (Q4_K_M و ۱ تا ۳ میلیارد پارامتر) بگیر."
            else ->
                "حافظهٔ آزاد گوشی را خالی کن یا نسخهٔ کوچک‌تر (Q4) مدل را بگذار."
        }
        return "بارگذاری مدل انجام نشد.\n$hint\nخطای موتور: $raw"
    }

    /**
     * پاسخ به یک پیام، کاملاً روی گوشی.
     *
     * [conversationHistory] کل گفتگو تا همین لحظه است؛ آخرین پیام کاربر، پیام تازه است
     * و بقیه به‌عنوان زمینه به مدل داده می‌شوند.
     */
    suspend fun chat(
        modelPath: String,
        conversationHistory: List<ChatMessage>,
        systemInstructionText: String
    ): ApiResult<ChatApiResponse> = lock.withLock {
        val ready = ensureLoaded(modelPath)
        if (ready is ApiResult.Error) return@withLock ApiResult.Error(ready.message)

        val model = loaded ?: return@withLock ApiResult.Error("مدل آفلاین بارگذاری نشده است.")
        val prompt = buildPrompt(conversationHistory)
        if (prompt.isBlank()) return@withLock ApiResult.Error("پیامی برای فرستادن به مدل آفلاین وجود ندارد.")

        try {
            val result = Llama.complete(
                model,
                prompt = prompt,
                systemPrompt = systemInstructionText,
                maxTokens = MAX_TOKENS
            )
            val text = result.text.trim()
            if (text.isBlank()) {
                ApiResult.Error("مدل آفلاین جواب خالی داد. دوباره بفرست یا مدل کوچک‌تری بگذار.")
            } else {
                val speed = "%.1f".format(result.tokensPerSecond)
                ApiResult.Success(
                    ChatApiResponse(
                        replyText = text,
                        thinkingText = null,
                        searchSources = emptyList(),
                        // سرعت واقعی هم نشان داده می‌شود تا کاربر بداند موتور کار می‌کند.
                        modelUsed = "آفلاین · $speed توکن/ثانیه"
                    )
                )
            }
        } catch (error: Throwable) {
            lastError = error.message ?: error.toString()
            ApiResult.Error("خطا در اجرای مدل آفلاین: ${(error.message ?: "خطای نامشخص").take(250)}")
        }
    }

    /** چند پیام آخر به یک متن تبدیل می‌شود؛ کتابخانه خودش قالب چت مدل را اعمال می‌کند. */
    private fun buildPrompt(history: List<ChatMessage>): String {
        val turns = history.filter { it.text.isNotBlank() }.takeLast(6)
        if (turns.isEmpty()) return ""
        if (turns.size == 1) return turns.last().text
        return buildString {
            append("گفتگوی قبلی:\n")
            turns.dropLast(1).forEach { message ->
                append(if (message.sender == MessageSender.USER) "کاربر" else "دستیار")
                append(": ")
                append(message.text.take(400))
                append("\n")
            }
            append("\nپیام جدید کاربر: ")
            append(turns.last().text)
        }
    }

    /**
     * روی پردازندهٔ گوشی هر توکن چند صد میلی‌ثانیه طول می‌کشد؛ سقف را پایین گذاشتیم تا
     * پاسخ در زمان معقول بیاید (جواب کوتاه بهتر از انتظار طولانی است).
     */
    private const val MAX_TOKENS = 320
}
