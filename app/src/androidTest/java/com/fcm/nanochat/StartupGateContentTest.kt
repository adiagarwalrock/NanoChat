package com.fcm.nanochat

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.fcm.nanochat.ui.NanoChatApp
import com.fcm.nanochat.ui.StartupGateContent
import org.junit.Rule
import org.junit.Test

class StartupGateContentTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun unresolvedStartupShowsNoTermsOrChatContent() {
        composeRule.setContent {
            StartupGateContent(
                gemmaTermsAccepted = null,
                onAccepted = {}
            ) {
                NanoChatApp()
            }
        }

        composeRule.onNodeWithText("Welcome to NanoChat").assertDoesNotExist()
        composeRule.onNodeWithText("How can I help today?").assertDoesNotExist()
    }

    @Test
    fun unacceptedStartupShowsTermsScreen() {
        composeRule.setContent {
            StartupGateContent(
                gemmaTermsAccepted = false,
                onAccepted = {}
            ) {
                NanoChatApp()
            }
        }

        composeRule.onNodeWithText("Welcome to NanoChat").assertIsDisplayed()
        composeRule.onNodeWithText("How can I help today?").assertDoesNotExist()
    }

    @Test
    fun acceptedStartupShowsAppContent() {
        composeRule.setContent {
            StartupGateContent(
                gemmaTermsAccepted = true,
                onAccepted = {}
            ) {
                NanoChatApp()
            }
        }

        composeRule.onNodeWithText("Welcome to NanoChat").assertDoesNotExist()
        composeRule.onNodeWithText("How can I help today?").assertIsDisplayed()
    }
}
