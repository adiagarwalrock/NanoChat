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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.fcm.nanochat.R
import com.fcm.nanochat.data.AcceleratorPreference
import com.fcm.nanochat.data.ThinkingEffort
import com.fcm.nanochat.inference.InferenceMode
import com.fcm.nanochat.inference.ReasoningContentParser
import com.fcm.nanochat.model.ChatMessage
import com.fcm.nanochat.model.ChatRole
import com.fcm.nanochat.model.ChatScreenState
import com.fcm.nanochat.model.SettingsScreenState
import com.fcm.nanochat.ui.theme.NanoChatTheme
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.latex.JLatexMathTheme
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin

private val HeaderIconShape = RoundedCornerShape(14.dp)
private val HeaderSelectorShape = RoundedCornerShape(18.dp)
private val UserBubbleShape =
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 8.dp)
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
        onStopGeneration: () -> Unit,
        onMessageDraftChange: (String) -> Unit,
        onCreateSession: () -> Unit,
        onRetryLast: () -> Unit,
        onInferenceModeChange: (InferenceMode) -> Unit,
        onOpenModelGallery: () -> Unit,
        onTemperatureChange: (Double) -> Unit,
        onTopPChange: (Double) -> Unit,
        onContextLengthChange: (Int) -> Unit,
        onThinkingEffortChange: (ThinkingEffort) -> Unit,
        onAcceleratorChange: (AcceleratorPreference) -> Unit,
        onMessageInfo: (ChatMessage) -> Unit,
        onDeleteMessage: (ChatMessage) -> Unit
) {
        val windowInfo = LocalWindowInfo.current
        val screenWidthDp = with(LocalDensity.current) { windowInfo.containerSize.width.toDp() }
        val compact = screenWidthDp < 700.dp
        val horizontalPadding = if (compact) 16.dp else 24.dp
        val contentModifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding)

        Box(modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)) {
            ChatTabContent(
                    state = state,
                    settingsState = settingsState,
                    modifier = contentModifier,
                    showMenuIcon = compact,
                    onOpenSessions = onOpenSessions,
                    onSendMessage = onSendMessage,
                    onStopGeneration = onStopGeneration,
                    onMessageDraftChange = onMessageDraftChange,
                    onRetryLast = onRetryLast,
                    onInferenceModeChange = onInferenceModeChange,
                    onOpenModelGallery = onOpenModelGallery,
                    onTemperatureChange = onTemperatureChange,
                    onTopPChange = onTopPChange,
                    onContextLengthChange = onContextLengthChange,
                    onThinkingEffortChange = onThinkingEffortChange,
                    onAcceleratorChange = onAcceleratorChange,
                    onMessageInfo = onMessageInfo,
                    onDeleteMessage = onDeleteMessage
            )
        }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ChatTabContent(
        state: ChatScreenState,
        settingsState: SettingsScreenState,
        modifier: Modifier = Modifier,
        showMenuIcon: Boolean,
        onOpenSessions: () -> Unit,
        onSendMessage: () -> Unit,
        onStopGeneration: () -> Unit,
        onMessageDraftChange: (String) -> Unit,
        onRetryLast: () -> Unit,
        onInferenceModeChange: (InferenceMode) -> Unit,
        onOpenModelGallery: () -> Unit,
        onTemperatureChange: (Double) -> Unit,
        onTopPChange: (Double) -> Unit,
        onContextLengthChange: (Int) -> Unit,
        onThinkingEffortChange: (ThinkingEffort) -> Unit,
        onAcceleratorChange: (AcceleratorPreference) -> Unit,
        onMessageInfo: (ChatMessage) -> Unit,
        onDeleteMessage: (ChatMessage) -> Unit
) {
        var controlsVisible by remember { mutableStateOf(false) }
        var composerHeightPx by remember { mutableIntStateOf(0) }
        val bottomPadding = with(LocalDensity.current) { composerHeightPx.toDp() }
        val listState = rememberLazyListState()
        val showLocalModelCta =
                state.inferenceMode == InferenceMode.DOWNLOADED && !state.isLocalModelReady

        Box(modifier = modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                        ChatTopBar(
                                selectedMode = state.inferenceMode,
                                modelName = settingsState.modelName,
                                activeLocalModelName = state.activeLocalModelName,
                                isLocalModelReady = state.isLocalModelReady,
                                showMenuIcon = showMenuIcon,
                                onInferenceModeChange = onInferenceModeChange,
                                onOpenSessions = onOpenSessions
                        )

                        if (state.inferenceMode == InferenceMode.DOWNLOADED && !state.isLocalModelReady) {
                                LocalModelStatusSurface(
                                        modelName = state.activeLocalModelName,
                                        isReady = state.isLocalModelReady,
                                        message = state.localModelStatusMessage,
                                        onOpenModelGallery = onOpenModelGallery,
                                        modifier =
                                                Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 10.dp)
                                )
                        }

                        if (showLocalModelCta && state.messages.isEmpty()) {
                                LocalModelEmptyState(
                                        statusMessage = state.localModelStatusMessage,
                                        modifier = Modifier.weight(1f),
                                        onOpenModelGallery = onOpenModelGallery
                                )
                        } else {
                                Box(modifier = Modifier.weight(1f)) {
                                        MessageList(
                                                messages = state.messages,
                                                isSending = state.isSending,
                                                listState = listState,
                                                bottomPadding = bottomPadding + 20.dp,
                                                onMessageInfo = onMessageInfo,
                                                onRetryLast = onRetryLast,
                                                onDeleteMessage = onDeleteMessage,
                                                modifier = Modifier.fillMaxSize()
                                        )

                                        // Soft top-fade overlay — only visible when scrolled
                                        if (listState.canScrollBackward) {
                                                Box(
                                                        modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(32.dp)
                                                                .align(Alignment.TopCenter)
                                                                .background(
                                                                        Brush.verticalGradient(
                                                                                colors = listOf(
                                                                                        MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                                                                                        MaterialTheme.colorScheme.background.copy(alpha = 0f)
                                                                                )
                                                                        )
                                                                )
                                                )
                                        }
                                }
                        }
                }

                Composer(
                        draft = state.draft,
                        isSending = state.isSending,
                        notice = state.notice,
                        canSend = !showLocalModelCta,
                        onOpenControls = { controlsVisible = true },
                        onDraftChange = onMessageDraftChange,
                        onSend = onSendMessage,
                        onStop = onStopGeneration,
                        onRetry = onRetryLast,
                        modifier =
                                Modifier
                                        .align(Alignment.BottomCenter)
                                        .onSizeChanged { composerHeightPx = it.height }
                                        .navigationBarsPadding()
                                        .imePadding()
                                        .padding(bottom = 15.dp)
                )

                if (controlsVisible) {
                        ModelControlsSheet(
                                state = state,
                                settings = settingsState,
                                onDismiss = { controlsVisible = false },
                                onUpdateSettings = { _, _, temperature, topP, contextLength ->
                                        onTemperatureChange(temperature)
                                        onTopPChange(topP)
                                        onContextLengthChange(contextLength)
                                },
                                onUpdateThinkingEffort = onThinkingEffortChange,
                                onUpdateAccelerator = onAcceleratorChange
                        )
                }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelControlsSheet(
        state: ChatScreenState,
        settings: SettingsScreenState,
        onDismiss: () -> Unit,
        onUpdateSettings:
                (
                        baseUrl: String,
                        modelName: String,
                        temperature: Double,
                        topP: Double,
                        contextLength: Int) -> Unit,
        onUpdateThinkingEffort: (ThinkingEffort) -> Unit,
        onUpdateAccelerator: (AcceleratorPreference) -> Unit
) {
        ModalBottomSheet(
                onDismissRequest = onDismiss,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                dragHandle = {
                        BottomSheetDefaults.DragHandle(
                                color =
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = 0.55f
                                        )
                        )
                }
        ) {
                Column(
                        modifier =
                                Modifier
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
                                                .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                        LabeledSlider(
                                                label =
                                                        stringResource(
                                                                id = R.string.temperature_label
                                                        ),
                                                description =
                                                        stringResource(
                                                                id =
                                                                        R.string
                                                                                .temperature_description
                                                        ),
                                                value = settings.temperature.toFloat(),
                                                range = 0f..2f,
                                                steps = 5,
                                                formatter = { "%.2f".format(it) },
                                                onValueChange = {
                                                        onUpdateSettings(
                                                                settings.baseUrl,
                                                                settings.modelName,
                                                                it.toDouble(),
                                                                settings.topP,
                                                                settings.contextLength
                                                        )
                                                }
                                        )
                                        HorizontalDivider(
                                                color =
                                                        MaterialTheme.colorScheme.outlineVariant
                                                                .copy(alpha = 0.5f)
                                        )
                                        LabeledSlider(
                                                label = stringResource(id = R.string.top_p_label),
                                                description =
                                                        stringResource(
                                                                id = R.string.top_p_description
                                                        ),
                                                value = settings.topP.toFloat(),
                                                range = 0f..1f,
                                                steps = 9,
                                                formatter = { "%.2f".format(it) },
                                                onValueChange = {
                                                        onUpdateSettings(
                                                                settings.baseUrl,
                                                                settings.modelName,
                                                                settings.temperature,
                                                                it.toDouble(),
                                                                settings.contextLength
                                                        )
                                                }
                                        )
                                        HorizontalDivider(
                                                color =
                                                        MaterialTheme.colorScheme.outlineVariant
                                                                .copy(alpha = 0.5f)
                                        )
                                        LabeledSlider(
                                                label =
                                                        stringResource(
                                                                id = R.string.context_length_label
                                                        ),
                                                description =
                                                        stringResource(
                                                                id =
                                                                        R.string
                                                                                .context_length_description
                                                        ),
                                                value = settings.contextLength.toFloat(),
                                                range = 512f..32768f,
                                                steps = 10,
                                                formatter = { it.toInt().toString() },
                                                onValueChange = {
                                                        onUpdateSettings(
                                                                settings.baseUrl,
                                                                settings.modelName,
                                                                settings.temperature,
                                                                settings.topP,
                                                                it.toInt()
                                                        )
                                                }
                                        )

                                        if (shouldShowThinkingControls(state, settings)) {
                                                HorizontalDivider(
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .outlineVariant.copy(
                                                                        alpha = 0.5f
                                                                )
                                                )
                                                LabeledChipRow(
                                                        label =
                                                                stringResource(
                                                                        id =
                                                                                R.string
                                                                                        .thinking_effort_label
                                                                ),
                                                        description =
                                                                stringResource(
                                                                        id =
                                                                                R.string
                                                                                        .thinking_effort_description
                                                                ),
                                                        options =
                                                                listOf(
                                                                        ChipOption(
                                                                                stringResource(
                                                                                        id =
                                                                                                R.string
                                                                                                        .thinking_effort_none
                                                                                ),
                                                                                ThinkingEffort.NONE
                                                                        ),
                                                                        ChipOption(
                                                                                stringResource(
                                                                                        id =
                                                                                                R.string
                                                                                                        .thinking_effort_low
                                                                                ),
                                                                                ThinkingEffort.LOW
                                                                        ),
                                                                        ChipOption(
                                                                                stringResource(
                                                                                        id =
                                                                                                R.string
                                                                                                        .thinking_effort_medium
                                                                                ),
                                                                                ThinkingEffort
                                                                                        .MEDIUM
                                                                        ),
                                                                        ChipOption(
                                                                                stringResource(
                                                                                        id =
                                                                                                R.string
                                                                                                        .thinking_effort_high
                                                                                ),
                                                                                ThinkingEffort.HIGH
                                                                        )
                                                                ),
                                                        selected = settings.thinkingEffort,
                                                        onSelected = onUpdateThinkingEffort
                                                )
                                        }

                                        if (state.inferenceMode == InferenceMode.DOWNLOADED) {
                                                HorizontalDivider(
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .outlineVariant.copy(
                                                                        alpha = 0.5f
                                                                )
                                                )
                                                val context = LocalContext.current
                                                val availableOptions =
                                                        remember(
                                                                state.localModelSupportedAccelerators
                                                        ) {
                                                                getAvailableAcceleratorOptions(
                                                                        context,
                                                                        state.localModelSupportedAccelerators
                                                                )
                                                        }

                                                LabeledChipRow(
                                                        label =
                                                                stringResource(
                                                                        id =
                                                                                R.string
                                                                                        .accelerator_label
                                                                ),
                                                        description =
                                                                stringResource(
                                                                        id =
                                                                                R.string
                                                                                        .accelerator_description
                                                                ),
                                                        options = availableOptions,
                                                        selected = settings.acceleratorPreference,
                                                        onSelected = onUpdateAccelerator
                                                )
                                        }
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
                                        modifier =
                                                Modifier.padding(
                                                        horizontal = 10.dp,
                                                        vertical = 5.dp
                                                )
                                )
                        }
                }

                Slider(
                        value = value,
                        onValueChange = onValueChange,
                        valueRange = range,
                        steps = steps,
                        colors =
                                SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor =
                                                MaterialTheme.colorScheme.primary.copy(
                                                        alpha = 0.75f
                                                ),
                                        inactiveTrackColor =
                                                MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                )
        }
}

