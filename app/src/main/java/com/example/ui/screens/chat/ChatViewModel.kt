package com.example.ui.screens.chat

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.agent.AgentEngine
import com.example.data.agent.AgentEvent
import com.example.data.local.ApiKeyStore
import com.example.data.local.AppDatabase
import com.example.data.local.ChatConversationEntity
import com.example.data.local.ChatMessageEntity
import com.example.data.local.LibraryItemEntity
import com.example.data.model.AiModelCatalog
import com.example.data.model.CatalogModel
import com.example.data.model.ChatMessage
import com.example.data.model.GroundingSource
import com.example.data.model.MessageSender
import com.example.data.model.SystemPersona
import com.example.data.remote.AiRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.util.UUID

data class AttachedImage(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val base64: String
)

data class AttachedFile(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val textContent: String? = null
)

data class ChatUiState(
    val currentConversationId: String = UUID.randomUUID().toString(),
    val conversationTitle: String = "گفتگوی جدید",
    val messages: List<ChatMessage> = emptyList(),
    val selectedCatalogModel: CatalogModel = AiModelCatalog.defaultModel(),
    val isAutoMode: Boolean = true,
    val selectedPersona: SystemPersona = SystemPersona.GENERAL,
    val isThinkingModeEnabled: Boolean = false,
    val isGoogleSearchEnabled: Boolean = false,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val attachedImages: List<AttachedImage> = emptyList(),
    val attachedFiles: List<AttachedFile> = emptyList(),
    val conversationsList: List<ChatConversationEntity> = emptyList(),
    val isApiKeyMissing: Boolean = false,
    val hasAnyProviderKey: Boolean = false
) {
    val attachedImageBase64: String?
        get() = attachedImages.firstOrNull()?.base64
    val attachedImageUri: Uri?
        get() = attachedImages.firstOrNull()?.uri
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val chatDao = db.chatDao()
    private val libraryDao = db.libraryDao()
    private val keyStore = ApiKeyStore(application)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val conversations: StateFlow<List<ChatConversationEntity>> = chatDao.getAllConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        val defaultCatalog = AiRouter.pickDefault(keyStore)
        _uiState.value = _uiState.value.copy(selectedCatalogModel = defaultCatalog)
        checkApiKey()
        observeConversations()
        startNewConversation()
    }

    fun refreshKeys() {
        checkApiKey()
        if (!_uiState.value.hasAnyProviderKey) return
        val current = _uiState.value.selectedCatalogModel
        if (keyStore.keyFor(current.provider).isBlank()) {
            val next = AiRouter.pickDefault(keyStore)
            _uiState.value = _uiState.value.copy(selectedCatalogModel = next)
        }
    }

    private fun checkApiKey() {
        val hasAny = keyStore.hasAnyKey()
        _uiState.value = _uiState.value.copy(
            hasAnyProviderKey = hasAny,
            isApiKeyMissing = !hasAny
        )
    }

    private fun observeConversations() {
        viewModelScope.launch {
            chatDao.getAllConversations().collectLatest { list ->
                _uiState.value = _uiState.value.copy(conversationsList = list)
            }
        }
    }

    fun startNewConversation() {
        val newId = UUID.randomUUID().toString()
        _uiState.value = _uiState.value.copy(
            currentConversationId = newId,
            conversationTitle = "گفتگوی جدید",
            messages = emptyList(),
            attachedImages = emptyList(),
            attachedFiles = emptyList(),
            statusMessage = null
        )
    }

    fun enableAutoMode() {
        val autoModel = AiRouter.pickDefault(keyStore)
        _uiState.value = _uiState.value.copy(
            isAutoMode = true,
            selectedCatalogModel = autoModel
        )
    }

    fun selectCatalogModel(model: CatalogModel) {
        _uiState.value = _uiState.value.copy(
            isAutoMode = false,
            selectedCatalogModel = model
        )
    }

    fun getKeySnapshot() = keyStore.snapshot()

    fun getActiveModels(): List<CatalogModel> = AiModelCatalog.activeModels(keyStore.snapshot())

    fun toggleThinkingMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isThinkingModeEnabled = enabled)
    }

    fun toggleGoogleSearch(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isGoogleSearchEnabled = enabled)
    }

    fun selectPersona(persona: SystemPersona) {
        _uiState.value = _uiState.value.copy(selectedPersona = persona)
    }

    fun attachImages(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            try {
                val newItems = mutableListOf<AttachedImage>()
                for (uri in uris) {
                    try {
                        val inputStream = context.contentResolver.openInputStream(uri)
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        if (bitmap != null) {
                            val outputStream = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                            val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                            newItems.add(AttachedImage(uri = uri, base64 = base64))
                        }
                    } catch (_: Exception) {}
                }
                _uiState.value = _uiState.value.copy(
                    attachedImages = _uiState.value.attachedImages + newItems
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(statusMessage = "خطا در بارگذاری تصاویر: ${e.message}")
            }
        }
    }

    fun attachImage(context: Context, uri: Uri) {
        attachImages(context, listOf(uri))
    }

    fun removeAttachedImage(id: String) {
        _uiState.value = _uiState.value.copy(
            attachedImages = _uiState.value.attachedImages.filter { it.id != id }
        )
    }

    fun clearAttachedImages() {
        _uiState.value = _uiState.value.copy(attachedImages = emptyList())
    }

    private val textFileExtensions = setOf(
        "txt", "md", "json", "csv", "xml", "html", "htm", "kt", "java", "py",
        "js", "ts", "css", "log", "yml", "yaml", "kts", "gradle", "pro", "sql", "sh"
    )

    fun attachFiles(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val newFiles = mutableListOf<AttachedFile>()
            for (uri in uris) {
                try {
                    var name = "file"
                    var size = 0L
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                            if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                        }
                    }
                    val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
                    val ext = name.substringAfterLast('.', "").lowercase()
                    val isTextLike = mime.startsWith("text/") || mime.contains("json") || ext in textFileExtensions
                    val text = if (isTextLike && size in 1..500_000) {
                        try {
                            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                                it.readText().take(30_000)
                            }
                        } catch (_: Exception) { null }
                    } else null
                    newFiles.add(AttachedFile(uri = uri, name = name, sizeBytes = size, textContent = text))
                } catch (_: Exception) {}
            }
            _uiState.value = _uiState.value.copy(
                attachedFiles = _uiState.value.attachedFiles + newFiles,
                statusMessage = if (newFiles.isEmpty()) "خواندن فایل ناموفق بود" else null
            )
        }
    }

    fun removeAttachedFile(id: String) {
        _uiState.value = _uiState.value.copy(
            attachedFiles = _uiState.value.attachedFiles.filter { it.id != id }
        )
    }

    fun clearAttachedFiles() {
        _uiState.value = _uiState.value.copy(attachedFiles = emptyList())
    }

    fun sendMessage(inputText: String, onReply: ((String) -> Unit)? = null) {
        val trimmed = inputText.trim()
        val currentImages = _uiState.value.attachedImages
        val currentFiles = _uiState.value.attachedFiles
        if (trimmed.isBlank() && currentImages.isEmpty() && currentFiles.isEmpty()) return

        val filesBlock = if (currentFiles.isEmpty()) "" else currentFiles.joinToString("\n\n") { f ->
            val sizeKb = (f.sizeBytes / 1024).coerceAtLeast(1)
            if (f.textContent != null) {
                "📎 محتوای فایل پیوست «${f.name}» (${sizeKb}KB):\n```\n${f.textContent}\n```"
            } else {
                "📎 فایل پیوست «${f.name}» (${sizeKb}KB) ارسال شد. این فایل باینری است؛ برای کار با آن از ابزارهای save_file و read_saved_file استفاده کن."
            }
        }
        val effectiveText = listOf(trimmed, filesBlock).filter { it.isNotBlank() }.joinToString("\n\n")
        val displayText = if (currentFiles.isEmpty()) {
            trimmed
        } else {
            listOf(trimmed, currentFiles.joinToString("  ") { "📎 ${it.name}" })
                .filter { it.isNotBlank() }.joinToString("\n")
        }

        val base64List = currentImages.map { it.base64 }
        val userMessage = ChatMessage(
            sender = MessageSender.USER,
            text = displayText,
            imageBase64 = base64List.firstOrNull(),
            imageBase64List = base64List
        )

        val updatedMessages = _uiState.value.messages + userMessage
        val conversationId = _uiState.value.currentConversationId

        val isFirstUserMessage = _uiState.value.messages.none { it.sender == MessageSender.USER }
        val titleSource = trimmed.ifBlank { currentFiles.firstOrNull()?.name.orEmpty() }
        val newTitle = if (isFirstUserMessage && titleSource.isNotBlank()) {
            if (titleSource.length > 30) titleSource.take(28) + "..." else titleSource
        } else {
            _uiState.value.conversationTitle
        }

        val effectiveModel = if (_uiState.value.isAutoMode) {
            AiRouter.pickDefault(keyStore)
        } else {
            _uiState.value.selectedCatalogModel
        }

        _uiState.value = _uiState.value.copy(
            messages = updatedMessages,
            selectedCatalogModel = effectiveModel,
            isLoading = true,
            statusMessage = if (_uiState.value.isThinkingModeEnabled) "در حال تفکر عمیق و بررسی گام‌به‌گام..." else if (_uiState.value.isGoogleSearchEnabled) "در حال جستجو در وب و استخراج اطلاعات..." else "در حال پردازش و تولید پاسخ...",
            attachedImages = emptyList(),
            attachedFiles = emptyList(),
            conversationTitle = newTitle
        )

        viewModelScope.launch {
            chatDao.insertConversation(
                ChatConversationEntity(
                    id = conversationId,
                    title = newTitle,
                    modelId = effectiveModel.id,
                    personaId = _uiState.value.selectedPersona.id,
                    updatedAt = System.currentTimeMillis()
                )
            )
            chatDao.insertMessage(
                ChatMessageEntity(
                    id = userMessage.id,
                    conversationId = conversationId,
                    sender = "USER",
                    text = userMessage.text,
                    imageBase64 = userMessage.imageBase64,
                    timestamp = userMessage.timestamp
                )
            )

            val appCtx = getApplication<Application>()

            val replyText = AgentEngine.run(
                context = appCtx,
                userText = effectiveText,
                history = updatedMessages.dropLast(1),
                catalogModel = effectiveModel,
                keyStore = keyStore,
                personaPrompt = _uiState.value.selectedPersona.systemPrompt,
                enableThinkingMode = _uiState.value.isThinkingModeEnabled,
                enableGoogleSearch = _uiState.value.isGoogleSearchEnabled
            ) { event ->
                when (event) {
                    is AgentEvent.Status -> {
                        _uiState.value = _uiState.value.copy(statusMessage = event.message)
                    }
                    is AgentEvent.ToolUsed -> {
                        _uiState.value = _uiState.value.copy(
                            statusMessage = "اجرای ابزار ${event.name}..."
                        )
                    }
                    is AgentEvent.Failure -> {
                        _uiState.value = _uiState.value.copy(statusMessage = event.message)
                    }
                    is AgentEvent.Reply -> Unit
                }
            }

            val aiMsg = ChatMessage(
                sender = MessageSender.ASSISTANT,
                text = replyText,
                modelUsed = effectiveModel.badge
            )
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + aiMsg,
                isLoading = false,
                statusMessage = null
            )

            onReply?.invoke(replyText)

            chatDao.insertMessage(
                ChatMessageEntity(
                    id = aiMsg.id,
                    conversationId = conversationId,
                    sender = "ASSISTANT",
                    text = aiMsg.text,
                    modelUsed = aiMsg.modelUsed,
                    timestamp = aiMsg.timestamp
                )
            )
        }
    }

    fun loadConversation(conversationId: String) {
        viewModelScope.launch {
            val conv = chatDao.getConversationById(conversationId)
            chatDao.getMessagesForConversation(conversationId).collectLatest { entityList ->
                val converted = entityList.map { e ->
                    val sources = mutableListOf<GroundingSource>()
                    if (!e.searchSourcesJson.isNullOrBlank()) {
                        try {
                            val arr = JSONArray(e.searchSourcesJson)
                            for (i in 0 until arr.length()) {
                                val o = arr.getJSONObject(i)
                                sources.add(GroundingSource(o.getString("title"), o.getString("uri")))
                            }
                        } catch (_: Exception) {}
                    }
                    ChatMessage(
                        id = e.id,
                        sender = if (e.sender == "USER") MessageSender.USER else MessageSender.ASSISTANT,
                        text = e.text,
                        imageBase64 = e.imageBase64,
                        thinkingText = e.thinkingText,
                        searchSources = sources,
                        modelUsed = e.modelUsed,
                        timestamp = e.timestamp,
                        isError = e.isError
                    )
                }
                _uiState.value = _uiState.value.copy(
                    currentConversationId = conversationId,
                    conversationTitle = conv?.title ?: "گفتگو",
                    messages = if (converted.isEmpty()) _uiState.value.messages else converted,
                    isLoading = false
                )
            }
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            chatDao.deleteConversation(conversationId)
            chatDao.deleteMessagesForConversation(conversationId)
            if (_uiState.value.currentConversationId == conversationId) {
                startNewConversation()
            }
        }
    }

    fun saveMessageToLibrary(message: ChatMessage) {
        viewModelScope.launch {
            val title = if (message.text.length > 40) message.text.take(38) + "..." else message.text.ifBlank { "پیام ذخیره‌شده" }
            libraryDao.insertItem(
                LibraryItemEntity(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    content = message.text,
                    type = "chat",
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }
}
