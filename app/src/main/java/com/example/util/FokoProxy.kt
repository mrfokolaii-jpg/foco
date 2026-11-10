package com.example.util

import android.content.Context
import com.example.data.local.VoicePrefs
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * پراکسی سراسری برای همه اتصال‌های فوکو به مدل‌ها و وب.
 * یک ProxySelector پیش‌فرض نصب می‌کند که هر بار تنظیمات را می‌خواند،
 * پس تغییر تنظیمات بدون راه‌اندازی دوباره اپ اعمال می‌شود.
 */
object FokoProxy {
    @Volatile private var installed = false
    private var prefs: VoicePrefs? = null

    fun install(context: Context) {
        prefs = VoicePrefs(context.applicationContext)
        if (installed) return
        synchronized(this) {
            if (installed) return
            val fallback = ProxySelector.getDefault()
            ProxySelector.setDefault(object : ProxySelector() {
                override fun select(uri: URI?): MutableList<Proxy> {
                    val p = prefs
                    val host = uri?.host.orEmpty()
                    val isLocal = host == "localhost" || host == "127.0.0.1"
                    if (p != null && p.proxyEnabled && p.proxyHost.isNotBlank() && p.proxyPort in 1..65535 && !isLocal) {
                        val type = if (p.proxyType.equals("HTTP", true)) Proxy.Type.HTTP else Proxy.Type.SOCKS
                        return mutableListOf(Proxy(type, InetSocketAddress.createUnresolved(p.proxyHost, p.proxyPort)))
                    }
                    return fallback?.select(uri)?.toMutableList() ?: mutableListOf(Proxy.NO_PROXY)
                }

                override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
                    fallback?.connectFailed(uri, sa, ioe)
                }
            })
            installed = true
        }
    }
}
