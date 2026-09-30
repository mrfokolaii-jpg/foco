package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ChatConversationEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val modelId: String,
    val personaId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey
    val id: String,
    val conversationId: String,
    val sender: String, // "USER" or "ASSISTANT"
    val text: String,
    val imageBase64: String? = null,
    val thinkingText: String? = null,
    val searchSourcesJson: String? = null,
    val modelUsed: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false
)

@Entity(tableName = "creations")
data class CreationEntity(
    @PrimaryKey
    val id: String,
    val type: String, // "IMAGE", "MUSIC", "VIDEO", "TOOL"
    val title: String,
    val prompt: String,
    val mediaData: String?, // Base64 or URL
    val modelUsed: String,
    val createdAt: Long = System.currentTimeMillis(),
    val metadata: String? = null // aspect ratio, resolution, genre, etc.
)

@Entity(tableName = "library_folders")
data class LibraryFolderEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0
)

@Entity(tableName = "library_items")
data class LibraryItemEntity(
    @PrimaryKey
    val id: String,
    val folderId: String? = null,
    val sourceType: String, // CHAT_MESSAGE, CONVERSATION, CREATION
    val sourceId: String,
    val title: String,
    val previewText: String,
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
