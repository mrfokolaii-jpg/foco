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

    // ---- 9Router: a self-hosted OpenAI-compatible router that rotates free models ----

    fun getNineRouterBaseUrl(): String =
        prefs.getString(KEY_9R_BASE, DEFAULT_9ROUTER_BASE_URL)?.trim().orEmpty()

    fun getNineRouterKey(): String = prefs.getString(KEY_9R_KEY, "")?.trim().orEmpty()
    fun getNineRouterModel(): String = prefs.getString(KEY_9R_MODEL, "")?.trim().orEmpty()

    fun isNineRouterEnabled(): Boolean = prefs.getBoolean(KEY_9R_ENABLED, false)

    fun setNineRouterEnabled(value: Boolean) = prefs.edit().putBoolean(KEY_9R_ENABLED, value).apply()

    fun setNineRouter(baseUrl: String, key: String, model: String) = prefs.edit()
        .putString(KEY_9R_BASE, baseUrl.trim().ifBlank { DEFAULT_9ROUTER_BASE_URL })
        .putString(KEY_9R_KEY, key.trim())
        .putString(KEY_9R_MODEL, model.trim())
        .putBoolean(KEY_9R_ENABLED, true)
        .apply()

    // ---- Manual provider: any site, its own base URL, key and model ----

    fun getCustomLabel(): String = prefs.getString(KEY_CUSTOM_LABEL, "")?.trim().orEmpty()
    fun getCustomBaseUrl(): String = prefs.getString(KEY_CUSTOM_BASE, "")?.trim().orEmpty()
    fun getCustomKey(): String = prefs.getString(KEY_CUSTOM_KEY, "")?.trim().orEmpty()
    fun getCustomModel(): String = prefs.getString(KEY_CUSTOM_MODEL, "")?.trim().orEmpty()

    fun setCustom(label: String, baseUrl: String, key: String, model: String) = prefs.edit()
        .putString(KEY_CUSTOM_LABEL, label.trim())
        .putString(KEY_CUSTOM_BASE, baseUrl.trim())
        .putString(KEY_CUSTOM_KEY, key.trim())
        .putString(KEY_CUSTOM_MODEL, model.trim())
        .apply()

    fun keyFor(provider: AiProviderKind): String = when (provider) {
        AiProviderKind.GROQ -> getGroqKey()
        AiProviderKind.XAI_GROK -> getXaiGrokKey()
        AiProviderKind.HUGGINGFACE -> getHuggingFaceKey()
        AiProviderKind.OPENROUTER -> getOpenRouterKey()
        AiProviderKind.GEMINI -> getGeminiKey()
        AiProviderKind.NINE_ROUTER -> getNineRouterKey()
        AiProviderKind.CUSTOM -> getCustomKey()
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
        geminiConfigured = getGeminiKey().isNotBlank(),
        nineRouterConfigured = isNineRouterEnabled() && getNineRouterBaseUrl().isNotBlank(),
        customConfigured = getCustomBaseUrl().isNotBlank() && getCustomModel().isNotBlank()
    )

    companion object {
        /** Where a 9Router instance listens by default. Change it to your server/LAN address. */
        const val DEFAULT_9ROUTER_BASE_URL = "http://localhost:20128/v1"

        private const val PREFS_NAME = "ai_keys"
        private const val KEY_GROQ = "groq"
        private const val KEY_XAI_GROK = "xai_grok"
        private const val KEY_HF = "huggingface"
        private const val KEY_OPENROUTER = "openrouter"
        private const val KEY_GEMINI = "gemini"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_ACCESSIBILITY_ENABLED = "accessibility_enabled"
        private const val KEY_9R_ENABLED = "nine_router_enabled"
        private const val KEY_9R_BASE = "nine_router_base"
        private const val KEY_9R_KEY = "nine_router_key"
        private const val KEY_9R_MODEL = "nine_router_model"
        private const val KEY_CUSTOM_LABEL = "custom_label"
        private const val KEY_CUSTOM_BASE = "custom_base"
        private const val KEY_CUSTOM_KEY = "custom_key"
        private const val KEY_CUSTOM_MODEL = "custom_model"
    }
}

data class ApiKeySnapshot(
    val xaiGrokConfigured: Boolean = false,
    val groqConfigured: Boolean = false,
    val huggingFaceConfigured: Boolean = false,
    val openRouterConfigured: Boolean = false,
    val geminiConfigured: Boolean = false,
    val nineRouterConfigured: Boolean = false,
    val customConfigured: Boolean = false
) {
    fun isConfigured(provider: AiProviderKind): Boolean = when (provider) {
        AiProviderKind.XAI_GROK -> xaiGrokConfigured
        AiProviderKind.GROQ -> groqConfigured
        AiProviderKind.HUGGINGFACE -> huggingFaceConfigured
        AiProviderKind.OPENROUTER -> openRouterConfigured
        AiProviderKind.GEMINI -> geminiConfigured
        AiProviderKind.NINE_ROUTER -> nineRouterConfigured
        AiProviderKind.CUSTOM -> customConfigured
        AiProviderKind.BUILTIN_FREE -> true
    }

    fun hasAny(): Boolean =
        xaiGrokConfigured || groqConfigured || huggingFaceConfigured ||
            openRouterConfigured || geminiConfigured || nineRouterConfigured || customConfigured
}
