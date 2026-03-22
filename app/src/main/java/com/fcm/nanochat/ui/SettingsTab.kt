package com.fcm.nanochat.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.compose.material.icons.automirrored.outlined.Send
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.fcm.nanochat.R
import com.fcm.nanochat.inference.RemoteConfigValidator
import com.fcm.nanochat.model.GeminiNanoStatusUi
import com.fcm.nanochat.model.ModelGalleryScreenState
import com.fcm.nanochat.model.SettingsScreenState
import com.fcm.nanochat.models.compatibility.LocalModelCompatibilityState
import com.fcm.nanochat.models.registry.ModelInstallState
import com.fcm.nanochat.ui.theme.NanoChatTheme
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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
    DataHistory,
    AppInfo,
    AboutDeveloper,
    OpenSourceLicenses
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
private const val SupportEmail = "strikecode.fcm@gmail.com"
private const val PrivacyPolicyUrl =
    "https://docs.google.com/document/d/1RsLlKxclAe3LfuaDMTz6CjWW3-4P-i-P_X6IJHjvtb0/"
private const val GitHubRepoUrl = "https://github.com/adiagarwalrock/NanoChat"
private const val HuggingFaceModelsUrl = "https://huggingface.co/adiagarwal/nanochat-models"
private const val AppInfoTitle = "App info"
private const val AppInfoSubtitle = "Version, device diagnostics, and debug report"
private const val AppInfoSummary =
    "Helpful app, device, and runtime details for support and debugging."
private const val DeviceTitle = "Device"
private const val DeviceSubtitle = "OS, hardware, and notification state."
private const val RuntimeTitle = "Runtime"
private const val RuntimeSubtitle = "Current AI configuration and app activity."
private const val AppVersionLabel = "App version"
private const val BuildTypeLabel = "Build type"
private const val PackageNameLabel = "Package name"
private const val LastUpdatedLabel = "Last updated"
private const val AndroidVersionLabel = "Android version"
private const val SecurityPatchLabel = "Security patch"
private const val DeviceNameLabel = "Device"
private const val PrimaryAbiLabel = "Primary ABI"
private const val LocaleLabel = "Locale"
private const val NotificationsLabel = "Notifications"
private const val InstalledOnLabel = "Installed on"
private const val RemoteProviderLabel = "Remote provider"
private const val ModelNameLabel = "Model name"
private const val GeminiStatusLabel = "Gemini Nano"
private const val UsageLabel = "Usage"
private const val EnabledLabel = "Enabled"
private const val DisabledLabel = "Disabled"
private const val ConfiguredLabel = "Configured"
private const val NeedsSetupLabel = "Needs setup"
private const val NotSetLabel = "Not set"
private const val ReadyLabel = "Ready"
private const val DownloadNeededLabel = "Download needed"
private const val UnavailableLabel = "Unavailable"
private const val DebugReportTitle = "Send debug report"
private const val DebugReportSubtitle =
    "Open your email app with a prefilled diagnostics report. Review it before sending."
