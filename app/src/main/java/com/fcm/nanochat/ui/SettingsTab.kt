package com.fcm.nanochat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fcm.nanochat.R
import com.fcm.nanochat.inference.RemoteConfigValidator
import com.fcm.nanochat.model.GeminiNanoStatusUi
import com.fcm.nanochat.model.HuggingFaceAccountUi
import com.fcm.nanochat.model.ModelGalleryScreenState
import com.fcm.nanochat.model.SettingsScreenState
import com.fcm.nanochat.models.compatibility.LocalModelCompatibilityState
import com.fcm.nanochat.models.registry.ModelInstallState
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

internal enum class SettingsSection {
        Home,
        AiConfiguration,
        ModelControls,
        Connection,
        HuggingFaceConnection,
        DataHistory
}

private enum class BehaviorPreset(
        val title: String,
        val description: String,
        val temperature: Double,
        val topP: Double
) {
        Precise(
                title = "Precise",
                description = "Sharper, deterministic responses with less variation.",
                temperature = 0.2,
                topP = 0.70
        ),
        Balanced(
                title = "Balanced",
                description = "Consistent answers with healthy creativity.",
                temperature = 0.7,
                topP = 0.90
        ),
        Creative(
                title = "Creative",
                description = "Higher variation for brainstorming and exploration.",
                temperature = 1.0,
                topP = 1.0
        )
}

private enum class BadgeTone {
        Positive,
        Neutral,
        Warning
}

private val SettingsScreenShape = RoundedCornerShape(24.dp)
private val SettingsRowShape = RoundedCornerShape(18.dp)
private val IconContainerShape = RoundedCornerShape(12.dp)
private val InputShape = RoundedCornerShape(16.dp)
private val ScreenHorizontalPadding = 20.dp
private val SectionSpacing = 28.dp
private val ItemSpacing = 16.dp
private val CardPadding = 20.dp

