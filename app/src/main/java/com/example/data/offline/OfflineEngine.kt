package com.example.data.offline

import android.content.Context
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
 * سه چیز این‌جا مدیریت می‌شود:
 *   ۱) تنظیمات سرعت/کیفیت که خودِ کاربر انتخاب می‌کند ([OfflineSettings]).
 *   ۲) تمیز کردن پاسخ: مدل‌های استدلالی مثل DeepSeek-R1 اول یک بلوک طولانی
 *      «فکر کردن» می‌نویسند؛ آن بلوک جدا می‌شود تا کاربر جواب واقعی را ببیند.
 *   ۳) دادن دانش از پایگاه دانش محلی ([KnowledgeBase]) پیش از پرسیدن از مدل.
 */
object OfflineEngine {

    private const val THINK_OPEN = "<" + "think" + ">"
    private const val THINK_CLOSE = "<" + "/" + "think" + ">"

    /** یک مدل در هر لحظه؛ llama.cpp می‌گوید یک نمونه thread-safe نیست. */
    private val lock = Mutex()

    /** برای خواندن تنظیمات و پایگاه دانش. با [attach] پر می‌شود. */
    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var loaded: LlamaModel? = null

    @Volatile
    private var loadedPath: String = ""

    /** امضای تنظیماتی که مدل با آن بارگذاری شده؛ اگر عوض شود، مدل دوباره بارگذاری می‌شود. */
    @Volatile
    private var loadedSignature: String = ""

    @Volatile
    var lastError: String = ""
        private set

    @Volatile
    var isLoading: Boolean = false
        private set

