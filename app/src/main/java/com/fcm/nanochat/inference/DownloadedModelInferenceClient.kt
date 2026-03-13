package com.fcm.nanochat.inference

import android.util.Log
import com.fcm.nanochat.data.SettingsSnapshot
import com.fcm.nanochat.models.compatibility.LocalModelCompatibilityState
import com.fcm.nanochat.models.registry.InstalledModelRecord
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
        val activeModelId = resolveActiveModelId(settings)
            ?: return BackendAvailability.Unavailable(
                "No local model selected. Open Model Library and choose one."
            )
        val record = resolveActiveModelRecord(activeModelId)
            ?: return BackendAvailability.Unavailable(
                "Selected local model is unavailable. Choose another model."
            )

        if (!record.isLegacy && record.allowlistedModel?.recommendedForChat == false) {
            return BackendAvailability.Unavailable(
                "Selected local model is not optimized for chat in NanoChat."
            )
        }

        if (record.installState != ModelInstallState.INSTALLED) {
            return BackendAvailability.Unavailable("Selected local model is not installed.")
        }

        val localPath = record.localPath?.trim().orEmpty()
        if (localPath.isBlank()) {
            return BackendAvailability.Unavailable(
                "Selected local model is missing its file path. Re-download the model."
            )
        }

        val file = java.io.File(localPath)
        if (!file.exists() || file.length() <= 0L) {
            return BackendAvailability.Unavailable(
                "Selected local model file is missing. Re-download the model."
            )
        }

        return when (val compatibility = record.compatibility) {
            LocalModelCompatibilityState.Ready -> BackendAvailability.Available
            LocalModelCompatibilityState.Downloadable -> {
                BackendAvailability.Unavailable("Download this local model before using it.")
            }

            LocalModelCompatibilityState.TokenRequired -> {
                BackendAvailability.Unavailable("Hugging Face token is required for this model.")
            }

            is LocalModelCompatibilityState.NeedsMoreRam -> {
                BackendAvailability.Unavailable(
                    "This model needs ${compatibility.requiredGb} GB RAM."
                )
            }

            is LocalModelCompatibilityState.NeedsMoreStorage -> {
                BackendAvailability.Unavailable("Not enough free storage for this model.")
            }

            is LocalModelCompatibilityState.UnsupportedDevice -> {
                BackendAvailability.Unavailable(toFriendlyRuntimeError(compatibility.reason))
            }

            is LocalModelCompatibilityState.DownloadedButNotActivatable -> {
                BackendAvailability.Unavailable(toFriendlyRuntimeError(compatibility.reason))
            }

            LocalModelCompatibilityState.CorruptedModel -> {
                BackendAvailability.Unavailable(
                    "This install appears incomplete. Try re-downloading."
                )
            }

            is LocalModelCompatibilityState.RuntimeUnavailable -> {
                BackendAvailability.Unavailable(toFriendlyRuntimeError(compatibility.reason))
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

        val activeModelId = resolveActiveModelId(request.settings)
            ?: throw InferenceException.Configuration("No local model selected.")
        val record = resolveActiveModelRecord(activeModelId)
            ?: throw InferenceException.BackendUnavailable(
                "Selected local model is unavailable. Choose another model."
            )
        val resolvedModelId = record.modelId
        val model = record.allowlistedModel
        val localPath = record.localPath?.trim().orEmpty()
        if (localPath.isBlank()) {
            throw InferenceException.BackendUnavailable(
                "Selected local model file is missing. Re-download the model."
            )
        }

        Log.d(TAG, "Starting local generation with modelId=$resolvedModelId")

        val runtimeHandle = runCatching {
            runtimeManager.acquire(
                modelId = resolvedModelId,
                modelPath = localPath,
                defaultConfig = model?.defaultConfig
                    ?: request.settings.toFallbackDownloadedConfig()
            )
        }.getOrElse { error ->
            Log.e(TAG, "Failed to initialize downloaded runtime", error)
            throw InferenceException.BackendUnavailable(toFriendlyRuntimeError(error.message))
        }

        val formattedPrompt = PromptFormatter.flattenForDownloadedModel(
            history = request.history,
            prompt = request.prompt,
            maxTurns = 20
        )

        val generationStart = System.currentTimeMillis()
        val generated = runCatching {
            withContext(Dispatchers.IO) {
                runtimeHandle.runtime.generate(formattedPrompt)
            }
        }.getOrElse { error ->
            Log.e(TAG, "Downloaded runtime generation failed", error)
            scope.launch { runtimeManager.release() }
            throw InferenceException.BackendUnavailable(toFriendlyRuntimeError(error.message))
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
                modelId = resolvedModelId,
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
            Log.d(TAG, "Releasing downloaded runtime")
            runtimeManager.release()
        }
    }

    private fun resolveActiveModelId(settings: SettingsSnapshot): String? {
        val preferredId = settings.activeLocalModelId.trim().lowercase()
        val fallbackId =
            modelRegistry.activeModelStatus.value.modelId?.trim()?.lowercase().orEmpty()
        val candidateId = preferredId.ifBlank { fallbackId }
        return candidateId.ifBlank { null }
    }

    private fun resolveActiveModelRecord(activeModelId: String): InstalledModelRecord? {
        return modelRegistry.records.value.firstOrNull {
            it.modelId.equals(activeModelId, ignoreCase = true)
        }
    }

    private fun String.streamingChunks(): List<String> {
        if (length <= 24) return listOf(this)
        return split(Regex("(?<=\\s)"))
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

    private fun toFriendlyRuntimeError(raw: String?): String {
        val message = raw?.trim().orEmpty()
        if (message.isBlank()) {
            return "This model could not start on this device."
        }

        val lowercase = message.lowercase()
        return when {
            "missing runtime option method" in lowercase ||
                    "settopk" in lowercase ||
                    "setmaxtokens" in lowercase -> {
                "This downloaded file may be incompatible with the current runtime."
            }

            "missing" in lowercase && "file" in lowercase -> {
                "This install appears incomplete. Try re-downloading."
            }

            "outofmemory" in lowercase || "out of memory" in lowercase -> {
                "This model needs more memory on this device."
            }

            "permission" in lowercase -> {
                "NanoChat cannot access the local model file. Move or re-download the model."
            }

            else -> "This model could not start on this device."
        }
    }

    private companion object {
        const val TAG = "DownloadedInference"
    }
}
