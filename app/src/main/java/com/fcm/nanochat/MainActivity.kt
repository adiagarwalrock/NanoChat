package com.fcm.nanochat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fcm.nanochat.notifications.NotificationCoordinator
import com.fcm.nanochat.ui.NanoChatApp
import com.fcm.nanochat.ui.theme.NanoChatTheme
import com.fcm.nanochat.viewmodel.ChatViewModel
import com.fcm.nanochat.viewmodel.ModelManagerViewModel
import com.fcm.nanochat.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var notificationCoordinator: NotificationCoordinator

    private val sessionNavigation = MutableStateFlow<Long?>(null)
    private val modelsNavigation = MutableStateFlow(false)
    private val requestNotificationsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        notificationCoordinator.ensureChannels()
        handleNavigationIntent(intent)
        maybeRequestNotificationPermission()

        setContent {
            NanoChatTheme {
                val chatViewModel: ChatViewModel = viewModel()
                val settingsViewModel: SettingsViewModel = viewModel()
                val modelManagerViewModel: ModelManagerViewModel = viewModel()

                val chatState by chatViewModel.uiState.collectAsStateWithLifecycle()
                val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
                val modelState by modelManagerViewModel.uiState.collectAsStateWithLifecycle()
                val targetSessionId by sessionNavigation.collectAsStateWithLifecycle()
                val navigateToModels by modelsNavigation.collectAsStateWithLifecycle()

                NanoChatApp(
                    chatState = chatState,
                    modelState = modelState,
                    settingsState = settingsState,
                    navigateToChatSessionId = targetSessionId,
                    navigateToModels = navigateToModels,
                    onConsumedNavigation = { sessionNavigation.value = null },
                    onConsumedModelsNavigation = { modelsNavigation.value = false },
                    onSendMessage = chatViewModel::sendMessage,
                    onStopGeneration = chatViewModel::stopGeneration,
                    onMessageDraftChange = chatViewModel::updateDraft,
                    onSelectSession = chatViewModel::selectSession,
                    onCreateSession = chatViewModel::createSession,
                    onRetryLast = chatViewModel::retryLastMessage,
                    onInferenceModeChange = chatViewModel::setInferenceMode,
                    onRenameSession = chatViewModel::renameSession,
                    onDeleteSession = chatViewModel::deleteSession,
                    onPinSession = chatViewModel::setSessionPinned,
                    onDeleteMessage = chatViewModel::deleteMessage,
                    onOpenModelGallery = {},
                    onRefreshAllowlist = modelManagerViewModel::refreshAllowlist,
                    onDownloadModel = modelManagerViewModel::downloadModel,
                    onCancelModelDownload = modelManagerViewModel::cancelDownload,
                    onRetryModelDownload = modelManagerViewModel::retryDownload,
                    onUseModel = modelManagerViewModel::useModel,
                    onEjectModel = modelManagerViewModel::ejectModel,
                    onDeleteModel = modelManagerViewModel::deleteModel,
                    onMoveModelStorage = modelManagerViewModel::moveStorage,
                    onImportLocalModel = modelManagerViewModel::importLocalModel,
                    onDismissModelNotice = modelManagerViewModel::clearNotice,
                    onBaseUrlChange = settingsViewModel::updateBaseUrl,
                    onModelNameChange = settingsViewModel::updateModelName,
                    onApiKeyChange = settingsViewModel::updateApiKey,
                    onHuggingFaceTokenChange = settingsViewModel::updateHuggingFaceToken,
                    onValidateHuggingFaceToken = settingsViewModel::validateHuggingFaceToken,
                    onTemperatureChange = settingsViewModel::updateTemperature,
                    onTopPChange = settingsViewModel::updateTopP,
                    onContextLengthChange = settingsViewModel::updateContextLength,
                    onThinkingEffortChange = settingsViewModel::updateThinkingEffort,
                    onAcceleratorChange = settingsViewModel::updateAcceleratorPreference,
                    onSaveSettings = settingsViewModel::save,
                    onClearHistory = settingsViewModel::clearAllHistory,
                    onRefreshStats = settingsViewModel::refreshStats,
                    onRefreshGeminiStatus = settingsViewModel::refreshGeminiStatus,
                    onDownloadGeminiNano = settingsViewModel::downloadGeminiNano
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNavigationIntent(intent)
    }

    private fun handleNavigationIntent(intent: Intent?) {
        val sessionId = intent?.getLongExtra(NotificationCoordinator.EXTRA_SESSION_ID, -1L) ?: -1L
        if (sessionId > 0) {
            sessionNavigation.value = sessionId
        }
        val openModels = intent?.getBooleanExtra(NotificationCoordinator.EXTRA_OPEN_MODELS, false) ?: false
        if (openModels) {
            modelsNavigation.value = true
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        val granted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
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
