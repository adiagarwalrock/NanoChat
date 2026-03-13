package com.fcm.nanochat.models.registry

import com.fcm.nanochat.models.compatibility.LocalModelCompatibilityState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveModelResolverTest {
    @Test
    fun `resolve marks ready installed model as valid`() {
        val record = sampleRecord(
            modelId = "model-a",
            installState = ModelInstallState.INSTALLED,
            compatibility = LocalModelCompatibilityState.Ready
        )

        val resolution = ActiveModelResolver.resolve("model-a", listOf(record))

        assertFalse(resolution.shouldClearSelection)
        assertTrue(resolution.activeRecord != null)
    }

    @Test
    fun `resolve clears missing active model`() {
        val resolution = ActiveModelResolver.resolve("unknown", emptyList())

        assertTrue(resolution.shouldClearSelection)
        assertTrue(resolution.activeRecord == null)
    }

    @Test
    fun `resolve clears non-ready model`() {
        val record = sampleRecord(
            modelId = "model-a",
            installState = ModelInstallState.FAILED,
            compatibility = LocalModelCompatibilityState.DownloadedButNotActivatable("broken")
        )

        val resolution = ActiveModelResolver.resolve("model-a", listOf(record))

        assertTrue(resolution.shouldClearSelection)
        assertTrue(resolution.message?.contains("broken") == true)
    }

    private fun sampleRecord(
        modelId: String,
        installState: ModelInstallState,
        compatibility: LocalModelCompatibilityState
    ): InstalledModelRecord {
        return InstalledModelRecord(
            modelId = modelId,
            displayName = "Model",
            allowlistedModel = null,
            installState = installState,
            storageLocation = ModelStorageLocation.INTERNAL,
            localPath = "/tmp/model",
            sizeBytes = 1024,
            downloadedBytes = 1024,
            errorMessage = if (installState == ModelInstallState.FAILED) "broken" else null,
            allowlistVersion = "1_0_10",
            compatibility = compatibility,
            isLegacy = false,
            isActive = true
        )
    }
}
