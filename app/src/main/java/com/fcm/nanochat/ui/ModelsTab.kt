package com.fcm.nanochat.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fcm.nanochat.model.ModelCardUi
import com.fcm.nanochat.model.ModelGalleryScreenState
import com.fcm.nanochat.models.compatibility.LocalModelCompatibilityState
import com.fcm.nanochat.models.registry.ModelInstallState
import com.fcm.nanochat.models.registry.ModelStorageLocation
import kotlin.math.ln
import kotlin.math.pow

@Composable
internal fun ModelsTab(
    state: ModelGalleryScreenState,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit,
    onDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetryDownload: (String) -> Unit,
    onUseModel: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
    onMoveStorage: (String, ModelStorageLocation) -> Unit,
    onImportLocalModel: () -> Unit,
    onClearNotice: () -> Unit
) {
    var detailsModel by remember { mutableStateOf<ModelCardUi?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        ModelLibraryHeader(
            version = state.allowlistVersion,
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            onImportLocalModel = onImportLocalModel
        )

        state.runtimeMetrics?.let { metrics ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(
                    text = "Last run: ${metrics.modelId} | TTFB ${metrics.timeToFirstTokenMs}ms | ${"%.1f".format(metrics.tokensPerSecond)} tok/s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        if (state.notice != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = state.notice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onClearNotice) {
                        Text("Dismiss")
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.models, key = { it.modelId }) { model ->
                ModelCard(
                    model = model,
                    onShowDetails = { detailsModel = model },
                    onDownload = { onDownload(model.modelId) },
                    onCancelDownload = { onCancelDownload(model.modelId) },
                    onRetryDownload = { onRetryDownload(model.modelId) },
                    onUseModel = { onUseModel(model.modelId) },
                    onDeleteModel = { onDeleteModel(model.modelId) },
                    onMoveStorage = { target -> onMoveStorage(model.modelId, target) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    val details = detailsModel
    if (details != null) {
        ModelDetailsDialog(
            model = details,
            onDismiss = { detailsModel = null }
        )
    }
}

@Composable
private fun ModelLibraryHeader(
    version: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onImportLocalModel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Model Library",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Allowlist v${version.ifBlank { "unknown" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh allowlist")
                }
            }
        }

        OutlinedButton(
            onClick = onImportLocalModel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Icon(Icons.Default.SdStorage, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Import local model (coming soon)")
        }
    }
}

@Composable
private fun ModelCard(
    model: ModelCardUi,
    onShowDetails: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onRetryDownload: () -> Unit,
    onUseModel: () -> Unit,
    onDeleteModel: () -> Unit,
    onMoveStorage: (ModelStorageLocation) -> Unit
) {
    val compatibilityText = compatibilityText(model.compatibility)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onShowDetails),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = model.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (model.isActive) {
                    StatusBadge(label = "Active")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusBadge(label = "${humanBytes(model.sizeInBytes)}")
                StatusBadge(label = "RAM ${model.minDeviceMemoryInGb} GB+")
                StatusBadge(label = installStateLabel(model.installState))
                if (model.requiresHfToken) {
                    StatusBadge(label = "HF token")
                }
                if (model.isLegacy) {
                    StatusBadge(label = "Legacy")
                }
                if (model.llmSupportImage) {
                    StatusBadge(label = "Image")
                }
                if (model.llmSupportAudio) {
                    StatusBadge(label = "Audio")
                }
                if (model.recommendedForChat) {
                    StatusBadge(label = "Chat ready")
                }
            }

            Text(
                text = compatibilityText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (
                model.installState == ModelInstallState.DOWNLOADING ||
                model.installState == ModelInstallState.PAUSED
            ) {
                val total = if (model.sizeInBytes > 0L) model.sizeInBytes else model.sizeOnDiskBytes
                Text(
                    text = "${humanBytes(model.downloadedBytes)} / ${humanBytes(total)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            ModelActionRow(
                model = model,
                onDownload = onDownload,
                onCancelDownload = onCancelDownload,
                onRetryDownload = onRetryDownload,
                onUseModel = onUseModel,
                onDeleteModel = onDeleteModel,
                onMoveStorage = onMoveStorage
            )
        }
    }
}

