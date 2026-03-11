package com.fcm.nanochat

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.fcm.nanochat.ui.NanoChatApp
import org.junit.Rule
import org.junit.Test

class NanoChatAppTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun modelsTabShowsDeferredPlaceholder() {
        composeRule.setContent {
            NanoChatApp()
        }

        composeRule.onNodeWithText("Models").performClick()
        composeRule.onNodeWithText("Downloaded model management is deferred to milestone 2. This tab will host the curated catalog, download progress, and storage controls.")
            .assertIsDisplayed()
    }
}