private const val SendDebugReportLabel = "Send debug report"
private const val DebugEmailIntro = "Issue summary:"
private const val DebugEmailSteps = "Steps to reproduce:"
private const val DebugEmailExpected = "Expected result:"
private const val DebugEmailActual = "Actual result:"
private const val DebugEmailAppHeading = "App info"
private const val DebugEmailDeviceHeading = "Device info"
private const val DebugEmailRuntimeHeading = "Runtime info"

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
                onDeviceBadge =
                    when {
                        onDeviceEnabled -> "Enabled"
                        state.geminiStatus.supported -> "Available"
                        else -> "Unavailable"
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

        item {
            SettingsGroup(title = "About") {
                SettingsNavigationRow(
                    icon = {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null
                        )
                    },
                    title = AppInfoTitle,
                    subtitle = AppInfoSubtitle,
                    onClick = { onNavigate(SettingsSection.AppInfo) }
                )
                SettingsNavigationRow(
                    icon = {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null
                        )
                    },
                    title = stringResource(R.string.about_developer),
                    subtitle = "Contact details and privacy information",
                    onClick = { onNavigate(SettingsSection.AboutDeveloper) }
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
                    label = stringResource(id = R.string.temperature_label),
                    value = String.format(Locale.US, "%.2f", state.temperature)
                )
                SummaryStatusRow(
                    icon = {
                        Icon(Icons.Default.Tune, contentDescription = null)
                    },
                    label = stringResource(id = R.string.context_length_label),
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
                        Modifier
                            .fillMaxWidth()
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

                state.geminiStatus.message?.takeIf { it.isNotBlank() }?.let { message ->
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
                        Modifier
                            .fillMaxWidth()
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
                title = stringResource(id = R.string.model_controls_title),
                subtitle = stringResource(id = R.string.model_controls_subtitle),
                expanded = advancedExpanded,
                onToggle = { advancedExpanded = !advancedExpanded }
            ) {
                SliderSetting(
                    label = stringResource(id = R.string.temperature_label),
                    valueText =
                        String.format(Locale.US, "%.2f", state.temperature),
                    description =
                        stringResource(
                            id = R.string.temperature_description
                        ),
                    value = state.temperature.toFloat(),
                    valueRange = 0f..2f,
                    onValueChange = { onTemperatureChange(it.toDouble()) }
                )

                SliderSetting(
                    label = stringResource(id = R.string.top_p_label),
                    valueText = String.format(Locale.US, "%.2f", state.topP),
                    description =
                        stringResource(id = R.string.top_p_description),
                    value = state.topP.toFloat(),
                    valueRange = 0f..1f,
                    onValueChange = { onTopPChange(it.toDouble()) }
                )

                SliderSetting(
                    label = stringResource(id = R.string.context_length_label),
                    valueText = formatTokens(state.contextLength),
                    description =
                        stringResource(
                            id = R.string.context_length_description
                        ),
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
    onDeviceBadge: String,
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
            value = "Gemini Nano",
            badge = BadgeData(onDeviceBadge, onDeviceTone)
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
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier =
                    Modifier
                        .clip(IconContainerShape)
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
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    SettingsDetailRow(
        title = title,
        subtitle = subtitle,
        modifier = Modifier.clickable(onClick = onClick),
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}

@Composable
private fun SettingsStaticRow(
    title: String,
    subtitle: String
) {
    SettingsDetailRow(
        title = title,
        subtitle = subtitle
    )
}

@Composable
private fun SettingsDetailRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Surface(shape = SettingsRowShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
            trailing?.invoke()
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
                    Modifier
                        .fillMaxWidth()
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
                Modifier
                    .clip(IconContainerShape)
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
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
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

private data class BadgeData(val label: String, val tone: BadgeTone)

private data class DebugReport(
    val appVersion: String,
    val buildType: String,
    val packageName: String,
    val lastUpdated: String,
    val installedOn: String,
    val androidVersion: String,
    val securityPatch: String,
    val deviceName: String,
    val primaryAbi: String,
    val locale: String,
    val notifications: String,
    val remoteProvider: String,
    val modelName: String,
    val geminiStatus: String,
    val usageSummary: String,
    val emailSubject: String,
    val emailBody: String,
    val emailIntent: Intent
)

private fun buildDebugReport(context: Context, state: SettingsScreenState): DebugReport {
    val packageInfo = context.packageInfoOrNull()
    val versionCode =
        packageInfo?.longVersionCode?.toString() ?: context.getString(R.string.unknown_value)
    val versionName = packageInfo?.versionName ?: context.getString(R.string.unknown_value)
    val buildType = debugBuildType(context)
    val installedOn =
        packageInfo?.firstInstallTime?.let(::formatTimestamp)
            ?: context.getString(R.string.unknown_value)
    val lastUpdated =
        packageInfo?.lastUpdateTime?.let(::formatTimestamp)
            ?: context.getString(R.string.unknown_value)
    val securityPatch =
        Build.VERSION.SECURITY_PATCH.ifBlank { context.getString(R.string.unknown_value) }
    val locale = Locale.getDefault().toLanguageTag()
    val remoteConfigured =
        RemoteConfigValidator.missingFields(
            baseUrl = state.baseUrl,
            modelName = state.modelName,
            apiKey = state.apiKey
        ).isEmpty()
    val notificationStatus =
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            EnabledLabel
        } else {
            DisabledLabel
        }
    val geminiStatus = geminiStatusLabel(state)
    val modelName = state.modelName.ifBlank { NotSetLabel }
    val usageSummary =
        "Sessions ${state.stats.sessionCount} · Sent ${state.stats.messagesSent} · Received ${state.stats.messagesReceived}"
    val remoteProvider =
        "${inferProviderStatus(state.baseUrl)} · ${if (remoteConfigured) ConfiguredLabel else NeedsSetupLabel}"
    val temperature = String.format(Locale.US, "%.2f", state.temperature)
    val topP = String.format(Locale.US, "%.2f", state.topP)
    val contextLength = formatTokens(state.contextLength)
    val thinkingEffort =
        state.thinkingEffort.name.lowercase().replaceFirstChar { it.titlecase() }
    val accelerator = state.acceleratorPreference.name.uppercase(Locale.US)
    val emailSubject = "NanoChat Debug Report - $versionName - ${Build.MODEL}"
    val emailBody =
        buildString {
            appendLine(DebugEmailIntro)
            appendLine()
            appendLine(DebugEmailSteps)
            appendLine()
            appendLine(DebugEmailExpected)
            appendLine()
            appendLine(DebugEmailActual)
            appendLine()
            appendLine(DebugEmailAppHeading)
            appendLine("$AppVersionLabel: $versionName ($versionCode)")
            appendLine("$BuildTypeLabel: $buildType")
            appendLine("$PackageNameLabel: ${context.packageName}")
            appendLine("$InstalledOnLabel: $installedOn")
            appendLine("$LastUpdatedLabel: $lastUpdated")
            appendLine()
            appendLine(DebugEmailDeviceHeading)
            appendLine("$AndroidVersionLabel: Android ${Build.VERSION.RELEASE_OR_CODENAME} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("$SecurityPatchLabel: $securityPatch")
            appendLine("$DeviceNameLabel: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine(
                "$PrimaryAbiLabel: ${
                    Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
                        .ifBlank { context.getString(R.string.unknown_value) }
                }"
            )
            appendLine("$LocaleLabel: $locale")
            appendLine("$NotificationsLabel: $notificationStatus")
            appendLine()
            appendLine(DebugEmailRuntimeHeading)
            appendLine("$RemoteProviderLabel: $remoteProvider")
            appendLine("$ModelNameLabel: $modelName")
            appendLine("${context.getString(R.string.temperature_label)}: $temperature")
            appendLine("${context.getString(R.string.top_p_label)}: $topP")
            appendLine("${context.getString(R.string.context_length_label)}: $contextLength")
            appendLine("${context.getString(R.string.thinking_effort_label)}: $thinkingEffort")
            appendLine("${context.getString(R.string.accelerator_label)}: $accelerator")
            appendLine("$GeminiStatusLabel: $geminiStatus")
            appendLine("$UsageLabel: $usageSummary")
        }.trim()
    val emailIntent = buildEmailIntent(emailSubject, emailBody)

    return DebugReport(
        appVersion = "$versionName ($versionCode)",
        buildType = buildType,
        packageName = context.packageName,
        lastUpdated = lastUpdated,
        installedOn = installedOn,
        androidVersion = "Android ${Build.VERSION.RELEASE_OR_CODENAME} (SDK ${Build.VERSION.SDK_INT})",
        securityPatch = securityPatch,
        deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
        primaryAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty().ifBlank {
            context.getString(R.string.unknown_value)
        },
        locale = locale,
        notifications = notificationStatus,
        remoteProvider = remoteProvider,
        modelName = modelName,
        geminiStatus = geminiStatus,
        usageSummary = usageSummary,
        emailSubject = emailSubject,
        emailBody = emailBody,
        emailIntent = emailIntent
    )
}

private fun geminiStatusLabel(state: SettingsScreenState): String {
    return when {
        state.geminiStatus.downloaded -> ReadyLabel
        state.geminiStatus.downloading -> "Downloading"
        state.geminiStatus.supported -> DownloadNeededLabel
        else -> UnavailableLabel
    }
}

private fun buildEmailIntent(subject: String, body: String): Intent {
    val uri =
        Uri.Builder()
            .scheme("mailto")
            .opaquePart(SupportEmail)
            .appendQueryParameter("subject", subject)
            .appendQueryParameter("body", body)
            .build()
    return Intent(Intent.ACTION_SENDTO, uri).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(SupportEmail))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
}

private fun Context.startActivitySafely(intent: Intent) {
    runCatching {
        startActivity(intent)
    }
}

private fun Context.openExternalLink(url: String) {
    startActivitySafely(Intent(Intent.ACTION_VIEW, url.toUri()))
}

private fun debugBuildType(context: Context): String {
    return if ((context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
        "debug"
    } else {
        "release"
    }
}

@Suppress("DEPRECATION")
private fun Context.packageInfoOrNull() =
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            packageManager.getPackageInfo(packageName, 0)
        }
    }.getOrNull()

private fun formatTimestamp(epochMs: Long): String {
    return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
}

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

@Composable
internal fun AppInfoSettings(
    state: SettingsScreenState,
    modifier: Modifier = Modifier,
    onNavigate: (SettingsSection) -> Unit
) {
    val context = LocalContext.current
    val debugReport = remember(state, context) { buildDebugReport(context, state) }

    LazyColumn(
        modifier = modifier.padding(horizontal = ScreenHorizontalPadding, vertical = 16.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing)
    ) {
        item {
            SettingsPanel(
                title = AppInfoTitle,
                subtitle = AppInfoSummary
            ) {
                SummaryStatusRow(
                    icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                    label = AppVersionLabel,
                    value = debugReport.appVersion
                )
                SummaryStatusRow(
                    icon = { Icon(Icons.Outlined.Code, contentDescription = null) },
                    label = BuildTypeLabel,
                    value = debugReport.buildType
                )
                SummaryStatusRow(
                    icon = { Icon(Icons.Default.Storage, contentDescription = null) },
                    label = PackageNameLabel,
                    value = debugReport.packageName
                )
                SummaryStatusRow(
                    icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                    label = LastUpdatedLabel,
                    value = debugReport.lastUpdated
                )
            }
        }

        item {
            SettingsPanel(
                title = DeviceTitle,
                subtitle = DeviceSubtitle
            ) {
                SummaryStatusRow(
                    icon = { Icon(Icons.Default.Storage, contentDescription = null) },
                    label = AndroidVersionLabel,
                    value = debugReport.androidVersion
                )
                SummaryStatusRow(
                    icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                    label = DeviceNameLabel,
                    value = debugReport.deviceName
                )
                SummaryStatusRow(
                    icon = { Icon(Icons.Outlined.Code, contentDescription = null) },
                    label = PrimaryAbiLabel,
                    value = debugReport.primaryAbi
                )
                SummaryStatusRow(
                    icon = { Icon(Icons.Default.Link, contentDescription = null) },
                    label = NotificationsLabel,
                    value = debugReport.notifications
                )
            }
        }

        item {
            SettingsPanel(
                title = RuntimeTitle,
                subtitle = RuntimeSubtitle
            ) {
                SummaryStatusRow(
                    icon = { Icon(Icons.Default.Link, contentDescription = null) },
                    label = RemoteProviderLabel,
                    value = debugReport.remoteProvider
                )
                SummaryStatusRow(
                    icon = { Icon(Icons.Default.Star, contentDescription = null) },
                    label = ModelNameLabel,
                    value = debugReport.modelName
                )
                SummaryStatusRow(
                    icon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null) },
                    label = GeminiStatusLabel,
                    value = debugReport.geminiStatus
                )
                SummaryStatusRow(
                    icon = { Icon(Icons.Outlined.History, contentDescription = null) },
                    label = UsageLabel,
                    value = debugReport.usageSummary
                )
            }
        }

        item {
            SettingsPanel(
                title = DebugReportTitle,
                subtitle = DebugReportSubtitle
            ) {
                Button(
                    onClick = { context.startActivitySafely(debugReport.emailIntent) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = InputShape
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(SendDebugReportLabel)
                }
            }
        }

        item {
            SettingsGroup(title = "Resources") {
                SettingsNavigationRow(
                    icon = {
                        Icon(
                            Icons.Outlined.Code,
                            contentDescription = null
                        )
                    },
                    title = stringResource(R.string.open_source_licenses),
                    subtitle = "Libraries and tools powering NanoChat",
                    onClick = { onNavigate(SettingsSection.OpenSourceLicenses) }
                )
            }
        }
    }
}

@Composable
internal fun AboutDeveloperSettings(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier.padding(horizontal = ScreenHorizontalPadding, vertical = 16.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing)
    ) {
        item {
            SettingsGroup(title = stringResource(R.string.about_developer_title)) {
                SettingsStaticRow(
                    title = "Aditya Agarwal",
                    subtitle = "Lead Developer"
                )
                SettingsStaticRow(
                    title = "Nilesh Kumar Mandal",
                    subtitle = "Lead Developer"
                )
                SettingsActionRow(
                    title = "Contact Us",
                    subtitle = SupportEmail,
                    onClick = {
                        context.startActivitySafely(
                            buildEmailIntent(subject = "NanoChat Feedback", body = "")
                        )
                    }
                )
            }
        }

        item {
            SettingsGroup(title = "Project") {
                SettingsStaticRow(
                    title = "NanoChat is open source",
                    subtitle = "We appreciate every bug report, suggestion, contribution, and bit of help from the community."
                )
                SettingsActionRow(
                    title = "GitHub repository",
                    subtitle = "Source code and project repository for NanoChat",
                    onClick = { context.openExternalLink(GitHubRepoUrl) }
                )
                SettingsActionRow(
                    title = "Model hub",
                    subtitle = "NanoChat models on Hugging Face",
                    onClick = { context.openExternalLink(HuggingFaceModelsUrl) }
                )
            }
        }

        item {
            SettingsGroup(title = "Legal") {
                SettingsActionRow(
                    title = stringResource(R.string.privacy_policy_title),
                    subtitle = "Read our privacy policy online",
                    onClick = { context.openExternalLink(PrivacyPolicyUrl) }
                )
            }
        }
    }
}

