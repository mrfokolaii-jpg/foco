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

    fun buildSystemPrompt(context: Context, personaPrompt: String, userText: String): String {
        val memory = FokoMemory.contextBlock(context, userText)
        return buildString {
            appendLine(personaPrompt)
            appendLine()
            appendLine("تو یک عامل خودمختار (Autonomous Agent) هستی و ابزارهای زیر را واقعاً و همین حالا اجرا می‌کنی.")
            appendLine("قوانین مهم:")
            appendLine("۱. هرگز نگو «این قابلیت را ندارم»، «به عنوان مدل زبانی دسترسی ندارم» یا «در برنامه من این امکان نیست». به جای آن ابزار مناسب را اجرا کن.")
            appendLine("۲. اگر دانش کافی برای انجام کاری نداری، اول ابزار web_search را صدا بزن و صفحه‌ها را با read_web_page بخوان، سپس با harvest_knowledge دانش را در حافظه و گیت‌هاب کاربر بارگذاری کن.")
            appendLine("۳. برای اجرای یک ابزار، پاسخ را دقیقاً به این شکل بده و هیچ متن اضافه‌ای ننویس:")
            appendLine("""<foko_tool>{\"name\":\"نام_ابزار\",\"args\":{\"کلید\":\"مقدار\"}}</foko_tool>""")
            appendLine("۴. در هر نوبت فقط یک ابزار را صدا بزن. بعد از دیدن نتیجه، یا ابزار بعدی را صدا بزن یا پاسخ نهایی را بده.")
            appendLine("۵. پاسخ نهایی را بدون هیچ تگی، به فارسی روان، کامل و مفید بنویس.")
            appendLine("۶. تگ را کامل و درست بنویس: با <foko_tool> شروع و با </foko_tool> تمام شود. JSON باید معتبر باشد.")
            appendLine("۷. هر وقت کاربر درباره صفحه گوشی، برنامه باز، یا «چی می‌بینی / صفحه رو ببین» پرسید، اول ابزار read_screen را اجرا کن؛ برای این کار هرگز web_search نکن.")
            appendLine("۸. وقتی نتیجه ابزار رسید، اگر کافی است، فقط پاسخ نهایی فارسی بده و ابزار را تکرار نکن.")
            appendLine("۹. برای کار روی گوشی (باز کردن برنامه، زدن دکمه، نوشتن پیام) مرحله به مرحله پیش برو: open_app، بعد read_screen، بعد tap_text یا type_text، و بعد از هر کار دوباره read_screen.")
            appendLine("۱۰. قبل از فرستادن هر پیام یا جواب به اسم کاربر (reply_notification یا type_text با submit)، متن را به کاربر بگو و فقط بعد از تأیید صریحش بفرست.")
            appendLine("۱۱. برای یادآور و تقویم، تاریخ شمسی را خودت به میلادی تبدیل کن و در قالب 2026-11-12 08:00 بده.")
            appendLine("۱۲. اگر ابزار بومی (function calling) در دسترس است از همان استفاده کن؛ در غیر این صورت از تگ foko_tool.")
            appendLine()
            appendLine(com.example.util.PersianDate.nowLine())
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
        /** تکه‌های متن پاسخ نهایی، همان لحظه که می‌رسند (برای خواندن سریع‌تر). */
        onDelta: ((String) -> Unit)? = null,
        onEvent: (AgentEvent) -> Unit = {}
    ): String {
        val voicePrefs = com.example.data.local.VoicePrefs(context)
        val tools = if (voicePrefs.nativeToolsEnabled) FokoTools.toolDeclarations() else null
        val streamTo = if (voicePrefs.streamingEnabled) onDelta else null
        val systemPrompt = buildSystemPrompt(context, personaPrompt, userText)
        val working = history.toMutableList()
        working.add(ChatMessage(sender = MessageSender.USER, text = userText))

        onEvent(AgentEvent.Status(AgentPhase.THINKING, "در حال تحلیل درخواست..."))

        var lastReply = ""
        var emptyRetries = 0
        var lastToolSummary = ""
        var step = 0
        while (step < MAX_STEPS) {
            step++
            val result = AiRouter.chat(
                catalogModel = catalogModel,
                conversationHistory = working,
                systemInstructionText = systemPrompt,
                keyStore = keyStore,
                enableThinkingMode = enableThinkingMode,
                enableGoogleSearch = enableGoogleSearch,
                tools = tools,
                onDelta = streamTo?.let { sink -> deltaFilter(sink) }
            )
            when (result) {
                is ApiResult.Error -> {
                    onEvent(AgentEvent.Failure(result.message))
                    return "⚠️ ${result.message}"
                }
                is ApiResult.Success -> {
                    val reply = result.data.replyText.orEmpty().trim()
                    if (isEmptyReply(reply)) {
                        // مدل جواب خالی یا «null» داد؛ یک بار دیگر تلاش کن
                        emptyRetries++
                        if (emptyRetries <= 1) continue
                        val msg = "متأسفم، مدل جوابی نداد. لطفاً دوباره بگو یا مدل دیگری انتخاب کن."
                        onEvent(AgentEvent.Reply(msg))
                        return msg
                    }
                    lastReply = reply
                    val call = parseToolCall(reply)
                    if (call == null) {
                        val clean = stripToolMarkup(reply).ifBlank {
                            "نتوانستم درخواست را کامل انجام دهم. لطفاً دوباره و ساده‌تر بگو."
                        }
                        onEvent(AgentEvent.Reply(clean))
                        return clean
                    }
                    val toolName = call.optString("name")
                    val args = call.optJSONObject("args") ?: JSONObject()
                    onEvent(AgentEvent.Status(AgentPhase.THINKING, "اجرای ابزار: $toolName"))
                    val outcome = FokoTools.execute(context, toolName, args)
                    onEvent(AgentEvent.ToolUsed(toolName, outcome.text.take(400)))
                    lastToolSummary = outcome.text.take(600)
                    working.add(ChatMessage(sender = MessageSender.ASSISTANT, text = reply))
                    working.add(
                        ChatMessage(
                            sender = MessageSender.USER,
                            text = "نتیجه اجرای ابزار $toolName:\n${outcome.text}\n\n" +
                                "اگر به اطلاعات یا ابزار بیشتری نیاز داری مرحله بعد را انجام بده، در غیر این صورت پاسخ نهایی فارسی را بنویس.",
                            imageBase64 = outcome.imageBase64
                        )
                    )
                }
            }
        }
        // هرگز متن خام ابزار را به کاربر نشان نده یا بلند نخوان
        val cleanedLast = if (parseToolCall(lastReply) != null) "" else stripToolMarkup(lastReply)
        val fallback = cleanedLast.ifBlank {
            if (lastToolSummary.isNotBlank()) "این چیزی است که پیدا کردم:\n$lastToolSummary"
            else "نتوانستم پاسخی تولید کنم. لطفاً دوباره تلاش کن."
        }
        onEvent(AgentEvent.Reply(fallback))
        return fallback
    }

    /**
     * تکه‌ها را فقط وقتی جلو می‌فرستد که مطمئن باشد متن عادی است، نه تگ ابزار؛
     * تا متن خام ابزار هیچ‌وقت خوانده نشود.
     */
    private fun deltaFilter(sink: (String) -> Unit): (String) -> Unit {
        val buffer = StringBuilder()
        var decided = false
        var isTool = false
        return { piece ->
            if (!isTool) {
                if (decided) {
                    if (piece.contains("foko", true) || piece.contains("<")) isTool = true else sink(piece)
                } else {
                    buffer.append(piece)
                    val t = buffer.toString().trimStart()
                    if (t.contains("foko", true) || t.startsWith("<") || t.startsWith("{")) {
                        isTool = true
                    } else if (t.length >= 16) {
                        decided = true
                        sink(buffer.toString())
                    }
                }
            }
        }
    }

    private fun isEmptyReply(reply: String): Boolean {
        val r = reply.trim().trim('"').lowercase()
        return r.isEmpty() || r == "null" || r == "undefined" || r == "none"
    }

    /** حذف هر تکه تگ یا JSON ابزار از متنی که قرار است به کاربر نشان داده شود. */
    fun stripToolMarkup(text: String): String {
        var s = text
        s = s.replace(Regex("(?is)<?\\s*foko[_-]?tool\\s*>?.*?</?\\s*foko[_-]?tool\\s*>?"), " ")
        s = s.replace(Regex("(?i)</?\\s*foko[_-]?tool\\s*>?"), " ")
        s = s.replace(Regex("(?s)\\{\\s*\"name\"\\s*:.*?\"args\".*?\\}\\s*\\}"), " ")
        return s.replace(Regex("[ \\t]{2,}"), " ").trim()
    }

    fun parseToolCall(reply: String): JSONObject? {
        // مدل‌های ضعیف گاهی تگ را خراب می‌کنند:
        // foko_tool>{...}</foko_tool>  یا  <foko_tool>{...  یا JSON ناقص
        val normalized = reply
            .replace(Regex("(?i)<\\s*foko[_-]?tool\\s*>"), "<foko_tool>")
            .replace(Regex("(?i)</\\s*foko[_-]?tool\\s*>"), "</foko_tool>")
            .replace(Regex("(?i)(?<!<)foko[_-]?tool\\s*>"), "<foko_tool>")

        val marker = "<foko_tool>"
        val endMarker = "</foko_tool>"
        val startIdx = normalized.indexOf(marker)
        val candidate: String? = if (startIdx >= 0) {
            val bodyStart = startIdx + marker.length
            val endIdx = normalized.indexOf(endMarker, bodyStart)
            val region = if (endIdx > bodyStart) normalized.substring(bodyStart, endIdx) else normalized.substring(bodyStart)
            extractFirstJsonObject(region) ?: tryRepairJson(region)
        } else {
            // بدون تگ: اگر JSON با name به نظر می‌رسد
            val trimmed = normalized.trim()
            if (trimmed.contains("\"name\"") || trimmed.contains("'name'")) {
                extractFirstJsonObject(trimmed) ?: tryRepairJson(trimmed)
            } else null
        }
        val raw = candidate ?: return null
        return try {
            val obj = normalizeCall(JSONObject(raw))
            if (obj.optString("name").isBlank()) null else obj
        } catch (_: Exception) {
            // یک بار دیگر با تعمیر سعی کن
            try {
                val repaired = tryRepairJson(raw) ?: return null
                val obj = normalizeCall(JSONObject(repaired))
                if (obj.optString("name").isBlank()) null else obj
            } catch (_: Exception) {
                null
            }
        }
    }

    /** کلیدها و مقدارهایی مثل "<args" یا "mrfokolaii-jpg/<foco" را تمیز می‌کند. */
    private fun normalizeCall(obj: JSONObject): JSONObject {
        val out = JSONObject()
        val name = obj.optString("name").trim().trim('<', '>', ' ')
        out.put("name", name)
        var args: JSONObject? = obj.optJSONObject("args")
        if (args == null) {
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (k.trim('<', '>', ' ', '"').equals("args", ignoreCase = true)) {
                    args = obj.optJSONObject(k)
                    break
                }
            }
        }
        val cleanArgs = JSONObject()
        args?.let { a ->
            val keys = a.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = a.get(k)
                val cleanKey = k.trim('<', '>', ' ')
                cleanArgs.put(cleanKey, if (v is String) v.replace("<", "").replace(">", "").trim() else v)
            }
        }
        out.put("args", cleanArgs)
        return out
    }

    /**
     * تعمیر رایج‌ترین خرابکاری‌های مدل‌های ضعیف روی JSON ابزار.
     * مثال:  {"name":"x",> args":{...}}  یا کوتیشن‌های تک
     */
    private fun tryRepairJson(text: String): String? {
        var s = text.trim()
        // حذف متن قبل از اولین {
        val brace = s.indexOf('{')
        if (brace < 0) return null
        s = s.substring(brace)
        // حذف متن بعد از آخرین }
        val lastBrace = s.lastIndexOf('}')
        if (lastBrace > 0) s = s.substring(0, lastBrace + 1)

        // "<args"  یا  ">args"  →  "args"
        s = s.replace(Regex("\"\\s*[<>]+\\s*args\""), "\"args\"")
        // ",> args  →  ,"args
        s = s.replace(Regex(",\\s*>\\s*args"), ",\"args\"")
        s = s.replace(Regex(",\\s*>\\s*\"args\""), ",\"args\"")
        // name بدون کوتیشن درست
        s = s.replace(Regex("\\{\\s*name\\s*:"), "{\"name\":")
        s = s.replace(Regex(",\\s*args\\s*:"), ",\"args\":")
        // کوتیشن‌های تکی به دوتایی (ساده)
        if (s.contains("'") && !s.contains("\"")) {
            s = s.replace('\'', '"')
        }
        return try {
            JSONObject(s)
            s
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
