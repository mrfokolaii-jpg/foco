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

    /**
     * پیام خام خودِ سرور را از بدنهٔ پاسخ بیرون می‌کشد.
     *
     * بعضی سرویس‌ها خطا را به شکل `{"error": "متن"}` می‌فرستند و بعضی
     * `{"error": {"message": "متن"}}`. هر دو حالت پوشش داده می‌شود تا هیچ‌وقت
     * دلیل واقعیِ خطا پنهان نماند.
     */
    fun rawMessage(body: String): String {
        val text = try {
            val obj = JSONObject(body)
            when (val err = obj.opt("error")) {
                is JSONObject -> err.optString("message").ifBlank { err.toString() }
                is String -> err
                else -> obj.optString("message").ifBlank { obj.optString("detail") }
            }
        } catch (_: Exception) {
            body.trim().take(200)
        }
        return text.trim().take(400)
    }

    /**
     * کد وضعیت را به پیام فارسی تبدیل می‌کند و **متن خود سرور را هم می‌چسباند**.
     * پیش‌تر متن سرور حذف می‌شد و کاربر فقط «خطای سرور (402)» می‌دید؛ بدون این متن
     * نمی‌شد فهمید مشکل کلید است، اعتبار است یا محدودیت نرخ.
     */
    fun mapStatus(code: Int, body: String): String {
        val main = when (code) {
            401 -> "کلید پذیرفته نشد (401)."
            402 -> "این کلید اعتبار کافی ندارد (402)."
            403 -> "این کلید اجازهٔ دسترسی به این مدل را ندارد (403)."
            404 -> "این مدل برای کلید تو در دسترس نیست (404)."
            429 -> "سهمیه یا محدودیت نرخ پر شده است (429)."
            in 500..599 -> "سرور سرویس‌دهنده موقتاً پاسخگو نیست ($code)."
            else -> "خطای سرور ($code)."
        }
        val raw = rawMessage(body)
        return if (raw.isBlank()) main else "$main\nپیام سرور: $raw"
    }
}

/** آدرس پایهٔ هر سرویس — هم برای گفتگو و هم برای خواندن فهرست مدل‌ها. */
object ProviderEndpoints {
    const val HUGGING_FACE = "https://router.huggingface.co/v1"
    const val OPENROUTER = "https://openrouter.ai/api/v1"
    const val GROQ = "https://api.groq.com/openai/v1"
    const val XAI = "https://api.x.ai/v1"
}

object OpenAiCompatProvider {

    /**
     * Reads the provider's own model list (GET {base}/models) so the user picks a real model
     * name instead of typing one from memory. Returns an empty list on any failure.
     */
    suspend fun listModels(baseUrl: String, apiKey: String): List<String> =
        when (val result = listModelsDetailed(baseUrl, apiKey)) {
            is ApiResult.Success -> result.data
            is ApiResult.Error -> emptyList()
        }

