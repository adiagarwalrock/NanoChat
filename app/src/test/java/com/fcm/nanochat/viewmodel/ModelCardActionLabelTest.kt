package com.fcm.nanochat.viewmodel

import com.fcm.nanochat.model.ModelCardUi
import com.fcm.nanochat.models.compatibility.LocalModelCompatibilityState
import com.fcm.nanochat.models.registry.ModelInstallState
import com.fcm.nanochat.models.registry.ModelStorageLocation
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelCardActionLabelTest {
    @Test
    fun `token required state maps to requires token`() {
        val card = card(
            installState = ModelInstallState.NOT_INSTALLED,
            compatibility = LocalModelCompatibilityState.TokenRequired
        )

        assertEquals("Add token", card.primaryActionLabel())
    }

    @Test
    fun `installed active state maps to selected`() {
        val card = card(
            installState = ModelInstallState.INSTALLED,
            compatibility = LocalModelCompatibilityState.Ready,
            isActive = true
        )

        assertEquals("Selected", card.primaryActionLabel())
    }

    @Test
    fun `failed state maps to retry`() {
        val card = card(
            installState = ModelInstallState.FAILED,
            compatibility = LocalModelCompatibilityState.DownloadedButNotActivatable("failed")
        )

        assertEquals("Retry", card.primaryActionLabel())
    }

    private fun card(
        installState: ModelInstallState,
        compatibility: LocalModelCompatibilityState,
        isActive: Boolean = false
    ): ModelCardUi {
        return ModelCardUi(
            modelId = "id",
            displayName = "Model",
            description = "Description",
            modelFile = "model.litertlm",
            sourceRepo = "repo",
            sizeInBytes = 100,
            minDeviceMemoryInGb = 6,
            taskTypes = listOf("llm_chat"),
            bestForTaskTypes = listOf("llm_chat"),
            llmSupportImage = false,
            llmSupportAudio = false,
            requiresHfToken = false,
            recommendedForChat = true,
            isExperimental = false,
            installState = installState,
            compatibility = compatibility,
            isActive = isActive,
            isLegacy = false,
            storageLocation = ModelStorageLocation.INTERNAL,
            downloadedBytes = 0,
            sizeOnDiskBytes = 0,
            localPath = null,
            errorMessage = null
        )
    }
}
