package com.fcm.nanochat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.fcm.nanochat.inference.RemoteConfigValidator
import com.fcm.nanochat.model.SettingsScreenState

@Composable
internal fun SettingsTab(
    state: SettingsScreenState,
    modifier: Modifier = Modifier,
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
    val missingRemoteFields = RemoteConfigValidator.missingFields(
        baseUrl = state.baseUrl,
        modelName = state.modelName,
        apiKey = state.apiKey
    )
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
            StatsCard(state = state, onRefresh = onRefreshStats)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Provider status", style = MaterialTheme.typography.labelLarge)
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
            ParameterSliders(
                temperature = state.temperature,
                topP = state.topP,
                contextLength = state.contextLength,
                onTemperatureChange = onTemperatureChange,
                onTopPChange = onTopPChange,
                onContextLengthChange = onContextLengthChange
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
                Text(
                    text = listOfNotNull(state.saveNotice, state.clearNotice).joinToString(" "),
                    color = MaterialTheme.colorScheme.primary
                )
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
                    val labels = missingRemoteFields.joinToString { it.displayName }
                    "Remote is missing: $labels. Nano requires Gemini Nano enabled in Developer Options on a supported device."
                }
            )
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onClearHistory, modifier = Modifier.fillMaxWidth()) {
                Text("Clear all chat history")
            }
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
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
            Text("Sessions: ${state.stats.sessionCount}")
            Text("Messages sent: ${state.stats.messagesSent}")
            Text("Messages received: ${state.stats.messagesReceived}")
        }
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
