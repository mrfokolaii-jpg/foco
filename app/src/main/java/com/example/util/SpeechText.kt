package com.example.util

import android.content.Context
import java.util.Locale

/**
 * آماده‌سازی متن برای موتور خواندن فارسی (Text-to-Speech).
 *
 * موتورهای صدا اگر متن خام بگیرند، نشانه‌ها را حرف‌به‌حرف می‌خوانند ("ستاره"، "ویرگول")،
 * عددهای لاتین را غلط می‌خوانند و کلمه‌های انگلیسی را با لهجه‌ی بد تلفظ می‌کنند.
 *
 * این کلاس متن را قبل از خواندن «فارسی‌خوان» می‌کند:
 *  ۱. کد، لینک، مارک‌داون و پرانتز حذف می‌شود.
 *  ۲. نشانه‌ها به کلمه تبدیل می‌شوند (٪ → «درصد»، & → «و»).
 *  ۳. حروف عربی (ي، ك، ة) به فارسی برگردانده می‌شود.
 *  ۴. عددها به حروف فارسی نوشته می‌شوند (۱۴۰۲ → «هزار و چهارصد و دو»).
 *  ۵. کلمه‌های انگلیسی/فنی با کمک فایل داده‌ی «persian/lexicon.tsv» فارسی‌نویسی می‌شوند.
 *
 * فایل داده در `assets/persian/lexicon.tsv` است؛ ساختار هر خط: `<نوشته>\t<خوانده>`.
 * برای اضافه‌کردن کلمه‌ی تازه فقط همان فایل را ویرایش کنید — نیازی به تغییر کد نیست.
 */
object SpeechText {

    // ---------------------------------------------------------------- regex ----

    private val CODE_BLOCK = Regex("```[\\s\\S]*?```")
    private val URL = Regex("https?://\\S+")
    private val BULLET = Regex("^\\s*[-•●▪◦‣∙]\\s*", RegexOption.MULTILINE)
    private val LIST_NUMBER = Regex("^\\s*\\d+[.)-]\\s*", RegexOption.MULTILINE)
    private val MARKDOWN_MARKS = Regex("[*#_~`^>|]+")
    private val BRACKETS = Regex("[\\[\\](){}<>«»\"'“”‘’]")

    /** هر چیزی جز حرف فارسی/لاتین، رقم، فاصله و نیم‌فاصله. */
    private val NOT_SPEAKABLE = Regex("[^\\p{IsArabic}\\p{IsAlphabetic}\\p{Nd}\\s\\u200C\\u200F]")

    private val MULTI_SPACE = Regex("[\\s\\u200C\\u200F]+")

    // در Kotlin/Java، `\d` فقط رقم‌های لاتین (0-9) را می‌گیرد؛ پس رقم‌های فارسی (۰-۹)
    // را هم صریح می‌نویسیم تا متن فارسی هم درست تبدیل شود.
    private val THOUSAND_SEP = Regex("(?<=[0-9\\u06F0-\\u06F9]),(?=[0-9\\u06F0-\\u06F9]{3}(?![0-9\\u06F0-\\u06F9]))")

    /** ممیز اعشار: نقطه یا ویرگول بین رقم‌ها. */
    private val DECIMAL_SEP = Regex("(?<=[0-9\\u06F0-\\u06F9])[.،](?=[0-9\\u06F0-\\u06F9])")

    /** یک رشته‌ی رقمی پیوسته (لاتین یا فارسی). */
    private val DIGIT_RUN = Regex("[0-9\\u06F0-\\u06F9]{1,15}")

    /** یک کلمه‌ی لاتین/فنی/رقمی برای جست‌وجو در فایل داده. */
    private val LATIN_TOKEN = Regex("[A-Za-z][A-Za-z0-9_+#.\\-]*")

    // -------------------------------------------------------------- symbols ----

    /** نشانه‌ها و واحدهایی که باید کلمه خوانده شوند. */
    private val SYMBOLS: List<Pair<String, String>> = listOf(
        "٪" to " درصد ",
        "%" to " درصد ",
        "‰" to " در هزار ",
        "&" to " و ",
        "@" to " ات ",
        "×" to " ضربدر ",
        "÷" to " تقسیم بر ",
        "+" to " به‌علاوه ",
        "=" to " مساوی ",
        "–" to " تا ",
        "—" to " تا ",
        "…" to " ",
        "﷼" to " ریال ",
        "₿" to " بیت‌کوین ",
        "✓" to " ",
        "✔" to " ",
        "★" to " ",
        "→" to " ",
        "←" to " "
    )