private data class ChipOption<T>(val label: String, val value: T)

@Composable
private fun <T> LabeledChipRow(
        label: String,
        description: String,
        options: List<ChipOption<T>>,
        selected: T,
        onSelected: (T) -> Unit
) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(
                        modifier = Modifier.fillMaxWidth(),
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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { option ->
                                FilterChip(
                                        selected = option.value == selected,
                                        onClick = { onSelected(option.value) },
                                        label = { Text(option.label) }
                                )
                        }
                }
        }
}

@Composable
private fun ChatTopBar(
        selectedMode: InferenceMode,
        modelName: String,
        activeLocalModelName: String?,
        isLocalModelReady: Boolean = false,
        showMenuIcon: Boolean,
        onInferenceModeChange: (InferenceMode) -> Unit,
        onOpenSessions: () -> Unit
) {
        var expanded by remember { mutableStateOf(false) }
        val cleanModel =
                modelName.replace(ModelSeparatorRegex, " ").trim().ifBlank {
                        stringResource(id = R.string.default_model_label)
                }

        val selectedLabel =
                when (selectedMode) {
                        InferenceMode.AICORE -> stringResource(id = R.string.gemini_nano_title)
                        InferenceMode.DOWNLOADED -> activeLocalModelName ?: "Downloaded model"
                        InferenceMode.REMOTE -> cleanModel
                }

        val readyGreen = Color(0xFF4CAF50)
        val statusDotColor =
                when (selectedMode) {
                        InferenceMode.AICORE -> MaterialTheme.colorScheme.secondary
                        InferenceMode.DOWNLOADED -> if (isLocalModelReady) readyGreen else MaterialTheme.colorScheme.tertiary
                        InferenceMode.REMOTE -> MaterialTheme.colorScheme.primary
                }


        val chevronRotation by
                animateFloatAsState(
                        targetValue = if (expanded) 180f else 0f,
                        animationSpec = tween(durationMillis = 160),
                        label = "ModelChevronRotation"
                )

        Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                        modifier =
                                Modifier
                                        .fillMaxWidth()
                                        .statusBarsPadding()
                                        .padding(top = 6.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        if (showMenuIcon) {
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
                                                        contentDescription =
                                                                stringResource(id = R.string.open_sessions),
                                                        tint = MaterialTheme.colorScheme.onSurface
                                                )
                                        }
                                }
                        } else {
                                Spacer(modifier = Modifier.size(42.dp))
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
                                                color =
                                                        MaterialTheme.colorScheme
                                                                .surfaceContainerHigh,
                                                tonalElevation = 1.dp,
                                                shadowElevation = 2.dp
                                        ) {
                                                Row(
                                                        modifier =
                                                                Modifier
                                                                        .animateContentSize()
                                                                        .padding(
                                                                                horizontal = 14.dp,
                                                                                vertical = 8.dp
                                                                        ),
                                                        verticalAlignment =
                                                                Alignment.CenterVertically,
                                                        horizontalArrangement =
                                                                Arrangement.spacedBy(8.dp)
                                                ) {
                                                        Box(
                                                                modifier =
                                                                        Modifier
                                                                                .size(7.dp)
                                                                                .clip(CircleShape)
                                                                                .background(
                                                                                        statusDotColor
                                                                                )
                                                        )
                                                        Text(
                                                                text = selectedLabel,
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelLarge,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurface,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                        )
                                                        Icon(
                                                                imageVector =
                                                                        Icons.Default.ArrowDropDown,
                                                                contentDescription =
                                                                        stringResource(
                                                                                id =
                                                                                        R.string
                                                                                                .select_backend
                                                                        ),
                                                                tint =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant,
                                                                modifier =
                                                                        Modifier
                                                                                .size(18.dp)
                                                                                .graphicsLayer {
                                                                                        rotationZ =
                                                                                                chevronRotation
                                                                                }
                                                        )
                                                }
                                        }

                                        DropdownMenu(
                                                expanded = expanded,
                                                onDismissRequest = { expanded = false },
                                                shape = RoundedCornerShape(16.dp),
                                                containerColor =
                                                        MaterialTheme.colorScheme
                                                                .surfaceContainerHigh
                                        ) {
                                                DropdownMenuItem(
                                                        text = {
                                                                Column {
                                                                        Text(
                                                                                text =
                                                                                        stringResource(
                                                                                                id =
                                                                                                        R.string
                                                                                                                .gemini_nano_title
                                                                                        )
                                                                        )
                                                                        Text(
                                                                                text =
                                                                                        stringResource(
                                                                                                id =
                                                                                                        R.string
                                                                                                                .on_device_mode
                                                                                        ),
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .bodySmall,
                                                                                color =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .onSurfaceVariant
                                                                        )
                                                                }
                                                        },
                                                        onClick = {
                                                                expanded = false
                                                                onInferenceModeChange(
                                                                        InferenceMode.AICORE
                                                                )
                                                        }
                                                )
                                                DropdownMenuItem(
                                                        text = {
                                                                Column {
                                                                        Text(
                                                                                text =
                                                                                        activeLocalModelName
                                                                                                ?: "Downloaded model"
                                                                        )
                                                                        Text(
                                                                                text =
                                                                                        "Local model",
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .bodySmall,
                                                                                color =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .onSurfaceVariant
                                                                        )
                                                                }
                                                        },
                                                        onClick = {
                                                                expanded = false
                                                                onInferenceModeChange(
                                                                        InferenceMode.DOWNLOADED
                                                                )
                                                        }
                                                )
                                                DropdownMenuItem(
                                                        text = {
                                                                Column {
                                                                        Text(text = cleanModel)
                                                                        Text(
                                                                                text =
                                                                                        stringResource(
                                                                                                id =
                                                                                                        R.string
                                                                                                                .remote_mode
                                                                                        ),
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .bodySmall,
                                                                                color =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .onSurfaceVariant
                                                                        )
                                                                }
                                                        },
                                                        onClick = {
                                                                expanded = false
                                                                onInferenceModeChange(
                                                                        InferenceMode.REMOTE
                                                                )
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
private fun LocalModelStatusSurface(
        modelName: String?,
        isReady: Boolean,
        message: String?,
        onOpenModelGallery: () -> Unit,
        modifier: Modifier = Modifier
) {
        val normalizedMessage = message?.trim().orEmpty()
        val lowerMessage = normalizedMessage.lowercase()
        val preparing = !isReady && ("preparing" in lowerMessage || "loading" in lowerMessage)

        val titleText =
                when {
                        isReady -> "Running on ${modelName.orEmpty().ifBlank { "local model" }}"
                        preparing -> "Preparing local model"
                        modelName.isNullOrBlank() -> "Select a local model"
                        else -> "Local model selected"
                }

        Surface(
                modifier = modifier,
                shape = RoundedCornerShape(14.dp),
                color =
                        if (isReady) {
                                MaterialTheme.colorScheme.secondaryContainer
                        } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                        }
        ) {
                Column(
                        modifier =
                                Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                        Text(
                                                text = titleText,
                                                style = MaterialTheme.typography.labelLarge,
                                                color =
                                                        if (isReady) {
                                                                MaterialTheme.colorScheme
                                                                        .onSecondaryContainer
                                                        } else {
                                                                MaterialTheme.colorScheme.onSurface
                                                        }
                                        )
                                        if (normalizedMessage.isNotBlank()) {
                                                Text(
                                                        text = normalizedMessage,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color =
                                                                if (isReady) {
                                                                        MaterialTheme.colorScheme
                                                                                .onSecondaryContainer
                                                                } else {
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
                                                                }
                                                )
                                        }
                                }
                                TextButton(onClick = onOpenModelGallery) {
                                        Text(text = stringResource(id = R.string.open_model_library))
                                }
                        }

                        if (preparing) {
                                LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor =
                                                MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                        }
                }
        }
}

