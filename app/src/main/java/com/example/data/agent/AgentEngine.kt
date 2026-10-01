package com.example.data.agent

import android.content.Context
import com.example.data.local.ApiKeyStore
import com.example.data.model.CatalogModel
import com.example.data.model.ChatMessage
import com.example.data.model.MessageSender
import com.example.data.remote.AiRouter
import com.example.data.remote.ApiResult
import org.json.JSONObject

enum class AgentPhase { IDLE, LISTENING, THINKING, SPEAKING, ERROR }

sealed class AgentEvent {
    data class Status(val phase: AgentPhase, val message: String) : AgentEvent()
    data class ToolUsed(val name: String, val summary: String) : AgentEvent()
    data class Reply(val text: String) : AgentEvent()
    data class Failure(val message: String) : AgentEvent()

    /** Auto mode moved to another model (the previous one hit its limit). */
    data class ModelSwitched(val model: CatalogModel) : AgentEvent()
}

/**
 * The autonomous agent loop.
 *
 * It wraps any chat provider with a prompt-based tool protocol, so even the free
 * built-in provider can search the web, read the screen, open apps, save files and
 * commit knowledge back to GitHub. This is what makes FOKO "do everything" instead of
 * replying that it lacks a capability.
 */
object AgentEngine {

    private const val MAX_STEPS = 5

    /** How many times Auto mode may hop to another model inside one request. */
    private const val MAX_ROTATIONS = 6

    /**
     * True when a provider error means "this model is out of room right now", so the agent
     * should silently move on to the next model instead of failing the whole request.
     */
    private fun isQuotaError(message: String): Boolean {
        val m = message.lowercase()
        return m.contains("429") ||
            m.contains("rate limit") ||
            m.contains("too many requests") ||
            m.contains("quota") ||
            m.contains("capacity") ||
            m.contains("503") ||
            m.contains("سقف") ||
            m.contains("سهمیه") ||
            m.contains("محدودیت نرخ") ||
            m.contains("پاسخگو نیست")
    }

    fun buildSystemPrompt(context: Context, personaPrompt: String, userText: String): String {
        val memory = FokoMemory.contextBlock(context, userText)
        return buildString {
            appendLine(personaPrompt)
            appendLine()
            appendLine("تو یک عامل خودمختار (Autonomous Agent) هستی و ابزارهای زیر را واقعاً و همین حالا اجرا می‌کنی.")
            appendLine("قوانین مهم:")
            appendLine("۱. هرگز نگو «این قابلیت را ندارم»، «به عنوان مدل زبانی دسترسی ندارم» یا «در برنامه من این امکان نیست». به جای آن ابزار مناسب را اجرا کن.")
            appendLine("۲. اگر دانش یا ابزار کافی برای انجام کاری نداری، اول ابزار web_search را صدا بزن و صفحه‌ها را با read_web_page بخوان، سپس با harvest_knowledge یک پوشه دانش در گیت‌هاب کاربر بساز و اطلاعات را داخلش بارگذاری کن.")
            appendLine("۳. اگر پروژه یا کدی پیدا کردی که برای خودِ اپلیکیشن مفید است، با github_write_file آن را در مخزن بارگذاری کن و بعد با build_app آن را کامپایل کن؛ نتیجه کامپایل (موفق/ناموفق و لینک خروجی) را به کاربر گزارش بده و بگو «اپلیکیشن جدید آماده شد».")
            appendLine("۴. برای اجرای یک ابزار، پاسخ را دقیقاً به این شکل بده و هیچ متن اضافه‌ای ننویس:")
            appendLine("""<foko_tool>{"name":"نام_ابزار","args":{"کلید":"مقدار"}}</foko_tool>""")
            appendLine("۵. در هر نوبت فقط یک ابزار را صدا بزن. بعد از دیدن نتیجه، یا ابزار بعدی را صدا بزن یا پاسخ نهایی را بده.")
            appendLine("۶. پاسخ نهایی را بدون هیچ تگی، به فارسی روان، کامل و مفید بنویس.")
            appendLine()
            appendLine(FokoTools.describeForPrompt())
            if (memory.isNotBlank()) {
                appendLine()
                appendLine(memory)
            }
        }.trim()
    }

