package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.AiProviderKind
import com.example.data.model.CustomProvider
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ApiKeyStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getGroqKey(): String = prefs.getString(KEY_GROQ, "")?.trim().orEmpty()
    fun getXaiGrokKey(): String = prefs.getString(KEY_XAI_GROK, "")?.trim().orEmpty()
    fun getHuggingFaceKey(): String = prefs.getString(KEY_HF, "")?.trim().orEmpty()
    fun getOpenRouterKey(): String = prefs.getString(KEY_OPENROUTER, "")?.trim().orEmpty()
    fun getGeminiOverride(): String = prefs.getString(KEY_GEMINI, "")?.trim().orEmpty()

    // هیچ مقدار پیش‌فرضی داخل اپ نیست؛ کاربر خودش وارد می‌کند.
    fun getUserEmail(): String = prefs.getString(KEY_USER_EMAIL, "")?.trim().orEmpty()
    fun setUserEmail(value: String) = prefs.edit().putString(KEY_USER_EMAIL, value.trim()).apply()

    fun isAccessibilityEnabled(): Boolean = prefs.getBoolean(KEY_ACCESSIBILITY_ENABLED, false)
    fun setAccessibilityEnabled(value: Boolean) = prefs.edit().putBoolean(KEY_ACCESSIBILITY_ENABLED, value).apply()

    /**
     * کلید جمینای فقط از ورودی دستی خود کاربر می‌آید.
     * هیچ کلید پیش‌فرض یا هاردکدشده‌ای داخل اپ وجود ندارد.
     */
    fun getGeminiKey(): String = getGeminiOverride()

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

    // ---- Hand-wired providers -----------------------------------------------
    // The user can add as many as they like; auto-rotation walks through all of them.

    fun getCustomProviders(): List<CustomProvider> = try {
        val raw = prefs.getString(KEY_CUSTOM_LIST, null)
        if (raw.isNullOrBlank()) {
            // Migrate the older single-provider fields so nothing already entered is lost.
            val legacyBase = prefs.getString(KEY_CUSTOM_BASE, "")?.trim().orEmpty()
            if (legacyBase.isBlank()) {
                emptyList()
            } else {
                listOf(
                    CustomProvider(
                        id = "migrated",
                        label = prefs.getString(KEY_CUSTOM_LABEL, "")?.trim().orEmpty()
                            .ifBlank { "سرویس دستی" },
                        baseUrl = legacyBase,
                        apiKey = prefs.getString(KEY_CUSTOM_KEY, "")?.trim().orEmpty(),
                        model = prefs.getString(KEY_CUSTOM_MODEL, "")?.trim().orEmpty()
                    )
                )
            }
        } else {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val base = o.optString("baseUrl").trim()
                if (base.isBlank()) return@mapNotNull null
                CustomProvider(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    label = o.optString("label").ifBlank { "سرویس دستی" },
                    baseUrl = base,
                    apiKey = o.optString("apiKey"),
                    model = o.optString("model"),
                    enabled = o.optBoolean("enabled", true)
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun setCustomProviders(list: List<CustomProvider>) {
        try {
            val arr = JSONArray()
            list.forEach { p ->
                arr.put(
                    JSONObject().apply {
                        put("id", p.id)
                        put("label", p.label)
                        put("baseUrl", p.baseUrl)
                        put("apiKey", p.apiKey)
                        put("model", p.model)
                        put("enabled", p.enabled)
                    }
                )
            }
            prefs.edit().putString(KEY_CUSTOM_LIST, arr.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun upsertCustomProvider(provider: CustomProvider) {
        val list = getCustomProviders().toMutableList()
        val index = list.indexOfFirst { it.id == provider.id }
        if (index >= 0) list[index] = provider else list.add(provider)
        setCustomProviders(list)
    }

    fun removeCustomProvider(id: String) {
        setCustomProviders(getCustomProviders().filterNot { it.id == id })
    }

    fun customProviderById(id: String): CustomProvider? =
        getCustomProviders().firstOrNull { it.id == id }

    /** First enabled hand-wired provider — the default pick when auto mode starts. */
    fun primaryCustomProvider(): CustomProvider? =
        getCustomProviders().firstOrNull { it.enabled }

    // Legacy single-provider accessors, kept so older call sites keep compiling.
    fun getCustomLabel(): String = primaryCustomProvider()?.label.orEmpty()
    fun getCustomBaseUrl(): String = primaryCustomProvider()?.baseUrl.orEmpty()
    fun getCustomKey(): String = primaryCustomProvider()?.apiKey.orEmpty()
    fun getCustomModel(): String = primaryCustomProvider()?.model.orEmpty()

    fun setCustom(label: String, baseUrl: String, key: String, model: String) {
        if (baseUrl.isBlank()) return
        upsertCustomProvider(
            CustomProvider(
                id = "migrated",
                label = label.trim().ifBlank { "سرویس دستی" },
                baseUrl = baseUrl.trim(),
                apiKey = key.trim(),
                model = model.trim()
            )
        )
    }

    // ---- Voice (TTS) selection ------------------------------------------------

    /** Engine voice name the user picked (empty = let the engine choose). */
    fun getTtsVoice(): String = prefs.getString(KEY_TTS_VOICE, "")?.trim().orEmpty()

    fun setTtsVoice(name: String) = prefs.edit().putString(KEY_TTS_VOICE, name.trim()).apply()

    fun getTtsPitch(): Float = prefs.getFloat(KEY_TTS_PITCH, 0.85f)

    fun setTtsPitch(value: Float) = prefs.edit().putFloat(KEY_TTS_PITCH, value).apply()

    fun getTtsRate(): Float = prefs.getFloat(KEY_TTS_RATE, 1.0f)

    fun setTtsRate(value: Float) = prefs.edit().putFloat(KEY_TTS_RATE, value).apply()

    /** Custom display names so the user can rename voices ("آقا امیر" instead of "fa-ir-x-..."). */
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

    fun keyFor(provider: AiProviderKind): String = when (provider) {
        AiProviderKind.GROQ -> getGroqKey()
        AiProviderKind.XAI_GROK -> getXaiGrokKey()
        AiProviderKind.HUGGINGFACE -> getHuggingFaceKey()
        AiProviderKind.OPENROUTER -> getOpenRouterKey()
        AiProviderKind.GEMINI -> getGeminiKey()
        AiProviderKind.NINE_ROUTER -> getNineRouterKey()
        AiProviderKind.CUSTOM -> getCustomKey()
        AiProviderKind.NONE -> ""
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
        customConfigured = getCustomProviders().any { it.enabled && it.baseUrl.isNotBlank() && it.model.isNotBlank() }
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
        private const val KEY_CUSTOM_LIST = "custom_provider_list"
        private const val KEY_TTS_VOICE = "tts_voice"
        private const val KEY_TTS_PITCH = "tts_pitch"
        private const val KEY_TTS_RATE = "tts_rate"
        private const val KEY_TTS_LABELS = "tts_labels"
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
        AiProviderKind.NONE -> false
    }

    fun hasAny(): Boolean =
        xaiGrokConfigured || groqConfigured || huggingFaceConfigured ||
            openRouterConfigured || geminiConfigured || nineRouterConfigured || customConfigured
}
