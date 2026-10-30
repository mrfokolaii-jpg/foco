package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.AiProviderKind

class ApiKeyStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---- Slot 1 (اصلی) ----------------------------------------------------
    fun getHuggingFaceKey(): String = prefs.getString(KEY_HF, "")?.trim().orEmpty()
    fun setHuggingFaceKey(value: String) = prefs.edit().putString(KEY_HF, value.trim()).apply()

    fun getBaseUrl(): String = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL)?.trim().orEmpty()
    fun setBaseUrl(value: String) = prefs.edit().putString(KEY_BASE_URL, value.trim().ifBlank { DEFAULT_BASE_URL }).apply()

    fun getModelName(): String = prefs.getString(KEY_MODEL, DEFAULT_MODEL)?.trim().orEmpty()
    fun setModelName(value: String) = prefs.edit().putString(KEY_MODEL, value.trim().ifBlank { DEFAULT_MODEL }).apply()

    // ---- Slot 2 (اضافی) ---------------------------------------------------
    fun getApiKey2(): String = prefs.getString(KEY_API2, "")?.trim().orEmpty()
    fun setApiKey2(value: String) = prefs.edit().putString(KEY_API2, value.trim()).apply()

    fun getBaseUrl2(): String = prefs.getString(KEY_BASE_URL2, DEFAULT_BASE_URL2)?.trim().orEmpty()
    fun setBaseUrl2(value: String) = prefs.edit().putString(KEY_BASE_URL2, value.trim().ifBlank { DEFAULT_BASE_URL2 }).apply()

    fun getModelName2(): String = prefs.getString(KEY_MODEL2, DEFAULT_MODEL2)?.trim().orEmpty()
    fun setModelName2(value: String) = prefs.edit().putString(KEY_MODEL2, value.trim().ifBlank { DEFAULT_MODEL2 }).apply()

    // ---- سایر -------------------------------------------------------------
    fun getUserEmail(): String = prefs.getString(KEY_USER_EMAIL, "mrfokolaii@gmail.com")?.trim().orEmpty()
    fun setUserEmail(value: String) = prefs.edit().putString(KEY_USER_EMAIL, value.trim()).apply()

    fun isAccessibilityEnabled(): Boolean = prefs.getBoolean(KEY_ACCESSIBILITY_ENABLED, false)
    fun setAccessibilityEnabled(value: Boolean) = prefs.edit().putBoolean(KEY_ACCESSIBILITY_ENABLED, value).apply()

    fun keyFor(provider: AiProviderKind): String = when (provider) {
        AiProviderKind.HUGGINGFACE -> getHuggingFaceKey()
    }

    fun hasAnyKey(): Boolean = getHuggingFaceKey().isNotBlank() || getApiKey2().isNotBlank()

    fun snapshot(): ApiKeySnapshot = ApiKeySnapshot(
        huggingFaceConfigured = getHuggingFaceKey().isNotBlank(),
        baseUrl = getBaseUrl(),
        modelName = getModelName(),
        apiKey2Configured = getApiKey2().isNotBlank(),
        baseUrl2 = getBaseUrl2(),
        modelName2 = getModelName2()
    )

    companion object {
        private const val PREFS_NAME = "ai_keys"
        private const val KEY_HF = "huggingface"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_MODEL = "model_name"
        private const val KEY_API2 = "api_key_2"
        private const val KEY_BASE_URL2 = "base_url_2"
        private const val KEY_MODEL2 = "model_name_2"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_ACCESSIBILITY_ENABLED = "accessibility_enabled"

        const val DEFAULT_BASE_URL = "https://router.huggingface.co/v1"
        const val DEFAULT_MODEL = "Qwen/Qwen2.5-7B-Instruct"
        const val DEFAULT_BASE_URL2 = "https://api.openai.com/v1"
        const val DEFAULT_MODEL2 = "gpt-4o-mini"
    }
}

data class ApiKeySnapshot(
    val huggingFaceConfigured: Boolean = false,
    val baseUrl: String = ApiKeyStore.DEFAULT_BASE_URL,
    val modelName: String = ApiKeyStore.DEFAULT_MODEL,
    val apiKey2Configured: Boolean = false,
    val baseUrl2: String = ApiKeyStore.DEFAULT_BASE_URL2,
    val modelName2: String = ApiKeyStore.DEFAULT_MODEL2
) {
    fun isConfigured(provider: AiProviderKind): Boolean = when (provider) {
        AiProviderKind.HUGGINGFACE -> huggingFaceConfigured
    }

    fun hasAny(): Boolean = huggingFaceConfigured || apiKey2Configured
}
