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
            snapshot.huggingFaceConfigured -> AiModelCatalog.hfQwen25
            snapshot.xaiGrokConfigured -> AiModelCatalog.xaiGrok2
            snapshot.openRouterConfigured -> AiModelCatalog.orLlamaFree
            snapshot.groqConfigured -> AiModelCatalog.groqLlama33
            snapshot.geminiConfigured -> AiModelCatalog.geminiFlash
            else -> AiModelCatalog.builtinFree
        }
    }
}
