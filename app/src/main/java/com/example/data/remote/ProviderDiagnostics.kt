package com.example.data.remote

import com.example.data.model.AiProviderKind

/**
 * بررسی یکجای همهٔ سرویس‌ها با کلید خودِ کاربر.
 *
 * این تنها راهی است که کاربر می‌تواند بفهمد چرا یک سرویس کار نمی‌کند: به‌جای حدس زدن،
 * اپ واقعاً به هر سرویس وصل می‌شود و پیام خود سرور را برمی‌گرداند.
 *
 *   ۴۰۱ → کلید غلط   ۴۰۲ → اعتبار تمام شده   ۴۰۳ → دسترسی بسته   ۴۲۹ → سهمیه پر
 */
object ProviderDiagnostics {

    suspend fun check(
        provider: AiProviderKind,
        apiKey: String,
        baseUrl: String = "",
        model: String = ""
    ): ApiResult<String> = when (provider) {
        AiProviderKind.GEMINI -> checkGemini(apiKey)
        AiProviderKind.HUGGINGFACE -> checkCompat(HuggingFaceProvider.BASE_URL, apiKey, model)
        AiProviderKind.OPENROUTER -> checkCompat(OpenRouterProvider.BASE_URL, apiKey, model)
        AiProviderKind.GROQ -> checkCompat(GroqProvider.BASE_URL, apiKey, model)
        AiProviderKind.XAI_GROK -> checkCompat(XaiGrokProvider.BASE_URL, apiKey, model)
        AiProviderKind.CUSTOM, AiProviderKind.NINE_ROUTER ->
            OpenAiCompatProvider.testConnection(baseUrl, apiKey, model)
        AiProviderKind.NONE -> ApiResult.Error("هیچ سرویسی وصل نیست.")
    }

    private suspend fun checkGemini(apiKey: String): ApiResult<String> {
        if (apiKey.isBlank()) return ApiResult.Error("کلید Gemini وارد نشده است.")
        return when (val result = GeminiModels.listDetailed(apiKey)) {
            is ApiResult.Success -> ApiResult.Success("کار می‌کند — ${result.data.size} مدل در دسترس است")
            is ApiResult.Error -> ApiResult.Error(result.message)
        }
    }

    /**
     * برای سرویس‌های سازگار با OpenAI: اگر مسیر `/models` بود از آن استفاده می‌کنیم
     * (ارزان‌ترین راه)، و اگر سرویس چنین مسیری نداشت یک پیام یک‌کلمه‌ای می‌فرستیم.
     */
    private suspend fun checkCompat(base: String, apiKey: String, model: String): ApiResult<String> {
        if (apiKey.isBlank()) return ApiResult.Error("کلید وارد نشده است.")
        return when (val result = OpenAiCompatProvider.listModelsDetailed(base, apiKey)) {
            is ApiResult.Success -> ApiResult.Success("کار می‌کند — ${result.data.size} مدل در دسترس است")
            is ApiResult.Error -> when (val probe = OpenAiCompatProvider.testConnection(base, apiKey, model)) {
                is ApiResult.Success -> ApiResult.Success("کار می‌کند")
                is ApiResult.Error -> ApiResult.Error(result.message)
            }
        }
    }
}
