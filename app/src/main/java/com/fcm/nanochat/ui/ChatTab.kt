package com.fcm.nanochat.ui

import android.graphics.drawable.GradientDrawable
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.fcm.nanochat.R
import com.fcm.nanochat.inference.InferenceMode
import com.fcm.nanochat.model.ChatMessage
import com.fcm.nanochat.model.ChatRole
import com.fcm.nanochat.model.ChatScreenState
import com.fcm.nanochat.model.SettingsScreenState
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.latex.JLatexMathTheme
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin

@Composable
internal fun ChatTab(
    state: ChatScreenState,
    settingsState: SettingsScreenState,
    modifier: Modifier = Modifier,
    onOpenSessions: () -> Unit,
    onSendMessage: () -> Unit,
    onMessageDraftChange: (String) -> Unit,
    onCreateSession: () -> Unit,
    onRetryLast: () -> Unit,
    onInferenceModeChange: (InferenceMode) -> Unit,
    onTemperatureChange: (Double) -> Unit,
    onTopPChange: (Double) -> Unit,
    onContextLengthChange: (Int) -> Unit,
    onMessageInfo: (ChatMessage) -> Unit
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
        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val horizontalPadding = if (compact) 14.dp else 20.dp
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPadding)

        if (compact) {
            ChatTabContent(
                state = state,
                settingsState = settingsState,
                imeVisible = imeVisible,
                modifier = contentModifier,
                onOpenSessions = onOpenSessions,
                onSendMessage = onSendMessage,
                onMessageDraftChange = onMessageDraftChange,
                onCreateSession = onCreateSession,
                onRetryLast = onRetryLast,
                onInferenceModeChange = onInferenceModeChange,
                onTemperatureChange = onTemperatureChange,
                onTopPChange = onTopPChange,
                onContextLengthChange = onContextLengthChange,
                onMessageInfo = onMessageInfo
            )
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
                ChatTabContent(
                    state = state,
                    settingsState = settingsState,
                    imeVisible = imeVisible,
                    modifier = contentModifier,
                    onOpenSessions = onOpenSessions,
                    onSendMessage = onSendMessage,
                    onMessageDraftChange = onMessageDraftChange,
                    onCreateSession = onCreateSession,
                    onRetryLast = onRetryLast,
                    onInferenceModeChange = onInferenceModeChange,
                    onTemperatureChange = onTemperatureChange,
                    onTopPChange = onTopPChange,
                    onContextLengthChange = onContextLengthChange,
                    onMessageInfo = onMessageInfo
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ChatTabContent(
    state: ChatScreenState,
    settingsState: SettingsScreenState,
    imeVisible: Boolean,
    modifier: Modifier = Modifier,
    onOpenSessions: () -> Unit,
    onSendMessage: () -> Unit,
    onMessageDraftChange: (String) -> Unit,
    onCreateSession: () -> Unit,
    onRetryLast: () -> Unit,
    onInferenceModeChange: (InferenceMode) -> Unit,
    onTemperatureChange: (Double) -> Unit,
    onTopPChange: (Double) -> Unit,
    onContextLengthChange: (Int) -> Unit,
    onMessageInfo: (ChatMessage) -> Unit
) {
    var composerHeightPx by remember { mutableStateOf(0) }
    var controlsVisible by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val measuredComposerHeight = with(density) { composerHeightPx.toDp() }
    val imeBottomInset = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    val navBottomInset = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    val bottomInset = maxOf(imeBottomInset, navBottomInset)
    val composerHeight = if (measuredComposerHeight > 0.dp) measuredComposerHeight else 96.dp
    val listBottomPadding = composerHeight + bottomInset + 20.dp

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ChatTopBar(
                selectedMode = state.inferenceMode,
                modelName = settingsState.modelName,
                onInferenceModeChange = onInferenceModeChange,
                onOpenSessions = onOpenSessions
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (state.messages.isEmpty()) {
                Box(modifier = Modifier.weight(1f)) {
                    if (!imeVisible) {
                        EmptyConversation(modifier = Modifier.fillMaxSize())
                    }
                }
            } else {
                MessageList(
                    messages = state.messages,
                    modifier = Modifier.weight(1f),
                    bottomPadding = listBottomPadding,
                    onMessageInfo = onMessageInfo
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
        ) {
            Composer(
                draft = state.draft,
                isSending = state.isSending,
                notice = state.notice,
                onOpenControls = { controlsVisible = true },
                onDraftChange = onMessageDraftChange,
                onSend = onSendMessage,
                onRetry = onRetryLast,
                modifier = Modifier
                    .onSizeChanged { composerHeightPx = it.height }
                    .padding(bottom = 8.dp)
            )
        }
    }

    if (controlsVisible) {
        ModelControlsSheet(
            temperature = settingsState.temperature,
            topP = settingsState.topP,
            contextLength = settingsState.contextLength,
            onTemperatureChange = onTemperatureChange,
            onTopPChange = onTopPChange,
            onContextLengthChange = onContextLengthChange,
            onDismiss = { controlsVisible = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelControlsSheet(
    temperature: Double,
    topP: Double,
    contextLength: Int,
    onTemperatureChange: (Double) -> Unit,
    onTopPChange: (Double) -> Unit,
    onContextLengthChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Model controls", style = MaterialTheme.typography.titleMedium)
            LabeledSlider(
                label = "Temperature",
                value = temperature.toFloat(),
                range = 0f..2f,
                steps = 5,
                formatter = { "%.2f".format(it) },
                onValueChange = { onTemperatureChange(it.toDouble()) }
            )
            LabeledSlider(
                label = "Top P",
                value = topP.toFloat(),
                range = 0f..1f,
                steps = 5,
                formatter = { "%.2f".format(it) },
                onValueChange = { onTopPChange(it.toDouble()) }
            )
            LabeledSlider(
                label = "Context length",
                value = contextLength.toFloat(),
                range = 512f..32768f,
                steps = 10,
                formatter = { it.toInt().toString() },
                onValueChange = { onContextLengthChange(it.toInt()) }
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    formatter: (Float) -> String,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(formatter(value), style = MaterialTheme.typography.labelMedium)
        }
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps
        )
    }
}

@Composable
private fun ChatTopBar(
    selectedMode: InferenceMode,
    modelName: String,
    onInferenceModeChange: (InferenceMode) -> Unit,
    onOpenSessions: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val cleanModel = modelName
        .replace(Regex("[-_]+"), " ")
        .trim()
        .ifBlank { "Model" }
    val selectedLabel = if (selectedMode == InferenceMode.REMOTE) {
        "Remote // $cleanModel"
    } else {
        "Gemini Nano"
    }

    val pillShape = RoundedCornerShape(22.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onOpenSessions) {
            Icon(Icons.Default.Menu, contentDescription = "Sessions")
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Surface(
                    onClick = { expanded = true },
                    shape = pillShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = selectedLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select backend"
                        )
                    }
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Gemini Nano") },
                        onClick = {
                            expanded = false
                            onInferenceModeChange(InferenceMode.AICORE)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Remote // $cleanModel") },
                        onClick = {
                            expanded = false
                            onInferenceModeChange(InferenceMode.REMOTE)
                        }
                    )
                }
            }
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
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 24.dp,
    onMessageInfo: (ChatMessage) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)
    ) {
        items(messages, key = { it.id }) { message ->
            val isUser = message.role == ChatRole.USER
            var showUserCopy by remember(message.id) { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
            ) {
                if (isUser) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.82f)
                            .padding(end = 4.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Surface(
                            shape = RoundedCornerShape(
                                topStart = 18.dp,
                                topEnd = 20.dp,
                                bottomStart = 18.dp,
                                bottomEnd = 10.dp
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            tonalElevation = 2.dp,
                            shadowElevation = 6.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { onMessageInfo(message) },
                                        onLongClick = {
                                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                            showUserCopy = true
                                        }
                                    )
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                MarkdownMessage(
                                    content = message.content,
                                    textColor = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (showUserCopy) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        MessageCopyButton(
                                            enabled = message.content.isNotBlank(),
                                            onCopy = {
                                                clipboardManager.setText(AnnotatedString(message.content))
                                                showUserCopy = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (message.content.isBlank() && message.isStreaming) {
                            Text(
                                text = "Thinking...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            MarkdownMessage(
                                content = message.content,
                                textColor = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            MessageCopyButton(
                                enabled = message.content.isNotBlank(),
                                onCopy = { clipboardManager.setText(AnnotatedString(message.content)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageCopyButton(
    enabled: Boolean,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp
    ) {
        IconButton(
            onClick = onCopy,
            enabled = enabled,
            modifier = modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = stringResource(id = R.string.copy_message),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun MarkdownMessage(
    content: String,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val textArgb = textColor.toArgb()
    val latexBackgroundColor = textColor.copy(alpha = 0.12f).toArgb()
    val latexCornerRadiusPx = with(density) { 10.dp.toPx() }

    val markwon = remember(
        context,
        textArgb,
        latexBackgroundColor,
        latexCornerRadiusPx
    ) {
        Markwon.builder(context)
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(
                JLatexMathPlugin.create(16f) { builder ->
                    builder.inlinesEnabled(true)
                    builder.theme().backgroundProvider {
                        GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = latexCornerRadiusPx
                            setColor(latexBackgroundColor)
                        }
                    }
                    builder
                        .theme()
                        .padding(JLatexMathTheme.Padding.symmetric(5, 8))
                    builder.theme().textColor(textArgb)
                }
            )
            .build()
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            TextView(viewContext).apply {
                setTextColor(textArgb)
                textSize = 14f
                setLineSpacing(0f, 1.2f)
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { textView ->
            textView.setTextColor(textArgb)
            textView.textSize = 14f
            textView.setLineSpacing(0f, 1.2f)
            markwon.setMarkdown(textView, normalizeLists(content))
        }
    )
}

@Composable
private fun Composer(
    draft: String,
    isSending: Boolean,
    notice: String?,
    onOpenControls: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sendEnabled = draft.isNotBlank() && !isSending
    val sendContainerColor = if (sendEnabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val sendContentColor = if (sendEnabled) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 2.dp
                ) {
                    IconButton(onClick = onOpenControls) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Model controls",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                TextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Chat with NanoChat...") },
                    minLines = 2,
                    maxLines = 6,
                    shape = RoundedCornerShape(18.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = sendContainerColor,
                    tonalElevation = if (sendEnabled) 6.dp else 0.dp
                ) {
                    IconButton(onClick = onSend, enabled = sendEnabled) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Send",
                            tint = sendContentColor
                        )
                    }
                }
            }

            if (notice != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onRetry, enabled = !isSending) {
                        Text("Retry last")
                    }
                }
            }
        }
    }
}

private fun normalizeLists(raw: String): String {
    if (raw.isBlank()) return raw

    val lines = raw.lines()
    val builder = StringBuilder()
    var insideFence = false

    for (line in lines) {
        val trimmed = line.trimStart()
        val isFenceToggle = trimmed.startsWith("```") || trimmed.startsWith("~~~")
        if (isFenceToggle) {
            insideFence = !insideFence
            builder.append(line).append('\n')
            continue
        }

        if (!insideFence && line.startsWith("    ")) {
            builder.append(line.removePrefix("    ")).append('\n')
        } else {
            builder.append(line).append('\n')
        }
    }

    return builder.toString().trimEnd('\n')
}
