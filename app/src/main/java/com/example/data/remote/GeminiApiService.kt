package com.example.data.remote

import android.util.Log
import com.example.data.model.AiModelOption
import com.example.data.model.ChatMessage
import com.example.data.model.GeminiModelConstants
import com.example.data.model.GroundingSource
import com.example.data.model.ImageGenerationConfig
import com.example.data.model.MessageSender
import com.example.data.model.MusicGenerationConfig
import com.example.data.model.VideoGenerationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class ApiResult<out T> {
    data class Success<out T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val throwable: Throwable? = null) : ApiResult<Nothing>()
}

data class ChatApiResponse(
    val replyText: String,
    val thinkingText: String? = null,
    val searchSources: List<GroundingSource> = emptyList(),
    val modelUsed: String
)

data class GeneratedImageResult(
    val imageBase64: String?,
    val textDescription: String?,
    val modelUsed: String
)

data class GeneratedAudioResult(
    val audioBase64: String?,
    val description: String,
    val modelUsed: String
)

data class GeneratedVideoResult(
    val videoUrlOrBase64: String?,
    val description: String,
    val modelUsed: String
)

object GeminiApiService {
    private const val TAG = "GeminiApiService"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(65, TimeUnit.SECONDS)
        .readTimeout(65, TimeUnit.SECONDS)
        .writeTimeout(65, TimeUnit.SECONDS)
        .build()

    @Volatile
    var runtimeApiKeyOverride: String = ""

    /**
     * کلید فقط از ورودی دستی کاربر می‌آید؛ کلید پیش‌فرضی داخل اپ وجود ندارد.
     */
    private fun getApiKey(): String = runtimeApiKeyOverride.trim()

    fun isApiKeyConfigured(): Boolean {
        return getApiKey().isNotBlank()
    }

    /** نتیجهٔ یک تلاش: کد وضعیت (اگر خطای HTTP بود) و پاسخ. */
    private data class ChatAttempt(val code: Int?, val result: ApiResult<ChatApiResponse>)

    /** چند مدل مختلف را امتحان کنیم و برای هر مدل چند بار. */
    private const val MAX_MODEL_ATTEMPTS = 3
    private const val ATTEMPTS_PER_MODEL = 2
    private const val RETRY_DELAY_MS = 700L

