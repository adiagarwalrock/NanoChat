package com.fcm.nanochat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.ErrorOutline
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
import com.fcm.nanochat.model.LocalModelHealthState
import com.fcm.nanochat.model.ModelCardUi
import com.fcm.nanochat.model.ModelGalleryScreenState
import com.fcm.nanochat.model.needsAttention
import com.fcm.nanochat.models.compatibility.LocalModelCompatibilityState
import com.fcm.nanochat.models.registry.ModelInstallState
import com.fcm.nanochat.models.registry.ModelStorageLocation
import kotlin.math.ln
import kotlin.math.pow

private enum class ModelFilter(val labelRes: Int) {
    All(R.string.filter_all),
    ChatReady(R.string.filter_chat_ready),
    Installed(R.string.filter_installed),
    RequiresToken(R.string.filter_requires_token),
    NeedsAttention(R.string.filter_needs_attention)
}

private enum class ModelSort(val labelRes: Int) {
    Recommended(R.string.sort_recommended),
    InstalledFirst(R.string.sort_installed),
    SmallestFirst(R.string.sort_smallest),
    Name(R.string.sort_name)
}

private enum class ActionType {
    Download,
    Resume,
    Retry,
    Use,
    AddToken,
    Cancel,
    Delete,
    Move,
    OpenDetails,
    None
}

private data class ActionPlan(
    val primaryLabel: String,
    val primaryAction: ActionType,
    val primaryEnabled: Boolean,
    val secondaryLabel: String? = null,
    val secondaryAction: ActionType = ActionType.None,
    val secondaryEnabled: Boolean = true,
    val allowOverflow: Boolean = false
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
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
            .filter { it.matchesQuery(query) }
            .filter { it.matchesFilter(selectedFilter) }
            .sortedWith(selectedSort.comparator())
            .toList()
    }
    val recommended = filteredModels.filter { it.recommendedForChat }
    val other = filteredModels.filterNot { it.recommendedForChat }
    val detailsModel = remember(detailsModelId, state.models) {
        state.models.firstOrNull { it.modelId == detailsModelId }
    }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            LibraryHeader(
                allowlistVersion = state.allowlistVersion,
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                onImportLocalModel = onImportLocalModel
            )
        }

        if (state.notice != null) {
            item {
                NoticeBanner(
                    message = toFriendlyError(state.notice),
                    onDismiss = onClearNotice
                )
            }
        }

        state.runtimeMetrics?.let { metrics ->
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(
                            id = R.string.local_runtime_summary,
                            metrics.modelId,
                            metrics.timeToFirstTokenMs,
                            "%.1f".format(metrics.tokensPerSecond)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        stickyHeader {
            Surface(color = MaterialTheme.colorScheme.background) {
                PinnedToolsHeader(
                    query = query,
                    selectedFilter = selectedFilter,
                    selectedSort = selectedSort,
                    onQueryChange = { query = it },
                    onFilterChange = { selectedFilter = it },
                    onSortChange = { selectedSort = it }
                )
            }
        }

        if (recommended.isNotEmpty()) {
            item {
                SectionHeader(
                    title = stringResource(id = R.string.recommended_for_chat),
                    count = recommended.size
                )
            }
            items(recommended, key = { it.modelId }) { model ->
                SimplifiedModelCard(
                    model = model,
                    onOpenDetails = { detailsModelId = model.modelId },
                    onOpenHuggingFaceSettings = onOpenHuggingFaceSettings,
                    onDownload = { onDownload(model.modelId) },
                    onCancelDownload = { onCancelDownload(model.modelId) },
                    onRetryDownload = { onRetryDownload(model.modelId) },
                    onUseModel = { onUseModel(model.modelId) },
                    onDeleteModel = { onDeleteModel(model.modelId) },
                    onMoveStorage = {
                        val target = if (model.storageLocation == ModelStorageLocation.INTERNAL) {
                            ModelStorageLocation.EXTERNAL
                        } else {
                            ModelStorageLocation.INTERNAL
                        }
                        onMoveStorage(model.modelId, target)
                    }
                )
            }
        }

        if (other.isNotEmpty()) {
            item {
                SectionHeader(
                    title = stringResource(id = R.string.other_supported_models),
                    count = other.size
                )
            }
            items(other, key = { it.modelId }) { model ->
                SimplifiedModelCard(
                    model = model,
                    onOpenDetails = { detailsModelId = model.modelId },
                    onOpenHuggingFaceSettings = onOpenHuggingFaceSettings,
                    onDownload = { onDownload(model.modelId) },
                    onCancelDownload = { onCancelDownload(model.modelId) },
                    onRetryDownload = { onRetryDownload(model.modelId) },
                    onUseModel = { onUseModel(model.modelId) },
                    onDeleteModel = { onDeleteModel(model.modelId) },
                    onMoveStorage = {
                        val target = if (model.storageLocation == ModelStorageLocation.INTERNAL) {
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
                        text = stringResource(id = R.string.no_models_match_filters),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp)
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(18.dp)) }
    }

    if (detailsModel != null) {
        RedesignedDetailsSheet(
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
            .padding(horizontal = 16.dp, vertical = 10.dp)
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
                    text = stringResource(
                        id = R.string.allowlist_version,
                        allowlistVersion.ifBlank { stringResource(id = R.string.unknown_value) }
                    ),
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
            Text(text = stringResource(id = R.string.import_local_model_coming_soon))
        }
    }
}

@Composable
private fun NoticeBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
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
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.dismiss))
            }
        }
    }
}

