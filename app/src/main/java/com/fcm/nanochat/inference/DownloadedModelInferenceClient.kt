package com.fcm.nanochat.inference

import android.util.Log
import com.fcm.nanochat.data.AcceleratorPreference
import com.fcm.nanochat.data.SettingsSnapshot
import com.fcm.nanochat.data.media.ChatMediaStore
import com.fcm.nanochat.models.allowlist.AllowlistDefaultConfig
import com.fcm.nanochat.models.compatibility.LocalModelCompatibilityState
import com.fcm.nanochat.models.registry.InstalledModelRecord
import com.fcm.nanochat.models.registry.ModelInstallState
import com.fcm.nanochat.models.registry.ModelRegistry
import com.fcm.nanochat.models.runtime.LocalRuntimeAttachment
import com.fcm.nanochat.models.runtime.LocalRuntimeTelemetry
import com.fcm.nanochat.models.runtime.ModelRuntimeManager
import com.fcm.nanochat.util.CrashReporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class DownloadedModelInferenceClient(
    private val modelRegistry: ModelRegistry,
    private val runtimeManager: ModelRuntimeManager,
    private val mediaStore: ChatMediaStore,
    private val telemetry: LocalRuntimeTelemetry,
    private val crashReporter: CrashReporter
) : InferenceClient {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun availability(settings: SettingsSnapshot): BackendAvailability {
        val activeModelId =
            resolveActiveModelId(settings, settings.activeLocalModelId)
                ?: return BackendAvailability.Unavailable(
                    "No local model selected. Open Model Library and choose one."
                )
        val record =
            resolveActiveModelRecord(activeModelId)
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
            LocalModelCompatibilityState.Ready -> availabilityForReadyModel(record)
            LocalModelCompatibilityState.Downloadable -> {
                BackendAvailability.Unavailable("Download this local model before using it.")
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
            LocalModelCompatibilityState.UnsupportedForChat -> {
                BackendAvailability.Unavailable("This model is not designed for chat in NanoChat.")
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

    override suspend fun capabilities(settings: SettingsSnapshot): InferenceCapabilities {
        val runtimeMultimodalReady = supportsRuntimeMultimodalContent()
        val activeModelId = resolveActiveModelId(settings, settings.activeLocalModelId)
        val record = activeModelId?.let { resolveActiveModelRecord(it) }
        val model = record?.allowlistedModel
        val missingModelReason = if (activeModelId == null || record == null) {
            "Choose an installed local model to use multimodal input."
        } else {
            "Selected local model does not declare multimodal support."
        }

        val visionState = when {
            !isDownloadedImageInputEnabled() -> SupportedState.unsupported(
                downloadedImageInputDisabledReason()
            )

            !runtimeMultimodalReady -> SupportedState.unsupported(
                "Current local runtime build does not expose image content support."
            )

            model == null -> SupportedState.unsupported(missingModelReason)
            !model.llmSupportImage -> SupportedState.unsupported(
                "Selected local model does not support image understanding."
            )

            else -> SupportedState.supported()
        }

        val audioState = when {
            !runtimeMultimodalReady -> SupportedState.unsupported(
                "Current local runtime build does not expose audio content support."
            )

            model == null -> SupportedState.unsupported(missingModelReason)
            !model.llmSupportAudio -> SupportedState.unsupported(
                "Selected local model does not support audio understanding."
            )

            else -> SupportedState.supported()
        }

        return InferenceCapabilities(
            textGeneration = SupportedState.supported(),
            visionUnderstanding = visionState,
            audioTranscription = audioState,
            streaming = SupportedState.supported()
        )
    }

    private fun availabilityForReadyModel(record: InstalledModelRecord): BackendAvailability {
        val runtimeState = runtimeManager.loadState.value
        val runtimeModelId = normalizeModelId(runtimeState.modelId)
        val selectedModelId = normalizeModelId(record.modelId)
        val matchesSelectedModel = runtimeModelId == selectedModelId

        return when (runtimeState.phase) {
            com.fcm.nanochat.models.runtime.RuntimeLoadPhase.LOADING -> {
                if (matchesSelectedModel) {
                    BackendAvailability.Unavailable(
                        "Selected local model is being prepared for local chat."
                    )
                } else {
                    notReadyYetAvailability()
                }
            }
            com.fcm.nanochat.models.runtime.RuntimeLoadPhase.LOADED -> {
                if (matchesSelectedModel) {
                    BackendAvailability.Available
                } else {
                    notReadyYetAvailability()
                }
            }
            com.fcm.nanochat.models.runtime.RuntimeLoadPhase.EJECTED,
            com.fcm.nanochat.models.runtime.RuntimeLoadPhase.IDLE -> {
                notReadyYetAvailability()
            }
            com.fcm.nanochat.models.runtime.RuntimeLoadPhase.FAILED -> {
                if (runtimeModelId.isBlank() || matchesSelectedModel) {
                    BackendAvailability.Unavailable(
                        "Selected local model failed to prepare for local chat. Tap Use model to retry."
                    )
                } else {
                    notReadyYetAvailability()
                }
            }
        }
    }

    private fun notReadyYetAvailability(): BackendAvailability.Unavailable {
        return BackendAvailability.Unavailable(
            "Selected local model is not ready yet. NanoChat is preparing it now."
        )
    }

    override fun streamChat(request: InferenceRequest): Flow<String> = flow {
        if (request.settings.inferenceMode != InferenceMode.DOWNLOADED) {
            Log.w(TAG, "Downloaded client invoked with mode=${request.settings.inferenceMode.name}")
        }

        when (val availability = availability(request.settings)) {
            BackendAvailability.Available -> Unit
            is BackendAvailability.Unavailable -> {
                throw InferenceException.BackendUnavailable(availability.message)
            }
        }

        val activeModelId =
            resolveActiveModelId(request.settings, request.activeDownloadedModelId)
                ?: throw InferenceException.Configuration("No local model selected.")
        val record =
            resolveActiveModelRecord(activeModelId)
                ?: throw InferenceException.BackendUnavailable(
                    "Selected local model is unavailable. Choose another model."
                )

        val resolvedModelId = record.modelId
        val normalizedResolvedModelId = normalizeModelId(resolvedModelId)
        val model = record.allowlistedModel
        val imageAttachment = request.imageAttachment
        if (imageAttachment != null) {
            if (!isDownloadedImageInputEnabled()) {
                throw InferenceException.UnsupportedModality(
                    downloadedImageInputDisabledReason()
                )
            }
            if (!supportsRuntimeMultimodalContent()) {
                throw InferenceException.UnsupportedModality(
                    "Current local runtime build does not expose image content support."
                )
            }
            if (model?.llmSupportImage != true) {
                throw InferenceException.UnsupportedModality(
                    "Selected local model does not support image understanding."
                )
            }
        }

        val runtimeAttachments = mutableListOf<LocalRuntimeAttachment>()
        imageAttachment?.let { attachment ->
            val imageFile = mediaStore.resolveFile(attachment.relativePath)
                ?: throw InferenceException.MissingFile("Selected image file is missing.")
            runtimeAttachments += LocalRuntimeAttachment.ImageFile(imageFile.absolutePath)
        }

        val baseRuntimeConfig =
            applyAcceleratorPreference(
                config = model?.defaultConfig
                    ?: request.settings.toFallbackDownloadedConfig(),
                preference = request.settings.acceleratorPreference
            )
        val localPath = record.localPath?.trim().orEmpty()
        if (localPath.isBlank()) {
            throw InferenceException.BackendUnavailable(
                "Selected local model file is missing. Re-download the model."
            )
        }

        Log.d(
            TAG,
            "Starting local generation modelId=$resolvedModelId selectedModelId=$activeModelId path=$localPath installState=${record.installState} historyTurns=${request.history.size}"
        )
        crashReporter.logBreadcrumb(
            "local_generation_prepare modelId=$resolvedModelId sessionId=${request.sessionId ?: -1L}"
        )

        val activeSessionId = request.sessionId
        val runtimeState = runtimeManager.loadState.value
        val isReusing =
            activeSessionId != null &&
                    runtimeState.phase ==
                    com.fcm.nanochat.models.runtime.RuntimeLoadPhase.LOADED &&
                    normalizeModelId(runtimeState.modelId) == normalizedResolvedModelId &&
                    runtimeManager.getActiveSessionId() == activeSessionId

        val promptFamily = model?.promptFamily.toPromptFamily()
        val isGemmaFamily = promptFamily == DownloadedPromptFamily.GEMMA
        val effectivePrompt = request.prompt.ifBlank {
            if (imageAttachment != null) {
                "Describe the attached image."
            } else {
                ""
            }
        }
        val formattedPrompt =
            if (isReusing) {
                val systemPrompt =
                    PromptFormatter.applyThinkingInstruction(
                        systemPrompt =
                            "You are NanoChat, a helpful local assistant. Reply in clean Markdown and keep numbered or bulleted lists on separate lines.",
                        effort = request.settings.thinkingEffort,
                        supportsThinking = model?.supportsThinking ?: false
                    )
                val finalSystemInstruction = if (isGemmaFamily) "" else systemPrompt
                val latestTurn =
                    PromptFormatter.formatLatestTurn(
                        prompt = effectivePrompt,
                        thinkingEffort = request.settings.thinkingEffort,
                        supportsThinking = model?.supportsThinking ?: false
                    )
                val finalUserMessage = latestTurn

                DownloadedPrompt(
                    family = promptFamily,
                    systemInstruction = finalSystemInstruction,
                    userMessage = finalUserMessage
                )
            } else {
                PromptFormatter.formatDownloadedPrompt(
                    history = request.history,
                    prompt = effectivePrompt,
                    maxTurns = 20,
                    promptFamily = model?.promptFamily ?: resolvedModelId,
                    thinkingEffort = request.settings.thinkingEffort,
                    supportsThinking = model?.supportsThinking ?: false
                )
            }

        logFormattedPrompt(
            modelId = resolvedModelId,
            family = formattedPrompt.family,
            systemInstruction = formattedPrompt.systemInstruction,
            userMessage = formattedPrompt.userMessage,
            isReusing = isReusing
        )

        suspend fun kotlinx.coroutines.flow.FlowCollector<String>.emitGenerationAttempt(
            runtimeConfig: AllowlistDefaultConfig,
            isRetry: Boolean
        ) {
            val currentRuntimeHandle =
                runCatching {
                    runtimeManager.acquire(
                        modelId = resolvedModelId,
                        modelPath = localPath,
                        defaultConfig = runtimeConfig,
                        expectedFileName = model?.modelFile,
                        expectedFileType = model?.fileType,
                        expectedSizeBytes = model?.sizeInBytes ?: 0L
                    )
                }
                    .getOrElse { error ->
                        Log.e(TAG, "Failed to initialize downloaded runtime", error)
                        throw InferenceException.BackendUnavailable(
                            toFriendlyRuntimeError(error)
                        )
                    }

            if (runtimeConfig.acceleratorHints.isNotEmpty()) {
                Log.d(
                    TAG,
                    "local_runtime_config modelId=$resolvedModelId accelerators=${
                        runtimeConfig.acceleratorHints.joinToString(
                            ","
                        )
                    }" +
                            " strict=${runtimeConfig.strictAccelerator}" +
                            " topK=${runtimeConfig.topK} topP=${runtimeConfig.topP} temperature=${runtimeConfig.temperature} maxTokens=${runtimeConfig.maxTokens}"
                )
            }

            val loadedModelId =
                normalizeModelId(runtimeManager.loadState.value.modelId)
            if (loadedModelId.isNotBlank() && loadedModelId != normalizedResolvedModelId) {
                throw InferenceException.BackendUnavailable(
                    "Selected local model is not the active runtime. Tap Use model and retry."
                )
            }

            val generationStart = System.currentTimeMillis()
            val noTokenWatchdogMs = configuredNoTokenWatchdogMs(isRetry)
            val visibleStallWatchdogMs = configuredVisibleStallWatchdogMs()
            val parentJob = currentCoroutineContext()[Job]
            var firstRawCallbackAt = 0L
            var firstVisibleTokenAt = 0L
            var lastVisibleProgressAt = 0L
            var rawCallbackCount = 0
            var nonVisibleChunkCount = 0
            var visibleChunkCount = 0
            var emittedVisibleChars = 0
            var rawOutput = ""
            var visibleOutput = ""
            var emittedOutput = ""
            var emissionUnlocked = false
            var completionReason = "completed"

            val stopSequenceDetector = StopSequenceDetector(stopSequences = emptyList())
            val repetitionDetector = RepetitionDetector
            val stopSequenceFound = AtomicBoolean(false)

            Log.d(
                TAG,
                "local_generation_started modelId=$resolvedModelId family=${formattedPrompt.family.name} watchdogMs=$noTokenWatchdogMs stallMs=$visibleStallWatchdogMs isRetry=$isRetry reusing=$isReusing"
            )
            crashReporter.logBreadcrumb(
                "local_generation_started modelId=$resolvedModelId watchdogMs=$noTokenWatchdogMs retry=$isRetry"
            )

            suspend fun emitStableDelta(currentSnapshot: String, chunkIndex: Int) {
                val emittedDelta = incrementalVisibleDelta(emittedOutput, currentSnapshot)
                if (emittedDelta.isBlank()) {
                    return
                }

                if (firstVisibleTokenAt == 0L) {
                    firstVisibleTokenAt = System.currentTimeMillis()
                    Log.d(
                        TAG,
                        "local_generation_first_token modelId=$resolvedModelId firstTokenMs=${firstVisibleTokenAt - generationStart} rawCallbacks=$rawCallbackCount"
                    )
                    crashReporter.updateInferenceStage(
                        stage = "local_first_token",
                        visibleChars = currentSnapshot.length
                    )
                }
                lastVisibleProgressAt = System.currentTimeMillis()
                visibleChunkCount += 1
                emittedVisibleChars += emittedDelta.length
                emittedOutput = currentSnapshot
                if (shouldLogChunk(chunkIndex)) {
                    Log.d(
                        TAG,
                        "local_generation_chunk_visible modelId=$resolvedModelId index=$chunkIndex deltaLen=${emittedDelta.length} visibleLen=${currentSnapshot.length} delta='${
                            chunkPreview(
                                emittedDelta
                            )
                        }'"
                    )
                }
                this@emitGenerationAttempt.emit(emittedDelta)
            }

            try {
                coroutineScope {
                    val noVisibleWatchdogJob = launch {
                        delay(noTokenWatchdogMs)
                        val now = System.currentTimeMillis()
                        if (shouldTriggerNoVisibleWatchdog(
                                firstVisibleTokenAtMs = firstVisibleTokenAt,
                                startedAtMs = generationStart,
                                nowMs = now,
                                thresholdMs = noTokenWatchdogMs
                            )
                        ) {
                            completionReason =
                                when (visibilityState(firstRawCallbackAt, firstVisibleTokenAt)
                                ) {
                                    LocalGenerationVisibilityState.NO_CALLBACK ->
                                        "no_callback_watchdog"

                                    LocalGenerationVisibilityState.CALLBACK_NO_VISIBLE ->
                                        "callbacks_without_visible_watchdog"

                                    LocalGenerationVisibilityState.VISIBLE_STARTED ->
                                        "visible_watchdog_skipped"
                                }
                            Log.w(
                                TAG,
                                "local_generation_watchdog_triggered modelId=$resolvedModelId waitedMs=$noTokenWatchdogMs state=${
                                    visibilityState(
                                        firstRawCallbackAt,
                                        firstVisibleTokenAt
                                    ).name
                                } rawCallbacks=$rawCallbackCount"
                            )
                            currentRuntimeHandle.runtime.cancelActiveGeneration(
                                "no_visible_watchdog"
                            )
                            parentJob?.cancel(NoTokenWatchdogTimeout(NO_TOKEN_WATCHDOG_MESSAGE))
                        }
                    }

                    val visibleStallWatchdogJob = launch {
                        while (true) {
                            delay(300L)
                            if (firstVisibleTokenAt == 0L) {
                                continue
                            }
                            val lastProgressAt =
                                lastVisibleProgressAt.takeIf { it > 0L } ?: firstVisibleTokenAt
                            val now = System.currentTimeMillis()
                            if (shouldTriggerVisibleStallWatchdog(
                                    firstVisibleTokenAtMs = firstVisibleTokenAt,
                                    lastVisibleProgressAtMs = lastProgressAt,
                                    nowMs = now,
                                    thresholdMs = visibleStallWatchdogMs
                                )
                            ) {
                                completionReason = "visible_stall_watchdog"
                                Log.w(
                                    TAG,
                                    "local_generation_visible_stall modelId=$resolvedModelId stallMs=$visibleStallWatchdogMs rawCallbacks=$rawCallbackCount visibleChunks=$visibleChunkCount visibleChars=$emittedVisibleChars"
                                )
                                currentRuntimeHandle.runtime.cancelActiveGeneration(
                                    "visible_stall_watchdog"
                                )
                                parentJob?.cancel(
                                    VisibleOutputStalledTimeout(VISIBLE_STALL_WATCHDOG_MESSAGE)
                                )
                                return@launch
                            }
                        }
                    }

                    try {
                        currentRuntimeHandle.runtime.stream(
                            sessionId = activeSessionId,
                            prompt = formattedPrompt.userMessage,
                            systemInstruction = formattedPrompt.systemInstruction,
                            attachments = runtimeAttachments
                        )
                            .collect { runtimeChunk ->
                                currentCoroutineContext().ensureActive()
                                rawCallbackCount += 1
                                if (firstRawCallbackAt == 0L) {
                                    firstRawCallbackAt = System.currentTimeMillis()
                                    Log.d(
                                        TAG,
                                        "local_generation_first_callback modelId=$resolvedModelId callbackMs=${firstRawCallbackAt - generationStart} rawChunk='${
                                            chunkPreview(
                                                runtimeChunk
                                            )
                                        }'"
                                    )
                                }

                                if (runtimeChunk.isBlank() ||
                                    runtimeChunk.equals("null", ignoreCase = true)
                                ) {
                                    nonVisibleChunkCount += 1
                                    if (shouldLogChunk(rawCallbackCount)) {
                                        Log.d(
                                            TAG,
                                            "local_generation_chunk_non_visible modelId=$resolvedModelId index=$rawCallbackCount reason=blank_or_null raw='${
                                                chunkPreview(
                                                    runtimeChunk
                                                )
                                            }'"
                                        )
                                    }
                                    return@collect
                                }

                                rawOutput = mergeRuntimeChunk(rawOutput, runtimeChunk)
                                val sanitizedSnapshot =
                                    GeneratedTextSanitizer.sanitize(rawOutput)
                                val snapshotDelta =
                                    incrementalVisibleDelta(
                                        visibleOutput,
                                        sanitizedSnapshot
                                    )
                                visibleOutput = sanitizedSnapshot

                                if (snapshotDelta.isBlank()) {
                                    nonVisibleChunkCount += 1
                                    if (shouldLogChunk(rawCallbackCount)) {
                                        Log.d(
                                            TAG,
                                            "local_generation_chunk_non_visible modelId=$resolvedModelId index=$rawCallbackCount reason=sanitized_empty raw='${
                                                chunkPreview(
                                                    runtimeChunk
                                                )
                                            }' sanitizedLen=${sanitizedSnapshot.length}"
                                        )
                                    }
                                    return@collect
                                }

                                if (repetitionDetector.isLikelyDegenerate(visibleOutput)) {
                                    completionReason = "degenerate_output"
                                    Log.w(
                                        TAG,
                                        "local_generation_degenerate_output modelId=$resolvedModelId sample='${
                                            chunkPreview(
                                                visibleOutput
                                            )
                                        }' visibleLen=${visibleOutput.length}"
                                    )
                                    currentRuntimeHandle.runtime.cancelActiveGeneration(
                                        "degenerate_output"
                                    )
                                    throw DegenerateOutputException()
                                }

                                if (stopSequenceDetector.hasStopSequence(visibleOutput)) {
                                    completionReason = "stop_sequence"
                                    Log.d(
                                        TAG,
                                        "local_generation_stop_sequence modelId=$resolvedModelId sequence='${stopSequenceDetector.foundSequence}' visibleLen=${visibleOutput.length}"
                                    )
                                    stopSequenceFound.set(true)
                                    currentRuntimeHandle.runtime.cancelActiveGeneration(
                                        "stop_sequence"
                                    )
                                    return@collect
                                }

                                if (!emissionUnlocked) {
                                    if (!shouldUnlockVisibleEmission(visibleOutput)) {
                                        nonVisibleChunkCount += 1
                                        if (shouldLogChunk(rawCallbackCount)) {
                                            Log.d(
                                                TAG,
                                                "local_generation_chunk_non_visible modelId=$resolvedModelId index=$rawCallbackCount reason=buffering_pre_unlock raw='${
                                                    chunkPreview(
                                                        runtimeChunk
                                                    )
                                                }' visibleLen=${visibleOutput.length}"
                                            )
                                        }
                                        return@collect
                                    }
                                    emissionUnlocked = true
                                }

                                emitStableDelta(
                                    currentSnapshot = visibleOutput,
                                    chunkIndex = rawCallbackCount
                                )
                            }
                    } finally {
                        noVisibleWatchdogJob.cancel()
                        visibleStallWatchdogJob.cancel()
                        Log.d(
                            TAG,
                            "local_generation_watchdog_stopped modelId=$resolvedModelId firstRawCallbackMs=${if (firstRawCallbackAt == 0L) -1 else firstRawCallbackAt - generationStart} firstVisibleMs=${if (firstVisibleTokenAt == 0L) -1 else firstVisibleTokenAt - generationStart}"
                        )
                    }
                }

                var finalizedVisibleOutput = GeneratedTextSanitizer.sanitize(rawOutput)
                if (stopSequenceFound.get()) {
                    finalizedVisibleOutput =
                        stopSequenceDetector.trimStopSequence(finalizedVisibleOutput)
                }

                if (repetitionDetector.isLikelyDegenerate(finalizedVisibleOutput)) {
                    completionReason = "degenerate_output"
                    throw DegenerateOutputException()
                }
                if (!emissionUnlocked && finalizedVisibleOutput.isNotBlank()) {
                    emissionUnlocked = true
                }
                if (emissionUnlocked) {
                    emitStableDelta(
                        currentSnapshot = finalizedVisibleOutput,
                        chunkIndex = rawCallbackCount
                    )
                }

                if (finalizedVisibleOutput.isBlank()) {
                    completionReason = "blank_after_sanitize"
                    throw InferenceException.BackendUnavailable(
                        "Local model produced no visible text. Retry, or reselect this model."
                    )
                }

                val finishedAt = System.currentTimeMillis()
                val generationDurationMs = (finishedAt - generationStart).coerceAtLeast(1L)
                val tokensPerSecond = visibleChunkCount * 1000.0 / generationDurationMs.toDouble()

                telemetry.onMetrics(
                    com.fcm.nanochat.models.runtime.LocalRuntimeMetrics(
                        modelId = resolvedModelId,
                        initDurationMs = currentRuntimeHandle.initDurationMs,
                        timeToFirstTokenMs =
                            if (firstVisibleTokenAt == 0L) {
                                finishedAt - generationStart
                            } else {
                                firstVisibleTokenAt - generationStart
                            },
                        generationDurationMs = generationDurationMs,
                        tokensPerSecond = tokensPerSecond,
                        backend = "litert-lm"
                    )
                )

                Log.d(
                    TAG,
                    "local_generation_completed modelId=$resolvedModelId completionReason=$completionReason rawCallbacks=$rawCallbackCount nonVisibleChunks=$nonVisibleChunkCount visibleChunks=$visibleChunkCount visibleChars=$emittedVisibleChars durationMs=$generationDurationMs"
                )
            } catch (cancellation: CancellationException) {
                currentRuntimeHandle.runtime.cancelActiveGeneration("cancelled")
                if (cancellation is NoTokenWatchdogTimeout) {
                    Log.w(
                        TAG,
                        "local_generation_no_visible modelId=$resolvedModelId waitedMs=$noTokenWatchdogMs state=${
                            visibilityState(
                                firstRawCallbackAt,
                                firstVisibleTokenAt
                            ).name
                        } rawCallbacks=$rawCallbackCount"
                    )
                    throw InferenceException.BackendUnavailable(
                        cancellation.message ?: NO_TOKEN_WATCHDOG_MESSAGE
                    )
                }
                if (cancellation is VisibleOutputStalledTimeout) {
                    completionReason = "visible_stall_watchdog"
                    val finalizedVisibleOutput = GeneratedTextSanitizer.sanitize(rawOutput)
                    if (finalizedVisibleOutput.isNotBlank()) {
                        if (!emissionUnlocked) {
                            emissionUnlocked = true
                        }
                        emitStableDelta(
                            currentSnapshot = finalizedVisibleOutput,
                            chunkIndex = rawCallbackCount
                        )
                        Log.w(
                            TAG,
                            "local_generation_stalled_after_visible modelId=$resolvedModelId rawCallbacks=$rawCallbackCount nonVisibleChunks=$nonVisibleChunkCount visibleChunks=$visibleChunkCount visibleChars=$emittedVisibleChars"
                        )
                        return
                    }
                    throw InferenceException.BackendUnavailable(
                        cancellation.message ?: VISIBLE_STALL_WATCHDOG_MESSAGE
                    )
                }
                Log.d(
                    TAG,
                    "local_generation_cancelled modelId=$resolvedModelId reason=${cancellation.message.orEmpty()} rawCallbacks=$rawCallbackCount visibleChars=$emittedVisibleChars"
                )
                throw cancellation
            } catch (backendUnavailable: InferenceException.BackendUnavailable) {
                Log.w(
                    TAG,
                    "local_generation_failed modelId=$resolvedModelId completionReason=$completionReason message=${backendUnavailable.message.orEmpty()}"
                )
                crashReporter.recordNonFatal(
                    backendUnavailable,
                    "local_generation_failed modelId=$resolvedModelId reason=$completionReason"
                )
                throw backendUnavailable
            } catch (degenerate: DegenerateOutputException) {
                throw degenerate
            } catch (error: Throwable) {
                completionReason = "error"
                currentRuntimeHandle.runtime.cancelActiveGeneration("error")
                Log.e(TAG, "Downloaded runtime generation failed modelId=$resolvedModelId", error)
                if (imageAttachment != null && isLikelyUnsupportedMultimodalError(error, "image")) {
                    throw InferenceException.UnsupportedModality(
                        "Selected local model/runtime could not process image input."
                    )
                }
                crashReporter.recordNonFatal(
                    error,
                    "local_generation_error modelId=$resolvedModelId"
                )
                throw InferenceException.BackendUnavailable(toFriendlyRuntimeError(error))
            }
        }

        val attemptConfigs = generationAttemptConfigs(config = baseRuntimeConfig)

        for ((attemptIndex, runtimeConfig) in attemptConfigs.withIndex()) {
            try {
                emitGenerationAttempt(runtimeConfig, isRetry = attemptIndex > 0)
                return@flow
            } catch (_: DegenerateOutputException) {
                val shouldRetry =
                    shouldRetryOnCpuFallback(
                        attemptIndex = attemptIndex,
                        attemptCount = attemptConfigs.size
                    )
                if (!shouldRetry) {
                    throw unstableOutputException(request.settings.acceleratorPreference)
                }

                val nextConfig = attemptConfigs[attemptIndex + 1]
                Log.w(
                    TAG,
                    "local_generation_retrying modelId=$resolvedModelId from=${runtimeConfig.accelerators} to=${nextConfig.accelerators} reason=degenerate_output"
                )
                try {
                    runtimeManager.release()
                } catch (_: Throwable) {
                    // Ignore release failures and continue with the CPU retry.
                }
            }
        }

        throw unstableOutputException(request.settings.acceleratorPreference)
    }

    override suspend fun transcribeAudio(
        request: AudioTranscriptionRequest
    ): AudioTranscriptionResult {
        if (!supportsRuntimeMultimodalContent()) {
            throw InferenceException.UnsupportedModality(
                "Current local runtime build does not expose audio content support."
            )
        }

        when (val availability = availability(request.settings)) {
            BackendAvailability.Available -> Unit
            is BackendAvailability.Unavailable -> {
                throw InferenceException.BackendUnavailable(availability.message)
            }
        }

        val activeModelId =
            resolveActiveModelId(request.settings, request.settings.activeLocalModelId)
                ?: throw InferenceException.Configuration("No local model selected.")
        val record =
            resolveActiveModelRecord(activeModelId)
                ?: throw InferenceException.BackendUnavailable(
                    "Selected local model is unavailable. Choose another model."
                )
        val model = record.allowlistedModel
        if (model?.llmSupportAudio != true) {
            throw InferenceException.UnsupportedModality(
                "Selected local model does not support audio understanding."
            )
        }
        val allowlistedModel = requireNotNull(model)

        val localPath = record.localPath?.trim().orEmpty()
        if (localPath.isBlank()) {
            throw InferenceException.MissingFile(
                "Selected local model file is missing. Re-download the model."
            )
        }

        val audioFile = mediaStore.resolveFile(request.audioAttachment.relativePath)
            ?: throw InferenceException.MissingFile("Selected audio file is missing.")

        val runtimeConfig =
            applyAcceleratorPreference(
                config = allowlistedModel.defaultConfig,
                preference = request.settings.acceleratorPreference
            )

        val handle = runCatching {
            runtimeManager.acquire(
                modelId = record.modelId,
                modelPath = localPath,
                defaultConfig = runtimeConfig,
                expectedFileName = allowlistedModel.modelFile,
                expectedFileType = allowlistedModel.fileType,
                expectedSizeBytes = allowlistedModel.sizeInBytes
            )
        }.getOrElse { error ->
            throw InferenceException.BackendUnavailable(toFriendlyRuntimeError(error))
        }

        val transcript = StringBuilder()
        val prompt = buildAudioTranscriptionPrompt(request.audioAttachment.displayName)

        return withContext(Dispatchers.IO) {
            runCatching {
                handle.runtime.stream(
                    sessionId = null,
                    prompt = prompt,
                    systemInstruction = DOWNLOADED_AUDIO_TRANSCRIPTION_SYSTEM_PROMPT,
                    attachments = listOf(LocalRuntimeAttachment.AudioFile(audioFile.absolutePath))
                ).collect { chunk ->
                    transcript.append(chunk)
                }

                val transcriptText = GeneratedTextSanitizer
                    .sanitize(raw = transcript.toString(), preserveThinkingBlocks = false)
                    .trim()
                if (transcriptText.isBlank()) {
                    throw InferenceException.TranscriptionFailure(
                        "Local model returned an empty transcript."
                    )
                }
                AudioTranscriptionResult(transcript = transcriptText)
            }.getOrElse { error ->
                when (error) {
                    is CancellationException -> throw error
                    is InferenceException -> throw error
                    else -> {
                        if (isLikelyUnsupportedMultimodalError(error, "audio")) {
                            throw InferenceException.UnsupportedModality(
                                "Selected local model/runtime could not process audio input."
                            )
                        }
                        throw InferenceException.TranscriptionFailure(
                            "Local audio transcription failed.",
                            error
                        )
                    }
                }
            }
        }
    }

    override fun release() {
        scope.launch {
            Log.d(TAG, "Releasing downloaded runtime")
            runtimeManager.release()
        }
    }

    private fun resolveActiveModelId(
        settings: SettingsSnapshot,
        requestedActiveModelId: String?
    ): String? {
        val preferredId = normalizeModelId(requestedActiveModelId)
        val settingsId = normalizeModelId(settings.activeLocalModelId)
        val candidateId = preferredId.ifBlank { settingsId }
        return candidateId.ifBlank { null }
    }

    private fun resolveActiveModelRecord(activeModelId: String): InstalledModelRecord? {
        val normalized = activeModelId.trim()
        return modelRegistry.records.value.firstOrNull {
            it.modelId.equals(normalized, ignoreCase = true)
        }
    }

    private fun mergeRuntimeChunk(existing: String, incoming: String): String {
        if (incoming.isBlank()) return existing
        if (existing.isBlank()) return incoming

        return if (incoming.length > existing.length && incoming.startsWith(existing)) {
            incoming
        } else {
            existing + incoming
        }
    }

    private fun incrementalVisibleDelta(previous: String, current: String): String {
        if (current.isBlank() || current == previous || previous.startsWith(current)) return ""
        if (previous.isBlank()) return current

        return if (current.startsWith(previous)) {
            current.removePrefix(previous)
        } else {
            current
        }
    }

    private fun shouldLogChunk(index: Int): Boolean {
        return index <= 5 || index % 32 == 0
    }

    private fun chunkPreview(raw: String): String {
        return raw.replace("\r\n", "\n").replace("\n", "\\n").take(200)
    }

    private fun shouldUnlockVisibleEmission(output: String): Boolean {
        if (output.isBlank()) return false

        var insideTag = false
        output.forEach { char ->
            when {
                char == '<' -> insideTag = true
                char == '>' -> insideTag = false
                !insideTag && char.isLetterOrDigit() -> return true
            }
        }
        return false
    }

    private fun supportsRuntimeMultimodalContent(): Boolean {
        return runCatching {
            Class.forName("com.google.ai.edge.litertlm.Content\$ImageFile")
            Class.forName("com.google.ai.edge.litertlm.Content\$AudioFile")
        }.isSuccess
    }

    private fun isDownloadedImageInputEnabled(): Boolean {
        // Temporary kill-switch until LiteRT-LM image turns are stable on production devices.
        return System.getProperty(DEBUG_ENABLE_LOCAL_IMAGE_INPUT_PROPERTY)
            ?.toBooleanStrictOrNull() ?: true
    }

    private fun downloadedImageInputDisabledReason(): String {
        return "Local image input is temporarily unavailable in Downloaded mode. Use Remote mode for image understanding."
    }

    private fun isLikelyUnsupportedMultimodalError(error: Throwable, modality: String): Boolean {
        val combined = buildString {
            append(error.message.orEmpty())
            append(' ')
            append(error.cause?.message.orEmpty())
        }.lowercase()

        if (combined.isBlank()) return false
        if ("unsupported" !in combined && "not support" !in combined) return false

        return modality in combined || "multimodal" in combined || "content" in combined
    }

    private fun buildAudioTranscriptionPrompt(displayName: String): String {
        val label = displayName.trim().ifBlank { "the attached audio file" }
        return "Transcribe $label exactly as spoken. Return only the transcript text."
    }

    private fun normalizeModelId(value: String?): String {
        return value?.trim()?.lowercase().orEmpty()
    }

    private fun configuredNoTokenWatchdogMs(isRetry: Boolean): Long {
        val overrideMs = System.getProperty(DEBUG_NO_TOKEN_WATCHDOG_PROPERTY)?.toLongOrNull()
        if (overrideMs != null) return overrideMs.coerceAtLeast(1_000L)

        return if (isRetry) {
            LOCAL_RETRY_NO_TOKEN_WATCHDOG_MS
        } else {
            LOCAL_NO_TOKEN_WATCHDOG_MS
        }
    }

    private fun configuredVisibleStallWatchdogMs(): Long {
        val overrideMs = System.getProperty(DEBUG_VISIBLE_STALL_WATCHDOG_PROPERTY)?.toLongOrNull()
        return overrideMs?.coerceAtLeast(2_000L) ?: LOCAL_VISIBLE_STALL_WATCHDOG_MS
    }

    private fun logFormattedPrompt(
        modelId: String,
        family: DownloadedPromptFamily,
        systemInstruction: String,
        userMessage: String,
        isReusing: Boolean
    ) {
        if (!isPromptLoggingEnabled()) return

        val systemPreview =
            systemInstruction
                .replace("\r\n", "\n")
                .replace("\n", "\\n")
                .take(PROMPT_LOG_PREVIEW_LIMIT)
        val userPreview =
            userMessage
                .replace("\r\n", "\n")
                .replace("\n", "\\n")
                .take(PROMPT_LOG_PREVIEW_LIMIT)
        Log.d(
            TAG,
            "local_prompt modelId=$modelId family=${family.name} systemChars=${systemInstruction.length} userChars=${userMessage.length} isReusing=$isReusing system='$systemPreview' user='$userPreview'"
        )
    }

    private fun isPromptLoggingEnabled(): Boolean {
        return System.getProperty(DEBUG_PROMPT_LOGGING_PROPERTY)?.toBooleanStrictOrNull() == true
    }

    private fun SettingsSnapshot.toFallbackDownloadedConfig():
            AllowlistDefaultConfig {
        return AllowlistDefaultConfig(
            topK = 40,
            topP = topP,
            temperature = temperature,
            maxTokens = contextLength,
            accelerators = "cpu",
            strictAccelerator = false,
            promptFamily = null
        )
    }

    private fun applyAcceleratorPreference(
        config: AllowlistDefaultConfig,
        preference: AcceleratorPreference
    ): AllowlistDefaultConfig {
        return when (preference) {
            AcceleratorPreference.AUTO -> config.copy(strictAccelerator = false)
            AcceleratorPreference.CPU ->
                config.copy(accelerators = "cpu", strictAccelerator = true)

            AcceleratorPreference.GPU ->
                config.copy(accelerators = "gpu", strictAccelerator = true)

            AcceleratorPreference.NNAPI ->
                config.copy(accelerators = "npu", strictAccelerator = true)
        }
    }

    private fun generationAttemptConfigs(
        config: AllowlistDefaultConfig
    ): List<AllowlistDefaultConfig> {
        val firstAccelerator = config.acceleratorHints.firstOrNull()?.lowercase()
        val cpuConfig = config.copy(accelerators = "cpu", strictAccelerator = true)

        return if (firstAccelerator == "cpu" && config.acceleratorHints.size == 1) {
            listOf(config)
        } else {
            listOf(config, cpuConfig).distinctBy { "${it.accelerators}|${it.strictAccelerator}" }
        }
    }

    private fun shouldRetryOnCpuFallback(attemptIndex: Int, attemptCount: Int): Boolean {
        return attemptIndex + 1 < attemptCount
    }

    private fun unstableOutputException(
        preference: AcceleratorPreference
    ): InferenceException.BackendUnavailable {
        val message =
            when (preference) {
                AcceleratorPreference.CPU -> {
                    "Local model produced unstable repetitive output on CPU. Retry, or switch to a different model."
                }

                else -> {
                    "Local model produced unstable repetitive output on all available accelerators. Retry, or switch to a different model."
                }
            }
        return InferenceException.BackendUnavailable(message)
    }

    private fun toFriendlyRuntimeError(error: Throwable): String {
        val message = error.message?.trim().orEmpty()
        val cause = error.cause?.message?.trim().orEmpty()
        val combined = "$message $cause".lowercase()

        if (combined.isBlank()) {
            return "This model could not start on this device."
        }

        return when {
            "session already exists" in combined -> {
                "LiteRT-LM is busy cleaning up a previous chat. Wait a moment and retry."
            }

            "missing runtime option method" in combined ||
                    "settopk" in combined ||
                    "setmaxtokens" in combined -> {
                "This downloaded file may be incompatible with the current runtime."
            }

            "flatbuffer" in combined ||
                    "error building tflite model" in combined ||
                    "invocationtargetexception" in combined -> {
                "Installed, but NanoChat could not start this model."
            }

            "missing" in combined && "file" in combined -> {
                "This install appears incomplete. Try re-downloading."
            }

            "outofmemory" in combined || "out of memory" in combined -> {
                "This model needs more memory on this device."
            }

            "context" in combined && "limit" in combined ||
                    "context" in combined && "exceed" in combined ||
                    "maximum context" in combined ||
                    "too many token" in combined ||
                    "input token" in combined && "limit" in combined ||
                    "prompt token" in combined && "limit" in combined -> {
                "This conversation is too long for the local model. Start a new chat or shorten your message."
            }

            "permission" in combined -> {
                "NanoChat cannot access the local model file. Move or re-download the model."
            }

            else -> "This model could not start on this device."
        }
    }

    private fun toFriendlyRuntimeError(raw: String?): String {
        val message = raw?.trim().orEmpty()
        if (message.isBlank()) {
            return "This model could not start on this device."
        }

        val lowercase = message.lowercase()
        return when {
            "session already exists" in lowercase -> {
                "LiteRT-LM is busy cleaning up a previous chat. Wait a moment and retry."
            }
            "missing runtime option method" in lowercase ||
                    "settopk" in lowercase ||
                    "setmaxtokens" in lowercase -> {
                "This downloaded file may be incompatible with the current runtime."
            }
            "flatbuffer" in lowercase ||
                    "error building tflite model" in lowercase ||
                    "invocationtargetexception" in lowercase -> {
                "Installed, but NanoChat could not start this model."
            }
            "missing" in lowercase && "file" in lowercase -> {
                "This install appears incomplete. Try re-downloading."
            }
            "outofmemory" in lowercase || "out of memory" in lowercase -> {
                "This model needs more memory on this device."
            }
            "context" in lowercase && "limit" in lowercase ||
                    "context" in lowercase && "exceed" in lowercase ||
                    "maximum context" in lowercase ||
                    "too many token" in lowercase ||
                    "input token" in lowercase && "limit" in lowercase ||
                    "prompt token" in lowercase && "limit" in lowercase -> {
                "This conversation is too long for the local model. Start a new chat or shorten your message."
            }
            "permission" in lowercase -> {
                "NanoChat cannot access the local model file. Move or re-download the model."
            }
            else -> "This model could not start on this device."
        }
    }

    private companion object {
        const val TAG = "DownloadedInference"
        const val LOCAL_NO_TOKEN_WATCHDOG_MS = 45_000L
        const val LOCAL_RETRY_NO_TOKEN_WATCHDOG_MS = 75_000L
        const val LOCAL_VISIBLE_STALL_WATCHDOG_MS = 10_000L
        const val NO_TOKEN_WATCHDOG_MESSAGE =
            "Local generation started but produced no visible output. Retry, or reselect this model."
        const val VISIBLE_STALL_WATCHDOG_MESSAGE =
            "Local generation stalled after starting to respond. Retry or reselect this model."
        const val DEBUG_NO_TOKEN_WATCHDOG_PROPERTY = "nanochat.debug.local_no_token_watchdog_ms"
        const val DEBUG_VISIBLE_STALL_WATCHDOG_PROPERTY =
            "nanochat.debug.local_visible_stall_watchdog_ms"
        const val DEBUG_PROMPT_LOGGING_PROPERTY = "nanochat.debug.log_local_prompts"
        const val DEBUG_ENABLE_LOCAL_IMAGE_INPUT_PROPERTY =
            "nanochat.debug.enable_local_image_input"
        const val PROMPT_LOG_PREVIEW_LIMIT = 1_800
        const val DOWNLOADED_AUDIO_TRANSCRIPTION_SYSTEM_PROMPT =
            "You are a precise transcription assistant. Transcribe the audio faithfully in plain text without summaries or extra commentary."
    }
}

