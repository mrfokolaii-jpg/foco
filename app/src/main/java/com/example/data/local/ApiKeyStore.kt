package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.AiProviderKind
import java.io.File

/**
 * کلیدها و تنظیمات محلی اپ.
 *
 * فقط کلید جمینای، کلید Hugging Face و مدل آفلاین این‌جا نگه داشته می‌شوند. کلیدهای
 * Groq / OpenRouter / xAI / 9Router و سرویس‌های دستی حذف شده‌اند و با
 * [purgeRemovedProviders] یک‌بار برای همیشه از حافظه پاک می‌شوند.
 */
class ApiKeyStore(context: Context) {
    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---- Google Gemini -------------------------------------------------------

    /** کلید جمینای فقط از ورودی دستی خود کاربر می‌آید؛ هیچ کلید پیش‌فرضی در اپ نیست. */
    fun getGeminiKey(): String = prefs.getString(KEY_GEMINI, "")?.trim().orEmpty()
    fun setGeminiKey(value: String) = prefs.edit().putString(KEY_GEMINI, value.trim()).apply()

    // ---- Hugging Face -------------------------------------------------------

    fun getHuggingFaceKey(): String = prefs.getString(KEY_HF, "")?.trim().orEmpty()
    fun setHuggingFaceKey(value: String) = prefs.edit().putString(KEY_HF, value.trim()).apply()

    // ---- کاربر و دسترسی‌ها ----------------------------------------------------

    fun getUserEmail(): String = prefs.getString(KEY_USER_EMAIL, "")?.trim().orEmpty()
    fun setUserEmail(value: String) = prefs.edit().putString(KEY_USER_EMAIL, value.trim()).apply()

    fun isAccessibilityEnabled(): Boolean = prefs.getBoolean(KEY_ACCESSIBILITY_ENABLED, false)
    fun setAccessibilityEnabled(value: Boolean) =
        prefs.edit().putBoolean(KEY_ACCESSIBILITY_ENABLED, value).apply()

    // ---- مدل آفلاین (روی خود گوشی) -------------------------------------------

    /** مسیر فایل GGUF روی حافظه. */
    fun getOfflineModelPath(): String = prefs.getString(KEY_OFFLINE_PATH, "")?.trim().orEmpty()

    /** نام نمایشی مدل آفلاین (نام فایل). */
    fun getOfflineModelName(): String = prefs.getString(KEY_OFFLINE_NAME, "")?.trim().orEmpty()

    fun setOfflineModel(path: String, name: String) = prefs.edit()
        .putString(KEY_OFFLINE_PATH, path.trim())
        .putString(KEY_OFFLINE_NAME, name.trim())
        .apply()

    fun clearOfflineModel() = prefs.edit()
        .remove(KEY_OFFLINE_PATH)
        .remove(KEY_OFFLINE_NAME)
        .apply()

    /** مدل آفلاین وقتی «وصل» حساب می‌شود که فایلش واقعاً روی حافظه باشد. */
    fun hasUsableOfflineModel(): Boolean {
        val path = getOfflineModelPath()
        return path.isNotBlank() && File(path).exists()
    }

    // ---- Voice (TTS) selection ------------------------------------------------

    /** Engine voice name the user picked (empty = let the engine choose). */
    fun getTtsVoice(): String = prefs.getString(KEY_TTS_VOICE, "")?.trim().orEmpty()
    fun setTtsVoice(name: String) = prefs.edit().putString(KEY_TTS_VOICE, name.trim()).apply()

    fun getTtsPitch(): Float = prefs.getFloat(KEY_TTS_PITCH, 0.85f)
    fun setTtsPitch(value: Float) = prefs.edit().putFloat(KEY_TTS_PITCH, value).apply()

    fun getTtsRate(): Float = prefs.getFloat(KEY_TTS_RATE, 1.0f)
    fun setTtsRate(value: Float) = prefs.edit().putFloat(KEY_TTS_RATE, value).apply()

    /** Custom display names so the user can rename voices (\"آقا امیر\" instead of \"fa-ir-x-...\"). */
    fun getVoiceLabels(): Map<String, String> = try {
        val raw = prefs.getString(KEY_TTS_LABELS, "{}") ?: "{}"
        val json = org.json.JSONObject(raw)
        json.keys().asSequence().associateWith { json.optString(it) }
    } catch (_: Exception) {
        emptyMap()
    }

    fun setVoiceLabel(voiceName: String, label: String) {
        try {
            val json = org.json.JSONObject(prefs.getString(KEY_TTS_LABELS, "{}") ?: "{}")
            if (label.isBlank()) json.remove(voiceName) else json.put(voiceName, label.trim())
            prefs.edit().putString(KEY_TTS_LABELS, json.toString()).apply()
        } catch (_: Exception) {
        }
    }

    // ---- وضعیت --------------------------------------------------------------

    fun keyFor(provider: AiProviderKind): String = when (provider) {
        AiProviderKind.GEMINI -> getGeminiKey()
        AiProviderKind.HUGGINGFACE -> getHuggingFaceKey()
        // مدل آفلاین کلید نمی‌خواهد؛ مسیر فایلش مدلِ استفاده‌شده است.
        AiProviderKind.OFFLINE -> getOfflineModelPath()
        AiProviderKind.NONE -> ""
    }

    fun hasAnyKey(): Boolean =
        getGeminiKey().isNotBlank() ||
            getHuggingFaceKey().isNotBlank() ||
            hasUsableOfflineModel()

    fun snapshot(): ApiKeySnapshot = ApiKeySnapshot(
        geminiConfigured = getGeminiKey().isNotBlank(),
        huggingFaceConfigured = getHuggingFaceKey().isNotBlank(),
        offlineConfigured = hasUsableOfflineModel()
    )

    /**
     * پاک کردن کلیدهای سرویس‌هایی که دیگر در اپ نیستند.
     * یک‌بار در شروع برنامه صدا زده می‌شود تا هیچ کلید قدیمی‌ای در حافظه نماند.
     */
    fun purgeRemovedProviders() {
        prefs.edit()
            .remove("groq")
            .remove("xai_grok")
            .remove("openrouter")
            .remove("nine_router_enabled")
            .remove("nine_router_base")
            .remove("nine_router_key")
            .remove("nine_router_model")
            .remove("custom_label")
            .remove("custom_base")
            .remove("custom_key")
            .remove("custom_model")
            .remove("custom_provider_list")
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "ai_keys"
        private const val KEY_GEMINI = "gemini"
        private const val KEY_HF = "huggingface"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_ACCESSIBILITY_ENABLED = "accessibility_enabled"
        private const val KEY_OFFLINE_PATH = "offline_model_path"
        private const val KEY_OFFLINE_NAME = "offline_model_name"
        private const val KEY_TTS_VOICE = "tts_voice"
        private const val KEY_TTS_PITCH = "tts_pitch"
        private const val KEY_TTS_RATE = "tts_rate"
        private const val KEY_TTS_LABELS = "tts_labels"
    }
}

data class ApiKeySnapshot(
    val geminiConfigured: Boolean = false,
    val huggingFaceConfigured: Boolean = false,
    val offlineConfigured: Boolean = false
) {
    fun isConfigured(provider: AiProviderKind): Boolean = when (provider) {
        AiProviderKind.GEMINI -> geminiConfigured
        AiProviderKind.HUGGINGFACE -> huggingFaceConfigured
        AiProviderKind.OFFLINE -> offlineConfigured
        AiProviderKind.NONE -> false
    }

    fun hasAny(): Boolean =
        geminiConfigured || huggingFaceConfigured || offlineConfigured
}