@Composable
private fun LocalModelEmptyState(
        statusMessage: String?,
        modifier: Modifier = Modifier,
        onOpenModelGallery: () -> Unit
) {
        val normalizedStatus = statusMessage?.trim().orEmpty()

        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                        Text(
                                text =
                                        stringResource(
                                                id = R.string.local_model_empty_title
                                        ),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                        )

                        if (normalizedStatus.isNotBlank()) {
                                Text(
                                        text = normalizedStatus,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        }

                        Button(onClick = onOpenModelGallery) {
                                Text(text = stringResource(id = R.string.open_model_library))
                        }
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
        if (messages.isEmpty() && !isSending) {
                EmptyChatGreeting(bottomPadding = bottomPadding, modifier = modifier)
        } else {
                LazyColumn(
                        state = listState,
                        modifier = modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(top = 12.dp, bottom = bottomPadding)
                ) {
                        itemsIndexed(messages, key = { _, message -> message.id }) { index, message
                                ->
                                val sameRoleAsPrevious =
                                        messages.getOrNull(index - 1)?.role == message.role
                                val topPadding =
                                        when {
                                                index == 0 -> 2.dp
                                                sameRoleAsPrevious -> 10.dp
                                                else -> 18.dp
                                        }

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
}

@Composable
private fun EmptyChatGreeting(bottomPadding: Dp, modifier: Modifier = Modifier) {
        val density = LocalDensity.current
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }

        val alpha by
        animateFloatAsState(
                targetValue = if (visible) 1f else 0f,
                animationSpec = tween(600),
                label = "GreetingFade"
        )

        val translateY by
        animateFloatAsState(
                targetValue = if (visible) 0f else 24f,
                animationSpec = tween(600),
                label = "GreetingSlide"
        )

        Column(
                modifier =
                        modifier
                                .fillMaxWidth()
                                .padding(horizontal = 28.dp)
                                .padding(bottom = bottomPadding)
                                .graphicsLayer {
                                        this.alpha = alpha
                                        translationY = with(density) { translateY.dp.toPx() }
                                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
        ) {
                Surface(
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ) {
                        Box(contentAlignment = Alignment.Center) {
                                Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(38.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                )
                        }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                        text = stringResource(id = R.string.empty_chat_title),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                        text = stringResource(id = R.string.empty_chat_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
        @Suppress("DEPRECATION") val clipboardManager = LocalClipboardManager.current
        val haptics = LocalHapticFeedback.current
        val context = LocalContext.current
        val density = LocalDensity.current

        var actionMenuExpanded by remember(message.id) { mutableStateOf(false) }
        var entered by remember(message.id) { mutableStateOf(false) }

        LaunchedEffect(message.id) { entered = true }

        val alpha by
                animateFloatAsState(
                        targetValue = if (entered) 1f else 0f,
                        animationSpec = tween(durationMillis = 170),
                        label = "MessageFade"
                )
        val offsetY by
                animateFloatAsState(
                        targetValue = if (entered) 0f else 10f,
                        animationSpec = tween(durationMillis = 170),
                        label = "MessageOffset"
                )

        Row(
                modifier =
                        modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                        this.alpha = alpha
                                        translationY = with(density) { offsetY.dp.toPx() }
                                },
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
                Box(modifier = Modifier.fillMaxWidth(if (isUser) 0.76f else 0.96f)) {
                        val interactionModifier =
                                Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                                onClick = { onMessageInfo(message) },
                                                onLongClick = {
                                                        haptics.performHapticFeedback(
                                                                androidx.compose.ui.hapticfeedback
                                                                        .HapticFeedbackType
                                                                        .LongPress
                                                        )
                                                        actionMenuExpanded = true
                                                }
                                        )

                        if (isUser) {
                                Surface(
                                        modifier = interactionModifier,
                                        shape = UserBubbleShape,
                                        color =
                                                MaterialTheme.colorScheme.primaryContainer.copy(
                                                        alpha = 0.86f
                                                ),
                                        tonalElevation = 1.dp
                                ) {
                                        MarkdownMessage(
                                                content = message.content,
                                                textColor =
                                                        MaterialTheme.colorScheme
                                                                .onPrimaryContainer,
                                                fontSizeSp = 15f,
                                                lineSpacingMultiplier = 1.33f,
                                                modifier =
                                                        Modifier
                                                                .fillMaxWidth()
                                                                .padding(
                                                                        horizontal = 14.dp,
                                                                        vertical = 11.dp
                                                                )
                                        )
                                }
                        } else {
                                Column(
                                        modifier =
                                                interactionModifier.padding(
                                                        horizontal = 8.dp,
                                                        vertical = 4.dp
                                                ),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                        if (message.content.isBlank() && message.isStreaming) {
                                                TypingIndicator()
                                        } else if (message.content.isBlank()) {
                                                Text(
                                                        text =
                                                                stringResource(
                                                                        id =
                                                                                R.string
                                                                                        .assistant_no_response_yet
                                                                ),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                                )
                                        } else {
                                                val (thinking, visible) =
                                                        splitThinking(message.content)
                                                if (thinking != null) {
                                                        ThinkAccordion(thinkingText = thinking)
                                                }
                                                if (visible.isNotBlank()) {
                                                        MarkdownMessage(
                                                                content = visible,
                                                                textColor =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurface,
                                                                fontSizeSp = 16f,
                                                                lineSpacingMultiplier = 1.45f,
                                                                modifier = Modifier.fillMaxWidth()
                                                        )
                                                }
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
                                Icon(imageVector = Icons.Default.Share, contentDescription = null)
                        },
                        onClick = onShare
                )
                DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.regenerate_response)) },
                        leadingIcon = {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                        },
                        enabled = canRegenerate,
                        onClick = onRegenerate
                )
                DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.delete_message)) },
                        leadingIcon = {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                        },
                        enabled = canDelete,
                        onClick = onDelete
                )
        }
}

