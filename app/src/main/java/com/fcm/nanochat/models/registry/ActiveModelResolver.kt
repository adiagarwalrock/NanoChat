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
            message = toFriendlyResolverMessage(record.errorMessage)
        )
    }

    private fun toFriendlyResolverMessage(raw: String?): String {
        val message = raw?.trim().orEmpty()
        if (message.isBlank()) return "Selected local model is not ready."

        val lowercase = message.lowercase()
        return when {
            "missing runtime option method" in lowercase || "settopk" in lowercase -> {
                "This model could not start with the current on-device runtime."
            }

            "missing" in lowercase && "file" in lowercase -> {
                "Local model file is missing. Re-download and try again."
            }

            else -> message
        }
    }
}
