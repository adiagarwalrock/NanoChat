package com.fcm.nanochat.models.runtime

import android.content.Context
import android.util.Log
import com.fcm.nanochat.models.allowlist.AllowlistDefaultConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
    private var activeConfigSignature: String? = null
    private var activeRuntime: LocalModelRuntime? = null

    private val _loadState = MutableStateFlow(RuntimeLoadState())
    val loadState: StateFlow<RuntimeLoadState> = _loadState.asStateFlow()

    suspend fun acquire(
        modelId: String,
        modelPath: String,
        defaultConfig: AllowlistDefaultConfig,
        expectedFileName: String? = null,
        expectedFileType: String? = null,
        expectedSizeBytes: Long = 0L
    ): RuntimeHandle {
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val shouldReuse = activeRuntime != null &&
                        activeModelId?.trim()?.lowercase() == modelId.trim().lowercase() &&
                        activeModelPath == modelPath &&
                        activeConfigSignature == configSignature(defaultConfig)
                if (shouldReuse) {
                    Log.d(TAG, "Reusing local runtime for modelId=$modelId")
                    _loadState.value = RuntimeLoadState(
                        phase = RuntimeLoadPhase.LOADED,
                        modelId = modelId,
                        message = null
                    )
                    return@withLock RuntimeHandle(
                        runtime = checkNotNull(activeRuntime),
                        initDurationMs = 0L
                    )
                }

                Log.d(TAG, "Preparing local runtime modelId=$modelId")
                _loadState.value = RuntimeLoadState(
                    phase = RuntimeLoadPhase.LOADING,
                    modelId = modelId,
                    message = null
                )
                activeRuntime?.close()
                activeRuntime = null
                activeModelId = null
                activeModelPath = null
                activeConfigSignature = null

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
                    _loadState.value = RuntimeLoadState(
                        phase = RuntimeLoadPhase.FAILED,
                        modelId = modelId,
                        message = error.message
                    )
                    throw error
                }
                val initDuration = System.currentTimeMillis() - initStart

                activeModelId = modelId
                activeModelPath = modelPath
                activeConfigSignature = configSignature(defaultConfig)
                activeRuntime = runtime

                _loadState.value = RuntimeLoadState(
                    phase = RuntimeLoadPhase.LOADED,
                    modelId = modelId,
                    message = null
                )

                Log.d(TAG, "Local runtime ready modelId=$modelId initMs=$initDuration")

                RuntimeHandle(runtime = runtime, initDurationMs = initDuration)
            }
        }
    }

    suspend fun release(reason: RuntimeReleaseReason = RuntimeReleaseReason.GENERIC) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val releasedModelId = activeModelId
                Log.d(TAG, "Releasing local runtime modelId=${activeModelId.orEmpty()}")
                activeRuntime?.close()
                activeRuntime = null
                activeModelId = null
                activeModelPath = null
                activeConfigSignature = null
                _loadState.value = RuntimeLoadState(
                    phase = if (reason == RuntimeReleaseReason.EJECTED) {
                        RuntimeLoadPhase.EJECTED
                    } else {
                        RuntimeLoadPhase.IDLE
                    },
                    modelId = releasedModelId,
                    message = null
                )
            }
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
        return withContext(Dispatchers.IO) {
            mutex.withLock {
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
    }

    fun getActiveSessionId(): Long? {
        return activeRuntime?.getActiveSessionId()
    }

    private companion object {
        const val TAG = "ModelRuntimeManager"
    }

    private fun configSignature(config: AllowlistDefaultConfig): String {
        return "${config.topK}|${config.topP}|${config.temperature}|${config.maxTokens}|${config.accelerators}|${config.strictAccelerator}"
    }
}
