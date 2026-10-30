package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.AiProviderKind

class ApiKeyStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getHuggingFaceKey(): String = prefs.getString(KEY_HF, "")?.trim().orEmpty()

    fun getUserEmail(): String = prefs.getString(KEY_USER_EMAIL, "mrfokolaii@gmail.com")?.trim().orEmpty()
    fun setUserEmail(value: String) = prefs.edit().putString(KEY_USER_EMAIL, value.trim()).apply()

    fun isAccessibilityEnabled(): Boolean = prefs.getBoolean(KEY_ACCESSIBILITY_ENABLED, false)
    fun setAccessibilityEnabled(value: Boolean) = prefs.edit().putBoolean(KEY_ACCESSIBILITY_ENABLED, value).apply()

    fun setHuggingFaceKey(value: String) = prefs.edit().putString(KEY_HF, value.trim()).apply()

    fun keyFor(provider: AiProviderKind): String = when (provider) {
        AiProviderKind.HUGGINGFACE -> getHuggingFaceKey()
        AiProviderKind.BUILTIN_FREE -> "FREE"
    }

    fun hasAnyKey(): Boolean = getHuggingFaceKey().isNotBlank()

    fun snapshot(): ApiKeySnapshot = ApiKeySnapshot(
        huggingFaceConfigured = getHuggingFaceKey().isNotBlank()
    )

    companion object {
        private const val PREFS_NAME = "ai_keys"
        private const val KEY_HF = "huggingface"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_ACCESSIBILITY_ENABLED = "accessibility_enabled"
    }
}

data class ApiKeySnapshot(
    val huggingFaceConfigured: Boolean = false
) {
    fun isConfigured(provider: AiProviderKind): Boolean = when (provider) {
        AiProviderKind.HUGGINGFACE -> huggingFaceConfigured
        AiProviderKind.BUILTIN_FREE -> true
    }

    fun hasAny(): Boolean = huggingFaceConfigured
}
