package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import com.example.data.model.AiProviderKind

class ApiKeyStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getGroqKey(): String = prefs.getString(KEY_GROQ, "")?.trim().orEmpty()
    fun getXaiGrokKey(): String = prefs.getString(KEY_XAI_GROK, "")?.trim().orEmpty()
    fun getHuggingFaceKey(): String = prefs.getString(KEY_HF, "")?.trim().orEmpty()
    fun getOpenRouterKey(): String = prefs.getString(KEY_OPENROUTER, "")?.trim().orEmpty()
    fun getGeminiOverride(): String = prefs.getString(KEY_GEMINI, "")?.trim().orEmpty()

    fun getUserEmail(): String = prefs.getString(KEY_USER_EMAIL, "mrfokolaii@gmail.com")?.trim().orEmpty()
    fun setUserEmail(value: String) = prefs.edit().putString(KEY_USER_EMAIL, value.trim()).apply()

    fun isAccessibilityEnabled(): Boolean = prefs.getBoolean(KEY_ACCESSIBILITY_ENABLED, false)
    fun setAccessibilityEnabled(value: Boolean) = prefs.edit().putBoolean(KEY_ACCESSIBILITY_ENABLED, value).apply()

    fun getGeminiKey(): String {
        val override = getGeminiOverride()
        if (override.isNotBlank()) return override
        return try {
            val builtIn = BuildConfig.GEMINI_API_KEY
            if (builtIn.isBlank() || builtIn == "MY_GEMINI_API_KEY") "" else builtIn
        } catch (_: Throwable) {
            ""
        }
    }

    fun setGroqKey(value: String) = prefs.edit().putString(KEY_GROQ, value.trim()).apply()
    fun setXaiGrokKey(value: String) = prefs.edit().putString(KEY_XAI_GROK, value.trim()).apply()
    fun setHuggingFaceKey(value: String) = prefs.edit().putString(KEY_HF, value.trim()).apply()
    fun setOpenRouterKey(value: String) = prefs.edit().putString(KEY_OPENROUTER, value.trim()).apply()
    fun setGeminiKey(value: String) = prefs.edit().putString(KEY_GEMINI, value.trim()).apply()

    fun keyFor(provider: AiProviderKind): String = when (provider) {
        AiProviderKind.GROQ -> getGroqKey()
        AiProviderKind.XAI_GROK -> getXaiGrokKey()
        AiProviderKind.HUGGINGFACE -> getHuggingFaceKey()
        AiProviderKind.OPENROUTER -> getOpenRouterKey()
        AiProviderKind.GEMINI -> getGeminiKey()
        AiProviderKind.BUILTIN_FREE -> "FREE"
    }

    fun hasAnyKey(): Boolean =
        getXaiGrokKey().isNotBlank() ||
            getGroqKey().isNotBlank() ||
            getHuggingFaceKey().isNotBlank() ||
            getOpenRouterKey().isNotBlank() ||
            getGeminiKey().isNotBlank()

    fun snapshot(): ApiKeySnapshot = ApiKeySnapshot(
        xaiGrokConfigured = getXaiGrokKey().isNotBlank(),
        groqConfigured = getGroqKey().isNotBlank(),
        huggingFaceConfigured = getHuggingFaceKey().isNotBlank(),
        openRouterConfigured = getOpenRouterKey().isNotBlank(),
        geminiConfigured = getGeminiKey().isNotBlank()
    )

    companion object {
        private const val PREFS_NAME = "ai_keys"
        private const val KEY_GROQ = "groq"
        private const val KEY_XAI_GROK = "xai_grok"
        private const val KEY_HF = "huggingface"
        private const val KEY_OPENROUTER = "openrouter"
        private const val KEY_GEMINI = "gemini"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_ACCESSIBILITY_ENABLED = "accessibility_enabled"
    }
}

data class ApiKeySnapshot(
    val xaiGrokConfigured: Boolean = false,
    val groqConfigured: Boolean = false,
    val huggingFaceConfigured: Boolean = false,
    val openRouterConfigured: Boolean = false,
    val geminiConfigured: Boolean = false
) {
    fun isConfigured(provider: AiProviderKind): Boolean = when (provider) {
        AiProviderKind.XAI_GROK -> xaiGrokConfigured
        AiProviderKind.GROQ -> groqConfigured
        AiProviderKind.HUGGINGFACE -> huggingFaceConfigured
        AiProviderKind.OPENROUTER -> openRouterConfigured
        AiProviderKind.GEMINI -> geminiConfigured
        AiProviderKind.BUILTIN_FREE -> true
    }

    fun hasAny(): Boolean =
        xaiGrokConfigured || groqConfigured || huggingFaceConfigured || openRouterConfigured || geminiConfigured
}
