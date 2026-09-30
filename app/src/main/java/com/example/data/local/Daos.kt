package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<ChatConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :conversationId LIMIT 1")
    suspend fun getConversationById(conversationId: String): ChatConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ChatConversationEntity)

    @Update
    suspend fun updateConversation(conversation: ChatConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: String)

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesList(conversationId: String): List<ChatMessageEntity>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE conversationId = :conversationId")
    fun getMessageCountFlow(conversationId: String): Flow<Int>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessageForConversation(conversationId: String): ChatMessageEntity?

    @Query("SELECT * FROM conversations WHERE title LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchConversations(query: String): Flow<List<ChatConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()

    @Query("DELETE FROM conversations")
    suspend fun clearAllConversations()
}

@Dao
interface CreationDao {
    @Query("SELECT * FROM creations ORDER BY createdAt DESC")
    fun getAllCreations(): Flow<List<CreationEntity>>

    @Query("SELECT * FROM creations WHERE type = :type ORDER BY createdAt DESC")
    fun getCreationsByType(type: String): Flow<List<CreationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCreation(creation: CreationEntity)

    @Query("DELETE FROM creations WHERE id = :id")
    suspend fun deleteCreation(id: String)

    @Query("DELETE FROM creations")
    suspend fun clearAllCreations()
}

@Dao
interface LibraryDao {
    @Query("SELECT * FROM library_folders ORDER BY sortOrder ASC, createdAt ASC")
    fun getAllFolders(): Flow<List<LibraryFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: LibraryFolderEntity)

    @Query("DELETE FROM library_folders WHERE id = :id")
    suspend fun deleteFolder(id: String)

    @Query("UPDATE library_items SET folderId = NULL WHERE folderId = :folderId")
    suspend fun moveItemsToInbox(folderId: String)

    @Query("SELECT * FROM library_items ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllItems(): Flow<List<LibraryItemEntity>>

    @Query("SELECT * FROM library_items WHERE folderId = :folderId ORDER BY isPinned DESC, updatedAt DESC")
    fun getItemsInFolder(folderId: String): Flow<List<LibraryItemEntity>>

    @Query("SELECT * FROM library_items WHERE folderId IS NULL ORDER BY isPinned DESC, updatedAt DESC")
    fun getInboxItems(): Flow<List<LibraryItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: LibraryItemEntity)

    @Query("DELETE FROM library_items WHERE id = :id")
    suspend fun deleteItem(id: String)

    @Query("UPDATE library_items SET isPinned = :pinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE library_items SET folderId = :folderId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun moveItem(id: String, folderId: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM library_folders")
    suspend fun folderCount(): Int
}
