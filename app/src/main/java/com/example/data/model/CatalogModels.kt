package com.example.data.model

enum class AiProviderKind(val persianTitle: String, val badgeColorHex: Long) {
    HUGGINGFACE("Hugging Face", 0xFFFFD21E)
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

    val all: List<CatalogModel> = listOf(
        hfQwen25,
        hfQwenCoder,
        hfDeepseekR1,
        hfLlama31
    )

    fun defaultModel(): CatalogModel = hfQwen25

    fun findById(id: String): CatalogModel? = all.find { it.id == id }

    fun textModels(): List<CatalogModel> =
        all.filter { it.capability != ModelCapability.IMAGE }

    fun activeModels(snapshot: com.example.data.local.ApiKeySnapshot): List<CatalogModel> {
        return textModels().filter { snapshot.isConfigured(it.provider) }
    }

    fun modelsForProvider(provider: AiProviderKind): List<CatalogModel> {
        return textModels().filter { it.provider == provider }
    }
}
