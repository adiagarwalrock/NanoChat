package com.fcm.nanochat.models.compatibility

sealed interface LocalModelCompatibilityState {
    data object Ready : LocalModelCompatibilityState
    data object Downloadable : LocalModelCompatibilityState
    data class NeedsMoreStorage(
        val requiredBytes: Long,
        val availableBytes: Long
    ) : LocalModelCompatibilityState

    data class NeedsMoreRam(
        val requiredGb: Int,
        val availableGb: Int
    ) : LocalModelCompatibilityState

    data class UnsupportedDevice(val reason: String) : LocalModelCompatibilityState
    data object TokenRequired : LocalModelCompatibilityState
    data class DownloadedButNotActivatable(val reason: String) : LocalModelCompatibilityState
    data object CorruptedModel : LocalModelCompatibilityState
    data class RuntimeUnavailable(val reason: String) : LocalModelCompatibilityState
}
