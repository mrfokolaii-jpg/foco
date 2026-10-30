package com.example.ui.screens.chat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.connectors.ConnectorSnapshot
import com.example.data.connectors.ConnectorStore
import com.example.data.local.ChatConversationEntity
import com.example.data.model.AiModelCatalog
import com.example.data.model.CatalogModel
import com.example.data.model.ChatMessage
import com.example.data.model.MessageSender
import com.example.ui.theme.PersianTurquoise
import com.example.ui.theme.PersianViolet
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
    var connectorSnapshot by remember { mutableStateOf(connectorStore.snapshot()) }

    LaunchedEffect(openTwoWayVoice) {
        if (openTwoWayVoice) {
            showTwoWayVoiceModal = true
            onTwoWayVoiceConsumed()
        }
    }

    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Multi-image picker launcher (1 to 10+ images)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.attachImages(context, uris)
            Toast.makeText(context, "${uris.size} تصویر پیوست شد", Toast.LENGTH_SHORT).show()
        }
    }

    // Any-file picker launcher (zip, documents, apk, ...)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.attachFiles(context, uris)
            Toast.makeText(context, "${uris.size} فایل پیوست شد", Toast.LENGTH_SHORT).show()
        }
    }

    // TTS Engine for voice playback with proper initialization and fallback
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

    // Direct in-app Speech Recognizer
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    DisposableEffect(Unit) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        }
        onDispose {
            speechRecognizer?.destroy()
        }
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
                if (!matches.isNullOrEmpty()) {
                    inputText = matches[0]
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        sr.startListening(intent)
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startBarVoiceInput()
        } else {
            Toast.makeText(context, "مجوز دسترسی به میکروفون داده نشد", Toast.LENGTH_SHORT).show()
        }
    }

    fun onStartVoiceInput() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startBarVoiceInput()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Auto-scroll on new message
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
            // Top Bar
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
                                text = if (uiState.isAutoMode) "هوش مصنوعی آماده (حالت خودکار)" else uiState.selectedCatalogModel.title,
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

            // Chat Messages List
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
                                    ttsEngine?.speak(message.text, TextToSpeech.QUEUE_FLUSH, null, message.id)
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

            // Attached images preview
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

            // Attached files preview
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

            // Input bar
            ModernChatInputBar(
                inputText = inputText,
                onTextChanged = { inputText = it },
                activeModelLabel = if (uiState.isAutoMode) "Auto" else uiState.selectedCatalogModel.badge,
                isLoading = uiState.isLoading,
                isListening = isListeningInBar,
                hasImages = uiState.attachedImages.isNotEmpty(),
                hasFiles = uiState.attachedFiles.isNotEmpty(),
                onSendMessage = {
                    if (inputText.isNotBlank() || uiState.attachedImages.isNotEmpty() || uiState.attachedFiles.isNotEmpty()) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    }
                },
                onAttachClick = { photoPickerLauncher.launch("image/*") },
                onAttachFileClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                onVoiceClick = { onStartVoiceInput() },
                onSelectModelClick = { showModelSelectorSheet = true },
                onPluginsClick = {
                    connectorSnapshot = connectorStore.snapshot()
                    showPluginsSheet = true
                }
            )
        }
    }

    // Model selector bottom sheet
    if (showModelSelectorSheet) {
        val activeModels = viewModel.getActiveModels()
        ModalBottomSheet(
            onDismissRequest = { showModelSelectorSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "انتخاب هوش مصنوعی (Select Model):",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // 1. Auto Option (Always Recommended)
                ModelOptionCard(
                    title = "Auto (حالت خودکار و بهینه)",
                    description = "انتخاب هوشمند سریع‌ترین و بهترین مدل از میان سرویس‌های فعال شما",
                    tag = "پیشنهادی فوکو",
                    isSelected = uiState.isAutoMode,
                    onClick = {
                        viewModel.enableAutoMode()
                        showModelSelectorSheet = false
                    }
                )

                if (activeModels.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "مدل‌های فعال شما بر اساس کلیدها (${activeModels.size} مدل):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = PersianTurquoise
                    )

                    activeModels.forEach { model ->
                        val isSelected = !uiState.isAutoMode && uiState.selectedCatalogModel.id == model.id
                        ModelOptionCard(
                            title = model.title,
                            description = model.subtitle,
                            tag = model.badge,
                            isSelected = isSelected,
                            onClick = {
                                viewModel.selectCatalogModel(model)
                                showModelSelectorSheet = false
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Plugins / Connectors Bottom Sheet
    if (showPluginsSheet) {
        val connectedCount = listOf(
            connectorSnapshot.githubConfigured,
            connectorSnapshot.huggingFaceConfigured,
            connectorSnapshot.cloudflareConfigured,
            connectorSnapshot.driveConfigured
        ).count { it }

        ModalBottomSheet(
            onDismissRequest = { showPluginsSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "پلاگین‌ها و اتصالات (Plugins):",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = if (connectedCount > 0) PersianTurquoise.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "$connectedCount از ۴ متصل",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (connectedCount > 0) PersianTurquoise else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                PluginOptionRow(
                    emoji = "🐙",
                    title = "GitHub",
                    description = "خواندن/نوشتن فایل، جستجوی کد و کامیت دانش در مخزن",
                    isConnected = connectorSnapshot.githubConfigured
                )
                PluginOptionRow(
                    emoji = "🤗",
                    title = "Hugging Face",
                    description = "جستجو و استفاده از مدل‌ها و سرویس‌های هوش مصنوعی",
                    isConnected = connectorSnapshot.huggingFaceConfigured
                )
                PluginOptionRow(
                    emoji = "☁️",
                    title = "Cloudflare",
                    description = "مدیریت DNS، Workers و سرویس‌های لبه شبکه",
                    isConnected = connectorSnapshot.cloudflareConfigured
                )
                PluginOptionRow(
                    emoji = "📁",
                    title = "Google Drive",
                    description = "آپلود، دانلود و لیست فایل‌های ذخیره‌شده",
                    isConnected = connectorSnapshot.driveConfigured
                )

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        showPluginsSheet = false
                        onNavigateToScreen("connectors")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("مدیریت و اتصال پلاگین‌ها (تب ابزارها)", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Two-Way Interactive Voice Modal (JARVIS 1 Voice Engine)
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
