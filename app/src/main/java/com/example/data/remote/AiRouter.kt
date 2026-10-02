package com.example.data.remote

import com.example.data.local.ApiKeyStore
import com.example.data.model.AiModelCatalog
import com.example.data.model.AiModelOption
import com.example.data.model.AiProviderKind
import com.example.data.model.CatalogModel
import com.example.data.model.ChatMessage

object AiRouter {
    suspend fun chat(
        catalogModel: CatalogModel,
        conversationHistory: List<ChatMessage>,
        systemInstructionText: String,
        keyStore: ApiKeyStore,
        enableThinkingMode: Boolean = false,
        enableGoogleSearch: Boolean = false
    ): ApiResult<ChatApiResponse> {
        val key = keyStore.keyFor(catalogModel.provider)

        // Inject live web search if requested
        var effectiveSystemPrompt = systemInstructionText
        if (enableGoogleSearch && conversationHistory.isNotEmpty()) {
            val lastUserQuery = conversationHistory.lastOrNull { it.sender.name == "USER" }?.text.orEmpty()
            if (lastUserQuery.isNotBlank()) {
                val searchSnippets = WebSearchProvider.searchLive(lastUserQuery)
                if (searchSnippets.isNotBlank()) {
                    effectiveSystemPrompt += "\n\n[اطلاعات زنده دریافت شده از وب]:\n$searchSnippets"
                }
            }
        }

        return when (catalogModel.provider) {
            AiProviderKind.BUILTIN_FREE -> {
                PollinationsFreeProvider.chat(
                    conversationHistory = conversationHistory,
                    systemInstructionText = effectiveSystemPrompt
                )
            }
            AiProviderKind.HUGGINGFACE -> {
                if (key.isBlank()) return PollinationsFreeProvider.chat(conversationHistory, effectiveSystemPrompt)
                HuggingFaceProvider.chat(
                    apiKey = key,
                    apiModelId = catalogModel.apiModelId,
                    catalogId = catalogModel.id,
                    conversationHistory = conversationHistory,
                    systemInstructionText = effectiveSystemPrompt
                )
            }
            AiProviderKind.XAI_GROK -> {
                if (key.isBlank()) return PollinationsFreeProvider.chat(conversationHistory, effectiveSystemPrompt)
                XaiGrokProvider.chat(
                    apiKey = key,
                    apiModelId = catalogModel.apiModelId,
                    catalogId = catalogModel.id,
                    conversationHistory = conversationHistory,
                    systemInstructionText = effectiveSystemPrompt
                )
            }
            AiProviderKind.OPENROUTER -> {
                if (key.isBlank()) return PollinationsFreeProvider.chat(conversationHistory, effectiveSystemPrompt)
                OpenRouterProvider.chat(
                    apiKey = key,
                    apiModelId = catalogModel.apiModelId,
                    catalogId = catalogModel.id,
                    conversationHistory = conversationHistory,
                    systemInstructionText = effectiveSystemPrompt
                )
            }
            AiProviderKind.GROQ -> {
                if (key.isBlank()) return PollinationsFreeProvider.chat(conversationHistory, effectiveSystemPrompt)
                GroqProvider.chat(
                    apiKey = key,
                    apiModelId = catalogModel.apiModelId,
                    catalogId = catalogModel.id,
                    conversationHistory = conversationHistory,
                    systemInstructionText = effectiveSystemPrompt
                )
            }
            AiProviderKind.NINE_ROUTER -> {
                // 9Router is OpenAI-compatible; its combos do the model rotation server-side.
                OpenAiCompatProvider.chat(
                    endpoint = "${keyStore.getNineRouterBaseUrl().trimEnd('/')}/chat/completions",
                    apiKey = key,
                    apiModelId = catalogModel.apiModelId,
                    catalogId = catalogModel.id,
                    conversationHistory = conversationHistory,
                    systemInstructionText = effectiveSystemPrompt
                )
            }
            AiProviderKind.CUSTOM -> {
                // Resolve the exact provider this catalog entry points at, so several
                // hand-wired services can coexist and each keeps its own key.
                val provider = keyStore.customProviderById(
                    AiModelCatalog.customProviderIdOf(catalogModel.id)
                ) ?: keyStore.primaryCustomProvider()

                if (provider == null || provider.baseUrl.isBlank()) {
                    ApiResult.Error("این سرویس دستی حذف شده است؛ از تنظیمات دوباره اضافه‌اش کن.")
                } else {
                    val base = provider.baseUrl.trimEnd('/')
                    val endpoint =
                        if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
                    OpenAiCompatProvider.chat(
                        endpoint = endpoint,
                        apiKey = provider.apiKey.ifBlank { key },
                        apiModelId = catalogModel.apiModelId.ifBlank { provider.model },
                        catalogId = catalogModel.id,
                        conversationHistory = conversationHistory,
                        systemInstructionText = effectiveSystemPrompt
                    )
                }
            }
            AiProviderKind.GEMINI -> {
                if (key.isBlank()) return PollinationsFreeProvider.chat(conversationHistory, effectiveSystemPrompt)
                GeminiApiService.runtimeApiKeyOverride = key
                val option = AiModelCatalog.toGeminiOption(catalogModel) ?: AiModelOption.FLASH
                GeminiApiService.generateChatResponse(
                    conversationHistory = conversationHistory,
                    systemInstructionText = effectiveSystemPrompt,
                    selectedModel = option,
                    enableThinkingMode = enableThinkingMode,
                    enableGoogleSearch = enableGoogleSearch
                )
            }
        }
    }

