package com.fcm.nanochat.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fcm.nanochat.inference.InferenceMode
import com.fcm.nanochat.model.ChatRole
import com.fcm.nanochat.models.registry.ModelInstallState
import com.fcm.nanochat.models.registry.ModelStorageLocation

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sessionId"),
        Index(value = ["sessionId", "createdAt", "id"])
    ]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: ChatRole,
    val content: String,
    val inferenceMode: InferenceMode,
    val modelName: String,
    val temperature: Double,
    val topP: Double,
    val contextLength: Int,
    val createdAt: Long,
    val updatedAt: Long
)

enum class ChatMessagePartType {
    IMAGE,
    AUDIO,
    TRANSCRIPT
}

enum class ChatMessagePartState {
    READY,
    PENDING,
    FAILED,
    COMPLETED
}

@Entity(
    tableName = "chat_message_parts",
    foreignKeys = [
        ForeignKey(
            entity = ChatMessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["messageId"]),
        Index(value = ["messageId", "partIndex"]),
        Index(value = ["partType"]),
        Index(value = ["sourceMessageId"])
    ]
)
data class ChatMessagePartEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: Long,
    val partIndex: Int,
    val partType: ChatMessagePartType,
    val relativePath: String?,
    val mimeType: String?,
    val displayName: String?,
    val sizeBytes: Long?,
    val widthPx: Int?,
    val heightPx: Int?,
    val durationMs: Long?,
    val sourceMessageId: Long?,
    val state: ChatMessagePartState,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "installed_models",
    indices = [
        Index(value = ["updatedAt"]),
        Index(value = ["installState"])
    ]
)
data class InstalledModelEntity(
    @PrimaryKey val modelId: String,
    val displayName: String,
    val modelFileName: String,
    val localPath: String,
    val sizeBytes: Long,
    val downloadedBytes: Long,
    val installState: ModelInstallState,
    val storageLocation: ModelStorageLocation,
    val allowlistVersion: String,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long
)
