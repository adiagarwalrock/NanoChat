package com.fcm.nanochat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fcm.nanochat.R
import com.fcm.nanochat.model.ModelCardUi
import com.fcm.nanochat.model.ModelGalleryScreenState
import com.fcm.nanochat.models.compatibility.LocalModelCompatibilityState
import com.fcm.nanochat.models.registry.ModelInstallState
import com.fcm.nanochat.models.registry.ModelStorageLocation
import kotlin.math.ln
import kotlin.math.pow

private enum class ModelFilter(val labelRes: Int) {
    All(R.string.filter_all),
    ChatReady(R.string.filter_chat_ready),
    Installed(R.string.filter_installed),
    NotInstalled(R.string.filter_not_installed),
    RequiresToken(R.string.filter_requires_token),
    FitsDevice(R.string.filter_fits_device)
}

private enum class ModelSort(val labelRes: Int) {
    Recommended(R.string.sort_recommended),
    InstalledFirst(R.string.sort_installed),
    SmallestFirst(R.string.sort_smallest),
    BestForDevice(R.string.sort_best_device)
}

private enum class ActionType {
    DOWNLOAD,
    RESUME,
    RETRY,
    USE,
    ADD_TOKEN,
    CANCEL,
    DELETE,
    MOVE,
    OPEN_DETAILS,
    NONE
}

private enum class Tone {
    Positive,
    Neutral,
    Warning,
    Error
}

private data class StatusPresentation(
    val label: String,
    val supportingText: String,
    val tone: Tone,
    val progress: Float? = null,
    val progressText: String? = null
)

