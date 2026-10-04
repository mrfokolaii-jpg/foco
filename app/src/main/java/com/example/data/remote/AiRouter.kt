package com.example.data.remote

import com.example.data.local.ApiKeyStore
import com.example.data.model.AiModelCatalog
import com.example.data.model.AiModelOption
import com.example.data.model.AiProviderKind
import com.example.data.model.CatalogModel
import com.example.data.model.ChatMessage
import com.example.data.offline.OfflineEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * تنها جایی که تصمیم گرفته می‌شود پیام کاربر به کدام هوش مصنوعی برود.
 *
 * سه مسیر وجود دارد: کلید جمینای، کلید Hugging Face، و مدل آفلاین روی خود گوشی.
 * اگر هیچ‌کدام وصل نباشد، به‌جای جواب ساختگی و به‌جای گفتن «سرور شلوغ است»، یک پیام
 * روشن گفته می‌شود که کاربر باید چه کار کند.
 */
object AiRouter {

    /**
     * ارسال پیام به مدل انتخاب‌شده.
     *
     * کل کار روی رشتهٔ ورودی/خروجی (IO) انجام می‌شود. پیش‌تر هیچ تضمینی نبود و یک
     * درخواست شبکه می‌توانست روی رشتهٔ اصلی بیفتد؛ اندروید در این حالت برنامه را با
     * خطای `NetworkOnMainThreadException` می‌بندد — دقیقاً همان چیزی که کاربر می‌دید:
     * پیام می‌فرستاد و از برنامه بیرون انداخته می‌شد.
     */
    suspend fun chat(
        catalogModel: CatalogModel,
        conversationHistory: List<ChatMessage>,
        systemInstructionText: String,
        keyStore: ApiKeyStore,
        enableThinkingMode: Boolean = false,
        enableGoogleSearch: Boolean = false
    ): ApiResult<ChatApiResponse> = withContext(Dispatchers.IO) {
        chatOnWorkerThread(
            catalogModel = catalogModel,
            conversationHistory = conversationHistory,
            systemInstructionText = systemInstructionText,
            keyStore = keyStore,
            enableThinkingMode = enableThinkingMode,
            enableGoogleSearch = enableGoogleSearch
        )
    }

    /**
     * بدنهٔ اصلی، جدا نوشته شده تا `return`های ساده مجاز باشند (بازگشتِ ساده داخل
     * بلوک `withContext` خطای کامپایل می‌دهد).
     */
    private suspend fun chatOnWorkerThread(
        catalogModel: CatalogModel,
        conversationHistory: List<ChatMessage>,
        systemInstructionText: String,
        keyStore: ApiKeyStore,
        enableThinkingMode: Boolean,
        enableGoogleSearch: Boolean
    ): ApiResult<ChatApiResponse> {
        val key = keyStore.keyFor(catalogModel.provider)

        // Inject live web search if requested
        var effectiveSystemPrompt = systemInstructionText
        if (enableGoogleSearch && conversationHistory.isNotEmpty()) {
            val lastUserQuery = conversationHistory.lastOrNull { it.sender.name == "USER" }?.text.orEmpty()
            if (lastUserQuery.isNotBlank()) {
                // اگر جستجوی وب شکست بخورد، فرستادن پیام نباید شکست بخورد.
                val searchSnippets = try {
                    WebSearchProvider.searchLive(lastUserQuery)
                } catch (_: Throwable) {
                    ""
                }
                if (searchSnippets.isNotBlank()) {
                    effectiveSystemPrompt += "\n\n[اطلاعات زنده دریافت شده از وب]:\n$searchSnippets"
                }
            }
        }

        return when (catalogModel.provider) {
            AiProviderKind.NONE -> notConnected()

            AiProviderKind.GEMINI -> {
                if (key.isBlank()) return missingKey(AiProviderKind.GEMINI)
                GeminiApiService.runtimeApiKeyOverride = key
                val option = AiModelCatalog.toGeminiOption(catalogModel) ?: AiModelOption.FLASH
                GeminiApiService.generateChatResponse(
                    conversationHistory = conversationHistory,
                    systemInstructionText = effectiveSystemPrompt,
                    selectedModel = option,
                    // مهم: شناسهٔ واقعی همان مدلی که کاربر انتخاب کرده به گوگل می‌رود.
                    modelIdOverride = catalogModel.apiModelId,
                    // اگر این مدل شلوغ بود یا برای این کلید بسته بود، روی مدل بعدیِ همین
                    // کلید می‌پرد. فهرست از خودِ گوگل و با همین کلید خوانده می‌شود.
                    fallbackModelIds = GeminiModels.ensure(key),
                    enableThinkingMode = enableThinkingMode,
                    enableGoogleSearch = enableGoogleSearch
                )
            }

            AiProviderKind.HUGGINGFACE -> {
                if (key.isBlank()) return missingKey(AiProviderKind.HUGGINGFACE)
                HuggingFaceProvider.chat(
                    apiKey = key,
                    apiModelId = catalogModel.apiModelId,
                    catalogId = catalogModel.id,
                    conversationHistory = conversationHistory,
                    systemInstructionText = effectiveSystemPrompt
                )
            }

            AiProviderKind.OFFLINE -> {
                // مسیر مدل روی گوشی است؛ اینترنت و کلید هیچ نقشی ندارد.
                val path = catalogModel.apiModelId.ifBlank { keyStore.getOfflineModelPath() }
                OfflineEngine.chat(
                    modelPath = path,
                    conversationHistory = conversationHistory,
                    systemInstructionText = effectiveSystemPrompt
                )
            }
        }
    }