internal enum class LocalGenerationVisibilityState {
    NO_CALLBACK,
    CALLBACK_NO_VISIBLE,
    VISIBLE_STARTED
}

internal fun visibilityState(
    firstRawCallbackAtMs: Long,
    firstVisibleTokenAtMs: Long
): LocalGenerationVisibilityState {
    return when {
        firstVisibleTokenAtMs > 0L -> LocalGenerationVisibilityState.VISIBLE_STARTED
        firstRawCallbackAtMs > 0L -> LocalGenerationVisibilityState.CALLBACK_NO_VISIBLE
        else -> LocalGenerationVisibilityState.NO_CALLBACK
    }
}

internal fun shouldTriggerNoVisibleWatchdog(
    firstVisibleTokenAtMs: Long,
    startedAtMs: Long,
    nowMs: Long,
    thresholdMs: Long
): Boolean {
    if (firstVisibleTokenAtMs > 0L) return false
    if (thresholdMs <= 0L) return false
    return nowMs - startedAtMs >= thresholdMs
}

internal fun shouldTriggerVisibleStallWatchdog(
    firstVisibleTokenAtMs: Long,
    lastVisibleProgressAtMs: Long,
    nowMs: Long,
    thresholdMs: Long
): Boolean {
    if (firstVisibleTokenAtMs <= 0L) return false
    if (thresholdMs <= 0L) return false
    return nowMs - lastVisibleProgressAtMs >= thresholdMs
}

private class NoTokenWatchdogTimeout(message: String) : CancellationException(message)

private class VisibleOutputStalledTimeout(message: String) : CancellationException(message)

private class DegenerateOutputException : RuntimeException("Degenerate local output")
