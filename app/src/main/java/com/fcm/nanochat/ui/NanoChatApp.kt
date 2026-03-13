package com.fcm.nanochat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.fcm.nanochat.R
import com.fcm.nanochat.inference.InferenceMode
import com.fcm.nanochat.model.ChatScreenState
import com.fcm.nanochat.model.ModelGalleryScreenState
import com.fcm.nanochat.model.SettingsScreenState
import com.fcm.nanochat.ui.SettingsSection.AiConfiguration
import com.fcm.nanochat.ui.SettingsSection.Connection
import com.fcm.nanochat.ui.SettingsSection.DataHistory
import com.fcm.nanochat.ui.SettingsSection.Home
import com.fcm.nanochat.ui.SettingsSection.HuggingFaceConnection
import com.fcm.nanochat.ui.SettingsSection.ModelControls
import kotlinx.coroutines.launch

private enum class AppDestination { Chat, Models, Settings }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NanoChatApp(
    chatState: ChatScreenState = ChatScreenState(),
    modelState: ModelGalleryScreenState = ModelGalleryScreenState(),
    settingsState: SettingsScreenState = SettingsScreenState(),
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
    onDeleteModel: (String) -> Unit = {},
    onMoveModelStorage: (String, com.fcm.nanochat.models.registry.ModelStorageLocation) -> Unit = { _, _ -> },
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
    onSaveSettings: () -> Unit = {},
    onClearHistory: () -> Unit = {},
    onRefreshStats: () -> Unit = {},
    onRefreshGeminiStatus: () -> Unit = {},
    onDownloadGeminiNano: () -> Unit = {},
    onDismissNotice: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var destination by rememberSaveable { mutableStateOf(AppDestination.Chat) }
    var renameTargetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var renameDraft by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(chatState.notice) {
        val currentNotice = chatState.notice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(currentNotice)
        onDismissNotice()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor = MaterialTheme.colorScheme.onSurface
            ) {
                SessionsDrawer(
                    state = chatState,
                    onCreateSession = {
                        onCreateSession()
                        scope.launch { drawerState.close() }
                    },
                    onSelectSession = { id ->
                        onSelectSession(id)
                        scope.launch { drawerState.close() }
                    },
                    onPinSession = onPinSession,
                    onDeleteSession = onDeleteSession,
                    onRenameSession = { id, currentTitle ->
                        renameTargetId = id
                        renameDraft = currentTitle
                    },
                    onOpenSettings = {
                        destination = AppDestination.Settings
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = destination == AppDestination.Chat,
                        onClick = { destination = AppDestination.Chat },
                        icon = {
                            Icon(
                                Icons.Default.Chat,
                                contentDescription = stringResource(id = R.string.tab_chat)
                            )
                        },
                        label = { Text(stringResource(id = R.string.tab_chat)) }
                    )
                    NavigationBarItem(
                        selected = destination == AppDestination.Models,
                        onClick = {
                            destination = AppDestination.Models
                            onOpenModelGallery()
                        },
                        icon = {
                            Icon(
                                Icons.Default.Storage,
                                contentDescription = stringResource(id = R.string.tab_models)
                            )
                        },
                        label = { Text(stringResource(id = R.string.tab_models)) }
                    )
                    NavigationBarItem(
                        selected = destination == AppDestination.Settings,
                        onClick = { destination = AppDestination.Settings },
                        icon = {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = stringResource(id = R.string.tab_settings)
                            )
                        },
                        label = { Text(stringResource(id = R.string.tab_settings)) }
                    )
                }
            }
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
                            destination = AppDestination.Models
                            onOpenModelGallery()
                        },
                        onTemperatureChange = onTemperatureChange,
                        onTopPChange = onTopPChange,
                        onContextLengthChange = onContextLengthChange,
                        onMessageInfo = {},
                        onDeleteMessage = { message -> onDeleteMessage(message.id) }
                    )
                }

                AppDestination.Models -> {
                    ModelsTab(
                        state = modelState,
                        modifier = Modifier.padding(innerPadding),
                        onRefresh = onRefreshAllowlist,
                        onDownload = onDownloadModel,
                        onCancelDownload = onCancelModelDownload,
                        onRetryDownload = onRetryModelDownload,
                        onUseModel = onUseModel,
                        onDeleteModel = onDeleteModel,
                        onMoveStorage = onMoveModelStorage,
                        onImportLocalModel = onImportLocalModel,
                        onClearNotice = onDismissModelNotice
                    )
                }

                AppDestination.Settings -> {
                    SettingsPage(
                        state = settingsState,
                        modifier = Modifier.padding(innerPadding),
                        onBack = { destination = AppDestination.Chat },
                        onBaseUrlChange = onBaseUrlChange,
                        onModelNameChange = onModelNameChange,
                        onApiKeyChange = onApiKeyChange,
                        onHuggingFaceTokenChange = onHuggingFaceTokenChange,
                        onValidateHuggingFaceToken = onValidateHuggingFaceToken,
                        onTemperatureChange = onTemperatureChange,
                        onTopPChange = onTopPChange,
                        onContextLengthChange = onContextLengthChange,
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
private fun SettingsPage(
    state: SettingsScreenState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onModelNameChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onHuggingFaceTokenChange: (String) -> Unit,
    onValidateHuggingFaceToken: () -> Unit,
    onTemperatureChange: (Double) -> Unit,
    onTopPChange: (Double) -> Unit,
    onContextLengthChange: (Int) -> Unit,
    onSaveSettings: () -> Unit,
    onClearHistory: () -> Unit,
    onRefreshStats: () -> Unit,
    onRefreshGeminiStatus: () -> Unit,
    onDownloadGeminiNano: () -> Unit
) {
    var section by rememberSaveable { mutableStateOf(Home) }
    val title = when (section) {
        Home -> "Settings"
        AiConfiguration -> "AI configuration"
        Connection -> "Connection"
        ModelControls -> "Model behavior"
        HuggingFaceConnection -> "Hugging Face"
        DataHistory -> "Usage and history"
    }

    val navigateBack: () -> Unit = {
        section = when (section) {
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
    ) { inner ->
        val contentModifier = Modifier
            .padding(inner)

        when (section) {
            Home -> SettingsHome(
                state = state,
                modifier = contentModifier,
                onNavigate = { section = it }
            )

            AiConfiguration -> AiConfigurationSettings(
                state = state,
                modifier = contentModifier,
                onNavigate = { section = it }
            )

            Connection -> ConnectionSettings(
                state = state,
                modifier = contentModifier,
                onBaseUrlChange = onBaseUrlChange,
                onModelNameChange = onModelNameChange,
                onApiKeyChange = onApiKeyChange,
                onRefreshGeminiStatus = onRefreshGeminiStatus,
                onDownloadGeminiNano = onDownloadGeminiNano,
                onSaveSettings = onSaveSettings
            )

            ModelControls -> ModelControlsSettings(
                state = state,
                modifier = contentModifier,
                onTemperatureChange = onTemperatureChange,
                onTopPChange = onTopPChange,
                onContextLengthChange = onContextLengthChange,
                onSaveSettings = onSaveSettings
            )

            HuggingFaceConnection -> HuggingFaceConnectionSettings(
                state = state,
                modifier = contentModifier,
                onHuggingFaceTokenChange = onHuggingFaceTokenChange,
                onValidateHuggingFaceToken = onValidateHuggingFaceToken,
                onSaveSettings = onSaveSettings
            )

            DataHistory -> DataHistorySettings(
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
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
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