@Composable
internal fun OpenSourceLicensesSettings(
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = ScreenHorizontalPadding, vertical = 16.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing)
    ) {
        item {
            SettingsGroup(title = "Dependencies") {
                SettingsStaticRow(title = "Jetpack Compose", subtitle = "Android UI toolkit")
                SettingsStaticRow(title = "Material 3", subtitle = "Design system & components")
                SettingsStaticRow(
                    title = "Kotlin Coroutines",
                    subtitle = "Asynchronous programming"
                )
                SettingsStaticRow(title = "Room", subtitle = "Local SQLite persistence")
                SettingsStaticRow(title = "DataStore", subtitle = "Typed preferences storage")
                SettingsStaticRow(title = "Hilt", subtitle = "Dependency injection")
                SettingsStaticRow(title = "OkHttp", subtitle = "HTTP client and SSE processing")
                SettingsStaticRow(title = "Coil", subtitle = "Image loading")
                SettingsStaticRow(title = "Markwon", subtitle = "Markdown rendering")
                SettingsStaticRow(title = "AICore", subtitle = "Android on-device AI integration")
                SettingsStaticRow(
                    title = "LiteRT-LM",
                    subtitle = "Local LLM runtime for downloaded models"
                )
                SettingsStaticRow(title = "Firebase", subtitle = "App distribution and analytics")
                SettingsStaticRow(
                    title = "kotlinx.serialization",
                    subtitle = "JSON parsing and serialization"
                )
                SettingsStaticRow(
                    title = "EncryptedSharedPreferences",
                    subtitle = "Secure secret storage"
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsHomePreview() {
    NanoChatTheme {
        SettingsHome(
            state = SettingsScreenState(modelName = "Gemini Nano"),
            modelState = ModelGalleryScreenState(),
            onNavigate = {},
            onOpenModelLibrary = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AiConfigurationSettingsPreview() {
    NanoChatTheme { AiConfigurationSettings(state = SettingsScreenState(), onNavigate = {}) }
}

@Preview(showBackground = true)
@Composable
private fun ConnectionSettingsPreview() {
    NanoChatTheme {
        ConnectionSettings(
            state = SettingsScreenState(),
            onBaseUrlChange = {},
            onModelNameChange = {},
            onApiKeyChange = {},
            onRefreshGeminiStatus = {},
            onDownloadGeminiNano = {},
            onSaveSettings = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ModelControlsSettingsPreview() {
    NanoChatTheme {
        ModelControlsSettings(
            state = SettingsScreenState(),
            onTemperatureChange = {},
            onTopPChange = {},
            onContextLengthChange = {},
            onSaveSettings = {}
        )
    }
}
