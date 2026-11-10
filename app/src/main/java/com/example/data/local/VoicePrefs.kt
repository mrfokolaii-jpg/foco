package com.example.data.local

import android.content.Context

/**
 * تنظیمات دستیار صوتی و شبکه فوکو.
 */
class VoicePrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("foko_voice_prefs", Context.MODE_PRIVATE)

    /** بیدار شدن با گفتن «فوکو» (آفلاین، با مدل Vosk). */
    var wakeWordEnabled: Boolean
        get() = prefs.getBoolean(K_WAKE, false)
        set(v) = prefs.edit().putBoolean(K_WAKE, v).apply()

    /** تشخیص گفتار آفلاین فارسی به‌جای سرویس گوگل. */
    var offlineSttEnabled: Boolean
        get() = prefs.getBoolean(K_OFFLINE, false)
        set(v) = prefs.edit().putBoolean(K_OFFLINE, v).apply()

    /** صدای طبیعی با مدل هوش مصنوعی (جمنای یا OpenAI) به‌جای صدای گوشی. */
    var naturalVoiceEnabled: Boolean
        get() = prefs.getBoolean(K_NATURAL, false)
        set(v) = prefs.edit().putBoolean(K_NATURAL, v).apply()

    var naturalVoiceName: String
        get() = prefs.getString(K_VOICE_NAME, "Kore").orEmpty().ifBlank { "Kore" }
        set(v) = prefs.edit().putString(K_VOICE_NAME, v.trim()).apply()

    /** قطع کردن فوکو وسط حرف با شروع صحبت کاربر. */
    var bargeInEnabled: Boolean
        get() = prefs.getBoolean(K_BARGE, true)
        set(v) = prefs.edit().putBoolean(K_BARGE, v).apply()

    /** جواب کلمه به کلمه (استریم) و خواندن جمله به جمله. */
    var streamingEnabled: Boolean
        get() = prefs.getBoolean(K_STREAM, true)
        set(v) = prefs.edit().putBoolean(K_STREAM, v).apply()

    /** استفاده از ابزارهای بومی مدل (function calling). */
    var nativeToolsEnabled: Boolean
        get() = prefs.getBoolean(K_NATIVE_TOOLS, true)
        set(v) = prefs.edit().putBoolean(K_NATIVE_TOOLS, v).apply()

    /** حالت توضیح زنده صفحه. */
    var liveExplainEnabled: Boolean
        get() = prefs.getBoolean(K_LIVE, false)
        set(v) = prefs.edit().putBoolean(K_LIVE, v).apply()

    // پراکسی
    var proxyEnabled: Boolean
        get() = prefs.getBoolean(K_PROXY_ON, false)
        set(v) = prefs.edit().putBoolean(K_PROXY_ON, v).apply()

    /** "HTTP" یا "SOCKS" */
    var proxyType: String
        get() = prefs.getString(K_PROXY_TYPE, "SOCKS").orEmpty().ifBlank { "SOCKS" }
        set(v) = prefs.edit().putString(K_PROXY_TYPE, v).apply()

    var proxyHost: String
        get() = prefs.getString(K_PROXY_HOST, "127.0.0.1").orEmpty()
        set(v) = prefs.edit().putString(K_PROXY_HOST, v.trim()).apply()

    var proxyPort: Int
        get() = prefs.getInt(K_PROXY_PORT, 10808)
        set(v) = prefs.edit().putInt(K_PROXY_PORT, v).apply()

    companion object {
        private const val K_WAKE = "wake_word"
        private const val K_OFFLINE = "offline_stt"
        private const val K_NATURAL = "natural_voice"
        private const val K_VOICE_NAME = "natural_voice_name"
        private const val K_BARGE = "barge_in"
        private const val K_STREAM = "streaming"
        private const val K_NATIVE_TOOLS = "native_tools"
        private const val K_LIVE = "live_explain"
        private const val K_PROXY_ON = "proxy_on"
        private const val K_PROXY_TYPE = "proxy_type"
        private const val K_PROXY_HOST = "proxy_host"
        private const val K_PROXY_PORT = "proxy_port"
    }
}
