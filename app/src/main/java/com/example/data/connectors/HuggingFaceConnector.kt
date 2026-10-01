package com.example.data.connectors

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
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

    private val JSON = "application/json; charset=utf-8".toMediaType()

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

    /** Dataset discovery on the Hub. */
    suspend fun searchDatasets(query: String, token: String = ""): ConnectorResult<List<String>> =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://huggingface.co/api/datasets?limit=15&sort=downloads&direction=-1" +
                    if (query.isBlank()) "" else "&search=${URLEncoder.encode(query, "UTF-8")}"
                val builder = Request.Builder().url(url).addHeader("User-Agent", "FOKO-AI/2.0")
                if (token.isNotBlank()) builder.addHeader("Authorization", "Bearer $token")
                val res = client.newCall(builder.build()).execute()
                val body = res.body?.string().orEmpty()
                if (!res.isSuccessful) return@withContext ConnectorResult.Err("جستجوی دیتاست‌ها ناموفق بود (${res.code}).", res.code)
                val arr = JSONArray(body)
                val out = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    out.add("${o.optString("id")} — دانلود ${o.optLong("downloads")}")
                }
                ConnectorResult.Ok(out)
            } catch (e: Exception) {
                ConnectorResult.Err("جستجوی دیتاست‌ها ناموفق بود: ${e.localizedMessage}")
            }
        }

    /**
     * Runs a conversation on Hugging Face Inference Providers with the user's own token.
     * OpenAI-compatible endpoint: https://router.huggingface.co/v1/chat/completions
     */
    suspend fun chat(token: String, model: String, prompt: String): ConnectorResult<String> =
        withContext(Dispatchers.IO) {
            if (token.isBlank()) return@withContext ConnectorResult.Err("توکن Hugging Face وارد نشده است.")
            if (prompt.isBlank()) return@withContext ConnectorResult.Err("متن پرسش خالی است.")
            val target = model.ifBlank { DEFAULT_CHAT_MODEL }
            try {
                val payload = JSONObject().apply {
                    put("model", target)
                    put(
                        "messages",
                        JSONArray().put(
                            JSONObject().apply {
                                put("role", "user")
                                put("content", prompt)
                            }
                        )
                    )
                    put("max_tokens", 1024)
                    put("stream", false)
                }
                val res = client.newCall(
                    Request.Builder()
                        .url("https://router.huggingface.co/v1/chat/completions")
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("User-Agent", "FOKO-AI/2.0")
                        .post(payload.toString().toRequestBody(JSON))
                        .build()
                ).execute()
                val body = res.body?.string().orEmpty()
                if (!res.isSuccessful) return@withContext ConnectorResult.Err(
                    when (res.code) {
                        401 -> "توکن Hugging Face نامعتبر است یا مجوز «Inference Providers» ندارد."
                        402 -> "اعتبار استنتاج (Inference) حساب Hugging Face کافی نیست."
                        403 -> "دسترسی به مدل «$target» با این توکن مجاز نیست."
                        404 -> "مدل «$target» در Hugging Face پیدا نشد."
                        429 -> "محدودیت نرخ درخواست Hugging Face؛ کمی بعد دوباره تلاش کن."
                        503 -> "مدل در حال آماده‌سازی است؛ چند لحظه بعد دوباره تلاش کن."
                        else -> "خطای Hugging Face (${res.code})."
                    },
                    res.code
                )
                val text = JSONObject(body)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()
                if (text.isBlank()) {
                    ConnectorResult.Err("پاسخ خالی از Hugging Face دریافت شد.")
                } else {
                    ConnectorResult.Ok(text)
                }
            } catch (e: Exception) {
                ConnectorResult.Err("اجرای مدل روی Hugging Face ناموفق بود: ${e.localizedMessage}")
            }
        }

    /** Fallback model when the caller does not name one. */
    const val DEFAULT_CHAT_MODEL = "Qwen/Qwen2.5-7B-Instruct"
}
