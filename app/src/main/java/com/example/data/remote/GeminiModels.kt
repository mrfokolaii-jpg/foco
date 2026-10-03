package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Reads the models that the user's OWN Gemini key can actually use.
 *
 * Nothing is pre-stored in the app: the list is asked from Google every time, so a key always
 * shows exactly what that key has access to.
 */
object GeminiModels {

    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"

    suspend fun list(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()
        try {
            val url = "$ENDPOINT?key=${URLEncoder.encode(apiKey, "UTF-8")}&pageSize=200"
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Accept", "application/json")
                .build()

            ProviderHttp.client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) return@withContext emptyList()

                val models = JSONObject(body).optJSONArray("models") ?: JSONArray()
                (0 until models.length()).mapNotNull { index ->
                    val model = models.optJSONObject(index) ?: return@mapNotNull null
                    val methods = model.optJSONArray("supportedGenerationMethods") ?: JSONArray()
                    val canChat = (0 until methods.length()).any {
                        methods.optString(it) == "generateContent"
                    }
                    if (!canChat) return@mapNotNull null
                    model.optString("name")
                        .removePrefix("models/")
                        .takeIf { it.isNotBlank() }
                }
                    .distinct()
                    // مدل‌های واقعی گفتگوگوی gemini اول می‌آیند و در میان آن‌ها نسخهٔ بالاتر جلوتر.
                    .sortedWith(compareBy<String>({ chatRank(it) }, { -versionOf(it) }, { it }))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * رتبهٔ نمایش مدل‌ها.
     *
     * پیش‌تر فهرست فقط الفبایی مرتب می‌شد؛ در نتیجه مدل‌هایی مثل `antigravity-preview-*` و
     * `deep-research-*` بالای فهرست می‌نشستند و با `take(n)` جایی برای `gemini-*` نمی‌ماند.
     * حالا مدل‌های گفتگوگوی gemini اول می‌آیند:
     *   ۰ = gemini پایدار (بدون preview/exp)
     *   ۱ = gemini پیش‌نمایشی یا آزمایشی
     *   ۲ = بقیهٔ مدل‌ها
     */
    /**
     * نسخهٔ مدل از روی شناسه — `gemini-3.5-flash-lite` → 3.5 و `gemini-3.1-pro` → 3.1.
     * مدل‌های بدون شمارهٔ نسخه صفر می‌گیرند و آخر می‌ایستند.
     */
    private fun versionOf(name: String): Double {
        val match = Regex("""gemini-(\d+)(?:\.(\d+))?""").find(name) ?: return 0.0
        val major = match.groupValues[1].toDoubleOrNull() ?: 0.0
        val minor = match.groupValues[2].toDoubleOrNull() ?: 0.0
        return major + minor / 10.0
    }

    private fun chatRank(name: String): Int {
        val isGemini = name.startsWith("gemini-")
        val isUnstable = name.contains("preview", ignoreCase = true) ||
            name.contains("experimental", ignoreCase = true) ||
            name.contains("-exp", ignoreCase = true)
        return when {
            isGemini && !isUnstable -> 0
            isGemini -> 1
            else -> 2
        }
    }
}