@Composable
private fun PinnedToolsHeader(
    query: String,
    selectedFilter: ModelFilter,
    selectedSort: ModelSort,
    onQueryChange: (String) -> Unit,
    onFilterChange: (ModelFilter) -> Unit,
    onSortChange: (ModelSort) -> Unit
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 6.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            placeholder = { Text(text = stringResource(id = R.string.search_models)) }
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

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
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
private fun SimplifiedModelCard(
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
    val status = remember(model) { model.statusLine() }
    val actionPlan = remember(model) { model.actionPlan() }
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onOpenDetails),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        if (model.isActive) {
                            ModelStatusBadge(
                                text = stringResource(id = R.string.selected_for_chat),
                                tone = ModelBadgeTone.Positive
                            )
                        }
                    }

                    Text(
                        text = model.cardSummary(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (actionPlan.allowOverflow) {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(text = stringResource(id = R.string.move_storage)) },
                            leadingIcon = {
                                Icon(Icons.Default.SwapHoriz, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                onMoveStorage()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(text = stringResource(id = R.string.delete_model)) },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                onDeleteModel()
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ModelStatusBadge(
                    text = humanBytes(model.sizeInBytes),
                    tone = ModelBadgeTone.Neutral
                )
                if (model.minDeviceMemoryInGb > 0) {
                    ModelStatusBadge(
                        text = stringResource(
                            id = R.string.minimum_ram_value,
                            model.minDeviceMemoryInGb
                        ),
                        tone = ModelBadgeTone.Neutral
                    )
                }
                if (model.requiresHfToken) {
                    ModelStatusBadge(
                        text = stringResource(id = R.string.requires_token),
                        tone = ModelBadgeTone.Warning
                    )
                }
                if (!model.recommendedForChat) {
                    ModelStatusBadge(
                        text = stringResource(id = R.string.not_chat_ready),
                        tone = ModelBadgeTone.Warning
                    )
                }
            }

            StatusRow(
                text = status.label,
                supporting = status.supporting,
                tone = status.tone,
                progress = status.progress
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        when (actionPlan.primaryAction) {
                            ActionType.Download -> onDownload()
                            ActionType.Resume,
                            ActionType.Retry -> onRetryDownload()

                            ActionType.Use -> onUseModel()
                            ActionType.AddToken -> onOpenHuggingFaceSettings()
                            ActionType.Cancel -> onCancelDownload()
                            ActionType.Delete -> onDeleteModel()
                            ActionType.Move -> onMoveStorage()
                            ActionType.OpenDetails -> onOpenDetails()
                            ActionType.None -> Unit
                        }
                    },
                    enabled = actionPlan.primaryEnabled,
                    modifier = Modifier.weight(1f)
                ) {
                    if (actionPlan.primaryAction == ActionType.Download) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(text = actionPlan.primaryLabel)
                }

                if (actionPlan.secondaryLabel != null) {
                    OutlinedButton(
                        onClick = {
                            when (actionPlan.secondaryAction) {
                                ActionType.Cancel -> onCancelDownload()
                                ActionType.Delete -> onDeleteModel()
                                ActionType.OpenDetails -> onOpenDetails()
                                else -> Unit
                            }
                        },
                        enabled = actionPlan.secondaryEnabled
                    ) {
                        Text(text = actionPlan.secondaryLabel)
                    }
                }

                TextButton(onClick = onOpenDetails) {
                    Text(text = stringResource(id = R.string.view_details))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RedesignedDetailsSheet(
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
    val status = remember(model) { model.statusLine() }
    var showDiagnostics by rememberSaveable(model.modelId) { mutableStateOf(false) }

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
                StatusRow(
                    text = status.label,
                    supporting = status.supporting,
                    tone = status.tone,
                    progress = status.progress
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            when (actionPlan.primaryAction) {
                                ActionType.Download -> onDownload()
                                ActionType.Resume,
                                ActionType.Retry -> onRetryDownload()

                                ActionType.Use -> onUseModel()
                                ActionType.AddToken -> onOpenHuggingFaceSettings()
                                ActionType.Cancel -> onCancelDownload()
                                ActionType.Delete -> onDeleteModel()
                                ActionType.Move -> onMoveStorage()
                                ActionType.OpenDetails,
                                ActionType.None -> Unit
                            }
                        },
                        enabled = actionPlan.primaryEnabled,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = actionPlan.primaryLabel)
                    }
                    if (actionPlan.secondaryLabel != null) {
                        OutlinedButton(
                            onClick = {
                                when (actionPlan.secondaryAction) {
                                    ActionType.Cancel -> onCancelDownload()
                                    ActionType.Delete -> onDeleteModel()
                                    ActionType.OpenDetails -> Unit
                                    else -> Unit
                                }
                            },
                            enabled = actionPlan.secondaryEnabled
                        ) {
                            Text(text = actionPlan.secondaryLabel)
                        }
                    }
                }
            }

            item {
                DetailsSection(title = stringResource(id = R.string.details_section_readiness)) {
                    DetailRow(
                        label = stringResource(id = R.string.details_health_state),
                        value = model.healthStateLabel()
                    )
                    DetailRow(
                        label = stringResource(id = R.string.details_install_state),
                        value = model.installStateLabel()
                    )
                    DetailRow(
                        label = stringResource(id = R.string.details_compatibility),
                        value = model.compatibilitySummary()
                    )
                    DetailRow(
                        label = stringResource(id = R.string.details_startup_validation),
                        value = model.startupValidationResult()
                    )
                    DetailRow(
                        label = stringResource(id = R.string.details_startup_file_path),
                        value = model.localPath?.takeIf { it.isNotBlank() }
                            ?: stringResource(id = R.string.details_not_available)
                    )
                    DetailRow(
                        label = stringResource(id = R.string.details_detected_format),
                        value = model.detectedFileFormat()
                    )
                    DetailRow(
                        label = stringResource(id = R.string.details_root_cause),
                        value = model.rootCauseDetail()
                            ?: stringResource(id = R.string.details_not_available)
                    )
                }
            }

            item {
                DetailsSection(title = stringResource(id = R.string.details_section_use)) {
                    DetailRow(
                        label = stringResource(id = R.string.details_good_for),
                        value = model.bestUseCases()
                    )
                    DetailRow(
                        label = stringResource(id = R.string.details_not_ideal_for),
                        value = model.notIdealForUseCases()
                    )
                    DetailRow(
                        label = stringResource(id = R.string.details_task_support),
                        value = model.taskTypes.toPrettyList()
                    )
                }
            }

            item {
                DetailsSection(title = stringResource(id = R.string.details_section_requirements)) {
                    DetailRow(
                        label = stringResource(id = R.string.details_storage_estimate),
                        value = humanBytes(model.sizeInBytes)
                    )
                    DetailRow(
                        label = stringResource(id = R.string.details_minimum_ram),
                        value = stringResource(
                            id = R.string.minimum_ram_value,
                            model.minDeviceMemoryInGb
                        )
                    )
                    DetailRow(
                        label = stringResource(id = R.string.details_token_requirement),
                        value = if (model.requiresHfToken) {
                            stringResource(id = R.string.details_token_required)
                        } else {
                            stringResource(id = R.string.details_token_not_required)
                        }
                    )
                }
            }

            item {
                DetailsSection(title = stringResource(id = R.string.details_section_source_and_file)) {
                    DetailRow(
                        label = stringResource(id = R.string.details_source),
                        value = model.sourceRepo
                    )
                    DetailRow(
                        label = stringResource(id = R.string.details_runtime_defaults),
                        value = stringResource(
                            id = R.string.details_runtime_defaults_value,
                            model.defaultTopK,
                            model.defaultTopP,
                            model.defaultTemperature,
                            model.defaultMaxTokens
                        )
                    )
                    if (!model.localPath.isNullOrBlank()) {
                        DetailRow(
                            label = stringResource(id = R.string.details_installed_path),
                            value = model.localPath.orEmpty()
                        )
                    }
                }
            }

            item {
                DetailsSection(title = stringResource(id = R.string.details_section_diagnostics)) {
                    if (!model.errorMessage.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = toFriendlyError(model.errorMessage),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        TextButton(onClick = { showDiagnostics = !showDiagnostics }) {
                            Text(
                                text = if (showDiagnostics) {
                                    stringResource(id = R.string.hide_diagnostics)
                                } else {
                                    stringResource(id = R.string.show_diagnostics)
                                }
                            )
                        }
                        AnimatedVisibility(visible = showDiagnostics) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = model.errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = stringResource(id = R.string.no_diagnostics_available),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun DetailsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private enum class ModelBadgeTone {
    Positive,
    Neutral,
    Warning,
    Error
}

@Composable
private fun ModelStatusBadge(text: String, tone: ModelBadgeTone) {
    val bg = when (tone) {
        ModelBadgeTone.Positive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        ModelBadgeTone.Neutral -> MaterialTheme.colorScheme.surfaceContainerHighest
        ModelBadgeTone.Warning -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
        ModelBadgeTone.Error -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
    }
    val fg = when (tone) {
        ModelBadgeTone.Positive -> MaterialTheme.colorScheme.primary
        ModelBadgeTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        ModelBadgeTone.Warning -> MaterialTheme.colorScheme.tertiary
        ModelBadgeTone.Error -> MaterialTheme.colorScheme.error
    }

    Surface(shape = RoundedCornerShape(999.dp), color = bg) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private data class StatusLine(
    val label: String,
    val supporting: String,
    val tone: ModelBadgeTone,
    val progress: Float? = null
)

@Composable
private fun StatusRow(
    text: String,
    supporting: String,
    tone: ModelBadgeTone,
    progress: Float?
) {
    val container = when (tone) {
        ModelBadgeTone.Positive -> MaterialTheme.colorScheme.secondaryContainer
        ModelBadgeTone.Neutral -> MaterialTheme.colorScheme.surfaceContainer
        ModelBadgeTone.Warning -> MaterialTheme.colorScheme.tertiaryContainer
        ModelBadgeTone.Error -> MaterialTheme.colorScheme.errorContainer
    }
    val content = when (tone) {
        ModelBadgeTone.Positive -> MaterialTheme.colorScheme.onSecondaryContainer
        ModelBadgeTone.Neutral -> MaterialTheme.colorScheme.onSurface
        ModelBadgeTone.Warning -> MaterialTheme.colorScheme.onTertiaryContainer
        ModelBadgeTone.Error -> MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = container,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = text, style = MaterialTheme.typography.labelLarge, color = content)
            Text(text = supporting, style = MaterialTheme.typography.bodySmall, color = content)
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }
        }
    }
}

private fun ModelCardUi.matchesQuery(query: String): Boolean {
    val q = query.trim().lowercase()
    if (q.isBlank()) return true
    return displayName.lowercase().contains(q) ||
            sourceRepo.lowercase().contains(q) ||
            description.lowercase().contains(q)
}

private fun ModelCardUi.matchesFilter(filter: ModelFilter): Boolean {
    return when (filter) {
        ModelFilter.All -> true
        ModelFilter.ChatReady -> recommendedForChat
        ModelFilter.Installed -> installState == ModelInstallState.INSTALLED
        ModelFilter.RequiresToken -> healthState == LocalModelHealthState.RequiresToken
        ModelFilter.NeedsAttention -> healthState.needsAttention
    }
}

private fun ModelSort.comparator(): Comparator<ModelCardUi> {
    return when (this) {
        ModelSort.Recommended -> compareByDescending<ModelCardUi> { it.isActive }
            .thenByDescending { it.recommendedForChat }
            .thenBy { it.compatibilityRank() }
            .thenBy { it.displayName.lowercase() }

        ModelSort.InstalledFirst -> compareByDescending<ModelCardUi> {
            it.installState == ModelInstallState.INSTALLED
        }
            .thenByDescending { it.isActive }
            .thenBy { it.compatibilityRank() }
            .thenBy { it.displayName.lowercase() }

        ModelSort.SmallestFirst -> compareBy<ModelCardUi> { it.sizeInBytes }
            .thenBy { it.displayName.lowercase() }

        ModelSort.Name -> compareBy { it.displayName.lowercase() }
    }
}

private fun ModelCardUi.compatibilityRank(): Int {
    return when (compatibility) {
        LocalModelCompatibilityState.Ready -> 0
        LocalModelCompatibilityState.Downloadable -> 1
        LocalModelCompatibilityState.TokenRequired -> 2
        is LocalModelCompatibilityState.DownloadedButNotActivatable -> 3
        is LocalModelCompatibilityState.RuntimeUnavailable -> 4
        LocalModelCompatibilityState.UnsupportedForChat -> 5
        is LocalModelCompatibilityState.NeedsMoreStorage -> 6
        is LocalModelCompatibilityState.NeedsMoreRam -> 7
        is LocalModelCompatibilityState.UnsupportedDevice -> 8
        LocalModelCompatibilityState.CorruptedModel -> 9
    }
}

private fun ModelCardUi.statusLine(): StatusLine {
    val totalBytes = sizeInBytes.takeIf { it > 0L } ?: sizeOnDiskBytes
    val progressValue = if (totalBytes > 0L) {
        downloadedBytes.toFloat() / totalBytes.toFloat()
    } else {
        null
    }

    return when (val state = healthState) {
        LocalModelHealthState.NotInstalled -> StatusLine(
            label = "Not installed",
            supporting = "Download to use this model in local chat.",
            tone = ModelBadgeTone.Neutral
        )

        is LocalModelHealthState.Downloading -> StatusLine(
            label = "Downloading",
            supporting = "${humanBytes(state.downloadedBytes)} of ${humanBytes(state.totalBytes)}",
            tone = ModelBadgeTone.Neutral,
            progress = progressValue
        )

        is LocalModelHealthState.Paused -> StatusLine(
            label = "Download paused",
            supporting = "Resume when you are ready.",
            tone = ModelBadgeTone.Warning,
            progress = progressValue
        )

        is LocalModelHealthState.DownloadFailed -> StatusLine(
            label = "Download failed",
            supporting = toFriendlyError(state.message),
            tone = ModelBadgeTone.Error
        )

        LocalModelHealthState.InstalledNeedsValidation -> StatusLine(
            label = "Finalizing setup",
            supporting = "Validating and preparing this model for chat.",
            tone = ModelBadgeTone.Neutral
        )

        LocalModelHealthState.InstalledReady -> StatusLine(
            label = if (isActive) "Selected for chat" else "Ready to use",
            supporting = if (isActive) {
                "NanoChat will use this model for on-device responses."
            } else {
                "Installed and compatible with this device."
            },
            tone = ModelBadgeTone.Positive
        )

        is LocalModelHealthState.InstalledStartupFailed -> StatusLine(
            label = "Installed, but NanoChat could not start this model",
            supporting = toFriendlyError(state.message),
            tone = ModelBadgeTone.Warning
        )

        LocalModelHealthState.RequiresToken -> StatusLine(
            label = "Token required",
            supporting = "Add a Hugging Face read token to continue setup.",
            tone = ModelBadgeTone.Warning
        )

        LocalModelHealthState.RequiresLicenseApproval -> StatusLine(
            label = "Access approval required",
            supporting = "Approve model access on Hugging Face, then retry.",
            tone = ModelBadgeTone.Warning
        )

        is LocalModelHealthState.NotCompatible -> StatusLine(
            label = "Not compatible",
            supporting = state.message,
            tone = ModelBadgeTone.Warning
        )

        LocalModelHealthState.UnsupportedForChat -> StatusLine(
            label = "Not optimized for chat",
            supporting = "This model can run but is not tuned for conversational chat.",
            tone = ModelBadgeTone.Warning
        )
    }
}

private fun ModelCardUi.actionPlan(): ActionPlan {
    return when (healthState) {
        LocalModelHealthState.NotInstalled -> ActionPlan(
            primaryLabel = "Download",
            primaryAction = ActionType.Download,
            primaryEnabled = true
        )

        is LocalModelHealthState.Downloading -> ActionPlan(
            primaryLabel = "Downloading",
            primaryAction = ActionType.None,
            primaryEnabled = false,
            secondaryLabel = "Cancel",
            secondaryAction = ActionType.Cancel
        )

        is LocalModelHealthState.Paused -> ActionPlan(
            primaryLabel = "Resume",
            primaryAction = ActionType.Resume,
            primaryEnabled = true,
            secondaryLabel = "Delete",
            secondaryAction = ActionType.Delete
        )

        is LocalModelHealthState.DownloadFailed -> ActionPlan(
            primaryLabel = "Retry",
            primaryAction = ActionType.Retry,
            primaryEnabled = true,
            secondaryLabel = "Delete",
            secondaryAction = ActionType.Delete
        )

        LocalModelHealthState.InstalledNeedsValidation -> ActionPlan(
            primaryLabel = "Continue setup",
            primaryAction = ActionType.OpenDetails,
            primaryEnabled = true
        )

        LocalModelHealthState.InstalledReady -> {
            if (isActive) {
                ActionPlan(
                    primaryLabel = "Selected",
                    primaryAction = ActionType.None,
                    primaryEnabled = false,
                    allowOverflow = true
                )
            } else {
                ActionPlan(
                    primaryLabel = "Use model",
                    primaryAction = ActionType.Use,
                    primaryEnabled = true,
                    allowOverflow = true
                )
            }
        }

        is LocalModelHealthState.InstalledStartupFailed -> ActionPlan(
            primaryLabel = "Continue setup",
            primaryAction = ActionType.OpenDetails,
            primaryEnabled = true,
            secondaryLabel = "Delete",
            secondaryAction = ActionType.Delete,
            allowOverflow = true
        )

        LocalModelHealthState.RequiresToken -> ActionPlan(
            primaryLabel = "Add token",
            primaryAction = ActionType.AddToken,
            primaryEnabled = true,
            secondaryLabel = "View details",
            secondaryAction = ActionType.OpenDetails
        )

        LocalModelHealthState.RequiresLicenseApproval -> ActionPlan(
            primaryLabel = "View details",
            primaryAction = ActionType.OpenDetails,
            primaryEnabled = true,
            secondaryLabel = "Delete",
            secondaryAction = ActionType.Delete
        )

        is LocalModelHealthState.NotCompatible,
        LocalModelHealthState.UnsupportedForChat -> ActionPlan(
            primaryLabel = "View details",
            primaryAction = ActionType.OpenDetails,
            primaryEnabled = true
        )
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

private fun ModelCardUi.healthStateLabel(): String {
    return when (val state = healthState) {
        LocalModelHealthState.NotInstalled -> "Not installed"
        is LocalModelHealthState.Downloading -> "Downloading"
        is LocalModelHealthState.Paused -> "Paused"
        is LocalModelHealthState.DownloadFailed -> "Download failed"
        LocalModelHealthState.InstalledNeedsValidation -> "Needs validation"
        LocalModelHealthState.InstalledReady -> "Installed and ready"
        is LocalModelHealthState.InstalledStartupFailed -> "Startup failed"
        LocalModelHealthState.RequiresToken -> "Token required"
        LocalModelHealthState.RequiresLicenseApproval -> "License approval required"
        is LocalModelHealthState.NotCompatible -> "Not compatible"
        LocalModelHealthState.UnsupportedForChat -> "Unsupported for chat"
    }
}

private fun ModelCardUi.compatibilitySummary(): String {
    return when (val value = compatibility) {
        LocalModelCompatibilityState.Ready -> "Ready on this device"
        LocalModelCompatibilityState.Downloadable -> "Download ready"
        is LocalModelCompatibilityState.NeedsMoreStorage -> "Needs more free storage"
        is LocalModelCompatibilityState.NeedsMoreRam -> "Needs more RAM"
        is LocalModelCompatibilityState.UnsupportedDevice -> toFriendlyError(value.reason)
        LocalModelCompatibilityState.UnsupportedForChat -> "Not optimized for chat"
        LocalModelCompatibilityState.TokenRequired -> "Requires Hugging Face token"
        is LocalModelCompatibilityState.DownloadedButNotActivatable -> toFriendlyError(value.reason)
        LocalModelCompatibilityState.CorruptedModel -> "Model file issue"
        is LocalModelCompatibilityState.RuntimeUnavailable -> toFriendlyError(value.reason)
    }
}

private fun ModelCardUi.cardSummary(maxLength: Int = 120): String {
    val cleaned = cleanDescription(description)
    if (cleaned.isBlank()) {
        return if (recommendedForChat) {
            "Chat model tuned for on-device responses."
        } else {
            "General local model for specialized tasks."
        }
    }
    return cleaned.take(maxLength).trimEnd().let { text ->
        if (text.length < cleaned.length) "$text..." else text
    }
}

private fun ModelCardUi.fullDescription(): String {
    val cleaned = cleanDescription(description)
    if (cleaned.isBlank()) {
        return if (recommendedForChat) {
            "Optimized for on-device chat responses in NanoChat."
        } else {
            "Specialized local model for non-chat workloads."
        }
    }
    return cleaned
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
        LocalModelCompatibilityState.UnsupportedForChat -> notes += "Conversational chat tasks"
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

        "startup_validation_failed" in lowercase ||
                "flatbuffer" in lowercase ||
                "error building tflite model" in lowercase -> {
            "Installed, but NanoChat could not start this model."
        }

        "invocationtargetexception" in lowercase -> {
            "Installed, but NanoChat could not start this model."
        }

        "token" in lowercase -> {
            "Add a Hugging Face read token to continue setup."
        }

        "access approval" in lowercase ||
                "does not have access" in lowercase ||
                "license" in lowercase -> {
            "Approve this model on Hugging Face, then retry."
        }

        else -> message
    }
}

private fun humanBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val unit = 1000.0
    val exp = (ln(bytes.toDouble()) / ln(unit)).toInt().coerceAtLeast(0)
    if (exp == 0) return "$bytes B"
    val prefix = "kMGTPE".getOrElse(exp - 1) { 'E' }
    val value = bytes / unit.pow(exp.toDouble())
    return "%.1f %sB".format(value, prefix)
}

private fun ModelCardUi.detectedFileFormat(): String {
    val fromError = errorMessage
        ?.substringAfter("format=", missingDelimiterValue = "")
        ?.substringBefore(';')
        ?.trim()
        .orEmpty()
    if (fromError.isNotBlank()) return fromError

    val extension = localPath
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.lowercase()
        ?.ifBlank { null }
        ?: modelFile.substringAfterLast('.', missingDelimiterValue = "").lowercase()

    return when (extension) {
        "litertlm" -> "litertlm-package"
        "tflite" -> "tflite-flatbuffer"
        "task" -> "mediapipe-task"
        "" -> "unknown"
        else -> extension
    }
}

private fun ModelCardUi.startupValidationResult(): String {
    return when (healthState) {
        LocalModelHealthState.InstalledReady -> "Runnable"
        LocalModelHealthState.InstalledNeedsValidation -> "Validation pending"
        is LocalModelHealthState.InstalledStartupFailed -> "Startup failed"
        LocalModelHealthState.RequiresToken -> "Blocked: token required"
        LocalModelHealthState.RequiresLicenseApproval -> "Blocked: access approval required"
        LocalModelHealthState.NotInstalled,
        is LocalModelHealthState.Downloading,
        is LocalModelHealthState.Paused,
        is LocalModelHealthState.DownloadFailed -> "Not installed"

        is LocalModelHealthState.NotCompatible,
        LocalModelHealthState.UnsupportedForChat -> "Not compatible"
    }
}

private fun ModelCardUi.rootCauseDetail(): String? {
    val raw = errorMessage?.trim().orEmpty()
    if (raw.isBlank()) return null

    val parsed = raw.substringAfter("rootCause=", missingDelimiterValue = "")
        .substringBefore(';')
        .trim()
    if (parsed.isNotBlank()) {
        return parsed
    }

    val sentence = raw.substringAfter("Root cause:", missingDelimiterValue = "").trim()
    if (sentence.isNotBlank()) {
        return sentence
    }

    return raw
}

private val MarkdownLinkRegex = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")
private val UrlRegex = Regex("https?://\\S+")
