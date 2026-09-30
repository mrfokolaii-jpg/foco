package com.example.ui.screens.library

import android.app.Application
import android.content.Intent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.AppDatabase
import com.example.data.local.ChatConversationEntity
import com.example.data.local.CreationEntity
import com.example.data.local.LibraryFolderEntity
import com.example.data.local.LibraryItemEntity
import com.example.data.model.ChatMessage
import com.example.data.model.ConversationExport
import com.example.data.model.MessageSender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class LibraryUiState(
    val conversations: List<ChatConversationEntity> = emptyList(),
    val creations: List<CreationEntity> = emptyList(),
    val folders: List<LibraryFolderEntity> = emptyList(),
    val items: List<LibraryItemEntity> = emptyList(),
    val searchQuery: String = "",
    val selectedFolderId: String? = null
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val chatDao = db.chatDao()
    private val creationDao = db.creationDao()
    private val libraryDao = db.libraryDao()

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { ensureDefaultFolders() }
        viewModelScope.launch {
            chatDao.getAllConversations().collectLatest { list ->
                _uiState.value = _uiState.value.copy(conversations = list)
            }
        }
        viewModelScope.launch {
            creationDao.getAllCreations().collectLatest { list ->
                _uiState.value = _uiState.value.copy(creations = list)
            }
        }
        viewModelScope.launch {
            libraryDao.getAllFolders().collectLatest { list ->
                _uiState.value = _uiState.value.copy(folders = list)
            }
        }
        viewModelScope.launch {
            libraryDao.getAllItems().collectLatest { list ->
                _uiState.value = _uiState.value.copy(items = list)
            }
        }
    }

    private suspend fun ensureDefaultFolders() {
        if (libraryDao.folderCount() > 0) return
        val defaults = listOf("صندوق" to 0, "چت‌های مهم" to 1, "تصاویر" to 2, "یادداشت" to 3)
        defaults.forEach { (name, order) ->
            libraryDao.insertFolder(
                LibraryFolderEntity(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    sortOrder = order
                )
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun selectFolder(id: String?) {
        _uiState.value = _uiState.value.copy(selectedFolderId = id)
    }

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            libraryDao.insertFolder(
                LibraryFolderEntity(
                    id = UUID.randomUUID().toString(),
                    name = trimmed,
                    sortOrder = _uiState.value.folders.size
                )
            )
        }
    }

    fun renameFolder(folder: LibraryFolderEntity, name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            libraryDao.insertFolder(folder.copy(name = trimmed))
        }
    }

    fun deleteFolder(id: String) {
        viewModelScope.launch {
            libraryDao.moveItemsToInbox(id)
            libraryDao.deleteFolder(id)
            if (_uiState.value.selectedFolderId == id) {
                _uiState.value = _uiState.value.copy(selectedFolderId = null)
            }
        }
    }

    fun pinConversation(conv: ChatConversationEntity) {
        viewModelScope.launch {
            val existing = _uiState.value.items.find {
                it.sourceType == "CONVERSATION" && it.sourceId == conv.id
            }
            if (existing != null) {
                libraryDao.setPinned(existing.id, !existing.isPinned)
            } else {
                libraryDao.insertItem(
                    LibraryItemEntity(
                        id = UUID.randomUUID().toString(),
                        folderId = importantFolderId(),
                        sourceType = "CONVERSATION",
                        sourceId = conv.id,
                        title = conv.title,
                        previewText = conv.modelId,
                        isPinned = true
                    )
                )
            }
        }
    }

    fun saveConversation(conv: ChatConversationEntity) {
        viewModelScope.launch {
            libraryDao.insertItem(
                LibraryItemEntity(
                    id = UUID.randomUUID().toString(),
                    folderId = importantFolderId(),
                    sourceType = "CONVERSATION",
                    sourceId = conv.id,
                    title = conv.title,
                    previewText = conv.modelId
                )
            )
        }
    }

    fun togglePinItem(item: LibraryItemEntity) {
        viewModelScope.launch { libraryDao.setPinned(item.id, !item.isPinned) }
    }

    fun moveItem(itemId: String, folderId: String?) {
        viewModelScope.launch { libraryDao.moveItem(itemId, folderId) }
    }

    fun deleteItem(id: String) {
        viewModelScope.launch { libraryDao.deleteItem(id) }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            chatDao.deleteConversation(id)
            chatDao.deleteMessagesForConversation(id)
        }
    }

    fun clearAllConversations() {
        viewModelScope.launch {
            chatDao.clearAllConversations()
            chatDao.clearAllMessages()
        }
    }

    fun deleteCreation(id: String) {
        viewModelScope.launch { creationDao.deleteCreation(id) }
    }

    fun clearAllCreations() {
        viewModelScope.launch { creationDao.clearAllCreations() }
    }

    suspend fun exportConversationText(conversationId: String): String? {
        val conv = chatDao.getConversationById(conversationId) ?: return null
        val entities = chatDao.getMessagesList(conversationId)
        val messages = entities.map { e ->
            ChatMessage(
                id = e.id,
                sender = if (e.sender == "USER") MessageSender.USER else MessageSender.ASSISTANT,
                text = e.text,
                timestamp = e.timestamp
            )
        }
        return ConversationExport.toPlainText(conv.title, messages)
    }

    fun saveMessageToLibrary(title: String, text: String, conversationId: String) {
        viewModelScope.launch {
            libraryDao.insertItem(
                LibraryItemEntity(
                    id = UUID.randomUUID().toString(),
                    folderId = _uiState.value.folders.find { it.name == "یادداشت" }?.id,
                    sourceType = "CHAT_MESSAGE",
                    sourceId = conversationId,
                    title = if (title.length > 40) title.take(38) + "..." else title.ifBlank { "پیام ذخیره‌شده" },
                    previewText = text.take(200)
                )
            )
        }
    }

    private fun importantFolderId(): String? =
        _uiState.value.folders.find { it.name == "چت‌های مهم" }?.id
}

