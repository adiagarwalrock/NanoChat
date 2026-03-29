package com.fcm.nanochat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fcm.nanochat.data.AppPreferences
import com.fcm.nanochat.model.ModelCardUi
import com.fcm.nanochat.model.ModelGalleryScreenState
import com.fcm.nanochat.model.ModelLibraryPhase
import com.fcm.nanochat.models.compatibility.LocalModelCompatibilityState
import com.fcm.nanochat.models.registry.ModelInstallState
import com.fcm.nanochat.notifications.NotificationCoordinator
import com.fcm.nanochat.ui.NanoChatApp
import com.fcm.nanochat.ui.StartupGateContent
import com.fcm.nanochat.ui.theme.NanoChatTheme
import com.fcm.nanochat.util.CrashReporter
import com.fcm.nanochat.util.InferenceCrashMarkerStore
import com.fcm.nanochat.util.InferenceDumpLogger
import com.fcm.nanochat.viewmodel.ChatViewModel
import com.fcm.nanochat.viewmodel.ModelManagerViewModel
import com.fcm.nanochat.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var notificationCoordinator: NotificationCoordinator

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var crashReporter: CrashReporter

    @Inject
    lateinit var inferenceDumpLogger: InferenceDumpLogger

    @Inject
    lateinit var inferenceCrashMarkerStore: InferenceCrashMarkerStore

    private val sessionNavigation = MutableStateFlow<Long?>(null)
    private val modelsNavigation = MutableStateFlow(false)
    private val requestNotificationsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }
    private var gemmaTermsAccepted by mutableStateOf<Boolean?>(null)
    private var onboardingDownloadPromptSeen by mutableStateOf<Boolean?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        runCatching {
            installSplashScreen().setKeepOnScreenCondition {
                gemmaTermsAccepted == null || onboardingDownloadPromptSeen == null
            }
        }.onFailure { error ->
            Log.w(TAG, "SplashScreen setup failed, continuing without keep condition", error)
            crashReporter.logBreadcrumb("startup_splashscreen_setup_failed")
            crashReporter.recordNonFatal(error, "startup_splashscreen_setup_failed")
        }

        super.onCreate(savedInstanceState)
        runCatching { enableEdgeToEdge() }
            .onFailure { error ->
                Log.w(TAG, "Edge-to-edge setup failed, continuing without it", error)
                crashReporter.logBreadcrumb("startup_edge_to_edge_failed")
                crashReporter.recordNonFatal(error, "startup_edge_to_edge_failed")
            }

        inferenceCrashMarkerStore.consumeUncleanMarker()?.let { marker ->
            crashReporter.recordUncleanInferenceTermination(marker)
            inferenceDumpLogger.writeInferenceEvent(
                event = "unclean_inference_termination",
                mode = marker.mode,
                modelId = marker.modelId,
                sessionId = marker.sessionId,
                requestId = marker.requestId,
                stage = marker.stage,
                visibleChars = marker.visibleChars,
                marker = marker
            )
        }

        notificationCoordinator.ensureChannels()
        handleNavigationIntent(intent)
        maybeRequestNotificationPermission()

        lifecycleScope.launch {
            appPreferences.gemmaTermsAccepted.collect { accepted ->
                gemmaTermsAccepted = accepted
            }
        }
        lifecycleScope.launch {
            appPreferences.onboardingDownloadPromptSeen.collect { seen ->
                onboardingDownloadPromptSeen = seen
            }
        }

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
                val isModelLibraryLoaded = modelState.phase != ModelLibraryPhase.Loading
                val hasInstalledModels =
                    modelState.models.any { it.installState == ModelInstallState.INSTALLED }
                val onboardingModel =
                    if (isModelLibraryLoaded && !hasInstalledModels) {
                        onboardingCandidateModel(modelState)
                    } else {
                        null
                    }

                StartupGateContent(
                    gemmaTermsAccepted = gemmaTermsAccepted,
                    onboardingDownloadPromptSeen = onboardingDownloadPromptSeen,
                    isModelLibraryLoaded = isModelLibraryLoaded,
                    hasInstalledModels = hasInstalledModels,
                    onboardingModel = onboardingModel,
                    onAccepted = {
                        gemmaTermsAccepted = true
                        lifecycleScope.launch {
                            appPreferences.updateGemmaTermsAccepted(true)
                        }
                    },
                    onContinueOnboarding = {
                        onboardingDownloadPromptSeen = true
                        lifecycleScope.launch {
                            appPreferences.updateOnboardingDownloadPromptSeen(true)
                        }
                        onboardingModel?.let { model ->
                            modelManagerViewModel.selectAndPreferDownloadedMode(model.modelId)
                            if (model.installState != ModelInstallState.INSTALLED) {
                                modelManagerViewModel.downloadModel(model.modelId)
                                modelManagerViewModel.markPendingAutoActivation(model.modelId)
                            } else {
                                // Model already installed — activate immediately
                                modelManagerViewModel.useModel(model.modelId)
                            }
                        }
                    },
                    onOpenModelManagement = {
                        onboardingDownloadPromptSeen = true
                        modelsNavigation.value = true
                        lifecycleScope.launch {
                            appPreferences.updateOnboardingDownloadPromptSeen(true)
                        }
                    },
                    onDismissOnboarding = {
                        onboardingDownloadPromptSeen = true
                        lifecycleScope.launch {
                            appPreferences.updateOnboardingDownloadPromptSeen(true)
                        }
                    }
                ) {
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

    private companion object {
        const val TAG = "MainActivity"
    }
}

private fun onboardingCandidateModel(state: ModelGalleryScreenState): ModelCardUi? {
    val candidates = state.models.filter { card ->
        if (card.isLegacy || !card.recommendedForChat) {
            return@filter false
        }
        when (card.compatibility) {
            LocalModelCompatibilityState.Ready,
            LocalModelCompatibilityState.Downloadable -> true

            else -> card.installState == ModelInstallState.INSTALLED
        }
    }

    if (candidates.isEmpty()) {
        return state.models
            .filter { !it.isLegacy && it.recommendedForChat }
            .minByOrNull { it.sizeInBytes }
    }

    return candidates.minByOrNull { it.sizeInBytes }
}

@Preview(showBackground = true)
@Composable
private fun NanoChatPreview() {
    NanoChatTheme {
        NanoChatApp()
    }
}
