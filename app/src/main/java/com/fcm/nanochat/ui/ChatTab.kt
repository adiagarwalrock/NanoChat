package com.fcm.nanochat.ui

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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

private val HeaderIconShape = RoundedCornerShape(14.dp)
private val HeaderSelectorShape = RoundedCornerShape(18.dp)
private val UserBubbleShape = RoundedCornerShape(
    topStart = 18.dp,
    topEnd = 18.dp,
    bottomStart = 18.dp,
    bottomEnd = 8.dp
)
private val ComposerShape = RoundedCornerShape(24.dp)
private val ControlCardShape = RoundedCornerShape(18.dp)
private val ModelSeparatorRegex = Regex("[-_]+")

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
    onMessageInfo: (ChatMessage) -> Unit,
    onDeleteMessage: (ChatMessage) -> Unit
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val compact = maxWidth < 700.dp
        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val horizontalPadding = if (compact) 16.dp else 24.dp
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
                onRetryLast = onRetryLast,
                onInferenceModeChange = onInferenceModeChange,
                onTemperatureChange = onTemperatureChange,
                onTopPChange = onTopPChange,
                onContextLengthChange = onContextLengthChange,
                onMessageInfo = onMessageInfo,
                onDeleteMessage = onDeleteMessage
            )
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                SessionsRail(
                    state = state,
                    modifier = Modifier
                        .width(280.dp)
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
                    onRetryLast = onRetryLast,
                    onInferenceModeChange = onInferenceModeChange,
                    onTemperatureChange = onTemperatureChange,
                    onTopPChange = onTopPChange,
                    onContextLengthChange = onContextLengthChange,
                    onMessageInfo = onMessageInfo,
                    onDeleteMessage = onDeleteMessage
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
    onRetryLast: () -> Unit,
    onInferenceModeChange: (InferenceMode) -> Unit,
    onTemperatureChange: (Double) -> Unit,
    onTopPChange: (Double) -> Unit,
    onContextLengthChange: (Int) -> Unit,
    onMessageInfo: (ChatMessage) -> Unit,
    onDeleteMessage: (ChatMessage) -> Unit
) {
    var composerHeightPx by remember { mutableStateOf(0) }
    var controlsVisible by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val listState = rememberLazyListState()

    val measuredComposerHeight = with(density) { composerHeightPx.toDp() }
    val imeBottomInset = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    val navBottomInset = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    val bottomInset = maxOf(imeBottomInset, navBottomInset)
    val composerHeight = if (measuredComposerHeight > 0.dp) measuredComposerHeight else 92.dp
    val listBottomPadding = composerHeight + bottomInset + 24.dp

    val keepScrolledToBottom by remember {
        derivedStateOf {
            if (state.messages.isEmpty()) {
                true
            } else {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= state.messages.lastIndex - 1
            }
        }
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty() && keepScrolledToBottom) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    LaunchedEffect(state.messages.lastOrNull()?.content, state.isSending) {
        if (state.messages.isNotEmpty() && state.isSending && keepScrolledToBottom) {
            listState.scrollToItem(state.messages.lastIndex)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ChatTopBar(
                selectedMode = state.inferenceMode,
                modelName = settingsState.modelName,
                onInferenceModeChange = onInferenceModeChange,
                onOpenSessions = onOpenSessions
            )

            if (state.messages.isEmpty()) {
                Box(modifier = Modifier.weight(1f)) {
                    if (!imeVisible) {
                        EmptyConversation(modifier = Modifier.fillMaxSize())
                    }
                }
            } else {
                MessageList(
                    messages = state.messages,
                    isSending = state.isSending,
                    listState = listState,
                    modifier = Modifier.weight(1f),
                    bottomPadding = listBottomPadding,
                    onMessageInfo = onMessageInfo,
                    onRetryLast = onRetryLast,
                    onDeleteMessage = onDeleteMessage
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
                    .padding(bottom = 10.dp)
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
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(id = R.string.model_controls_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(id = R.string.model_controls_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                shape = ControlCardShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    LabeledSlider(
                        label = stringResource(id = R.string.temperature_label),
                        description = stringResource(id = R.string.temperature_description),
                        value = temperature.toFloat(),
                        range = 0f..2f,
                        steps = 5,
                        formatter = { "%.2f".format(it) },
                        onValueChange = { onTemperatureChange(it.toDouble()) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    LabeledSlider(
                        label = stringResource(id = R.string.top_p_label),
                        description = stringResource(id = R.string.top_p_description),
                        value = topP.toFloat(),
                        range = 0f..1f,
                        steps = 9,
                        formatter = { "%.2f".format(it) },
                        onValueChange = { onTopPChange(it.toDouble()) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    LabeledSlider(
                        label = stringResource(id = R.string.context_length_label),
                        description = stringResource(id = R.string.context_length_description),
                        value = contextLength.toFloat(),
                        range = 512f..32768f,
                        steps = 10,
                        formatter = { it.toInt().toString() },
                        onValueChange = { onContextLengthChange(it.toInt()) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    description: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    formatter: (Float) -> String,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Text(
                    text = formatter(value),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
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
        .replace(ModelSeparatorRegex, " ")
        .trim()
        .ifBlank { stringResource(id = R.string.default_model_label) }

    val selectedLabel = if (selectedMode == InferenceMode.REMOTE) {
        cleanModel
    } else {
        stringResource(id = R.string.gemini_nano_title)
    }

    val statusDotColor = if (selectedMode == InferenceMode.REMOTE) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondary
    }

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "ModelChevronRotation"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = HeaderIconShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 1.dp
            ) {
                IconButton(
                    onClick = onOpenSessions,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = stringResource(id = R.string.open_sessions),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box {
                    Surface(
                        onClick = { expanded = true },
                        shape = HeaderSelectorShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 1.dp,
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .animateContentSize()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(statusDotColor)
                            )
                            Text(
                                text = selectedLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = stringResource(id = R.string.select_backend),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(18.dp)
                                    .graphicsLayer { rotationZ = chevronRotation }
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(text = stringResource(id = R.string.gemini_nano_title))
                                    Text(
                                        text = stringResource(id = R.string.on_device_mode),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                expanded = false
                                onInferenceModeChange(InferenceMode.AICORE)
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(text = cleanModel)
                                    Text(
                                        text = stringResource(id = R.string.remote_mode),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                expanded = false
                                onInferenceModeChange(InferenceMode.REMOTE)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.size(42.dp))
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    }
}

@Composable
private fun EmptyConversation(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
            )
            Text(
                text = stringResource(id = R.string.empty_chat_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Text(
                text = stringResource(id = R.string.empty_chat_subtitle),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    isSending: Boolean,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 24.dp,
    onMessageInfo: (ChatMessage) -> Unit,
    onRetryLast: () -> Unit,
    onDeleteMessage: (ChatMessage) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(top = 12.dp, bottom = bottomPadding)
    ) {
        itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
            val sameRoleAsPrevious = messages.getOrNull(index - 1)?.role == message.role
            val topPadding = if (index == 0) 2.dp else if (sameRoleAsPrevious) 10.dp else 18.dp

            MessageRow(
                message = message,
                isSending = isSending,
                modifier = Modifier.padding(top = topPadding),
                onMessageInfo = onMessageInfo,
                onRetryLast = onRetryLast,
                onDeleteMessage = onDeleteMessage
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    message: ChatMessage,
    isSending: Boolean,
    modifier: Modifier = Modifier,
    onMessageInfo: (ChatMessage) -> Unit,
    onRetryLast: () -> Unit,
    onDeleteMessage: (ChatMessage) -> Unit
) {
    val isUser = message.role == ChatRole.USER
    val clipboardManager = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val density = LocalDensity.current

    var actionMenuExpanded by remember(message.id) { mutableStateOf(false) }
    var entered by remember(message.id) { mutableStateOf(false) }

    LaunchedEffect(message.id) {
        entered = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 170),
        label = "MessageFade"
    )
    val offsetY by animateFloatAsState(
        targetValue = if (entered) 0f else 10f,
        animationSpec = tween(durationMillis = 170),
        label = "MessageOffset"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = alpha
                translationY = with(density) { offsetY.dp.toPx() }
            },
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.76f else 0.96f)
        ) {
            val interactionModifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onMessageInfo(message) },
                    onLongClick = {
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        actionMenuExpanded = true
                    }
                )

            if (isUser) {
                Surface(
                    modifier = interactionModifier,
                    shape = UserBubbleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.86f),
                    tonalElevation = 1.dp
                ) {
                    MarkdownMessage(
                        content = message.content,
                        textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSizeSp = 15f,
                        lineSpacingMultiplier = 1.33f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 11.dp)
                    )
                }
            } else {
                Column(
                    modifier = interactionModifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (message.content.isBlank() && message.isStreaming) {
                        TypingIndicator()
                    } else {
                        MarkdownMessage(
                            content = message.content,
                            textColor = MaterialTheme.colorScheme.onSurface,
                            fontSizeSp = 16f,
                            lineSpacingMultiplier = 1.45f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            MessageActionsMenu(
                expanded = actionMenuExpanded,
                canRegenerate = !isSending,
                canDelete = !message.isStreaming,
                onDismiss = { actionMenuExpanded = false },
                onCopy = {
                    clipboardManager.setText(AnnotatedString(message.content))
                    actionMenuExpanded = false
                },
                onShare = {
                    shareMessage(context, message.content)
                    actionMenuExpanded = false
                },
                onRegenerate = {
                    onRetryLast()
                    actionMenuExpanded = false
                },
                onDelete = {
                    onDeleteMessage(message)
                    actionMenuExpanded = false
                }
            )
        }
    }
}

@Composable
private fun MessageActionsMenu(
    expanded: Boolean,
    canRegenerate: Boolean,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(14.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(id = R.string.copy_message)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null
                )
            },
            onClick = onCopy
        )
        DropdownMenuItem(
            text = { Text(stringResource(id = R.string.share_message)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null
                )
            },
            onClick = onShare
        )
        DropdownMenuItem(
            text = { Text(stringResource(id = R.string.regenerate_response)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null
                )
            },
            enabled = canRegenerate,
            onClick = onRegenerate
        )
        DropdownMenuItem(
            text = { Text(stringResource(id = R.string.delete_message)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null
                )
            },
            enabled = canDelete,
            onClick = onDelete
        )
    }
}

@Composable
private fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "TypingDots")
    val dotOne by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DotOneAlpha"
    )
    val dotTwo by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520, delayMillis = 120),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DotTwoAlpha"
    )
    val dotThree by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520, delayMillis = 240),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DotThreeAlpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        TypingDot(alpha = dotOne)
        TypingDot(alpha = dotTwo)
        TypingDot(alpha = dotThree)
        Text(
            text = stringResource(id = R.string.assistant_thinking),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TypingDot(alpha: Float) {
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
    )
}

@Composable
private fun MarkdownMessage(
    content: String,
    textColor: Color,
    fontSizeSp: Float,
    lineSpacingMultiplier: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val textArgb = textColor.toArgb()
    val latexBackgroundColor = textColor.copy(alpha = 0.14f).toArgb()
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
                textSize = fontSizeSp
                setLineSpacing(0f, lineSpacingMultiplier)
                movementMethod = LinkMovementMethod.getInstance()
                setTextIsSelectable(true)
            }
        },
        update = { textView ->
            textView.setTextColor(textArgb)
            textView.textSize = fontSizeSp
            textView.setLineSpacing(0f, lineSpacingMultiplier)
            textView.setTextIsSelectable(true)
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
    val sendInteractionSource = remember { MutableInteractionSource() }
    val sendPressed by sendInteractionSource.collectIsPressedAsState()
    val sendScale by animateFloatAsState(
        targetValue = if (sendPressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = 110),
        label = "SendButtonScale"
    )

    val sendContainerColor = if (sendEnabled) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val sendContentColor = if (sendEnabled) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ComposerShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f)
                ) {
                    IconButton(
                        onClick = onOpenControls,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = stringResource(id = R.string.model_controls_title),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                TextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = stringResource(id = R.string.composer_placeholder),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    minLines = 1,
                    maxLines = 5,
                    shape = RoundedCornerShape(16.dp),
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
                    modifier = Modifier.graphicsLayer {
                        scaleX = sendScale
                        scaleY = sendScale
                    },
                    shape = CircleShape,
                    color = sendContainerColor,
                    tonalElevation = if (sendEnabled) 3.dp else 0.dp
                ) {
                    IconButton(
                        onClick = onSend,
                        enabled = sendEnabled,
                        interactionSource = sendInteractionSource,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = stringResource(id = R.string.send_message),
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
                    TextButton(
                        onClick = onRetry,
                        enabled = !isSending
                    ) {
                        Text(text = stringResource(id = R.string.retry_last))
                    }
                }
            }
        }
    }
}

private fun shareMessage(context: android.content.Context, content: String) {
    if (content.isBlank()) return

    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, content)
    }
    val chooser = Intent.createChooser(sendIntent, context.getString(R.string.share_message))
    if (context !is Activity) {
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(chooser) }
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