@Composable
internal fun SettingsHome(
        state: SettingsScreenState,
        modelState: ModelGalleryScreenState,
        modifier: Modifier = Modifier,
        onNavigate: (SettingsSection) -> Unit,
        onOpenModelLibrary: () -> Unit
) {
        val activePreset = closestBehaviorPreset(state.temperature, state.topP)
        val remoteConfigured =
                RemoteConfigValidator.missingFields(
                                baseUrl = state.baseUrl,
                                modelName = state.modelName,
                                apiKey = state.apiKey
                        )
                        .isEmpty()
        val onDeviceEnabled = state.geminiStatus.supported && state.geminiStatus.downloaded
        val activeLocalModel =
                modelState.models.firstOrNull { it.modelId == modelState.activeModelId }
        val localModelSubtitle =
                when {
                        activeLocalModel == null -> "No local model selected"
                        activeLocalModel.installState == ModelInstallState.INSTALLED &&
                                activeLocalModel.compatibility is
                                        LocalModelCompatibilityState.Ready -> {
                                "${activeLocalModel.displayName} · Ready"
                        }
                        else -> "${activeLocalModel.displayName} · Needs attention"
                }

        LazyColumn(
                modifier = modifier.padding(horizontal = ScreenHorizontalPadding, vertical = 16.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(SectionSpacing)
        ) {
                item {
                        SystemSummaryCard(
                                modelName =
                                        if (remoteConfigured) displayModelName(state.modelName)
                                        else "Not configured",
                                behaviorMode = activePreset.title,
                                providerStatus = connectionModeSummary(remoteConfigured),
                                onDeviceStatus =
                                        when {
                                                onDeviceEnabled -> "Gemini Nano enabled"
                                                state.geminiStatus.supported ->
                                                        "Gemini Nano available"
                                                else -> "Gemini Nano unavailable"
                                        },
                                onDeviceTone =
                                        when {
                                                onDeviceEnabled -> BadgeTone.Positive
                                                state.geminiStatus.supported -> BadgeTone.Neutral
                                                else -> BadgeTone.Warning
                                        }
                        )
                }

                item {
                        SettingsGroup(title = "AI Configuration") {
                                SettingsNavigationRow(
                                        icon = {
                                                Icon(Icons.Default.Tune, contentDescription = null)
                                        },
                                        title = "Remote AI configuration",
                                        subtitle = "Behavior profiles and connection setup",
                                        onClick = { onNavigate(SettingsSection.AiConfiguration) }
                                )
                                SettingsNavigationRow(
                                        icon = {
                                                Icon(
                                                        Icons.Default.Storage,
                                                        contentDescription = null
                                                )
                                        },
                                        title = "Local models",
                                        subtitle = localModelSubtitle,
                                        onClick = onOpenModelLibrary
                                )
                        }
                }

                item {
                        SettingsGroup(title = "Integrations") {
                                SettingsNavigationRow(
                                        icon = {
                                                Icon(
                                                        Icons.Default.VpnKey,
                                                        contentDescription = null
                                                )
                                        },
                                        title = "Hugging Face",
                                        subtitle = huggingFaceSubtitle(state),
                                        onClick = {
                                                onNavigate(SettingsSection.HuggingFaceConnection)
                                        }
                                )
                        }
                }

                item {
                        SettingsGroup(title = "Data") {
                                SettingsNavigationRow(
                                        icon = {
                                                Icon(
                                                        Icons.Outlined.History,
                                                        contentDescription = null
                                                )
                                        },
                                        title = "Usage and history",
                                        subtitle =
                                                "Sessions ${state.stats.sessionCount} · Sent ${state.stats.messagesSent} · Received ${state.stats.messagesReceived}",
                                        onClick = { onNavigate(SettingsSection.DataHistory) }
                                )
                        }
                }
        }
}

@Composable
internal fun AiConfigurationSettings(
        state: SettingsScreenState,
        modifier: Modifier = Modifier,
        onNavigate: (SettingsSection) -> Unit
) {
        val remoteConfigured =
                RemoteConfigValidator.missingFields(
                                baseUrl = state.baseUrl,
                                modelName = state.modelName,
                                apiKey = state.apiKey
                        )
                        .isEmpty()

        LazyColumn(
                modifier = modifier.padding(horizontal = ScreenHorizontalPadding, vertical = 16.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(SectionSpacing)
        ) {
                item {
                        SettingsPanel(
                                title = "Current configuration",
                                subtitle = "Overview of active model behavior and connection"
                        ) {
                                SummaryStatusRow(
                                        icon = {
                                                Icon(Icons.Default.Star, contentDescription = null)
                                        },
                                        label = "Current model",
                                        value =
                                                if (remoteConfigured)
                                                        displayModelName(state.modelName)
                                                else "Not configured"
                                )
                                SummaryStatusRow(
                                        icon = {
                                                Icon(Icons.Default.Tune, contentDescription = null)
                                        },
                                        label = "Temperature",
                                        value = String.format(Locale.US, "%.2f", state.temperature)
                                )
                                SummaryStatusRow(
                                        icon = {
                                                Icon(Icons.Default.Tune, contentDescription = null)
                                        },
                                        label = "Context length",
                                        value = formatTokens(state.contextLength)
                                )
                                SummaryStatusRow(
                                        icon = {
                                                Icon(Icons.Default.Link, contentDescription = null)
                                        },
                                        label = "Connection",
                                        value = connectionModeSummary(remoteConfigured)
                                )
                        }
                }

                item {
                        SettingsGroup(title = "Configuration panels") {
                                SettingsNavigationRow(
                                        icon = {
                                                Icon(Icons.Default.Tune, contentDescription = null)
                                        },
                                        title = "Edit model behavior",
                                        subtitle = "Behavior presets and advanced model tuning",
                                        onClick = { onNavigate(SettingsSection.ModelControls) }
                                )
                                SettingsNavigationRow(
                                        icon = {
                                                Icon(Icons.Default.Link, contentDescription = null)
                                        },
                                        title = "Edit connection settings",
                                        subtitle =
                                                "Connection status and advanced API configuration",
                                        onClick = { onNavigate(SettingsSection.Connection) }
                                )
                        }
                }
        }
}

@Composable
internal fun ConnectionSettings(
        state: SettingsScreenState,
        modifier: Modifier = Modifier,
        onBaseUrlChange: (String) -> Unit,
        onModelNameChange: (String) -> Unit,
        onApiKeyChange: (String) -> Unit,
        onRefreshGeminiStatus: () -> Unit,
        onDownloadGeminiNano: () -> Unit,
        onSaveSettings: () -> Unit = {}
) {
        val providerStatus = inferProviderStatus(state.baseUrl)
        val missingRemoteFields =
                RemoteConfigValidator.missingFields(
                        baseUrl = state.baseUrl,
                        modelName = state.modelName,
                        apiKey = state.apiKey
                )

        var advancedExpanded by rememberSaveable { mutableStateOf(false) }
        var apiKeyVisible by rememberSaveable { mutableStateOf(false) }

        LazyColumn(
                modifier = modifier.padding(horizontal = ScreenHorizontalPadding, vertical = 16.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(SectionSpacing)
        ) {
                item {
                        SettingsPanel(
                                title = "Connection status",
                                subtitle = "Current provider and on-device model readiness"
                        ) {
                                SummaryStatusRow(
                                        icon = {
                                                Icon(Icons.Default.Link, contentDescription = null)
                                        },
                                        label = "Remote provider",
                                        value = providerStatus,
                                        badge =
                                                if (missingRemoteFields.isEmpty()) {
                                                        BadgeData("Connected", BadgeTone.Positive)
                                                } else {
                                                        BadgeData("Needs setup", BadgeTone.Warning)
                                                }
                                )
                                SummaryStatusRow(
                                        icon = {
                                                Icon(
                                                        Icons.Outlined.CheckCircle,
                                                        contentDescription = null
                                                )
                                        },
                                        label = "On-device model",
                                        value = stringResource(R.string.gemini_nano_title),
                                        badge =
                                                when {
                                                        state.geminiStatus.supported &&
                                                                state.geminiStatus.downloaded ->
                                                                BadgeData(
                                                                        "Available",
                                                                        BadgeTone.Positive
                                                                )
                                                        state.geminiStatus.supported ->
                                                                BadgeData(
                                                                        "Supported",
                                                                        BadgeTone.Neutral
                                                                )
                                                        else ->
                                                                BadgeData(
                                                                        "Unavailable",
                                                                        BadgeTone.Warning
                                                                )
                                                }
                                )

                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                        StatusBadge(
                                                label =
                                                        if (state.geminiStatus.supported)
                                                                "Supported"
                                                        else "Unsupported",
                                                tone =
                                                        if (state.geminiStatus.supported)
                                                                BadgeTone.Positive
                                                        else BadgeTone.Warning
                                        )
                                        StatusBadge(
                                                label =
                                                        when {
                                                                state.geminiStatus.downloaded ->
                                                                        "Downloaded"
                                                                state.geminiStatus.downloading ->
                                                                        "Downloading"
                                                                else -> "Not downloaded"
                                                        },
                                                tone =
                                                        when {
                                                                state.geminiStatus.downloaded ->
                                                                        BadgeTone.Positive
                                                                state.geminiStatus.downloading ->
                                                                        BadgeTone.Neutral
                                                                else -> BadgeTone.Warning
                                                        }
                                        )
                                }

                                val sizeLabel =
                                        formatModelSize(
                                                status = state.geminiStatus,
                                                unavailableLabel =
                                                        stringResource(
                                                                R.string.gemini_size_unavailable
                                                        )
                                        )
                                if (sizeLabel.isNotBlank()) {
                                        Text(
                                                text = "Model size: $sizeLabel",
                                                style =
                                                        MaterialTheme.typography.bodySmall.copy(
                                                                fontSize = 13.sp
                                                        ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                }

                                if (state.geminiStatus.downloading) {
                                        val downloaded = state.geminiStatus.bytesDownloaded ?: 0L
                                        val total =
                                                state.geminiStatus.bytesToDownload
                                                        ?: state.geminiStatus
                                                                .lastKnownModelSizeBytes
                                        val progressText =
                                                if (total > 0) {
                                                        val pct =
                                                                (downloaded * 100f / total)
                                                                        .coerceIn(0f, 100f)
                                                        stringResource(
                                                                R.string.gemini_downloading_percent,
                                                                pct.toInt()
                                                        )
                                                } else {
                                                        stringResource(
                                                                R.string.gemini_downloading_generic
                                                        )
                                                }
                                        Text(
                                                text = progressText,
                                                style =
                                                        MaterialTheme.typography.bodySmall.copy(
                                                                fontSize = 13.sp
                                                        ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                }

                                if (!state.geminiStatus.downloaded &&
                                                state.geminiStatus.supported &&
                                                state.geminiStatus.downloadable &&
                                                !state.geminiStatus.downloading
                                ) {
                                        OutlinedButton(
                                                modifier = Modifier.fillMaxWidth(),
                                                onClick = onDownloadGeminiNano
                                        ) { Text(stringResource(R.string.gemini_download_gemini)) }
                                }

                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                ) {
                                        TextButton(onClick = onRefreshGeminiStatus) {
                                                Text(stringResource(R.string.gemini_refresh_status))
                                        }
                                }

                                state.geminiStatus.message?.takeIf { it.isNotBlank() }?.let {
                                        message ->
                                        Text(
                                                text = message,
                                                style =
                                                        MaterialTheme.typography.bodySmall.copy(
                                                                fontSize = 13.sp
                                                        ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                }
                        }
                }

                item {
                        ExpandablePanel(
                                title = "Advanced connection settings",
                                subtitle = "Base URL, model name, and API credentials",
                                expanded = advancedExpanded,
                                onToggle = { advancedExpanded = !advancedExpanded }
                        ) {
                                OutlinedTextField(
                                        value = state.baseUrl,
                                        onValueChange = onBaseUrlChange,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = InputShape,
                                        singleLine = true,
                                        label = { Text("Base URL") },
                                        supportingText = {
                                                Text(
                                                        "Used for remote inference API. Use the base path, the app appends /chat/completions."
                                                )
                                        }
                                )

                                OutlinedTextField(
                                        value = state.modelName,
                                        onValueChange = onModelNameChange,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = InputShape,
                                        singleLine = true,
                                        label = { Text("Model name") },
                                        supportingText = {
                                                Text("Model used for remote generation.")
                                        }
                                )

                                OutlinedTextField(
                                        value = state.apiKey,
                                        onValueChange = onApiKeyChange,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = InputShape,
                                        singleLine = true,
                                        label = { Text("API key") },
                                        visualTransformation =
                                                if (apiKeyVisible) {
                                                        VisualTransformation.None
                                                } else {
                                                        PasswordVisualTransformation()
                                                },
                                        trailingIcon = {
                                                IconButton(
                                                        onClick = { apiKeyVisible = !apiKeyVisible }
                                                ) {
                                                        Icon(
                                                                imageVector =
                                                                        if (apiKeyVisible) {
                                                                                Icons.Default
                                                                                        .VisibilityOff
                                                                        } else {
                                                                                Icons.Default
                                                                                        .Visibility
                                                                        },
                                                                contentDescription =
                                                                        if (apiKeyVisible) {
                                                                                "Hide API key"
                                                                        } else {
                                                                                "Show API key"
                                                                        }
                                                        )
                                                }
                                        },
                                        supportingText = { Text("Stored securely on this device.") }
                                )

                                state.saveNotice?.takeIf { it.isNotBlank() }?.let { notice ->
                                        Text(
                                                text = notice,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.primary
                                        )
                                }

                                Button(
                                        onClick = onSaveSettings,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = InputShape
                                ) {
                                        Icon(Icons.Default.Save, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Save connection settings")
                                }
                        }
                }
        }
}

@Composable
internal fun ModelControlsSettings(
        state: SettingsScreenState,
        modifier: Modifier = Modifier,
        onTemperatureChange: (Double) -> Unit,
        onTopPChange: (Double) -> Unit,
        onContextLengthChange: (Int) -> Unit,
        onThinkingEffortChange: (com.fcm.nanochat.data.ThinkingEffort) -> Unit = {},
        onAcceleratorChange: (com.fcm.nanochat.data.AcceleratorPreference) -> Unit = {},
        onSaveSettings: () -> Unit = {}
) {
        var advancedExpanded by rememberSaveable { mutableStateOf(false) }
        val activePreset = closestBehaviorPreset(state.temperature, state.topP)

        LazyColumn(
                modifier = modifier.padding(horizontal = ScreenHorizontalPadding, vertical = 16.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(SectionSpacing)
        ) {
                item {
                        SettingsPanel(
                                title = "AI behavior",
                                subtitle = "Choose how the assistant should respond"
                        ) {
                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                        BehaviorPreset.entries.forEach { preset ->
                                                FilterChip(
                                                        selected = activePreset == preset,
                                                        onClick = {
                                                                onTemperatureChange(
                                                                        preset.temperature
                                                                )
                                                                onTopPChange(preset.topP)
                                                        },
                                                        label = { Text(preset.title) },
                                                        colors =
                                                                FilterChipDefaults.filterChipColors(
                                                                        selectedContainerColor =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primaryContainer,
                                                                        selectedLabelColor =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onPrimaryContainer
                                                                )
                                                )
                                        }
                                }

                                Text(
                                        text = activePreset.description,
                                        style =
                                                MaterialTheme.typography.bodySmall.copy(
                                                        fontSize = 13.sp
                                                ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        }
                }

                item {
                        ExpandablePanel(
                                title = "Advanced model tuning",
                                subtitle = "Temperature, top-p, and context length",
                                expanded = advancedExpanded,
                                onToggle = { advancedExpanded = !advancedExpanded }
                        ) {
                                SliderSetting(
                                        label = "Temperature",
                                        valueText =
                                                String.format(Locale.US, "%.2f", state.temperature),
                                        description = "Controls creativity of responses.",
                                        value = state.temperature.toFloat(),
                                        valueRange = 0f..2f,
                                        onValueChange = { onTemperatureChange(it.toDouble()) }
                                )

                                SliderSetting(
                                        label = "Top-P",
                                        valueText = String.format(Locale.US, "%.2f", state.topP),
                                        description = "Limits token probability distribution.",
                                        value = state.topP.toFloat(),
                                        valueRange = 0f..1f,
                                        onValueChange = { onTopPChange(it.toDouble()) }
                                )

                                SliderSetting(
                                        label = "Context length",
                                        valueText = formatTokens(state.contextLength),
                                        description = "Maximum tokens used as context.",
                                        value = state.contextLength.toFloat(),
                                        valueRange = 512f..32768f,
                                        onValueChange = { onContextLengthChange(it.roundToInt()) }
                                )

                                state.saveNotice?.takeIf { it.isNotBlank() }?.let { notice ->
                                        Text(
                                                text = notice,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.primary
                                        )
                                }

                                Button(
                                        onClick = onSaveSettings,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = InputShape
                                ) {
                                        Icon(Icons.Default.Save, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Save model settings")
                                }
                        }
                }
        }
}

@Composable
internal fun HuggingFaceConnectionSettings(
        state: SettingsScreenState,
        modifier: Modifier = Modifier,
        onHuggingFaceTokenChange: (String) -> Unit,
        onValidateHuggingFaceToken: () -> Unit,
        onSaveSettings: () -> Unit
) {
        var manageTokenExpanded by rememberSaveable {
                mutableStateOf(state.huggingFaceToken.isBlank())
        }
        var tokenVisible by rememberSaveable { mutableStateOf(false) }

        LazyColumn(
                modifier = modifier.padding(horizontal = ScreenHorizontalPadding, vertical = 16.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(SectionSpacing)
        ) {
                item {
                        SettingsPanel(
                                title = "Account",
                                subtitle = "Connected integration status"
                        ) {
                                when {
                                        state.huggingFaceAccount.isValidating -> {
                                                Row(
                                                        horizontalArrangement =
                                                                Arrangement.spacedBy(10.dp),
                                                        verticalAlignment =
                                                                Alignment.CenterVertically
                                                ) {
                                                        CircularProgressIndicator(
                                                                modifier = Modifier.size(18.dp),
                                                                strokeWidth = 2.dp
                                                        )
                                                        Text(
                                                                text =
                                                                        "Refreshing account details...",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodyMedium
                                                        )
                                                }
                                        }
                                        state.huggingFaceAccount.isValid -> {
                                                AccountDetailsCard(
                                                        account = state.huggingFaceAccount,
                                                        onRefresh = onValidateHuggingFaceToken
                                                )
                                        }
                                        else -> {
                                                Column(
                                                        verticalArrangement =
                                                                Arrangement.spacedBy(10.dp)
                                                ) {
                                                        SummaryStatusRow(
                                                                icon = {
                                                                        Icon(
                                                                                Icons.Default
                                                                                        .VpnKey,
                                                                                contentDescription =
                                                                                        null
                                                                        )
                                                                },
                                                                label = "Connection",
                                                                value = "Not connected",
                                                                badge =
                                                                        BadgeData(
                                                                                "Disconnected",
                                                                                BadgeTone.Warning
                                                                        )
                                                        )
                                                        Text(
                                                                text =
                                                                        state.huggingFaceAccount
                                                                                .message
                                                                                ?: "Validate your token to fetch account details.",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodySmall.copy(
                                                                                fontSize = 13.sp
                                                                        ),
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
                                                        )
                                                }
                                        }
                                }
                        }
                }

                item {
                        ExpandablePanel(
                                title = "Manage token",
                                subtitle = "Access token and validation actions",
                                expanded = manageTokenExpanded,
                                onToggle = { manageTokenExpanded = !manageTokenExpanded }
                        ) {
                                OutlinedTextField(
                                        value = state.huggingFaceToken,
                                        onValueChange = onHuggingFaceTokenChange,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = InputShape,
                                        singleLine = true,
                                        label = { Text("Access token") },
                                        visualTransformation =
                                                if (tokenVisible) {
                                                        VisualTransformation.None
                                                } else {
                                                        PasswordVisualTransformation()
                                                },
                                        trailingIcon = {
                                                IconButton(
                                                        onClick = { tokenVisible = !tokenVisible }
                                                ) {
                                                        Icon(
                                                                imageVector =
                                                                        if (tokenVisible) {
                                                                                Icons.Default
                                                                                        .VisibilityOff
                                                                        } else {
                                                                                Icons.Default
                                                                                        .Visibility
                                                                        },
                                                                contentDescription =
                                                                        if (tokenVisible) {
                                                                                "Hide token"
                                                                        } else {
                                                                                "Show token"
                                                                        }
                                                        )
                                                }
                                        },
                                        supportingText = {
                                                Text(
                                                        "Use a token with read access for model downloads."
                                                )
                                        }
                                )

                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                        TextButton(
                                                enabled =
                                                        state.huggingFaceToken.isNotBlank() &&
                                                                !state.huggingFaceAccount
                                                                        .isValidating,
                                                onClick = onValidateHuggingFaceToken
                                        ) { Text("Validate token") }
                                        TextButton(
                                                enabled =
                                                        state.huggingFaceAccount.isValid &&
                                                                !state.huggingFaceAccount
                                                                        .isValidating,
                                                onClick = onValidateHuggingFaceToken
                                        ) {
                                                Icon(
                                                        Icons.Default.Refresh,
                                                        contentDescription = null
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Refresh account")
                                        }
                                }

                                state.saveNotice?.takeIf { it.isNotBlank() }?.let { notice ->
                                        Text(
                                                text = notice,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.primary
                                        )
                                }

                                Button(
                                        onClick = onSaveSettings,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = InputShape
                                ) {
                                        Icon(Icons.Default.Save, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Save")
                                }
                        }
                }
        }
}

@Composable
internal fun DataHistorySettings(
        state: SettingsScreenState,
        modifier: Modifier = Modifier,
        onRefreshStats: () -> Unit,
        onClearHistory: () -> Unit
) {
        val missingRemoteFields =
                RemoteConfigValidator.missingFields(
                        baseUrl = state.baseUrl,
                        modelName = state.modelName,
                        apiKey = state.apiKey
                )
        val remoteConfigured = missingRemoteFields.isEmpty()
        val nanoAvailable = state.geminiStatus.supported && state.geminiStatus.downloaded
        var confirmClearHistory by rememberSaveable { mutableStateOf(false) }

        LazyColumn(
                modifier = modifier.padding(horizontal = ScreenHorizontalPadding, vertical = 16.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(SectionSpacing)
        ) {
                item {
                        SettingsPanel(
                                title = "Usage",
                                subtitle = "Conversation activity",
                                trailing = {
                                        TextButton(onClick = onRefreshStats) { Text("Refresh") }
                                }
                        ) {
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                        UsageTile(
                                                icon = {
                                                        Icon(
                                                                Icons.Outlined.History,
                                                                contentDescription = null
                                                        )
                                                },
                                                label = "Sessions",
                                                value = state.stats.sessionCount,
                                                modifier = Modifier.weight(1f)
                                        )
                                        UsageTile(
                                                icon = {
                                                        Icon(
                                                                Icons.Default.ArrowUpward,
                                                                contentDescription = null
                                                        )
                                                },
                                                label = "Sent",
                                                value = state.stats.messagesSent,
                                                modifier = Modifier.weight(1f)
                                        )
                                        UsageTile(
                                                icon = {
                                                        Icon(
                                                                Icons.Default.ArrowDownward,
                                                                contentDescription = null
                                                        )
                                                },
                                                label = "Received",
                                                value = state.stats.messagesReceived,
                                                modifier = Modifier.weight(1f)
                                        )
                                }
                        }
                }

                item {
                        SettingsPanel(
                                title = "System health",
                                subtitle = "Backend and on-device readiness"
                        ) {
                                SummaryStatusRow(
                                        icon = {
                                                Icon(Icons.Default.Link, contentDescription = null)
                                        },
                                        label = "Remote API",
                                        value =
                                                if (remoteConfigured) "Configured"
                                                else "Setup required",
                                        badge =
                                                if (remoteConfigured) {
                                                        BadgeData("Connected", BadgeTone.Positive)
                                                } else {
                                                        BadgeData(
                                                                "Missing fields",
                                                                BadgeTone.Warning
                                                        )
                                                }
                                )
                                SummaryStatusRow(
                                        icon = {
                                                Icon(
                                                        Icons.Outlined.CheckCircle,
                                                        contentDescription = null
                                                )
                                        },
                                        label = "Gemini Nano",
                                        value =
                                                when {
                                                        nanoAvailable -> "Available"
                                                        state.geminiStatus.supported ->
                                                                "Not downloaded"
                                                        else -> "Unavailable"
                                                },
                                        badge =
                                                when {
                                                        nanoAvailable ->
                                                                BadgeData(
                                                                        "Ready",
                                                                        BadgeTone.Positive
                                                                )
                                                        state.geminiStatus.supported ->
                                                                BadgeData(
                                                                        "Download needed",
                                                                        BadgeTone.Neutral
                                                                )
                                                        else ->
                                                                BadgeData(
                                                                        "Unavailable",
                                                                        BadgeTone.Warning
                                                                )
                                                }
                                )
                        }
                }

                item {
                        SettingsPanel(
                                title = "Data",
                                subtitle = "Manage local conversation history"
                        ) {
                                state.clearNotice?.takeIf { it.isNotBlank() }?.let { notice ->
                                        Text(
                                                text = notice,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.primary
                                        )
                                }

                                Button(
                                        onClick = { confirmClearHistory = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = InputShape,
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor =
                                                                MaterialTheme.colorScheme
                                                                        .errorContainer,
                                                        contentColor =
                                                                MaterialTheme.colorScheme
                                                                        .onErrorContainer
                                                )
                                ) {
                                        Icon(Icons.Default.Delete, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Clear chat history")
                                }
                        }
                }
        }

        if (confirmClearHistory) {
                AlertDialog(
                        onDismissRequest = { confirmClearHistory = false },
                        title = { Text("Delete all conversations?") },
                        text = { Text("This action cannot be undone.") },
                        confirmButton = {
                                Button(
                                        onClick = {
                                                confirmClearHistory = false
                                                onClearHistory()
                                        },
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor =
                                                                MaterialTheme.colorScheme.error,
                                                        contentColor =
                                                                MaterialTheme.colorScheme.onError
                                                )
                                ) { Text("Delete") }
                        },
                        dismissButton = {
                                TextButton(onClick = { confirmClearHistory = false }) {
                                        Text("Cancel")
                                }
                        }
                )
        }
}

@Composable
private fun SystemSummaryCard(
        modelName: String,
        behaviorMode: String,
        providerStatus: String,
        onDeviceStatus: String,
        onDeviceTone: BadgeTone
) {
        SettingsPanel(
                title = "AI system overview",
                subtitle = "Quick status of model, behavior, and connectivity"
        ) {
                SummaryStatusRow(
                        icon = { Icon(Icons.Default.Star, contentDescription = null) },
                        label = "Active model",
                        value = modelName
                )
                SummaryStatusRow(
                        icon = { Icon(Icons.Default.Tune, contentDescription = null) },
                        label = "Behavior mode",
                        value = behaviorMode
                )
                SummaryStatusRow(
                        icon = { Icon(Icons.Default.Link, contentDescription = null) },
                        label = "Connection",
                        value = providerStatus
                )
                SummaryStatusRow(
                        icon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null) },
                        label = "On-device AI",
                        value = onDeviceStatus,
                        badge =
                                BadgeData(
                                        onDeviceStatus.substringAfterLast(' ').replaceFirstChar {
                                                it.titlecase()
                                        },
                                        onDeviceTone
                                )
                )
        }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                        text = title,
                        style =
                                MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                )
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
}

@Composable
private fun SettingsNavigationRow(
        icon: @Composable () -> Unit,
        title: String,
        subtitle: String,
        onClick: () -> Unit
) {
        Surface(shape = SettingsRowShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .clickable(onClick = onClick)
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                        Box(
                                modifier =
                                        Modifier.clip(IconContainerShape)
                                                .background(
                                                        MaterialTheme.colorScheme.surfaceContainer
                                                )
                                                .padding(8.dp),
                                contentAlignment = Alignment.Center
                        ) { icon() }
                        Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                                Text(
                                        text = title,
                                        style =
                                                MaterialTheme.typography.bodyLarge.copy(
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Medium
                                                )
                                )
                                Text(
                                        text = subtitle,
                                        style =
                                                MaterialTheme.typography.bodySmall.copy(
                                                        fontSize = 13.sp
                                                ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        }
                        Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                }
        }
}

@Composable
private fun SettingsPanel(
        title: String,
        subtitle: String? = null,
        trailing: (@Composable () -> Unit)? = null,
        content: @Composable ColumnScope.() -> Unit
) {
        Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = SettingsScreenShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
                Column(
                        modifier = Modifier.padding(CardPadding),
                        verticalArrangement = Arrangement.spacedBy(ItemSpacing)
                ) {
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
                                                text = title,
                                                style =
                                                        MaterialTheme.typography.titleMedium.copy(
                                                                fontSize = 16.sp,
                                                                fontWeight = FontWeight.Medium
                                                        )
                                        )
                                        if (!subtitle.isNullOrBlank()) {
                                                Text(
                                                        text = subtitle,
                                                        style =
                                                                MaterialTheme.typography.bodySmall
                                                                        .copy(fontSize = 13.sp),
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                                )
                                        }
                                }
                                trailing?.invoke()
                        }

                        content()
                }
        }
}

@Composable
private fun ExpandablePanel(
        title: String,
        subtitle: String,
        expanded: Boolean,
        onToggle: () -> Unit,
        content: @Composable ColumnScope.() -> Unit
) {
        Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = SettingsScreenShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
                Column(
                        modifier = Modifier.padding(CardPadding),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .clip(InputShape)
                                                .clickable(onClick = onToggle)
                                                .padding(horizontal = 2.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                                Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                        Text(
                                                text = title,
                                                style =
                                                        MaterialTheme.typography.titleMedium.copy(
                                                                fontSize = 16.sp,
                                                                fontWeight = FontWeight.Medium
                                                        )
                                        )
                                        Text(
                                                text = subtitle,
                                                style =
                                                        MaterialTheme.typography.bodySmall.copy(
                                                                fontSize = 13.sp
                                                        ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                }
                                Icon(
                                        imageVector = Icons.Default.ExpandMore,
                                        contentDescription = if (expanded) "Collapse" else "Expand",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        }

                        AnimatedVisibility(
                                visible = expanded,
                                enter =
                                        expandVertically(
                                                animationSpec = tween(durationMillis = 180)
                                        ) + fadeIn(animationSpec = tween(durationMillis = 160)),
                                exit =
                                        shrinkVertically(
                                                animationSpec = tween(durationMillis = 160)
                                        ) + fadeOut(animationSpec = tween(durationMillis = 120))
                        ) {
                                Column(
                                        verticalArrangement = Arrangement.spacedBy(ItemSpacing),
                                        content = content
                                )
                        }
                }
        }
}

@Composable
private fun SummaryStatusRow(
        icon: @Composable () -> Unit,
        label: String,
        value: String,
        badge: BadgeData? = null
) {
        Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
                Box(
                        modifier =
                                Modifier.clip(IconContainerShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                        .padding(8.dp),
                        contentAlignment = Alignment.Center
                ) { icon() }
                Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                        Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                                text = value,
                                style =
                                        MaterialTheme.typography.bodyLarge.copy(
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium
                                        ),
                                color = MaterialTheme.colorScheme.onSurface
                        )
                }
                if (badge != null) {
                        StatusBadge(label = badge.label, tone = badge.tone)
                }
        }
}

@Composable
private fun SliderSetting(
        label: String,
        valueText: String,
        description: String,
        value: Float,
        valueRange: ClosedFloatingPointRange<Float>,
        onValueChange: (Float) -> Unit
) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Text(
                                text = label,
                                style =
                                        MaterialTheme.typography.bodyLarge.copy(
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium
                                        )
                        )
                        Text(
                                text = valueText,
                                style =
                                        MaterialTheme.typography.bodyLarge.copy(
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium
                                        )
                        )
                }

                Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Slider(
                        value = value,
                        onValueChange = onValueChange,
                        valueRange = valueRange,
                        colors =
                                SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor =
                                                MaterialTheme.colorScheme.primary.copy(
                                                        alpha = 0.70f
                                                ),
                                        inactiveTrackColor =
                                                MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                )
        }
}

@Composable
private fun UsageTile(
        icon: @Composable () -> Unit,
        label: String,
        value: Long,
        modifier: Modifier = Modifier
) {
        val animatedValue by
                animateIntAsState(
                        targetValue = value.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        animationSpec = tween(durationMillis = 180),
                        label = "UsageTileNumber"
                )

        Surface(
                modifier = modifier,
                shape = SettingsRowShape,
                color = MaterialTheme.colorScheme.surface
        ) {
                Column(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                        Box(
                                modifier =
                                        Modifier.size(28.dp)
                                                .clip(IconContainerShape)
                                                .background(
                                                        MaterialTheme.colorScheme.surfaceContainer
                                                ),
                                contentAlignment = Alignment.Center
                        ) { icon() }
                        Text(
                                text = formatCount(animatedValue.toLong()),
                                style =
                                        MaterialTheme.typography.titleLarge.copy(
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.SemiBold
                                        )
                        )
                        Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                }
        }
}

@Composable
private fun StatusBadge(label: String, tone: BadgeTone) {
        val containerColor =
                when (tone) {
                        BadgeTone.Positive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        BadgeTone.Neutral -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
                        BadgeTone.Warning -> MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                }
        val contentColor =
                when (tone) {
                        BadgeTone.Positive -> MaterialTheme.colorScheme.primary
                        BadgeTone.Neutral -> MaterialTheme.colorScheme.onSurface
                        BadgeTone.Warning -> MaterialTheme.colorScheme.error
                }

        Surface(shape = CircleShape, color = containerColor) {
                Text(
                        text = label,
                        color = contentColor,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
        }
}

@Composable
private fun AccountDetailsCard(account: HuggingFaceAccountUi, onRefresh: () -> Unit) {
        val uriHandler = LocalUriHandler.current

        Surface(shape = SettingsRowShape, color = MaterialTheme.colorScheme.surface) {
                Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                                Row(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Avatar(
                                                avatarUrl = account.avatarUrl,
                                                name = account.fullName ?: account.username ?: "?"
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                Text(
                                                        text = account.fullName
                                                                        ?: account.username
                                                                                ?: "Unknown account",
                                                        style =
                                                                MaterialTheme.typography.bodyLarge
                                                                        .copy(
                                                                                fontSize = 15.sp,
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Medium
                                                                        )
                                                )
                                                account.username?.let { username ->
                                                        Text(
                                                                text = "@$username",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodySmall.copy(
                                                                                fontSize = 13.sp
                                                                        ),
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
                                                        )
                                                }
                                                account.email?.let { email ->
                                                        Text(
                                                                text = email,
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodySmall.copy(
                                                                                fontSize = 13.sp
                                                                        ),
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
                                                        )
                                                }
                                        }
                                }

                                IconButton(onClick = onRefresh) {
                                        Icon(
                                                Icons.Default.Refresh,
                                                contentDescription = "Refresh account"
                                        )
                                }
                        }

                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                                StatusBadge(label = "Connected", tone = BadgeTone.Positive)
                                account.tokenRole?.let {
                                        StatusBadge(
                                                label =
                                                        "Role: ${it.lowercase().replaceFirstChar { c -> c.titlecase() }}",
                                                tone = BadgeTone.Neutral
                                        )
                                }
                                account.tokenName?.let {
                                        StatusBadge(
                                                label = "Token: Active",
                                                tone = BadgeTone.Positive
                                        )
                                }
                        }

                        account.profileUrl?.let { profileUrl ->
                                TextButton(
                                        onClick = { runCatching { uriHandler.openUri(profileUrl) } }
                                ) { Text("View profile") }
                        }
                }
        }
}

