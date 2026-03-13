package com.fcm.nanochat.models.registry

import com.fcm.nanochat.data.AppPreferences
import com.fcm.nanochat.data.db.InstalledModelDao
import com.fcm.nanochat.data.db.InstalledModelEntity
import com.fcm.nanochat.inference.InferenceMode
import com.fcm.nanochat.models.allowlist.AllowlistRepository
import com.fcm.nanochat.models.allowlist.AllowlistedModel
import com.fcm.nanochat.models.compatibility.LocalModelCompatibilityEvaluator
import com.fcm.nanochat.models.compatibility.LocalModelCompatibilityState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class ModelRegistry(
    private val allowlistRepository: AllowlistRepository,
    private val installedModelDao: InstalledModelDao,
    private val preferences: AppPreferences,
    private val compatibilityEvaluator: LocalModelCompatibilityEvaluator
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _records = MutableStateFlow<List<InstalledModelRecord>>(emptyList())
    val records: StateFlow<List<InstalledModelRecord>> = _records.asStateFlow()

    private val _recordsHydrated = MutableStateFlow(false)
    val recordsHydrated: StateFlow<Boolean> = _recordsHydrated.asStateFlow()

    private val _activeModelStatus = MutableStateFlow(
        ActiveModelStatus(
            modelId = null,
            displayName = null,
            ready = false,
            message = null
        )
    )
    val activeModelStatus: StateFlow<ActiveModelStatus> = _activeModelStatus.asStateFlow()

    init {
        scope.launch {
            reconcileInstalledFiles()
        }
        scope.launch {
            observeRecords()
        }
    }

    suspend fun setActiveModel(modelId: String?) {
        val normalized = modelId?.trim()?.lowercase().orEmpty()
        preferences.updateActiveLocalModelId(normalized.ifBlank { null })
    }

    suspend fun setInferenceMode(mode: InferenceMode) {
        preferences.updateInferenceMode(mode)
    }

    suspend fun activeModelId(): String =
        preferences.settings.first().activeLocalModelId.trim().lowercase()

    suspend fun reconcileInstalledFiles() {
        val snapshot = allowlistRepository.snapshot.value.let { current ->
            if (current.models.isNotEmpty() || current.version.value.isNotBlank()) {
                current
            } else {
                allowlistRepository.snapshot.first { it.models.isNotEmpty() || it.version.value.isNotBlank() }
            }
        }
        val allowlistedById = snapshot.models.associateBy { it.id }
        val tokenPresent = preferences.settings.first().huggingFaceToken.isNotBlank()
        val installed = installedModelDao.allInstalledModels()
        installed.forEach { entity ->
            if (entity.installState != ModelInstallState.INSTALLED) return@forEach
            val file = File(entity.localPath)
            if (!file.exists() || file.length() <= 0L) {
                installedModelDao.upsert(
                    entity.copy(
                        installState = ModelInstallState.BROKEN,
                        errorMessage = "Model file is missing or empty.",
                        updatedAt = System.currentTimeMillis()
                    )
                )
                return@forEach
            }

            val allowlisted = allowlistedById[entity.modelId.lowercase()] ?: return@forEach
            val compatibility = compatibilityEvaluator.evaluate(
                model = allowlisted,
                installedPath = entity.localPath,
                installState = ModelInstallState.INSTALLED,
                tokenPresent = tokenPresent
            )

            when (compatibility) {
                LocalModelCompatibilityState.Ready -> {
                    if (!entity.errorMessage.isNullOrBlank()) {
                        installedModelDao.upsert(
                            entity.copy(
                                errorMessage = null,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }

                LocalModelCompatibilityState.CorruptedModel -> {
                    installedModelDao.upsert(
                        entity.copy(
                            installState = ModelInstallState.BROKEN,
                            errorMessage = "Model file appears corrupted.",
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }

                is LocalModelCompatibilityState.RuntimeUnavailable,
                is LocalModelCompatibilityState.DownloadedButNotActivatable -> {
                    val issueMessage = when (compatibility) {
                        is LocalModelCompatibilityState.RuntimeUnavailable -> compatibility.reason
                        is LocalModelCompatibilityState.DownloadedButNotActivatable -> compatibility.reason
                        else -> ""
                    }
                    val persistedMessage = sanitizeCompatibilityReason(issueMessage)
                    if (entity.errorMessage != persistedMessage) {
                        installedModelDao.upsert(
                            entity.copy(
                                errorMessage = persistedMessage,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }

                else -> Unit
            }
        }
    }

    private suspend fun observeRecords() {
        combine(
            allowlistRepository.snapshot,
            installedModelDao.observeInstalledModels(),
            preferences.settings
        ) { allowlistSnapshot, installedEntities, settings ->
            val installedById = installedEntities.associateBy { it.modelId.lowercase() }
            val tokenPresent = settings.huggingFaceToken.isNotBlank()
            val activeId = settings.activeLocalModelId.trim().lowercase()
            val records = mutableListOf<InstalledModelRecord>()

            allowlistSnapshot.models.forEach { model ->
                val installed = installedById[model.id]
                records += buildAllowlistedRecord(
                    model = model,
                    installed = installed,
                    tokenPresent = tokenPresent,
                    activeId = activeId,
                    allowlistVersion = allowlistSnapshot.version.value
                )
            }

            val allowlistedIds = allowlistSnapshot.models.map { it.id }.toSet()
            installedEntities
                .filter { entity -> entity.modelId.lowercase() !in allowlistedIds }
                .forEach { legacy ->
                    records += buildLegacyRecord(legacy = legacy, activeId = activeId)
                }

            val sorted = records.sortedWith(
                compareByDescending<InstalledModelRecord> { it.isActive }
                    .thenByDescending { it.allowlistedModel?.recommendedForChat ?: true }
                    .thenBy { it.displayName.lowercase() }
            )
            sorted to activeId
        }.collect { (records, activeId) ->
            _records.value = records
            if (!_recordsHydrated.value) {
                _recordsHydrated.value = true
            }
            updateActiveStatus(records, activeId)
        }
    }

    private suspend fun buildAllowlistedRecord(
        model: AllowlistedModel,
        installed: InstalledModelEntity?,
        tokenPresent: Boolean,
        activeId: String,
        allowlistVersion: String
    ): InstalledModelRecord {
        val installState = installed?.installState ?: ModelInstallState.NOT_INSTALLED
        val localPath = installed?.localPath?.ifBlank { null }
        val compatibility = compatibilityEvaluator.evaluate(
            model = model,
            installedPath = localPath,
            installState = installState,
            tokenPresent = tokenPresent
        )

        return InstalledModelRecord(
            modelId = model.id,
            displayName = model.displayName,
            allowlistedModel = model,
            installState = installState,
            storageLocation = installed?.storageLocation ?: ModelStorageLocation.INTERNAL,
            localPath = localPath,
            sizeBytes = installed?.sizeBytes?.takeIf { it > 0L } ?: model.sizeInBytes,
            downloadedBytes = installed?.downloadedBytes ?: 0L,
            errorMessage = installed?.errorMessage,
            allowlistVersion = installed?.allowlistVersion?.ifBlank { null } ?: allowlistVersion,
            compatibility = compatibility,
            isLegacy = false,
            isActive = model.id == activeId
        )
    }

    private fun buildLegacyRecord(
        legacy: InstalledModelEntity,
        activeId: String
    ): InstalledModelRecord {
        val file = File(legacy.localPath)
        val compatibility = when {
            legacy.installState == ModelInstallState.INSTALLED && file.exists() && file.length() > 0L -> {
                LocalModelCompatibilityState.Ready
            }

            legacy.installState == ModelInstallState.INSTALLED -> {
                LocalModelCompatibilityState.CorruptedModel
            }

            legacy.installState == ModelInstallState.BROKEN -> {
                LocalModelCompatibilityState.CorruptedModel
            }

            else -> {
                LocalModelCompatibilityState.DownloadedButNotActivatable(
                    legacy.errorMessage ?: "Legacy model is not ready."
                )
            }
        }

        return InstalledModelRecord(
            modelId = legacy.modelId,
            displayName = legacy.displayName,
            allowlistedModel = null,
            installState = legacy.installState,
            storageLocation = legacy.storageLocation,
            localPath = legacy.localPath,
            sizeBytes = legacy.sizeBytes,
            downloadedBytes = legacy.downloadedBytes,
            errorMessage = legacy.errorMessage,
            allowlistVersion = legacy.allowlistVersion,
            compatibility = compatibility,
            isLegacy = true,
            isActive = legacy.modelId.lowercase() == activeId
        )
    }

    private suspend fun updateActiveStatus(records: List<InstalledModelRecord>, activeId: String) {
        val resolution = ActiveModelResolver.resolve(activeId, records)
        if (resolution.shouldClearSelection) {
            preferences.updateActiveLocalModelId(null)
        }

        val active = resolution.activeRecord
        val ready = active != null &&
                active.installState == ModelInstallState.INSTALLED &&
                active.compatibility is LocalModelCompatibilityState.Ready

        _activeModelStatus.value = ActiveModelStatus(
            modelId = active?.modelId,
            displayName = active?.displayName,
            ready = ready,
            message = if (ready) {
                null
            } else {
                resolution.message ?: active?.errorMessage?.let(::sanitizeCompatibilityReason)
                ?: active?.let {
                    compatibilityMessage(it.compatibility)
                }
            }
        )
    }

    private fun compatibilityMessage(compatibility: LocalModelCompatibilityState): String {
        return when (compatibility) {
            LocalModelCompatibilityState.Ready -> "Ready"
            LocalModelCompatibilityState.Downloadable -> "Download this model to use it."
            is LocalModelCompatibilityState.NeedsMoreStorage -> "Not enough storage for this model."
            is LocalModelCompatibilityState.NeedsMoreRam -> "Not enough memory for this model."
            is LocalModelCompatibilityState.UnsupportedDevice -> {
                sanitizeCompatibilityReason(compatibility.reason)
            }

            LocalModelCompatibilityState.UnsupportedForChat -> {
                "This model is not designed for chat in NanoChat."
            }

            LocalModelCompatibilityState.TokenRequired -> "This model requires a Hugging Face token."
            is LocalModelCompatibilityState.DownloadedButNotActivatable -> {
                sanitizeCompatibilityReason(compatibility.reason)
            }

            LocalModelCompatibilityState.CorruptedModel -> "Model file appears corrupted."
            is LocalModelCompatibilityState.RuntimeUnavailable -> {
                sanitizeCompatibilityReason(compatibility.reason)
            }
        }
    }

    private fun sanitizeCompatibilityReason(raw: String): String {
        val text = raw.trim()
        if (text.isBlank()) return "This model is not ready."

        val lowercase = text.lowercase()
        return when {
            "startup_validation_failed" in lowercase ||
                    "error building tflite model" in lowercase ||
                    "flatbuffer" in lowercase ||
                    "invocationtargetexception" in lowercase -> {
                "Installed, but NanoChat could not start this model."
            }

            "missing runtime option method" in lowercase ||
                    "settopk" in lowercase ||
                    "setmaxtokens" in lowercase ||
                    "runtime unavailable" in lowercase -> {
                "This model could not be started with the current local runtime."
            }

            "missing" in lowercase && "file" in lowercase -> {
                "This install appears incomplete. Try re-downloading."
            }

            "corrupt" in lowercase || "size mismatch" in lowercase -> {
                "This downloaded file may be incompatible with the current runtime."
            }

            else -> text
        }
    }
}
