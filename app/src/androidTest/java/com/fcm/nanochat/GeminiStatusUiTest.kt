package com.fcm.nanochat

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.fcm.nanochat.model.GeminiNanoStatusUi
import com.fcm.nanochat.model.SettingsScreenState
import com.fcm.nanochat.ui.ConnectionSettings
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
            ConnectionSettings(
                state = state,
                onBaseUrlChange = {},
                onModelNameChange = {},
                onApiKeyChange = {},
                onRefreshGeminiStatus = {},
                onDownloadGeminiNano = {},
                onSaveSettings = {}
            )
        }

        composeRule.onNodeWithText("On-device model").assertIsDisplayed()
        composeRule.onNodeWithText("Downloaded").assertIsDisplayed()
        composeRule.onAllNodesWithText("Download Gemini Nano").assertCountEquals(0)
    }

    @Test
    fun showsDownloadButtonWhenDownloadable() {
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
            ConnectionSettings(
                state = state,
                onBaseUrlChange = {},
                onModelNameChange = {},
                onApiKeyChange = {},
                onRefreshGeminiStatus = {},
                onDownloadGeminiNano = {},
                onSaveSettings = {}
            )
        }

        composeRule.onNodeWithText("Download Gemini Nano").assertIsDisplayed()
    }
}
