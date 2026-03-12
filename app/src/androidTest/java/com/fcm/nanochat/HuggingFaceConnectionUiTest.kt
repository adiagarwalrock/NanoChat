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
                name = "Nano User",
                email = "nano@example.com",
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

        composeRule.onNodeWithText("Account details").assertIsDisplayed()
        composeRule.onNodeWithText("Name: Nano User").assertIsDisplayed()
        composeRule.onNodeWithText("Email: nano@example.com").assertIsDisplayed()
    }
}
