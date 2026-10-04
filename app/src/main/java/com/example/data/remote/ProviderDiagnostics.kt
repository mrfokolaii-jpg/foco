package com.example.data.remote

import com.example.data.model.AiProviderKind
import com.example.data.offline.OfflineEngine
import com.example.data.offline.OfflineModelImporter
import java.io.File

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
        modelPath: String = ""
    ): ApiResult<String> = when (provider) {
        AiProviderKind.GEMINI -> checkGemini(apiKey)
        AiProviderKind.HUGGINGFACE -> checkCompat(HuggingFaceProvider.BASE_URL, apiKey)
        AiProviderKind.OFFLINE -> checkOffline(modelPath.ifBlank { apiKey })
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
     * برای Hugging Face: اگر مسیر `/models` بود از آن استفاده می‌کنیم (ارزان‌ترین راه)،
     * و اگر سرویس چنین مسیری نداشت یک پیام یک‌کلمه‌ای می‌فرستیم.
     */
    private suspend fun checkCompat(base: String, apiKey: String): ApiResult<String> {
        if (apiKey.isBlank()) return ApiResult.Error("کلید وارد نشده است.")
        return when (val result = OpenAiCompatProvider.listModelsDetailed(base, apiKey)) {
            is ApiResult.Success -> ApiResult.Success("کار می‌کند — ${result.data.size} مدل در دسترس است")
            is ApiResult.Error -> when (val probe = OpenAiCompatProvider.testConnection(base, apiKey)) {
                is ApiResult.Success -> ApiResult.Success("کار می‌کند")
                is ApiResult.Error -> ApiResult.Error(result.message)
            }
        }
    }

    /** مدل آفلاین: بدون اینترنت بررسی می‌شود — فایل هست؟ سالم است؟ موتور اجرایش می‌کند؟ */
    private fun checkOffline(path: String): ApiResult<String> {
        if (path.isBlank()) return ApiResult.Error("هیچ مدل آفلاینی انتخاب نشده است.")
        if (!OfflineEngine.isDeviceSupported()) {
            return ApiResult.Error("این گوشی arm64 نیست؛ اجرای مدل آفلاین پشتیبانی نمی‌شود.")
        }
        val file = File(path)
        if (!file.exists()) return ApiResult.Error("فایل مدل پیدا نشد: $path")
        if (!OfflineModelImporter.isGgufFile(file)) {
            return ApiResult.Error("این فایل یک مدل GGUF سالم نیست.")
        }
        val size = OfflineModelImporter.readableSize(file.length())
        val warning = OfflineModelImporter.sizeWarning(file.length())?.let { "\n$it" }.orEmpty()
        return ApiResult.Success(
            "فایل مدل سالم است ($size)$warning\n" +
                "برای اطمینان از اجرا، دکمهٔ «فعال‌سازی روی گوشی» را بزن."
        )
    }
}
