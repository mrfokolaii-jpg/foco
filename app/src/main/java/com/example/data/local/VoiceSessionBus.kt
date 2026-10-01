package com.example.data.local

/**
 * Tiny bridge between the floating widget (a Service) and the chat screen (a ViewModel).
 *
 * The widget no longer keeps its own transcript: every voice turn is written into the same
 * conversation the user is reading, so the chat screen shows the whole conversation.
 */
object VoiceSessionBus {

    /** Conversation used when the app has not opened a chat yet. */
    const val DEFAULT_CONVERSATION_ID = "foko-voice"

    /** Conversation the floating widget should append to. */
    @Volatile
    var conversationId: String = DEFAULT_CONVERSATION_ID

    /** Title used when the widget has to create that conversation itself. */
    const val VOICE_CONVERSATION_TITLE = "مکالمه صوتی فوکو"
}
