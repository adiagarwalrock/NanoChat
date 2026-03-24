package com.fcm.nanochat.inference

import com.fcm.nanochat.data.SettingsSnapshot
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class LocalInferenceClient : InferenceClient {
    override suspend fun availability(settings: SettingsSnapshot): BackendAvailability {
        val status = runCatching { generationClient.checkStatus() }
            .getOrElse { return BackendAvailability.Unavailable(availabilityErrorMessage(it)) }

        return when (status) {
            FeatureStatus.AVAILABLE -> BackendAvailability.Available
            FeatureStatus.DOWNLOADABLE -> BackendAvailability.Unavailable(
                "Gemini Nano is not downloaded. Open Settings to download and enable on-device inference."
            )

            FeatureStatus.DOWNLOADING -> BackendAvailability.Unavailable(
                "Gemini Nano is downloading. Keep the device online and retry after it finishes."
            )

            FeatureStatus.UNAVAILABLE -> BackendAvailability.Unavailable(
                "Gemini Nano is unavailable on this device. Ensure AICore is enabled, updated, and the bootloader is locked."
            )
            else -> BackendAvailability.Unavailable(
                "Unable to determine Gemini Nano availability (status=$status)."
            )
        }
    }

    override suspend fun capabilities(settings: SettingsSnapshot): InferenceCapabilities {
        return InferenceCapabilities.defaultTextOnly(
            reason = "Gemini Nano in NanoChat currently supports text-only chat."
        )
    }

    override fun streamChat(request: InferenceRequest): Flow<String> = flow {
        if (request.imageAttachment != null) {
            throw InferenceException.UnsupportedModality(
                "Gemini Nano currently does not support image input in NanoChat."
            )
        }

        when (val availability = availability(request.settings)) {
            is BackendAvailability.Unavailable -> throw InferenceException.BackendUnavailable(availability.message)
            BackendAvailability.Available -> Unit
        }

        val prompt = PromptFormatter.flattenForAicore(request.history, request.prompt, maxTurns = 10)
        val client = try {
            generationClient
        } catch (error: Throwable) {
            throw mapThrowableToInferenceException(error)
        }

        runCatching { client.warmup() }

        val response = runCatching {
            client.generateContent(prompt)
        }.getOrElse { error ->
            throw mapThrowableToInferenceException(error)
        }

        val text = extractText(response)
            ?: throw InferenceException.BackendUnavailable(
                "Gemini Nano returned an empty response. Try a more specific prompt."
            )

        emit(text)
    }

    private val generationClient: GenerativeModel by lazy {
        Generation.getClient()
    }

    suspend fun geminiStatus(): GeminiNanoStatus {
        val status = runCatching { generationClient.checkStatus() }
            .getOrElse { error ->
                return GeminiNanoStatus(
                    supported = false,
                    downloaded = false,
                    downloading = false,
                    downloadable = false,
                    bytesDownloaded = null,
                    bytesToDownload = null,
                    message = availabilityErrorMessage(error)
                )
            }

        return when (status) {
            FeatureStatus.AVAILABLE -> GeminiNanoStatus(
                supported = true,
                downloaded = true,
                downloading = false,
                downloadable = false,
                bytesDownloaded = null,
                bytesToDownload = null,
                message = null
            )

            FeatureStatus.DOWNLOADABLE -> GeminiNanoStatus(
                supported = true,
                downloaded = false,
                downloading = false,
                downloadable = true,
                bytesDownloaded = null,
                bytesToDownload = null,
                message = "Gemini Nano can be downloaded on this device."
            )

            FeatureStatus.DOWNLOADING -> GeminiNanoStatus(
                supported = true,
                downloaded = false,
                downloading = true,
                downloadable = true,
                bytesDownloaded = null,
                bytesToDownload = null,
                message = "Gemini Nano is downloading. Keep the device online."
            )

            FeatureStatus.UNAVAILABLE -> GeminiNanoStatus(
                supported = false,
                downloaded = false,
                downloading = false,
                downloadable = false,
                bytesDownloaded = null,
                bytesToDownload = null,
                message = "Gemini Nano is unavailable on this device. Ensure AICore is enabled and updated."
            )

            else -> GeminiNanoStatus(
                supported = false,
                downloaded = false,
                downloading = false,
                downloadable = false,
                bytesDownloaded = null,
                bytesToDownload = null,
                message = "Unable to determine Gemini Nano status (status=$status)."
            )
        }
    }

    fun downloadModel(): Flow<GeminiNanoStatus> {
        val initial = GeminiNanoStatus(
            supported = true,
            downloaded = false,
            downloading = true,
            downloadable = true,
            bytesDownloaded = null,
            bytesToDownload = null,
            message = "Starting download"
        )

        return generationClient.download().map { status ->
            when (status) {
                is DownloadStatus.DownloadStarted -> GeminiNanoStatus(
                    supported = true,
                    downloaded = false,
                    downloading = true,
                    downloadable = true,
                    bytesDownloaded = 0L,
                    bytesToDownload = status.bytesToDownload,
                    message = "Downloading Gemini Nano"
                )

                is DownloadStatus.DownloadProgress -> GeminiNanoStatus(
                    supported = true,
                    downloaded = false,
                    downloading = true,
                    downloadable = true,
                    bytesDownloaded = status.totalBytesDownloaded,
                    bytesToDownload = null,
                    message = "Downloading Gemini Nano"
                )

                is DownloadStatus.DownloadCompleted -> GeminiNanoStatus(
                    supported = true,
                    downloaded = true,
                    downloading = false,
                    downloadable = false,
                    bytesDownloaded = null,
                    bytesToDownload = null,
                    message = null
                )

                is DownloadStatus.DownloadFailed -> {
                    val mapped = mapThrowableToInferenceException(status.e)
                    GeminiNanoStatus(
                        supported = true,
                        downloaded = false,
                        downloading = false,
                        downloadable = true,
                        bytesDownloaded = null,
                        bytesToDownload = null,
                        message = mapped.message ?: "Gemini Nano download failed."
                    )
                }

            }
        }
    }

    private fun availabilityErrorMessage(error: Throwable): String {
        return when (val mapped = mapThrowableToInferenceException(error)) {
            is InferenceException.Busy,
            is InferenceException.BackendUnavailable,
            is InferenceException.Configuration,
            is InferenceException.RemoteFailure,
            is InferenceException.UnsupportedModality,
            is InferenceException.MissingPermission,
            is InferenceException.MissingFile,
            is InferenceException.TranscriptionFailure,
            is InferenceException.UploadFailure -> mapped.message.orEmpty()
        }
    }

    private fun mapThrowableToInferenceException(error: Throwable): InferenceException {
        if (error is InferenceException) {
            return error
        }

        if (isGenAiException(error)) {
            return mapGenAiException(error)
        }

        val message = error.message.orEmpty()
        return messageMappedBackendUnavailable(message)
            ?: InferenceException.BackendUnavailable(
                "Gemini Nano failed: ${error.message ?: error.javaClass.simpleName}"
            )
    }

    private fun mapGenAiException(error: Throwable): InferenceException {
        val fallbackMessage = error.message ?: "Unknown AICore error."
        val errorCodeName = enumName(readProperty(error, "errorCode"))

        return when (errorCodeName) {
            "BUSY", "PER_APP_BATTERY_USE_QUOTA_EXCEEDED" -> {
                InferenceException.Busy("AICore is busy or quota-limited. Wait a moment and retry.")
            }

            "BACKGROUND_USE_BLOCKED" -> {
                InferenceException.BackendUnavailable(
                    "AICore inference requires the app in foreground. Keep NanoChat open and retry."
                )
            }

            "NOT_AVAILABLE" -> {
                InferenceException.BackendUnavailable(
                    "Gemini Nano is not available yet. Keep device online and retry in a few minutes."
                )
            }

            "NEEDS_SYSTEM_UPDATE", "AICORE_INCOMPATIBLE" -> {
                InferenceException.BackendUnavailable(
                    "AICore requires a system update. Update Android system components and retry."
                )
            }

            "NOT_ENOUGH_DISK_SPACE" -> {
                InferenceException.BackendUnavailable(
                    "AICore needs more storage. Free disk space and retry."
                )
            }

            "REQUEST_TOO_LARGE" -> {
                InferenceException.Configuration(
                    "Prompt is too long for on-device inference. Try a shorter prompt."
                )
            }
            else -> {
                messageMappedBackendUnavailable(fallbackMessage)
                    ?: InferenceException.BackendUnavailable("Gemini Nano failed: $fallbackMessage")
            }
        }
    }

    private fun messageMappedBackendUnavailable(
        message: String
    ): InferenceException.BackendUnavailable? {
        return when {
            message.contains("BINDING_FAILURE", ignoreCase = true) -> {
                InferenceException.BackendUnavailable(
                    "AICore service failed to bind. Update AICore, then reinstall NanoChat."
                )
            }

            message.contains("FEATURE_NOT_FOUND", ignoreCase = true) -> {
                InferenceException.BackendUnavailable(
                    "AICore setup is still initializing. Keep network on, restart device, and retry shortly."
                )
            }

            message.contains("Unable to resolve host", ignoreCase = true) -> {
                InferenceException.BackendUnavailable(
                    "AICore setup needs network access. Connect to internet and retry."
                )
            }

            else -> null
        }
    }

    private fun extractText(response: Any): String? {
        val candidates = when (val value = readProperty(response, "candidates")) {
            is Iterable<*> -> value.asSequence()
            is Array<*> -> value.asSequence()
            else -> emptySequence()
        }

        return candidates
            .mapNotNull { candidate ->
                val raw = candidate?.let { readProperty(it, "text") } as? String
                raw?.trim()?.takeIf(String::isNotEmpty)
            }
            .firstOrNull()
    }

    private fun invokeNoArg(instance: Any, methodName: String): Any? {
        val method = instance.javaClass.methods.firstOrNull {
            it.name == methodName && it.parameterCount == 0
        } ?: throw NoSuchMethodException("${instance.javaClass.name}#$methodName()")

        return method.invoke(instance)
    }

    private fun invokeSingleArg(instance: Any, methodName: String, arg: Any): Any {
        val method = instance.javaClass.methods.firstOrNull {
            it.name == methodName &&
                    it.parameterCount == 1 &&
                    it.parameterTypes[0].isAssignableFrom(arg.javaClass)
        } ?: instance.javaClass.methods.firstOrNull {
            it.name == methodName && it.parameterCount == 1
        } ?: throw NoSuchMethodException("${instance.javaClass.name}#$methodName(..)")

        return method.invoke(instance, arg)
            ?: throw IllegalStateException("${instance.javaClass.name}#$methodName(..) returned null")
    }

    private fun readProperty(instance: Any, propertyName: String): Any? {
        val getter = "get${propertyName.replaceFirstChar { it.uppercaseChar() }}"
        val method = instance.javaClass.methods.firstOrNull {
            it.name == getter && it.parameterCount == 0
        }
        if (method != null) {
            return method.invoke(instance)
        }

        return runCatching {
            instance.javaClass.getField(propertyName).get(instance)
        }.getOrNull()
    }

    private fun enumName(value: Any?): String {
        return when (value) {
            is Enum<*> -> value.name
            null -> "UNKNOWN"
            else -> value.toString()
        }
    }

    private fun isGenAiException(error: Throwable): Boolean {
        return error is GenAiException || error.javaClass.name == "com.google.mlkit.genai.common.GenAiException"
    }
}