    /**
     * Multi-turn chat generation with support for thinking mode and Google Search Grounding.
     *
     * اگر سرور شلوغ باشد (429/5xx) یا مدل برای این کلید در دسترس نباشد (404)، خودش
     * چند بار تلاش می‌کند و بعد روی مدل بعدیِ همان کلید می‌پرد. پیش‌تر یک تلاش خشک و
     * خالی بود: کاربر فقط «سرور شلوغ است» می‌دید و کارش راه نمی‌افتاد.
     */
    suspend fun generateChatResponse(
        conversationHistory: List<ChatMessage>,
        systemInstructionText: String,
        selectedModel: AiModelOption,
        /** شناسهٔ دقیق مدلی که کاربر انتخاب کرده (مثلاً gemini-3.8-flash). */
        modelIdOverride: String = "",
        /** مدل‌های دیگری که همین کلید دارد — جانشین‌های پرش خودکار. */
        fallbackModelIds: List<String> = emptyList(),
        enableThinkingMode: Boolean = false,
        enableGoogleSearch: Boolean = false
    ): ApiResult<ChatApiResponse> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext ApiResult.Error("کلید API جِمینای تنظیم نشده است. لطفاً کلید معتبر را در پنل Secrets وارد نمایید.")
        }

        // Determine actual model to use based on features
        val effectiveModel = when {
            enableThinkingMode -> GeminiModelConstants.MODEL_PRO
            // انتخاب صریح کاربر بر همه چیز مقدم است؛ وگرنه همهٔ مدل‌های Gemini
            // به یک مدل ثابت می‌رسیدند و یک جواب می‌دادند.
            modelIdOverride.isNotBlank() -> modelIdOverride
            enableGoogleSearch && selectedModel == AiModelOption.LITE -> GeminiModelConstants.MODEL_FLASH
            else -> selectedModel.modelId
        }

        // مدل انتخاب‌شده اول صف است و بعد جانشین‌ها.
        val queue = (listOf(effectiveModel) + fallbackModelIds)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_MODEL_ATTEMPTS)

        var lastError: ApiResult.Error = ApiResult.Error("پاسخی از مدل هوش مصنوعی دریافت نشد.")

        queue.forEachIndexed { index, model ->
            var attempt = 0
            while (attempt < ATTEMPTS_PER_MODEL) {
                val outcome = attemptOnce(
                    apiKey = apiKey,
                    model = model,
                    conversationHistory = conversationHistory,
                    systemInstructionText = systemInstructionText,
                    enableThinkingMode = enableThinkingMode,
                    enableGoogleSearch = enableGoogleSearch
                )
                if (outcome.result is ApiResult.Success) return@withContext outcome.result

                lastError = outcome.result as ApiResult.Error
                val code = outcome.code
                // فقط خطاهای گذرا ارزش تلاش دوباره دارند. کلید غلط یا درخواست بد
                // را نباید سه بار پشت سر هم تکرار کرد.
                val transient = code == null || code == 429 || code == 404 || code in 500..599
                if (!transient) return@withContext lastError
                attempt++
                if (attempt < ATTEMPTS_PER_MODEL) delay(RETRY_DELAY_MS * attempt)
            }
            if (index < queue.lastIndex) delay(RETRY_DELAY_MS)
        }

        val tried = if (queue.size > 1) {
            "\n\n${queue.size} مدلِ این کلید امتحان شد (آخرین: ${queue.last()})."
        } else {
            ""
        }
        ApiResult.Error("همهٔ تلاش‌ها ناموفق بود.\n" + lastError.message + tried)
    }

    /**
     * یک درخواست کامل به گوگل. کد وضعیت هم برگردانده می‌شود تا تصمیم «تلاش دوباره یا
     * پرش به مدل بعدی» ممکن باشد.
     */
    private suspend fun attemptOnce(
        apiKey: String,
        model: String,
        conversationHistory: List<ChatMessage>,
        systemInstructionText: String,
        enableThinkingMode: Boolean,
        enableGoogleSearch: Boolean
    ): ChatAttempt = withContext(Dispatchers.IO) {
        try {
            val requestJson = JSONObject()

            // System Instruction in Persian
            val systemObj = JSONObject()
            val systemParts = JSONArray()
            val systemPart = JSONObject()
            val fullInstruction = buildString {
                append(systemInstructionText)
                append("\n\nقانون مهم: تمام پاسخ‌ها باید کاملاً به زبان فارسی باشد.")
                if (enableThinkingMode) {
                    append("\nلطفاً برای این سوال تمام مراحل استدلال، تحلیل و تفکر منطقی خود را گام‌به‌گام توضیح دهید.")
                }
            }
            systemPart.put("text", fullInstruction)
            systemParts.put(systemPart)
            systemObj.put("parts", systemParts)
            requestJson.put("systemInstruction", systemObj)

            // Contents (History + Current Prompt)
            val contentsArray = JSONArray()
            conversationHistory.takeLast(12).forEach { msg ->
                val contentObj = JSONObject()
                contentObj.put("role", if (msg.sender == MessageSender.USER) "user" else "model")
                val partsArray = JSONArray()

                // Text part
                if (msg.text.isNotBlank()) {
                    val textPart = JSONObject()
                    textPart.put("text", msg.text)
                    partsArray.put(textPart)
                }

                // Image parts if user uploaded images
                if (msg.sender == MessageSender.USER) {
                    msg.allImages().forEach { b64 ->
                        val imagePart = JSONObject()
                        val inlineData = JSONObject()
                        inlineData.put("mimeType", "image/jpeg")
                        inlineData.put("data", b64)
                        imagePart.put("inlineData", inlineData)
                        partsArray.put(imagePart)
                    }
                }

                if (partsArray.length() > 0) {
                    contentObj.put("parts", partsArray)
                    contentsArray.put(contentObj)
                }
            }
            requestJson.put("contents", contentsArray)

            // Generation Config
            val genConfig = JSONObject()
            genConfig.put("temperature", if (enableThinkingMode) 0.4 else 0.7)

            if (enableThinkingMode && model == GeminiModelConstants.MODEL_PRO) {
                val thinkingConfig = JSONObject()
                thinkingConfig.put("thinkingLevel", "HIGH")
                genConfig.put("thinkingConfig", thinkingConfig)
                // Note: Do NOT set maxOutputTokens for thinking mode as per instructions
            }
            requestJson.put("generationConfig", genConfig)

            // Google Search Tool
            if (enableGoogleSearch) {
                val toolsArray = JSONArray()
                val toolObj = JSONObject()
                toolObj.put("googleSearch", JSONObject())
                toolsArray.put(toolObj)
                requestJson.put("tools", toolsArray)
            }

            val endpoint = "$BASE_URL$model:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(endpoint)
                .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Chat API failed ($model): code=${response.code}, body=$responseBody")
                return@withContext ChatAttempt(
                    response.code,
                    ApiResult.Error(parseErrorMessage(responseBody, response.code))
                )
            }

            val parsedJson = JSONObject(responseBody)
            val candidates = parsedJson.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return@withContext ChatAttempt(
                    null,
                    ApiResult.Error("مدل «$model» پاسخی برنگرداند. یک مدل دیگر را امتحان کن.")
                )
            }

            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts")

            var mainText = ""
            var thinkingText: String? = null

            if (parts != null) {
                val sb = StringBuilder()
                for (i in 0 until parts.length()) {
                    val p = parts.getJSONObject(i)
                    if (p.has("thought") || p.optBoolean("thought", false)) {
                        thinkingText = p.optString("text")
                    } else if (p.has("text")) {
                        sb.append(p.getString("text"))
                    }
                }
                mainText = sb.toString()
            }

            // Extract Grounding Metadata (Search Sources)
            val sources = mutableListOf<GroundingSource>()
            val groundingMetadata = firstCandidate.optJSONObject("groundingMetadata")
            if (groundingMetadata != null) {
                val searchChunks = groundingMetadata.optJSONArray("groundingChunks")
                if (searchChunks != null) {
                    for (i in 0 until searchChunks.length()) {
                        val chunk = searchChunks.getJSONObject(i)
                        val web = chunk.optJSONObject("web")
                        if (web != null) {
                            val title = web.optString("title", "منبع وب")
                            val uri = web.optString("uri", "")
                            if (uri.isNotBlank()) {
                                sources.add(GroundingSource(title = title, uri = uri))
                            }
                        }
                    }
                }
            }

            if (mainText.isBlank()) {
                mainText = "پاسخ با موفقیت پردازش شد اما متنی بازگردانده نشد."
            }

            ChatAttempt(
                null,
                ApiResult.Success(
                    ChatApiResponse(
                        replyText = mainText,
                        thinkingText = thinkingText,
                        searchSources = sources.distinctBy { it.uri },
                        modelUsed = model
                    )
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in generateChatResponse", e)
            ChatAttempt(
                null,
                ApiResult.Error("خطا در برقراری ارتباط با سرور هوش مصنوعی: ${e.localizedMessage ?: e.message}")
            )
        }
    }

    /**
     * Generate or Edit Image using Gemini 3 Pro / Flash Image Preview.
     */
    suspend fun generateOrEditImage(config: ImageGenerationConfig): ApiResult<GeneratedImageResult> =
        withContext(Dispatchers.IO) {
            val apiKey = getApiKey()
            if (apiKey.isBlank()) {
                return@withContext ApiResult.Error("کلید API جِمینای برای ساخت تصویر تنظیم نشده است.")
            }

            val modelId = if (config.resolution == "4K" || config.resolution == "2K") {
                GeminiModelConstants.MODEL_IMAGE_PRO
            } else {
                config.modelId
            }

            try {
                val requestJson = JSONObject()
                val contentsArray = JSONArray()
                val contentObj = JSONObject()
                val partsArray = JSONArray()

                // Final Prompt in Persian with style enhancements
                val enrichedPrompt = buildString {
                    append(config.prompt)
                    if (config.stylePrompt.isNotBlank()) {
                        append("، در سبک هنری: ").append(config.stylePrompt)
                    }
                    append("، کیفیت فوق‌العاده بالا، جزئیات دقیق، نورپردازی حرفه‌ای")
                }

                val textPart = JSONObject()
                textPart.put("text", enrichedPrompt)
                partsArray.put(textPart)

                // If base image provided for editing/remixing
                if (!config.baseImageBase64.isNullOrBlank()) {
                    val imagePart = JSONObject()
                    val inlineData = JSONObject()
                    inlineData.put("mimeType", "image/jpeg")
                    inlineData.put("data", config.baseImageBase64)
                    imagePart.put("inlineData", inlineData)
                    partsArray.put(imagePart)
                }

                contentObj.put("parts", partsArray)
                contentsArray.put(contentObj)
                requestJson.put("contents", contentsArray)

                // Generation Config with imageConfig
                val genConfig = JSONObject()
                val imgConfig = JSONObject()
                imgConfig.put("aspectRatio", config.aspectRatio)
                imgConfig.put("imageSize", config.resolution)
                genConfig.put("imageConfig", imgConfig)

                val modalities = JSONArray()
                modalities.put("TEXT")
                modalities.put("IMAGE")
                genConfig.put("responseModalities", modalities)

                requestJson.put("generationConfig", genConfig)

                val endpoint = "$BASE_URL$modelId:generateContent?key=$apiKey"
                val request = Request.Builder()
                    .url(endpoint)
                    .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    Log.e(TAG, "Image API failed ($modelId): code=${response.code}, body=$responseBody")
                    return@withContext ApiResult.Error(parseErrorMessage(responseBody, response.code))
                }

                val parsedJson = JSONObject(responseBody)
                val candidates = parsedJson.optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) {
                    return@withContext ApiResult.Error("تصویری توسط مدل تولید نشد.")
                }

                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")

                var returnedImageBase64: String? = null
                var returnedText: String? = null

                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val p = parts.getJSONObject(i)
                        if (p.has("inlineData")) {
                            val inlineData = p.getJSONObject("inlineData")
                            returnedImageBase64 = inlineData.optString("data")
                        } else if (p.has("text")) {
                            returnedText = p.optString("text")
                        }
                    }
                }

                ApiResult.Success(
                    GeneratedImageResult(
                        imageBase64 = returnedImageBase64,
                        textDescription = returnedText ?: "تصویر با موفقیت ایجاد شد.",
                        modelUsed = modelId
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in generateOrEditImage", e)
                ApiResult.Error("خطا در تولید تصویر: ${e.localizedMessage ?: e.message}")
            }
        }

    /**
     * Generate Music using Lyria-3 models.
     */
    suspend fun generateMusic(config: MusicGenerationConfig): ApiResult<GeneratedAudioResult> =
        withContext(Dispatchers.IO) {
            val apiKey = getApiKey()
            if (apiKey.isBlank()) {
                return@withContext ApiResult.Error("کلید API جِمینای برای تولید موسیقی تنظیم نشده است.")
            }

            try {
                val requestJson = JSONObject()
                val contentsArray = JSONArray()
                val contentObj = JSONObject()
                val partsArray = JSONArray()

                val enrichedPrompt = "تولید قطعه موسیقی با مشخصات: ${config.prompt}، سبک: ${config.genre}، مدت زمان: ${config.durationSeconds} ثانیه، با سازبندی غنی و صدای باکیفیت استودیویی."
                val textPart = JSONObject()
                textPart.put("text", enrichedPrompt)
                partsArray.put(textPart)

                contentObj.put("parts", partsArray)
                contentsArray.put(contentObj)
                requestJson.put("contents", contentsArray)

                val genConfig = JSONObject()
                val modalities = JSONArray()
                modalities.put("AUDIO")
                genConfig.put("responseModalities", modalities)
                requestJson.put("generationConfig", genConfig)

                val endpoint = "$BASE_URL${config.modelId}:generateContent?key=$apiKey"
                val request = Request.Builder()
                    .url(endpoint)
                    .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    Log.e(TAG, "Music API failed (${config.modelId}): code=${response.code}, body=$responseBody")
                    // If Lyria is preview/quota limited in some regions, return friendly explanation
                    return@withContext ApiResult.Error(parseErrorMessage(responseBody, response.code))
                }

                val parsedJson = JSONObject(responseBody)
                val candidates = parsedJson.optJSONArray("candidates")
                var audioBase64: String? = null
                var desc = "موسیقی با موفقیت ساخته شد."

                if (candidates != null && candidates.length() > 0) {
                    val first = candidates.getJSONObject(0)
                    val content = first.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val p = parts.getJSONObject(i)
                            if (p.has("inlineData")) {
                                audioBase64 = p.getJSONObject("inlineData").optString("data")
                            } else if (p.has("text")) {
                                desc = p.optString("text")
                            }
                        }
                    }
                }

                ApiResult.Success(
                    GeneratedAudioResult(
                        audioBase64 = audioBase64,
                        description = desc,
                        modelUsed = config.modelId
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in generateMusic", e)
                ApiResult.Error("خطا در تولید قطعه موسیقی: ${e.localizedMessage ?: e.message}")
            }
        }

    /**
     * Generate Video using Veo-3.1 model.
     */
    suspend fun generateVideo(config: VideoGenerationConfig): ApiResult<GeneratedVideoResult> =
        withContext(Dispatchers.IO) {
            val apiKey = getApiKey()
            if (apiKey.isBlank()) {
                return@withContext ApiResult.Error("کلید API جِمینای برای ساخت ویدیو تنظیم نشده است.")
            }

            try {
                // Veo endpoint request
                val requestJson = JSONObject()
                val enrichedPrompt = "Cinematic animated scene in Persian aesthetic: ${config.prompt}, high motion fluidity, 4k cinematic render, aspect ratio ${config.aspectRatio}"
                requestJson.put("prompt", enrichedPrompt)

                val veoConfig = JSONObject()
                veoConfig.put("numberOfVideos", 1)
                veoConfig.put("resolution", "720p")
                veoConfig.put("aspectRatio", config.aspectRatio)
                requestJson.put("config", veoConfig)

                if (!config.baseImageBase64.isNullOrBlank()) {
                    val imageObj = JSONObject()
                    val bytesBase64Obj = JSONObject()
                    bytesBase64Obj.put("bytesBase64Encoded", config.baseImageBase64)
                    imageObj.put("image", bytesBase64Obj)
                    requestJson.put("image", imageObj)
                }

                val endpoint = "$BASE_URL${config.modelId}:generateVideos?key=$apiKey"
                val request = Request.Builder()
                    .url(endpoint)
                    .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    Log.e(TAG, "Veo API failed (${config.modelId}): code=${response.code}, body=$responseBody")
                    return@withContext ApiResult.Error(parseErrorMessage(responseBody, response.code))
                }

                val parsedJson = JSONObject(responseBody)
                val operationName = parsedJson.optString("name", "عملیات تولید ویدیو با وئو آغاز شد")

                ApiResult.Success(
                    GeneratedVideoResult(
                        videoUrlOrBase64 = operationName,
                        description = "درخواست ساخت ویدیو با نسبت ${config.aspectRatio} با موفقیت ثبت و پردازش شد.",
                        modelUsed = config.modelId
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in generateVideo", e)
                ApiResult.Error("خطا در پردازش ویدیو: ${e.localizedMessage ?: e.message}")
            }
        }

    /**
     * پیام خطای گوگل را به فارسی توضیح می‌دهد و **متن خود سرور را هم نگه می‌دارد**.
     *
     * پیش‌تر برای ۵۰۰/۵۰۳ متن واقعی دور ریخته می‌شد و کاربر فقط «سرور شلوغ است»
     * می‌دید؛ بدون متن گوگل نمی‌شد فهمید مشکل سهمیه است، مدل است یا کلید.
     */
    private fun parseErrorMessage(responseBody: String, statusCode: Int): String {
        val rawMsg = ProviderHttp.rawMessage(responseBody)
        val main = when (statusCode) {
            400 -> "درخواست نامعتبر بود (400)."
            401 -> "کلید Gemini پذیرفته نشد (401)."
            403 -> "دسترسی این کلید به این مدل بسته است یا سهمیه تمام شده (403)."
            404 -> "این مدل برای کلید تو در دسترس نیست (404)."
            429 -> "سهمیهٔ درخواست پر شده است (429)."
            500, 503 -> "سرورهای گوگل موقتاً در حال پردازش سنگین هستند ($statusCode)."
            else -> "خطای سرور ($statusCode)."
        }
        return if (rawMsg.isBlank()) main else "$main\nپیام گوگل: $rawMsg"
    }
}