@Composable
private fun ModelActionRow(
    model: ModelCardUi,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onRetryDownload: () -> Unit,
    onUseModel: () -> Unit,
    onDeleteModel: () -> Unit,
    onMoveStorage: (ModelStorageLocation) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (model.installState) {
            ModelInstallState.NOT_INSTALLED -> {
                val tokenRequired = model.compatibility is LocalModelCompatibilityState.TokenRequired
                Button(
                    onClick = onDownload,
                    enabled = !tokenRequired && model.compatibility is LocalModelCompatibilityState.Downloadable
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (tokenRequired) "Requires token" else "Download")
                }
            }

            ModelInstallState.QUEUED -> {
                OutlinedButton(onClick = {}, enabled = false) {
                    Text("Queued")
                }
            }

            ModelInstallState.DOWNLOADING -> {
                OutlinedButton(onClick = onCancelDownload) {
                    Text("Cancel")
                }
            }

            ModelInstallState.PAUSED,
            ModelInstallState.FAILED,
            ModelInstallState.BROKEN -> {
                OutlinedButton(onClick = onRetryDownload) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry")
                }
            }

            ModelInstallState.VALIDATING,
            ModelInstallState.MOVING,
            ModelInstallState.DELETING -> {
                OutlinedButton(onClick = {}, enabled = false) {
                    Text(model.installState.name.lowercase().replaceFirstChar { it.titlecase() })
                }
            }

            ModelInstallState.INSTALLED -> {
                if (!model.isActive && model.compatibility is LocalModelCompatibilityState.Ready) {
                    Button(onClick = onUseModel) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Use model")
                    }
                } else if (model.isActive) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(vertical = 6.dp)
                    ) {
                        Text(
                            text = "In use",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                } else {
                    OutlinedButton(onClick = {}, enabled = false) {
                        Text("Unavailable")
                    }
                }

                OutlinedButton(onClick = {
                    val target = if (model.storageLocation == ModelStorageLocation.INTERNAL) {
                        ModelStorageLocation.EXTERNAL
                    } else {
                        ModelStorageLocation.INTERNAL
                    }
                    onMoveStorage(target)
                }) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Move")
                }

                OutlinedButton(onClick = onDeleteModel) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun ModelDetailsDialog(
    model: ModelCardUi,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = {
            Text(model.displayName)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(model.description)
                DetailsRow("Repository", model.sourceRepo)
                DetailsRow("File", model.modelFile)
                DetailsRow("Expected size", humanBytes(model.sizeInBytes))
                DetailsRow("Minimum RAM", "${model.minDeviceMemoryInGb} GB")
                DetailsRow(
                    "Storage requirement",
                    "Keep about ${humanBytes(model.sizeInBytes)} free for the model file and temporary validation."
                )
                DetailsRow("Task types", model.taskTypes.joinToString())
                DetailsRow("Best for", model.bestForTaskTypes.joinToString())
                DetailsRow("Chat suitability", if (model.recommendedForChat) "Recommended" else "Not recommended")
                DetailsRow(
                    "Default runtime config",
                    "topK=${model.defaultTopK}, topP=${model.defaultTopP}, temp=${model.defaultTemperature}, maxTokens=${model.defaultMaxTokens}"
                )
                if (model.acceleratorHints.isNotEmpty()) {
                    DetailsRow("Accelerator hints", model.acceleratorHints.joinToString())
                }
                DetailsRow("Token required", if (model.requiresHfToken) "Yes" else "No")
                if (model.requiresHfToken) {
                    DetailsRow("Token details", "Add a Hugging Face read token in Settings before downloading.")
                }
                DetailsRow("Image support", if (model.llmSupportImage) "Yes" else "No")
                DetailsRow("Audio support", if (model.llmSupportAudio) "Yes" else "No")
                DetailsRow("Storage location", model.storageLocation.name)
                if (!model.localPath.isNullOrBlank()) {
                    DetailsRow("Installed path", model.localPath)
                }
                if (!model.errorMessage.isNullOrBlank()) {
                    DetailsRow("Last error", model.errorMessage)
                }
            }
        }
    )
}

@Composable
private fun DetailsRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun StatusBadge(label: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
        )
    }
}

private fun compatibilityText(state: LocalModelCompatibilityState): String {
    return when (state) {
        LocalModelCompatibilityState.Ready -> "Ready on this device"
        LocalModelCompatibilityState.Downloadable -> "Downloadable"
        is LocalModelCompatibilityState.NeedsMoreStorage -> "Needs more storage"
        is LocalModelCompatibilityState.NeedsMoreRam -> "Needs more memory"
        is LocalModelCompatibilityState.UnsupportedDevice -> state.reason
        LocalModelCompatibilityState.TokenRequired -> "Requires Hugging Face token"
        is LocalModelCompatibilityState.DownloadedButNotActivatable -> state.reason
        LocalModelCompatibilityState.CorruptedModel -> "Corrupted model"
        is LocalModelCompatibilityState.RuntimeUnavailable -> state.reason
    }
}

private fun installStateLabel(state: ModelInstallState): String {
    return when (state) {
        ModelInstallState.NOT_INSTALLED -> "Not installed"
        ModelInstallState.QUEUED -> "Queued"
        ModelInstallState.DOWNLOADING -> "Downloading"
        ModelInstallState.PAUSED -> "Paused"
        ModelInstallState.FAILED -> "Failed"
        ModelInstallState.VALIDATING -> "Validating"
        ModelInstallState.INSTALLED -> "Installed"
        ModelInstallState.BROKEN -> "Broken"
        ModelInstallState.DELETING -> "Deleting"
        ModelInstallState.MOVING -> "Moving"
    }
}

private fun humanBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val unit = 1000.0
    val exp = (ln(bytes.toDouble()) / ln(unit)).toInt()
    if (exp == 0) return "$bytes B"
    val prefix = "kMGTPE"[exp - 1]
    val value = bytes / unit.pow(exp.toDouble())
    return "%.1f %sB".format(value, prefix)
}
