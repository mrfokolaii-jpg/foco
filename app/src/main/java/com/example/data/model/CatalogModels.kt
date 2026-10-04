package com.example.data.model

/**
 * سرویس‌های هوش مصنوعیِ اپ.
 *
 * فقط سه چیز باقی مانده: کلید جمینای، کلید Hugging Face، و مدل آفلاین روی خود گوشی.
 * بقیهٔ سرویس‌ها (Groq، OpenRouter، xAI، 9Router و کلیدهای دستی) کامل حذف شدند؛
 * کلیدهایشان هم هنگام باز شدن اپ از حافظه پاک می‌شود.
 */
enum class AiProviderKind(val persianTitle: String, val badgeColorHex: Long) {
    /** «هیچ سرویسی وصل نیست» — هیچ درخواستی به هیچ سرویسی فرستاده نمی‌شود. */
    NONE("بدون سرویس", 0xFF94A3B8),

    GEMINI("Google Gemini", 0xFF2563EB),
    HUGGINGFACE("Hugging Face", 0xFFFFD21E),

    /** مدلی که کاربر از حافظهٔ گوشی می‌آورد؛ روی خود گوشی اجرا می‌شود، بی‌اینترنت. */
    OFFLINE("آفلاین", 0xFF10B981)
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
    /**
     * حالت «وصل نشده». هیچ هوش مصنوعی داخلی و بی‌کلیدی وجود ندارد؛ این مدل در فهرست
     * انتخاب مدل دیده نمی‌شود و فقط تا وقتی کاربر چیزی وصل نکرده، جای مدل انتخابی را
     * نگه می‌دارد تا یک پیام روشن نشان داده شود.
     */
    val notConnected = CatalogModel(
        id = "not-connected",
        title = "هیچ سرویسی وصل نیست",
        subtitle = "کلید جمینای، کلید Hugging Face یا یک مدل آفلاین اضافه کن",
        provider = AiProviderKind.NONE,
        apiModelId = "",
        badge = "—",
        isRecommended = false
    )

    /** Prefix for a model fetched live from the provider with the user's own key. */
    const val LIVE_ID_PREFIX = "live:"

    /**
     * مدلی که مستقیم از فهرست خودِ سرویس با کلید کاربر خوانده شده است.
     *
     * هیچ نامی حدس زده نمی‌شود: [apiModelId] عیناً همان چیزی است که سرور برگردانده،
     * پس دیگر پیش نمی‌آید که اپ مدلی را صدا بزند که کلید کاربر به آن دسترسی ندارد.
     */
    fun live(provider: AiProviderKind, modelId: String): CatalogModel = CatalogModel(
        id = LIVE_ID_PREFIX + provider.name + ":" + modelId,
        title = modelId,
        subtitle = modelId,
        provider = provider,
        apiModelId = modelId,
        badge = provider.persianTitle
    )

    /** Prefix for a model that runs locally, straight from a file on the phone. */
    const val OFFLINE_ID_PREFIX = "offline:"

    /** مدل محلی. [displayName] نام فایل GGUF است و [path] مسیر واقعی همان فایل. */
    fun offline(displayName: String, path: String): CatalogModel = CatalogModel(
        id = OFFLINE_ID_PREFIX + path,
        title = displayName,
        subtitle = "روی همین گوشی اجرا می‌شود — بدون اینترنت",
        provider = AiProviderKind.OFFLINE,
        apiModelId = path,
        badge = "آفلاین",
        isRecommended = true
    )

    /** Prefix for a Gemini model that came straight from the user's own key. */
    const val GEMINI_ID_PREFIX = "gemini:"

    /**
     * A Gemini model discovered live from the user's key. Nothing is pre-stored — [modelId]
     * is exactly the id Google returned for that key.
     *
     * [title] is only a prettier rendering of that same id (`gemini-3.5-flash-lite` shows as
     * «3.5 Flash Lite») و شناسهٔ خام در زیرنویس می‌آید.
     */
    fun gemini(modelId: String): CatalogModel = CatalogModel(
        id = GEMINI_ID_PREFIX + modelId,
        title = prettyGeminiLabel(modelId),
        subtitle = modelId,
        provider = AiProviderKind.GEMINI,
        apiModelId = modelId,
        badge = "Gemini"
    )

