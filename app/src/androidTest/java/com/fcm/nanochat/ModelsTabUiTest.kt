package com.fcm.nanochat

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.fcm.nanochat.model.ModelCardUi
import com.fcm.nanochat.model.ModelGalleryScreenState
import com.fcm.nanochat.model.ModelLibraryPhase
import com.fcm.nanochat.models.allowlist.AllowlistSourceType
import com.fcm.nanochat.models.compatibility.LocalModelCompatibilityState
import com.fcm.nanochat.models.registry.ModelInstallState
import com.fcm.nanochat.models.registry.ModelStorageLocation
import com.fcm.nanochat.ui.ModelsTab
import org.junit.Rule
import org.junit.Test

class ModelsTabUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersModelGalleryCard() {
        val state = ModelGalleryScreenState(
            allowlistVersion = "1_0_10",
            allowlistSource = AllowlistSourceType.BUNDLED,
            phase = ModelLibraryPhase.Ready,
            models = listOf(
                sampleCard(
                    modelId = "qwen",
                    name = "Qwen2.5-1.5B-Instruct",
                    compatibility = LocalModelCompatibilityState.Downloadable,
                    installState = ModelInstallState.NOT_INSTALLED
                )
            )
        )

        composeRule.setContent {
            ModelsTab(
                state = state,
                onRefresh = {},
                onDownload = {},
                onCancelDownload = {},
                onRetryDownload = {},
                onUseModel = {},
                onEjectModel = {},
                onDeleteModel = {},
                onMoveStorage = { _, _ -> },
                onImportLocalModel = {}
            )
        }

        composeRule.onNodeWithText("Model Library").assertIsDisplayed()
        composeRule.onNodeWithText("Qwen2.5-1.5B-Instruct").assertIsDisplayed()
        composeRule.onNodeWithText("Not installed").assertIsDisplayed()
    }

    @Test
    fun showsCompatibilityWarningForUnavailableModel() {
        val state = ModelGalleryScreenState(
            allowlistVersion = "1_0_10",
            allowlistSource = AllowlistSourceType.BUNDLED,
            phase = ModelLibraryPhase.Ready,
            models = listOf(
                sampleCard(
                    modelId = "gemma-e4b",
                    name = "Gemma-3n-E4B-it",
                    compatibility = LocalModelCompatibilityState.NeedsMoreRam(
                        requiredGb = 12,
                        availableGb = 8
                    ),
                    installState = ModelInstallState.NOT_INSTALLED
                )
            )
        )

        composeRule.setContent {
            ModelsTab(
                state = state,
                onRefresh = {},
                onDownload = {},
                onCancelDownload = {},
                onRetryDownload = {},
                onUseModel = {},
                onEjectModel = {},
                onDeleteModel = {},
                onMoveStorage = { _, _ -> },
                onImportLocalModel = {}
            )
        }

        composeRule.onNodeWithText("Not compatible").assertIsDisplayed()
    }

    private fun sampleCard(
        modelId: String,
        name: String,
        compatibility: LocalModelCompatibilityState,
        installState: ModelInstallState
    ): ModelCardUi {
        return ModelCardUi(
            modelId = modelId,
            displayName = name,
            description = "Test description",
            modelFile = "$modelId.litertlm",
            sourceRepo = "litert-community/$modelId",
            sizeInBytes = 1_024,
            minDeviceMemoryInGb = 6,
            taskTypes = listOf("llm_chat"),
            bestForTaskTypes = listOf("llm_chat"),
            llmSupportImage = false,
            llmSupportAudio = false,
            recommendedForChat = true,
            isExperimental = false,
            installState = installState,
            compatibility = compatibility,
            isActive = false,
            isLegacy = false,
            storageLocation = ModelStorageLocation.INTERNAL,
            downloadedBytes = 0,
            sizeOnDiskBytes = 0,
            localPath = null,
            errorMessage = null
        )
    }
}