private data class ActionPlan(
    val primaryLabel: String,
    val primaryAction: ActionType,
    val primaryEnabled: Boolean,
    val secondaryLabel: String? = null,
    val secondaryAction: ActionType = ActionType.NONE,
    val secondaryEnabled: Boolean = true,
    val showOverflow: Boolean = false,
    val overflowMoveEnabled: Boolean = false,
    val overflowDeleteEnabled: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
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
    onOpenHuggingFaceSettings: () -> Unit = {},
    onClearNotice: () -> Unit
) {
    var detailsModelId by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(ModelFilter.All) }
    var selectedSort by rememberSaveable { mutableStateOf(ModelSort.Recommended) }

    val filteredModels = remember(state.models, query, selectedFilter, selectedSort) {
        state.models
            .asSequence()
            .filter { model -> model.matchesQuery(query) }
            .filter { model -> model.matchesFilter(selectedFilter) }
            .sortedWith(selectedSort.comparator())
            .toList()
    }

    val chatReadyModels = filteredModels.filter { it.recommendedForChat }
    val otherModels = filteredModels.filterNot { it.recommendedForChat }
    val detailsModel = remember(detailsModelId, state.models) {
        state.models.firstOrNull { it.modelId == detailsModelId }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        LibraryHeader(
            allowlistVersion = state.allowlistVersion,
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            onImportLocalModel = onImportLocalModel
        )

        if (state.notice != null) {
            NoticeBanner(
                message = toFriendlyError(state.notice),
                onDismiss = onClearNotice,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            )
        }

        state.runtimeMetrics?.let { metrics ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            ) {
                Text(
                    text = "Last local run: ${metrics.modelId}  ·  TTFB ${metrics.timeToFirstTokenMs} ms  ·  ${
                        "%.1f".format(
                            metrics.tokensPerSecond
                        )
                    } tok/s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        LibraryDiscoveryBar(
            query = query,
            selectedFilter = selectedFilter,
            selectedSort = selectedSort,
            onQueryChange = { query = it },
            onFilterChange = { selectedFilter = it },
            onSortChange = { selectedSort = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (chatReadyModels.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = stringResource(id = R.string.recommended_for_chat),
                        count = chatReadyModels.size
                    )
                }
                items(chatReadyModels, key = { it.modelId }) { model ->
                    CuratedModelCard(
                        model = model,
                        onOpenDetails = { detailsModelId = model.modelId },
                        onOpenHuggingFaceSettings = onOpenHuggingFaceSettings,
                        onDownload = { onDownload(model.modelId) },
                        onCancelDownload = { onCancelDownload(model.modelId) },
                        onRetryDownload = { onRetryDownload(model.modelId) },
                        onUseModel = { onUseModel(model.modelId) },
                        onDeleteModel = { onDeleteModel(model.modelId) },
                        onMoveStorage = {
                            val target =
                                if (model.storageLocation == ModelStorageLocation.INTERNAL) {
                                    ModelStorageLocation.EXTERNAL
                                } else {
                                    ModelStorageLocation.INTERNAL
                                }
                            onMoveStorage(model.modelId, target)
                        }
                    )
                }
            }

            if (otherModels.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = stringResource(id = R.string.other_supported_models),
                        count = otherModels.size
                    )
                }
                items(otherModels, key = { it.modelId }) { model ->
                    CuratedModelCard(
                        model = model,
                        onOpenDetails = { detailsModelId = model.modelId },
                        onOpenHuggingFaceSettings = onOpenHuggingFaceSettings,
                        onDownload = { onDownload(model.modelId) },
                        onCancelDownload = { onCancelDownload(model.modelId) },
                        onRetryDownload = { onRetryDownload(model.modelId) },
                        onUseModel = { onUseModel(model.modelId) },
                        onDeleteModel = { onDeleteModel(model.modelId) },
                        onMoveStorage = {
                            val target =
                                if (model.storageLocation == ModelStorageLocation.INTERNAL) {
                                    ModelStorageLocation.EXTERNAL
                                } else {
                                    ModelStorageLocation.INTERNAL
                                }
                            onMoveStorage(model.modelId, target)
                        }
                    )
                }
            }

            if (filteredModels.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No models match your filters.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp)
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(18.dp)) }
        }
    }

    if (detailsModel != null) {
        ModelDetailsSheet(
            model = detailsModel,
            onDismiss = { detailsModelId = null },
            onOpenHuggingFaceSettings = {
                detailsModelId = null
                onOpenHuggingFaceSettings()
            },
            onDownload = { onDownload(detailsModel.modelId) },
            onCancelDownload = { onCancelDownload(detailsModel.modelId) },
            onRetryDownload = { onRetryDownload(detailsModel.modelId) },
            onUseModel = { onUseModel(detailsModel.modelId) },
            onDeleteModel = { onDeleteModel(detailsModel.modelId) },
            onMoveStorage = {
                val target = if (detailsModel.storageLocation == ModelStorageLocation.INTERNAL) {
                    ModelStorageLocation.EXTERNAL
                } else {
                    ModelStorageLocation.INTERNAL
                }
                onMoveStorage(detailsModel.modelId, target)
            }
        )
    }
}

