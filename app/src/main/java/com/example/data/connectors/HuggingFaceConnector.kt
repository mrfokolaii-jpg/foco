package com.example.data.connectors

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Hugging Face Hub client: identity, model discovery and inference router access.
 */
object HuggingFaceConnector {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun whoAmI(token: String): ConnectorResult<String> = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext ConnectorResult.Err("توکن Hugging Face وارد نشده است.")
        try {
            val res = client.newCall(
                Request.Builder()
                    .url("https://huggingface.co/api/whoami-v2")
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("User-Agent", "FOKO-AI/2.0")
                    .build()
            ).execute()
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) return@withContext ConnectorResult.Err(
                if (res.code == 401) "توکن Hugging Face نامعتبر است." else "خطای Hugging Face (${res.code}).",
                res.code
            )
            ConnectorResult.Ok(org.json.JSONObject(body).optString("name", "unknown"))
        } catch (e: Exception) {
            ConnectorResult.Err("اتصال به Hugging Face برقرار نشد: ${e.localizedMessage}")
        }
    }

    suspend fun searchModels(query: String, token: String = ""): ConnectorResult<List<HfModelInfo>> =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://huggingface.co/api/models?limit=20&sort=downloads&direction=-1" +
                    if (query.isBlank()) "" else "&search=${URLEncoder.encode(query, "UTF-8")}"
                val builder = Request.Builder().url(url).addHeader("User-Agent", "FOKO-AI/2.0")
                if (token.isNotBlank()) builder.addHeader("Authorization", "Bearer $token")
                val res = client.newCall(builder.build()).execute()
                val body = res.body?.string().orEmpty()
                if (!res.isSuccessful) return@withContext ConnectorResult.Err("جستجوی مدل‌ها ناموفق بود (${res.code}).", res.code)
                val arr = JSONArray(body)
                val out = mutableListOf<HfModelInfo>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    out.add(
                        HfModelInfo(
                            id = o.optString("id"),
                            downloads = o.optLong("downloads"),
                            likes = o.optLong("likes"),
                            pipelineTag = o.optString("pipeline_tag")
                        )
                    )
                }
                ConnectorResult.Ok(out)
            } catch (e: Exception) {
                ConnectorResult.Err("جستجوی مدل‌ها ناموفق بود: ${e.localizedMessage}")
            }
        }
}
