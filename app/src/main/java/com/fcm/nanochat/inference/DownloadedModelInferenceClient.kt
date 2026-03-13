package com.fcm.nanochat.inference

import com.fcm.nanochat.data.SettingsSnapshot
import com.fcm.nanochat.models.compatibility.LocalModelCompatibilityState
import com.fcm.nanochat.models.registry.ModelInstallState
import com.fcm.nanochat.models.registry.ModelRegistry
import com.fcm.nanochat.models.runtime.LocalRuntimeMetrics
import com.fcm.nanochat.models.runtime.LocalRuntimeTelemetry
import com.fcm.nanochat.models.runtime.ModelRuntimeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class DownloadedModelInferenceClient(
    private val modelRegistry: ModelRegistry,
    private val runtimeManager: ModelRuntimeManager,
    private val telemetry: LocalRuntimeTelemetry
) : InferenceClient {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun availability(settings: SettingsSnapshot): BackendAvailability {
        val active = modelRegistry.activeModelStatus.value
        val modelId = active.modelId
        if (modelId.isNullOrBlank()) {
            return BackendAvailability.Unavailable(
                "No local model selected. Open the Models tab and choose one."
            )
        }

        if (!active.ready) {
            return BackendAvailability.Unavailable(
                active.message ?: "Selected local model is not ready."
            )
        }

        val record = modelRegistry.records.value.firstOrNull { it.modelId == modelId }
            ?: return BackendAvailability.Unavailable(
                "Selected local model is unavailable. Choose another model."
            )

        if (record.installState != ModelInstallState.INSTALLED || record.localPath.isNullOrBlank()) {
            return BackendAvailability.Unavailable(
                "Selected local model is not installed."
            )
        }

        return when (val compatibility = record.compatibility) {
            LocalModelCompatibilityState.Ready -> BackendAvailability.Available
            is LocalModelCompatibilityState.DownloadedButNotActivatable -> {
                BackendAvailability.Unavailable(compatibility.reason)
            }

            is LocalModelCompatibilityState.RuntimeUnavailable -> {
                BackendAvailability.Unavailable(compatibility.reason)
            }

            LocalModelCompatibilityState.TokenRequired -> {
                BackendAvailability.Unavailable("Hugging Face token is required for this model.")
            }

            LocalModelCompatibilityState.CorruptedModel -> {
                BackendAvailability.Unavailable("Installed model is corrupted. Re-download and retry.")
            }

            is LocalModelCompatibilityState.NeedsMoreStorage -> {
                BackendAvailability.Unavailable("Insufficient storage for this model.")
            }

            is LocalModelCompatibilityState.NeedsMoreRam -> {
                BackendAvailability.Unavailable("This model needs more RAM on this device.")
            }

            is LocalModelCompatibilityState.UnsupportedDevice -> {
                BackendAvailability.Unavailable(compatibility.reason)
            }

            LocalModelCompatibilityState.Downloadable -> {
                BackendAvailability.Unavailable("Download this local model before using it.")
            }
        }
    }

    override fun streamChat(request: InferenceRequest): Flow<String> = flow {
        when (val availability = availability(request.settings)) {
            BackendAvailability.Available -> Unit
            is BackendAvailability.Unavailable -> {
                throw InferenceException.BackendUnavailable(availability.message)
            }
        }

        val active = modelRegistry.activeModelStatus.value
        val activeModelId = active.modelId
            ?: throw InferenceException.Configuration("No local model selected.")
        val record = modelRegistry.records.value.firstOrNull { it.modelId == activeModelId }
            ?: throw InferenceException.BackendUnavailable("Selected local model is unavailable.")
        val model = record.allowlistedModel
        val localPath = record.localPath
            ?: throw InferenceException.BackendUnavailable("Selected model file is missing.")

        val runtimeHandle = runtimeManager.acquire(
            modelId = activeModelId,
            modelPath = localPath,
            defaultConfig = model?.defaultConfig
                ?: request.settings.toFallbackDownloadedConfig()
        )

        val formattedPrompt = PromptFormatter.flattenForDownloadedModel(
            history = request.history,
            prompt = request.prompt,
            maxTurns = 20
        )

        val generationStart = System.currentTimeMillis()
        val generated = withContext(Dispatchers.IO) {
            runtimeHandle.runtime.generate(formattedPrompt)
        }
        val generatedAt = System.currentTimeMillis()

        val normalized = generated.trim()
        if (normalized.isBlank()) {
            throw InferenceException.BackendUnavailable(
                "Local model returned an empty response. Try another prompt."
            )
        }

        val chunks = normalized.streamingChunks()
        var emittedTokens = 0
        var firstTokenAt = 0L
        chunks.forEach { chunk ->
            coroutineContext.ensureActive()
            if (firstTokenAt == 0L) {
                firstTokenAt = System.currentTimeMillis()
            }
            emittedTokens += 1
            emit(chunk)
        }
        val finishedAt = System.currentTimeMillis()

        val generationDurationMs = (finishedAt - generationStart).coerceAtLeast(1L)
        val tokensPerSecond = emittedTokens * 1000.0 / generationDurationMs.toDouble()

        telemetry.onMetrics(
            LocalRuntimeMetrics(
                modelId = activeModelId,
                initDurationMs = runtimeHandle.initDurationMs,
                timeToFirstTokenMs = if (firstTokenAt == 0L) {
                    generatedAt - generationStart
                } else {
                    firstTokenAt - generationStart
                },
                generationDurationMs = generationDurationMs,
                tokensPerSecond = tokensPerSecond,
                backend = "litert-lm-mediapipe"
            )
        )
    }

    override fun release() {
        scope.launch {
            runtimeManager.release()
        }
    }

    private fun String.streamingChunks(): List<String> {
        val value = this
        if (value.length <= 24) return listOf(value)
        return value
            .split(Regex("(?<=\\s)"))
            .filter { it.isNotEmpty() }
    }

    private fun SettingsSnapshot.toFallbackDownloadedConfig(): com.fcm.nanochat.models.allowlist.AllowlistDefaultConfig {
        return com.fcm.nanochat.models.allowlist.AllowlistDefaultConfig(
            topK = 40,
            topP = topP,
            temperature = temperature,
            maxTokens = contextLength,
            accelerators = "cpu"
        )
    }
}
