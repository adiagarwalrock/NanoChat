package com.fcm.nanochat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fcm.nanochat.data.repository.LocalModelRepository
import com.fcm.nanochat.model.ActiveLocalModelSummaryUi
import com.fcm.nanochat.model.LocalModelHealthState
import com.fcm.nanochat.model.LocalModelMemoryState
import com.fcm.nanochat.model.ModelCardUi
import com.fcm.nanochat.model.ModelGalleryScreenState
import com.fcm.nanochat.model.ModelLibraryPhase
import com.fcm.nanochat.model.RuntimeDiagnosticsUi
import com.fcm.nanochat.models.compatibility.LocalModelCompatibilityState
import com.fcm.nanochat.models.registry.ModelInstallState
import com.fcm.nanochat.models.registry.ModelStorageLocation
import com.fcm.nanochat.models.runtime.RuntimeLoadPhase
import com.fcm.nanochat.models.runtime.RuntimeLoadState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ModelManagerViewModel(
    private val localModelRepository: LocalModelRepository
) : ViewModel() {
    private val notice = MutableStateFlow<String?>(null)
    private val isRefreshing = MutableStateFlow(false)

    init {
        localModelRepository.reconcile()
    }

    val uiState: StateFlow<ModelGalleryScreenState> = combine(
        localModelRepository.allowlist,
        localModelRepository.records,
        localModelRepository.activeModelStatus,
        localModelRepository.runtimeLoadState,
        notice
    ) { allowlist, records, activeStatus, runtimeLoadState, noticeValue ->
        GalleryInputs(
            allowlist = allowlist,
            records = records,
            activeStatus = activeStatus,
            runtimeLoadState = runtimeLoadState,
            notice = noticeValue
        )
    }.combine(isRefreshing) { inputs, refreshing ->
        val runtimeMetrics = localModelRepository.latestRuntimeMetrics()
        val cardItems = inputs.records
            .map { record ->
                val model = record.allowlistedModel
                val healthState = mapHealthState(record)
                val memory = mapMemoryState(
                    record = record,
                    runtimeLoadState = inputs.runtimeLoadState,
                    runtimeMetrics = runtimeMetrics
                )
                val diagnosticsMessage = when (val compatibility = record.compatibility) {
                    LocalModelCompatibilityState.Ready -> null
                    is LocalModelCompatibilityState.RuntimeUnavailable -> compatibility.reason
                    is LocalModelCompatibilityState.DownloadedButNotActivatable -> compatibility.reason
                    else -> record.errorMessage
                }
                ModelCardUi(
                    modelId = record.modelId,
                    displayName = record.displayName,
                    description = model?.description ?: "Legacy local model",
                    modelFile = model?.modelFile ?: record.localPath.orEmpty(),
                    sourceRepo = model?.sourceRepo ?: "Imported",
                    sizeInBytes = model?.sizeInBytes ?: record.sizeBytes,
                    minDeviceMemoryInGb = model?.minDeviceMemoryInGb ?: 0,
                    taskTypes = model?.taskTypes.orEmpty(),
                    bestForTaskTypes = model?.bestForTaskTypes.orEmpty(),
                    llmSupportImage = model?.llmSupportImage ?: false,
                    llmSupportAudio = model?.llmSupportAudio ?: false,
                    defaultTopK = model?.defaultConfig?.topK ?: 40,
                    defaultTopP = model?.defaultConfig?.topP ?: 0.9,
                    defaultTemperature = model?.defaultConfig?.temperature ?: 0.7,
                    defaultMaxTokens = model?.defaultConfig?.maxTokens ?: 1024,
                    acceleratorHints = model?.acceleratorHints.orEmpty(),
                    requiresHfToken = model?.requiresHfToken ?: false,
                    recommendedForChat = model?.recommendedForChat ?: true,
                    isExperimental = model?.isExperimental ?: false,
                    installState = record.installState,
                    compatibility = record.compatibility,
                    healthState = healthState,
                    memoryState = memory.state,
                    memoryMessage = memory.message,
                    isActive = record.isActive,
                    isLegacy = record.isLegacy,
                    storageLocation = record.storageLocation,
                    downloadedBytes = record.downloadedBytes,
                    sizeOnDiskBytes = record.sizeBytes,
                    localPath = record.localPath,
                    errorMessage = diagnosticsMessage
                )
            }
            .sortedWith(
                compareByDescending<ModelCardUi> { it.isActive }
                    .thenByDescending { it.recommendedForChat }
                    .thenBy { it.displayName.lowercase() }
            )

        val phase = when {
            inputs.allowlist.models.isEmpty() && inputs.allowlist.version.value.isBlank() -> {
                ModelLibraryPhase.Loading
            }

            inputs.allowlist.models.isNotEmpty() && cardItems.size < inputs.allowlist.models.size -> {
                ModelLibraryPhase.Loading
            }

            cardItems.isEmpty() && !inputs.allowlist.lastRefreshError.isNullOrBlank() -> {
                ModelLibraryPhase.Error
            }

            else -> ModelLibraryPhase.Ready
        }

        val activeSummary = buildActiveSummary(
            cards = cardItems,
            activeModelId = inputs.activeStatus.modelId,
            runtimeMetrics = runtimeMetrics
        )

        ModelGalleryScreenState(
            allowlistVersion = inputs.allowlist.version.value,
            allowlistSource = inputs.allowlist.sourceType,
            allowlistRefreshedAtEpochMs = inputs.allowlist.version.refreshedAtEpochMs,
            phase = phase,
            activeModelId = inputs.activeStatus.modelId,
            activeSummary = activeSummary,
            models = cardItems,
            libraryError = inputs.allowlist.lastRefreshError,
            runtimeMetrics = runtimeMetrics?.toUi(),
            notice = inputs.notice,
            isRefreshing = refreshing
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ModelGalleryScreenState()
    )

    fun refreshAllowlist() {
        viewModelScope.launch {
            isRefreshing.value = true
            localModelRepository.refreshAllowlist()
            delay(250)
            isRefreshing.value = false
        }
    }

    fun downloadModel(modelId: String) {
        localModelRepository.downloadModel(modelId)
    }

    fun cancelDownload(modelId: String) {
        localModelRepository.cancelDownload(modelId)
    }

    fun retryDownload(modelId: String) {
        localModelRepository.retryDownload(modelId)
    }

    fun deleteModel(modelId: String) {
        localModelRepository.deleteModel(modelId)
    }

    fun useModel(modelId: String) {
        viewModelScope.launch {
            val model = localModelRepository.records.value.firstOrNull { it.modelId == modelId }
            if (model == null) {
                notice.update { "This model is not ready yet. Finish setup and try again." }
                return@launch
            }

            val healthState = mapHealthState(model)
            if (healthState is LocalModelHealthState.UnsupportedForChat) {
                notice.update { "This model is not optimized for chat in NanoChat." }
                return@launch
            }
            if (healthState !is LocalModelHealthState.InstalledReady) {
                notice.update { "This model is not ready yet. Finish setup and try again." }
                return@launch
            }

            localModelRepository.setActiveModel(modelId)
            localModelRepository.prepareModelInMemory(modelId)
        }
    }

    fun ejectModel(modelId: String) {
        viewModelScope.launch {
            val error = localModelRepository.ejectModelFromMemory(modelId)
            if (!error.isNullOrBlank()) {
                notice.update { error }
            }
        }
    }

    fun moveStorage(modelId: String, target: ModelStorageLocation) {
        localModelRepository.moveModel(modelId, target)
    }

    fun importLocalModel() {
        viewModelScope.launch {
            when (localModelRepository.validateImport("placeholder.litertlm")) {
                is com.fcm.nanochat.models.importing.ImportValidationResult.UnsupportedForNow -> {
                    notice.value = "Import local model is coming soon."
                }

                is com.fcm.nanochat.models.importing.ImportValidationResult.Valid -> {
                    notice.value = "Import flow will be added in a future update."
                }

                is com.fcm.nanochat.models.importing.ImportValidationResult.Invalid -> {
                    notice.value = "Import is unavailable for now."
                }
            }
        }
    }

    fun clearNotice() {
        notice.value = null
    }

    private fun mapHealthState(record: com.fcm.nanochat.models.registry.InstalledModelRecord): LocalModelHealthState {
        val compatibility = record.compatibility
        val issueMessage = record.errorMessage?.trim().orEmpty()

        return when (record.installState) {
            ModelInstallState.NOT_INSTALLED -> {
                when {
                    compatibility is LocalModelCompatibilityState.TokenRequired -> {
                        LocalModelHealthState.RequiresToken
                    }

                    compatibility is LocalModelCompatibilityState.UnsupportedForChat -> {
                        LocalModelHealthState.UnsupportedForChat
                    }

                    compatibility is LocalModelCompatibilityState.NeedsMoreRam -> {
                        LocalModelHealthState.NotCompatible(
                            "Requires ${compatibility.requiredGb} GB RAM."
                        )
                    }

                    compatibility is LocalModelCompatibilityState.NeedsMoreStorage -> {
                        LocalModelHealthState.NotCompatible("Not enough free storage.")
                    }

                    compatibility is LocalModelCompatibilityState.UnsupportedDevice -> {
                        LocalModelHealthState.NotCompatible(compatibility.reason)
                    }

                    indicatesLicenseApproval(issueMessage) -> {
                        LocalModelHealthState.RequiresLicenseApproval
                    }

                    else -> LocalModelHealthState.NotInstalled
                }
            }

            ModelInstallState.QUEUED,
            ModelInstallState.DOWNLOADING -> {
                LocalModelHealthState.Downloading(
                    downloadedBytes = record.downloadedBytes,
                    totalBytes = record.sizeBytes
                )
            }

            ModelInstallState.PAUSED -> {
                LocalModelHealthState.Paused(
                    downloadedBytes = record.downloadedBytes,
                    totalBytes = record.sizeBytes
                )
            }

            ModelInstallState.FAILED -> {
                when {
                    compatibility is LocalModelCompatibilityState.TokenRequired -> {
                        LocalModelHealthState.RequiresToken
                    }

                    indicatesLicenseApproval(issueMessage) -> {
                        LocalModelHealthState.RequiresLicenseApproval
                    }

                    else -> {
                        LocalModelHealthState.DownloadFailed(
                            message = issueMessage.ifBlank { "Download failed." }
                        )
                    }
                }
            }

            ModelInstallState.VALIDATING,
            ModelInstallState.MOVING,
            ModelInstallState.DELETING -> LocalModelHealthState.InstalledNeedsValidation

            ModelInstallState.INSTALLED -> {
                when {
                    compatibility is LocalModelCompatibilityState.Ready -> {
                        LocalModelHealthState.InstalledReady
                    }

                    compatibility is LocalModelCompatibilityState.TokenRequired -> {
                        LocalModelHealthState.RequiresToken
                    }

                    compatibility is LocalModelCompatibilityState.UnsupportedForChat -> {
                        LocalModelHealthState.UnsupportedForChat
                    }

                    compatibility is LocalModelCompatibilityState.NeedsMoreRam -> {
                        LocalModelHealthState.NotCompatible(
                            "Requires ${compatibility.requiredGb} GB RAM."
                        )
                    }

                    compatibility is LocalModelCompatibilityState.NeedsMoreStorage -> {
                        LocalModelHealthState.NotCompatible("Not enough free storage.")
                    }

                    compatibility is LocalModelCompatibilityState.UnsupportedDevice -> {
                        LocalModelHealthState.NotCompatible(compatibility.reason)
                    }

                    compatibility is LocalModelCompatibilityState.CorruptedModel -> {
                        LocalModelHealthState.InstalledStartupFailed("This model file appears corrupted.")
                    }

                    compatibility is LocalModelCompatibilityState.RuntimeUnavailable -> {
                        LocalModelHealthState.InstalledStartupFailed(
                            compatibility.reason.ifBlank {
                                "NanoChat could not start this model on your device."
                            }
                        )
                    }

                    compatibility is LocalModelCompatibilityState.DownloadedButNotActivatable -> {
                        LocalModelHealthState.InstalledStartupFailed(
                            compatibility.reason.ifBlank {
                                "NanoChat could not start this model on your device."
                            }
                        )
                    }

                    indicatesLicenseApproval(issueMessage) -> {
                        LocalModelHealthState.RequiresLicenseApproval
                    }

                    else -> {
                        LocalModelHealthState.InstalledStartupFailed(
                            issueMessage.ifBlank {
                                "NanoChat could not start this model on your device."
                            }
                        )
                    }
                }
            }

            ModelInstallState.BROKEN -> {
                LocalModelHealthState.InstalledStartupFailed(
                    message = issueMessage.ifBlank { "This model file appears corrupted." }
                )
            }
        }
    }

    private fun mapMemoryState(
        record: com.fcm.nanochat.models.registry.InstalledModelRecord,
        runtimeLoadState: RuntimeLoadState,
        runtimeMetrics: com.fcm.nanochat.models.runtime.LocalRuntimeMetrics?
    ): ModelMemoryUi {
        val recordId = record.modelId.lowercase()
        val runtimeModelId = runtimeLoadState.modelId?.lowercase()
        val recentlyUsed = runtimeMetrics != null &&
                runtimeMetrics.modelId.equals(record.modelId, ignoreCase = true) &&
                System.currentTimeMillis() - runtimeMetrics.measuredAtEpochMs <= RECENT_USE_WINDOW_MS

        if (!record.isActive) {
            return ModelMemoryUi(LocalModelMemoryState.NotSelected, null)
        }

        return when (runtimeLoadState.phase) {
            RuntimeLoadPhase.IDLE -> {
                ModelMemoryUi(LocalModelMemoryState.SelectedNotLoaded, null)
            }

            RuntimeLoadPhase.LOADING -> {
                if (runtimeModelId == recordId) {
                    ModelMemoryUi(
                        LocalModelMemoryState.LoadingIntoMemory,
                        "Loading into memory"
                    )
                } else {
                    ModelMemoryUi(LocalModelMemoryState.SelectedNotLoaded, null)
                }
            }

            RuntimeLoadPhase.LOADED -> {
                if (runtimeModelId == recordId) {
                    if (recentlyUsed) {
                        ModelMemoryUi(LocalModelMemoryState.InUse, "In use")
                    } else {
                        ModelMemoryUi(LocalModelMemoryState.LoadedInMemory, "Loaded in memory")
                    }
                } else {
                    ModelMemoryUi(LocalModelMemoryState.SelectedNotLoaded, null)
                }
            }

            RuntimeLoadPhase.FAILED -> {
                if (runtimeModelId == recordId || runtimeModelId == null) {
                    ModelMemoryUi(
                        LocalModelMemoryState.FailedToLoad,
                        runtimeLoadState.message
                    )
                } else {
                    ModelMemoryUi(LocalModelMemoryState.NeedsReload, null)
                }
            }

            RuntimeLoadPhase.EJECTED -> {
                if (runtimeModelId == recordId) {
                    ModelMemoryUi(LocalModelMemoryState.EjectedFromMemory, "Ejected from memory")
                } else {
                    ModelMemoryUi(LocalModelMemoryState.SelectedNotLoaded, null)
                }
            }
        }
    }

    private fun buildActiveSummary(
        cards: List<ModelCardUi>,
        activeModelId: String?,
        runtimeMetrics: com.fcm.nanochat.models.runtime.LocalRuntimeMetrics?
    ): ActiveLocalModelSummaryUi? {
        val activeId = activeModelId?.trim()?.lowercase().orEmpty()
        if (activeId.isBlank()) return null

        val model =
            cards.firstOrNull { it.modelId.equals(activeId, ignoreCase = true) } ?: return null
        val metricsText = runtimeMetrics
            ?.takeIf { it.modelId.equals(model.modelId, ignoreCase = true) }
            ?.let { "TTFB ${it.timeToFirstTokenMs} ms - ${"%.1f".format(it.tokensPerSecond)} tok/s" }

        return ActiveLocalModelSummaryUi(
            modelId = model.modelId,
            displayName = model.displayName,
            memoryState = model.memoryState,
            statusText = when (model.memoryState) {
                LocalModelMemoryState.NotSelected -> "Not selected"
                LocalModelMemoryState.SelectedNotLoaded -> "Selected, not loaded"
                LocalModelMemoryState.LoadingIntoMemory -> "Loading into memory"
                LocalModelMemoryState.LoadedInMemory -> "Loaded in memory"
                LocalModelMemoryState.InUse -> "In use"
                LocalModelMemoryState.EjectedFromMemory -> "Ejected from memory"
                LocalModelMemoryState.NeedsReload -> "Needs reload"
                LocalModelMemoryState.FailedToLoad -> "Failed to load"
            },
            metricsText = metricsText
        )
    }

    private fun indicatesLicenseApproval(message: String): Boolean {
        if (message.isBlank()) return false
        val lowercase = message.lowercase()
        return "access approval" in lowercase ||
                "does not have access" in lowercase ||
                "license" in lowercase
    }

    private fun com.fcm.nanochat.models.runtime.LocalRuntimeMetrics.toUi(): RuntimeDiagnosticsUi {
        return RuntimeDiagnosticsUi(
            modelId = modelId,
            initDurationMs = initDurationMs,
            timeToFirstTokenMs = timeToFirstTokenMs,
            generationDurationMs = generationDurationMs,
            tokensPerSecond = tokensPerSecond,
            backend = backend
        )
    }

    private data class ModelMemoryUi(
        val state: LocalModelMemoryState,
        val message: String?
    )

    private data class GalleryInputs(
        val allowlist: com.fcm.nanochat.models.allowlist.AllowlistSnapshot,
        val records: List<com.fcm.nanochat.models.registry.InstalledModelRecord>,
        val activeStatus: com.fcm.nanochat.models.registry.ActiveModelStatus,
        val runtimeLoadState: RuntimeLoadState,
        val notice: String?
    )

    private companion object {
        const val RECENT_USE_WINDOW_MS = 60_000L
    }
}

class ModelManagerViewModelFactory(
    private val localModelRepository: LocalModelRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ModelManagerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ModelManagerViewModel(localModelRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

internal fun ModelCardUi.primaryActionLabel(): String {
    if (isActive) {
        return when (memoryState) {
            LocalModelMemoryState.LoadingIntoMemory -> "Loading"
            LocalModelMemoryState.LoadedInMemory,
            LocalModelMemoryState.InUse -> "Eject"

            LocalModelMemoryState.EjectedFromMemory,
            LocalModelMemoryState.SelectedNotLoaded,
            LocalModelMemoryState.NeedsReload,
            LocalModelMemoryState.FailedToLoad -> "Load"

            LocalModelMemoryState.NotSelected -> "Use model"
        }
    }

    return when (healthState) {
        LocalModelHealthState.NotInstalled -> "Download"
        is LocalModelHealthState.Downloading -> "Downloading"
        is LocalModelHealthState.Paused -> "Resume"
        is LocalModelHealthState.DownloadFailed -> "Retry"
        LocalModelHealthState.InstalledNeedsValidation -> "Continue setup"
        LocalModelHealthState.InstalledReady -> if (isActive) "Selected" else "Use model"
        is LocalModelHealthState.InstalledStartupFailed -> "Continue setup"
        LocalModelHealthState.RequiresToken -> "Add token"
        LocalModelHealthState.RequiresLicenseApproval -> "Continue setup"
        is LocalModelHealthState.NotCompatible -> "View details"
        LocalModelHealthState.UnsupportedForChat -> "View details"
    }
}
