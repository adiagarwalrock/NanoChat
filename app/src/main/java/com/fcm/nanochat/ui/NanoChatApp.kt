package com.fcm.nanochat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fcm.nanochat.inference.InferenceMode
import com.fcm.nanochat.model.ChatMessage
import com.fcm.nanochat.model.ChatRole
import com.fcm.nanochat.model.ChatScreenState
import com.fcm.nanochat.model.ChatSession
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
        val notice = chatState.notice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(notice)
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

    if (renameTargetId != null) {
        AlertDialog(
            onDismissRequest = { renameTargetId = null },
            confirmButton = {
                Button(
                    enabled = renameDraft.trim().isNotEmpty(),
                    onClick = {
                        onRenameSession(renameTargetId!!, renameDraft)
                        renameTargetId = null
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetId = null }) {
                    Text("Cancel")
                }
            },
            title = { Text("Rename chat") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Chat title") }
                )
            }
        )
    }
}

@Composable
private fun ChatTab(
    state: ChatScreenState,
    modifier: Modifier = Modifier,
    onOpenSessions: () -> Unit,
    onOpenSettings: () -> Unit,
    onSendMessage: () -> Unit,
    onMessageDraftChange: (String) -> Unit,
    onCreateSession: () -> Unit,
    onRetryLast: () -> Unit,
    onInferenceModeChange: (InferenceMode) -> Unit
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        val compact = maxWidth < 700.dp

        if (compact) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp)
            ) {
                ChatTopBar(
                    modelLabel = if (state.inferenceMode == InferenceMode.REMOTE) "Remote" else "Gemini Nano",
                    onOpenSessions = onOpenSessions,
                    onOpenSettings = onOpenSettings
                )
                Spacer(modifier = Modifier.height(12.dp))
                InferenceModeRow(
                    selectedMode = state.inferenceMode,
                    onInferenceModeChange = onInferenceModeChange
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (state.messages.isEmpty()) {
                    EmptyConversation(modifier = Modifier.weight(1f))
                } else {
                    MessageList(
                        messages = state.messages,
                        modifier = Modifier.weight(1f)
                    )
                }
                Composer(
                    draft = state.draft,
                    isSending = state.isSending,
                    onDraftChange = onMessageDraftChange,
                    onSend = onSendMessage,
                    onRetry = onRetryLast,
                    onCreateSession = onCreateSession,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .imePadding()
                )
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                SessionsRail(
                    state = state,
                    modifier = Modifier
                        .width(260.dp)
                        .fillMaxSize(),
                    onCreateSession = onCreateSession,
                    onSelectSession = onOpenSessions
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    ChatTopBar(
                        modelLabel = if (state.inferenceMode == InferenceMode.REMOTE) "Remote" else "Gemini Nano",
                        onOpenSessions = onOpenSessions,
                        onOpenSettings = onOpenSettings
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    InferenceModeRow(
                        selectedMode = state.inferenceMode,
                        onInferenceModeChange = onInferenceModeChange
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    if (state.messages.isEmpty()) {
                        EmptyConversation(modifier = Modifier.weight(1f))
                    } else {
                        MessageList(
                            messages = state.messages,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Composer(
                        draft = state.draft,
                        isSending = state.isSending,
                        onDraftChange = onMessageDraftChange,
                        onSend = onSendMessage,
                        onRetry = onRetryLast,
                        onCreateSession = onCreateSession,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .imePadding()
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatTopBar(
    modelLabel: String,
    onOpenSessions: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onOpenSessions) {
            Icon(Icons.Default.Menu, contentDescription = "Sessions")
        }
        Text(
            text = modelLabel,
            style = MaterialTheme.typography.titleLarge,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold
        )
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
        }
    }
}

@Composable
private fun EmptyConversation(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "How can I help you today?",
                style = MaterialTheme.typography.headlineLarge,
                fontFamily = FontFamily.Serif,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

@Composable
private fun SessionsDrawer(
    state: ChatScreenState,
    onCreateSession: () -> Unit,
    onSelectSession: (Long) -> Unit,
    onPinSession: (Long, Boolean) -> Unit,
    onDeleteSession: (Long) -> Unit,
    onRenameSession: (Long, String) -> Unit
) {
    val pinned = state.sessions.filter { it.isPinned }
    val recents = state.sessions.filterNot { it.isPinned }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp)
    ) {
        Text(
            text = "NanoChat",
            style = MaterialTheme.typography.headlineLarge,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onCreateSession) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("New chat")
        }

        if (pinned.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Pinned",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            pinned.forEach { session ->
                SessionRow(
                    session = session,
                    isSelected = state.selectedSessionId == session.id,
                    onSelectSession = onSelectSession,
                    onPinSession = onPinSession,
                    onDeleteSession = onDeleteSession,
                    onRenameSession = onRenameSession
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Recents",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (recents.isEmpty()) {
            Text(
                text = "No recent sessions.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recents, key = { it.id }) { session ->
                    SessionRow(
                        session = session,
                        isSelected = state.selectedSessionId == session.id,
                        onSelectSession = onSelectSession,
                        onPinSession = onPinSession,
                        onDeleteSession = onDeleteSession,
                        onRenameSession = onRenameSession
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: ChatSession,
    isSelected: Boolean,
    onSelectSession: (Long) -> Unit,
    onPinSession: (Long, Boolean) -> Unit,
    onDeleteSession: (Long) -> Unit,
    onRenameSession: (Long, String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest
                else MaterialTheme.colorScheme.surface
            )
            .clickable { onSelectSession(session.id) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = session.title,
            maxLines = 1,
            modifier = Modifier.weight(1f),
            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onPinSession(session.id, !session.isPinned) }) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = "Pin",
                    tint = if (session.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alpha(if (session.isPinned) 1f else 0.6f)
                )
            }
            IconButton(onClick = { onRenameSession(session.id, session.title) }) {
                Icon(Icons.Default.Edit, contentDescription = "Rename")
            }
            IconButton(onClick = { onDeleteSession(session.id) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}

@Composable
private fun SessionsRail(
    state: ChatScreenState,
    modifier: Modifier = Modifier,
    onCreateSession: () -> Unit,
    onSelectSession: () -> Unit
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Chats", fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onCreateSession) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onSelectSession, modifier = Modifier.fillMaxWidth()) {
            Text("Open Sessions")
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "${state.sessions.size} sessions",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InferenceModeRow(
    selectedMode: InferenceMode,
    onInferenceModeChange: (InferenceMode) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterChip(
            selected = selectedMode == InferenceMode.AICORE,
            onClick = { onInferenceModeChange(InferenceMode.AICORE) },
            label = { Text("Nano") }
        )
        FilterChip(
            selected = selectedMode == InferenceMode.REMOTE,
            onClick = { onInferenceModeChange(InferenceMode.REMOTE) },
            label = { Text("Remote") }
        )
    }
}

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            val isUser = message.role == ChatRole.USER
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (isUser) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                        .padding(14.dp)
                        .fillMaxWidth(0.88f)
                ) {
                    Text(
                        text = if (isUser) "You" else "Assistant",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (message.content.isBlank() && message.isStreaming) "Thinking..." else message.content,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun Composer(
    draft: String,
    isSending: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
    onCreateSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Chat with NanoChat...") },
                minLines = 2,
                maxLines = 5,
                shape = RoundedCornerShape(18.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCreateSession) {
                        Icon(Icons.Default.Add, contentDescription = "New chat")
                    }
                    TextButton(onClick = onRetry, enabled = !isSending) {
                        Text("Retry")
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable(enabled = !isSending && draft.isNotBlank(), onClick = onSend)
                            .alpha(if (!isSending && draft.isNotBlank()) 1f else 0.45f)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Send",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun SettingsTab(
    state: SettingsScreenState,
    modifier: Modifier = Modifier,
    onBaseUrlChange: (String) -> Unit,
    onModelNameChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onHuggingFaceTokenChange: (String) -> Unit,
    onSaveSettings: () -> Unit
) {
    val missingRemoteFields = buildList {
        if (state.baseUrl.isBlank()) add("Base URL")
        if (state.modelName.isBlank()) add("Model name")
        if (state.apiKey.isBlank()) add("API key")
    }
    val providerStatus = inferProviderStatus(state.baseUrl)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Provider Status", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(providerStatus)
                }
            }
        }
        item {
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = onBaseUrlChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                label = { Text("Base URL") },
                supportingText = { Text("Use base path; app auto-appends /chat/completions") }
            )
        }
        item {
            OutlinedTextField(
                value = state.modelName,
                onValueChange = onModelNameChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Model name") }
            )
        }
        item {
            OutlinedTextField(
                value = state.apiKey,
                onValueChange = onApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = LastFiveVisibleApiKeyTransformation(),
                supportingText = { Text("Only the last 5 characters are visible") }
            )
        }
        item {
            OutlinedTextField(
                value = state.huggingFaceToken,
                onValueChange = onHuggingFaceTokenChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("HF token") }
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = state.saveNotice.orEmpty(), color = MaterialTheme.colorScheme.primary)
                Button(onClick = onSaveSettings) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save")
                }
            }
        }
        item {
            StatusCard(
                title = "Backend readiness",
                body = if (missingRemoteFields.isEmpty()) {
                    "Remote is configured. Nano still requires Gemini Nano enabled in Developer Options on a supported device."
                } else {
                    "Remote is missing: ${missingRemoteFields.joinToString()}. Nano requires Gemini Nano enabled in Developer Options on a supported device."
                }
            )
        }
    }
}

private fun inferProviderStatus(baseUrl: String): String {
    val normalized = baseUrl.lowercase()
    return when {
        normalized.isBlank() -> "No endpoint configured"
        "generativelanguage.googleapis.com" in normalized -> "Using Gemini via GenAI"
        "aiplatform.googleapis.com" in normalized || "vertexai" in normalized -> "Using Gemini via VertexAI"
        "api.openai.com" in normalized -> "Using OpenAI"
        "anthropic.com" in normalized -> "Using Anthropic-compatible endpoint"
        else -> "Using OpenAI-compatible endpoint"
    }
}

private class LastFiveVisibleApiKeyTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.length <= 5) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val masked = "•".repeat(raw.length - 5) + raw.takeLast(5)
        return TransformedText(AnnotatedString(masked), OffsetMapping.Identity)
    }
}

@Composable
private fun StatusCard(title: String, body: String) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(body)
        }
    }
}