    /** پیام روشن برای وقتی که کاربر هنوز چیزی وصل نکرده است. */
    private fun missingKey(provider: AiProviderKind): ApiResult.Error = ApiResult.Error(
        "کلید «${provider.persianTitle}» وارد نشده است. در «تنظیمات» کلیدش را بگذار."
    )

    private fun notConnected(): ApiResult.Error = ApiResult.Error(
        "هیچ هوش مصنوعی‌ای وصل نیست.\n" +
            "یکی از این کارها را بکن:\n" +
            "• در «تنظیمات» کلید جمینای را وارد کن\n" +
            "• یا یک مدل آفلاین (فایل GGUF) روی گوشی اضافه کن"
    )

    fun pickDefault(keyStore: ApiKeyStore): CatalogModel {
        val snapshot = keyStore.snapshot()
        return when {
            snapshot.geminiConfigured -> AiModelCatalog.geminiFlash
            snapshot.huggingFaceConfigured -> AiModelCatalog.hfQwen25
            snapshot.offlineConfigured -> AiModelCatalog.offline(
                displayName = keyStore.getOfflineModelName().ifBlank { "مدل آفلاین" },
                path = keyStore.getOfflineModelPath()
            )
            else -> AiModelCatalog.notConnected
        }
    }

    /** همهٔ مدل‌هایی که کاربر همین حالا می‌تواند استفاده کند — به‌ترتیب اولویت. */
    fun rotationChain(keyStore: ApiKeyStore): List<CatalogModel> {
        val snapshot = keyStore.snapshot()
        val chain = mutableListOf<CatalogModel>()
        if (snapshot.geminiConfigured) {
            chain.add(AiModelCatalog.geminiFlash)
            chain.add(AiModelCatalog.geminiPro)
        }
        if (snapshot.huggingFaceConfigured) {
            chain.add(AiModelCatalog.hfQwen25)
            chain.add(AiModelCatalog.hfQwenCoder)
            chain.add(AiModelCatalog.hfLlama31)
        }
        if (snapshot.offlineConfigured) {
            chain.add(
                AiModelCatalog.offline(
                    displayName = keyStore.getOfflineModelName().ifBlank { "مدل آفلاین" },
                    path = keyStore.getOfflineModelPath()
                )
            )
        }
        return chain.distinctBy { it.id }
    }
}
