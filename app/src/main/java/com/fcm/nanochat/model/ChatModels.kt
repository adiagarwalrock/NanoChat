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
    val inferenceMode: InferenceMode = InferenceMode.REMOTE,
    val modelName: String = "",
    val temperature: Double = com.fcm.nanochat.data.AppPreferences.DEFAULT_TEMPERATURE,
    val topP: Double = com.fcm.nanochat.data.AppPreferences.DEFAULT_TOP_P,
    val contextLength: Int = com.fcm.nanochat.data.AppPreferences.DEFAULT_CONTEXT_LENGTH,
    val isStreaming: Boolean = false
)

data class ChatSession(
    val id: Long,
    val title: String,
    val updatedAt: Long,
    val isPinned: Boolean = false
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
    val temperature: Double = com.fcm.nanochat.data.AppPreferences.DEFAULT_TEMPERATURE,
    val topP: Double = com.fcm.nanochat.data.AppPreferences.DEFAULT_TOP_P,
    val contextLength: Int = com.fcm.nanochat.data.AppPreferences.DEFAULT_CONTEXT_LENGTH,
    val stats: UsageStats = UsageStats(),
    val saveNotice: String? = null,
    val clearNotice: String? = null
)

data class UsageStats(
    val sessionCount: Long = 0,
    val messagesSent: Long = 0,
    val messagesReceived: Long = 0
)
