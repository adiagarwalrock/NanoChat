package com.fcm.nanochat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fcm.nanochat.R
import com.fcm.nanochat.inference.InferenceMode
import com.fcm.nanochat.model.ChatMessage
import com.fcm.nanochat.model.ChatRole
import com.fcm.nanochat.model.ChatScreenState
import com.fcm.nanochat.model.ModelGalleryScreenState
import com.fcm.nanochat.model.SettingsScreenState
import com.fcm.nanochat.ui.SettingsSection.AiConfiguration
import com.fcm.nanochat.ui.SettingsSection.Connection
import com.fcm.nanochat.ui.SettingsSection.DataHistory
import com.fcm.nanochat.ui.SettingsSection.Home
import com.fcm.nanochat.ui.SettingsSection.HuggingFaceConnection
import com.fcm.nanochat.ui.SettingsSection.ModelControls
import com.fcm.nanochat.ui.theme.NanoChatTheme
import kotlinx.coroutines.launch

private enum class AppDestination {
    Chat,
    Models,
    Settings
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NanoChatApp(
    chatState: ChatScreenState = ChatScreenState(),
    modelState: ModelGalleryScreenState = ModelGalleryScreenState(),
    settingsState: SettingsScreenState = SettingsScreenState(),
    navigateToChatSessionId: Long? = null,
    navigateToModels: Boolean = false,
    onConsumedNavigation: () -> Unit = {},
    onConsumedModelsNavigation: () -> Unit = {},
    onSendMessage: () -> Unit = {},
    onStopGeneration: () -> Unit = {},
    onMessageDraftChange: (String) -> Unit = {},
    onSelectSession: (Long) -> Unit = {},
    onCreateSession: () -> Unit = {},
    onRetryLast: () -> Unit = {},
    onInferenceModeChange: (InferenceMode) -> Unit = {},
    onRenameSession: (Long, String) -> Unit = { _, _ -> },
    onDeleteSession: (Long) -> Unit = {},
    onPinSession: (Long, Boolean) -> Unit = { _, _ -> },
    onDeleteMessage: (Long) -> Unit = {},
    onOpenModelGallery: () -> Unit = {},
    onRefreshAllowlist: () -> Unit = {},
    onDownloadModel: (String) -> Unit = {},
    onCancelModelDownload: (String) -> Unit = {},
    onRetryModelDownload: (String) -> Unit = {},
    onUseModel: (String) -> Unit = {},
    onEjectModel: (String) -> Unit = {},
    onDeleteModel: (String) -> Unit = {},
    onMoveModelStorage:
        (String, com.fcm.nanochat.models.registry.ModelStorageLocation) -> Unit =
        { _, _ ->
        },
    onImportLocalModel: () -> Unit = {},
    onDismissModelNotice: () -> Unit = {},
    onBaseUrlChange: (String) -> Unit = {},
    onModelNameChange: (String) -> Unit = {},
    onApiKeyChange: (String) -> Unit = {},
    onHuggingFaceTokenChange: (String) -> Unit = {},
    onValidateHuggingFaceToken: () -> Unit = {},
    onTemperatureChange: (Double) -> Unit = {},
    onTopPChange: (Double) -> Unit = {},
    onContextLengthChange: (Int) -> Unit = {},
    onThinkingEffortChange: (com.fcm.nanochat.data.ThinkingEffort) -> Unit = {},
    onAcceleratorChange: (com.fcm.nanochat.data.AcceleratorPreference) -> Unit = {},
    onSaveSettings: () -> Unit = {},
    onClearHistory: () -> Unit = {},
    onRefreshStats: () -> Unit = {},
    onRefreshGeminiStatus: () -> Unit = {},
    onDownloadGeminiNano: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var destination by rememberSaveable { mutableStateOf(AppDestination.Chat) }
    var modelsBackDestination by rememberSaveable { mutableStateOf(AppDestination.Chat) }
    var settingsStartSection by rememberSaveable { mutableStateOf(Home) }
    var renameTargetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var renameDraft by rememberSaveable { mutableStateOf("") }

    val windowInfo = LocalWindowInfo.current
    val screenWidthDp = with(LocalDensity.current) { windowInfo.containerSize.width.toDp() }
    val isExpanded = screenWidthDp >= 700.dp

    LaunchedEffect(modelState.notice) {
        val currentNotice = modelState.notice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(currentNotice)
        onDismissModelNotice()
    }

    LaunchedEffect(navigateToChatSessionId) {
        val target = navigateToChatSessionId ?: return@LaunchedEffect
        destination = AppDestination.Chat
        onSelectSession(target)
        onConsumedNavigation()
    }

    LaunchedEffect(navigateToModels) {
        if (!navigateToModels) return@LaunchedEffect
        modelsBackDestination = AppDestination.Chat
        destination = AppDestination.Models
        onConsumedModelsNavigation()
    }

    val drawerContentComposable: @Composable () -> Unit = {
        SessionsDrawer(
            state = chatState,
            modifier = if (isExpanded) Modifier
                .width(300.dp)
                .statusBarsPadding() else Modifier,
            onCreateSession = {
                onCreateSession()
                if (!isExpanded) scope.launch { drawerState.close() }
            },
            onSelectSession = { id ->
                onSelectSession(id)
                if (!isExpanded) scope.launch { drawerState.close() }
            },
            onPinSession = onPinSession,
            onDeleteSession = onDeleteSession,
            onRenameSession = { id, currentTitle ->
                renameTargetId = id
                renameDraft = currentTitle
            },
            onOpenModels = {
                modelsBackDestination = AppDestination.Chat
                destination = AppDestination.Models
                onOpenModelGallery()
                if (!isExpanded) scope.launch { drawerState.close() }
            },
            onOpenSettings = {
                settingsStartSection = Home
                destination = AppDestination.Settings
                if (!isExpanded) scope.launch { drawerState.close() }
            }
        )
    }

    val appContent: @Composable () -> Unit = {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            when (destination) {
                AppDestination.Chat -> {
                    ChatTab(
                        state = chatState,
                        settingsState = settingsState,
                        modifier = Modifier.padding(innerPadding),
                        onOpenSessions = { scope.launch { drawerState.open() } },
                        onSendMessage = onSendMessage,
                        onStopGeneration = onStopGeneration,
                        onMessageDraftChange = onMessageDraftChange,
                        onCreateSession = onCreateSession,
                        onRetryLast = onRetryLast,
                        onInferenceModeChange = onInferenceModeChange,
                        onOpenModelGallery = {
                            modelsBackDestination = AppDestination.Chat
                            destination = AppDestination.Models
                            onOpenModelGallery()
                        },
                        onTemperatureChange = onTemperatureChange,
                        onTopPChange = onTopPChange,
                        onContextLengthChange = onContextLengthChange,
                        onThinkingEffortChange = onThinkingEffortChange,
                        onAcceleratorChange = onAcceleratorChange,
                        onMessageInfo = {},
                        onDeleteMessage = { message -> onDeleteMessage(message.id) }
                    )
                }
                AppDestination.Models -> {
                    ModelsPage(
                        state = modelState,
                        modifier = Modifier.padding(innerPadding),
                        onBack = {
                            destination = modelsBackDestination
                            if (modelsBackDestination != AppDestination.Settings) {
                                settingsStartSection = Home
                            }
                        },
                        onOpenHuggingFaceSettings = {
                            settingsStartSection = HuggingFaceConnection
                            destination = AppDestination.Settings
                        },
                        onRefreshAllowlist = onRefreshAllowlist,
                        onDownloadModel = onDownloadModel,
                        onCancelModelDownload = onCancelModelDownload,
                        onRetryModelDownload = onRetryModelDownload,
                        onUseModel = onUseModel,
                        onEjectModel = onEjectModel,
                        onDeleteModel = onDeleteModel,
                        onMoveModelStorage = onMoveModelStorage,
                        onImportLocalModel = onImportLocalModel
                    )
                }
                AppDestination.Settings -> {
                    SettingsPage(
                        state = settingsState,
                        modelState = modelState,
                        modifier = Modifier.padding(innerPadding),
                        startSection = settingsStartSection,
                        onBack = { destination = AppDestination.Chat },
                        onOpenModelLibrary = {
                            modelsBackDestination = AppDestination.Settings
                            settingsStartSection = Home
                            destination = AppDestination.Models
                            onOpenModelGallery()
                        },
                        onBaseUrlChange = onBaseUrlChange,
                        onModelNameChange = onModelNameChange,
                        onApiKeyChange = onApiKeyChange,
                        onHuggingFaceTokenChange = onHuggingFaceTokenChange,
                        onValidateHuggingFaceToken = onValidateHuggingFaceToken,
                        onTemperatureChange = onTemperatureChange,
                        onTopPChange = onTopPChange,
                        onContextLengthChange = onContextLengthChange,
                        onThinkingEffortChange = onThinkingEffortChange,
                        onAcceleratorChange = onAcceleratorChange,
                        onSaveSettings = onSaveSettings,
                        onClearHistory = onClearHistory,
                        onRefreshStats = onRefreshStats,
                        onRefreshGeminiStatus = onRefreshGeminiStatus,
                        onDownloadGeminiNano = onDownloadGeminiNano
                    )
                }
            }
        }
    }

