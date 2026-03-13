package com.fcm.nanochat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fcm.nanochat.data.repository.LocalModelRepository
import com.fcm.nanochat.model.ModelCardUi
import com.fcm.nanochat.model.ModelGalleryScreenState
import com.fcm.nanochat.model.RuntimeDiagnosticsUi
import com.fcm.nanochat.models.compatibility.LocalModelCompatibilityState
import com.fcm.nanochat.models.registry.ModelInstallState
import com.fcm.nanochat.models.registry.ModelStorageLocation
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

    val uiState: StateFlow<ModelGalleryScreenState> = combine(
        localModelRepository.allowlist,
        localModelRepository.records,
        localModelRepository.activeModelStatus,
        notice,
        isRefreshing
    ) { allowlist, records, activeStatus, noticeValue, refreshing ->
        val cardItems = records
            .map { record ->
                val model = record.allowlistedModel
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
                    isActive = record.isActive,
                    isLegacy = record.isLegacy,
                    storageLocation = record.storageLocation,
                    downloadedBytes = record.downloadedBytes,
                    sizeOnDiskBytes = record.sizeBytes,
                    localPath = record.localPath,
                    errorMessage = record.errorMessage
                )
            }
            .sortedWith(
                compareByDescending<ModelCardUi> { it.isActive }
                    .thenByDescending { it.recommendedForChat }
                    .thenBy { it.displayName.lowercase() }
            )

        ModelGalleryScreenState(
            allowlistVersion = allowlist.version.value,
            allowlistSource = allowlist.sourceType,
            allowlistRefreshedAtEpochMs = allowlist.version.refreshedAtEpochMs,
            activeModelId = activeStatus.modelId,
            models = cardItems,
            runtimeMetrics = localModelRepository.latestRuntimeMetrics()?.toUi(),
            notice = noticeValue ?: allowlist.lastRefreshError,
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
            if (
                model == null ||
                model.installState != ModelInstallState.INSTALLED ||
                model.compatibility !is LocalModelCompatibilityState.Ready
            ) {
                notice.update { "This model is not ready yet. Finish setup and try again." }
                return@launch
            }

            val chatReady = model.allowlistedModel?.recommendedForChat ?: true
            if (!chatReady && !model.isLegacy) {
                notice.update { "This model is not optimized for chat in NanoChat." }
                return@launch
            }

            localModelRepository.setActiveModel(modelId)
            notice.update { "Active local model updated." }
        }
    }

    fun clearActiveModel() {
        viewModelScope.launch {
            localModelRepository.setActiveModel(null)
            notice.update { "Local model cleared." }
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
    if (compatibility is LocalModelCompatibilityState.TokenRequired) {
        return "Add token"
    }

    return when (installState) {
        ModelInstallState.NOT_INSTALLED -> "Download"
        ModelInstallState.QUEUED -> "Queued"
        ModelInstallState.DOWNLOADING -> "Downloading"
        ModelInstallState.PAUSED -> "Resume"
        ModelInstallState.FAILED -> "Retry"
        ModelInstallState.VALIDATING -> "Validating"
        ModelInstallState.INSTALLED -> if (isActive) "Selected" else "Use model"
        ModelInstallState.BROKEN -> "Retry"
        ModelInstallState.DELETING -> "Deleting"
        ModelInstallState.MOVING -> "Moving"
    }
}
