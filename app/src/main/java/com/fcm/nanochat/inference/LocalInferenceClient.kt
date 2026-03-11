package com.fcm.nanochat.inference

import android.content.Context
import com.fcm.nanochat.data.SettingsSnapshot
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class LocalInferenceClient(
    private val context: Context
) : InferenceClient {
    override suspend fun availability(settings: SettingsSnapshot): BackendAvailability {
        if (!hasAiCorePackage()) {
            return BackendAvailability.Unavailable(
                "AICore is not available on this device. Install or enable Gemini Nano in Developer Options."
            )
        }

        val status = runCatching { generativeModel.checkStatus() }.getOrElse { error ->
            return BackendAvailability.Unavailable(availabilityErrorMessage(error))
        }

        return when (status) {
            FeatureStatus.AVAILABLE -> BackendAvailability.Available
            FeatureStatus.DOWNLOADABLE -> BackendAvailability.Available
            FeatureStatus.DOWNLOADING -> BackendAvailability.Unavailable(
                "Gemini Nano is downloading in AICore. Keep device online and retry once download completes."
            )
            FeatureStatus.UNAVAILABLE -> BackendAvailability.Unavailable(
                "Gemini Nano is unavailable right now. Ensure AICore has finished setup and the bootloader is locked."
            )
            else -> BackendAvailability.Unavailable(
                "Unable to determine Gemini Nano availability (status=$status)."
            )
        }
    }

    override fun streamChat(request: InferenceRequest): Flow<String> = flow {
        when (val availability = availability(request.settings)) {
            is BackendAvailability.Unavailable -> throw InferenceException.BackendUnavailable(availability.message)
            BackendAvailability.Available -> Unit
        }

        val prompt = PromptFormatter.flattenForAicore(request.history, request.prompt, maxTurns = 10)

        runCatching { generativeModel.warmup() }

        val response = runCatching {
            generativeModel.generateContent(prompt)
        }.getOrElse { error ->
            throw mapThrowableToInferenceException(error)
        }

        val text = response.candidates
            .asSequence()
            .mapNotNull { candidate -> candidate.text?.trim()?.takeIf(String::isNotEmpty) }
            .firstOrNull()
            ?: throw InferenceException.BackendUnavailable(
                "Gemini Nano returned an empty response. Try a more specific prompt."
            )

        emit(text)
    }

    private val generativeModel: GenerativeModel by lazy {
        Generation.getClient()
    }

    @Suppress("DEPRECATION")
    private fun hasAiCorePackage(): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(AICORE_PACKAGE_NAME, 0)
        }.isSuccess
    }

    private fun availabilityErrorMessage(error: Throwable): String {
        return when (val mapped = mapThrowableToInferenceException(error)) {
            is InferenceException.Busy,
            is InferenceException.BackendUnavailable,
            is InferenceException.Configuration,
            is InferenceException.RemoteFailure -> mapped.message.orEmpty()
        }
    }

    private fun mapThrowableToInferenceException(error: Throwable): InferenceException {
        if (error is InferenceException) {
            return error
        }

        if (error is GenAiException) {
            return mapGenAiException(error)
        }

        val message = error.message.orEmpty()
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
            else -> {
                InferenceException.BackendUnavailable(
                    "Gemini Nano failed: ${error.message ?: error.javaClass.simpleName}"
                )
            }
        }
    }

    private fun mapGenAiException(error: GenAiException): InferenceException {
        val fallbackMessage = error.message ?: "Unknown AICore error."

        return when (error.errorCode) {
            GenAiException.ErrorCode.BUSY,
            GenAiException.ErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED -> {
                InferenceException.Busy("AICore is busy or quota-limited. Wait a moment and retry.")
            }
            GenAiException.ErrorCode.BACKGROUND_USE_BLOCKED -> {
                InferenceException.BackendUnavailable(
                    "AICore inference requires the app in foreground. Keep NanoChat open and retry."
                )
            }
            GenAiException.ErrorCode.NOT_AVAILABLE -> {
                InferenceException.BackendUnavailable(
                    "Gemini Nano is not available yet. Keep device online and retry in a few minutes."
                )
            }
            GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE,
            GenAiException.ErrorCode.AICORE_INCOMPATIBLE -> {
                InferenceException.BackendUnavailable(
                    "AICore requires a system update. Update Android system components and retry."
                )
            }
            GenAiException.ErrorCode.NOT_ENOUGH_DISK_SPACE -> {
                InferenceException.BackendUnavailable(
                    "AICore needs more storage. Free disk space and retry."
                )
            }
            GenAiException.ErrorCode.REQUEST_TOO_LARGE -> {
                InferenceException.Configuration(
                    "Prompt is too long for on-device inference. Try a shorter prompt."
                )
            }
            else -> {
                when {
                    fallbackMessage.contains("BINDING_FAILURE", ignoreCase = true) -> {
                        InferenceException.BackendUnavailable(
                            "AICore service failed to bind. Update AICore, then reinstall NanoChat."
                        )
                    }
                    fallbackMessage.contains("FEATURE_NOT_FOUND", ignoreCase = true) -> {
                        InferenceException.BackendUnavailable(
                            "AICore setup is still initializing. Keep network on, restart device, and retry shortly."
                        )
                    }
                    fallbackMessage.contains("Unable to resolve host", ignoreCase = true) -> {
                        InferenceException.BackendUnavailable(
                            "AICore setup needs network access. Connect to internet and retry."
                        )
                    }
                    else -> {
                        InferenceException.BackendUnavailable("Gemini Nano failed: $fallbackMessage")
                    }
                }
            }
        }
    }

    private companion object {
        const val AICORE_PACKAGE_NAME = "com.google.android.aicore"
    }
}
