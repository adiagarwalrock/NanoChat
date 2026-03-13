package com.fcm.nanochat.data.repository

import com.fcm.nanochat.models.allowlist.AllowlistRepository
import com.fcm.nanochat.models.allowlist.AllowlistSnapshot
import com.fcm.nanochat.models.download.ModelDownloadCoordinator
import com.fcm.nanochat.models.importing.ImportValidationResult
import com.fcm.nanochat.models.importing.LocalModelImportCoordinator
import com.fcm.nanochat.models.registry.ActiveModelStatus
import com.fcm.nanochat.models.registry.InstalledModelRecord
import com.fcm.nanochat.models.registry.ModelRegistry
import com.fcm.nanochat.models.registry.ModelStorageLocation
import com.fcm.nanochat.models.runtime.InMemoryLocalRuntimeTelemetry
import com.fcm.nanochat.models.runtime.LocalRuntimeMetrics
import kotlinx.coroutines.flow.StateFlow

class LocalModelRepository(
    private val allowlistRepository: AllowlistRepository,
    private val modelRegistry: ModelRegistry,
    private val downloadCoordinator: ModelDownloadCoordinator,
    private val importCoordinator: LocalModelImportCoordinator,
    private val telemetry: InMemoryLocalRuntimeTelemetry
) {
    val allowlist: StateFlow<AllowlistSnapshot> = allowlistRepository.snapshot
    val records: StateFlow<List<InstalledModelRecord>> = modelRegistry.records
    val activeModelStatus: StateFlow<ActiveModelStatus> = modelRegistry.activeModelStatus

    fun refreshAllowlist() {
        allowlistRepository.refresh()
    }

    suspend fun setActiveModel(modelId: String?) {
        modelRegistry.setActiveModel(modelId)
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

    fun latestRuntimeMetrics(): LocalRuntimeMetrics? = telemetry.latest()
}