@Composable
private fun TypingIndicator() {
        val transition = rememberInfiniteTransition(label = "TypingDots")
        val dotOne by
                transition.animateFloat(
                        initialValue = 0.25f,
                        targetValue = 0.9f,
                        animationSpec =
                                infiniteRepeatable(
                                        animation = tween(durationMillis = 520),
                                        repeatMode = RepeatMode.Reverse
                                ),
                        label = "DotOneAlpha"
                )
        val dotTwo by
                transition.animateFloat(
                        initialValue = 0.25f,
                        targetValue = 0.9f,
                        animationSpec =
                                infiniteRepeatable(
                                        animation = tween(durationMillis = 520, delayMillis = 120),
                                        repeatMode = RepeatMode.Reverse
                                ),
                        label = "DotTwoAlpha"
                )
        val dotThree by
                transition.animateFloat(
                        initialValue = 0.25f,
                        targetValue = 0.9f,
                        animationSpec =
                                infiniteRepeatable(
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
                modifier =
                        Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = alpha
                                        )
                                )
        )
}

private fun shouldShowThinkingControls(
        state: ChatScreenState,
        settings: SettingsScreenState
): Boolean {
        return when (state.inferenceMode) {
                InferenceMode.DOWNLOADED -> state.localModelSupportsThinking
                InferenceMode.REMOTE -> supportsRemoteThinking(settings.modelName)
                InferenceMode.AICORE -> false
        }
}

