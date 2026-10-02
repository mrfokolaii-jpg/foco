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
                }.distinct().sorted()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