    if (isExpanded) {
        PermanentNavigationDrawer(
            drawerContent = {
                PermanentDrawerSheet(
                    drawerContainerColor = MaterialTheme.colorScheme.surface,
                    drawerContentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(300.dp)
                ) {
                    drawerContentComposable()
                }
            }
        ) {
            appContent()
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = MaterialTheme.colorScheme.surface,
                    drawerContentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    drawerContentComposable()
                }
            }
        ) {
            appContent()
        }
    }

    RenameSessionDialog(
        sessionId = renameTargetId,
        draft = renameDraft,
        onDraftChange = { renameDraft = it },
        onConfirm = { id, title ->
            onRenameSession(id, title)
            renameTargetId = null
        },
        onDismiss = { renameTargetId = null }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelsPage(
    state: ModelGalleryScreenState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenHuggingFaceSettings: () -> Unit,
    onRefreshAllowlist: () -> Unit,
    onDownloadModel: (String) -> Unit,
    onCancelModelDownload: (String) -> Unit,
    onRetryModelDownload: (String) -> Unit,
    onUseModel: (String) -> Unit,
    onEjectModel: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
    onMoveModelStorage: (String, com.fcm.nanochat.models.registry.ModelStorageLocation) -> Unit,
    onImportLocalModel: () -> Unit
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.local_models),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
    ) { inner ->
        ModelsTab(
            state = state,
            modifier = Modifier.padding(inner),
            onRefresh = onRefreshAllowlist,
            onDownload = onDownloadModel,
            onCancelDownload = onCancelModelDownload,
            onRetryDownload = onRetryModelDownload,
            onUseModel = onUseModel,
            onEjectModel = onEjectModel,
            onDeleteModel = onDeleteModel,
            onMoveStorage = onMoveModelStorage,
            onImportLocalModel = onImportLocalModel,
            onOpenHuggingFaceSettings = onOpenHuggingFaceSettings
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPage(
    state: SettingsScreenState,
    modelState: ModelGalleryScreenState,
    modifier: Modifier = Modifier,
    startSection: SettingsSection,
    onBack: () -> Unit,
    onOpenModelLibrary: () -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onModelNameChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onHuggingFaceTokenChange: (String) -> Unit,
    onValidateHuggingFaceToken: () -> Unit,
    onTemperatureChange: (Double) -> Unit,
    onTopPChange: (Double) -> Unit,
    onContextLengthChange: (Int) -> Unit,
    onThinkingEffortChange: (com.fcm.nanochat.data.ThinkingEffort) -> Unit,
    onAcceleratorChange: (com.fcm.nanochat.data.AcceleratorPreference) -> Unit,
    onSaveSettings: () -> Unit,
    onClearHistory: () -> Unit,
    onRefreshStats: () -> Unit,
    onRefreshGeminiStatus: () -> Unit,
    onDownloadGeminiNano: () -> Unit
) {
    var section by rememberSaveable(startSection) { mutableStateOf(startSection) }
    val title =
        when (section) {
            Home -> "Settings"
            AiConfiguration -> "Remote AI configuration"
            Connection -> "Connection"
            ModelControls -> "Model behavior"
            HuggingFaceConnection -> "Hugging Face"
            DataHistory -> "Usage and history"
        }

    val navigateBack: () -> Unit = {
        section =
            when (section) {
                Home -> {
                    onBack()
                    Home
                }

                AiConfiguration, HuggingFaceConnection, DataHistory -> Home
                Connection, ModelControls -> AiConfiguration
            }
    }

    BackHandler(onBack = navigateBack)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
    ) { inner ->
        val contentModifier = Modifier.padding(inner)

        when (section) {
            Home ->
                SettingsHome(
                    state = state,
                    modelState = modelState,
                    modifier = contentModifier,
                    onNavigate = { section = it },
                    onOpenModelLibrary = onOpenModelLibrary
                )

            AiConfiguration ->
                AiConfigurationSettings(
                    state = state,
                    modifier = contentModifier,
                    onNavigate = { section = it }
                )

            Connection ->
                ConnectionSettings(
                    state = state,
                    modifier = contentModifier,
                    onBaseUrlChange = onBaseUrlChange,
                    onModelNameChange = onModelNameChange,
                    onApiKeyChange = onApiKeyChange,
                    onRefreshGeminiStatus = onRefreshGeminiStatus,
                    onDownloadGeminiNano = onDownloadGeminiNano,
                    onSaveSettings = onSaveSettings
                )

            ModelControls ->
                ModelControlsSettings(
                    state = state,
                    modifier = contentModifier,
                    onTemperatureChange = onTemperatureChange,
                    onTopPChange = onTopPChange,
                    onContextLengthChange = onContextLengthChange,
                    onThinkingEffortChange = onThinkingEffortChange,
                    onAcceleratorChange = onAcceleratorChange,
                    onSaveSettings = onSaveSettings
                )

            HuggingFaceConnection ->
                HuggingFaceConnectionSettings(
                    state = state,
                    modifier = contentModifier,
                    onHuggingFaceTokenChange = onHuggingFaceTokenChange,
                    onValidateHuggingFaceToken = onValidateHuggingFaceToken,
                    onSaveSettings = onSaveSettings
                )

            DataHistory ->
                DataHistorySettings(
                    state = state,
                    modifier = contentModifier,
                    onRefreshStats = onRefreshStats,
                    onClearHistory = onClearHistory
                )
        }
    }
}

@Composable
private fun RenameSessionDialog(
    sessionId: Long?,
    draft: String,
    onDraftChange: (String) -> Unit,
    onConfirm: (Long, String) -> Unit,
    onDismiss: () -> Unit
) {
    val currentSessionId = sessionId ?: return

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                enabled = draft.trim().isNotEmpty(),
                onClick = { onConfirm(currentSessionId, draft) }
            ) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Rename chat") },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Chat title") }
            )
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun NanoChatAppChatPreview() {
    NanoChatTheme {
        NanoChatApp(
            chatState =
                ChatScreenState(
                    messages =
                        listOf(
                            ChatMessage(
                                1,
                                1,
                                ChatRole.USER,
                                "Show me the app!"
                            ),
                            ChatMessage(
                                2,
                                1,
                                ChatRole.ASSISTANT,
                                "Here is a preview of the main chat interface."
                            )
                        )
                )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NanoChatAppSettingsPreview() {
    NanoChatTheme { NanoChatApp(settingsState = SettingsScreenState(modelName = "Gemini Nano")) }
}
