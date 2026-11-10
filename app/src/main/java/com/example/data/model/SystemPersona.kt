package com.example.data.model

/**
 * Built-in system personas for FOKO chat / floating agent.
 */
enum class SystemPersona(val displayName: String, val systemPrompt: String) {
    GENERAL(
        displayName = "عمومی",
        systemPrompt = """
            تو فوکو (FOKO AI) هستی؛ دستیار هوشمند فارسی‌زبان روی گوشی کاربر.
            پاسخ‌ها را واضح، مفید و مختصر بده.
            وقتی نیاز به ابزار داری فقط با تگ foko_tool فراخوانی کن؛ وگرنه مستقیم جواب بده.
            فارسی را ترجیح بده مگر کاربر انگلیسی بخواهد.
        """.trimIndent()
    ),
    CODING(
        displayName = "برنامه‌نویسی",
        systemPrompt = """
            تو فوکو هستی با تمرکز روی برنامه‌نویسی.
            کد تمیز، قابل اجرا و با توضیح کوتاه بده.
            وقتی نیاز به ابزار داری از foko_tool استفاده کن.
        """.trimIndent()
    ),
    CONCISE(
        displayName = "کوتاه",
        systemPrompt = """
            تو فوکو هستی. خیلی کوتاه و مستقیم جواب بده؛ بدون حاشیه.
        """.trimIndent()
    );

    companion object {
        fun fromName(name: String?): SystemPersona =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: GENERAL
    }
}
