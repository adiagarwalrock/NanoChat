package com.fcm.nanochat.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.fcm.nanochat.inference.InferenceMode
import com.fcm.nanochat.model.ChatMessage
import com.fcm.nanochat.model.ChatScreenState
import com.fcm.nanochat.model.SettingsScreenState
import kotlinx.coroutines.launch

private enum class AppDestination { Chat, Settings }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NanoChatApp(
    chatState: ChatScreenState = ChatScreenState(),
    settingsState: SettingsScreenState = SettingsScreenState(),
    onSendMessage: () -> Unit = {},
    onMessageDraftChange: (String) -> Unit = {},
    onSelectSession: (Long) -> Unit = {},
    onCreateSession: () -> Unit = {},
    onRetryLast: () -> Unit = {},
    onInferenceModeChange: (InferenceMode) -> Unit = {},
    onRenameSession: (Long, String) -> Unit = { _, _ -> },
    onDeleteSession: (Long) -> Unit = {},
    onPinSession: (Long, Boolean) -> Unit = { _, _ -> },
    onBaseUrlChange: (String) -> Unit = {},
    onModelNameChange: (String) -> Unit = {},
    onApiKeyChange: (String) -> Unit = {},
    onHuggingFaceTokenChange: (String) -> Unit = {},
    onTemperatureChange: (Double) -> Unit = {},
    onTopPChange: (Double) -> Unit = {},
    onContextLengthChange: (Int) -> Unit = {},
    onSaveSettings: () -> Unit = {},
    onClearHistory: () -> Unit = {},
    onRefreshStats: () -> Unit = {},
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
                        onMessageDraftChange = onMessageDraftChange,
                        onCreateSession = onCreateSession,
                        onRetryLast = onRetryLast,
                        onInferenceModeChange = onInferenceModeChange,
                        onTemperatureChange = onTemperatureChange,
                        onTopPChange = onTopPChange,
                        onContextLengthChange = onContextLengthChange,
                        onMessageInfo = { message ->
                            scope.launch { snackbarHostState.showSnackbar(formatMessageInfo(message)) }
                        }
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
                        onTemperatureChange = onTemperatureChange,
                        onTopPChange = onTopPChange,
                        onContextLengthChange = onContextLengthChange,
                        onSaveSettings = onSaveSettings,
                        onClearHistory = onClearHistory,
                        onRefreshStats = onRefreshStats
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
    onTemperatureChange: (Double) -> Unit,
    onTopPChange: (Double) -> Unit,
    onContextLengthChange: (Int) -> Unit,
    onSaveSettings: () -> Unit,
    onClearHistory: () -> Unit,
    onRefreshStats: () -> Unit
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    TextButton(
                        onClick = onBack
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Text("Back", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            )
        },
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
    ) { inner ->
        SettingsTab(
            state = state,
            modifier = Modifier
                .padding(inner)
                .fillMaxSize(),
            onBaseUrlChange = onBaseUrlChange,
            onModelNameChange = onModelNameChange,
            onApiKeyChange = onApiKeyChange,
            onHuggingFaceTokenChange = onHuggingFaceTokenChange,
            onTemperatureChange = onTemperatureChange,
            onTopPChange = onTopPChange,
            onContextLengthChange = onContextLengthChange,
            onSaveSettings = onSaveSettings,
            onClearHistory = onClearHistory,
            onRefreshStats = onRefreshStats
        )
    }
}

private fun formatMessageInfo(message: ChatMessage): String {
    val modeLabel = if (message.inferenceMode == InferenceMode.REMOTE) "Remote" else "Nano"
    return buildString {
        append("Model: ${message.modelName.ifBlank { "(unspecified)" }}\n")
        append("Backend: $modeLabel\n")
        append("Temp: ${"%.2f".format(message.temperature)}  TopP: ${"%.2f".format(message.topP)}  Context: ${message.contextLength}")
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
