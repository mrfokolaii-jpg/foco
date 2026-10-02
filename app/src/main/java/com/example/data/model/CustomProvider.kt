package com.example.data.model

/**
 * A provider the user wired up by hand: any OpenAI-compatible endpoint plus its key.
 * The app keeps a list of these so several free services can be used at once — the
 * auto-rotation walks through them whenever one hits its limit.
 */
data class CustomProvider(
    val id: String,
    val label: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val enabled: Boolean = true
)

/**
 * Known free services, used to pre-fill the name and base URL.
 *
 * Only the label and the base URL are stored here — deliberately no model id, because the
 * available model names change and are different for every account. The app fetches the
 * real list from the provider itself (GET {baseUrl}/models) so nothing is guessed.
 */
data class ProviderPreset(
    val label: String,
    val baseUrl: String,
    val note: String
)

object ProviderPresets {

    val ALL: List<ProviderPreset> = listOf(
        ProviderPreset(
            "Groq",
            "https://api.groq.com/openai/v1",
            "بدون کارت اعتباری · ۱۰۰۰ درخواست در روز · خیلی سریع"
        ),
        ProviderPreset(
            "OpenRouter",
            "https://openrouter.ai/api/v1",
            "۱۷ مدل رایگان · ۵۰ درخواست در روز برای هر مدل"
        ),
        ProviderPreset(
            "NVIDIA NIM",
            "https://integrate.api.nvidia.com/v1",
            "سخاوتمندترین · ۱۰٬۰۰۰ درخواست در روز · ۱۰۰+ مدل"
        ),
        ProviderPreset(
            "Kilo Code (بدون کلید)",
            "https://api.kilo.ai/api/gateway",
            "هیچ کلیدی لازم نیست · ۲۰۰ درخواست در ساعت"
        ),
        ProviderPreset(
            "OVHcloud (بدون کلید)",
            "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1",
            "بدون ثبت‌نام · فقط ۲ درخواست در دقیقه"
        ),
        ProviderPreset(
            "Cloudflare Workers AI",
            "https://api.cloudflare.com/client/v4/accounts/{account_id}/ai/run",
            "۱۰٬۰۰۰ نرون در روز · جای {account_id} شناسه حساب خودت را بگذار"
        ),
        ProviderPreset(
            "Mistral",
            "https://api.mistral.ai/v1",
            "۱۰ دلار کردیت رایگان در ماه"
        ),
        ProviderPreset(
            "Z AI (GLM)",
            "https://open.bigmodel.cn/api/paas/v4",
            "مدل GLM رایگان با ۲۰۰ هزار کانتکست"
        ),
        ProviderPreset(
            "SiliconFlow",
            "https://api.siliconflow.cn/v1",
            "مدل‌های چینی رایگان"
        ),
        ProviderPreset(
            "ModelScope",
            "https://api-inference.modelscope.cn/v1",
            "پلن رایگان دائمی"
        ),
        ProviderPreset(
            "Ollama Cloud",
            "https://ollama.com/v1",
            "پلن رایگان ساعتی"
        ),
        ProviderPreset(
            "LLM7.io",
            "https://api.llm7.io/v1",
            "بدون ثبت‌نام، کلید اختیاری"
        ),
        ProviderPreset(
            "Cohere",
            "https://api.cohere.com/v2",
            "۱۰۰۰ تماس در ماه · فقط استفاده غیرتجاری"
        ),
        ProviderPreset(
            "Aion Labs",
            "https://api.aionlabs.ai/v1",
            "پلن رایگان دائمی"
        )
    )
}