    /**
     * همان کار بالا، ولی خطای واقعی سرور را هم برمی‌گرداند.
     *
     * این‌جا مشخص می‌شود چرا یک کلید کار نمی‌کند: ۴۰۱ یعنی کلید غلط، ۴۰۲ یعنی اعتبار
     * تمام شده (مورد Hugging Face)، ۴۰۳ یعنی دسترسی به سرویس استنتاج بسته است.
     */
    suspend fun listModelsDetailed(baseUrl: String, apiKey: String): ApiResult<List<String>> =
        withContext(Dispatchers.IO) {
            val base = baseUrl.trim().trimEnd('/')
            if (base.isBlank()) return@withContext ApiResult.Error("آدرس سرویس خالی است.")
            val modelsUrl = if (base.endsWith("/models")) base else "$base/models"
            try {
                val builder = Request.Builder()
                    .url(modelsUrl)
                    .get()
                    .addHeader("Accept", "application/json")
                if (apiKey.isNotBlank()) builder.addHeader("Authorization", "Bearer $apiKey")
                ProviderHttp.client.newCall(builder.build()).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    if (!res.isSuccessful) {
                        return@withContext ApiResult.Error(ProviderHttp.mapStatus(res.code, body))
                    }
                    val arr = JSONObject(body).optJSONArray("data") ?: JSONArray()
                    val ids = (0 until arr.length()).mapNotNull { i ->
                        arr.optJSONObject(i)?.optString("id")?.takeIf { it.isNotBlank() }
                    }.sorted()
                    if (ids.isEmpty()) {
                        ApiResult.Error("سرور جواب داد ولی هیچ مدلی برنگرداند.")
                    } else {
                        ApiResult.Success(ids)
                    }
                }
            } catch (e: Exception) {
                ApiResult.Error("اتصال برقرار نشد: ${e.localizedMessage ?: "خطای شبکه"}")
            }
        }

    /**
     * پیش از ذخیرهٔ یک سرویس دستی، واقعاً به آن وصل می‌شود تا کاربر سرویس خراب ذخیره نکند.
     *
     *  ۱) ارزان‌ترین راه: فهرست مدل‌ها را می‌گیریم (GET {base}/models).
     *  ۲) اگر سرویس چنین مسیری نداشت، یک پیام یک‌کلمه‌ای می‌فرستیم و فقط موفق/ناموفق را می‌سنجیم.
     */
    suspend fun testConnection(
        baseUrl: String,
        apiKey: String,
        model: String = ""
    ): ApiResult<String> = withContext(Dispatchers.IO) {
        val base = baseUrl.trim().trimEnd('/')
        if (base.isBlank()) return@withContext ApiResult.Error("آدرس API خالی است.")

        val models = listModels(base, apiKey)
        if (models.isNotEmpty()) {
            return@withContext ApiResult.Success("اتصال برقرار شد — ${models.size} مدل پیدا شد")
        }

        val endpoint =
            if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        try {
            val body = JSONObject()
            body.put("model", model.trim().ifBlank { "gpt-4o-mini" })
            val messages = JSONArray()
            val probe = JSONObject()
            probe.put("role", "user")
            probe.put("content", "ping")
            messages.put(probe)
            body.put("messages", messages)
            body.put("max_tokens", 1)

            val builder = Request.Builder()
                .url(endpoint)
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(ProviderHttp.JSON))
            if (apiKey.isNotBlank()) builder.addHeader("Authorization", "Bearer $apiKey")

            ProviderHttp.client.newCall(builder.build()).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (res.isSuccessful) {
                    ApiResult.Success("اتصال برقرار شد")
                } else {
                    ApiResult.Error(ProviderHttp.mapStatus(res.code, text))
                }
            }
        } catch (e: Exception) {
            ApiResult.Error("اتصال برقرار نشد: ${e.localizedMessage ?: "خطای شبکه"}")
        }
    }

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
            // Routers (9Router, gateways) report the model that actually answered in `model`.
            val servedBy = json.optString("model").takeIf { it.isNotBlank() } ?: catalogId
            ApiResult.Success(
                ChatApiResponse(
                    replyText = text,
                    thinkingText = null,
                    searchSources = emptyList(),
                    modelUsed = servedBy
                )
            )
        } catch (e: Exception) {
            ApiResult.Error("اتصال اینترنت برقرار نشد: ${e.localizedMessage ?: "خطای شبکه"}")
        }
    }
}

object XaiGrokProvider {
    /** آدرس پایه — هم برای گفتگو، هم برای خواندن فهرست مدل‌های همین کلید. */
    const val BASE_URL = ProviderEndpoints.XAI
    private const val ENDPOINT = "$BASE_URL/chat/completions"

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

object HuggingFaceProvider {
    /**
     * روتر Hugging Face. فهرست مدل‌های همین آدرس با کلید کاربر خوانده می‌شود،
     * چون هر کلید فقط به بخشی از مدل‌ها دسترسی دارد (خطای «not supported by any
     * provider you have enabled» از همین‌جا می‌آمد).
     */
    const val BASE_URL = ProviderEndpoints.HUGGING_FACE
    private const val ENDPOINT = "$BASE_URL/chat/completions"

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

object OpenRouterProvider {
    /** آدرس پایه — هم برای گفتگو، هم برای خواندن فهرست مدل‌های همین کلید. */
    const val BASE_URL = ProviderEndpoints.OPENROUTER
    private const val ENDPOINT = "$BASE_URL/chat/completions"

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
        systemInstructionText = systemInstructionText,
        extraHeaders = mapOf(
            "HTTP-Referer" to "https://foko.ai",
            "X-Title" to "FOKO AI"
        )
    )
}

object GroqProvider {
    /** آدرس پایه — هم برای گفتگو، هم برای خواندن فهرست مدل‌های همین کلید. */
    const val BASE_URL = ProviderEndpoints.GROQ
    private const val ENDPOINT = "$BASE_URL/chat/completions"

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
