package com.fcm.nanochat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fcm.nanochat.ui.NanoChatApp
import com.fcm.nanochat.ui.theme.NanoChatTheme
import com.fcm.nanochat.viewmodel.ChatViewModel
import com.fcm.nanochat.viewmodel.ChatViewModelFactory
import com.fcm.nanochat.viewmodel.SettingsViewModel
import com.fcm.nanochat.viewmodel.SettingsViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as NanoChatApplication).container

        setContent {
            NanoChatTheme {
                val chatViewModel: ChatViewModel = viewModel(
                    factory = ChatViewModelFactory(container.chatRepository)
                )
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = SettingsViewModelFactory(
                        container.preferences,
                        container.chatRepository
                    )
                )

                val chatState by chatViewModel.uiState.collectAsStateWithLifecycle()
                val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

                NanoChatApp(
                    chatState = chatState,
                    settingsState = settingsState,
                    onSendMessage = chatViewModel::sendMessage,
                    onMessageDraftChange = chatViewModel::updateDraft,
                    onSelectSession = chatViewModel::selectSession,
                    onCreateSession = chatViewModel::createSession,
                    onRetryLast = chatViewModel::retryLastMessage,
                    onInferenceModeChange = chatViewModel::setInferenceMode,
                    onRenameSession = chatViewModel::renameSession,
                    onDeleteSession = chatViewModel::deleteSession,
                    onPinSession = chatViewModel::setSessionPinned,
                    onBaseUrlChange = settingsViewModel::updateBaseUrl,
                    onModelNameChange = settingsViewModel::updateModelName,
                    onApiKeyChange = settingsViewModel::updateApiKey,
                    onHuggingFaceTokenChange = settingsViewModel::updateHuggingFaceToken,
                    onTemperatureChange = settingsViewModel::updateTemperature,
                    onTopPChange = settingsViewModel::updateTopP,
                    onContextLengthChange = settingsViewModel::updateContextLength,
                    onSaveSettings = settingsViewModel::save,
                    onClearHistory = settingsViewModel::clearAllHistory,
                    onRefreshStats = settingsViewModel::refreshStats,
                    onDismissNotice = chatViewModel::clearNotice
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NanoChatPreview() {
    NanoChatTheme {
        NanoChatApp()
    }
}