    /** یک‌بار از برنامه صدا زده می‌شود تا تنظیمات و پایگاه دانش در دسترس باشند. */
    fun attach(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    private fun tuning(): OfflineTuning =
        appContext?.let { OfflineSettings.load(it) } ?: OfflineTuning()

    fun currentTuning(): OfflineTuning = tuning()

    /**
     * کتابخانه فقط `arm64-v8a` دارد. روی گوشی ۳۲بیتی یا شبیه‌ساز x86 اجرا نمی‌شود و
     * باید پیش از هر تلاشی این را بررسی کنیم تا اپ کرش نکند.
     */
    fun isDeviceSupported(): Boolean =
        Build.SUPPORTED_ABIS.any { it.equals("arm64-v8a", ignoreCase = true) }

    /** حداکثر لایه‌های مدل که روی GPU فرستاده می‌شود؛ ۹۹ یعنی «همهٔ لایه‌ها». */
    const val GPU_ALL_LAYERS = 99

    /**
     * آیا موتور GPU دارد؟
     *
     * از نسخهٔ ۲.۵ کتابخانهٔ بومی با پشتیبانی Vulkan (libggml-vulkan.so) داخل اپ
     * ساخته می‌شود، پس GPU در دسترس است. اگر گوشی Vulkan نداشته باشد، بارگذاری
     * مدل خودش روی CPU ادامه پیدا می‌کند (پایین‌تر، در ensureLoaded).
     */
    fun isGpuAvailable(): Boolean = true

    fun isLoaded(): Boolean = loaded?.isLoaded == true

    fun loadedModelPath(): String = loadedPath

    fun systemInfo(): String = runCatching { Llama.getSystemInfo() }.getOrDefault("")

    private fun configFor(tuning: OfflineTuning, gpuLayers: Int = gpuLayersFor(tuning)): LlamaConfig = LlamaConfig(
        contextSize = tuning.contextSize,
        threads = tuning.threads,
        // صفر = فقط CPU، هر عدد مثبت = همان تعداد لایه روی GPU. ۹۹ یعنی همهٔ لایه‌ها.
        gpuLayers = gpuLayers,
        temperature = tuning.temperature,
        topP = tuning.topP,
        topK = tuning.topK,
        seed = -1
    )

    private fun gpuLayersFor(tuning: OfflineTuning): Int =
        if (tuning.gpuEnabled) GPU_ALL_LAYERS else 0

    private fun signatureOf(path: String, tuning: OfflineTuning): String =
        "$path|ctx=${tuning.contextSize}|th=${tuning.threads}|t=${tuning.temperature}|p=${tuning.topP}|k=${tuning.topK}|gpu=${gpuLayersFor(tuning)}"

    suspend fun loadModel(path: String): ApiResult<String> = lock.withLock { ensureLoaded(path) }

    /** آزاد کردن حافظهٔ مدل. */
    fun release() {
        runCatching { loaded?.let { Llama.releaseModel(it) } }
        loaded = null
        loadedPath = ""
        loadedSignature = ""
    }

    private suspend fun ensureLoaded(path: String): ApiResult<String> {
        if (!isDeviceSupported()) {
            return ApiResult.Error(
                "اجرای مدل آفلاین روی این گوشی پشتیبانی نمی‌شود (فقط گوشی‌های arm64)."
            )
        }
        if (path.isBlank()) return ApiResult.Error("هیچ مدل آفلاینی انتخاب نشده است.")
        val file = File(path)
        if (!file.exists()) return ApiResult.Error("فایل مدل پیدا نشد: $path")

        val tuning = tuning()
        val signature = signatureOf(path, tuning)
        val current = loaded
        if (current != null && current.isLoaded && signature == loadedSignature) {
            return ApiResult.Success("مدل از قبل بارگذاری شده است")
        }

        isLoading = true
        // مدل قبلی آزاد می‌شود؛ ماندن دو مدل روی هم یعنی تمام شدن حافظه.
        runCatching { loaded?.let { Llama.releaseModel(it) } }
        loaded = null
        loadedPath = ""
        loadedSignature = ""

        return try {
            val wantsGpu = tuning.gpuEnabled
            var usedCpuFallback = false
            val model = try {
                Llama.loadModel(modelPath = path, config = configFor(tuning))
            } catch (gpuError: Throwable) {
                // گوشی Vulkan ندارد یا حافظهٔ GPU بس نیست. یک بار روی CPU امتحان می‌کنیم
                // تا کاربر به‌جای پیام خطا، مدل را داشته باشد.
                if (!wantsGpu) throw gpuError
                usedCpuFallback = true
                Llama.loadModel(modelPath = path, config = configFor(tuning, gpuLayers = 0))
            }
            loaded = model
            loadedPath = path
            loadedSignature = if (usedCpuFallback) "$signature|cpu-fallback" else signature
            lastError = ""
            val where = when {
                wantsGpu && !usedCpuFallback -> "GPU (Vulkan)"
                wantsGpu && usedCpuFallback -> "CPU — GPU در دسترس نبود"
                else -> "CPU"
            }
            ApiResult.Success(
                "مدل بارگذاری شد — $where، ${tuning.threads} رشته، پنجرهٔ ${tuning.contextSize} توکن"
            )
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
                "این فایل ${"%.1f".format(sizeGb)} گیگابایت است. روی این گوشی باید باز شود، پس اگر خطا داد «پنجرهٔ متن» را در تنظیمات سرعت کمتر کن (۵۱۲)."
            else ->
                "«پنجرهٔ متن» را در تنظیمات سرعت کمتر کن، یا چند اپ دیگر را ببند و دوباره امتحان کن."
        }
        return "بارگذاری مدل انجام نشد.\n$hint\nخطای موتور: $raw"
    }

    /**
     * پاسخ به یک پیام، کاملاً روی گوشی.
     *
     * [conversationHistory] کل گفتگو تا همین لحظه است؛ آخرین پیام کاربر، پیام تازه است.
     */
    suspend fun chat(
        modelPath: String,
        conversationHistory: List<ChatMessage>,
        systemInstructionText: String
    ): ApiResult<ChatApiResponse> = lock.withLock {
        val ready = ensureLoaded(modelPath)
        if (ready is ApiResult.Error) return@withLock ApiResult.Error(ready.message)

        val model = loaded ?: return@withLock ApiResult.Error("مدل آفلاین بارگذاری نشده است.")
        val tuning = tuning()
        val question = conversationHistory.lastOrNull { it.sender == MessageSender.USER }?.text.orEmpty()
        if (question.isBlank() && conversationHistory.none { it.text.isNotBlank() }) {
            return@withLock ApiResult.Error("پیامی برای فرستادن به مدل آفلاین وجود ندارد.")
        }

        // دانش محلی: فقط بخش مربوط به همین سؤال به مدل داده می‌شود تا پنجرهٔ کوچک
        // مدل با متن بی‌ربط پر نشود.
        // دانش محلی بر اساس بودجهٔ توکن درخواست می‌شود، نه با یک عدد ثابت؛ وگرنه روی
        // پنجرهٔ کوچک، دانش جای سؤال کاربر را می‌گیرد و مدل را می‌بندد.
        val knowledge = if (tuning.knowledgeEnabled) {
            val share = TokenBudget(tuning.contextSize, tuning.maxTokens)
            val maxChars = ((share.forInput * KNOWLEDGE_SHARE) / 100 * CHARS_PER_TOKEN).toInt()
            appContext?.let { KnowledgeBase.contextFor(it, question, maxChars) }.orEmpty()
        } else {
            ""
        }

        // ---- بودجهٔ توکن: مهم‌ترین بخش این تابع ----
        // اگر مجموع متنِ فرستاده‌شده از پنجرهٔ مدل بزرگ‌تر شود، llama.cpp خودش را
        // می‌بندد (abort داخل llama_context::decode) و کل برنامه بسته می‌شود.
        // این خطای بومی را هیچ try/catch نمی‌گیرد، پس باید از قبل جلویش را گرفت.
        val budget = TokenBudget(tuning.contextSize, tuning.maxTokens)

        val systemPrompt = buildString {
            append(
                truncateToTokens(
                    compactSystemPrompt(systemInstructionText),
                    (budget.forInput * SYSTEM_SHARE) / 100
                )
            )
            append("\nفقط جواب نهایی را بنویس، کوتاه و مستقیم و به زبان فارسی.")
            if (knowledge.isNotBlank()) {
                append("\n\nاگر در «اطلاعات پایگاه دانش» مطلبی مربوط به سؤال بود، از همان استفاده کن:\n")
                append(
                    truncateToTokens(
                        knowledge,
                        (budget.forInput * KNOWLEDGE_SHARE) / 100
                    )
                )
            }
        }

        val prompt = buildPrompt(
            history = conversationHistory,
            tokenBudget = budget.forInput - estimateTokens(systemPrompt) - 24
        )

        try {
            val result = Llama.complete(
                model,
                prompt = prompt,
                systemPrompt = systemPrompt,
                maxTokens = tuning.maxTokens
            )
            val (answer, thinking) = splitThinking(result.text)
            val speed = "%.1f".format(result.tokensPerSecond)

            when {
                answer.isNotBlank() -> ApiResult.Success(
                    ChatApiResponse(
                        replyText = answer,
                        thinkingText = thinking,
                        searchSources = emptyList(),
                        modelUsed = "آفلاین · $speed توکن/ثانیه" + if (knowledge.isNotBlank()) " · با پایگاه دانش" else ""
                    )
                )
                // مدل تمام سقف را صرف فکر کردن کرد و به جواب نرسید. این حالت پیش‌تر
                // به‌صورت یک متن انگلیسی عجیب به کاربر نشان داده می‌شد.
                thinking != null -> ApiResult.Error(
                    "مدل فقط «فکر کرد» و سقف پاسخ تمام شد. راه‌حل: در تنظیمات سرعت، «حداکثر طول پاسخ» را بیشتر کن (مثلاً ۵۱۲) یا سؤال کوتاه‌تری بپرس."
                )
                else -> ApiResult.Error("مدل آفلاین جواب خالی داد. دوباره بفرست یا مدل کوچک‌تری بگذار.")
            }
        } catch (error: Throwable) {
            lastError = error.message ?: error.toString()
            ApiResult.Error("خطا در اجرای مدل آفلاین: ${(error.message ?: "خطای نامشخص").take(250)}")
        }
    }

    /**
     * جدا کردن بلوک «فکر کردن» از جواب نهایی.
     *
     * مدل‌های DeepSeek-R1 و مانند آن، اول یک بلوک بلند انگلیسی بین `think` می‌نویسند
     * و بعد جواب فارسی. اگر آن بلوک جدا نشود، کاربر فکر می‌کند اپ خراب است (در آزمون
     * واقعی هم دقیقاً همین اتفاق افتاد).
     */
    private fun splitThinking(raw: String): Pair<String, String?> {
        val open = raw.indexOf(THINK_OPEN)
        val close = raw.indexOf(THINK_CLOSE)

        if (open >= 0 && close > open) {
            val thinking = raw.substring(open + THINK_OPEN.length, close).trim()
            val answer = (raw.substring(0, open) + raw.substring(close + THINK_CLOSE.length)).trim()
            return answer to thinking.ifBlank { null }
        }
        if (close >= 0) {
            // تگ بسته هست ولی باز نیست: هرچه قبلش بود فکر کردن بوده.
            val thinking = raw.substring(0, close).trim()
            return raw.substring(close + THINK_CLOSE.length).trim() to thinking.ifBlank { null }
        }
        if (open >= 0) {
            // فکر کردن شروع شده ولی سقف توکن‌ها اجازهٔ بستنش را نداده.
            val thinking = raw.substring(open + THINK_OPEN.length).trim()
            return "" to thinking.ifBlank { null }
        }
        return raw.trim() to null
    }

    /**
     * بودجهٔ توکن: چه چیزی جا می‌شود و چه چیزی نه.
     *
     * llama.cpp وقتی متن از پنجرهٔ مدل بزرگ‌تر شود، خودش را با `abort` می‌بندد؛
     * این خطای بومی (native) با try/catch گرفته نمی‌شود و کل برنامه را می‌کشد.
     * پس پیش از فرستادن، خودمان متن را در بودجه جا می‌دهیم.
     */
    private class TokenBudget(contextSize: Int, maxTokens: Int) {
        /** سهم پاسخ؛ هیچ‌وقت بیشتر از یک‌سوم پنجره، تا متن ورودی جا بماند. */
        val forOutput: Int = maxTokens.coerceIn(64, (contextSize / 3).coerceAtLeast(64))

        /** سهم ورودی، با یک حاشیهٔ امن برای اختلاف توکنایزرها. */
        val forInput: Int = (contextSize - forOutput - HEADROOM_TOKENS).coerceAtLeast(128)
    }

    /**
     * راهنمای شخصیت را از فهرست ابزارها جدا می‌کند.
     *
     * راهنمای اپ فهرست بلندی از ابزارها (گیت‌هاب، اجرای برنامه، خواندن صفحه و…) دارد
     * که چند صد توکن از پنجرهٔ مدل را می‌خورد. مدل ۱.۵ میلیاردی توانایی صدا زدن درست
     * آن ابزارها را ندارد و اگر اشتباهی تگی تولید کند، ممکن است کار ناخواسته‌ای انجام
     * شود. پس فقط شخصیت و قوانین می‌مانند و توکن‌های آزادشده به سؤال کاربر و پایگاه
     * دانش می‌رسند.
     */
    private fun compactSystemPrompt(text: String): String {
        val marker = "ابزارهای در دسترس تو:"
        val cut = text.indexOf(marker)
        if (cut <= 0) return text
        return text.substring(0, cut).trim() +
            "\n(ابزارها روی مدل آفلاین غیرفعال هستند؛ با دانش خودت جواب بده.)"
    }

    /** تخمین محافظه‌کارانهٔ تعداد توکن از روی طول متن (فارسی معمولاً گران‌تر است). */
    private fun estimateTokens(text: String): Int =
        (text.length / CHARS_PER_TOKEN).toInt() + 12

    /** متن را به اندازهٔ بودجهٔ توکنی کوتاه می‌کند. */
    private fun truncateToTokens(text: String, tokenBudget: Int): String {
        if (tokenBudget <= 0) return ""
        val maxChars = (tokenBudget * CHARS_PER_TOKEN).toInt()
        if (text.length <= maxChars) return text
        return text.take(maxChars).trimEnd() + "…"
    }

    /**
     * چند پیام آخر به یک متن تبدیل می‌شود؛ خودِ کتابخانه قالب چت مدل را اعمال می‌کند.
     *
     * پیام‌ها از **آخر به اول** اضافه می‌شوند تا وقتی بودجه تمام شد، قدیمی‌ترین‌ها
     * حذف شوند و پیام تازهٔ کاربر همیشه بماند. پیش‌تر فقط «۶ پیام آخر» گرفته می‌شد
     * و هیچ سقفی برای طول کل وجود نداشت — همان چیزی که باعث بسته شدن برنامه می‌شد.
     */
    private fun buildPrompt(history: List<ChatMessage>, tokenBudget: Int): String {
        val turns = history.filter { it.text.isNotBlank() }.takeLast(8)
        if (turns.isEmpty()) return ""

        val last = turns.last()
        val budget = tokenBudget.coerceAtLeast(64)

        // پیام تازهٔ کاربر همیشه می‌ماند (اگر لازم شد، کوتاه می‌شود).
        val lastText = truncateToTokens(last.text, budget)
        var remaining = budget - estimateTokens(lastText) - 8

        val earlier = mutableListOf<String>()
        turns.dropLast(1).asReversed().forEach { message ->
            if (remaining < 48) return@forEach
            val line = (if (message.sender == MessageSender.USER) "کاربر" else "دستیار") +
                ": " + message.text.replace('\n', ' ')
            val trimmed = truncateToTokens(line, remaining.coerceAtMost(220))
            earlier.add(0, trimmed)
            remaining -= estimateTokens(trimmed)
        }

        if (earlier.isEmpty()) return lastText
        return buildString {
            append("گفتگوی قبلی:\n")
            earlier.forEach { append(it).append('\n') }
            append("\nپیام جدید کاربر: ")
            append(lastText)
        }
    }

    /**
     * هر توکن به‌طور محافظه‌کارانه معادل [CHARS_PER_TOKEN] حرف حساب می‌شود.
     * کمتر گرفتن این عدد یعنی متن کوتاه‌تر می‌فرستیم و احتمال بسته شدن اپ کمتر می‌شود.
     */
    private const val CHARS_PER_TOKEN = 1.6

    /** حاشیهٔ امن برای اختلاف توکنایزر مدل‌های مختلف. */
    private const val HEADROOM_TOKENS = 96

    /** حداکثر سهم متن راهنما (شخصیت و قوانین) از ورودی. */
    private const val SYSTEM_SHARE = 40

    /** حداکثر سهم پایگاه دانش از ورودی. */
    private const val KNOWLEDGE_SHARE = 35
}
