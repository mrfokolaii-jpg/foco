package com.example.data.offline

import android.content.Context
import android.content.SharedPreferences

/**
 * تنظیمات سرعت و کیفیتِ مدل آفلاین — کاربر خودش تنظیم می‌کند.
 *
 * چرا این تنظیمات مهم‌اند: روی گوشی، «سریع‌تر» و «خنک‌تر» تقریباً یک چیز هستند.
 * بیشترین گرمای گوشی از این‌جا می‌آید که همهٔ هسته‌ها (حتی هسته‌های ضعیف) تا آخر
 * کار کنند؛ کم کردن رشته‌ها معمولاً هم سرعت را بهتر می‌کند و هم گوشی را خنک‌تر.
 */
data class OfflineTuning(
    /** پنجرهٔ متن: هرچه بزرگ‌تر، متن بیشتری به یاد می‌آورد ولی کندتر و داغ‌تر. */
    val contextSize: Int = DEFAULT_CONTEXT,
    /** تعداد رشته‌های پردازشی. */
    val threads: Int = 4,
    /** حداکثر طول پاسخ. */
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
    /** خلاقیت: پایین = منطقی‌تر و دقیق‌تر. */
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    /** از نسخهٔ ۲.۵ موتور Vulkan داخل اپ هست، پس پیش‌فرض روشن است. */
    val gpuEnabled: Boolean = true,
    /** آیا از پایگاه دانش در پاسخ‌ها استفاده شود. */
    val knowledgeEnabled: Boolean = true
) {
    companion object {
        // ۲۰۴۸ برای این مدل‌ها تنگ است: راهنمای اپ + پایگاه دانش + گفتگو به‌سرعت
        // از آن رد می‌شد و موتور مجبور می‌شد برنامه را ببندد. روی گوشی ۱۲ گیگابایتی
        // ۴۰۹۶ هیچ هزینهٔ محسوسی ندارد.
        const val DEFAULT_CONTEXT = 4096
        const val DEFAULT_MAX_TOKENS = 320

        val CONTEXT_CHOICES = listOf(1024, 2048, 4096, 6144, 8192)
        val MAX_TOKEN_CHOICES = listOf(128, 192, 320, 512, 768)
        val THREAD_CHOICES = listOf(2, 3, 4, 5, 6, 8)
    }
}

object OfflineSettings {

    private const val PREFS = "offline_tuning"
    private const val KEY_CONTEXT = "context_size"
    private const val KEY_THREADS = "threads"
    private const val KEY_MAX_TOKENS = "max_tokens"
    private const val KEY_TEMP = "temperature"
    private const val KEY_TOPP = "top_p"
    private const val KEY_TOPK = "top_k"
    private const val KEY_GPU = "gpu_enabled"
    private const val KEY_KB = "knowledge_enabled"
    private const val KEY_MIGRATED_CTX = "migrated_context_v2"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * رشته پیشنهادی: هسته‌های «بزرگ».
     *
     * گوشی‌های امروزی هسته‌های ضعیف (LITTLE) هم دارند. اگر همه را با هم به کار
     * بگیری، مدل باید منتظرِ کندترین هسته بماند: هم کندتر می‌شود و هم داغ‌تر.
     * برای همین روی ۴ رشته (هسته‌های قدرتمند) متوقف می‌شویم.
     */
    fun recommendedThreads(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        return when {
            cores >= 6 -> 4
            cores >= 4 -> 3
            else -> 2
        }
    }

    /** اندازه‌های پیشنهادی بر اساس رم واقعی گوشی (نه یک عدد ثابت). */
    fun recommendedContext(): Int = OfflineTuning.DEFAULT_CONTEXT

    /**
     * یک‌بار برای همیشه، پنجرهٔ متنِ نصب‌های قبلی را بزرگ می‌کند.
     *
     * پیش‌تر مقدار پیش‌فرض ۲۰۴۸ بود و همان عدد در حافظهٔ گوشی ذخیره شده است؛
     * همان مقدارِ تنگ باعث می‌شد موتور آفلاین هنگام پاسخ‌دادن برنامه را ببندد.
     */
    fun migrate(context: Context) {
        val p = prefs(context)
        if (p.getBoolean(KEY_MIGRATED_CTX, false)) return
        val target = OfflineTuning.DEFAULT_CONTEXT
        if (p.getInt(KEY_CONTEXT, target) < target) {
            p.edit().putInt(KEY_CONTEXT, target).apply()
        }
        p.edit().putBoolean(KEY_MIGRATED_CTX, true).apply()
    }

    fun load(context: Context): OfflineTuning {
        val p = prefs(context)
        val tuning = OfflineTuning(
            contextSize = p.getInt(KEY_CONTEXT, recommendedContext()),
            threads = p.getInt(KEY_THREADS, recommendedThreads()),
            maxTokens = p.getInt(KEY_MAX_TOKENS, OfflineTuning.DEFAULT_MAX_TOKENS),
            temperature = p.getFloat(KEY_TEMP, 0.7f),
            topP = p.getFloat(KEY_TOPP, 0.9f),
            topK = p.getInt(KEY_TOPK, 40),
            gpuEnabled = p.getBoolean(KEY_GPU, true),
            knowledgeEnabled = p.getBoolean(KEY_KB, true)
        )
        return tuning.sanitized()
    }

    fun save(context: Context, tuning: OfflineTuning) {
        val t = tuning.sanitized()
        prefs(context).edit()
            .putInt(KEY_CONTEXT, t.contextSize)
            .putInt(KEY_THREADS, t.threads)
            .putInt(KEY_MAX_TOKENS, t.maxTokens)
            .putFloat(KEY_TEMP, t.temperature)
            .putFloat(KEY_TOPP, t.topP)
            .putInt(KEY_TOPK, t.topK)
            .putBoolean(KEY_GPU, t.gpuEnabled)
            .putBoolean(KEY_KB, t.knowledgeEnabled)
            .apply()
    }

    fun reset(context: Context): OfflineTuning {
        val fresh = OfflineTuning()
        save(context, fresh)
        return fresh
    }

    /** مقادیر بیرون از محدوده را به محدودهٔ سالم برمی‌گرداند. */
    private fun OfflineTuning.sanitized(): OfflineTuning = copy(
        contextSize = contextSize.coerceIn(256, 8192),
        threads = threads.coerceIn(1, 16),
        maxTokens = maxTokens.coerceIn(32, 2048),
        temperature = temperature.coerceIn(0.0f, 1.5f),
        topP = topP.coerceIn(0.1f, 1.0f),
        topK = topK.coerceIn(1, 200)
    )
}
