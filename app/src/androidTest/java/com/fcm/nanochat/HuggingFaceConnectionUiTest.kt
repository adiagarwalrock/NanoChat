package com.fcm.nanochat

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.fcm.nanochat.model.HuggingFaceAccountUi
import com.fcm.nanochat.model.SettingsScreenState
import com.fcm.nanochat.ui.HuggingFaceConnectionSettings
import org.junit.Rule
import org.junit.Test

class HuggingFaceConnectionUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun showsValidatedAccountDetails() {
        val state = SettingsScreenState(
            huggingFaceToken = "hf_xxx",
            huggingFaceAccount = HuggingFaceAccountUi(
                isValidating = false,
                isValid = true,
                username = "nano-user",
                fullName = "Nano User",
                email = "nano@example.com",
                tokenRole = "read",
                tokenName = "primary",
                message = "Connected"
            )
        )

        composeRule.setContent {
            HuggingFaceConnectionSettings(
                state = state,
                onHuggingFaceTokenChange = {},
                onValidateHuggingFaceToken = {},
                onSaveSettings = {}
            )
        }

        composeRule.onNodeWithText("Nano User").assertIsDisplayed()
        composeRule.onNodeWithText("@nano-user").assertIsDisplayed()
        composeRule.onNodeWithText("nano@example.com").assertIsDisplayed()
        composeRule.onNodeWithText("Connected").assertIsDisplayed()
    }
}
