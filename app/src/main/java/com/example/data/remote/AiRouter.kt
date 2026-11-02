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
            AiProviderKind.HUGGINGFACE -> {
                val key = keyStore.getHuggingFaceKey()
                if (key.isBlank()) {
                    return ApiResult.Error("کلید API تنظیم نشده است. لطفاً از بخش تنظیمات کلید را وارد کنید.")
                }
                HuggingFaceProvider.chat(
                    apiKey = key,
                    apiModelId = catalogModel.apiModelId,
                    catalogId = catalogModel.id,
                    conversationHistory = conversationHistory,
                    systemInstructionText = effectiveSystemPrompt
                )
            }
            AiProviderKind.CUSTOM -> {
                // کلید ۱ سفارشی (مثل Gemini) یا کلید ۲
                val isSlot2 = catalogModel.id == "custom-slot2"
                val key = if (isSlot2) keyStore.getApiKey2() else keyStore.getHuggingFaceKey()
                val baseUrl = catalogModel.customBaseUrl
                    ?: if (isSlot2) keyStore.getBaseUrl2() else keyStore.getBaseUrl()
                val modelId = catalogModel.apiModelId.ifBlank {
                    if (isSlot2) keyStore.getModelName2() else keyStore.getModelName()
                }

                if (key.isBlank()) {
                    return ApiResult.Error("کلید API تنظیم نشده است. لطفاً از بخش تنظیمات کلید را وارد کنید.")
                }
                if (baseUrl.isBlank() || modelId.isBlank()) {
                    return ApiResult.Error("Base URL یا نام مدل خالی است. لطفاً در تنظیمات کامل کنید.")
                }

                val endpoint = normalizeChatEndpoint(baseUrl)
                OpenAiCompatProvider.chat(
                    endpoint = endpoint,
                    apiKey = key,
                    apiModelId = modelId,
                    catalogId = catalogModel.id,
                    conversationHistory = conversationHistory,
                    systemInstructionText = effectiveSystemPrompt
                )
            }
        }
    }

    fun pickDefault(keyStore: ApiKeyStore): CatalogModel {
        return com.example.data.model.AiModelCatalog.fromKeyStore(keyStore)
    }

    /** اطمینان از اینکه URL به endpoint چت ختم شود */
    private fun normalizeChatEndpoint(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return when {
            trimmed.endsWith("/chat/completions") -> trimmed
            trimmed.endsWith("/v1") -> "$trimmed/chat/completions"
            trimmed.endsWith("/openai") -> "$trimmed/chat/completions"
            trimmed.contains("/v1beta/openai") -> "$trimmed/chat/completions"
            else -> "$trimmed/chat/completions"
        }
    }
}
