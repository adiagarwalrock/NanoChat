package com.fcm.nanochat.models.runtime

import android.content.Context
import android.util.Log
import java.io.File
import java.security.MessageDigest
import java.util.Locale

enum class RuntimeCacheState {
    DISABLED,
    COLD,
    WARM,
    RECOVERED,
    ENGINE_REUSED
}

internal data class RuntimeCachePreparation(
    val directory: File?,
    val state: RuntimeCacheState,
    val sizeBytes: Long
)

internal data class RuntimeCacheInitialization<T>(
    val value: T,
    val state: RuntimeCacheState
)

class ModelRuntimeCache internal constructor(
    private val rootDirectory: File,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES
) {
    constructor(context: Context) : this(File(context.cacheDir, CACHE_ROOT_DIRECTORY))

    init {
        require(maxEntries > 0) { "Runtime cache must retain at least one entry." }
    }

    @Synchronized
    internal fun prepare(modelId: String): RuntimeCachePreparation {
        val directory = directoryFor(modelId)
        return runCatching {
            checkDirectory(directory)
            val sizeBytes = directorySize(directory)
            RuntimeCachePreparation(
                directory = directory,
                state = if (sizeBytes > 0L) RuntimeCacheState.WARM else RuntimeCacheState.COLD,
                sizeBytes = sizeBytes
            )
        }.getOrElse { error ->
            Log.w(TAG, "Runtime cache unavailable; continuing without cache", error)
            RuntimeCachePreparation(
                directory = null,
                state = RuntimeCacheState.DISABLED,
                sizeBytes = 0L
            )
        }
    }

    @Synchronized
    internal fun markInitialized(modelId: String): Long {
        val currentDirectory = directoryFor(modelId)
        if (!currentDirectory.isDirectory) return 0L

        if (!currentDirectory.setLastModified(System.currentTimeMillis())) {
            Log.w(TAG, "Unable to update runtime cache recency")
        }
        prune(currentDirectory)
        return directorySize(currentDirectory)
    }

    @Synchronized
    fun clear(modelId: String): Boolean {
        val directory = directoryFor(modelId)
        return runCatching {
            !directory.exists() || directory.deleteRecursively()
        }.getOrElse { error ->
            Log.w(TAG, "Unable to clear runtime cache", error)
            false
        }.also { cleared ->
            if (!cleared) {
                Log.w(TAG, "Runtime cache directory could not be fully removed")
            }
        }
    }

    internal fun directoryFor(modelId: String): File {
        val normalized = modelId.trim().lowercase(Locale.US)
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
        val key = digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
        return File(rootDirectory, key)
    }

    private fun checkDirectory(directory: File) {
        if (!rootDirectory.exists() && !rootDirectory.mkdirs()) {
            error("Unable to create runtime cache root.")
        }
        if (!rootDirectory.isDirectory) {
            error("Runtime cache root is not a directory.")
        }
        if (!directory.exists() && !directory.mkdirs()) {
            error("Unable to create model runtime cache.")
        }
        if (!directory.isDirectory) {
            error("Model runtime cache is not a directory.")
        }
    }

    private fun prune(currentDirectory: File) {
        val retainedInactiveEntries = (maxEntries - 1).coerceAtLeast(0)
        val staleDirectories =
            rootDirectory.listFiles()
                ?.filter { it.isDirectory && it != currentDirectory }
                ?.sortedByDescending(File::lastModified)
                ?.drop(retainedInactiveEntries)
                .orEmpty()

        staleDirectories.forEach { stale ->
            runCatching { stale.deleteRecursively() }
                .onFailure { error ->
                    Log.w(TAG, "Unable to prune stale runtime cache", error)
                }
        }
    }

    private fun directorySize(directory: File): Long {
        return runCatching {
            if (!directory.exists()) {
                0L
            } else {
                directory.walkTopDown()
                    .filter(File::isFile)
                    .fold(0L) { total, file -> total + file.length() }
            }
        }.getOrElse { error ->
            Log.w(TAG, "Unable to measure runtime cache", error)
            0L
        }
    }

    private companion object {
        const val TAG = "ModelRuntimeCache"
        const val CACHE_ROOT_DIRECTORY = "litertlm"
        const val DEFAULT_MAX_ENTRIES = 2
    }
}

internal inline fun <T> initializeWithCacheRecovery(
    initialPreparation: RuntimeCachePreparation,
    recoverCache: () -> RuntimeCachePreparation,
    initialize: (cacheDirectory: File?) -> T
): RuntimeCacheInitialization<T> {
    return try {
        RuntimeCacheInitialization(
            value = initialize(initialPreparation.directory),
            state = initialPreparation.state
        )
    } catch (initialError: Throwable) {
        if (initialPreparation.state != RuntimeCacheState.WARM) {
            throw initialError
        }

        val recoveredPreparation = recoverCache()
        try {
            RuntimeCacheInitialization(
                value = initialize(recoveredPreparation.directory),
                state = RuntimeCacheState.RECOVERED
            )
        } catch (recoveryError: Throwable) {
            initialError.addSuppressed(recoveryError)
            throw initialError
        }
    }
}
