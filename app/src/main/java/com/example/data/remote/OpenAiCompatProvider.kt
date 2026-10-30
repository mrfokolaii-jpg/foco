package com.example.data.remote

import com.example.data.model.ChatMessage
import com.example.data.model.MessageSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ProviderHttp {
    val JSON = "application/json; charset=utf-8".toMediaType()

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(65, TimeUnit.SECONDS)
        .readTimeout(65, TimeUnit.SECONDS)
        .writeTimeout(65, TimeUnit.SECONDS)
        .build()

    fun mapStatus(code: Int, body: String): String {
        return when (code) {
            401, 403 -> "کلید نامعتبر است یا منقضی شده. لطفاً کلید صحیح را از سایت سازنده دریافت کنید."
            404 -> "مدل انتخابی در دسترس نیست. مدل دیگری را انتخاب کنید."
            429 -> "سهمیه یا محدودیت نرخ پر شده است. لطفاً چند لحظه بعد تلاش کنید."
            in 500..599 -> "سرور هوش مصنوعی موقتاً پاسخگو نیست. لطفاً دوباره تلاش کنید."
            else -> {
                val parsed = try {
                    JSONObject(body).optJSONObject("error")?.optString("message")
                } catch (_: Exception) {
                    null
                }
                parsed?.takeIf { it.isNotBlank() } ?: "خطای سرور ($code)"
            }
        }
    }
}

object OpenAiCompatProvider {
    suspend fun chat(
        endpoint: String,
        apiKey: String,
        apiModelId: String,
        catalogId: String,
        conversationHistory: List<ChatMessage>,
        systemInstructionText: String,
        extraHeaders: Map<String, String> = emptyMap()
    ): ApiResult<ChatApiResponse> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject()
            body.put("model", apiModelId)

            val messages = JSONArray()
            if (systemInstructionText.isNotBlank()) {
                val sys = JSONObject()
                sys.put("role", "system")
                sys.put("content", systemInstructionText)
                messages.put(sys)
            }

            conversationHistory.takeLast(12).forEach { msg ->
                val images = msg.allImages()
                if (msg.text.isBlank() && images.isEmpty()) return@forEach
                val obj = JSONObject()
                obj.put("role", if (msg.sender == MessageSender.USER) "user" else "assistant")

                if (images.isNotEmpty() && msg.sender == MessageSender.USER) {
                    val contentArr = JSONArray()
                    if (msg.text.isNotBlank()) {
                        val textPart = JSONObject()
                        textPart.put("type", "text")
                        textPart.put("text", msg.text)
                        contentArr.put(textPart)
                    }
                    images.forEach { b64 ->
                        val imgPart = JSONObject()
                        imgPart.put("type", "image_url")
                        val urlObj = JSONObject()
                        urlObj.put("url", "data:image/jpeg;base64,$b64")
                        imgPart.put("image_url", urlObj)
                        contentArr.put(imgPart)
                    }
                    obj.put("content", contentArr)
                } else {
                    obj.put("content", msg.text)
                }
                messages.put(obj)
            }
            body.put("messages", messages)

            val builder = Request.Builder()
                .url(endpoint)
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(ProviderHttp.JSON))

            if (apiKey.isNotBlank() && apiKey != "FREE") {
                builder.addHeader("Authorization", "Bearer $apiKey")
            }
            extraHeaders.forEach { (k, v) -> builder.addHeader(k, v) }

            val response = ProviderHttp.client.newCall(builder.build()).execute()
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext ApiResult.Error(ProviderHttp.mapStatus(response.code, responseBody))
            }

            val json = JSONObject(responseBody)
            val choices = json.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return@withContext ApiResult.Error("پاسخی از مدل هوش مصنوعی دریافت نشد.")
            }
            val message = choices.getJSONObject(0).optJSONObject("message")
            val text = message?.optString("content").orEmpty().trim()
            if (text.isBlank()) {
                return@withContext ApiResult.Error("پاسخی از مدل هوش مصنوعی دریافت نشد.")
            }
            ApiResult.Success(
                ChatApiResponse(
                    replyText = text,
                    thinkingText = null,
                    searchSources = emptyList(),
                    modelUsed = catalogId
                )
            )
        } catch (e: Exception) {
            ApiResult.Error("اتصال اینترنت برقرار نشد: ${e.localizedMessage ?: "خطای شبکه"}")
        }
    }
}

object PollinationsFreeProvider {
    private const val ENDPOINT = "https://text.pollinations.ai/openai"

    suspend fun chat(
        conversationHistory: List<ChatMessage>,
        systemInstructionText: String
    ): ApiResult<ChatApiResponse> = OpenAiCompatProvider.chat(
        endpoint = ENDPOINT,
        apiKey = "FREE",
        apiModelId = "openai-large",
        catalogId = "foko-free",
        conversationHistory = conversationHistory,
        systemInstructionText = systemInstructionText
    )
}

object HuggingFaceProvider {
    private const val ENDPOINT = "https://router.huggingface.co/v1/chat/completions"

    suspend fun chat(
        apiKey: String,
        apiModelId: String,
        catalogId: String,
        conversationHistory: List<ChatMessage>,
        systemInstructionText: String
    ): ApiResult<ChatApiResponse> = OpenAiCompatProvider.chat(
        endpoint = ENDPOINT,
        apiKey = apiKey,
        apiModelId = apiModelId,
        catalogId = catalogId,
        conversationHistory = conversationHistory,
        systemInstructionText = systemInstructionText
    )
}
