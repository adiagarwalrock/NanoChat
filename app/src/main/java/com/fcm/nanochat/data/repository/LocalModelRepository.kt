package com.fcm.nanochat.data.repository

import com.fcm.nanochat.models.allowlist.AllowlistRepository
import com.fcm.nanochat.models.allowlist.AllowlistSnapshot
import com.fcm.nanochat.models.download.ModelDownloadCoordinator
import com.fcm.nanochat.models.importing.ImportValidationResult
import com.fcm.nanochat.models.importing.LocalModelImportCoordinator
import com.fcm.nanochat.models.registry.ActiveModelStatus
import com.fcm.nanochat.models.registry.InstalledModelRecord
import com.fcm.nanochat.models.registry.ModelInstallState
import com.fcm.nanochat.models.registry.ModelRegistry
import com.fcm.nanochat.models.registry.ModelStorageLocation
import com.fcm.nanochat.models.runtime.InMemoryLocalRuntimeTelemetry
import com.fcm.nanochat.models.runtime.LocalRuntimeMetrics
import com.fcm.nanochat.models.runtime.ModelRuntimeManager
import com.fcm.nanochat.models.runtime.RuntimeLoadPhase
import com.fcm.nanochat.models.runtime.RuntimeLoadState
import com.fcm.nanochat.models.runtime.RuntimeReleaseReason
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class LocalModelRepository(
    private val allowlistRepository: AllowlistRepository,
    private val modelRegistry: ModelRegistry,
    private val downloadCoordinator: ModelDownloadCoordinator,
    private val importCoordinator: LocalModelImportCoordinator,
    private val telemetry: InMemoryLocalRuntimeTelemetry,
    private val runtimeManager: ModelRuntimeManager
) {
    val allowlist: StateFlow<AllowlistSnapshot> = allowlistRepository.snapshot
    val records: StateFlow<List<InstalledModelRecord>> = modelRegistry.records
    val activeModelStatus: StateFlow<ActiveModelStatus> = modelRegistry.activeModelStatus
    val runtimeLoadState: StateFlow<RuntimeLoadState> = runtimeManager.loadState

    fun refreshAllowlist() {
        allowlistRepository.refresh()
    }

    suspend fun setActiveModel(modelId: String?) {
        val normalized = modelId?.trim()?.lowercase().orEmpty().ifBlank { null }
        val previous = modelRegistry.activeModelId().ifBlank { null }

        if (previous == normalized) {
            return
        }

        modelRegistry.setActiveModel(normalized)
        runtimeManager.release()
    }

    fun downloadModel(modelId: String) {
        downloadCoordinator.download(modelId)
    }

    fun cancelDownload(modelId: String) {
        downloadCoordinator.cancel(modelId)
    }

    fun retryDownload(modelId: String) {
        downloadCoordinator.download(modelId)
    }

    fun deleteModel(modelId: String) {
        downloadCoordinator.delete(modelId)
    }

    fun moveModel(modelId: String, target: ModelStorageLocation) {
        downloadCoordinator.moveStorage(modelId, target)
    }

    fun reconcile() {
        downloadCoordinator.reconcile()
    }

    suspend fun validateImport(path: String): ImportValidationResult {
        return importCoordinator.validateImport(path)
    }

    suspend fun prepareModelInMemory(modelId: String): String? {
        val normalized = modelId.trim().lowercase()
        if (normalized.isBlank()) return "No local model selected."

        val activeModelId = modelRegistry.activeModelId()
        if (activeModelId != normalized) {
            return "Select this model before loading it into memory."
        }

        val record = records.value.firstOrNull { it.modelId == normalized }
            ?: return "Selected local model is unavailable."

        if (record.installState != ModelInstallState.INSTALLED) {
            return "Selected local model is not installed."
        }

        val model = record.allowlistedModel
        val localPath = record.localPath?.trim().orEmpty()
        if (localPath.isBlank()) {
            return "Selected local model file is missing. Re-download the model."
        }
        val modelFile = File(localPath)
        if (!modelFile.exists() || modelFile.length() <= 0L) {
            return "Selected local model file is missing. Re-download the model."
        }

        return runCatching {
            runtimeManager.acquire(
                modelId = record.modelId,
                modelPath = localPath,
                defaultConfig = model?.defaultConfig
                    ?: com.fcm.nanochat.models.allowlist.AllowlistDefaultConfig(
                        topK = 40,
                        topP = 0.9,
                        temperature = 0.7,
                        maxTokens = 1024,
                        accelerators = "cpu"
                    ),
                expectedFileName = model?.modelFile,
                expectedFileType = model?.fileType,
                expectedSizeBytes = model?.sizeInBytes ?: 0L
            )
        }.exceptionOrNull()?.message
    }

    suspend fun ejectModelFromMemory(modelId: String): String? {
        val normalized = modelId.trim().lowercase()
        if (normalized.isBlank()) return "No local model selected."

        val activeModelId = modelRegistry.activeModelId()
        if (activeModelId != normalized) {
            return "Only the selected model can be ejected from memory."
        }

        val loadState = runtimeManager.loadState.value
        val loadedModelId = loadState.modelId?.trim()?.lowercase().orEmpty()
        val isLoaded = loadState.phase == RuntimeLoadPhase.LOADED && loadedModelId == normalized
        if (!isLoaded) {
            return "Selected local model is not loaded in memory."
        }

        runtimeManager.release(reason = RuntimeReleaseReason.EJECTED)
        return null
    }

    fun latestRuntimeMetrics(): LocalRuntimeMetrics? = telemetry.latest()
}
