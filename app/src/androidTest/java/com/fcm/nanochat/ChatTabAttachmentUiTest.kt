package com.fcm.nanochat

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.fcm.nanochat.inference.InferenceCapabilities
import com.fcm.nanochat.inference.SupportedState
import com.fcm.nanochat.model.ChatMessage
import com.fcm.nanochat.model.ChatRole
import com.fcm.nanochat.model.ChatScreenState
import com.fcm.nanochat.model.ComposerAttachment
import com.fcm.nanochat.model.ComposerAttachmentType
import com.fcm.nanochat.model.MessagePart
import com.fcm.nanochat.model.SettingsScreenState
import com.fcm.nanochat.ui.ChatTab
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChatTabAttachmentUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun attachSheetShowsUnsupportedReasons() {
        var cameraTapped = false
        var imageTapped = false
        var audioTapped = false
        setChat(
            state = ChatScreenState(
                capabilities = InferenceCapabilities(
                    textGeneration = SupportedState.supported(),
                    visionUnderstanding = SupportedState.unsupported("Vision unavailable"),
                    audioTranscription = SupportedState.unsupported("Audio unavailable"),
                    streaming = SupportedState.supported()
                )
            ),
            onRequestCameraCapture = { cameraTapped = true },
            onRequestImagePicker = { imageTapped = true },
            onRequestAudioPicker = { audioTapped = true }
        )

        composeRule.onNodeWithContentDescription("Attach media").performClick()
        composeRule.onNodeWithText("Attach").assertIsDisplayed()
        composeRule.onNodeWithText("Vision unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Audio unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Take photo").performClick()
        composeRule.onNodeWithContentDescription("Attach media").performClick()
        composeRule.onNodeWithText("Choose image").performClick()
        composeRule.onNodeWithContentDescription("Attach media").performClick()
        composeRule.onNodeWithText("Choose audio").performClick()
        composeRule.runOnIdle {
            assertTrue(cameraTapped)
            assertTrue(imageTapped)
            assertTrue(audioTapped)
        }
    }

    @Test
    fun draftAttachmentPreviewShowsAndCanBeRemoved() {
        var removed = false
        setChat(
            state = ChatScreenState(
                draftAttachment = ComposerAttachment(
                    type = ComposerAttachmentType.AUDIO,
                    relativePath = "audio/voice.m4a",
                    absolutePath = "/tmp/voice.m4a",
                    mimeType = "audio/mp4",
                    displayName = "voice.m4a",
                    sizeBytes = 512L,
                    durationMs = 5_000L
                )
            ),
            onRemoveDraftAttachment = { removed = true }
        )

        composeRule.onNodeWithText("voice.m4a").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Remove attachment").performClick()
        composeRule.runOnIdle { assertTrue(removed) }
    }

    @Test
    fun chatBubbleRendersImageAndAudioAttachmentMetadata() {
        setChat(
            state = ChatScreenState(
                messages = listOf(
                    ChatMessage(
                        id = 1L,
                        sessionId = 1L,
                        role = ChatRole.USER,
                        content = "What do you hear?",
                        parts = listOf(
                            MessagePart.ImagePart(
                                relativePath = "images/picture.jpg",
                                absolutePath = "/tmp/picture.jpg",
                                mimeType = "image/jpeg",
                                displayName = "picture.jpg",
                                sizeBytes = 1024L,
                                widthPx = 640,
                                heightPx = 480
                            ),
                            MessagePart.AudioPart(
                                relativePath = "audio/clip.m4a",
                                absolutePath = "/tmp/clip.m4a",
                                mimeType = "audio/mp4",
                                displayName = "clip.m4a",
                                sizeBytes = 2048L,
                                durationMs = 5_000L
                            ),
                            MessagePart.TranscriptPart(
                                sourceMessageId = 1L,
                                label = "Transcript"
                            )
                        )
                    )
                )
            )
        )

        composeRule.onNodeWithContentDescription("Attached image preview").assertExists()
        composeRule.onNodeWithText("clip.m4a").assertIsDisplayed()
        composeRule.onNodeWithText("0:05").assertIsDisplayed()
        composeRule.onNodeWithText("Transcript").assertIsDisplayed()
    }

    private fun setChat(
        state: ChatScreenState,
        onRemoveDraftAttachment: () -> Unit = {},
        onRequestCameraCapture: () -> Unit = {},
        onRequestImagePicker: () -> Unit = {},
        onRequestAudioPicker: () -> Unit = {}
    ) {
        composeRule.setContent {
            ChatTab(
                state = state,
                settingsState = SettingsScreenState(),
                onOpenSessions = {},
                onSendMessage = {},
                onStopGeneration = {},
                onMessageDraftChange = {},
                onRequestCameraCapture = onRequestCameraCapture,
                onRequestImagePicker = onRequestImagePicker,
                onRequestAudioPicker = onRequestAudioPicker,
                onRemoveDraftAttachment = onRemoveDraftAttachment,
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
    }
}
