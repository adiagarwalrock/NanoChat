package com.fcm.nanochat.models.runtime

import android.content.Context
import android.util.Log
import com.fcm.nanochat.models.allowlist.AllowlistDefaultConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class RuntimeHandle(
    val runtime: LocalModelRuntime,
    val initDurationMs: Long
)

class ModelRuntimeManager(
    context: Context
) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()

    private var activeModelId: String? = null
    private var activeModelPath: String? = null
    private var activeRuntime: LocalModelRuntime? = null

    suspend fun acquire(
        modelId: String,
        modelPath: String,
        defaultConfig: AllowlistDefaultConfig,
        expectedFileName: String? = null,
        expectedFileType: String? = null,
        expectedSizeBytes: Long = 0L
    ): RuntimeHandle {
        return mutex.withLock {
            val shouldReuse = activeRuntime != null &&
                    activeModelId == modelId &&
                    activeModelPath == modelPath
            if (shouldReuse) {
                Log.d(TAG, "Reusing local runtime for modelId=$modelId")
                return@withLock RuntimeHandle(
                    runtime = checkNotNull(activeRuntime),
                    initDurationMs = 0L
                )
            }

            Log.d(TAG, "Preparing local runtime modelId=$modelId")
            activeRuntime?.close()
            activeRuntime = null
            activeModelId = null
            activeModelPath = null

            val initStart = System.currentTimeMillis()
            val runtime = runCatching {
                LiteRtLmRuntimeFactory.create(
                    context = appContext,
                    modelId = modelId,
                    modelPath = modelPath,
                    config = defaultConfig,
                    expectedFileName = expectedFileName,
                    expectedFileType = expectedFileType,
                    expectedSizeBytes = expectedSizeBytes
                )
            }.getOrElse { error ->
                Log.e(TAG, "Failed to initialize local runtime", error)
                throw error
            }
            val initDuration = System.currentTimeMillis() - initStart

            activeModelId = modelId
            activeModelPath = modelPath
            activeRuntime = runtime

            Log.d(TAG, "Local runtime ready modelId=$modelId initMs=$initDuration")

            RuntimeHandle(runtime = runtime, initDurationMs = initDuration)
        }
    }

    suspend fun release() {
        mutex.withLock {
            Log.d(TAG, "Releasing local runtime modelId=${activeModelId.orEmpty()}")
            activeRuntime?.close()
            activeRuntime = null
            activeModelId = null
            activeModelPath = null
        }
    }

    suspend fun probe(modelPath: String, defaultConfig: AllowlistDefaultConfig): String? {
        return probe(
            modelId = "unknown",
            modelPath = modelPath,
            defaultConfig = defaultConfig,
            expectedFileName = null,
            expectedFileType = null,
            expectedSizeBytes = 0L
        )
    }

    suspend fun probe(
        modelId: String,
        modelPath: String,
        defaultConfig: AllowlistDefaultConfig,
        expectedFileName: String?,
        expectedFileType: String?,
        expectedSizeBytes: Long
    ): String? {
        return mutex.withLock {
            if (!LiteRtLmRuntimeFactory.isRuntimeClassAvailable()) {
                return@withLock "Local runtime is unavailable on this build."
            }
            LiteRtLmRuntimeFactory.probe(
                context = appContext,
                modelId = modelId,
                modelPath = modelPath,
                config = defaultConfig,
                expectedFileName = expectedFileName,
                expectedFileType = expectedFileType,
                expectedSizeBytes = expectedSizeBytes
            )
        }
    }

    private companion object {
        const val TAG = "ModelRuntimeManager"
    }
}
