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
        return runtimeApiKeyOverride.trim()
    }

    fun isApiKeyConfigured(): Boolean {
        return getApiKey().isNotBlank()
    }

    suspend fun generateChatResponse(
        conversationHistory: List<ChatMessage>,
        systemInstructionText: String,
        selectedModel: AiModelOption,
        enableThinkingMode: Boolean = false,
        enableGoogleSearch: Boolean = false
    ): ApiResult<ChatApiResponse> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext ApiResult.Error("کلید API جِمینای تنظیم نشده است.")
        }

        val effectiveModel = if (enableThinkingMode) {
            GeminiModelConstants.MODEL_PRO
        } else if (enableGoogleSearch && selectedModel == AiModelOption.LITE) {
            GeminiModelConstants.MODEL_FLASH
        } else {
            selectedModel.modelId
        }

        try {
            val requestJson = JSONObject()

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

            val contentsArray = JSONArray()
            conversationHistory.takeLast(12).forEach { msg ->
                val contentObj = JSONObject()
                contentObj.put("role", if (msg.sender == MessageSender.USER) "user" else "model")
                val partsArray = JSONArray()

                if (msg.text.isNotBlank()) {
                    val textPart = JSONObject()
                    textPart.put("text", msg.text)
                    partsArray.put(textPart)
                }

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

            val genConfig = JSONObject()
            genConfig.put("temperature", if (enableThinkingMode) 0.4 else 0.7)

            if (enableThinkingMode && effectiveModel == GeminiModelConstants.MODEL_PRO) {
                val thinkingConfig = JSONObject()
                thinkingConfig.put("thinkingLevel", "HIGH")
                genConfig.put("thinkingConfig", thinkingConfig)
            }
            requestJson.put("generationConfig", genConfig)

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
                return@withContext ApiResult.Error(parseErrorMessage(responseBody, response.code))
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

    suspend fun generateOrEditImage(config: ImageGenerationConfig): ApiResult<GeneratedImageResult> =
        withContext(Dispatchers.IO) {
            ApiResult.Error("ساخت تصویر جمینای در این نسخه غیرفعال است.")
        }

    suspend fun generateMusic(config: MusicGenerationConfig): ApiResult<GeneratedAudioResult> =
        withContext(Dispatchers.IO) {
            ApiResult.Error("تولید موسیقی جمینای در این نسخه غیرفعال است.")
        }

    suspend fun generateVideo(config: VideoGenerationConfig): ApiResult<GeneratedVideoResult> =
        withContext(Dispatchers.IO) {
            ApiResult.Error("تولید ویدیو جمینای در این نسخه غیرفعال است.")
        }

    private fun parseErrorMessage(body: String, code: Int): String {
        return try {
            val json = JSONObject(body)
            val err = json.optJSONObject("error")
            err?.optString("message")?.takeIf { it.isNotBlank() }
                ?: "خطای سرور ($code)"
        } catch (_: Exception) {
            "خطای سرور ($code)"
        }
    }
}
