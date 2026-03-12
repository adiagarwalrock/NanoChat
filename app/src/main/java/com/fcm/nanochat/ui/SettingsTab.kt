package com.fcm.nanochat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fcm.nanochat.R
import com.fcm.nanochat.inference.RemoteConfigValidator
import com.fcm.nanochat.model.GeminiNanoStatusUi
import com.fcm.nanochat.model.SettingsScreenState
import kotlin.math.ln
import kotlin.math.pow

internal enum class SettingsSection { Home, Connection, ModelControls, HuggingFaceConnection, DataHistory }

@Composable
internal fun SettingsHome(
    state: SettingsScreenState,
    modifier: Modifier = Modifier,
    onNavigate: (SettingsSection) -> Unit
) {
    val providerStatus = inferProviderStatus(state.baseUrl)
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingRow(
                    icon = { Icon(Icons.Default.Link, contentDescription = null) },
                    title = "Connection",
                    subtitle = providerStatus,
                    onClick = { onNavigate(SettingsSection.Connection) }
                )
                SettingRow(
                    icon = { Icon(Icons.Default.Tune, contentDescription = null) },
                    title = "Model controls",
                    subtitle = "Temperature, Top P, Context length",
                    onClick = { onNavigate(SettingsSection.ModelControls) }
                )
                SettingRow(
                    icon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                    title = "Hugging Face",
                    subtitle = huggingFaceSubtitle(state),
                    onClick = { onNavigate(SettingsSection.HuggingFaceConnection) }
                )
                SettingRow(
                    icon = { Icon(Icons.Outlined.History, contentDescription = null) },
                    title = "Data & history",
                    subtitle = "Sessions ${state.stats.sessionCount} · Sent ${state.stats.messagesSent} · Received ${state.stats.messagesReceived}",
                    onClick = { onNavigate(SettingsSection.DataHistory) }
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
    onDownloadGeminiNano: () -> Unit
) {
    val providerStatus = inferProviderStatus(state.baseUrl)
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard(
            title = stringResource(R.string.gemini_nano_title),
            subtitle = stringResource(R.string.gemini_nano_subtitle)
        ) {
            GeminiStatusBlock(
                status = state.geminiStatus,
                onRefresh = onRefreshGeminiStatus,
                onDownload = onDownloadGeminiNano
            )
        }
        SectionCard(title = "Provider status", subtitle = providerStatus) {}
        SectionCard(title = "Connection") {
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = onBaseUrlChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                label = { Text("Base URL") },
                supportingText = { Text("Use base path; app auto-appends /chat/completions") }
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.modelName,
                onValueChange = onModelNameChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Model name") }
            )
            Spacer(modifier = Modifier.height(12.dp))
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
    }
}

