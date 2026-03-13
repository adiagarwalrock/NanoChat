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
                message = "Choose a local model from the gallery."
            )
        }

        val record = records.firstOrNull { it.modelId == normalizedId }
            ?: return ActiveModelResolution(
                activeRecord = null,
                shouldClearSelection = true,
                message = "Selected local model no longer exists."
            )

        val ready = record.installState == ModelInstallState.INSTALLED &&
            record.compatibility is LocalModelCompatibilityState.Ready
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
            message = record.errorMessage ?: "Selected local model is not ready."
        )
    }
}
