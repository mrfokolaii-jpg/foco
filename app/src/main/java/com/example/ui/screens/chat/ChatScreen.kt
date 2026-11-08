package com.example.ui.screens.chat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.connectors.ConnectorStore
import com.example.ui.theme.PersianTurquoise
import com.example.util.SpeechTextCleaner
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    openTwoWayVoice: Boolean = false,
    onTwoWayVoiceConsumed: () -> Unit = {},
    onNavigateToScreen: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val conversations by viewModel.conversations.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var showModelSelectorSheet by remember { mutableStateOf(false) }
    var showPluginsSheet by remember { mutableStateOf(false) }
    var showTwoWayVoiceModal by remember { mutableStateOf(false) }
    var isListeningInBar by remember { mutableStateOf(false) }

    val connectorStore = remember { ConnectorStore(context.applicationContext) }

    LaunchedEffect(openTwoWayVoice) {
        if (openTwoWayVoice) {
            showTwoWayVoiceModal = true
            onTwoWayVoiceConsumed()
        }
    }

    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.attachImages(context, uris)
            Toast.makeText(context, "${uris.size} تصویر پیوست شد", Toast.LENGTH_SHORT).show()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.attachFiles(context, uris)
            Toast.makeText(context, "${uris.size} فایل پیوست شد", Toast.LENGTH_SHORT).show()
        }
    }

    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var ttsInstance: TextToSpeech? = null
        ttsInstance = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                try {
                    val result = ttsInstance?.setLanguage(Locale("fa", "IR"))
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        ttsInstance?.setLanguage(Locale("fa"))
                    }
                    ttsInstance?.setPitch(1.0f)
                    ttsInstance?.setSpeechRate(0.95f)
                } catch (_: Exception) {}
                ttsEngine = ttsInstance
            }
        }
        onDispose {
            ttsInstance?.stop()
            ttsInstance?.shutdown()
        }
    }

    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    DisposableEffect(Unit) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        }
        onDispose { speechRecognizer?.destroy() }
    }

    fun startBarVoiceInput() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "لطفاً دسترسی میکروفون را فعال کنید", Toast.LENGTH_SHORT).show()
            return
        }
        val sr = speechRecognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
        }
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListeningInBar = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListeningInBar = false }
            override fun onError(error: Int) { isListeningInBar = false }
            override fun onResults(results: Bundle?) {
                isListeningInBar = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val spoken = matches[0]
                    inputText = if (inputText.isBlank()) spoken else "$inputText $spoken"
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) inputText = matches[0]
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        sr.startListening(intent)
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) startBarVoiceInput()
        else Toast.makeText(context, "مجوز دسترسی به میکروفون داده نشد", Toast.LENGTH_SHORT).show()
    }

    fun onStartVoiceInput() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startBarVoiceInput()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerTonalElevation = 6.dp
            ) {
                ChatDrawerContent(
                    conversations = conversations,
                    currentId = uiState.currentConversationId,
                    onSelectConversation = { id ->
                        viewModel.loadConversation(id)
                        scope.launch { drawerState.close() }
                    },
                    onDeleteConversation = { id -> viewModel.deleteConversation(id) },
                    onNewChat = {
                        viewModel.startNewConversation()
                        scope.launch { drawerState.close() }
                    },
                    onOpenTwoWayVoice = {
                        scope.launch { drawerState.close() }
                        showTwoWayVoiceModal = true
                    },
                    onNavigateToScreen = { route ->
                        scope.launch { drawerState.close() }
                        onNavigateToScreen(route)
                    }
                )
            }
        }
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "FOKO AI (فوکو)",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(PersianTurquoise)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = uiState.selectedCatalogModel.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = PersianTurquoise
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = "منو")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showTwoWayVoiceModal = true },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(PersianTurquoise.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = "مکالمه صوتی دوطرفه",
                            tint = PersianTurquoise
                        )
                    }
                    IconButton(onClick = { viewModel.startNewConversation() }) {
                        Icon(Icons.Default.Add, contentDescription = "گفتگوی جدید")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (uiState.messages.isEmpty()) {
                    EmptyChatGreeting(
                        onSuggestionClick = { prompt ->
                            inputText = prompt
                            viewModel.sendMessage(prompt)
                            inputText = ""
                        }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.messages, key = { it.id }) { message ->
                            ChatMessageBubble(
                                message = message,
                                onCopy = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Foko Text", message.text))
                                    Toast.makeText(context, "متن کپی شد", Toast.LENGTH_SHORT).show()
                                },
                                onSpeak = {
                                    val cleaned = SpeechTextCleaner.forSpeech(message.text)
                                    if (cleaned.isNotBlank()) {
                                        try {
                                            ttsEngine?.language = SpeechTextCleaner.preferredLocale(cleaned)
                                        } catch (_: Exception) {}
                                        ttsEngine?.speak(cleaned, TextToSpeech.QUEUE_FLUSH, null, message.id)
                                    }
                                },
                                onSaveToLibrary = { viewModel.saveMessageToLibrary(message) }
                            )
                        }
                        if (uiState.isLoading) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(uiState.statusMessage ?: "در حال پردازش...")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.attachedImages.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.attachedImages, key = { it.id }) { img ->
                        Box {
                            AsyncImage(
                                model = img.uri,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = { viewModel.removeAttachedImage(img.id) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(20.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            if (uiState.attachedFiles.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.attachedFiles, key = { it.id }) { f ->
                        Card(shape = RoundedCornerShape(8.dp)) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.InsertDriveFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(f.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                IconButton(onClick = { viewModel.removeAttachedFile(f.id) }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                }
            }

            ModernChatInputBar(
                inputText = inputText,
                onTextChanged = { inputText = it },
                activeModelLabel = uiState.selectedCatalogModel.title,
                isLoading = uiState.isLoading,
                isListening = isListeningInBar,
                hasImages = uiState.attachedImages.isNotEmpty(),
                hasFiles = uiState.attachedFiles.isNotEmpty(),
                onSendMessage = {
                    val text = inputText.trim()
                    if (text.isNotBlank() || uiState.attachedImages.isNotEmpty() || uiState.attachedFiles.isNotEmpty()) {
                        viewModel.sendMessage(text)
                        inputText = ""
                    }
                },
                onAttachClick = { photoPickerLauncher.launch("image/*") },
                onAttachFileClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                onVoiceClick = { onStartVoiceInput() },
                onSelectModelClick = { showModelSelectorSheet = true },
                onPluginsClick = { showPluginsSheet = true }
            )
        }
    }

    if (showTwoWayVoiceModal) {
        TwoWayVoiceModal(
            onDismiss = { showTwoWayVoiceModal = false },
            onSendMessage = { prompt, onReply ->
                viewModel.sendMessage(prompt) { reply ->
                    onReply(reply)
                }
            },
            ttsEngine = ttsEngine
        )
    }
}
