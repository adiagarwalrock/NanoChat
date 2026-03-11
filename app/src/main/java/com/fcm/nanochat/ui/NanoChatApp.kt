package com.fcm.nanochat.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.fcm.nanochat.inference.InferenceMode
import com.fcm.nanochat.model.ChatScreenState
import com.fcm.nanochat.model.SettingsScreenState
import kotlinx.coroutines.launch

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
    onSaveSettings: () -> Unit = {},
    onDismissNotice: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showSettings by rememberSaveable { mutableStateOf(false) }
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
            ChatTab(
                state = chatState,
                modifier = Modifier.padding(innerPadding),
                onOpenSessions = { scope.launch { drawerState.open() } },
                onOpenSettings = { showSettings = true },
                onSendMessage = onSendMessage,
                onMessageDraftChange = onMessageDraftChange,
                onCreateSession = onCreateSession,
                onRetryLast = onRetryLast,
                onInferenceModeChange = onInferenceModeChange
            )
        }

        if (showSettings) {
            ModalBottomSheet(
                onDismissRequest = { showSettings = false },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                SettingsTab(
                    state = settingsState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.94f),
                    onBaseUrlChange = onBaseUrlChange,
                    onModelNameChange = onModelNameChange,
                    onApiKeyChange = onApiKeyChange,
                    onHuggingFaceTokenChange = onHuggingFaceTokenChange,
                    onSaveSettings = onSaveSettings
                )
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