    suspend fun run(
        context: Context,
        userText: String,
        history: List<ChatMessage>,
        catalogModel: CatalogModel,
        keyStore: ApiKeyStore,
        personaPrompt: String,
        enableThinkingMode: Boolean = false,
        enableGoogleSearch: Boolean = false,
        /** Auto-mode fallbacks: tried in order whenever a model hits its limit. */
        rotationChain: List<CatalogModel> = emptyList(),
        onEvent: (AgentEvent) -> Unit = {}
    ): String {
        val systemPrompt = buildSystemPrompt(context, personaPrompt, userText)
        val working = history.toMutableList()
        working.add(ChatMessage(sender = MessageSender.USER, text = userText))

        var currentModel = catalogModel
        val remaining = rotationChain.filter { it.id != currentModel.id }.toMutableList()
        var rotations = 0

        onEvent(AgentEvent.Status(AgentPhase.THINKING, "در حال تحلیل درخواست..."))
        onEvent(AgentEvent.ModelSwitched(currentModel))

        var lastReply = ""
        var step = 0
        while (step < MAX_STEPS) {
            step++
            val result = AiRouter.chat(
                catalogModel = currentModel,
                conversationHistory = working,
                systemInstructionText = systemPrompt,
                keyStore = keyStore,
                enableThinkingMode = enableThinkingMode,
                enableGoogleSearch = enableGoogleSearch
            )
            when (result) {
                is ApiResult.Error -> {
                    val next = remaining.firstOrNull()
                    if (next != null && rotations < MAX_ROTATIONS && isQuotaError(result.message)) {
                        remaining.removeAt(0)
                        rotations++
                        currentModel = next
                        onEvent(AgentEvent.ModelSwitched(currentModel))
                        onEvent(
                            AgentEvent.Status(
                                AgentPhase.THINKING,
                                "این مدل به سقف رسید؛ مدل بعدی فعال شد: ${next.title}"
                            )
                        )
                        step-- // switching models must not eat a tool step
                        continue
                    }
                    onEvent(AgentEvent.Failure(result.message))
                    return "⚠️ ${result.message}"
                }
                is ApiResult.Success -> {
                    val reply = result.data.replyText.trim()
                    lastReply = reply
                    val call = parseToolCall(reply)
                    if (call == null) {
                        onEvent(AgentEvent.Reply(reply))
                        return reply
                    }
                    val toolName = call.optString("name")
                    val args = call.optJSONObject("args") ?: JSONObject()
                    onEvent(AgentEvent.Status(AgentPhase.THINKING, "اجرای ابزار: $toolName"))
                    val outcome = FokoTools.execute(context, toolName, args)
                    onEvent(AgentEvent.ToolUsed(toolName, outcome.text.take(400)))
                    working.add(ChatMessage(sender = MessageSender.ASSISTANT, text = reply))
                    working.add(
                        ChatMessage(
                            sender = MessageSender.USER,
                            text = "نتیجه اجرای ابزار $toolName:\n${outcome.text}\n\n" +
                                "اگر به اطلاعات یا ابزار بیشتری نیاز داری مرحله بعد را انجام بده، در غیر این صورت پاسخ نهایی فارسی را بنویس."
                        )
                    )
                }
            }
        }
        val fallback = lastReply.ifBlank { "نتوانستم پاسخی تولید کنم. لطفاً دوباره تلاش کن." }
        onEvent(AgentEvent.Reply(fallback))
        return fallback
    }

    fun parseToolCall(reply: String): JSONObject? {
        val marker = "<foko_tool>"
        val endMarker = "</foko_tool>"
        val startIdx = reply.indexOf(marker)
        val candidate: String? = if (startIdx >= 0) {
            val bodyStart = startIdx + marker.length
            val endIdx = reply.indexOf(endMarker, bodyStart)
            val region = if (endIdx > bodyStart) reply.substring(bodyStart, endIdx) else reply.substring(bodyStart)
            extractFirstJsonObject(region)
        } else {
            val trimmed = reply.trim()
            if (trimmed.startsWith("{") && trimmed.contains("\"name\"")) extractFirstJsonObject(trimmed) else null
        }
        val raw = candidate ?: return null
        return try {
            val obj = JSONObject(raw)
            if (obj.optString("name").isBlank()) null else obj
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns the first balanced JSON object in [text], respecting nested braces and
     * quoted strings. A plain regex cannot do this because tool arguments are nested.
     */
    private fun extractFirstJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return text.substring(start, i + 1)
                    }
                }
            }
        }
        return null
    }
}
