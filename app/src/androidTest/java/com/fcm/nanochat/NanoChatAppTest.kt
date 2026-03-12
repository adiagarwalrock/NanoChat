package com.fcm.nanochat

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.fcm.nanochat.ui.NanoChatApp
import org.junit.Rule
import org.junit.Test

class NanoChatAppTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun chatScreenShowsEmptyStateByDefault() {
        composeRule.setContent {
            NanoChatApp()
        }

        composeRule.onNodeWithText("How can I help today?").assertIsDisplayed()
    }
}