    // ------------------------------------------------------------ number words ----

    private val ONES = arrayOf(
        "", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه",
        "ده", "یازده", "دوازده", "سیزده", "چهارده", "پانزده", "شانزده",
        "هفده", "هجده", "نوزده"
    )

    private val TENS = arrayOf(
        "", "", "بیست", "سی", "چهل", "پنجاه", "شصت", "هفتاد", "هشتاد", "نود"
    )

    private val HUNDREDS = arrayOf(
        "", "صد", "دویست", "سیصد", "چهارصد", "پانصد", "ششصد", "هفتصد", "هشتصد", "نهصد"
    )

    private val SCALES = arrayOf("", "هزار", "میلیون", "میلیارد", "بیلیون")

    private val DIGIT_NAMES = arrayOf("صفر", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه")

    // --------------------------------------------------------------- lexicon ----

    /** نوشته → خوانده. از فایل `assets/persian/lexicon.tsv` پر می‌شود. */
    @Volatile
    private var lexicon: Map<String, String> = emptyMap()

    @Volatile
    private var lexiconLoaded = false

    /** قاعده‌های آماده‌ی جایگزینی، از عبارت‌های بلند به کوتاه. یک‌بار ساخته می‌شوند. */
    @Volatile
    private var lexiconRules: List<Pair<Regex, String>> = emptyList()

    /**
     * فایل داده‌ی فارسی را می‌خواند. چند بار صدا زدن مشکلی ندارد و فقط یک‌بار انجام می‌شود.
     * هر جایی که Context در دسترس بود (چت، ویجت، تنظیمات صدا) صدا بزنید.
     */
    fun init(context: Context) {
        if (lexiconLoaded) return
        synchronized(this) {
            if (lexiconLoaded) return
            lexicon = loadLexicon(context)
            lexiconRules = lexicon.keys
                .sortedByDescending { it.length }
                .mapNotNull { key ->
                    val value = lexicon[key] ?: return@mapNotNull null
                    Regex(
                        "(?<![\\p{L}\\p{N}])" + Regex.escape(key) + "(?![\\p{L}\\p{N}])",
                        RegexOption.IGNORE_CASE
                    ) to value
                }
            lexiconLoaded = true
        }
    }

    private fun loadLexicon(context: Context): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        try {
            context.assets.open("persian/lexicon.tsv").use { stream ->
                stream.bufferedReader(Charsets.UTF_8).forEachLine { raw ->
                    val line = raw.trimEnd()
                    if (line.isBlank() || line.startsWith("#")) return@forEachLine
                    val tab = line.indexOf('\t')
                    if (tab <= 0) return@forEachLine
                    val key = line.substring(0, tab).trim()
                    val value = line.substring(tab + 1).trim()
                    if (key.isNotEmpty() && value.isNotEmpty()) {
                        map[key.lowercase(Locale.ROOT)] = value
                    }
                }
            }
        } catch (_: Exception) {
            // فایل داده نبود؟ خواندن با قاعده‌های پیش‌فرض هم کار می‌کند.
        }
        return map
    }

    // ----------------------------------------------------------------- entry ----

    fun forSpeech(raw: String): String {
        if (raw.isBlank()) return ""
        var text = raw

        text = CODE_BLOCK.replace(text, " ")
        text = URL.replace(text, " ")
        text = BULLET.replace(text, "")
        text = LIST_NUMBER.replace(text, "")
        text = MARKDOWN_MARKS.replace(text, " ")
        text = BRACKETS.replace(text, " ")

        // ۱. نشانه‌ها → کلمه
        SYMBOLS.forEach { (symbol, word) -> text = text.replace(symbol, word) }

        // ۲. عربی → فارسی و رقم‌های عربی → فارسی
        text = normalizeArabic(text)

        // ۳. جداکننده‌ی هزارگان حذف، ممیز به کلمه
        text = THOUSAND_SEP.replace(text, "")
        text = DECIMAL_SEP.replace(text, " ممیز ")

        // ۴. کلمه‌های انگلیسی/فنی → فارسی (با کمک فایل داده)
        text = applyLexicon(text)

        // ۵. عدد → حروف فارسی
        text = DIGIT_RUN.replace(text) { match -> numberToWords(match.value) }

        // ۶. هر چیز ناخواندنی حذف
        text = NOT_SPEAKABLE.replace(text, " ")
        text = MULTI_SPACE.replace(text, " ")

        return text.trim()
    }

