package com.example.data.model

enum class AiProviderKind(val persianTitle: String, val badgeColorHex: Long) {
    BUILTIN_FREE("هوش مصنوعی رایگان فوکو", 0xFF10B981),
    HUGGINGFACE("Hugging Face", 0xFFFFD21E),
    XAI_GROK("xAI Grok", 0xFF1E293B),
    OPENROUTER("OpenRouter", 0xFF6366F1),
    GROQ("Groq", 0xFFEA580C),
    GEMINI("Google Gemini", 0xFF2563EB)
}

enum class ModelCapability {
    TEXT_CHAT,
    VISION,
    IMAGE
}

data class CatalogModel(
    val id: String,
    val title: String,
    val subtitle: String,
    val provider: AiProviderKind,
    val apiModelId: String,
    val badge: String,
    val isRecommended: Boolean = false,
    val capability: ModelCapability = ModelCapability.TEXT_CHAT
)

object AiModelCatalog {
    val builtinFree = CatalogModel(
        id = "foko-free",
        title = "هوش مصنوعی رایگان و عمومی فوکو",
        subtitle = "پاسخگویی هوشمند و سریع بدون نیاز به وارد کردن هیچ کلیدی",
        provider = AiProviderKind.BUILTIN_FREE,
        apiModelId = "openai-large",
        badge = "رایگان و فعال",
        isRecommended = true
    )

    val hfQwen25 = CatalogModel(
        id = "hf-qwen25",
        title = "کیوون ۷۲ میلیارد (Qwen 2.5)",
        subtitle = "استدلال، مکالمه دقیق فارسی و کدنویسی عمیق",
        provider = AiProviderKind.HUGGINGFACE,
        apiModelId = "Qwen/Qwen2.5-72B-Instruct",
        badge = "Hugging Face",
        isRecommended = true
    )

    val hfQwenCoder = CatalogModel(
        id = "hf-qwencoder",
        title = "کیوون کدنویسی (Qwen Coder 32B)",
        subtitle = "توسعه، ساخت اسکریپت و تحلیل تخصصی برنامه نویسی",
        provider = AiProviderKind.HUGGINGFACE,
        apiModelId = "Qwen/Qwen2.5-Coder-32B-Instruct",
        badge = "Hugging Face"
    )

    val hfDeepseekR1 = CatalogModel(
        id = "hf-deepseek-r1",
        title = "دیپ‌سیک R1 استدلالی (DeepSeek R1)",
        subtitle = "تفکر منطقی عمیق و حل مسائل ریاضی و تحلیلی",
        provider = AiProviderKind.HUGGINGFACE,
        apiModelId = "deepseek-ai/DeepSeek-R1-Distill-Qwen-32B",
        badge = "Hugging Face"
    )

    val hfLlama31 = CatalogModel(
        id = "hf-llama31",
        title = "لاما ۳.۱ هشت میلیارد (Llama 3.1 8B)",
        subtitle = "پاسخ‌های روان و سریع برای چت روزمره",
        provider = AiProviderKind.HUGGINGFACE,
        apiModelId = "meta-llama/Llama-3.1-8B-Instruct",
        badge = "Hugging Face"
    )

    val xaiGrok2 = CatalogModel(
        id = "xai-grok2",
        title = "گروک ۲ رسمی (Grok 2)",
        subtitle = "جدیدترین مدل هوش مصنوعی رسمی xAI ایلان ماسک",
        provider = AiProviderKind.XAI_GROK,
        apiModelId = "grok-2-latest",
        badge = "xAI Grok",
        isRecommended = true
    )

    val xaiGrokBeta = CatalogModel(
        id = "xai-grokbeta",
        title = "گروک آزمایشی (Grok Beta)",
        subtitle = "پاسخگویی سریع و طنزآمیز گروک با دسترسی مستقیم",
        provider = AiProviderKind.XAI_GROK,
        apiModelId = "grok-beta",
        badge = "xAI Grok"
    )