private fun supportsRemoteThinking(modelName: String): Boolean {
        val normalized = modelName.trim().lowercase()
        if (normalized.isBlank()) return false
        return listOf("o1", "o3", "deepseek", "reason", "think").any { hint ->
                normalized.contains(hint)
        }
}

private fun splitThinking(content: String): Pair<String?, String> {
        val parsed = ReasoningContentParser.split(content)
        return parsed.thinking to parsed.visible
}

private fun getAvailableAcceleratorOptions(
        context: android.content.Context,
        supportedAccelerators: List<String>
): List<ChipOption<AcceleratorPreference>> {
        val supported = supportedAccelerators.map { it.lowercase() }
        val options = mutableListOf<ChipOption<AcceleratorPreference>>()

        options.add(
                ChipOption(context.getString(R.string.accelerator_auto), AcceleratorPreference.AUTO)
        )

        if (supported.isEmpty() || supported.contains("cpu")) {
                options.add(
                        ChipOption(
                                context.getString(R.string.accelerator_cpu),
                                AcceleratorPreference.CPU
                        )
                )
        }

        if (supported.isEmpty() || supported.contains("gpu")) {
                options.add(
                        ChipOption(
                                context.getString(R.string.accelerator_gpu),
                                AcceleratorPreference.GPU
                        )
                )
        }

        if (supported.isEmpty() || supported.contains("npu")) {
                options.add(
                        ChipOption(
                                context.getString(R.string.accelerator_nnapi),
                                AcceleratorPreference.NNAPI
                        )
                )
        }

        return options
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThinkAccordion(thinkingText: String) {
        var expanded by remember { mutableStateOf(false) }

        Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                tonalElevation = 1.dp,
                modifier =
                        Modifier
                                .fillMaxWidth()
                                .animateContentSize()
                                .combinedClickable(
                                        onClick = { expanded = !expanded },
                                        onLongClick = { expanded = !expanded }
                                )
        ) {
                Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                                Text(
                                        text = stringResource(id = R.string.thinking_section_title),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier =
                                                Modifier.graphicsLayer {
                                                        rotationZ = if (expanded) 180f else 0f
                                                }
                                )
                        }

                        if (expanded) {
                                MarkdownMessage(
                                        content = thinkingText,
                                        textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSizeSp = 14f,
                                        lineSpacingMultiplier = 1.35f,
                                        modifier = Modifier.fillMaxWidth()
                                )
                        }
                }
        }
}