@Composable
private fun GeminiStatusBlock(
    status: GeminiNanoStatusUi,
    onRefresh: () -> Unit,
    onDownload: () -> Unit
) {
    val supportedIcon = if (status.supported) Icons.Outlined.CheckCircle else Icons.Outlined.Warning
    val supportedTint =
        if (status.supported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val downloadedIcon = when {
        status.downloaded -> Icons.Outlined.CheckCircle
        status.downloading -> Icons.Outlined.HourglassEmpty
        else -> Icons.Outlined.Warning
    }
    val downloadedTint = when {
        status.downloaded -> MaterialTheme.colorScheme.primary
        status.downloading -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.error
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.gemini_device_supported),
                style = MaterialTheme.typography.bodyMedium
            )
            Icon(supportedIcon, contentDescription = null, tint = supportedTint)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.gemini_model_downloaded),
                    style = MaterialTheme.typography.bodyMedium
                )
                val sizeLabel =
                    formatModelSize(status, stringResource(R.string.gemini_size_unavailable))
                if (sizeLabel.isNotBlank()) {
                    Text(
                        sizeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(downloadedIcon, contentDescription = null, tint = downloadedTint)
        }

        if (!status.downloaded && status.supported) {
            if (status.downloading) {
                val downloaded = status.bytesDownloaded ?: 0
                val total = status.bytesToDownload ?: status.lastKnownModelSizeBytes
                val progressText = if (total > 0) {
                    val pct = (downloaded * 100f / total).coerceIn(0f, 100f)
                    stringResource(R.string.gemini_downloading_percent, pct.toInt())
                } else {
                    stringResource(R.string.gemini_downloading_generic)
                }
                Text(
                    progressText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (status.downloadable) {
                Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.gemini_download_gemini))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onRefresh) {
                Text(stringResource(R.string.gemini_refresh_status))
            }
        }

        if (!status.message.isNullOrBlank()) {
            Text(
                status.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun ModelControlsSettings(
    state: SettingsScreenState,
    modifier: Modifier = Modifier,
    onTemperatureChange: (Double) -> Unit,
    onTopPChange: (Double) -> Unit,
    onContextLengthChange: (Int) -> Unit
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard(title = "Model controls") {
            ParameterSliders(
                temperature = state.temperature,
                topP = state.topP,
                contextLength = state.contextLength,
                onTemperatureChange = onTemperatureChange,
                onTopPChange = onTopPChange,
                onContextLengthChange = onContextLengthChange
            )
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
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard(title = "Authentication") {
            OutlinedTextField(
                value = state.huggingFaceToken,
                onValueChange = onHuggingFaceTokenChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Access token") },
                supportingText = { Text("For better compatibility, provide a Read key.") }
            )

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    enabled = state.huggingFaceToken.isNotBlank() && !state.huggingFaceAccount.isValidating,
                    onClick = onValidateHuggingFaceToken
                ) {
                    Text("Validate token")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onValidateHuggingFaceToken,
                        enabled = !state.huggingFaceAccount.isValidating
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Refresh")
                    }
                    Button(onClick = onSaveSettings) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when {
                state.huggingFaceAccount.isValidating -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(18.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            "Validating Hugging Face token…",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                state.huggingFaceAccount.isValid -> {
                    AccountDetailsCard(state.huggingFaceAccount, onValidateHuggingFaceToken)
                }

                !state.huggingFaceAccount.message.isNullOrBlank() -> {
                    Text(
                        state.huggingFaceAccount.message.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                else -> {
                    Text(
                        "No account details yet. Validate your token to see profile info.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!state.saveNotice.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    state.saveNotice.orEmpty(),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
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
    val missingRemoteFields = RemoteConfigValidator.missingFields(
        baseUrl = state.baseUrl,
        modelName = state.modelName,
        apiKey = state.apiKey
    )

    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatsCard(state = state, onRefresh = onRefreshStats)
        SectionCard(title = "Backend readiness") {
            val body = if (missingRemoteFields.isEmpty()) {
                "Remote is configured. Nano still requires Gemini Nano enabled in Developer Options on a supported device."
            } else {
                val labels = missingRemoteFields.joinToString { it.displayName }
                "Remote is missing: $labels. Nano requires Gemini Nano enabled in Developer Options on a supported device."
            }
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
        SectionCard(title = "History & data") {
            if (!state.clearNotice.isNullOrBlank()) {
                Text(state.clearNotice.orEmpty(), color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
            }
            Button(onClick = onClearHistory, modifier = Modifier.fillMaxWidth()) {
                Text("Clear all chat history")
            }
        }
    }
}

@Composable
private fun AccountDetailsCard(
    account: com.fcm.nanochat.model.HuggingFaceAccountUi,
    onRefresh: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(account.avatarUrl, account.fullName ?: account.username ?: "?")
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val displayName = account.fullName ?: account.username ?: "Unknown user"
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (account.isPro) {
                                AssistChip(
                                    onClick = {},
                                    enabled = false,
                                    label = { Text("PRO", fontSize = 12.sp) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        labelColor = MaterialTheme.colorScheme.onPrimary,
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                        account.username?.let { user ->
                            Text(
                                text = "@$user",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        account.email?.let { email ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(email, style = MaterialTheme.typography.bodyMedium)
                                if (account.emailVerified) {
                                    Icon(
                                        Icons.Outlined.CheckCircle,
                                        contentDescription = "Email verified",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh account")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                account.tokenName?.let {
                    AssistChip(onClick = {}, enabled = false, label = { Text("Token: $it") })
                }
                account.tokenRole?.let {
                    AssistChip(onClick = {}, enabled = false, label = { Text("Role: $it") })
                }
            }

            account.profileUrl?.let { url ->
                TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
                    Text("View profile")
                }
            }
        }
    }
}

@Composable
private fun Avatar(avatarUrl: String?, name: String) {
    val initials = name.trim().split(" ").filter { it.isNotBlank() }
        .mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString("")
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = "Avatar",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
        )
        Text(initials.ifBlank { "?" }, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SettingRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp)
        ) {
            icon()
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            content()
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

private fun huggingFaceSubtitle(state: SettingsScreenState): String {
    return when {
        state.huggingFaceToken.isBlank() -> "HF token not set"
        state.huggingFaceAccount.isValidating -> "Validating token"
        state.huggingFaceAccount.isValid -> {
            val label = state.huggingFaceAccount.fullName
                ?: state.huggingFaceAccount.username
                ?: state.huggingFaceAccount.email
            if (label.isNullOrBlank()) "HF token validated" else "Connected as $label"
        }

        !state.huggingFaceAccount.message.isNullOrBlank() -> "Token validation failed"
        else -> "HF token saved"
    }
}

private fun formatModelSize(status: GeminiNanoStatusUi, unavailableLabel: String): String {
    val size = when {
        status.bytesToDownload != null && status.bytesToDownload > 0 -> status.bytesToDownload
        status.lastKnownModelSizeBytes > 0 -> status.lastKnownModelSizeBytes
        else -> null
    }
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
private fun ParameterSliders(
    temperature: Double,
    topP: Double,
    contextLength: Int,
    onTemperatureChange: (Double) -> Unit,
    onTopPChange: (Double) -> Unit,
    onContextLengthChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
private fun StatsCard(state: SettingsScreenState, onRefresh: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Usage", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onRefresh) {
                    Text("Refresh")
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    UsageTile(
                        title = "Sessions",
                        value = state.stats.sessionCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    UsageTile(
                        title = "Sent",
                        value = state.stats.messagesSent.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    UsageTile(
                        title = "Received",
                        value = state.stats.messagesReceived.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun UsageTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.aspectRatio(1f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
