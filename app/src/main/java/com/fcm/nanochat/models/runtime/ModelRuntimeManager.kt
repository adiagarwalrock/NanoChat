package com.fcm.nanochat.models.runtime

import android.content.Context
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
        defaultConfig: AllowlistDefaultConfig
    ): RuntimeHandle {
        return mutex.withLock {
            val shouldReuse = activeRuntime != null &&
                activeModelId == modelId &&
                activeModelPath == modelPath
            if (shouldReuse) {
                return@withLock RuntimeHandle(
                    runtime = checkNotNull(activeRuntime),
                    initDurationMs = 0L
                )
            }

            activeRuntime?.close()

            val initStart = System.currentTimeMillis()
            val runtime = MediaPipeLlmRuntimeFactory.create(
                context = appContext,
                modelPath = modelPath,
                config = defaultConfig
            )
            val initDuration = System.currentTimeMillis() - initStart

            activeModelId = modelId
            activeModelPath = modelPath
            activeRuntime = runtime

            RuntimeHandle(runtime = runtime, initDurationMs = initDuration)
        }
    }

    suspend fun release() {
        mutex.withLock {
            activeRuntime?.close()
            activeRuntime = null
            activeModelId = null
            activeModelPath = null
        }
    }

    suspend fun probe(modelPath: String, defaultConfig: AllowlistDefaultConfig): String? {
        return mutex.withLock {
            if (!MediaPipeLlmRuntimeFactory.isRuntimeClassAvailable()) {
                return@withLock "Local runtime is unavailable on this build."
            }
            MediaPipeLlmRuntimeFactory.probe(
                context = appContext,
                modelPath = modelPath,
                config = defaultConfig
            )
        }
    }
}