@Composable
fun LibraryScreen(
    onSelectConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var moveTarget by remember { mutableStateOf<LibraryItemEntity?>(null) }

    val filteredConversations = remember(uiState.conversations, uiState.searchQuery) {
        if (uiState.searchQuery.isBlank()) uiState.conversations
        else uiState.conversations.filter { it.title.contains(uiState.searchQuery, ignoreCase = true) }
    }
    val filteredCreations = remember(uiState.creations, uiState.searchQuery) {
        if (uiState.searchQuery.isBlank()) uiState.creations
        else uiState.creations.filter {
            it.title.contains(uiState.searchQuery, ignoreCase = true) ||
                it.prompt.contains(uiState.searchQuery, ignoreCase = true)
        }
    }
    val filteredItems = remember(uiState.items, uiState.searchQuery, uiState.selectedFolderId) {
        uiState.items.filter { item ->
            val folderOk = uiState.selectedFolderId == null || item.folderId == uiState.selectedFolderId
            val queryOk = uiState.searchQuery.isBlank() ||
                item.title.contains(uiState.searchQuery, ignoreCase = true) ||
                item.previewText.contains(uiState.searchQuery, ignoreCase = true)
            folderOk && queryOk
        }.sortedWith(compareByDescending<LibraryItemEntity> { it.isPinned }.thenByDescending { it.updatedAt })
    }
    val pinnedConversations = remember(filteredConversations, uiState.items) {
        val pinnedIds = uiState.items.filter { it.isPinned && it.sourceType == "CONVERSATION" }.map { it.sourceId }.toSet()
        filteredConversations.filter { it.id in pinnedIds }
    }
    val unpinnedConversations = remember(filteredConversations, pinnedConversations) {
        val pinnedIds = pinnedConversations.map { it.id }.toSet()
        filteredConversations.filter { it.id !in pinnedIds }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.LibraryBooks,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "کتابخانه محلی",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "پوشه، سنجاق و خروجی متن روی دستگاه",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = { showNewFolderDialog = true }, modifier = Modifier.testTag("new_folder_btn")) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = "پوشه جدید")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.onSearchQueryChanged(it) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("library_search_input"),
            placeholder = { Text("جستجو در کتابخانه...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "جستجو") },
            trailingIcon = {
                if (uiState.searchQuery.isNotBlank()) {
                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "پاک کردن")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = uiState.selectedFolderId == null,
                    onClick = { viewModel.selectFolder(null) },
                    label = { Text("همه پوشه‌ها") }
                )
            }
            items(uiState.folders, key = { it.id }) { folder ->
                FilterChip(
                    selected = uiState.selectedFolderId == folder.id,
                    onClick = { viewModel.selectFolder(folder.id) },
                    label = { Text(folder.name) },
                    leadingIcon = {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.clip(RoundedCornerShape(14.dp))
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("گفتگوها", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("ذخیره‌شده‌ها", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("آثار", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (selectedTab) {
            0 -> ConversationList(
                pinned = pinnedConversations,
                others = unpinnedConversations,
                items = uiState.items,
                onOpen = onSelectConversation,
                onPin = { viewModel.pinConversation(it) },
                onSave = { viewModel.saveConversation(it) },
                onDelete = { viewModel.deleteConversation(it.id) },
                onExport = { conv ->
                    viewModel.viewModelScope.launch {
                        val text = viewModel.exportConversationText(conv.id) ?: return@launch
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                            putExtra(Intent.EXTRA_TITLE, conv.title)
                        }
                        context.startActivity(Intent.createChooser(send, "خروجی متن گفتگو"))
                    }
                },
                emptyQuery = uiState.searchQuery.isNotBlank()
            )
            1 -> SavedItemsList(
                items = filteredItems,
                folders = uiState.folders,
                onPin = { viewModel.togglePinItem(it) },
                onDelete = { viewModel.deleteItem(it) },
                onMove = { moveTarget = it },
                onOpenConversation = onSelectConversation
            )
            else -> CreationsList(
                creations = filteredCreations,
                onDelete = { viewModel.deleteCreation(it.id) },
                emptyQuery = uiState.searchQuery.isNotBlank()
            )
        }
    }

    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("پوشه جدید", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("نام پوشه") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.createFolder(newFolderName)
                    newFolderName = ""
                    showNewFolderDialog = false
                }) { Text("ساخت") }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) { Text("انصراف") }
            }
        )
    }

    moveTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { moveTarget = null },
            title = { Text("انتقال به پوشه", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    TextButton(onClick = {
                        viewModel.moveItem(item.id, null)
                        moveTarget = null
                    }) { Text("صندوق") }
                    uiState.folders.forEach { folder ->
                        TextButton(onClick = {
                            viewModel.moveItem(item.id, folder.id)
                            moveTarget = null
                        }) { Text(folder.name) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { moveTarget = null }) { Text("بستن") }
            }
        )
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("حذف", fontWeight = FontWeight.Bold) },
            text = { Text("آیا مطمئن هستید؟") },
            confirmButton = {
                TextButton(onClick = {
                    if (selectedTab == 0) viewModel.clearAllConversations() else viewModel.clearAllCreations()
                    showClearConfirmDialog = false
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) { Text("انصراف") }
            }
        )
    }
}