@Composable
private fun LibraryHeader(
    allowlistVersion: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onImportLocalModel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(id = R.string.model_library),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Allowlist v${allowlistVersion.ifBlank { "unknown" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(id = R.string.gemini_refresh_status)
                )
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
private fun NoticeBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun LibraryDiscoveryBar(
    query: String,
    selectedFilter: ModelFilter,
    selectedSort: ModelSort,
    onQueryChange: (String) -> Unit,
    onFilterChange: (ModelFilter) -> Unit,
    onSortChange: (ModelSort) -> Unit,
    modifier: Modifier = Modifier
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            placeholder = {
                Text(stringResource(id = R.string.search_models))
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModelFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { onFilterChange(filter) },
                        label = { Text(text = stringResource(id = filter.labelRes)) }
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { sortMenuExpanded = true }) {
                    Text(text = stringResource(id = selectedSort.labelRes))
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    ModelSort.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(text = stringResource(id = sort.labelRes)) },
                            onClick = {
                                sortMenuExpanded = false
                                onSortChange(sort)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CuratedModelCard(
    model: ModelCardUi,
    onOpenDetails: () -> Unit,
    onOpenHuggingFaceSettings: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onRetryDownload: () -> Unit,
    onUseModel: () -> Unit,
    onDeleteModel: () -> Unit,
    onMoveStorage: () -> Unit
) {
    val status = remember(model) { model.statusPresentation() }
    val actionPlan = remember(model) { model.actionPlan() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetails),
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = model.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        model.familyBadge()?.let { family ->
                            InlineBadge(text = family)
                        }

                        if (model.isActive) {
                            InlineBadge(
                                text = stringResource(id = R.string.selected_for_chat),
                                tone = Tone.Positive
                            )
                        }
                    }

                    Text(
                        text = model.cardSummary(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                InlineBadge(text = humanBytes(model.sizeInBytes))
                if (model.minDeviceMemoryInGb > 0) {
                    InlineBadge(text = "RAM ${model.minDeviceMemoryInGb} GB+")
                }
                InlineBadge(
                    text = if (model.recommendedForChat) {
                        stringResource(id = R.string.recommended_for_chat)
                    } else {
                        stringResource(id = R.string.not_chat_ready)
                    }
                )
                if (model.llmSupportImage) {
                    InlineBadge(text = stringResource(id = R.string.supports_image))
                }
                if (model.llmSupportAudio) {
                    InlineBadge(text = stringResource(id = R.string.supports_audio))
                }
                if (model.requiresHfToken) {
                    InlineBadge(text = stringResource(id = R.string.requires_token))
                }
            }

            StateStrip(status = status)

            ModelCardActions(
                plan = actionPlan,
                onAction = { action ->
                    when (action) {
                        ActionType.DOWNLOAD -> onDownload()
                        ActionType.RESUME -> onRetryDownload()
                        ActionType.RETRY -> onRetryDownload()
                        ActionType.USE -> onUseModel()
                        ActionType.ADD_TOKEN -> onOpenHuggingFaceSettings()
                        ActionType.CANCEL -> onCancelDownload()
                        ActionType.DELETE -> onDeleteModel()
                        ActionType.MOVE -> onMoveStorage()
                        ActionType.OPEN_DETAILS -> onOpenDetails()
                        ActionType.NONE -> Unit
                    }
                },
                onOpenDetails = onOpenDetails
            )
        }
    }
}

@Composable
private fun InlineBadge(
    text: String,
    tone: Tone = Tone.Neutral
) {
    val background = when (tone) {
        Tone.Positive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        Tone.Neutral -> MaterialTheme.colorScheme.surfaceContainerHighest
        Tone.Warning -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
        Tone.Error -> MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
    }
    val content = when (tone) {
        Tone.Positive -> MaterialTheme.colorScheme.primary
        Tone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        Tone.Warning -> MaterialTheme.colorScheme.tertiary
        Tone.Error -> MaterialTheme.colorScheme.error
    }

    Surface(shape = RoundedCornerShape(999.dp), color = background) {
        Text(
            text = text,
            color = content,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun StateStrip(status: StatusPresentation) {
    val background = when (status.tone) {
        Tone.Positive -> MaterialTheme.colorScheme.secondaryContainer
        Tone.Neutral -> MaterialTheme.colorScheme.surfaceContainer
        Tone.Warning -> MaterialTheme.colorScheme.tertiaryContainer
        Tone.Error -> MaterialTheme.colorScheme.errorContainer
    }
    val content = when (status.tone) {
        Tone.Positive -> MaterialTheme.colorScheme.onSecondaryContainer
        Tone.Neutral -> MaterialTheme.colorScheme.onSurface
        Tone.Warning -> MaterialTheme.colorScheme.onTertiaryContainer
        Tone.Error -> MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelLarge,
                color = content
            )
            Text(
                text = status.supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = content
            )

            if (status.progress != null) {
                LinearProgressIndicator(
                    progress = { status.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }

            if (!status.progressText.isNullOrBlank()) {
                Text(
                    text = status.progressText,
                    style = MaterialTheme.typography.labelSmall,
                    color = content
                )
            }
        }
    }
}

@Composable
private fun ModelCardActions(
    plan: ActionPlan,
    onAction: (ActionType) -> Unit,
    onOpenDetails: () -> Unit,
    showDetailsAction: Boolean = true
) {
    var overflowExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = { onAction(plan.primaryAction) },
            enabled = plan.primaryEnabled,
            modifier = Modifier.weight(1f)
        ) {
            when (plan.primaryAction) {
                ActionType.DOWNLOAD -> Icon(Icons.Default.Download, contentDescription = null)
                else -> Unit
            }
            if (plan.primaryAction == ActionType.DOWNLOAD) {
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(plan.primaryLabel, maxLines = 1)
        }

        if (plan.secondaryLabel != null) {
            OutlinedButton(
                onClick = { onAction(plan.secondaryAction) },
                enabled = plan.secondaryEnabled
            ) {
                Text(plan.secondaryLabel, maxLines = 1)
            }
        }

        if (showDetailsAction) {
            TextButton(onClick = onOpenDetails) {
                Text(stringResource(id = R.string.view_details), maxLines = 1)
            }
        }

        if (plan.showOverflow) {
            IconButton(onClick = { overflowExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = null)
            }
            DropdownMenu(
                expanded = overflowExpanded,
                onDismissRequest = { overflowExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(id = R.string.move_storage)) },
                    leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
                    enabled = plan.overflowMoveEnabled,
                    onClick = {
                        overflowExpanded = false
                        onAction(ActionType.MOVE)
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(id = R.string.delete_model)) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    enabled = plan.overflowDeleteEnabled,
                    onClick = {
                        overflowExpanded = false
                        onAction(ActionType.DELETE)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDetailsSheet(
    model: ModelCardUi,
    onDismiss: () -> Unit,
    onOpenHuggingFaceSettings: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onRetryDownload: () -> Unit,
    onUseModel: () -> Unit,
    onDeleteModel: () -> Unit,
    onMoveStorage: () -> Unit
) {
    val actionPlan = remember(model) { model.actionPlan() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = model.fullDescription(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            item {
                StateStrip(status = model.statusPresentation())
            }

            item {
                ModelCardActions(
                    plan = actionPlan,
                    onAction = { action ->
                        when (action) {
                            ActionType.DOWNLOAD -> onDownload()
                            ActionType.RESUME -> onRetryDownload()
                            ActionType.RETRY -> onRetryDownload()
                            ActionType.USE -> onUseModel()
                            ActionType.ADD_TOKEN -> onOpenHuggingFaceSettings()
                            ActionType.CANCEL -> onCancelDownload()
                            ActionType.DELETE -> onDeleteModel()
                            ActionType.MOVE -> onMoveStorage()
                            ActionType.OPEN_DETAILS,
                            ActionType.NONE -> Unit
                        }
                    },
                    onOpenDetails = {},
                    showDetailsAction = false
                )
            }

            item {
                DetailBlock(title = "Good for", value = model.bestUseCases())
            }

            item {
                DetailBlock(
                    title = "Not ideal for",
                    value = model.notIdealForUseCases()
                )
            }

            item {
                DetailBlock(title = "Minimum RAM", value = "${model.minDeviceMemoryInGb} GB")
                DetailBlock(title = "Estimated storage", value = humanBytes(model.sizeInBytes))
                DetailBlock(title = "Task support", value = model.taskTypes.toPrettyList())
                DetailBlock(
                    title = "Token requirement",
                    value = if (model.requiresHfToken) {
                        "Requires a Hugging Face read token"
                    } else {
                        "No token required"
                    }
                )
                DetailBlock(title = "Compatibility", value = model.compatibilitySummary())
                DetailBlock(title = "Install status", value = model.installStateLabel())
                DetailBlock(title = "Source", value = model.sourceRepo)
                DetailBlock(
                    title = "Runtime config",
                    value = "topK=${model.defaultTopK}, topP=${model.defaultTopP}, temp=${model.defaultTemperature}, maxTokens=${model.defaultMaxTokens}"
                )
                if (model.acceleratorHints.isNotEmpty()) {
                    DetailBlock(
                        title = "Accelerator hints",
                        value = model.acceleratorHints.toPrettyList()
                    )
                }
                if (!model.localPath.isNullOrBlank()) {
                    DetailBlock(title = "Installed path", value = model.localPath.orEmpty())
                }
            }

            if (!model.errorMessage.isNullOrBlank()) {
                item {
                    DetailBlock(
                        title = "Troubleshooting",
                        value = toFriendlyError(model.errorMessage)
                    )
                    DetailBlock(
                        title = "Diagnostics",
                        value = model.errorMessage
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun DetailBlock(title: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun ModelCardUi.matchesQuery(query: String): Boolean {
    val q = query.trim().lowercase()
    if (q.isBlank()) return true
    return displayName.lowercase().contains(q) ||
            sourceRepo.lowercase().contains(q) ||
            cardSummary(180).lowercase().contains(q)
}

private fun ModelCardUi.matchesFilter(filter: ModelFilter): Boolean {
    return when (filter) {
        ModelFilter.All -> true
        ModelFilter.ChatReady -> recommendedForChat
        ModelFilter.Installed -> installState == ModelInstallState.INSTALLED
        ModelFilter.NotInstalled -> installState == ModelInstallState.NOT_INSTALLED
        ModelFilter.RequiresToken -> {
            requiresHfToken || compatibility is LocalModelCompatibilityState.TokenRequired
        }

        ModelFilter.FitsDevice -> when (compatibility) {
            is LocalModelCompatibilityState.NeedsMoreRam,
            is LocalModelCompatibilityState.NeedsMoreStorage,
            is LocalModelCompatibilityState.UnsupportedDevice -> false

            else -> true
        }
    }
}

private fun ModelSort.comparator(): Comparator<ModelCardUi> {
    val recommendedComparator = compareByDescending<ModelCardUi> { it.isActive }
        .thenByDescending { it.recommendedForChat }
        .thenBy { it.compatibilityRank() }
        .thenBy { it.displayName.lowercase() }

    return when (this) {
        ModelSort.Recommended -> recommendedComparator
        ModelSort.InstalledFirst -> compareByDescending<ModelCardUi> {
            it.installState == ModelInstallState.INSTALLED
        }
            .thenByDescending { it.isActive }
            .thenByDescending { it.recommendedForChat }
            .thenBy { it.compatibilityRank() }
            .thenBy { it.displayName.lowercase() }

        ModelSort.SmallestFirst -> compareBy<ModelCardUi> { it.sizeInBytes }
            .thenBy { it.displayName.lowercase() }

        ModelSort.BestForDevice -> compareBy<ModelCardUi> { it.compatibilityRank() }
            .thenByDescending { it.recommendedForChat }
            .thenBy { it.sizeInBytes }
    }
}

private fun ModelCardUi.compatibilityRank(): Int {
    return when (compatibility) {
        LocalModelCompatibilityState.Ready -> 0
        LocalModelCompatibilityState.Downloadable -> 1
        is LocalModelCompatibilityState.DownloadedButNotActivatable -> 2
        LocalModelCompatibilityState.TokenRequired -> 3
        is LocalModelCompatibilityState.RuntimeUnavailable -> 4
        is LocalModelCompatibilityState.NeedsMoreStorage -> 5
        is LocalModelCompatibilityState.NeedsMoreRam -> 6
        LocalModelCompatibilityState.CorruptedModel -> 7
        is LocalModelCompatibilityState.UnsupportedDevice -> 8
    }
}

private fun ModelCardUi.installStateLabel(): String {
    return when (installState) {
        ModelInstallState.NOT_INSTALLED -> "Not installed"
        ModelInstallState.QUEUED -> "Queued"
        ModelInstallState.DOWNLOADING -> "Downloading"
        ModelInstallState.PAUSED -> "Paused"
        ModelInstallState.FAILED -> "Failed"
        ModelInstallState.VALIDATING -> "Validating"
        ModelInstallState.INSTALLED -> "Installed"
        ModelInstallState.BROKEN -> "Corrupted"
        ModelInstallState.DELETING -> "Deleting"
        ModelInstallState.MOVING -> "Moving"
    }
}

private fun ModelCardUi.compatibilitySummary(): String {
    val compatibilityState = compatibility
    return when (val value = compatibilityState) {
        LocalModelCompatibilityState.Ready -> "Ready on this device"
        LocalModelCompatibilityState.Downloadable -> "Download ready"
        is LocalModelCompatibilityState.NeedsMoreStorage -> "Needs more device storage"
        is LocalModelCompatibilityState.NeedsMoreRam -> "Needs more RAM"
        is LocalModelCompatibilityState.UnsupportedDevice -> toFriendlyError(value.reason)
        LocalModelCompatibilityState.TokenRequired -> "Requires Hugging Face token"
        is LocalModelCompatibilityState.DownloadedButNotActivatable -> toFriendlyError(value.reason)
        LocalModelCompatibilityState.CorruptedModel -> stringResourceSafe(R.string.model_corrupted)
        is LocalModelCompatibilityState.RuntimeUnavailable -> stringResourceSafe(R.string.runtime_unavailable)
    }
}

private fun ModelCardUi.statusPresentation(): StatusPresentation {
    val compatibilityState = compatibility
    val totalBytes = sizeInBytes.takeIf { it > 0L } ?: sizeOnDiskBytes
    val progressValue = if (totalBytes > 0L) {
        downloadedBytes.toFloat() / totalBytes.toFloat()
    } else {
        null
    }
    val transferText = if (totalBytes > 0L) {
        "${humanBytes(downloadedBytes)} of ${humanBytes(totalBytes)}"
    } else {
        null
    }

    return when (installState) {
        ModelInstallState.NOT_INSTALLED -> {
            when (compatibilityState) {
                LocalModelCompatibilityState.TokenRequired -> StatusPresentation(
                    label = "Requires Hugging Face token",
                    supportingText = stringResourceSafe(R.string.requires_hf_token_message),
                    tone = Tone.Warning
                )

                is LocalModelCompatibilityState.NeedsMoreRam -> StatusPresentation(
                    label = "Not compatible",
                    supportingText = "Needs ${compatibilityState.requiredGb} GB RAM. Device has ${compatibilityState.availableGb} GB.",
                    tone = Tone.Warning
                )

                is LocalModelCompatibilityState.NeedsMoreStorage -> StatusPresentation(
                    label = "Not enough storage",
                    supportingText = "Need ${humanBytes(compatibilityState.requiredBytes)} free.",
                    tone = Tone.Warning
                )

                is LocalModelCompatibilityState.UnsupportedDevice -> StatusPresentation(
                    label = "Not compatible",
                    supportingText = toFriendlyError(compatibilityState.reason),
                    tone = Tone.Warning
                )

                else -> StatusPresentation(
                    label = "Not installed",
                    supportingText = "Download to use this model in chat.",
                    tone = Tone.Neutral
                )
            }
        }

        ModelInstallState.QUEUED -> StatusPresentation(
            label = "Queued",
            supportingText = "Download will start shortly.",
            tone = Tone.Neutral
        )

        ModelInstallState.DOWNLOADING -> StatusPresentation(
            label = "Downloading ${(progressValue?.times(100f) ?: 0f).toInt()}%",
            supportingText = "Keep NanoChat open until setup completes.",
            tone = Tone.Neutral,
            progress = progressValue,
            progressText = transferText
        )

        ModelInstallState.PAUSED -> StatusPresentation(
            label = "Download paused",
            supportingText = "Resume when you are ready.",
            tone = Tone.Warning,
            progress = progressValue,
            progressText = transferText
        )

        ModelInstallState.FAILED -> StatusPresentation(
            label = "Download failed",
            supportingText = toFriendlyError(errorMessage),
            tone = Tone.Error,
            progress = progressValue,
            progressText = transferText
        )

        ModelInstallState.VALIDATING -> StatusPresentation(
            label = "Validating",
            supportingText = "Checking file integrity before activation.",
            tone = Tone.Neutral,
            progress = 1f,
            progressText = transferText
        )

        ModelInstallState.INSTALLED -> {
            when {
                isActive -> StatusPresentation(
                    label = "Selected for chat",
                    supportingText = "NanoChat uses this model for local responses.",
                    tone = Tone.Positive
                )

                compatibilityState is LocalModelCompatibilityState.Ready -> StatusPresentation(
                    label = "Ready to use",
                    supportingText = "Installed and compatible with this device.",
                    tone = Tone.Positive
                )

                else -> StatusPresentation(
                    label = "Installed with issues",
                    supportingText = toFriendlyError(errorMessage ?: compatibilitySummary()),
                    tone = Tone.Warning
                )
            }
        }

        ModelInstallState.BROKEN -> StatusPresentation(
            label = "Model file issue",
            supportingText = toFriendlyError(errorMessage),
            tone = Tone.Error
        )

        ModelInstallState.DELETING -> StatusPresentation(
            label = "Deleting",
            supportingText = "Removing local files.",
            tone = Tone.Neutral
        )

        ModelInstallState.MOVING -> StatusPresentation(
            label = "Moving storage",
            supportingText = "Updating model location.",
            tone = Tone.Neutral
        )
    }
}

private fun ModelCardUi.actionPlan(): ActionPlan {
    val ready = installState == ModelInstallState.INSTALLED &&
            compatibility is LocalModelCompatibilityState.Ready

    return when (installState) {
        ModelInstallState.NOT_INSTALLED -> {
            when {
                compatibility is LocalModelCompatibilityState.TokenRequired -> ActionPlan(
                    primaryLabel = "Add token",
                    primaryAction = ActionType.ADD_TOKEN,
                    primaryEnabled = true,
                    secondaryLabel = "Learn why",
                    secondaryAction = ActionType.OPEN_DETAILS
                )

                compatibility is LocalModelCompatibilityState.Downloadable -> ActionPlan(
                    primaryLabel = "Download",
                    primaryAction = ActionType.DOWNLOAD,
                    primaryEnabled = true
                )

                else -> ActionPlan(
                    primaryLabel = "View details",
                    primaryAction = ActionType.OPEN_DETAILS,
                    primaryEnabled = true
                )
            }
        }

        ModelInstallState.QUEUED -> ActionPlan(
            primaryLabel = "Queued",
            primaryAction = ActionType.NONE,
            primaryEnabled = false,
            secondaryLabel = "Cancel",
            secondaryAction = ActionType.CANCEL
        )

        ModelInstallState.DOWNLOADING -> ActionPlan(
            primaryLabel = "Downloading",
            primaryAction = ActionType.NONE,
            primaryEnabled = false,
            secondaryLabel = "Cancel",
            secondaryAction = ActionType.CANCEL
        )

        ModelInstallState.PAUSED -> ActionPlan(
            primaryLabel = "Resume",
            primaryAction = ActionType.RESUME,
            primaryEnabled = true,
            secondaryLabel = "Delete",
            secondaryAction = ActionType.DELETE
        )

        ModelInstallState.FAILED,
        ModelInstallState.BROKEN -> ActionPlan(
            primaryLabel = "Retry",
            primaryAction = ActionType.RETRY,
            primaryEnabled = true,
            secondaryLabel = "Delete",
            secondaryAction = ActionType.DELETE
        )

        ModelInstallState.VALIDATING -> ActionPlan(
            primaryLabel = "Validating",
            primaryAction = ActionType.NONE,
            primaryEnabled = false
        )

        ModelInstallState.DELETING -> ActionPlan(
            primaryLabel = "Deleting",
            primaryAction = ActionType.NONE,
            primaryEnabled = false
        )

        ModelInstallState.MOVING -> ActionPlan(
            primaryLabel = "Moving",
            primaryAction = ActionType.NONE,
            primaryEnabled = false
        )

        ModelInstallState.INSTALLED -> {
            when {
                isActive -> ActionPlan(
                    primaryLabel = "Selected",
                    primaryAction = ActionType.NONE,
                    primaryEnabled = false,
                    showOverflow = true,
                    overflowMoveEnabled = true,
                    overflowDeleteEnabled = true
                )

                ready -> ActionPlan(
                    primaryLabel = "Use model",
                    primaryAction = ActionType.USE,
                    primaryEnabled = true,
                    showOverflow = true,
                    overflowMoveEnabled = true,
                    overflowDeleteEnabled = true
                )

                else -> ActionPlan(
                    primaryLabel = "Continue setup",
                    primaryAction = ActionType.OPEN_DETAILS,
                    primaryEnabled = true,
                    secondaryLabel = "Delete",
                    secondaryAction = ActionType.DELETE,
                    showOverflow = true,
                    overflowMoveEnabled = true,
                    overflowDeleteEnabled = true
                )
            }
        }
    }
}

private fun ModelCardUi.familyBadge(): String? {
    val repo = sourceRepo.lowercase()
    return when {
        repo.startsWith("google/") -> "Google"
        repo.startsWith("litert-community/") -> "Community"
        displayName.contains("qwen", ignoreCase = true) -> "Qwen"
        displayName.contains("gemma", ignoreCase = true) -> "Gemma"
        displayName.contains("deepseek", ignoreCase = true) -> "DeepSeek"
        else -> null
    }
}

private fun ModelCardUi.cardSummary(maxLength: Int = 120): String {
    val cleaned = cleanDescription(description)
    if (cleaned.isBlank()) {
        return if (recommendedForChat) {
            "Chat model tuned for on-device generation."
        } else {
            "General local model for specialized tasks."
        }
    }

    val sentence = cleaned
        .split('.')
        .map(String::trim)
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
    val base = if (sentence.isNotBlank()) "$sentence." else cleaned
    return base.take(maxLength).trimEnd().let { text ->
        if (text.length < base.length) "$text…" else text
    }
}

private fun ModelCardUi.fullDescription(): String {
    val cleaned = cleanDescription(description)
    return if (cleaned.isBlank()) {
        if (recommendedForChat) {
            "Optimized for on-device chat responses in NanoChat."
        } else {
            "Specialized local model for non-chat workloads."
        }
    } else {
        cleaned
    }
}

private fun ModelCardUi.bestUseCases(): String {
    val useCases = bestForTaskTypes.ifEmpty { taskTypes }
    return if (useCases.isEmpty()) {
        if (recommendedForChat) "Chat and assistant conversations" else "Specialized local inference"
    } else {
        useCases.toPrettyList()
    }
}

private fun ModelCardUi.notIdealForUseCases(): String {
    val notes = mutableListOf<String>()
    if (!recommendedForChat) {
        notes += "General chat workflows"
    }
    when (compatibility) {
        is LocalModelCompatibilityState.NeedsMoreRam -> notes += "Devices below ${compatibility.requiredGb} GB RAM"
        is LocalModelCompatibilityState.NeedsMoreStorage -> notes += "Devices with low free storage"
        else -> Unit
    }
    return if (notes.isEmpty()) "No known limitations for this device" else notes.joinToString()
}

private fun List<String>.toPrettyList(): String {
    if (isEmpty()) return "Not specified"
    return joinToString { item ->
        item
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

private fun cleanDescription(raw: String): String {
    if (raw.isBlank()) return ""
    val withoutMarkdownLinks = MarkdownLinkRegex.replace(raw) { match ->
        match.groupValues.getOrNull(1).orEmpty()
    }
    val withoutUrls = UrlRegex.replace(withoutMarkdownLinks, "")
    return withoutUrls
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun toFriendlyError(raw: String?): String {
    val message = raw?.trim().orEmpty()
    if (message.isBlank()) {
        return "This model needs attention. Open details for troubleshooting."
    }

    val lowercase = message.lowercase()
    return when {
        "missing runtime option method" in lowercase ||
                "settopk" in lowercase ||
                "setmaxtokens" in lowercase ||
                "runtime" in lowercase && "unavailable" in lowercase -> {
            "This model could not start on this device."
        }

        "missing" in lowercase && "file" in lowercase -> {
            "This install appears incomplete. Try re-downloading."
        }

        "size mismatch" in lowercase ||
                "unsupported extension" in lowercase ||
                "corrupt" in lowercase -> {
            "This downloaded file may be incompatible with the current runtime."
        }

        "token" in lowercase -> {
            "Add a Hugging Face read token to continue setup."
        }

        else -> message
    }
}

private fun stringResourceSafe(id: Int): String {
    return when (id) {
        R.string.model_corrupted -> "Model file issue"
        R.string.runtime_unavailable -> "Runtime unavailable"
        R.string.requires_hf_token_message -> "Add a Hugging Face read token to continue setup."
        else -> ""
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

private val MarkdownLinkRegex = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")
private val UrlRegex = Regex("https?://\\S+")
