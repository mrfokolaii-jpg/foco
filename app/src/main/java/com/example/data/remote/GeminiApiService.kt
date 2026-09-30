package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.AiModelOption
import com.example.data.model.ChatMessage
import com.example.data.model.GeminiModelConstants
import com.example.data.model.GroundingSource
import com.example.data.model.ImageGenerationConfig
import com.example.data.model.MessageSender
import com.example.data.model.MusicGenerationConfig
import com.example.data.model.VideoGenerationConfig
import kotlinx.coroutines.Dispatchers
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

    private fun getApiKey(): String {
        if (runtimeApiKeyOverride.isNotBlank()) return runtimeApiKeyOverride
        return try {
            val key = BuildConfig.GEMINI_API_KEY
            if (key.isBlank() || key == "MY_GEMINI_API_KEY") {
                ""
            } else {
                key
            }
        } catch (e: Throwable) {
            ""
        }
    }

    fun isApiKeyConfigured(): Boolean {
        return getApiKey().isNotBlank()
    }

    /**
     * Multi-turn chat generation with support for thinking mode and Google Search Grounding.
     */
    suspend fun generateChatResponse(
        conversationHistory: List<ChatMessage>,
        systemInstructionText: String,
        selectedModel: AiModelOption,
        enableThinkingMode: Boolean = false,
        enableGoogleSearch: Boolean = false
    ): ApiResult<ChatApiResponse> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext ApiResult.Error("کلید API جِمینای تنظیم نشده است. لطفاً کلید معتبر را در پنل Secrets وارد نمایید.")
        }

        // Determine actual model to use based on features
        val effectiveModel = if (enableThinkingMode) {
            GeminiModelConstants.MODEL_PRO
        } else if (enableGoogleSearch && selectedModel == AiModelOption.LITE) {
            GeminiModelConstants.MODEL_FLASH
        } else {
            selectedModel.modelId
        }

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

            if (enableThinkingMode && effectiveModel == GeminiModelConstants.MODEL_PRO) {
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

            val endpoint = "$BASE_URL$effectiveModel:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(endpoint)
                .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Chat API failed ($effectiveModel): code=${response.code}, body=$responseBody")
                val errorMessage = parseErrorMessage(responseBody, response.code)
                return@withContext ApiResult.Error(errorMessage)
            }

            val parsedJson = JSONObject(responseBody)
            val candidates = parsedJson.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return@withContext ApiResult.Error("پاسخی از مدل هوش مصنوعی دریافت نشد.")
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

            ApiResult.Success(
                ChatApiResponse(
                    replyText = mainText,
                    thinkingText = thinkingText,
                    searchSources = sources.distinctBy { it.uri },
                    modelUsed = effectiveModel
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in generateChatResponse", e)
            ApiResult.Error("خطا در برقراری ارتباط با سرور هوش مصنوعی: ${e.localizedMessage ?: e.message}")
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

    private fun parseErrorMessage(responseBody: String, statusCode: Int): String {
        return try {
            val json = JSONObject(responseBody)
            val errorObj = json.optJSONObject("error")
            val rawMsg = errorObj?.optString("message") ?: "کد خطای سرور: $statusCode"
            when (statusCode) {
                400 -> "درخواست نامعتبر است: $rawMsg"
                403 -> "دسترسی به این مدل با کلید فعلی امکان‌پذیر نیست یا سهمیه تمام شده است."
                404 -> "مدل درخواستی یافت نشد یا در دسترس نیست."
                429 -> "محدودیت تعداد درخواست در دقیقه (Rate Limit). لطفاً چند لحظه صبر نمایید."
                500, 503 -> "سرورهای هوش مصنوعی موقتاً در حال پردازش سنگین هستند. لطفاً دوباره تلاش کنید."
                else -> rawMsg
            }
        } catch (e: Exception) {
            "خطای سرور ($statusCode)"
        }
    }
}
