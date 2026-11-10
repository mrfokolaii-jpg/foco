package com.example.ui.screens.chat

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ChatConversationEntity
import com.example.data.model.ChatMessage
import com.example.data.model.MessageSender
import com.example.ui.theme.PersianTurquoise

@Composable
fun PluginOptionRow(
    emoji: String,
    title: String,
    description: String,
    isConnected: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) PersianTurquoise.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = emoji, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isConnected) {
                Icon(Icons.Default.CheckCircle, null, tint = PersianTurquoise, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun ModelOptionCard(
    title: String,
    description: String,
    tag: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) PersianTurquoise.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Surface(
                    color = if (isSelected) PersianTurquoise else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ModernChatInputBar(
    inputText: String,
    onTextChanged: (String) -> Unit,
    activeModelLabel: String,
    isLoading: Boolean,
    isListening: Boolean,
    hasImages: Boolean,
    hasFiles: Boolean,
    onSendMessage: () -> Unit,
    onAttachClick: () -> Unit,
    onCameraClick: () -> Unit = {},
    onAttachFileClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onSelectModelClick: () -> Unit,
    onPluginsClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "بیشتر",
                        tint = PersianTurquoise
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("انتخاب مدل ($activeModelLabel)") },
                        onClick = {
                            menuExpanded = false
                            onSelectModelClick()
                        },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isListening) "در حال شنیدن..." else "ورودی صوتی") },
                        onClick = {
                            menuExpanded = false
                            onVoiceClick()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = null,
                                tint = if (isListening) PersianTurquoise else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("ارسال عکس") },
                        onClick = {
                            menuExpanded = false
                            onAttachClick()
                        },
                        leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("عکس با دوربین (خواندن متن و توضیح)") },
                        onClick = {
                            menuExpanded = false
                            onCameraClick()
                        },
                        leadingIcon = { Icon(Icons.Default.PhotoCamera, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("ارسال فایل") },
                        onClick = {
                            menuExpanded = false
                            onAttachFileClick()
                        },
                        leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null) }
                    )

                    HorizontalDivider()

                    DropdownMenuItem(
                        text = { Text("اتصال‌دهنده GitHub") },
                        onClick = {
                            menuExpanded = false
                            onPluginsClick()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Hub,
                                contentDescription = null,
                                tint = PersianTurquoise
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("پلاگین‌ها و اتصالات") },
                        onClick = {
                            menuExpanded = false
                            onPluginsClick()
                        },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                    )
                }
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = onTextChanged,
                modifier = Modifier.weight(1f),
                placeholder = { Text("پیام خود را بنویسید...") },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PersianTurquoise,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = onSendMessage,
                enabled = !isLoading && (inputText.isNotBlank() || hasImages || hasFiles)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "ارسال",
                    tint = if (!isLoading && (inputText.isNotBlank() || hasImages || hasFiles))
                        PersianTurquoise
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}

private enum class VoicePhase { STARTING, LISTENING, THINKING, SPEAKING, PAUSED, ERROR }

/**
 * Hands-free two-way voice conversation:
 * listen (fa-IR) -> send to FOKO -> speak the reply -> listen again, until the user ends it.
 */
