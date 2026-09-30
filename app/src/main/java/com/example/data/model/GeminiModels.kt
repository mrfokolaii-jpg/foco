package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject

object GeminiModelConstants {
    const val MODEL_PRO = "gemini-3.1-pro-preview"
    const val MODEL_FLASH = "gemini-3.5-flash"
    const val MODEL_LITE = "gemini-3.1-flash-lite-preview"
    const val MODEL_IMAGE_PRO = "gemini-3-pro-image-preview"
    const val MODEL_IMAGE_FLASH = "gemini-3.1-flash-image-preview"
    const val MODEL_IMAGE_FAST = "gemini-2.5-flash-image"
    const val MODEL_LYRIA_CLIP = "lyria-3-clip-preview"
    const val MODEL_LYRIA_PRO = "lyria-3-pro-preview"
    const val MODEL_VEO_FAST = "veo-3.1-fast-generate-preview"
}

enum class AiModelOption(
    val modelId: String,
    val persianTitle: String,
    val persianDescription: String,
    val badge: String
) {
    FLASH(
        modelId = GeminiModelConstants.MODEL_FLASH,
        persianTitle = "جِمینای ۳.۵ فلش",
        persianDescription = "تعادل عالی بین سرعت بالا و درک عمیق برای گفتگوهای روزمره و وب",
        badge = "پیش‌فرض"
    ),
    PRO(
        modelId = GeminiModelConstants.MODEL_PRO,
        persianTitle = "جِمینای ۳.۱ پرو (تفکر عمیق)",
        persianDescription = "قدرتمندترین مدل تحلیلی برای منطق، استدلال و کدنویسی پیچیده",
        badge = "هوش پیشرفته"
    ),
    LITE(
        modelId = GeminiModelConstants.MODEL_LITE,
        persianTitle = "جِمینای ۳.۱ فلش لایت",
        persianDescription = "فوق‌العاده سریع و سبک برای پاسخ‌های فوری و چکیده‌سازی سریع",
        badge = "فوق سریع"
    )
}

enum class SystemPersona(
    val id: String,
    val title: String,
    val iconName: String,
    val systemPrompt: String
) {
    GENERAL(
        id = "general",
        title = "دستیار صوتی و هوشمند فوکو (FOKO AI)",
        iconName = "AutoAwesome",
        systemPrompt = "شما «فوکو» (FOKO AI) هستید؛ دستیار هوشمند، همه‌کاره، سخنگوی صوتی و مجری کارهای کاربر روی دستگاه اندروید. شما کاملاً به گوشی، فرامین صوتی، برنامه‌ها، وب و ابزارها متصل هستید. همیشه پاسخ‌های خود را به زبان فارسی روان، شیوا، دوستانه و شفاف ارائه دهید. هرگز خود را فیلسوف میشل فوکو ندانید و هرگز نگویید به عنوان یک مدل زبانی به دستگاه یا اینترنت دسترسی ندارم. به تمام درخواست‌ها با اطمینان، ادب و آمادگی کامل برای کمک پاسخ دهید."
    ),
    POET(
        id = "poet",
        title = "شاعر و ادیب پارسی",
        iconName = "HistoryEdu",
        systemPrompt = "شما یک ادیب و شاعر برجسته زبان و ادبیات فارسی هستید. در پاسخ‌ها از نثری فاخر، تشبیهات زیبا، اشعار ناب فارسی (حافظ، سعدی، فردوسی، مولانا و سهراب) و بلاغت کلامی استفاده کنید و در صورت نیاز شعر مناسب بسرایید."
    ),
    CODER(
        id = "coder",
        title = "مهندس ارشد نرم‌افزار",
        iconName = "Code",
        systemPrompt = "شما یک مهندس ارشد نرم‌افزار و برنامه‌نویس متخصص هستید. تمام توضیحات، راهنمایی‌ها و کامنت‌های کد شما به زبان فارسی شفاف، روان و دقیق باشد. کدهای تمیز، امن و بهینه ارائه دهید."
    ),
    SCHOLAR(
        id = "scholar",
        title = "پژوهشگر و استاد علمی",
        iconName = "School",
        systemPrompt = "شما یک استاد و پژوهشگر علمی برجسته هستید. مباحث را با استدلال منطقی، ریشه‌یابی علمی و زبان فارسی علمی و آکادمیک شرح دهید و منابع معتبر را معرفی نمایید."
    ),
    CONSULTANT(
        id = "consultant",
        title = "مشاور زندگی و کسب‌وکار",
        iconName = "Psychology",
        systemPrompt = "شما یک مشاور مجرب در حوزه رشد فردی، روان‌شناسی و مدیریت کسب‌وکار هستید. با لحنی دلگرم‌کننده، سازنده و راهبردی به زبان فارسی به کاربر مشاوره دهید."
    )
}

data class GroundingSource(
    val title: String,
    val uri: String
)

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val imageBase64: String? = null,
    val imageBase64List: List<String> = emptyList(),
    val thinkingText: String? = null,
    val searchSources: List<GroundingSource> = emptyList(),
    val modelUsed: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false
) {
    fun allImages(): List<String> {
        val list = mutableListOf<String>()
        if (!imageBase64.isNullOrBlank()) list.add(imageBase64)
        list.addAll(imageBase64List)
        return list.distinct()
    }
}

enum class MessageSender {
    USER, ASSISTANT
}

data class ConversationExport(
    val title: String,
    val exportedAt: Long = System.currentTimeMillis(),
    val messages: List<ChatMessage>
) {
    fun toPlainText(): String = toPlainText(title, messages)

    companion object {
        fun toPlainText(title: String, messages: List<ChatMessage>): String {
            val sb = StringBuilder()
            sb.appendLine("عنوان گفتگو: $title")
            sb.appendLine("تاریخ خروجی: ${java.util.Date()}")
            sb.appendLine("----------------------------------------")
            messages.forEach { msg ->
                val sender = if (msg.sender == MessageSender.USER) "کاربر" else "فوکو"
                sb.appendLine("[$sender]: ${msg.text}")
            }
            return sb.toString()
        }
    }
}

data class ImageGenerationConfig(
    val modelId: String = GeminiModelConstants.MODEL_IMAGE_PRO,
    val prompt: String,
    val resolution: String = "1K", // 512px, 1K, 2K, 4K
    val aspectRatio: String = "1:1", // 1:1, 16:9, 9:16, 4:3, 3:4
    val stylePrompt: String = "",
    val baseImageBase64: String? = null
)

data class MusicGenerationConfig(
    val modelId: String = GeminiModelConstants.MODEL_LYRIA_CLIP,
    val prompt: String,
    val durationSeconds: Int = 30,
    val genre: String = "سنتی ایرانی"
)

data class VideoGenerationConfig(
    val modelId: String = GeminiModelConstants.MODEL_VEO_FAST,
    val prompt: String,
    val aspectRatio: String = "16:9", // 16:9 or 9:16
    val baseImageBase64: String? = null
)