private val markwonCache = android.util.LruCache<Int, Markwon>(4)

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

        val markwon =
                remember(context, textArgb, latexBackgroundColor, latexCornerRadiusPx) {
                        markwonCache.get(textArgb)
                                ?: Markwon.builder(context)
                                        .usePlugin(MarkwonInlineParserPlugin.create())
                                        .usePlugin(
                                                JLatexMathPlugin.create(16f) { builder ->
                                                        builder.inlinesEnabled(true)
                                                        builder.theme().backgroundProvider {
                                                                GradientDrawable().apply {
                                                                        shape =
                                                                                GradientDrawable
                                                                                        .RECTANGLE
                                                                        cornerRadius =
                                                                                latexCornerRadiusPx
                                                                        setColor(
                                                                                latexBackgroundColor
                                                                        )
                                                                }
                                                        }
                                                        builder.theme()
                                                                .padding(
                                                                        JLatexMathTheme.Padding
                                                                                .symmetric(5, 8)
                                                                )
                                                        builder.theme().textColor(textArgb)
                                                }
                                        )
                                        .build()
                                        .also { markwonCache.put(textArgb, it) }
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
        modifier: Modifier = Modifier,
        canSend: Boolean = true,
        onOpenControls: () -> Unit,
        onDraftChange: (String) -> Unit,
        onSend: () -> Unit,
        onStop: () -> Unit,
        onRetry: () -> Unit
) {
        val sendEnabled = canSend && draft.isNotBlank() && !isSending
        val sendInteractionSource = remember { MutableInteractionSource() }
        val sendPressed by sendInteractionSource.collectIsPressedAsState()
        val haptics = LocalHapticFeedback.current
        val sendScale by
                animateFloatAsState(
                        targetValue = if (sendPressed) 0.92f else 1f,
                        animationSpec = tween(durationMillis = 110),
                        label = "SendButtonScale"
                )

        val sendContainerColor =
                when {
                        sendEnabled -> MaterialTheme.colorScheme.primaryContainer
                        isSending -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                }
        val sendContentColor =
                when {
                        sendEnabled -> MaterialTheme.colorScheme.onPrimaryContainer
                        isSending -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

        Surface(
                modifier = modifier.fillMaxWidth(),
                shape = ComposerShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 2.dp,
                shadowElevation = 10.dp
        ) {
                Column(
                        modifier =
                                Modifier
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
                                        color =
                                                MaterialTheme.colorScheme.surfaceContainerHighest
                                                        .copy(alpha = 0.8f)
                                ) {
                                        IconButton(
                                                onClick = onOpenControls,
                                                modifier = Modifier.size(40.dp)
                                        ) {
                                                Icon(
                                                        imageVector = Icons.Default.Tune,
                                                        contentDescription =
                                                                stringResource(
                                                                        id =
                                                                                R.string
                                                                                        .model_controls_title
                                                                ),
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
                                                        text =
                                                                stringResource(
                                                                        id =
                                                                                R.string
                                                                                        .composer_placeholder
                                                                ),
                                                        style = MaterialTheme.typography.bodyMedium
                                                )
                                        },
                                        minLines = 1,
                                        maxLines = 5,
                                        shape = RoundedCornerShape(16.dp),
                                        colors =
                                                TextFieldDefaults.colors(
                                                        focusedContainerColor = Color.Transparent,
                                                        unfocusedContainerColor = Color.Transparent,
                                                        disabledContainerColor = Color.Transparent,
                                                        focusedIndicatorColor = Color.Transparent,
                                                        unfocusedIndicatorColor = Color.Transparent,
                                                        disabledIndicatorColor = Color.Transparent,
                                                        cursorColor =
                                                                MaterialTheme.colorScheme.primary,
                                                        focusedTextColor =
                                                                MaterialTheme.colorScheme.onSurface,
                                                        unfocusedTextColor =
                                                                MaterialTheme.colorScheme.onSurface,
                                                        focusedPlaceholderColor =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant,
                                                        unfocusedPlaceholderColor =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                                )
                                )

                                Surface(
                                        modifier =
                                                Modifier.graphicsLayer {
                                                        scaleX = sendScale
                                                        scaleY = sendScale
                                                },
                                        shape = CircleShape,
                                        color = sendContainerColor,
                                        tonalElevation = if (sendEnabled) 3.dp else 0.dp
                                ) {
                                        IconButton(
                                                onClick = {
                                                        if (isSending) {
                                                                onStop()
                                                        } else {
                                                                haptics.performHapticFeedback(
                                                                        HapticFeedbackType
                                                                                .TextHandleMove
                                                                )
                                                                onSend()
                                                        }
                                                },
                                                enabled = isSending || sendEnabled,
                                                interactionSource = sendInteractionSource,
                                                modifier = Modifier.size(40.dp)
                                        ) {
                                                Icon(
                                                        imageVector =
                                                                if (isSending) Icons.Default.Stop
                                                                else Icons.Default.ArrowUpward,
                                                        contentDescription =
                                                                if (isSending) {
                                                                        "Stop generation"
                                                                } else {
                                                                        stringResource(
                                                                                id =
                                                                                        R.string
                                                                                                .send_message
                                                                        )
                                                                },
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
                                                Text(
                                                        text =
                                                                stringResource(
                                                                        id = R.string.retry_last
                                                                )
                                                )
                                        }
                                }
                        }
                }
        }
}