@Composable
fun TwoWayVoiceModal(
    onDismiss: () -> Unit,
    onSendMessage: (String, (String) -> Unit) -> Unit,
    ttsEngine: TextToSpeech?
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }

    var phase by remember { mutableStateOf(VoicePhase.STARTING) }
    var statusText by remember { mutableStateOf("در حال آماده‌سازی...") }
    var heardText by remember { mutableStateOf("") }
    var replyText by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(true) }
    var errorStreak by remember { mutableStateOf(0) }

    val recognizer = remember {
        if (android.speech.SpeechRecognizer.isRecognitionAvailable(context))
            android.speech.SpeechRecognizer.createSpeechRecognizer(context)
        else null
    }

    // Holder so callbacks can call the latest functions
    val startListeningRef = remember { arrayOfNulls<() -> Unit>(1) }

    fun hasMic(): Boolean = androidx.core.content.ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.RECORD_AUDIO
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun scheduleListen(delayMs: Long) {
        mainHandler.postDelayed({ if (active) startListeningRef[0]?.invoke() }, delayMs)
    }

    fun speakReply(text: String) {
        val engine = ttsEngine
        val cleaned = com.example.util.SpeechTextCleaner.forSpeech(text)
        if (engine == null || cleaned.isBlank()) {
            scheduleListen(300)
            return
        }
        try { engine.language = com.example.util.SpeechTextCleaner.preferredLocale(cleaned) } catch (_: Exception) {}
        phase = VoicePhase.SPEAKING
        statusText = "فوکو در حال صحبت است..."
        val r = engine.speak(cleaned, TextToSpeech.QUEUE_FLUSH, null, "foko_two_way")
        if (r != TextToSpeech.SUCCESS) scheduleListen(300)
    }

    fun startListening() {
        if (!active) return
        val sr = recognizer
        if (sr == null) {
            phase = VoicePhase.ERROR
            statusText = "تشخیص گفتار روی این گوشی در دسترس نیست (Google Speech Services را نصب/فعال کنید)"
            return
        }
        if (!hasMic()) {
            phase = VoicePhase.ERROR
            statusText = "دسترسی میکروفون داده نشده است"
            return
        }
        try {
            ttsEngine?.stop()
            sr.cancel()
            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
                putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(android.speech.RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }
            phase = VoicePhase.LISTENING
            statusText = "در حال شنیدن... صحبت کنید"
            sr.startListening(intent)
        } catch (e: Exception) {
            phase = VoicePhase.ERROR
            statusText = "شروع شنیدن ناموفق بود: ${e.localizedMessage}"
        }
    }
    startListeningRef[0] = { startListening() }

    val micPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening() else {
            phase = VoicePhase.ERROR
            statusText = "بدون دسترسی میکروفون مکالمه صوتی ممکن نیست"
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        recognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {
                phase = VoicePhase.LISTENING; statusText = "در حال شنیدن... صحبت کنید"
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { statusText = "در حال تشخیص..." }
            override fun onError(error: Int) {
                if (!active) return
                when (error) {
                    android.speech.SpeechRecognizer.ERROR_NO_MATCH,
                    android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> scheduleListen(250)
                    android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        // Usually the floating widget is holding the recognizer; retry shortly
                        try { recognizer?.cancel() } catch (_: Exception) {}
                        scheduleListen(900)
                    }
                    android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        phase = VoicePhase.ERROR; statusText = "دسترسی میکروفون داده نشده است"
                    }
                    else -> {
                        errorStreak++
                        if (errorStreak <= 3) scheduleListen(700) else {
                            phase = VoicePhase.ERROR
                            statusText = "خطای تشخیص گفتار (کد $error). اینترنت/VPN را بررسی کنید و دوباره امتحان کنید"
                        }
                    }
                }
            }
            override fun onResults(results: android.os.Bundle?) {
                val text = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim().orEmpty()
                if (!active) return
                if (text.isBlank()) { scheduleListen(250); return }
                errorStreak = 0
                heardText = text
                replyText = ""
                phase = VoicePhase.THINKING
                statusText = "فوکو در حال فکر کردن..."
                onSendMessage(text) { reply ->
                    mainHandler.post {
                        replyText = reply
                        if (active) speakReply(reply)
                    }
                }
            }
            override fun onPartialResults(partialResults: android.os.Bundle?) {
                val p = partialResults?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!p.isNullOrBlank()) heardText = p
            }
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })

        ttsEngine?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId == "foko_two_way") mainHandler.post { if (active) startListeningRef[0]?.invoke() }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == "foko_two_way") mainHandler.post { if (active) startListeningRef[0]?.invoke() }
            }
        })

        if (hasMic()) startListening()
        else micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)

        onDispose {
            active = false
            mainHandler.removeCallbacksAndMessages(null)
            try { recognizer?.cancel() } catch (_: Exception) {}
            try { recognizer?.destroy() } catch (_: Exception) {}
            try { ttsEngine?.stop() } catch (_: Exception) {}
        }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = { },
        title = { Text("مکالمه صوتی دوطرفه", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (phase) {
                            VoicePhase.SPEAKING -> Icons.Default.VolumeUp
                            else -> Icons.Default.Mic
                        },
                        contentDescription = null,
                        tint = when (phase) {
                            VoicePhase.ERROR -> MaterialTheme.colorScheme.error
                            VoicePhase.PAUSED -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> PersianTurquoise
                        },
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(statusText, style = MaterialTheme.typography.bodyMedium)
                }
                if (heardText.isNotBlank()) {
                    Text("شما: $heardText", style = MaterialTheme.typography.bodySmall)
                }
                if (replyText.isNotBlank()) {
                    Text(
                        "فوکو: " + (if (replyText.length > 300) replyText.take(300) + "..." else replyText),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                active = false
                onDismiss()
            }) { Text("پایان مکالمه") }
        },
        dismissButton = {
            TextButton(onClick = {
                if (phase == VoicePhase.PAUSED || phase == VoicePhase.ERROR) {
                    active = true
                    errorStreak = 0
                    if (hasMic()) startListening()
                    else micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                } else {
                    active = false
                    mainHandler.removeCallbacksAndMessages(null)
                    try { recognizer?.cancel() } catch (_: Exception) {}
                    try { ttsEngine?.stop() } catch (_: Exception) {}
                    phase = VoicePhase.PAUSED
                    statusText = "مکث شد"
                }
            }) {
                Text(if (phase == VoicePhase.PAUSED || phase == VoicePhase.ERROR) "ادامه / تلاش دوباره" else "مکث")
            }
        }
    )
}

@Composable
fun ChatDrawerContent(
    conversations: List<ChatConversationEntity>,
    currentId: String?,
    onSelectConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onNewChat: () -> Unit,
    onOpenTwoWayVoice: () -> Unit,
    onNavigateToScreen: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("گفتگوها", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onNewChat, modifier = Modifier.fillMaxWidth()) {
            Text("گفتگوی جدید")
        }
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn {
            items(conversations) { conv ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectConversation(conv.id) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = conv.title.ifBlank { "بدون عنوان" },
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    IconButton(onClick = { onDeleteConversation(conv.id) }) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    onSpeak: () -> Unit,
    onSaveToLibrary: () -> Unit,
    onCopy: () -> Unit = {}
) {
    val isUser = message.sender == MessageSender.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) PersianTurquoise.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // لمس طولانی روی متن → انتخاب/مارک و منوی کپی سیستم اندروید
                SelectionContainer {
                    Text(text = message.text)
                }
                if (!isUser) {
                    Row {
                        IconButton(onClick = onSpeak, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.VolumeUp, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyChatGreeting(onSuggestionClick: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("FOKO AI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = PersianTurquoise)
        Spacer(modifier = Modifier.height(8.dp))
        Text("چطور می‌تونم کمکت کنم؟", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(24.dp))
        listOf("یک شعر بنویس", "کد پایتون بنویس", "ترجمه کن").forEach { suggestion ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onSuggestionClick(suggestion) },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(text = suggestion, modifier = Modifier.padding(12.dp))
            }
        }
    }
}
