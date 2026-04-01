package com.fcm.nanochat.models.download

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.fcm.nanochat.data.AppPreferences
import com.fcm.nanochat.data.db.InstalledModelDao
import com.fcm.nanochat.data.db.InstalledModelEntity
import com.fcm.nanochat.models.allowlist.AllowlistRepository
import com.fcm.nanochat.models.registry.ModelInstallState
import com.fcm.nanochat.models.registry.ModelStorageLocation
import com.fcm.nanochat.notifications.NotificationCoordinator
import com.fcm.nanochat.service.ModelDownloadService
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
import java.util.Locale

private val nonSafeIdCharacterRegex = Regex("[^a-zA-Z0-9._-]")

private sealed interface DownloadPreflightResult {
    data object Allowed : DownloadPreflightResult
    data class Blocked(val message: String) : DownloadPreflightResult
}

class ModelDownloadCoordinator(
    context: Context,
    private val httpClient: OkHttpClient,
    private val preferences: AppPreferences,
    private val allowlistRepository: AllowlistRepository,
    private val installedModelDao: InstalledModelDao,
    private val integrityValidator: DownloadIntegrityValidator,
    private val notificationCoordinator: NotificationCoordinator
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
        notificationCoordinator.cancelModelDownload(normalized)
        stopForegroundServiceIfIdle()
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
        notificationCoordinator.cancelModelDownload(normalized)

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
                        errorMessage = toFriendlyFailure(
                            error.message ?: "Unable to delete model."
                        ),
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
                        errorMessage = toFriendlyFailure(
                            error.message ?: "Unable to move model file."
                        ),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun reconcile() {
        scope.launch {
            modelTempDirectory().mkdirs()

            val installedEntities = installedModelDao.allInstalledModels()
            installedEntities.forEach { entity ->
                when (entity.installState) {
                    ModelInstallState.INSTALLED -> {
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

                    ModelInstallState.DOWNLOADING,
                    ModelInstallState.QUEUED,
                    ModelInstallState.VALIDATING -> {
                        val temp = tempFile(entity.modelId)
                        if (temp.exists() && temp.length() > 0L) {
                            installedModelDao.upsert(
                                entity.copy(
                                    installState = ModelInstallState.PAUSED,
                                    downloadedBytes = temp.length(),
                                    errorMessage = "Download paused.",
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }

                    else -> Unit
                }
            }

            val knownTempIds = installedEntities
                .map { safeId(it.modelId) }
                .toMutableSet()
            val allowlistedBySafeId = allowlistRepository.snapshot.value.models
                .associateBy { model -> safeId(model.id) }

            modelTempDirectory().listFiles()
                ?.filter { file -> file.isFile && file.name.endsWith(".part") }
                ?.forEach { partial ->
                    val safeModelId = partial.name.removeSuffix(".part")
                    if (safeModelId in knownTempIds) {
                        return@forEach
                    }

                    val allowlistedModel = allowlistedBySafeId[safeModelId]
                    if (allowlistedModel != null && partial.length() > 0L) {
                        val now = System.currentTimeMillis()
                        installedModelDao.upsert(
                            InstalledModelEntity(
                                modelId = allowlistedModel.id,
                                displayName = allowlistedModel.displayName,
                                modelFileName = allowlistedModel.modelFile,
                                localPath = "",
                                sizeBytes = allowlistedModel.sizeInBytes,
                                downloadedBytes = partial.length(),
                                installState = ModelInstallState.PAUSED,
                                storageLocation = ModelStorageLocation.INTERNAL,
                                allowlistVersion = allowlistRepository.snapshot.value.version.value,
                                errorMessage = "Download paused.",
                                createdAt = now,
                                updatedAt = now
                            )
                        )
                        knownTempIds += safeModelId
                    } else {
                        runCatching { partial.delete() }
                    }
                }
        }
    }

    private suspend fun runDownload(modelId: String) {
        val model = allowlistRepository.currentModelOrNull(modelId)
            ?: return upsertFailure(modelId, "Model is no longer listed in the allowlist.")

        Log.d(
            TAG,
            "download_target modelId=${model.id} url=${model.downloadUrl} expectedFile=${model.modelFile} " +
                    "expectedSizeBytes=${model.sizeInBytes} expectedExt=${model.fileType}"
        )

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

        // Start foreground service to keep the download alive in background.
        // The service handles the progress notification; NotificationCoordinator
        // is only used for terminal states (completed / failed / paused).
        ModelDownloadService.start(appContext, model.displayName)

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

        if (resumeBytes > 0L) {
            requestBuilder.header("Range", "bytes=$resumeBytes-")
        }

        val request = requestBuilder.build()
        var lastNotifyMs = 0L

        try {
            var resolvedDownloadUrl: String? = null
            var responseContentType: String? = null
            httpClient.newCall(request).execute().use { response ->
                resolvedDownloadUrl = response.request.url.toString()
                responseContentType = response.header("Content-Type")
                Log.d(
                    TAG,
                    "download_response modelId=${model.id} httpCode=${response.code} " +
                            "contentType=${responseContentType.orEmpty()} resolvedUrl=${resolvedDownloadUrl.orEmpty()}"
                )

                if (!response.isSuccessful && response.code != 206) {
            val message = downloadHttpErrorMessage(response.code, model.downloadUrl)
            upsertFailure(modelId, message)
            return
        }

                val body = response.body

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

                            if (nowMs - lastNotifyMs >= 750L) {
                                ModelDownloadService.updateProgress(
                                    appContext,
                                    model.displayName,
                                    bytesWritten,
                                    model.sizeInBytes
                                )
                                lastNotifyMs = nowMs
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

            ModelDownloadService.updateProgress(
                appContext,
                model.displayName,
                tempFile.length(),
                model.sizeInBytes
            )

            when (val validation =
                integrityValidator.validate(tempFile, model.modelFile, model.sizeInBytes)) {
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

            logInstalledArtifact(
                modelId = model.id,
                downloadUrl = resolvedDownloadUrl ?: model.downloadUrl,
                contentType = responseContentType,
                finalFile = finalFile,
                expectedFileName = model.modelFile,
                expectedSizeBytes = model.sizeInBytes,
                expectedExtension = model.fileType
            )

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

            notificationCoordinator.notifyModelDownload(
                modelId = modelId,
                displayName = model.displayName,
                downloadedBytes = finalFile.length(),
                totalBytes = finalFile.length(),
                status = NotificationCoordinator.DownloadStatus.Completed
            )
            ModelDownloadService.complete(appContext, model.displayName, success = true)
            stopForegroundServiceIfIdle()
        } catch (_: CancellationException) {
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

            notificationCoordinator.notifyModelDownload(
                modelId = modelId,
                displayName = model.displayName,
                downloadedBytes = tempFile.length(),
                totalBytes = model.sizeInBytes,
                status = NotificationCoordinator.DownloadStatus.Paused
            )
            stopForegroundServiceIfIdle()
        } catch (error: Throwable) {
            upsertFailure(modelId, toFriendlyFailure(error.message ?: "Model download failed."))
            notificationCoordinator.notifyModelDownload(
                modelId = modelId,
                displayName = model.displayName,
                downloadedBytes = tempFile.length(),
                totalBytes = model.sizeInBytes,
                status = NotificationCoordinator.DownloadStatus.Failed
            )
            ModelDownloadService.complete(appContext, model.displayName, success = false)
            stopForegroundServiceIfIdle()
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
            notificationCoordinator.notifyModelDownload(
                modelId = modelId,
                displayName = existing.displayName,
                downloadedBytes = existing.downloadedBytes,
                totalBytes = existing.sizeBytes,
                status = NotificationCoordinator.DownloadStatus.Failed
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

        notificationCoordinator.notifyModelDownload(
            modelId = model.id,
            displayName = model.displayName,
            downloadedBytes = 0L,
            totalBytes = model.sizeInBytes,
            status = NotificationCoordinator.DownloadStatus.Failed
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

    private fun downloadHttpErrorMessage(
        httpCode: Int,
        downloadUrl: String
    ): String {
        return when {
            httpCode in 400..599 -> "Download failed (HTTP $httpCode)."
            else -> "Download failed."
        }
    }

    private fun logInstalledArtifact(
        modelId: String,
        downloadUrl: String,
        contentType: String?,
        finalFile: File,
        expectedFileName: String,
        expectedSizeBytes: Long,
        expectedExtension: String
    ) {
        val actualSize = finalFile.length()
        val actualExtension = finalFile.extension.lowercase(Locale.US)
        val fileNameMatches = finalFile.name == expectedFileName
        val sizeMatches = expectedSizeBytes <= 0L || actualSize == expectedSizeBytes
        val extensionMatches = expectedExtension.isBlank() ||
                actualExtension.equals(expectedExtension, ignoreCase = true)

        Log.d(
            TAG,
            "download_artifact modelId=$modelId sourceUrl=$downloadUrl contentType=${contentType.orEmpty()} " +
                    "fileName=${finalFile.name} path=${finalFile.absolutePath} sizeBytes=$actualSize ext=$actualExtension " +
                    "matchesAllowlistName=$fileNameMatches matchesAllowlistSize=$sizeMatches " +
                    "matchesAllowlistExtension=$extensionMatches"
        )

        if (isDebugBuild()) {
            val magicHex = readMagicBytesHex(finalFile)
            if (magicHex.isNotBlank()) {
                Log.d(TAG, "download_artifact_magic modelId=$modelId bytes=$magicHex")
            }
        }
    }

    private fun readMagicBytesHex(file: File): String {
        return runCatching {
            val buffer = ByteArray(16)
            FileInputStream(file).use { input ->
                val read = input.read(buffer)
                if (read <= 0) {
                    ""
                } else {
                    buffer.copyOf(read).joinToString(" ") { byte ->
                        "%02X".format(byte.toInt() and 0xFF)
                    }
                }
            }
        }.getOrDefault("")
    }

    private fun safeId(modelId: String): String {
        return modelId.replace(nonSafeIdCharacterRegex, "_")
    }

    private fun isDebugBuild(): Boolean {
        return (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    private fun toFriendlyFailure(raw: String): String {
        val message = raw.trim()
        if (message.isBlank()) return "Unable to complete the model download."

        val lowercase = message.lowercase()
        return when {
            "enospc" in lowercase || "no space left" in lowercase -> {
                "Not enough storage to complete this download."
            }

            "socket" in lowercase || "timeout" in lowercase || "connection" in lowercase -> {
                "Download failed due to a network issue. Try again."
            }

            "401" in lowercase || "403" in lowercase -> {
                "Access denied. The model may not be publicly available."
            }

            else -> message
        }
    }

    /**
     * Stops the foreground service if no active download jobs remain.
     */
    private fun stopForegroundServiceIfIdle() {
        val hasActiveDownloads = jobs.values.any { it.isActive }
        if (!hasActiveDownloads) {
            ModelDownloadService.stop(appContext)
        }
    }

    private companion object {
        const val TAG = "ModelDownloadCoordinator"
    }
}
