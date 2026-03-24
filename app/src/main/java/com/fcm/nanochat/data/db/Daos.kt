package com.fcm.nanochat.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.fcm.nanochat.model.ChatRole
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatSessionDao {
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun observeSessions(): Flow<List<ChatSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ChatSessionEntity): Long

    @Query("UPDATE chat_sessions SET title = :title, updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun updateSession(sessionId: Long, title: String, updatedAt: Long): Int

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long): Int

    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestSession(): ChatSessionEntity?

    @Query("SELECT COUNT(*) FROM chat_sessions")
    suspend fun countSessions(): Long

    @Query("DELETE FROM chat_sessions")
    suspend fun deleteAll(): Int
}

@Dao
interface ChatMessageDao {
    @Transaction
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC, id ASC")
    fun observeMessages(sessionId: Long): Flow<List<ChatMessageWithParts>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessageEntity): Long

    @Transaction
    suspend fun insertAll(messages: List<ChatMessageEntity>): List<Long> =
        messages.map { insert(it) }

    @Query("UPDATE chat_messages SET content = :content, updatedAt = :updatedAt WHERE id = :messageId")
    suspend fun updateContent(messageId: Long, content: String, updatedAt: Long): Int

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt DESC, id DESC LIMIT :limit")
    suspend fun latestMessages(sessionId: Long, limit: Int): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPart(part: ChatMessagePartEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParts(parts: List<ChatMessagePartEntity>): List<Long>

    @Query("SELECT * FROM chat_message_parts WHERE messageId = :messageId ORDER BY partIndex ASC, id ASC")
    suspend fun messageParts(messageId: Long): List<ChatMessagePartEntity>

    @Query(
        "SELECT * FROM chat_message_parts " +
                "WHERE messageId IN (SELECT id FROM chat_messages WHERE sessionId = :sessionId)"
    )
    suspend fun sessionParts(sessionId: Long): List<ChatMessagePartEntity>

    @Query("DELETE FROM chat_message_parts WHERE messageId = :messageId")
    suspend fun deleteMessageParts(messageId: Long): Int

    @Query("SELECT COUNT(*) FROM chat_message_parts WHERE relativePath = :relativePath")
    suspend fun countPartsByRelativePath(relativePath: String): Long

    @Query("SELECT COUNT(*) FROM chat_messages WHERE role = :role")
    suspend fun countByRole(role: ChatRole): Long

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: Long): Int

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAll(): Int
}

data class ChatMessageWithParts(
    @Embedded val message: ChatMessageEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "messageId",
        entity = ChatMessagePartEntity::class
    )
    val parts: List<ChatMessagePartEntity>
)

@Dao
interface InstalledModelDao {
    @Query("SELECT * FROM installed_models ORDER BY updatedAt DESC")
    fun observeInstalledModels(): Flow<List<InstalledModelEntity>>

    @Query("SELECT * FROM installed_models ORDER BY updatedAt DESC")
    suspend fun allInstalledModels(): List<InstalledModelEntity>

    @Query("SELECT * FROM installed_models WHERE modelId = :modelId LIMIT 1")
    suspend fun installedModel(modelId: String): InstalledModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(model: InstalledModelEntity)

    @Query("DELETE FROM installed_models WHERE modelId = :modelId")
    suspend fun deleteById(modelId: String): Int
}
