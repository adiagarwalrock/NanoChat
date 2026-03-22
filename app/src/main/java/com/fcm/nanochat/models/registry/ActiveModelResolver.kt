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

        return ActiveModelResolution(
            activeRecord = record,
            shouldClearSelection = true,
            message = toFriendlyResolverMessage(
                record.errorMessage ?: compatibilityReason(record.compatibility)
            )
        )
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
