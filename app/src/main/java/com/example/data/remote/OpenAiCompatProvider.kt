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

    fun extractErrorMessage(body: String): String? {
        return try {
            val root = JSONObject(body)
            val err = root.optJSONObject("error")
            when {
                err != null -> {
                    val msg = err.optString("message").orEmpty()
                    val status = err.optString("status").orEmpty()
                    when {
                        msg.isNotBlank() && status.isNotBlank() -> "$msg ($status)"
                        msg.isNotBlank() -> msg
                        else -> null
                    }
                }
                else -> root.optString("message").takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun mapStatus(code: Int, body: String): String {
        val detailed = extractErrorMessage(body)
        // همیشه پیام واقعی سرور را ترجیح بده تا کاربر بفهمد مشکل چیست
        if (!detailed.isNullOrBlank()) {
            return when {
                code == 401 || code == 403 -> "کلید نامعتبر: $detailed"
                code == 404 -> "مدل یا آدرس پیدا نشد: $detailed"
                code == 429 -> "محدودیت نرخ: $detailed"
                else -> "خطا ($code): $detailed"
            }
        }
        return when (code) {
            401, 403 -> "کلید نامعتبر است یا منقضی شده. لطفاً کلید صحیح را از سایت سازنده دریافت کنید."
            404 -> "مدل انتخابی در دسترس نیست. مدل دیگری را انتخاب کنید."
            429 -> "سهمیه یا محدودیت نرخ پر شده است. لطفاً چند لحظه بعد تلاش کنید."
            in 500..599 -> "سرور هوش مصنوعی موقتاً پاسخگو نیست. لطفاً دوباره تلاش کنید."
            else -> "خطای سرور ($code)"
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
        extraHeaders: Map<String, String> = emptyMap(),
        /** تعریف ابزارها برای فراخوانی بومی (OpenAI tools). null یعنی بدون ابزار بومی. */
        tools: JSONArray? = null,
        /** اگر داده شود، پاسخ به صورت استریم می‌آید و هر تکه متن به این تابع داده می‌شود. */
        onDelta: ((String) -> Unit)? = null
    ): ApiResult<ChatApiResponse> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject()
            body.put("model", apiModelId)
            if (tools != null && tools.length() > 0) {
                body.put("tools", tools)
                body.put("tool_choice", "auto")
            }
            if (onDelta != null) body.put("stream", true)

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

            val isGemini = endpoint.contains("generativelanguage.googleapis.com", ignoreCase = true)

            val builder = Request.Builder()
                .url(endpoint)
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(ProviderHttp.JSON))

            if (apiKey.isNotBlank()) {
                // استاندارد OpenAI-compat
                builder.addHeader("Authorization", "Bearer $apiKey")
                // بعضی کلیدهای Gemini فقط با این هدر کار می‌کنند
                if (isGemini) {
                    builder.addHeader("x-goog-api-key", apiKey)
                }
            }
            extraHeaders.forEach { (k, v) -> builder.addHeader(k, v) }

            val response = ProviderHttp.client.newCall(builder.build()).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string().orEmpty()
                response.close()
                // بعضی مدل‌ها ابزار بومی یا استریم را پشتیبانی نمی‌کنند؛ یک بار بدون آن‌ها تلاش کن
                if (response.code in 400..422 && (tools != null || onDelta != null)) {
                    return@withContext chat(
                        endpoint, apiKey, apiModelId, catalogId, conversationHistory,
                        systemInstructionText, extraHeaders, tools = null, onDelta = null
                    )
                }
                return@withContext ApiResult.Error(ProviderHttp.mapStatus(response.code, errBody))
            }

            val text: String = if (onDelta != null) {
                readStream(response, onDelta)
            } else {
                val responseBody = response.body?.string().orEmpty()
                val json = JSONObject(responseBody)
                val choices = json.optJSONArray("choices")
                if (choices == null || choices.length() == 0) {
                    return@withContext ApiResult.Error("پاسخی از مدل هوش مصنوعی دریافت نشد.")
                }
                val message = choices.getJSONObject(0).optJSONObject("message")
                val toolText = message?.optJSONArray("tool_calls")?.let { toolCallsToTag(it) }.orEmpty()
                // optString برای مقدار null کلمه "null" برمی‌گرداند؛ این همان جواب «null» بود
                val content = if (message == null || message.isNull("content")) "" else message.optString("content")
                (toolText.ifBlank { content }).trim()
            }
            if (text.isBlank() || text.equals("null", ignoreCase = true)) {
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

/** تبدیل tool_calls استاندارد به تگ متنی فوکو تا موتور عامل بدون تغییر کار کند. */
internal fun toolCallsToTag(calls: JSONArray): String {
    if (calls.length() == 0) return ""
    val fn = calls.optJSONObject(0)?.optJSONObject("function") ?: return ""
    val name = fn.optString("name")
    if (name.isBlank()) return ""
    val args = try { JSONObject(fn.optString("arguments").ifBlank { "{}" }) } catch (_: Exception) { JSONObject() }
    return "<foko_tool>" + JSONObject().put("name", name).put("args", args).toString() + "</foko_tool>"
}

/** خواندن پاسخ استریم (SSE) و جمع کردن متن و فراخوانی ابزار. */
private fun readStream(response: okhttp3.Response, onDelta: (String) -> Unit): String {
    val content = StringBuilder()
    var toolName = ""
    val toolArgs = StringBuilder()
    val source = response.body?.source() ?: return ""
    try {
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith("data:")) continue
            val data = line.removePrefix("data:").trim()
            if (data == "[DONE]") break
            val json = try { JSONObject(data) } catch (_: Exception) { continue }
            val delta = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta") ?: continue
            if (!delta.isNull("content")) {
                val piece = delta.optString("content")
                if (piece.isNotEmpty()) {
                    content.append(piece)
                    onDelta(piece)
                }
            }
            val calls = delta.optJSONArray("tool_calls")
            if (calls != null && calls.length() > 0) {
                val fn = calls.optJSONObject(0)?.optJSONObject("function")
                if (fn != null) {
                    val n = fn.optString("name")
                    if (n.isNotBlank() && toolName.isBlank()) toolName = n
                    if (!fn.isNull("arguments")) toolArgs.append(fn.optString("arguments"))
                }
            }
        }
    } finally {
        try { response.close() } catch (_: Exception) {}
    }
    if (toolName.isNotBlank()) {
        val args = try { JSONObject(toolArgs.toString().ifBlank { "{}" }) } catch (_: Exception) { JSONObject() }
        return "<foko_tool>" + JSONObject().put("name", toolName).put("args", args).toString() + "</foko_tool>"
    }
    return content.toString()
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
