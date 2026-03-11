package com.fcm.nanochat.inference

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class LocalInferenceClient(
    private val context: Context
) : InferenceClient {
    override suspend fun availability(settings: com.fcm.nanochat.data.SettingsSnapshot): BackendAvailability {
        @Suppress("DEPRECATION")
        val hasAiCorePackage = runCatching {
            context.packageManager.getPackageInfo(AICORE_PACKAGE_NAME, 0)
        }.isSuccess

        if (!hasAiCorePackage) {
            return BackendAvailability.Unavailable(
                "AICore is not available on this device. Install or enable Gemini Nano in Developer Options."
            )
        }

        return if (bridge.isReady()) {
            BackendAvailability.Available
        } else {
            BackendAvailability.Unavailable(
                "AICore is installed, but the text generation bridge is unavailable in this build. Verify Gemini Nano is enabled."
            )
        }
    }

    override fun streamChat(request: InferenceRequest): Flow<String> = flow {
        when (val availability = availability(request.settings)) {
            is BackendAvailability.Unavailable -> throw InferenceException.BackendUnavailable(availability.message)
            BackendAvailability.Available -> Unit
        }

        val prompt = PromptFormatter.flattenForAicore(request.history, request.prompt, maxTurns = 10)
        val response = bridge.generate(prompt)
        emit(response)
    }

    private val bridge = AiCoreReflectionBridge(context)

    private companion object {
        const val AICORE_PACKAGE_NAME = "com.google.android.aicore"
    }
}

private class AiCoreReflectionBridge(
    private val context: Context
) {
    // The experimental AICore SDK surface is not stable yet, so keep the app decoupled
    // from hard references until the local environment can validate the exact API types.
    fun isReady(): Boolean {
        return runCatching {
            Class.forName("com.google.ai.edge.aicore.AiCoreClient")
        }.isSuccess
    }

    fun generate(prompt: String): String {
        val clientClass = runCatching { Class.forName("com.google.ai.edge.aicore.AiCoreClient") }
            .getOrNull()
        if (clientClass == null) {
            throw InferenceException.BackendUnavailable(
                "AICore classes are unavailable. Re-sync Gradle and verify the experimental SDK is present."
            )
        }

        return runCatching {
            val constructor = clientClass.constructors.firstOrNull()
                ?: error("Missing AICore client constructor.")
            val client = constructor.newInstance(context)
            val method = clientClass.methods.firstOrNull { candidate ->
                candidate.name.equals("generateText", ignoreCase = true) &&
                    candidate.parameterTypes.size == 1 &&
                    candidate.parameterTypes[0] == String::class.java
            } ?: error("Missing generateText(String) bridge.")
            method.invoke(client, prompt) as? String
        }.getOrElse { error ->
            throw InferenceException.BackendUnavailable(
                "AICore text generation is not callable with the current SDK surface: ${error.message}"
            )
        } ?: throw InferenceException.BackendUnavailable("AICore returned an empty response.")
    }
}
