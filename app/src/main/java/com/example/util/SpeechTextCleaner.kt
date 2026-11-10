package com.example.util

import java.util.Locale

/**
 * Prepares chat/assistant text for TextToSpeech.
 *
 * - Keeps Persian and English letters and numbers
 * - Removes markdown and decorative symbols the user does not want spoken
 * - Collapses whitespace so the engine reads more naturally
 */
object SpeechTextCleaner {

    // Symbols that must never be spoken (user request).
    // Use raw string to avoid invalid escapes like \\- or \\&
    private val SKIP_CHARS = (
        """¢€¥^°={}[]✓™®©%?!;:"'*()/+\-&_$#@""" +
            "Δ§×÷π√·|~£‹›«»•·…—–−±≈≠≤≥∞∫∑∏∂∇" +
            "★☆✦✧❖◆◇○●□■△▲▼▽←→↑↓↔⇒⇔" +
            "`´‘’“”„‟″‴‹›«»"
        ).toSet()

    /**
     * Returns text safe for TTS, or empty if nothing readable remains.
     */
    fun forSpeech(raw: String?): String {
        if (raw.isNullOrBlank()) return ""

        var s = raw

        // Strip foko tool tags if model leaked them
        s = s.replace(Regex("(?is)<\\s*foko[_-]?tool\\b[^>]*>.*?</\\s*foko[_-]?tool\\s*>"), " ")
        s = s.replace(Regex("(?is)<\\s*foko[_-]?tool\\b[^>]*/\\s*>"), " ")

        // Markdown-ish noise
        s = s.replace(Regex("(?m)^\\s{0,3}#{1,6}\\s*"), "")
        s = s.replace(Regex("(?m)^\\s{0,3}[-*+]\\s+"), "")
        s = s.replace(Regex("(?m)^\\s{0,3}\\d+\\.\\s+"), "")
        s = s.replace(Regex("`{1,3}"), "")
        s = s.replace(Regex("\\*{1,3}|_{1,3}"), "")
        s = s.replace(Regex("\\[([^\\]]*)\\]\\([^)]*\\)"), "$1")
        s = s.replace(Regex("!\\[[^\\]]*\\]\\([^)]*\\)"), " ")
        s = s.replace(Regex("(?m)^\\s*[-*_]{3,}\\s*$"), " ")
        s = s.replace(Regex("(?m)^\\|.*\\|$"), " ")

        // URLs → skip reading the whole link
        s = s.replace(Regex("https?://\\S+"), " ")
        s = s.replace(Regex("www\\.\\S+"), " ")

        val out = StringBuilder(s.length)
        for (ch in s) {
            when {
                ch in SKIP_CHARS -> out.append(' ')
                ch.isWhitespace() -> out.append(' ')
                isReadableChar(ch) -> out.append(ch)
                ch in ".,،؛:!؟…" -> out.append(ch)
                else -> out.append(' ')
            }
        }

        return out.toString()
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }

    private fun isReadableChar(ch: Char): Boolean {
        val type = Character.getType(ch).toByte()
        return when (type) {
            Character.UPPERCASE_LETTER,
            Character.LOWERCASE_LETTER,
            Character.TITLECASE_LETTER,
            Character.MODIFIER_LETTER,
            Character.OTHER_LETTER,
            Character.DECIMAL_DIGIT_NUMBER,
            Character.LETTER_NUMBER,
            Character.NON_SPACING_MARK,
            Character.COMBINING_SPACING_MARK -> true
            else -> false
        }
    }

    /** Prefer Persian TTS when the text is mostly Persian/Arabic script. */
    fun preferredLocale(text: String): Locale {
        var fa = 0
        var en = 0
        for (ch in text) {
            when {
                ch in '\u0600'..'\u06FF' || ch in '\u0750'..'\u077F' ||
                    ch in '\uFB50'..'\uFDFF' || ch in '\uFE70'..'\uFEFF' -> fa++
                ch in 'A'..'Z' || ch in 'a'..'z' -> en++
            }
        }
        return if (fa >= en) Locale("fa", "IR") else Locale.US
    }
}
