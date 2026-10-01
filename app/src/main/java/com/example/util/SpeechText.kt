package com.example.util

/**
 * Text preparation for the Persian text-to-speech engine.
 *
 * The engine reads punctuation literally ("ویرگول", "ستاره") and chokes on emoji and markdown,
 * so everything except Persian/Latin letters, digits, spaces and the half-space is removed
 * before speaking.
 */
object SpeechText {

    private val CODE_BLOCK = Regex("```[\\s\\S]*?```")
    private val MARKDOWN_MARKS = Regex("[*#_~`^>|]+")
    private val BRACKETS = Regex("[\\[\\](){}<>«»\"'“”‘’]")
    private val URL = Regex("https?://\\S+")
    private val BULLET = Regex("^\\s*[-•●▪◦‣∙]\\s*", RegexOption.MULTILINE)
    private val LIST_NUMBER = Regex("^\\s*\\d+[.)]\\s*", RegexOption.MULTILINE)

    /** Anything that is not a Persian/Latin letter, a digit, whitespace or the half-space. */
    private val NOT_SPEAKABLE = Regex("[^\\p{IsArabic}\\p{IsAlphabetic}\\p{Nd}\\s\\u200C\\u200F]")

    private val MULTI_SPACE = Regex("[\\s\\u200C]+")

    fun forSpeech(raw: String): String {
        if (raw.isBlank()) return ""
        var text = raw
        text = CODE_BLOCK.replace(text, " ")
        text = URL.replace(text, " ")
        text = BULLET.replace(text, "")
        text = LIST_NUMBER.replace(text, "")
        text = MARKDOWN_MARKS.replace(text, " ")
        text = BRACKETS.replace(text, " ")
        text = NOT_SPEAKABLE.replace(text, " ")
        // Half-space becomes a real space so the voice does not swallow the word boundary.
        text = MULTI_SPACE.replace(text, " ")
        return text.trim()
    }
}
