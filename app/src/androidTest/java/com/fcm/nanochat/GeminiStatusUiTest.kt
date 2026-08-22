package com.fcm.nanochat

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.fcm.nanochat.model.GeminiNanoStatusUi
import com.fcm.nanochat.model.ModelGalleryScreenState
import com.fcm.nanochat.model.SettingsScreenState
import com.fcm.nanochat.ui.SettingsHome
import org.junit.Rule
import org.junit.Test

class GeminiStatusUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun showsDownloadedStateWithoutDownloadButton() {
        val state = SettingsScreenState(
            baseUrl = "https://example.com",
            modelName = "gpt",
            geminiStatus = GeminiNanoStatusUi(
                supported = true,
                downloaded = true,
                downloading = false,
                downloadable = false,
                bytesDownloaded = null,
                bytesToDownload = null,
                lastKnownModelSizeBytes = 1_500_000,
                message = null
            )
        )

        composeRule.setContent {
            SettingsHome(
                state = state,
                modelState = ModelGalleryScreenState(),
                onNavigate = {},
                onOpenModelLibrary = {}
            )
        }

        composeRule.onNodeWithText("On-device AI").assertIsDisplayed()
        composeRule.onNodeWithText("Enabled").assertIsDisplayed()
        composeRule.onAllNodesWithText("Download Gemini Nano").assertCountEquals(0)
    }

    @Test
    fun showsAvailableStateWhenDownloadable() {
        val state = SettingsScreenState(
            baseUrl = "https://example.com",
            modelName = "gpt",
            geminiStatus = GeminiNanoStatusUi(
                supported = true,
                downloaded = false,
                downloading = false,
                downloadable = true,
                bytesDownloaded = null,
                bytesToDownload = 25_000_000,
                lastKnownModelSizeBytes = 25_000_000,
                message = null
            )
        )

        composeRule.setContent {
            SettingsHome(
                state = state,
                modelState = ModelGalleryScreenState(),
                onNavigate = {},
                onOpenModelLibrary = {}
            )
        }

        composeRule.onNodeWithText("On-device AI").assertIsDisplayed()
        composeRule.onNodeWithText("Available").assertIsDisplayed()
    }
}
