package com.fcm.nanochat.model

import com.fcm.nanochat.inference.InferenceMode

enum class ChatRole {
    USER,
    ASSISTANT
}

data class ChatMessage(
    val id: Long,
    val sessionId: Long,
    val role: ChatRole,
    val content: String,
    val isStreaming: Boolean = false
)

data class ChatSession(
    val id: Long,
    val title: String,
    val updatedAt: Long
)

data class ChatScreenState(
    val sessions: List<ChatSession> = emptyList(),
    val selectedSessionId: Long? = null,
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val inferenceMode: InferenceMode = InferenceMode.REMOTE,
    val isSending: Boolean = false,
    val notice: String? = null
)

data class SettingsScreenState(
    val baseUrl: String = "",
    val modelName: String = "",
    val apiKey: String = "",
    val huggingFaceToken: String = "",
    val saveNotice: String? = null
)

enum class AppTab {
    CHAT,
    MODELS,
    SETTINGS
}
