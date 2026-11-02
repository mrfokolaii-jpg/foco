package com.example.data.model

enum class AiProviderKind(val persianTitle: String, val badgeColorHex: Long) {
    HUGGINGFACE("Hugging Face", 0xFFFFD21E),
    CUSTOM("سفارشی", 0xFF00BFA5)
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
    val capability: ModelCapability = ModelCapability.TEXT_CHAT,
    val customBaseUrl: String? = null
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

    val noneConfigured = CatalogModel(
        id = "none",
        title = "مدل تنظیم نشده",
        subtitle = "از بخش تنظیمات کلید و مدل را وارد کنید",
        provider = AiProviderKind.CUSTOM,
        apiModelId = "",
        badge = "—"
    )

    val all: List<CatalogModel> = listOf(
        hfQwen25,
        hfQwenCoder,
        hfDeepseekR1,
        hfLlama31
    )

    fun defaultModel(): CatalogModel = noneConfigured

    fun findById(id: String): CatalogModel? = all.find { it.id == id }

    fun textModels(): List<CatalogModel> =
        all.filter { it.capability != ModelCapability.IMAGE }

    fun activeModels(snapshot: com.example.data.local.ApiKeySnapshot): List<CatalogModel> {
        val list = mutableListOf<CatalogModel>()
        if (snapshot.huggingFaceConfigured) {
            // اگر Base URL هنوز همان Hugging Face است، مدل‌های کاتالوگ HF را نشان بده
            if (snapshot.baseUrl.contains("huggingface", ignoreCase = true) ||
                snapshot.baseUrl.contains("router.huggingface", ignoreCase = true)
            ) {
                list.addAll(textModels())
            } else {
                // کلید سفارشی (مثل Gemini)
                list.add(customFromSnapshot(snapshot))
            }
        }
        return list
    }

    fun modelsForProvider(provider: AiProviderKind): List<CatalogModel> {
        return textModels().filter { it.provider == provider }
    }

    /** ساخت مدل پویا از تنظیمات کلید ۱ */
    fun customFromSnapshot(snapshot: com.example.data.local.ApiKeySnapshot): CatalogModel {
        val modelId = snapshot.modelName.ifBlank { "custom-model" }
        val shortTitle = modelId.substringAfterLast('/').take(40)
        return CatalogModel(
            id = "custom-slot1",
            title = shortTitle,
            subtitle = snapshot.baseUrl,
            provider = AiProviderKind.CUSTOM,
            apiModelId = modelId,
            badge = "سفارشی",
            customBaseUrl = snapshot.baseUrl
        )
    }

    fun fromKeyStore(keyStore: com.example.data.local.ApiKeyStore): CatalogModel {
        val snapshot = keyStore.snapshot()
        if (!snapshot.huggingFaceConfigured && !snapshot.apiKey2Configured) {
            return noneConfigured
        }
        // اولویت با کلید ۱
        if (snapshot.huggingFaceConfigured) {
            val isHf = snapshot.baseUrl.contains("huggingface", ignoreCase = true)
            return if (isHf) {
                // سعی کن مدل کاتالوگ را پیدا کنی، وگرنه سفارشی بساز
                all.find { it.apiModelId == snapshot.modelName } ?: customFromSnapshot(snapshot)
            } else {
                customFromSnapshot(snapshot)
            }
        }
        // فقط کلید ۲
        return CatalogModel(
            id = "custom-slot2",
            title = snapshot.modelName2.substringAfterLast('/').take(40),
            subtitle = snapshot.baseUrl2,
            provider = AiProviderKind.CUSTOM,
            apiModelId = snapshot.modelName2,
            badge = "کلید ۲",
            customBaseUrl = snapshot.baseUrl2
        )
    }
}