    // ------------------------------------------------------------- internals ----

    private fun normalizeArabic(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            sb.append(
                when (ch) {
                    'ي', 'ى' -> 'ی'
                    'ك', 'ڪ' -> 'ک'
                    'ة' -> 'ه'
                    'ۀ' -> 'ه'
                    'ؤ' -> 'و'
                    'إ', 'أ', 'ٱ' -> 'ا'
                    'ﻻ', 'ﻷ', 'ﻹ', 'ﻵ' -> 'لا'
                    'ـ' -> ' '            // کشیده
                    '\u064B', '\u064C', '\u064D',
                    '\u064E', '\u064F', '\u0650',
                    '\u0651', '\u0652', '\u0653',
                    '\u0654', '\u0655' -> ' '  // اِعراب
                    in '\u0660'..'\u0669' -> ('۰' + (ch - '\u0660'))  // ٠-٩ عربی
                    else -> ch
                }
            )
        }
        return sb.toString()
    }

    private fun applyLexicon(input: String): String {
        val rules = lexiconRules
        if (rules.isEmpty()) return input
        var result = input
        for ((pattern, replacement) in rules) {
            result = pattern.replace(result, replacement)
        }
        return result
    }

    /**
     * عدد را به حروف فارسی برمی‌گرداند.
     * عددهای معمولی (تا ۹ رقم) مثل عدد خوانده می‌شوند («۱۴۰۲» → «هزار و چهارصد و دو»)؛
     * رشته‌های بلندتر و آن‌هایی که با صفر شروع می‌شوند (شماره تلفن، کد ملی و کد پستی)
     * رقم‌به‌رقم خوانده می‌شوند.
     */
    fun numberToWords(digits: String): String {
        val startsWithZero = digits.isNotEmpty() && (digits[0] == '0' || digits[0] == '۰')
        if (digits.length > 9 || startsWithZero) {
            val sb = StringBuilder()
            for (ch in digits) {
                val d = when (ch) {
                    in '۰'..'۹' -> ch - '۰'
                    in '0'..'9' -> ch - '0'
                    else -> -1
                }
                if (d in 0..9) {
                    if (sb.isNotEmpty()) sb.append(' ')
                    sb.append(DIGIT_NAMES[d])
                }
            }
            return sb.toString()
        }

        var remaining = digits.map { ch ->
            when (ch) {
                in '۰'..'۹' -> ('0' + (ch - '۰'))
                else -> ch
            }
        }.joinToString("").toLongOrNull() ?: return digits

        if (remaining == 0L) return "صفر"

        val groups = ArrayList<Int>()
        while (remaining > 0) {
            groups.add((remaining % 1000).toInt())
            remaining /= 1000
        }

        val parts = ArrayList<String>()
        for (i in groups.indices.reversed()) {
            val group = groups[i]
            if (group == 0) continue
            val scale = if (i < SCALES.size) SCALES[i] else ""
            val groupWords = threeDigitToWords(group)
            parts.add(
                when {
                    scale.isEmpty() -> groupWords
                    // فارسی‌زبان‌ها «یک هزار» نمی‌گویند؛ فقط «هزار».
                    group == 1 && scale == "هزار" -> "هزار"
                    else -> "$groupWords $scale"
                }
            )
        }
        return parts.joinToString(" و ")
    }

    private fun threeDigitToWords(value: Int): String {
        val pieces = ArrayList<String>()
        val hundreds = value / 100
        val rest = value % 100
        if (hundreds > 0) pieces.add(HUNDREDS[hundreds])
        if (rest in 1..19) {
            pieces.add(ONES[rest])
        } else if (rest >= 20) {
            val tens = rest / 10
            val ones = rest % 10
            pieces.add(TENS[tens] + if (ones > 0) " و " + ONES[ones] else "")
        }
        return pieces.joinToString(" و ")
    }
}
