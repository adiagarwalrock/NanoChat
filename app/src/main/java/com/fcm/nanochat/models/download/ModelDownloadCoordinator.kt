package com.fcm.nanochat.models.download

import android.content.Context
import com.fcm.nanochat.data.AppPreferences
import com.fcm.nanochat.data.db.InstalledModelDao
import com.fcm.nanochat.data.db.InstalledModelEntity
import com.fcm.nanochat.models.allowlist.AllowlistRepository
import com.fcm.nanochat.models.registry.ModelInstallState
import com.fcm.nanochat.models.registry.ModelStorageLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class ModelDownloadCoordinator(
    context: Context,
    private val httpClient: OkHttpClient,
    private val preferences: AppPreferences,
    private val allowlistRepository: AllowlistRepository,
    private val installedModelDao: InstalledModelDao,
    private val integrityValidator: DownloadIntegrityValidator
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    fun download(modelId: String) {
        val normalized = modelId.trim().lowercase()
        if (normalized.isBlank()) return

        jobs.remove(normalized)?.cancel()
        jobs[normalized] = scope.launch {
            try {
                runDownload(normalized)
            } finally {
                jobs.remove(normalized)
            }
        }
    }

    fun cancel(modelId: String) {
        val normalized = modelId.trim().lowercase()
        jobs.remove(normalized)?.cancel()
        scope.launch {
            val existing = installedModelDao.installedModel(normalized) ?: return@launch
            installedModelDao.upsert(
                existing.copy(
                    installState = ModelInstallState.PAUSED,
                    errorMessage = "Download paused.",
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun delete(modelId: String) {
        val normalized = modelId.trim().lowercase()
        if (normalized.isBlank()) return
        jobs.remove(normalized)?.cancel()

        scope.launch {
            val existing = installedModelDao.installedModel(normalized) ?: return@launch
            val deleting = existing.copy(
                installState = ModelInstallState.DELETING,
                updatedAt = System.currentTimeMillis(),
                errorMessage = null
            )
            installedModelDao.upsert(deleting)

            runCatching {
                File(existing.localPath).takeIf(File::exists)?.delete()
                tempFile(normalized).takeIf(File::exists)?.delete()
                installedModelDao.deleteById(normalized)
            }.onFailure { error ->
                installedModelDao.upsert(
                    deleting.copy(
                        installState = ModelInstallState.BROKEN,
                        errorMessage = error.message ?: "Unable to delete model.",
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun moveStorage(modelId: String, target: ModelStorageLocation) {
        val normalized = modelId.trim().lowercase()
        if (normalized.isBlank()) return

        scope.launch {
            val existing = installedModelDao.installedModel(normalized) ?: return@launch
            if (existing.storageLocation == target) return@launch

            val sourceFile = File(existing.localPath)
            if (!sourceFile.exists()) {
                installedModelDao.upsert(
                    existing.copy(
                        installState = ModelInstallState.BROKEN,
                        errorMessage = "Model file is missing.",
                        updatedAt = System.currentTimeMillis()
                    )
                )
                return@launch
            }

            val moving = existing.copy(
                installState = ModelInstallState.MOVING,
                errorMessage = null,
                updatedAt = System.currentTimeMillis()
            )
            installedModelDao.upsert(moving)

            val resolvedTarget = resolveStorageLocation(target)
            val targetDir = modelStorageDirectory(resolvedTarget)
            targetDir.mkdirs()
            val targetFile = File(targetDir, existing.modelFileName)

            runCatching {
                copyFile(sourceFile, targetFile)
                sourceFile.delete()
                installedModelDao.upsert(
                    existing.copy(
                        installState = ModelInstallState.INSTALLED,
                        storageLocation = resolvedTarget,
                        localPath = targetFile.absolutePath,
                        errorMessage = null,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }.onFailure { error ->
                installedModelDao.upsert(
                    existing.copy(
                        installState = ModelInstallState.FAILED,
                        errorMessage = error.message ?: "Unable to move model file.",
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun reconcile() {
        scope.launch {
            modelTempDirectory().mkdirs()

            installedModelDao.allInstalledModels().forEach { entity ->
                if (entity.installState == ModelInstallState.INSTALLED) {
                    val file = File(entity.localPath)
                    if (!file.exists() || file.length() <= 0L) {
                        installedModelDao.upsert(
                            entity.copy(
                                installState = ModelInstallState.BROKEN,
                                errorMessage = "Model file is missing.",
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }

            val activeIds = installedModelDao.allInstalledModels()
                .map { safeId(it.modelId) }
                .toSet()
            modelTempDirectory().listFiles()
                ?.filter { file ->
                    file.isFile && file.name.endsWith(".part") &&
                        file.name.removeSuffix(".part") !in activeIds
                }
                ?.forEach { orphan ->
                    runCatching { orphan.delete() }
                }
        }
    }

    private suspend fun runDownload(modelId: String) {
        val model = allowlistRepository.currentModelOrNull(modelId)
            ?: return upsertFailure(modelId, "Model is no longer listed in the allowlist.")

        val settings = preferences.settings.first()
        val token = settings.huggingFaceToken.trim()
        if (model.requiresHfToken && token.isBlank()) {
            upsertFailure(modelId, "Hugging Face token is required for this download.")
            return
        }

        val now = System.currentTimeMillis()
        val existing = installedModelDao.installedModel(modelId)
        val storageLocation = existing?.storageLocation ?: ModelStorageLocation.INTERNAL
        val queuedEntity = InstalledModelEntity(
            modelId = modelId,
            displayName = model.displayName,
            modelFileName = model.modelFile,
            localPath = existing?.localPath.orEmpty(),
            sizeBytes = model.sizeInBytes,
            downloadedBytes = existing?.downloadedBytes ?: 0L,
            installState = ModelInstallState.QUEUED,
            storageLocation = storageLocation,
            allowlistVersion = allowlistRepository.snapshot.value.version.value,
            errorMessage = null,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        installedModelDao.upsert(queuedEntity)

        val tempFile = tempFile(modelId)
        tempFile.parentFile?.mkdirs()
        val resumeBytes = if (tempFile.exists()) tempFile.length() else 0L

        installedModelDao.upsert(
            queuedEntity.copy(
                installState = ModelInstallState.DOWNLOADING,
                downloadedBytes = resumeBytes,
                updatedAt = System.currentTimeMillis()
            )
        )

        val requestBuilder = Request.Builder()
            .url(model.downloadUrl)
            .header("Accept", "application/octet-stream")
            .get()

        if (model.requiresHfToken) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        if (resumeBytes > 0L) {
            requestBuilder.header("Range", "bytes=$resumeBytes-")
        }

        val request = requestBuilder.build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) {
                    upsertFailure(modelId, "Download failed (HTTP ${response.code}).")
                    return
                }

                val body = response.body ?: run {
                    upsertFailure(modelId, "Download failed. Empty response body.")
                    return
                }

                val append = resumeBytes > 0L && response.code == 206
                val startingBytes = if (append) resumeBytes else 0L

                FileOutputStream(tempFile, append).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var bytesRead: Int
                        var bytesWritten = startingBytes
                        var lastReport = System.currentTimeMillis()

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            ensureJobActive(modelId)
                            output.write(buffer, 0, bytesRead)
                            bytesWritten += bytesRead

                            val nowMs = System.currentTimeMillis()
                            if (nowMs - lastReport >= 150L) {
                                installedModelDao.upsert(
                                    queuedEntity.copy(
                                        installState = ModelInstallState.DOWNLOADING,
                                        downloadedBytes = bytesWritten,
                                        updatedAt = nowMs,
                                        localPath = existing?.localPath.orEmpty()
                                    )
                                )
                                lastReport = nowMs
                            }
                        }
                    }
                }
            }

            installedModelDao.upsert(
                queuedEntity.copy(
                    installState = ModelInstallState.VALIDATING,
                    downloadedBytes = tempFile.length(),
                    updatedAt = System.currentTimeMillis()
                )
            )

            when (val validation = integrityValidator.validate(tempFile, model.modelFile, model.sizeInBytes)) {
                ValidationResult.Success -> Unit
                is ValidationResult.Failure -> {
                    upsertFailure(modelId, validation.message)
                    return
                }
            }

            val resolvedStorageLocation = resolveStorageLocation(storageLocation)
            val finalDir = modelStorageDirectory(resolvedStorageLocation)
            finalDir.mkdirs()
            val finalFile = File(finalDir, model.modelFile)
            finalizeFile(tempFile, finalFile)

            installedModelDao.upsert(
                queuedEntity.copy(
                    installState = ModelInstallState.INSTALLED,
                    storageLocation = resolvedStorageLocation,
                    localPath = finalFile.absolutePath,
                    sizeBytes = finalFile.length(),
                    downloadedBytes = finalFile.length(),
                    errorMessage = null,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } catch (cancelled: CancellationException) {
            val paused = installedModelDao.installedModel(modelId)
            if (paused != null) {
                installedModelDao.upsert(
                    paused.copy(
                        installState = ModelInstallState.PAUSED,
                        downloadedBytes = tempFile.length(),
                        errorMessage = "Download paused.",
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        } catch (error: Throwable) {
            upsertFailure(modelId, error.message ?: "Model download failed.")
        }
    }

    private suspend fun upsertFailure(modelId: String, message: String) {
        val now = System.currentTimeMillis()
        val existing = installedModelDao.installedModel(modelId)
        if (existing != null) {
            installedModelDao.upsert(
                existing.copy(
                    installState = ModelInstallState.FAILED,
                    errorMessage = message,
                    updatedAt = now
                )
            )
            return
        }

        val model = allowlistRepository.currentModelOrNull(modelId)
        if (model == null) return
        installedModelDao.upsert(
            InstalledModelEntity(
                modelId = model.id,
                displayName = model.displayName,
                modelFileName = model.modelFile,
                localPath = "",
                sizeBytes = model.sizeInBytes,
                downloadedBytes = 0L,
                installState = ModelInstallState.FAILED,
                storageLocation = ModelStorageLocation.INTERNAL,
                allowlistVersion = allowlistRepository.snapshot.value.version.value,
                errorMessage = message,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private fun ensureJobActive(modelId: String) {
        val job = jobs[modelId]
        if (job != null && !job.isActive) {
            throw CancellationException("Download cancelled")
        }
    }

    private fun tempFile(modelId: String): File {
        return File(modelTempDirectory(), "${safeId(modelId)}.part")
    }

    private fun modelTempDirectory(): File {
        return File(modelRootDirectory(), "temp")
    }

    private fun modelRootDirectory(): File {
        return File(appContext.filesDir, "local_models")
    }

    private fun modelStorageDirectory(location: ModelStorageLocation): File {
        return when (location) {
            ModelStorageLocation.INTERNAL -> File(modelRootDirectory(), "installed")
            ModelStorageLocation.EXTERNAL -> {
                appContext.getExternalFilesDir("models") ?: File(modelRootDirectory(), "installed")
            }
        }
    }

    private fun resolveStorageLocation(location: ModelStorageLocation): ModelStorageLocation {
        return if (location == ModelStorageLocation.EXTERNAL && appContext.getExternalFilesDir("models") == null) {
            ModelStorageLocation.INTERNAL
        } else {
            location
        }
    }

    private fun finalizeFile(tempFile: File, finalFile: File) {
        if (finalFile.exists()) {
            finalFile.delete()
        }
        if (!tempFile.renameTo(finalFile)) {
            copyFile(tempFile, finalFile)
            tempFile.delete()
        }
    }

    private fun copyFile(source: File, target: File) {
        FileInputStream(source).use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
                output.flush()
            }
        }
    }

    private fun safeId(modelId: String): String {
        return modelId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }
}