private fun shareMessage(context: android.content.Context, content: String) {
        if (content.isBlank()) return

        val sendIntent =
                Intent(Intent.ACTION_SEND).apply {
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

        val orderedListMissingSpaceRegex = Regex("""(?<=\b\d)\.(?=[A-Z])""")
        val orderedListBoundaryRegex =
                Regex("""(?<=[\p{L}\p{N})\].!?:])(?=\d+\.\s*[A-Z])""")
        val unorderedListBoundaryRegex =
                Regex("""(?<=[\p{L}\p{N})\].!?:])(?=[-*+]\s+[A-Z])""")
        val lines = raw.replace("\r\n", "\n").replace('\r', '\n').lines()
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

                val normalizedLine =
                        if (insideFence) {
                                line
                        } else {
                                line.replace(orderedListMissingSpaceRegex, ". ")
                                        .replace(orderedListBoundaryRegex, "\n")
                                        .replace(unorderedListBoundaryRegex, "\n")
                        }

                if (!insideFence && normalizedLine.startsWith("    ")) {
                        builder.append(normalizedLine.removePrefix("    ")).append('\n')
                } else {
                        builder.append(normalizedLine).append('\n')
                }
        }

        return builder.toString().trimEnd('\n')
}

@Preview(showBackground = true)
@Composable
private fun ChatTabPreview() {
        NanoChatTheme(darkTheme = false) {
                ChatTab(
                        state =
                                ChatScreenState(
                                        messages =
                                                listOf(
                                                        ChatMessage(
                                                                1,
                                                                1,
                                                                ChatRole.USER,
                                                                "Hello! How does on-device AI work?"
                                                        ),
                                                        ChatMessage(
                                                                2,
                                                                1,
                                                                ChatRole.ASSISTANT,
                                                                "On-device AI works by running the model directly on your phone's processor (CPU, GPU, or NPU) rather than sending data to a cloud server. This ensures privacy, reduces latency, and works offline!"
                                                        )
                                                )
                                ),
                        settingsState = SettingsScreenState(modelName = "Mistral 7B (Local)"),
                        onOpenSessions = {},
                        onSendMessage = {},
                        onStopGeneration = {},
                        onMessageDraftChange = {},
                        onCreateSession = {},
                        onRetryLast = {},
                        onInferenceModeChange = {},
                        onOpenModelGallery = {},
                        onTemperatureChange = {},
                        onTopPChange = {},
                        onContextLengthChange = {},
                        onThinkingEffortChange = {},
                        onAcceleratorChange = {},
                        onMessageInfo = {},
                        onDeleteMessage = {}
                )
        }
}

