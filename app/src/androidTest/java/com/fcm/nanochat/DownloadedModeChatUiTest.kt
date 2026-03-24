package com.fcm.nanochat

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.fcm.nanochat.inference.InferenceMode
import com.fcm.nanochat.model.ChatScreenState
import com.fcm.nanochat.model.SettingsScreenState
import com.fcm.nanochat.ui.ChatTab
import org.junit.Rule
import org.junit.Test

class DownloadedModeChatUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun showsModelGalleryCtaWhenNoLocalModelSelected() {
        composeRule.setContent {
            ChatTab(
                state =
                    ChatScreenState(
                        inferenceMode = InferenceMode.DOWNLOADED,
                        isLocalModelReady = false,
                        localModelStatusMessage =
                            "Choose a local model from the gallery."
                    ),
                settingsState = SettingsScreenState(),
                onOpenSessions = {},
                onSendMessage = {},
                onStopGeneration = {},
                onMessageDraftChange = {},
                onRequestCameraCapture = {},
                onRequestImagePicker = {},
                onRequestAudioPicker = {},
                onRemoveDraftAttachment = {},
                onRetryLast = {},
                onInferenceModeChange = {},
                onOpenModelGallery = {},
                onTemperatureChange = {},
                onTopPChange = {},
                onContextLengthChange = {},
                onThinkingEffortChange = { _: com.fcm.nanochat.data.ThinkingEffort -> },
                onAcceleratorChange = { _: com.fcm.nanochat.data.AcceleratorPreference -> },
                onMessageInfo = {},
                onDeleteMessage = {}
            )
        }

        composeRule
            .onNodeWithText("Select a local model to start on-device chat")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Open model library").assertIsDisplayed()
    }
}