@Composable
private fun Avatar(avatarUrl: String?, name: String) {
        val initials =
                name.trim()
                        .split(" ")
                        .filter { it.isNotBlank() }
                        .mapNotNull { it.firstOrNull()?.uppercase() }
                        .take(2)
                        .joinToString("")

        Box(
                modifier =
                        Modifier.size(52.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center
        ) {
                AsyncImage(
                        model = avatarUrl,
                        contentDescription = "Avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceContainer),
                        error = ColorPainter(MaterialTheme.colorScheme.surfaceContainer)
                )
                Text(
                        text = initials.ifBlank { "?" },
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        color = MaterialTheme.colorScheme.onSurface
                )
        }
}

private data class BadgeData(val label: String, val tone: BadgeTone)

private fun closestBehaviorPreset(temperature: Double, topP: Double): BehaviorPreset {
        return BehaviorPreset.entries.minByOrNull { preset ->
                abs(temperature - preset.temperature) + abs(topP - preset.topP)
        }
                ?: BehaviorPreset.Balanced
}

private fun displayModelName(modelName: String): String {
        val cleaned = modelName.replace('-', ' ').replace('_', ' ').trim()
        return cleaned.ifBlank { "Gemini 3 Flash" }
}

private fun inferProviderStatus(baseUrl: String): String {
        val normalized = baseUrl.lowercase()
        return when {
                normalized.isBlank() -> "Not configured"
                "generativelanguage.googleapis.com" in normalized -> "Gemini via GenAI"
                "aiplatform.googleapis.com" in normalized || "vertexai" in normalized ->
                        "Gemini via Vertex AI"
                "api.openai.com" in normalized -> "OpenAI"
                "anthropic.com" in normalized -> "Anthropic-compatible"
                else -> "OpenAI-compatible"
        }
}

private fun connectionModeSummary(remoteConfigured: Boolean): String {
        return if (remoteConfigured) "Remote API connected" else "Not configured"
}

private fun huggingFaceSubtitle(state: SettingsScreenState): String {
        return when {
                state.huggingFaceToken.isBlank() -> "Token not set"
                state.huggingFaceAccount.isValidating -> "Validating token"
                state.huggingFaceAccount.isValid -> {
                        val label =
                                state.huggingFaceAccount.fullName
                                        ?: state.huggingFaceAccount.username
                                                ?: state.huggingFaceAccount.email
                        if (label.isNullOrBlank()) "Connected" else "Connected as $label"
                }
                !state.huggingFaceAccount.message.isNullOrBlank() -> "Validation failed"
                else -> "Token saved"
        }
}

private fun formatTokens(value: Int): String {
        return "${NumberFormat.getIntegerInstance().format(value)} tokens"
}

private fun formatCount(value: Long): String {
        return NumberFormat.getIntegerInstance().format(value)
}

private fun formatModelSize(status: GeminiNanoStatusUi, unavailableLabel: String): String {
        val size =
                status.bytesToDownload?.takeIf { it > 0 }
                        ?: status.lastKnownModelSizeBytes.takeIf { it > 0 }
        return size?.let { humanReadableByteCount(it) } ?: unavailableLabel
}

private fun humanReadableByteCount(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val unit = 1000.0
        val exp = (ln(bytes.toDouble()) / ln(unit)).toInt()
        val prefix = "kMGTPE"[exp - 1]
        val value = bytes / unit.pow(exp.toDouble())
        return "%.1f %sB".format(value, prefix)
}
