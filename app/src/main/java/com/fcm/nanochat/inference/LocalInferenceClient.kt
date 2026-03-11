package com.fcm.nanochat.inference

import android.content.Context
import com.fcm.nanochat.data.SettingsSnapshot
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

        val client = try {
            generationClient
        } catch (error: Throwable) {
            return BackendAvailability.Unavailable(availabilityErrorMessage(error))
        }

        val status = try {
            invokeNoArg(client, "checkStatus")
        } catch (error: Throwable) {
            return BackendAvailability.Unavailable(availabilityErrorMessage(error))
        }

        return when (enumName(status)) {
            "AVAILABLE", "DOWNLOADABLE" -> BackendAvailability.Available
            "DOWNLOADING" -> BackendAvailability.Unavailable(
                "Gemini Nano is downloading in AICore. Keep device online and retry once download completes."
            )

            "UNAVAILABLE" -> BackendAvailability.Unavailable(
                "Gemini Nano is unavailable right now. Ensure AICore has finished setup and the bootloader is locked."
            )
            else -> BackendAvailability.Unavailable(
                "Unable to determine Gemini Nano availability (status=${enumName(status)})."
            )
        }
    }

    override fun streamChat(request: InferenceRequest): Flow<String> = flow {
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

        runCatching { invokeNoArg(client, "warmup") }

        val response = runCatching {
            invokeSingleArg(client, "generateContent", prompt)
        }.getOrElse { error ->
            throw mapThrowableToInferenceException(error)
        }

        val text = extractText(response)
            ?: throw InferenceException.BackendUnavailable(
                "Gemini Nano returned an empty response. Try a more specific prompt."
            )

        emit(text)
    }

    private val generationClient: Any by lazy {
        val generationClass = Class.forName("com.google.mlkit.genai.prompt.Generation")
        val getClientMethod = generationClass.methods.firstOrNull {
            it.name == "getClient" && it.parameterCount == 0
        } ?: throw NoSuchMethodException("Generation.getClient() is unavailable.")

        getClientMethod.invoke(null)
            ?: throw IllegalStateException("Generation client is null.")
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

        if (isGenAiException(error)) {
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
        return error.javaClass.name == "com.google.mlkit.genai.common.GenAiException"
    }

    private companion object {
        const val AICORE_PACKAGE_NAME = "com.google.android.aicore"
    }
}
