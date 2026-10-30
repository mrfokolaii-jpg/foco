package com.example.data.remote

import com.example.data.local.ApiKeyStore
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
        }
    }

    fun pickDefault(keyStore: ApiKeyStore): CatalogModel {
        val snapshot = keyStore.snapshot()
        return when {
            snapshot.huggingFaceConfigured -> com.example.data.model.AiModelCatalog.hfQwen25
            else -> com.example.data.model.AiModelCatalog.builtinFree
        }
    }
}