    /**
     * شناسهٔ خام مدل را به برچسب خوانا تبدیل می‌کند — فقط برای نمایش.
     * شناسهٔ واقعی مدل (`apiModelId`) هرگز تغییر نمی‌کند.
     *
     *   gemini-3.5-flash-lite            →  3.5 Flash Lite
     *   gemini-3.1-pro                   →  3.1 Pro
     *   gemini-1.5-flash-8b              →  1.5 Flash 8B
     *   gemini-2.5-pro-preview-04-2026   →  2.5 Pro Preview 04-2026
     */
    private fun prettyGeminiLabel(modelId: String): String {
        val tokens = modelId.removePrefix("gemini-")
            .split("-")
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return modelId

        val numberRegex = Regex("""\d+(\.\d+)?""")
        val isNumber = { value: String -> numberRegex.matches(value) }

        val out = StringBuilder()
        tokens.forEachIndexed { index, raw ->
            val token = when {
                raw.matches(Regex("""\d+[bB]""")) -> raw.uppercase()   // 8b → 8B
                isNumber(raw) -> raw
                else -> raw.replaceFirstChar { it.uppercase() }
            }
            if (index == 0) {
                out.append(token)
            } else {
                // دو عدد پشت‌سرهم مثل 04 و 2026 با خط تیره به هم می‌چسبند: 04-2026
                val joinWithDash = isNumber(raw) && isNumber(tokens[index - 1])
                out.append(if (joinWithDash) "-" else " ").append(token)
            }
        }
        return out.toString()
    }

    // ---- مدل‌های پیش‌فرض (فقط به‌عنوان جایگزین وقتی فهرست زنده خوانده نشود) ----

    val hfQwen25 = CatalogModel(
        id = "hf-qwen25",
        title = "Qwen/Qwen2.5-72B-Instruct",
        subtitle = "کیوون ۷۲ میلیارد",
        provider = AiProviderKind.HUGGINGFACE,
        apiModelId = "Qwen/Qwen2.5-72B-Instruct",
        badge = "Hugging Face"
    )

    val hfQwenCoder = CatalogModel(
        id = "hf-qwencoder",
        title = "Qwen/Qwen2.5-Coder-32B-Instruct",
        subtitle = "کیوون کدنویسی",
        provider = AiProviderKind.HUGGINGFACE,
        apiModelId = "Qwen/Qwen2.5-Coder-32B-Instruct",
        badge = "Hugging Face"
    )

    val hfLlama31 = CatalogModel(
        id = "hf-llama31",
        title = "meta-llama/Llama-3.1-8B-Instruct",
        subtitle = "لاما ۳.۱ هشت میلیارد",
        provider = AiProviderKind.HUGGINGFACE,
        apiModelId = "meta-llama/Llama-3.1-8B-Instruct",
        badge = "Hugging Face"
    )

    val geminiFlash = CatalogModel(
        id = "gemini-flash",
        title = "جمینای فلش",
        subtitle = "gemini-flash",
        provider = AiProviderKind.GEMINI,
        apiModelId = "gemini-flash",
        badge = "Gemini",
        capability = ModelCapability.VISION
    )

    val geminiPro = CatalogModel(
        id = "gemini-pro",
        title = "جمینای پرو",
        subtitle = "gemini-pro",
        provider = AiProviderKind.GEMINI,
        apiModelId = "gemini-pro",
        badge = "Gemini",
        capability = ModelCapability.VISION
    )

    /** فهرست مدل‌های از پیش تعریف‌شده — [notConnected] عمداً این‌جا نیست. */
    val all: List<CatalogModel> = listOf(
        hfQwen25,
        hfQwenCoder,
        hfLlama31,
        geminiFlash,
        geminiPro
    )

    fun defaultModel(): CatalogModel = notConnected

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
