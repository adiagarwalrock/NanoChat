package com.fcm.nanochat.inference

import com.fcm.nanochat.data.SettingsSnapshot
import com.fcm.nanochat.model.ChatRole
import kotlinx.coroutines.flow.Flow

enum class InferenceMode {
    AICORE,
    REMOTE
}

data class ChatTurn(
    val role: ChatRole,
    val content: String
)

data class InferenceRequest(
    val history: List<ChatTurn>,
    val prompt: String,
    val settings: SettingsSnapshot
)

sealed interface BackendAvailability {
    data object Available : BackendAvailability
    data class Unavailable(val message: String) : BackendAvailability
}

sealed class InferenceException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    class Configuration(message: String) : InferenceException(message)
    class BackendUnavailable(message: String) : InferenceException(message)
    class Busy(message: String) : InferenceException(message)
    class RemoteFailure(message: String, cause: Throwable? = null) : InferenceException(message, cause)
}

interface InferenceClient {
    suspend fun availability(settings: SettingsSnapshot): BackendAvailability
    fun streamChat(request: InferenceRequest): Flow<String>
}
