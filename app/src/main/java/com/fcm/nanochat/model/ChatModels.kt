package com.fcm.nanochat.model

import com.fcm.nanochat.inference.InferenceMode
import com.fcm.nanochat.models.allowlist.AllowlistSourceType
import com.fcm.nanochat.models.compatibility.LocalModelCompatibilityState
import com.fcm.nanochat.models.registry.ModelInstallState
import com.fcm.nanochat.models.registry.ModelStorageLocation

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
        val isGeminiNanoSupported: Boolean = false,
        val activeLocalModelName: String? = null,
        val isLocalModelReady: Boolean = false,
        val localModelStatusMessage: String? = null,
        val localModelSupportsThinking: Boolean = false,
        val localModelSupportedAccelerators: List<String> = emptyList(),
        val isSending: Boolean = false,
        val notice: String? = null
)

data class SettingsScreenState(
        val baseUrl: String = "",
        val modelName: String = "",
        val apiKey: String = "",
        val temperature: Double = com.fcm.nanochat.data.AppPreferences.DEFAULT_TEMPERATURE,
        val topP: Double = com.fcm.nanochat.data.AppPreferences.DEFAULT_TOP_P,
        val contextLength: Int = com.fcm.nanochat.data.AppPreferences.DEFAULT_CONTEXT_LENGTH,
        val thinkingEffort: com.fcm.nanochat.data.ThinkingEffort =
                com.fcm.nanochat.data.ThinkingEffort.MEDIUM,
        val acceleratorPreference: com.fcm.nanochat.data.AcceleratorPreference =
                com.fcm.nanochat.data.AcceleratorPreference.AUTO,
        val stats: UsageStats = UsageStats(),
        val saveNotice: String? = null,
        val clearNotice: String? = null,
        val geminiStatus: GeminiNanoStatusUi = GeminiNanoStatusUi()
)

data class UsageStats(
        val sessionCount: Long = 0,
        val messagesSent: Long = 0,
        val messagesReceived: Long = 0
)

data class GeminiNanoStatusUi(
        val supported: Boolean = false,
        val downloaded: Boolean = false,
        val downloading: Boolean = false,
        val downloadable: Boolean = false,
        val bytesDownloaded: Long? = null,
        val bytesToDownload: Long? = null,
        val lastKnownModelSizeBytes: Long = 0,
        val message: String? = null
)

data class ModelGalleryScreenState(
        val allowlistVersion: String = "",
        val allowlistSource: AllowlistSourceType = AllowlistSourceType.BUNDLED,
        val allowlistRefreshedAtEpochMs: Long = 0L,
        val phase: ModelLibraryPhase = ModelLibraryPhase.Loading,
        val activeModelId: String? = null,
        val activeSummary: ActiveLocalModelSummaryUi? = null,
        val models: List<ModelCardUi> = emptyList(),
        val libraryError: String? = null,
        val runtimeMetrics: RuntimeDiagnosticsUi? = null,
        val notice: String? = null,
        val isRefreshing: Boolean = false
)

enum class ModelLibraryPhase {
    Loading,
    Ready,
    Empty,
    Error
}

enum class LocalModelMemoryState {
    NotSelected,
    SelectedNotLoaded,
    LoadingIntoMemory,
    LoadedInMemory,
    InUse,
    EjectedFromMemory,
    NeedsReload,
    FailedToLoad
}

data class ActiveLocalModelSummaryUi(
        val modelId: String,
        val displayName: String,
        val memoryState: LocalModelMemoryState,
        val statusText: String,
        val metricsText: String?
)

data class ModelCardUi(
        val modelId: String,
        val displayName: String,
        val description: String,
        val modelFile: String,
        val sourceRepo: String,
        val sizeInBytes: Long,
        val minDeviceMemoryInGb: Int,
        val taskTypes: List<String>,
        val bestForTaskTypes: List<String>,
        val llmSupportImage: Boolean,
        val llmSupportAudio: Boolean,
        val defaultTopK: Int = 40,
        val defaultTopP: Double = 0.9,
        val defaultTemperature: Double = 0.7,
        val defaultMaxTokens: Int = 1024,
        val acceleratorHints: List<String> = emptyList(),
        val recommendedForChat: Boolean,
        val isExperimental: Boolean,
        val installState: ModelInstallState,
        val compatibility: LocalModelCompatibilityState,
        val healthState: LocalModelHealthState = LocalModelHealthState.NotInstalled,
        val memoryState: LocalModelMemoryState = LocalModelMemoryState.NotSelected,
        val memoryMessage: String? = null,
        val isActive: Boolean,
        val isLegacy: Boolean,
        val storageLocation: ModelStorageLocation,
        val downloadedBytes: Long,
        val sizeOnDiskBytes: Long,
        val localPath: String?,
        val errorMessage: String?
)

data class RuntimeDiagnosticsUi(
        val modelId: String,
        val initDurationMs: Long,
        val timeToFirstTokenMs: Long,
        val generationDurationMs: Long,
        val tokensPerSecond: Double,
        val backend: String
)