    fun pickDefault(keyStore: ApiKeyStore): CatalogModel {
        val snapshot = keyStore.snapshot()
        return when {
            // 9Router first: it already rotates the free models for us.
            snapshot.nineRouterConfigured -> AiModelCatalog.nineRouter(keyStore.getNineRouterModel())
            snapshot.customConfigured -> keyStore.primaryCustomProvider()?.let { AiModelCatalog.custom(it) }
                ?: AiModelCatalog.builtinFree
            snapshot.geminiConfigured -> AiModelCatalog.geminiFlash
            snapshot.huggingFaceConfigured -> AiModelCatalog.hfQwen25
            snapshot.xaiGrokConfigured -> AiModelCatalog.xaiGrok2
            snapshot.openRouterConfigured -> AiModelCatalog.orLlamaFree
            snapshot.groqConfigured -> AiModelCatalog.groqLlama33
            else -> AiModelCatalog.builtinFree
        }
    }

    /** Every model the user can currently reach, best first — the rotation order for Auto. */
    fun rotationChain(keyStore: ApiKeyStore): List<CatalogModel> {
        val snapshot = keyStore.snapshot()
        val chain = mutableListOf<CatalogModel>()
        if (snapshot.nineRouterConfigured) chain.add(AiModelCatalog.nineRouter(keyStore.getNineRouterModel()))
        // Every hand-wired provider takes part, in the order they were added.
        if (snapshot.customConfigured) {
            chain.addAll(AiModelCatalog.customModels(keyStore.getCustomProviders()))
        }
        if (snapshot.geminiConfigured) {
            chain.add(AiModelCatalog.geminiFlash)
            chain.add(AiModelCatalog.geminiPro)
        }
        if (snapshot.huggingFaceConfigured) {
            chain.add(AiModelCatalog.hfQwen25)
            chain.add(AiModelCatalog.hfQwenCoder)
            chain.add(AiModelCatalog.hfDeepseekR1)
            chain.add(AiModelCatalog.hfLlama31)
        }
        if (snapshot.xaiGrokConfigured) {
            chain.add(AiModelCatalog.xaiGrok2)
            chain.add(AiModelCatalog.xaiGrokBeta)
        }
        if (snapshot.groqConfigured) chain.add(AiModelCatalog.groqLlama33)
        if (snapshot.openRouterConfigured) {
            chain.add(AiModelCatalog.orLlamaFree)
            chain.add(AiModelCatalog.orDeepseekFree)
            chain.add(AiModelCatalog.orQwenFree)
        }
        // Last resort so Auto never runs out of options.
        chain.add(AiModelCatalog.builtinFree)
        return chain.distinctBy { it.id }
    }
}
