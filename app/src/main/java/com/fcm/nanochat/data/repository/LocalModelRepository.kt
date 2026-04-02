package com.fcm.nanochat.data.repository

import com.fcm.nanochat.data.AcceleratorPreference
import com.fcm.nanochat.data.SettingsSnapshot
import com.fcm.nanochat.models.allowlist.AllowlistDefaultConfig
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class LocalModelRepository(
    private val allowlistRepository: AllowlistRepository,
    private val modelRegistry: ModelRegistry,
    private val downloadCoordinator: ModelDownloadCoordinator,
    private val importCoordinator: LocalModelImportCoordinator,
    private val telemetry: InMemoryLocalRuntimeTelemetry,
    private val runtimeManager: ModelRuntimeManager
) {
    private val hasReconciled = AtomicBoolean(false)

    val allowlist: StateFlow<AllowlistSnapshot> = allowlistRepository.snapshot
    val records: StateFlow<List<InstalledModelRecord>> = modelRegistry.records
    val recordsHydrated: StateFlow<Boolean> = modelRegistry.recordsHydrated
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

    suspend fun preferDownloadedMode() {
        modelRegistry.setInferenceMode(com.fcm.nanochat.inference.InferenceMode.DOWNLOADED)
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
        if (!hasReconciled.compareAndSet(false, true)) {
            return
        }
        downloadCoordinator.reconcile()
    }

    suspend fun validateImport(path: String): ImportValidationResult {
        return importCoordinator.validateImport(path)
    }

    suspend fun prepareModelInMemory(modelId: String, settings: SettingsSnapshot? = null): String? {
        return withContext(Dispatchers.IO) {
            val normalized = modelId.trim().lowercase()
            if (normalized.isBlank()) {
                return@withContext "No local model selected."
            }

            val activeModelId = modelRegistry.activeModelId()
            if (activeModelId != normalized) {
                return@withContext "Select this model before loading it into memory."
            }

            val record =
                records.value.firstOrNull { it.modelId == normalized }
                    ?: return@withContext "Selected local model is unavailable."

            if (record.installState != ModelInstallState.INSTALLED) {
                return@withContext "Selected local model is not installed."
            }

            val model = record.allowlistedModel
            val localPath = record.localPath?.trim().orEmpty()
            if (localPath.isBlank()) {
                return@withContext "Selected local model file is missing. Re-download the model."
            }
            val modelFile = File(localPath)
            if (!modelFile.exists() || modelFile.length() <= 0L) {
                return@withContext "Selected local model file is missing. Re-download the model."
            }

            val baseConfig =
                model?.defaultConfig
                    ?: settings?.toFallbackDownloadedConfig()
                    ?: AllowlistDefaultConfig(
                        topK = 40,
                        topP = 0.9,
                        temperature = 0.7,
                        maxTokens = 1024,
                        accelerators = "cpu"
                    )

            val finalConfig =
                if (settings != null) {
                    applyAcceleratorPreference(baseConfig, settings.acceleratorPreference)
                } else {
                    baseConfig
                }

            val loadState = runtimeManager.loadState.value
            val runtimeModelId = loadState.modelId?.trim()?.lowercase().orEmpty()
            val sameModel = runtimeModelId == normalized
            if (sameModel &&
                (loadState.phase == RuntimeLoadPhase.LOADING ||
                        loadState.phase == RuntimeLoadPhase.LOADED)
            ) {
                return@withContext null
            }

            return@withContext runCatching {
                runtimeManager.acquire(
                    modelId = record.modelId,
                    modelPath = localPath,
                    defaultConfig = finalConfig,
                    expectedFileName = model?.modelFile,
                    expectedFileType = model?.fileType,
                    expectedSizeBytes = model?.sizeInBytes ?: 0L,
                    supportsVisionInput = model?.llmSupportImage == true,
                    supportsAudioInput = model?.llmSupportAudio == true
                )
            }
                .exceptionOrNull()
                ?.message
        }
    }

    private fun SettingsSnapshot.toFallbackDownloadedConfig(): AllowlistDefaultConfig {
        return AllowlistDefaultConfig(
            topK = 40,
            topP = topP,
            temperature = temperature,
            maxTokens = contextLength,
            accelerators = "cpu",
            strictAccelerator = false,
            promptFamily = null
        )
    }

    private fun applyAcceleratorPreference(
        config: AllowlistDefaultConfig,
        preference: AcceleratorPreference
    ): AllowlistDefaultConfig {
        if (preference == AcceleratorPreference.AUTO) {
            return config.copy(strictAccelerator = false)
        }

        val normalized =
            when (preference) {
                AcceleratorPreference.AUTO -> config.accelerators
                AcceleratorPreference.CPU -> "cpu"
                AcceleratorPreference.GPU -> "gpu"
                AcceleratorPreference.NNAPI -> "npu"
            }
        return config.copy(accelerators = normalized, strictAccelerator = true)
    }

    suspend fun ejectModelFromMemory(modelId: String): String? {
        return withContext(Dispatchers.IO) {
            val normalized = modelId.trim().lowercase()
            if (normalized.isBlank()) {
                return@withContext "No local model selected."
            }

            val activeModelId = modelRegistry.activeModelId()
            if (activeModelId != normalized) {
                return@withContext "Only the selected model can be ejected from memory."
            }

            val loadState = runtimeManager.loadState.value
            val loadedModelId = loadState.modelId?.trim()?.lowercase().orEmpty()
            val isLoaded = loadState.phase == RuntimeLoadPhase.LOADED && loadedModelId == normalized
            if (!isLoaded) {
                return@withContext "Selected local model is not loaded in memory."
            }

            runtimeManager.release(reason = RuntimeReleaseReason.EJECTED)
            return@withContext null
        }
    }

    fun latestRuntimeMetrics(): LocalRuntimeMetrics? = telemetry.latest()
}
