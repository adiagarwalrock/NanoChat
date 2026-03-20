package com.fcm.nanochat.model

sealed interface LocalModelHealthState {
    data object NotInstalled : LocalModelHealthState
    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : LocalModelHealthState

    data class Paused(
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : LocalModelHealthState

    data class DownloadFailed(val message: String) : LocalModelHealthState
    data object InstalledNeedsValidation : LocalModelHealthState
    data object InstalledReady : LocalModelHealthState
    data class InstalledStartupFailed(val message: String) : LocalModelHealthState
    data object RequiresLicenseApproval : LocalModelHealthState
    data class NotCompatible(val message: String) : LocalModelHealthState
    data object UnsupportedForChat : LocalModelHealthState
}

val LocalModelHealthState.needsAttention: Boolean
    get() = when (this) {
        LocalModelHealthState.NotInstalled,
        is LocalModelHealthState.Downloading,
        is LocalModelHealthState.Paused,
        LocalModelHealthState.InstalledReady -> false

        else -> true
    }