@Composable
private fun ConversationList(
    pinned: List<ChatConversationEntity>,
    others: List<ChatConversationEntity>,
    items: List<LibraryItemEntity>,
    onOpen: (String) -> Unit,
    onPin: (ChatConversationEntity) -> Unit,
    onSave: (ChatConversationEntity) -> Unit,
    onDelete: (ChatConversationEntity) -> Unit,
    onExport: (ChatConversationEntity) -> Unit,
    emptyQuery: Boolean
) {
    val all = pinned + others
    if (all.isEmpty()) {
        EmptyHint(if (emptyQuery) "گفتگویی یافت نشد." else "هنوز هیچ گفتگویی ثبت نشده است.")
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        if (pinned.isNotEmpty()) {
            item {
                Text("سنجاق‌شده", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
            items(pinned, key = { "p-${it.id}" }) { conv ->
                ConversationCard(conv, true, onOpen, onPin, onSave, onDelete, onExport)
            }
        }
        items(others, key = { it.id }) { conv ->
            val pinnedIds = items.filter { it.isPinned && it.sourceType == "CONVERSATION" }.map { it.sourceId }
            ConversationCard(conv, conv.id in pinnedIds, onOpen, onPin, onSave, onDelete, onExport)
        }
    }
}

@Composable
private fun ConversationCard(
    conv: ChatConversationEntity,
    isPinned: Boolean,
    onOpen: (String) -> Unit,
    onPin: (ChatConversationEntity) -> Unit,
    onSave: (ChatConversationEntity) -> Unit,
    onDelete: (ChatConversationEntity) -> Unit,
    onExport: (ChatConversationEntity) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(conv.id) }
            .testTag("conversation_card_${conv.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(conv.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(conv.updatedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { onPin(conv) }) {
                Icon(
                    imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = "سنجاق",
                    tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { onExport(conv) }) {
                Icon(Icons.Default.Share, contentDescription = "خروجی متن")
            }
            IconButton(onClick = { onSave(conv) }) {
                Icon(Icons.Default.Add, contentDescription = "ذخیره در کتابخانه")
            }
            IconButton(onClick = { onDelete(conv) }) {
                Icon(Icons.Default.Delete, contentDescription = "حذف گفتگو", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SavedItemsList(
    items: List<LibraryItemEntity>,
    folders: List<LibraryFolderEntity>,
    onPin: (LibraryItemEntity) -> Unit,
    onDelete: (String) -> Unit,
    onMove: (LibraryItemEntity) -> Unit,
    onOpenConversation: (String) -> Unit
) {
    if (items.isEmpty()) {
        EmptyHint("هنوز موردی در کتابخانه ذخیره نشده است.")
        return
    }
    val folderName = folders.associate { it.id to it.name }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        items(items, key = { it.id }) { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (item.sourceType == "CONVERSATION") onOpenConversation(item.sourceId)
                    },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            folderName[item.folderId] ?: "صندوق",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onPin(item) }) {
                        Icon(
                            if (item.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "سنجاق"
                        )
                    }
                    IconButton(onClick = { onMove(item) }) {
                        Icon(Icons.Default.Folder, contentDescription = "انتقال")
                    }
                    IconButton(onClick = { onDelete(item.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun CreationsList(
    creations: List<CreationEntity>,
    onDelete: (CreationEntity) -> Unit,
    emptyQuery: Boolean
) {
    if (creations.isEmpty()) {
        EmptyHint(if (emptyQuery) "اثری یافت نشد." else "هنوز اثری ساخته نشده است.")
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        items(creations, key = { it.id }) { item ->
            val icon = when (item.type) {
                "IMAGE" -> Icons.Default.Image
                "MUSIC" -> Icons.Default.MusicNote
                "VIDEO" -> Icons.Default.Movie
                else -> Icons.Default.LibraryBooks
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { onDelete(item) }) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف اثر", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
