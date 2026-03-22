package com.fcm.nanochat.models.registry

import com.fcm.nanochat.models.allowlist.AllowlistDefaultConfig
import com.fcm.nanochat.models.allowlist.AllowlistedModel
import com.fcm.nanochat.models.compatibility.LocalModelCompatibilityState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveModelResolverTest {
    @Test
    fun `resolve marks ready installed model as valid`() {
        val record =
            sampleRecord(
                modelId = "model-a",
                installState = ModelInstallState.INSTALLED,
                compatibility = LocalModelCompatibilityState.Ready
            )

        val resolution = ActiveModelResolver.resolve("model-a", listOf(record))

        assertFalse(resolution.shouldClearSelection)
        assertNotNull(resolution.activeRecord)
    }

    @Test
    fun `resolve clears missing active model`() {
        val resolution = ActiveModelResolver.resolve("unknown", emptyList())

        assertTrue(resolution.shouldClearSelection)
        assertNull(resolution.activeRecord)
    }

    @Test
    fun `resolve clears non-ready model`() {
        val record =
            sampleRecord(
                modelId = "model-a",
                installState = ModelInstallState.FAILED,
                compatibility =
                    LocalModelCompatibilityState.DownloadedButNotActivatable("broken")
            )

        val resolution = ActiveModelResolver.resolve("model-a", listOf(record))

        assertTrue(resolution.shouldClearSelection)
        assertTrue(resolution.message.orEmpty().contains("broken"))
    }

    @Test
    fun `resolve clears non-chat allowlisted model`() {
        val record =
            sampleRecord(
                modelId = "model-a",
                installState = ModelInstallState.INSTALLED,
                compatibility = LocalModelCompatibilityState.Ready,
                allowlistedModel = sampleAllowlistedModel()
            )

        val resolution = ActiveModelResolver.resolve("model-a", listOf(record))

        assertTrue(resolution.shouldClearSelection)
        assertTrue(resolution.message.orEmpty().contains("not optimized"))
    }

    private fun sampleRecord(
        modelId: String,
        installState: ModelInstallState,
        compatibility: LocalModelCompatibilityState,
        allowlistedModel: AllowlistedModel? = null
    ): InstalledModelRecord {
        return InstalledModelRecord(
            modelId = modelId,
            displayName = "Model",
            allowlistedModel = allowlistedModel,
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

    private fun sampleAllowlistedModel(): AllowlistedModel {
        return AllowlistedModel(
            id = "model-a",
            enabled = true,
            displayName = "Model A",
            name = "Model A",
            modelId = "org/model-a",
            modelFile = "model-a.litertlm",
            description = "Test",
            sizeInBytes = 1_024,
            minDeviceMemoryInGb = 6,
            commitHash = "main",
            defaultConfig =
                AllowlistDefaultConfig(
                    topK = 40,
                    topP = 0.9,
                    temperature = 0.7,
                    maxTokens = 1024,
                    accelerators = "cpu"
                ),
            taskTypes = listOf("llm_prompt_lab"),
            bestForTaskTypes = listOf("llm_prompt_lab"),
            llmSupportImage = false,
            llmSupportAudio = false,
            backendType = "litert-lm",
            sourceRepo = "org/model-a",
            downloadRepo = null,
            downloadPath = null,
            isExperimental = false,
            supportedUseCases = listOf("prompt_lab"),
            recommendedForChat = false,
            memoryTier = "mid",
            acceleratorHints = listOf("cpu"),
            downloadUrl = "https://example.com/model-a.litertlm",
            fileType = "litertlm",
            supportedAbis = emptyList(),
            promptFamily = null,
            supportsThinking = false
        )
    }
}
