package com.fcm.nanochat.models.registry

import com.fcm.nanochat.models.compatibility.LocalModelCompatibilityState

data class ActiveModelResolution(
    val activeRecord: InstalledModelRecord?,
    val shouldClearSelection: Boolean,
    val message: String?
)

object ActiveModelResolver {
    fun resolve(activeModelId: String, records: List<InstalledModelRecord>): ActiveModelResolution {
        val normalizedId = activeModelId.trim().lowercase()
        if (normalizedId.isBlank()) {
            return ActiveModelResolution(
                activeRecord = null,
                shouldClearSelection = false,
                message = "Choose a local model from Model Library."
            )
        }

        val record = records.firstOrNull { it.modelId == normalizedId }
            ?: return ActiveModelResolution(
                activeRecord = null,
                shouldClearSelection = true,
                message = "Selected local model no longer exists."
            )

        val isChatReady = record.allowlistedModel?.recommendedForChat ?: true
        if (!record.isLegacy && !isChatReady) {
            return ActiveModelResolution(
                activeRecord = record,
                shouldClearSelection = true,
                message = "Selected local model is not optimized for chat."
            )
        }

        val ready = record.installState == ModelInstallState.INSTALLED &&
                record.compatibility is LocalModelCompatibilityState.Ready &&
                !record.localPath.isNullOrBlank()
        if (ready) {
            return ActiveModelResolution(
                activeRecord = record,
                shouldClearSelection = false,
                message = null
            )
        }

        // Model is selected but not fully ready.
        // Preserve the selection during transient download states so the UI
        // shows progress instead of the confusing "Select a local model" prompt.
        val isTransientDownloadState = record.installState in setOf(
            ModelInstallState.QUEUED,
            ModelInstallState.DOWNLOADING,
            ModelInstallState.VALIDATING,
            ModelInstallState.PAUSED,
            ModelInstallState.MOVING
        )

        val message = if (isTransientDownloadState) {
            downloadStateMessage(record)
        } else {
            toFriendlyResolverMessage(
                record.errorMessage ?: compatibilityReason(record.compatibility)
            )
        }

        return ActiveModelResolution(
            activeRecord = record,
            shouldClearSelection = !isTransientDownloadState,
            message = message
        )
    }

    private fun downloadStateMessage(record: InstalledModelRecord): String {
        val name = record.displayName.ifBlank { "Local model" }
        return when (record.installState) {
            ModelInstallState.QUEUED -> "Preparing to download $name…"
            ModelInstallState.DOWNLOADING -> {
                val total = record.sizeBytes
                val downloaded = record.downloadedBytes
                if (total > 0 && downloaded > 0) {
                    val percent = (downloaded * 100 / total).coerceAtMost(99)
                    "Downloading $name ($percent%)"
                } else {
                    "Downloading $name…"
                }
            }
            ModelInstallState.VALIDATING -> "Verifying $name…"
            ModelInstallState.PAUSED -> "Download paused for $name. Open Model Library to resume."
            ModelInstallState.MOVING -> "Moving $name to storage…"
            else -> "Preparing $name…"
        }
    }

    private fun compatibilityReason(compatibility: LocalModelCompatibilityState): String? {
        return when (compatibility) {
            is LocalModelCompatibilityState.RuntimeUnavailable -> compatibility.reason
            is LocalModelCompatibilityState.DownloadedButNotActivatable -> compatibility.reason
            LocalModelCompatibilityState.Ready,
            LocalModelCompatibilityState.Downloadable,
            is LocalModelCompatibilityState.NeedsMoreRam,
            is LocalModelCompatibilityState.NeedsMoreStorage,
            is LocalModelCompatibilityState.UnsupportedDevice,
            LocalModelCompatibilityState.UnsupportedForChat,
            LocalModelCompatibilityState.CorruptedModel -> null
        }
    }

    private fun toFriendlyResolverMessage(raw: String?): String {
        val message = raw?.trim().orEmpty()
        if (message.isBlank()) return "Selected local model is not ready."

        val lowercase = message.lowercase()
        return when {
            isStartupValidationFailure(lowercase) -> {
                "Installed, but NanoChat could not start this model."
            }

            isRuntimeOptionFailure(lowercase) -> {
                "This model could not start with the current on-device runtime."
            }

            isMissingFileFailure(lowercase) -> {
                "Local model file is missing. Re-download and try again."
            }

            else -> message
        }
    }

    private fun isStartupValidationFailure(message: String): Boolean {
        return "startup_validation_failed" in message ||
                "error building tflite model" in message ||
                "flatbuffer" in message ||
                "invocationtargetexception" in message
    }

    private fun isRuntimeOptionFailure(message: String): Boolean {
        return "missing runtime option method" in message || "settopk" in message
    }

    private fun isMissingFileFailure(message: String): Boolean {
        return "missing" in message && "file" in message
    }
}
