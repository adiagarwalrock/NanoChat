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
