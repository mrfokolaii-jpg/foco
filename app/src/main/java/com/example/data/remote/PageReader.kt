package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Fetches a web page and reduces it to readable plain text.
 * Used by the agent's "browser reading" capability.
 */
object PageReader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun read(url: String, maxChars: Int = 6000): String = withContext(Dispatchers.IO) {
        val target = normalizeUrl(url)
        if (target.isBlank()) return@withContext "آدرس نامعتبر است."
        try {
            val request = Request.Builder()
                .url(target)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Mobile Safari/537.36")
                .header("Accept-Language", "fa-IR,fa;q=0.9,en;q=0.8")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext "دریافت صفحه ناموفق بود (کد ${response.code})."
            }
            val text = htmlToText(body)
            if (text.isBlank()) return@withContext "محتوای متنی قابل خواندنی در صفحه یافت نشد."
            "منبع: $target\n\n" + text.take(maxChars)
        } catch (e: Exception) {
            "خطا در خواندن صفحه: ${e.localizedMessage}"
        }
    }

    fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim().trim('"', '\'', '`')
        if (trimmed.isBlank()) return ""
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains('.') && !trimmed.contains(' ') -> "https://$trimmed"
            else -> "https://duckduckgo.com/html/?q=" + java.net.URLEncoder.encode(trimmed, "UTF-8")
        }
    }

    fun htmlToText(html: String): String {
        var s = html
        s = s.replace(Regex("(?is)<script.*?</script>"), " ")
        s = s.replace(Regex("(?is)<style.*?</style>"), " ")
        s = s.replace(Regex("(?is)<noscript.*?</noscript>"), " ")
        s = s.replace(Regex("(?is)<svg.*?</svg>"), " ")
        s = s.replace(Regex("(?is)<head.*?</head>"), " ")
        s = s.replace(Regex("(?is)<(br|/p|/div|/li|/h[1-6]|/tr)[^>]*>"), "\n")
        s = s.replace(Regex("(?is)<[^>]+>"), " ")
        s = decodeEntities(s)
        s = s.replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
        s = s.replace(Regex("\\n\\s*\\n\\s*\\n+"), "\n\n")
        s = s.lines().map { it.trim() }.filter { it.isNotBlank() }.joinToString("\n")
        return s.trim()
    }

    fun decodeEntities(input: String): String {
        var s = input
        s = s.replace("&nbsp;", " ")
        s = s.replace("&amp;", "&")
        s = s.replace("&lt;", "<")
        s = s.replace("&gt;", ">")
        s = s.replace("&quot;", "\"")
        s = s.replace("&#39;", "'")
        s = s.replace("&apos;", "'")
        s = s.replace("&mdash;", "—")
        s = s.replace("&ndash;", "–")
        s = s.replace("&hellip;", "…")
        val numeric = Regex("&#(x?[0-9A-Fa-f]+);")
        s = numeric.replace(s) { m ->
            try {
                val raw = m.groupValues[1]
                val code = if (raw.startsWith("x") || raw.startsWith("X")) raw.substring(1).toInt(16)
                else raw.toInt()
                String(Character.toChars(code))
            } catch (_: Exception) {
                m.value
            }
        }
        return s
    }
}