@Preview(showBackground = true, backgroundColor = 0xFF111A22)
@Composable
private fun ChatTabDarkPreview() {
        NanoChatTheme(darkTheme = true) {
                ChatTab(
                        state =
                                ChatScreenState(
                                        messages =
                                                listOf(
                                                        ChatMessage(
                                                                1,
                                                                1,
                                                                ChatRole.USER,
                                                                "Explain quantum computing."
                                                        ),
                                                        ChatMessage(
                                                                2,
                                                                1,
                                                                ChatRole.ASSISTANT,
                                                                "Quantum computing uses quantum bits (qubits) which can exist in multiple states simultaneously, allowing for parallel processing of complex problems."
                                                        )
                                                )
                                ),
                        settingsState = SettingsScreenState(modelName = "Gemini Nano"),
                        onOpenSessions = {},
                        onSendMessage = {},
                        onStopGeneration = {},
                        onMessageDraftChange = {},
                        onCreateSession = {},
                        onRetryLast = {},
                        onInferenceModeChange = {},
                        onOpenModelGallery = {},
                        onTemperatureChange = {},
                        onTopPChange = {},
                        onContextLengthChange = {},
                        onThinkingEffortChange = {},
                        onAcceleratorChange = {},
                        onMessageInfo = {},
                        onDeleteMessage = {}
                )
        }
}

@Preview(showBackground = true)
@Composable
private fun ComposerPreview() {
        NanoChatTheme {
                Composer(
                        draft = "Hello, I have a question about...",
                        isSending = false,
                        notice = null,
                        onOpenControls = {},
                        onDraftChange = {},
                        onSend = {},
                        onStop = {},
                        onRetry = {}
                )
        }
}
