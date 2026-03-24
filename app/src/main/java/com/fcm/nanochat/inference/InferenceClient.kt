package com.fcm.nanochat.inference

import com.fcm.nanochat.data.SettingsSnapshot
import com.fcm.nanochat.model.ChatRole
import kotlinx.coroutines.flow.Flow

enum class InferenceMode {
    AICORE,
    DOWNLOADED,
    REMOTE
}

data class ChatTurn(
    val role: ChatRole,
    val content: String
)

data class SupportedState(
    val supported: Boolean,
    val reasonIfUnsupported: String? = null
) {
    companion object {
        fun supported(): SupportedState = SupportedState(supported = true)

        fun unsupported(reason: String): SupportedState =
            SupportedState(supported = false, reasonIfUnsupported = reason)
    }
}

data class InferenceCapabilities(
    val textGeneration: SupportedState,
    val visionUnderstanding: SupportedState,
    val audioTranscription: SupportedState,
    val streaming: SupportedState
) {
    companion object {
        fun defaultTextOnly(
            reason: String = "This backend does not support this modality."
        ): InferenceCapabilities {
            return InferenceCapabilities(
                textGeneration = SupportedState.supported(),
                visionUnderstanding = SupportedState.unsupported(reason),
                audioTranscription = SupportedState.unsupported(reason),
                streaming = SupportedState.supported()
            )
        }
    }
}

data class InferenceImageAttachment(
    val relativePath: String,
    val mimeType: String
)

data class InferenceAudioAttachment(
    val relativePath: String,
    val mimeType: String,
    val displayName: String
)

data class InferenceRequest(
    val history: List<ChatTurn>,
    val prompt: String,
    val settings: SettingsSnapshot,
    val imageAttachment: InferenceImageAttachment? = null,
    val activeDownloadedModelId: String? = null,
    val sessionId: Long? = null
)

data class AudioTranscriptionRequest(
    val settings: SettingsSnapshot,
    val audioAttachment: InferenceAudioAttachment
)

data class AudioTranscriptionResult(
    val transcript: String
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
    class UnsupportedModality(message: String) : InferenceException(message)
    class MissingPermission(message: String) : InferenceException(message)
    class MissingFile(message: String) : InferenceException(message)
    class TranscriptionFailure(message: String, cause: Throwable? = null) :
        InferenceException(message, cause)

    class UploadFailure(message: String, cause: Throwable? = null) :
        InferenceException(message, cause)
}

interface InferenceClient {
    suspend fun availability(settings: SettingsSnapshot): BackendAvailability
    suspend fun capabilities(settings: SettingsSnapshot): InferenceCapabilities
    fun streamChat(request: InferenceRequest): Flow<String>
    suspend fun transcribeAudio(request: AudioTranscriptionRequest): AudioTranscriptionResult {
        throw InferenceException.UnsupportedModality(
            "This backend does not support audio transcription."
        )
    }

    fun release() = Unit
}
