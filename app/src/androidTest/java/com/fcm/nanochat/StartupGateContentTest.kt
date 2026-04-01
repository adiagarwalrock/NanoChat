package com.fcm.nanochat

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.fcm.nanochat.ui.NanoChatApp
import com.fcm.nanochat.ui.StartupGateContent
import org.junit.Assert.assertTrue
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
                onboardingDownloadPromptSeen = null,
                isModelLibraryLoaded = false,
                hasInstalledModels = false,
                onboardingModel = null,
                onAccepted = {},
                onContinueOnboarding = {},
                onOpenModelManagement = {},
                onDismissOnboarding = {}
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
                onboardingDownloadPromptSeen = false,
                isModelLibraryLoaded = true,
                hasInstalledModels = false,
                onboardingModel = null,
                onAccepted = {},
                onContinueOnboarding = {},
                onOpenModelManagement = {},
                onDismissOnboarding = {}
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
                onboardingDownloadPromptSeen = true,
                isModelLibraryLoaded = true,
                hasInstalledModels = false,
                onboardingModel = null,
                onAccepted = {},
                onContinueOnboarding = {},
                onOpenModelManagement = {},
                onDismissOnboarding = {}
            ) {
                NanoChatApp()
            }
        }

        composeRule.onNodeWithText("Welcome to NanoChat").assertDoesNotExist()
        composeRule.onNodeWithText("How can I help today?").assertIsDisplayed()
    }

    @Test
    fun existingUserWithInstalledModelSkipsOnboarding() {
        var dismissed = false
        composeRule.setContent {
            StartupGateContent(
                gemmaTermsAccepted = true,
                onboardingDownloadPromptSeen = false,
                isModelLibraryLoaded = true,
                hasInstalledModels = true,
                onboardingModel = null,
                onAccepted = {},
                onContinueOnboarding = {},
                onOpenModelManagement = {},
                onDismissOnboarding = { dismissed = true }
            ) {
                NanoChatApp()
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("How can I help today?").assertIsDisplayed()
        assertTrue(dismissed)
    }
}
