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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
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
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.example.data.connectors.ConnectorResult
import com.example.data.connectors.ConnectorSnapshot
import com.example.data.connectors.ConnectorStore
import com.example.data.connectors.GitHubConnector
import com.example.data.connectors.HuggingFaceConnector
import com.example.data.local.ChatConversationEntity
import com.example.data.model.AiModelCatalog
import com.example.data.model.AiProviderKind
import com.example.data.model.CatalogModel
import com.example.data.model.ChatMessage
import com.example.data.model.MessageSender
import com.example.ui.screens.connectors.ConnectorAuthDialog
import com.example.util.FokoVoiceCtl
import com.example.util.SpeechText
import com.example.ui.screens.connectors.ConnectorAuthTarget
import com.example.ui.theme.PersianTurquoise
import com.example.ui.theme.PersianViolet
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    openTwoWayVoice: Boolean = false,
    onTwoWayVoiceConsumed: () -> Unit = {},
    onNavigateToScreen: (String) -> Unit = {},
    onOpenMenu: () -> Unit = {},
    onOpenQuickPanel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val conversations by viewModel.conversations.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var showModelSelectorSheet by remember { mutableStateOf(false) }
    var showPluginsSheet by remember { mutableStateOf(false) }
    var connectorAuthTarget by remember { mutableStateOf<ConnectorAuthTarget?>(null) }
    var showTwoWayVoiceModal by remember { mutableStateOf(false) }
    var isListeningInBar by remember { mutableStateOf(false) }
    // پیامی که کاربر می‌خواهد به آن پاسخ (ریپلای) بدهد.
    var replyTarget by remember { mutableStateOf<ChatMessage?>(null) }

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
                val engine = ttsInstance
                if (engine != null) {
                    FokoVoiceCtl.applySettings(context.applicationContext, engine)
                    ttsEngine = engine
                }
            }
        }
        // با هر تغییر صدا در کارت تنظیمات، صدای همین صفحه هم بلافاصله عوض می‌شود.
        val onVoiceChanged: () -> Unit = {
            FokoVoiceCtl.applySettings(context.applicationContext, ttsInstance)
        }
        FokoVoiceCtl.register(onVoiceChanged)
        onDispose {
            FokoVoiceCtl.unregister(onVoiceChanged)
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

    /** Verifies the token the user just produced and, if it works, connects the plugin. */
    fun connectConnector(target: ConnectorAuthTarget, rawToken: String) {
        val token = rawToken.trim()
        if (token.isBlank()) {
            connectorAuthTarget = null
            return
        }
        scope.launch {
            var verified = false
            var who = ""
            when (target.id) {
                ConnectorAuthTarget.GITHUB.id -> {
                    when (val result = GitHubConnector.whoAmI(token)) {
                        is ConnectorResult.Ok -> { verified = true; who = result.value }
                        is ConnectorResult.Err -> who = result.message
                    }
                    if (verified) {
                        connectorStore.setGitHub(
                            token = token,
                            owner = connectorStore.githubOwner(),
                            repo = connectorStore.githubRepo(),
                            branch = connectorStore.githubBranch()
                        )
                    }
                }
                ConnectorAuthTarget.HUGGING_FACE.id -> {
                    when (val result = HuggingFaceConnector.whoAmI(token)) {
                        is ConnectorResult.Ok -> { verified = true; who = result.value }
                        is ConnectorResult.Err -> who = result.message
                    }
                    if (verified) connectorStore.setHfToken(token)
                }
            }
            connectorSnapshot = connectorStore.snapshot()
            connectorAuthTarget = null
            Toast.makeText(
                context,
                if (verified) "${target.title} متصل شد ✓  ($who)" else "اتصال ناموفق — $who",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /** Unticking the checkbox removes the stored credentials for that plugin. */
    fun disconnectConnector(target: ConnectorAuthTarget) {
        when (target.id) {
            ConnectorAuthTarget.GITHUB.id -> connectorStore.setGitHub(
                token = "",
                owner = connectorStore.githubOwner(),
                repo = connectorStore.githubRepo(),
                branch = connectorStore.githubBranch()
            )
            ConnectorAuthTarget.HUGGING_FACE.id -> connectorStore.setHfToken("")
        }
        connectorSnapshot = connectorStore.snapshot()
        Toast.makeText(context, "${target.title} قطع شد", Toast.LENGTH_SHORT).show()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // سوایپ از لبه‌ی چپ حالا کشوی اصلی اپ (بخش‌ها) را باز می‌کند، پس این کشو
        // (فهرست گفتگوها) فقط با دکمه‌ی تاریخچه در نوار بالا باز می‌شود.
        gesturesEnabled = false,
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
                    // کشوی بخش‌های اپ؛ با سوایپ از لبه‌ی چپ هم باز می‌شود.
                    IconButton(onClick = onOpenMenu) {
                        Icon(Icons.Default.Menu, contentDescription = "بخش‌های اپ")
                    }
                },
                actions = {
                    // Two-way live voice mode button
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
                    // فهرست گفتگوهای قبلی (کشوی داخلی)
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.History, contentDescription = "گفتگوهای قبلی")
                    }
                    IconButton(onClick = { viewModel.startNewConversation() }) {
                        Icon(Icons.Default.Add, contentDescription = "گفتگوی جدید")
                    }
                    // پنل «کنترل و تنظیمات» — از لبهٔ چپ باز می‌شود.
                    IconButton(onClick = onOpenQuickPanel) {
                        Icon(Icons.Default.Tune, contentDescription = "پنل کنترل و تنظیمات")
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
                                    ttsEngine?.speak(
                                        SpeechText.forSpeech(message.text),
                                        TextToSpeech.QUEUE_FLUSH,
                                        null,
                                        message.id
                                    )
                                },
                                onReply = { replyTarget = message }
                            )
                        }
                    }
                }

                // Loading Indicator Badge
                if (uiState.isLoading) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(20.dp),
                        tonalElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = PersianTurquoise
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = uiState.statusMessage ?: "در حال پردازش فوکو...",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            // Attached Images Thumbnail Preview Row
            if (uiState.attachedImages.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "تصاویر پیوست‌شده (${uiState.attachedImages.size} تصویر):",
                                style = MaterialTheme.typography.labelSmall,
                                color = PersianTurquoise,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = { viewModel.clearAttachedImages() }) {
                                Text("حذف همه", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                            }
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(uiState.attachedImages, key = { it.id }) { item ->
                                Box(modifier = Modifier.size(64.dp)) {
                                    AsyncImage(
                                        model = item.uri,
                                        contentDescription = "تصویر پیوست",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    IconButton(
                                        onClick = { viewModel.removeAttachedImage(item.id) },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(20.dp)
                                            .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "حذف",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Attached Files Preview Row (zip, documents, any file)
            if (uiState.attachedFiles.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "فایل‌های پیوست‌شده (${uiState.attachedFiles.size} فایل):",
                                style = MaterialTheme.typography.labelSmall,
                                color = PersianTurquoise,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = { viewModel.clearAttachedFiles() }) {
                                Text("حذف همه", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                            }
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(uiState.attachedFiles, key = { it.id }) { item ->
                                val sizeLabel = when {
                                    item.sizeBytes >= 1_048_576 -> "%.1fMB".format(item.sizeBytes / 1_048_576f)
                                    item.sizeBytes >= 1024 -> "${item.sizeBytes / 1024}KB"
                                    else -> "${item.sizeBytes}B"
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.InsertDriveFile,
                                            contentDescription = null,
                                            tint = PersianTurquoise,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(
                                                text = item.name,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = sizeLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        IconButton(
                                            onClick = { viewModel.removeAttachedFile(item.id) },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "حذف",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // نوار «در پاسخ به ...» بالای کادر نوشتن
            val replying = replyTarget
            if (replying != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(34.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(PersianTurquoise)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (replying.sender == MessageSender.USER) {
                                    "پاسخ به پیام شما"
                                } else {
                                    "پاسخ به فوکو"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = PersianTurquoise
                            )
                            Text(
                                text = replying.text.replace("\n", " ").trim().take(120),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { replyTarget = null },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "لغو پاسخ",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Input Bar Container (flush with the bottom navigation bar)
            ModernChatInputBar(
                inputText = inputText,
                onTextChanged = { inputText = it },
                activeModelLabel = if (uiState.isAutoMode) {
                    if (uiState.autoModelLabel.isBlank()) "Auto" else "Auto · ${uiState.autoModelLabel}"
                } else uiState.selectedCatalogModel.badge,
                isLoading = uiState.isLoading,
                isListening = isListeningInBar,
                hasImages = uiState.attachedImages.isNotEmpty(),
                hasFiles = uiState.attachedFiles.isNotEmpty(),
                onSendMessage = {
                    val prompt = inputText
                    val quoted = replyTarget
                    inputText = ""
                    replyTarget = null
                    // اگر ریپلای فعال باشد، پیام نقل‌شده به‌صورت نقل‌قول بالای متن می‌رود.
                    val outgoing = if (quoted != null) {
                        val q = quoted.text.replace("\n", " ").trim().take(400)
                        "> $q\n\n$prompt"
                    } else {
                        prompt
                    }
                    viewModel.sendMessage(outgoing)
                },
                onAttachClick = { photoPickerLauncher.launch("image/*") },
                onAttachFileClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                onVoiceClick = { onStartVoiceInput() },
                onSelectModelClick = { showModelSelectorSheet = true },
                onPluginsClick = {
                    connectorSnapshot = connectorStore.snapshot()
                    showPluginsSheet = true
                },
                onCopyLastReply = {
                    val last = uiState.messages.lastOrNull { it.text.isNotBlank() }
                    if (last != null) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Foko Text", last.text))
                        Toast.makeText(context, "متن کپی شد", Toast.LENGTH_SHORT).show()
                    }
                },
                onClearConversation = {
                    viewModel.startNewConversation()
                    Toast.makeText(context, "گفتگوی جدید باز شد", Toast.LENGTH_SHORT).show()
                },
                hasCopyTarget = uiState.messages.any { it.text.isNotBlank() },
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            )
        }
    }

    // Dynamic Select Model Bottom Sheet
    if (showModelSelectorSheet) {
        val snapshot = viewModel.getKeySnapshot()
        val activeModels = AiModelCatalog.activeModels(snapshot)

        // Gemini models come from the user's own key, never from a hard-coded list.
        var geminiModels by remember { mutableStateOf<List<com.example.data.model.CatalogModel>>(emptyList()) }
        LaunchedEffect(snapshot.geminiConfigured) {
            geminiModels = if (snapshot.geminiConfigured) {
                val geminiKey = com.example.data.local.ApiKeyStore(context.applicationContext).getGeminiKey()
                com.example.data.remote.GeminiModels.list(geminiKey)
                    .take(12)
                    .map { AiModelCatalog.gemini(it) }
            } else {
                emptyList()
            }
        }

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

                // 1. Auto — routed through 9Router whenever it is connected.
                val nineRouterOn = snapshot.nineRouterConfigured
                ModelOptionCard(
                    title = "Auto (حالت خودکار)",
                    description = if (nineRouterOn) {
                        "مسیریابی از طریق 9Router: هر مدل به سقف خورد، مدل بعدی خودکار فعال می‌شود"
                    } else {
                        "به‌ترتیب از سرویس‌های فعال تو استفاده می‌کند و روی محدودیت، مدل را خودکار عوض می‌کند"
                    },
                    tag = if (nineRouterOn) "9Router" else "خودکار",
                    isSelected = uiState.isAutoMode,
                    onClick = {
                        viewModel.enableAutoMode()
                        showModelSelectorSheet = false
                    }
                )
                // Live: which bot is answering right now, and when Auto hops to the next one.
                if (uiState.isAutoMode) {
                    Text(
                        text = "الان با این مدل کار می‌کنی: " + uiState.autoModelLabel.ifBlank { "—" },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = PersianTurquoise,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }

                val nonGeminiActive = activeModels.filter {
                    it.id != AiModelCatalog.builtinFree.id && it.provider != AiProviderKind.GEMINI
                }
                if (nonGeminiActive.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "مدل‌های فعال تو (${nonGeminiActive.size} مدل):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = PersianTurquoise
                    )

                    nonGeminiActive.forEach { model ->
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

                // Gemini — shown ONLY when a key exists, and the list is fetched from that key.
                // With no key there is nothing here at all: no pre-stored model names.
                if (snapshot.geminiConfigured) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "مدل‌های موجود با کلید Gemini تو:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = PersianTurquoise
                    )
                    if (geminiModels.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = PersianTurquoise
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "در حال واکشی لیست از گوگل...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    geminiModels.forEach { model ->
                        val isSelected = !uiState.isAutoMode && uiState.selectedCatalogModel.id == model.id
                        ModelOptionCard(
                            title = model.title,
                            description = model.subtitle,
                            tag = "Gemini",
                            isSelected = isSelected,
                            onClick = {
                                viewModel.selectCatalogModel(model)
                                showModelSelectorSheet = false
                            }
                        )
                    }
                }

                // Every hand-wired service the user added in Settings.
                val manualModels = AiModelCatalog.customModels(
                    com.example.data.local.ApiKeyStore(context.applicationContext).getCustomProviders()
                )
                if (manualModels.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "سرویس‌های دستی تو (${manualModels.size}):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = PersianTurquoise
                    )
                    manualModels.forEach { model ->
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
            connectorSnapshot.cloudflareConfigured
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
                            text = "$connectedCount از ۳ متصل",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (connectedCount > 0) PersianTurquoise else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = "تیک هر سرویس را بزن؛ صفحه ورود همان سایت باز می‌شود، وارد حساب خودت شو و فوکو خودکار وصل می‌شود.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(2.dp))

                PluginOptionRow(
                    emoji = "🐙",
                    title = "GitHub",
                    description = "خواندن/نوشتن فایل، جستجوی کد و کامیت دانش در مخزن",
                    isConnected = connectorSnapshot.githubConfigured,
                    onToggle = { wanted ->
                        if (wanted) {
                            showPluginsSheet = false
                            connectorAuthTarget = ConnectorAuthTarget.GITHUB
                        } else {
                            disconnectConnector(ConnectorAuthTarget.GITHUB)
                        }
                    }
                )
                PluginOptionRow(
                    emoji = "🤗",
                    title = "Hugging Face",
                    description = "جستجو و استفاده از مدل‌ها و سرویس‌های هوش مصنوعی",
                    isConnected = connectorSnapshot.huggingFaceConfigured,
                    onToggle = { wanted ->
                        if (wanted) {
                            showPluginsSheet = false
                            connectorAuthTarget = ConnectorAuthTarget.HUGGING_FACE
                        } else {
                            disconnectConnector(ConnectorAuthTarget.HUGGING_FACE)
                        }
                    }
                )
                PluginOptionRow(
                    emoji = "☁️",
                    title = "Cloudflare",
                    description = "مدیریت DNS، Workers و سرویس‌های لبه شبکه",
                    isConnected = connectorSnapshot.cloudflareConfigured
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

    // In-app connector login (GitHub / Hugging Face) — opened from the plugins (+ ) sheet
    connectorAuthTarget?.let { target ->
        ConnectorAuthDialog(
            target = target,
            onDismiss = { connectorAuthTarget = null },
            onTokenDetected = { token -> connectConnector(target, token) }
        )
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

@Composable
fun PluginOptionRow(
    emoji: String,
    title: String,
    description: String,
    isConnected: Boolean,
    onToggle: ((Boolean) -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) PersianTurquoise.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isConnected) androidx.compose.foundation.BorderStroke(1.5.dp, PersianTurquoise.copy(alpha = 0.7f)) else null
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = emoji, fontSize = 22.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onToggle != null) {
                // Ticking this opens the provider's login page inside FOKO and connects it.
                Switch(
                    checked = isConnected,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(checkedTrackColor = PersianTurquoise)
                )
            } else {
                Surface(
                    color = if (isConnected) PersianTurquoise.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isConnected) "متصل ✓" else "قطع",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isConnected) PersianTurquoise else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
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
    onAttachFileClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onSelectModelClick: () -> Unit,
    onPluginsClick: () -> Unit,
    onCopyLastReply: () -> Unit = {},
    onClearConversation: () -> Unit = {},
    hasCopyTarget: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Docked composer: sits flush on top of the bottom navigation bar (no gap).
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Hairline seam that separates the thread from the composer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Attach file button (zip, documents, any file)
            IconButton(
                onClick = onAttachFileClick,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "پیوست فایل",
                    tint = if (hasFiles) PersianTurquoise else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Attach image button
            IconButton(
                onClick = onAttachClick,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = "پیوست تصاویر",
                    tint = if (hasImages) PersianTurquoise else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            // In-app voice button
            IconButton(
                onClick = onVoiceClick,
                modifier = Modifier
                    .size(38.dp)
                    .background(if (isListening) Color(0xFFEF4444).copy(alpha = 0.2f) else Color.Transparent, CircleShape)
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                    contentDescription = "ورودی صوتی",
                    tint = if (isListening) Color(0xFFEF4444) else PersianTurquoise,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Text Input Field
            OutlinedTextField(
                value = inputText,
                onValueChange = onTextChanged,
                placeholder = {
                    Text(
                        text = if (isListening) "در حال شنیدن صدای شما..." else "پیام خود را بنویسید...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isListening) PersianTurquoise else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp, max = 168.dp)
                    .testTag("chat_input_field"),
                // Room for a real paragraph instead of four cramped lines.
                maxLines = 9,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = PersianTurquoise
                )
            )

            // Copy the last reply
            IconButton(
                onClick = onCopyLastReply,
                enabled = hasCopyTarget,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "کپی آخرین پاسخ",
                    tint = if (hasCopyTarget) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Clear the whole conversation
            IconButton(
                onClick = onClearConversation,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "پاک کردن گفتگو",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Auto / Model Pill Button
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .clickable { onSelectModelClick() }
                    .padding(horizontal = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = activeModelLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "انتخاب مدل",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Plugins (+) button next to the model pill
            IconButton(
                onClick = onPluginsClick,
                modifier = Modifier
                    .size(34.dp)
                    .background(PersianTurquoise.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "پلاگین‌ها و اتصالات",
                    tint = PersianTurquoise,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            // Send Button
            IconButton(
                onClick = onSendMessage,
                enabled = !isLoading && (inputText.isNotBlank() || hasImages || hasFiles),
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        if (!isLoading && (inputText.isNotBlank() || hasImages || hasFiles)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = "ارسال پیام",
                    tint = if (!isLoading && (inputText.isNotBlank() || hasImages || hasFiles)) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) PersianTurquoise.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, PersianTurquoise) else null
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) PersianTurquoise else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "انتخاب شده",
                    tint = PersianTurquoise,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun TwoWayVoiceModal(
    onDismiss: () -> Unit,
    onSendMessage: (String, (String) -> Unit) -> Unit,
    ttsEngine: TextToSpeech?
) {
    val context = LocalContext.current
    var voiceStatus by remember { mutableStateOf("در حال گوش دادن...") }
    var recognizedText by remember { mutableStateOf("") }
    var lastReplyText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    fun startListening() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            voiceStatus = "مجوز میکروفون را در تنظیمات برنامه فعال کنید"
            return
        }
        if (speechRecognizer == null) {
            voiceStatus = "سرویس تشخیص گفتار روی این دستگاه در دسترس نیست"
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
        }
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                voiceStatus = "در حال گوش دادن..."
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
                voiceStatus = "در حال پردازش فوکو..."
            }
            override fun onError(error: Int) {
                isListening = false
                voiceStatus = "آماده شنیدن (لمس گوی برای صحبت)"
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val spoken = matches[0]
                    recognizedText = spoken
                    voiceStatus = "در حال تفکر و تولید پاسخ..."
                    onSendMessage(spoken) { reply ->
                        lastReplyText = reply
                        voiceStatus = "در حال پاسخ صوتی فوکو..."
                        val engine = ttsEngine
                        if (engine == null) {
                            voiceStatus = "موتور تبدیل متن به گفتار آماده نیست"
                        } else {
                            engine.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                                override fun onStart(utteranceId: String?) = Unit
                                override fun onDone(utteranceId: String?) {
                                    if (utteranceId == "live_reply") {
                                        (context as? android.app.Activity)?.runOnUiThread {
                                            voiceStatus = "در حال گوش دادن..."
                                            startListening()
                                        }
                                    }
                                }
                                override fun onError(utteranceId: String?) {
                                    if (utteranceId == "live_reply") {
                                        (context as? android.app.Activity)?.runOnUiThread {
                                            voiceStatus = "پخش صدا ناموفق بود؛ برای ادامه لمس کنید"
                                        }
                                    }
                                }
                            })
                            engine.speak(SpeechText.forSpeech(reply), TextToSpeech.QUEUE_FLUSH, null, "live_reply")
                        }
                    }
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    recognizedText = matches[0]
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        try {
            speechRecognizer.startListening(intent)
        } catch (_: Exception) {}
    }

    LaunchedEffect(Unit) {
        startListening()
    }

    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer?.destroy()
            ttsEngine?.stop()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Surface(
        color = Color(0xFF090D16).copy(alpha = 0.96f),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "مکالمه زنده با فوکو",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "بستن", tint = Color.White)
                }
            }

            // Pulsing Interactive Voice Orb (JARVIS 1 Interface)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .scale(if (isListening) pulseScale else 1f)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    if (isListening) PersianTurquoise else PersianViolet,
                                    Color(0xFF0F172A)
                                )
                            )
                        )
                        .clickable {
                            if (!isListening) startListening()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(54.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = voiceStatus,
                    color = PersianTurquoise,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )

                if (recognizedText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "شما: $recognizedText",
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                if (lastReplyText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = PersianTurquoise.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "فوکو: $lastReplyText",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { startListening() },
                    colors = ButtonDefaults.buttonColors(containerColor = PersianTurquoise),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("شروع مجدد گفتگو", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ChatDrawerContent(
    conversations: List<ChatConversationEntity>,
    currentId: String,
    onSelectConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onNewChat: () -> Unit,
    onOpenTwoWayVoice: () -> Unit,
    onNavigateToScreen: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Drawer Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PersianTurquoise),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Black)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("کاربر فوکو (FOKO)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("FOKO Autonomous Agent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(10.dp))

        // Quick Capabilities
        Text(
            text = "امکانات یکپارچه فوکو:",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))

        DrawerItem(
            icon = Icons.Default.RecordVoiceOver,
            title = "مکالمه دوطرفه صوتی",
            onClick = onOpenTwoWayVoice
        )
        DrawerItem(
            icon = Icons.Default.Settings,
            title = "تنظیمات کلیدها و دسترسی‌ها",
            onClick = { onNavigateToScreen("settings") }
        )

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "تاریخچه گفتگوها:",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(conversations) { item ->
                val isCurrent = item.id == currentId
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectConversation(item.id) },
                    color = if (isCurrent) PersianTurquoise.copy(alpha = 0.15f) else Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = { onDeleteConversation(item.id) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = onNewChat,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("گفتگوی جدید (+ New Chat)", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DrawerItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = title, tint = PersianTurquoise, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
    onReply: () -> Unit = {}
) {
    val isUser = message.sender == MessageSender.USER
    val isError = message.isError

    // ساعت پیام — گوشهٔ پایین حباب.
    val timeLabel = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.US).format(Date(message.timestamp))
    }
    val footerTint = when {
        isUser -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
        isError -> MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = when {
                isError -> MaterialTheme.colorScheme.errorContainer
                isUser -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Header (Sender / Model tag)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "شما" else "FOKO AI (فوکو)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else PersianTurquoise
                    )
                    if (message.modelUsed != null && !isUser) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = message.modelUsed,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Images preview if any
                val images = message.allImages()
                if (images.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        items(images) { b64 ->
                            val bitmap = remember(b64) {
                                try {
                                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                } catch (_: Exception) { null }
                            }
                            if (bitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }

                // Message Text — قابل انتخاب و کپی با نگه‌داشتن انگشت
                SelectionContainer {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            isError -> MaterialTheme.colorScheme.onErrorContainer
                            isUser -> MaterialTheme.colorScheme.onPrimary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        lineHeight = 22.sp
                    )
                }

                // Footer: ساعت پیام در گوشهٔ پایین + کپی / پخش صدا / ریپلای
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCopy, modifier = Modifier.size(26.dp)) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "کپی متن",
                            modifier = Modifier.size(14.dp),
                            tint = footerTint
                        )
                    }
                    if (!isUser && !isError) {
                        IconButton(onClick = onSpeak, modifier = Modifier.size(26.dp)) {
                            Icon(
                                Icons.Default.VolumeUp,
                                contentDescription = "پخش صدا",
                                modifier = Modifier.size(15.dp),
                                tint = PersianTurquoise
                            )
                        }
                    }
                    IconButton(onClick = onReply, modifier = Modifier.size(26.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.Reply,
                            contentDescription = "پاسخ به این پیام",
                            modifier = Modifier.size(15.dp),
                            tint = footerTint
                        )
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = timeLabel,
                        fontSize = 10.sp,
                        color = footerTint
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyChatGreeting(onSuggestionClick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(PersianTurquoise, PersianViolet))
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Black, modifier = Modifier.size(36.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "FOKO AI (دستیار هوشمند و مجری کارهای شما)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "آماده پاسخ به سوالات، اجرای دستورات گوشی و مکالمه صوتی دوطرفه",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SuggestionChipItem("🎙️ مکالمه زنده و صوتی دوطرفه با فوکو", onClick = { onSuggestionClick("سلام فوکو، خودتو معرفی کن و بگو چه کارهایی می‌تونی انجام بدی؟") })
            SuggestionChipItem("📱 باز کردن تلگرام، اینستاگرام، تماس و تنظیمات", onClick = { onSuggestionClick("تلگرام رو برام باز کن") })
            SuggestionChipItem("💡 تحلیل، استدلال و کدنویسی عمیق با Qwen و Grok", onClick = { onSuggestionClick("یک برنامه روزانه برای یادگیری هوش مصنوعی بهم پیشنهاد بده") })
        }
    }
}

@Composable
fun SuggestionChipItem(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