    val orLlamaFree = CatalogModel(
        id = "or-llama-free",
        title = "لاما ۳.۳ رایگان (OpenRouter)",
        subtitle = "مدل ۷۰ میلیارد رایگان از طریق OpenRouter",
        provider = AiProviderKind.OPENROUTER,
        apiModelId = "meta-llama/llama-3.3-70b-instruct:free",
        badge = "OpenRouter"
    )

    val orDeepseekFree = CatalogModel(
        id = "or-deepseek-free",
        title = "دیپ‌سیک R1 رایگان (OpenRouter)",
        subtitle = "استدلال هوشمند رایگان از طریق OpenRouter",
        provider = AiProviderKind.OPENROUTER,
        apiModelId = "deepseek/deepseek-r1:free",
        badge = "OpenRouter"
    )

    val orQwenFree = CatalogModel(
        id = "or-qwen-free",
        title = "کیوون ۲.۵ رایگان (OpenRouter)",
        subtitle = "مدل ۷۲ میلیارد رایگان از طریق OpenRouter",
        provider = AiProviderKind.OPENROUTER,
        apiModelId = "qwen/qwen-2.5-72b-instruct:free",
        badge = "OpenRouter"
    )

    val groqLlama33 = CatalogModel(
        id = "groq-llama33",
        title = "لاما ۳.۳ سریع (Groq)",
        subtitle = "پاسخ فوری و فوق سریع بر بستر زیرساخت LPU",
        provider = AiProviderKind.GROQ,
        apiModelId = "llama-3.3-70b-versatile",
        badge = "Groq"
    )

    val geminiFlash = CatalogModel(
        id = "gemini-flash",
        title = "جمینای ۲.۰ فلش (Google Gemini)",
        subtitle = "جدیدترین نسل هوش مصنوعی چندرسانه‌ای گوگل",
        provider = AiProviderKind.GEMINI,
        apiModelId = "gemini-2.0-flash",
        badge = "Gemini",
        capability = ModelCapability.VISION
    )

    val geminiPro = CatalogModel(
        id = "gemini-pro",
        title = "جمینای ۱.۵ پرو (تفکر عمیق گوگل)",
        subtitle = "درک زمینه‌های طولانی، منطق پیشرفته و برنامه‌نویسی",
        provider = AiProviderKind.GEMINI,
        apiModelId = "gemini-1.5-pro",
        badge = "Gemini",
        capability = ModelCapability.VISION
    )

    val all: List<CatalogModel> = listOf(
        builtinFree,
        hfQwen25,
        hfQwenCoder,
        hfDeepseekR1,
        hfLlama31,
        xaiGrok2,
        xaiGrokBeta,
        orLlamaFree,
        orDeepseekFree,
        orQwenFree,
        groqLlama33,
        geminiFlash,
        geminiPro
    )

    fun defaultModel(): CatalogModel = builtinFree

    fun findById(id: String): CatalogModel? = all.find { it.id == id }

    fun textModels(): List<CatalogModel> =
        all.filter { it.capability != ModelCapability.IMAGE }

    fun activeModels(snapshot: com.example.data.local.ApiKeySnapshot): List<CatalogModel> {
        return textModels().filter { snapshot.isConfigured(it.provider) }
    }

    fun modelsForProvider(provider: AiProviderKind): List<CatalogModel> {
        return textModels().filter { it.provider == provider }
    }

    fun fromGeminiOption(option: AiModelOption): CatalogModel {
        return when (option) {
            AiModelOption.FLASH -> geminiFlash
            AiModelOption.PRO -> geminiPro
            else -> geminiFlash
        }
    }

    fun toGeminiOption(model: CatalogModel): AiModelOption? {
        if (model.provider != AiProviderKind.GEMINI) return null
        return when (model.id) {
            "gemini-pro" -> AiModelOption.PRO
            else -> AiModelOption.FLASH
        }
    }
}
