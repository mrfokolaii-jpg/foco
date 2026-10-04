package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class SearchHit(
    val title: String,
    val url: String,
    val snippet: String
)

/**
 * Live web search used both by the chat's "Google search" toggle and by the agent's
 * `web_search` tool. Primary engine is DuckDuckGo's HTML endpoint; the instant-answer
 * API is kept as a fallback so the provider still works when the HTML page is blocked.
 */
object WebSearchProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    /** Structured search used by the agent tools. */
    suspend fun search(query: String, limit: Int = 6): List<SearchHit> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext emptyList()
        val fromHtml = tryHtmlSearch(clean, limit)
        if (fromHtml.isNotEmpty()) return@withContext fromHtml
        val instant = searchLive(clean)
        if (instant.isNotBlank()) {
            return@withContext listOf(SearchHit(title = "نتایج وب", url = "", snippet = instant))
        }
        emptyList()
    }

    /** Text blob used to ground the language model. */
    /**
     * جستجوی زنده.
     *
     * مهم: این تابع مستقیماً از مسیر ارسال پیام صدا زده می‌شود. پیش‌تر هیچ جابه‌جایی
     * رشته‌ای انجام نمی‌شد و درخواست شبکه روی رشتهٔ اصلی اجرا می‌شد؛ همین باعث می‌شد
     * اندروید با خطای NetworkOnMainThreadException برنامه را ببندد. حالا حتماً روی
     * رشتهٔ ورودی/خروجی اجرا می‌شود.
     */
    suspend fun searchLive(query: String): String = withContext(Dispatchers.IO) {
        val hits = try {
            tryHtmlSearch(query.trim(), 6)
        } catch (_: Exception) {
            emptyList()
        }
        if (hits.isNotEmpty()) {
            return@withContext hits.joinToString("\n") { hit ->
                buildString {
                    appendLine("عنوان: ${hit.title}")
                    if (hit.url.isNotBlank()) appendLine("لینک: ${hit.url}")
                    if (hit.snippet.isNotBlank()) appendLine("خلاصه: ${hit.snippet}")
                }.trim()
            }
        }
        instantAnswer(query)
    }

    private fun tryHtmlSearch(query: String, limit: Int): List<SearchHit> {
        val endpoints = listOf(
            "https://html.duckduckgo.com/html/" to "POST",
            "https://lite.duckduckgo.com/lite/" to "POST"
        )
        for ((endpoint, method) in endpoints) {
            try {
                val form = "q=" + URLEncoder.encode(query, "UTF-8")
                val builder = Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", UA)
                    .header("Accept-Language", "fa-IR,fa;q=0.9,en;q=0.8")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                val request = if (method == "POST") {
                    builder.post(form.toRequestBody(null)).build()
                } else {
                    builder.url("$endpoint?$form").get().build()
                }
                val response = client.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isBlank()) continue
                val hits = parseDuckDuckGo(body, limit)
                if (hits.isNotEmpty()) return hits
            } catch (_: Exception) {
            }
        }
        return emptyList()
    }

    private fun parseDuckDuckGo(html: String, limit: Int): List<SearchHit> {
        val hits = mutableListOf<SearchHit>()
        val linkRegex = Regex(
            "(?is)<a[^>]+class=\"[^\"]*result__a[^\"]*\"[^>]+href=\"([^\"]+)\"[^>]*>(.*?)</a>"
        )
        val snippetRegex = Regex(
            "(?is)<(?:a|td|div)[^>]+class=\"[^\"]*result__snippet[^\"]*\"[^>]*>(.*?)</(?:a|td|div)>"
        )
        val snippets = snippetRegex.findAll(html).map { PageReader.htmlToText(it.groupValues[1]) }.toList()
        linkRegex.findAll(html).forEachIndexed { index, match ->
            if (hits.size >= limit) return@forEachIndexed
            val href = decodeDuckDuckGoUrl(match.groupValues[1])
            val title = PageReader.htmlToText(match.groupValues[2])
            if (title.isBlank()) return@forEachIndexed
            hits.add(
                SearchHit(
                    title = title,
                    url = href,
                    snippet = snippets.getOrNull(index).orEmpty()
                )
            )
        }
        if (hits.isEmpty()) {
            // Lite layout fallback
            val lite = Regex("(?is)<a[^>]+class=\"result-link\"[^>]+href=\"([^\"]+)\"[^>]*>(.*?)</a>")
            lite.findAll(html).forEach { match ->
                if (hits.size >= limit) return@forEach
                hits.add(
                    SearchHit(
                        title = PageReader.htmlToText(match.groupValues[2]),
                        url = decodeDuckDuckGoUrl(match.groupValues[1]),
                        snippet = ""
                    )
                )
            }
        }
        return hits
    }

    private fun decodeDuckDuckGoUrl(href: String): String {
        val raw = href.trim()
        val uddg = Regex("[?&]uddg=([^&]+)").find(raw)?.groupValues?.get(1)
        if (!uddg.isNullOrBlank()) {
            return try {
                java.net.URLDecoder.decode(uddg, "UTF-8")
            } catch (_: Exception) {
                raw
            }
        }
        return when {
            raw.startsWith("//") -> "https:$raw"
            raw.startsWith("/") -> "https://duckduckgo.com$raw"
            else -> raw
        }
    }

    private suspend fun instantAnswer(query: String): String = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
            val response = client.newCall(
                Request.Builder().url(url).header("User-Agent", UA).build()
            ).execute()
            if (!response.isSuccessful) return@withContext ""
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return@withContext ""
            val json = JSONObject(body)
            val sb = StringBuilder()
            val heading = json.optString("Heading")
            val abstractText = json.optString("AbstractText")
            if (heading.isNotBlank() && abstractText.isNotBlank()) {
                sb.appendLine("عنوان: $heading")
                sb.appendLine("خلاصه نتایج وب: $abstractText")
            }
            val related = json.optJSONArray("RelatedTopics")
            if (related != null) {
                for (i in 0 until minOf(related.length(), 4)) {
                    val topic = related.optJSONObject(i) ?: continue
                    val text = topic.optString("Text")
                    if (text.isNotBlank()) sb.appendLine("- $text")
                }
            }
            sb.toString().trim()
        } catch (_: Exception) {
            ""
        }
    }
}
