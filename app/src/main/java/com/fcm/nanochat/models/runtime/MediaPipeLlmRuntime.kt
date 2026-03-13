package com.fcm.nanochat.models.runtime

import android.content.Context
import com.fcm.nanochat.models.allowlist.AllowlistDefaultConfig
import java.io.File
import java.lang.reflect.Method

internal class MediaPipeLlmRuntime(
    private val runtime: Any
) : LocalModelRuntime {
    override fun generate(prompt: String): String {
        val method = runtime.javaClass.methods.firstOrNull { candidate ->
            (candidate.name == "generateResponse" || candidate.name == "generate") &&
                    candidate.parameterCount == 1 &&
                    candidate.parameterTypes[0] == String::class.java
        } ?: error("Unable to locate runtime generate method.")

        val result = method.invoke(runtime, prompt)
        return (result as? CharSequence)?.toString().orEmpty()
    }

    override fun close() {
        runCatching {
            runtime.javaClass.methods.firstOrNull {
                it.name == "close" && it.parameterCount == 0
            }?.invoke(runtime)
        }
    }
}

internal object MediaPipeLlmRuntimeFactory {
    private const val LLM_CLASS = "com.google.mediapipe.tasks.genai.llminference.LlmInference"
    private const val OPTIONS_CLASS =
        "com.google.mediapipe.tasks.genai.llminference.LlmInference\$LlmInferenceOptions"

    fun create(
        context: Context,
        modelPath: String,
        config: AllowlistDefaultConfig
    ): LocalModelRuntime {
        val trimmedPath = modelPath.trim()
        validateModelPath(trimmedPath)

        val llmClass = Class.forName(LLM_CLASS)
        val optionsClass = Class.forName(OPTIONS_CLASS)
        val backends = backendOrder(config)
        val errors = mutableListOf<String>()

        for (backend in backends) {
            val runtime = runCatching {
                createRuntime(
                    context = context,
                    llmClass = llmClass,
                    optionsClass = optionsClass,
                    modelPath = trimmedPath,
                    config = config,
                    backendLabel = backend
                )
            }.getOrElse { error ->
                errors += "${backend.ifBlank { "auto" }}: ${error.message ?: error.javaClass.simpleName}"
                null
            }

            if (runtime != null) {
                return runtime
            }
        }

        error(
            errors.joinToString(separator = " | ").ifBlank {
                "Unable to initialize local runtime."
            }
        )
    }

    fun probe(context: Context, modelPath: String, config: AllowlistDefaultConfig): String? {
        return runCatching {
            val runtime = create(context, modelPath, config)
            runtime.close()
        }.exceptionOrNull()?.let { error ->
            toProbeMessage(error)
        }
    }

    fun isRuntimeClassAvailable(): Boolean {
        return runCatching {
            Class.forName(LLM_CLASS)
        }.isSuccess
    }

    private fun createRuntime(
        context: Context,
        llmClass: Class<*>,
        optionsClass: Class<*>,
        modelPath: String,
        config: AllowlistDefaultConfig,
        backendLabel: String
    ): LocalModelRuntime {
        val builder = optionsClass.getMethod("builder").invoke(null)

        invokeRequired(
            builder = builder,
            methodNames = listOf("setModelPath"),
            value = modelPath,
            missingMessage = "Runtime options do not support modelPath."
        )

        invokeOptional(
            builder = builder,
            methodNames = listOf("setMaxTokens", "setMaxNumTokens", "setMaxOutputTokens"),
            value = config.maxTokens.coerceAtLeast(256)
        )
        invokeOptional(
            builder = builder,
            methodNames = listOf("setTopK", "setMaxTopK"),
            value = config.topK.coerceAtLeast(1)
        )
        invokeOptional(
            builder = builder,
            methodNames = listOf("setTopP"),
            value = config.topP.toFloat().coerceIn(0f, 1f)
        )
        invokeOptional(
            builder = builder,
            methodNames = listOf("setTemperature"),
            value = config.temperature.toFloat().coerceAtLeast(0f)
        )

        applyBackendPreference(builder, llmClass, backendLabel)

        val options = builder.javaClass.getMethod("build").invoke(builder)
        val runtime = llmClass
            .getMethod("createFromOptions", Context::class.java, optionsClass)
            .invoke(null, context, options)
            ?: error("Failed to create local runtime.")

        return MediaPipeLlmRuntime(runtime)
    }

    private fun applyBackendPreference(builder: Any, llmClass: Class<*>, backendLabel: String) {
        if (backendLabel.isBlank() || backendLabel == "auto") {
            return
        }

        val backendValue = resolveBackendValue(llmClass, backendLabel) ?: return
        invokeOptional(
            builder = builder,
            methodNames = listOf("setPreferredBackend", "setBackend"),
            value = backendValue
        )
    }

    private fun resolveBackendValue(llmClass: Class<*>, backendLabel: String): Any? {
        val backendClass = llmClass.declaredClasses.firstOrNull { declared ->
            declared.simpleName.equals("Backend", ignoreCase = true)
        } ?: return null

        if (backendClass.isEnum) {
            return backendClass.enumConstants?.firstOrNull {
                val value = it as? Enum<*>
                value?.name?.equals(backendLabel, ignoreCase = true) == true
            }
        }

        backendClass.methods.firstOrNull { method ->
            method.name.equals(backendLabel, ignoreCase = true) &&
                    method.parameterCount == 0
        }?.let { factory ->
            return runCatching { factory.invoke(null) }.getOrNull()
        }

        backendClass.fields.firstOrNull { field ->
            field.name.equals(backendLabel, ignoreCase = true)
        }?.let { field ->
            return runCatching { field.get(null) }.getOrNull()
        }

        return null
    }

    private fun invokeRequired(
        builder: Any,
        methodNames: List<String>,
        value: Any,
        missingMessage: String
    ) {
        if (!invokeOptional(builder, methodNames, value)) {
            error(missingMessage)
        }
    }

    private fun invokeOptional(builder: Any, methodNames: List<String>, value: Any): Boolean {
        for (methodName in methodNames) {
            val methodWithValue = findCompatibleMethod(builder, methodName, value) ?: continue
            methodWithValue.first.invoke(builder, methodWithValue.second)
            return true
        }
        return false
    }

    private fun findCompatibleMethod(
        builder: Any,
        methodName: String,
        value: Any
    ): Pair<Method, Any>? {
        val candidates = builder.javaClass.methods.filter {
            it.name == methodName && it.parameterCount == 1
        }
        for (candidate in candidates) {
            val parameterType = candidate.parameterTypes.firstOrNull() ?: continue
            val adapted = adaptValue(value, parameterType) ?: continue
            return candidate to adapted
        }
        return null
    }

    private fun adaptValue(value: Any, parameterType: Class<*>): Any? {
        if (parameterType.isAssignableFrom(value.javaClass)) {
            return value
        }

        return when (parameterType) {
            java.lang.Integer.TYPE,
            java.lang.Integer::class.java -> (value as? Number)?.toInt()

            java.lang.Float.TYPE,
            java.lang.Float::class.java -> (value as? Number)?.toFloat()

            java.lang.Double.TYPE,
            java.lang.Double::class.java -> (value as? Number)?.toDouble()

            java.lang.Long.TYPE,
            java.lang.Long::class.java -> (value as? Number)?.toLong()

            java.lang.Boolean.TYPE,
            java.lang.Boolean::class.java -> value as? Boolean

            else -> null
        }
    }

    private fun backendOrder(config: AllowlistDefaultConfig): List<String> {
        val explicit = config.acceleratorHints
            .map { it.lowercase() }
            .filter { it == "cpu" || it == "gpu" || it == "npu" }
            .distinct()
            .toMutableList()

        if (explicit.isEmpty()) {
            explicit += listOf("gpu", "cpu")
        }

        if ("gpu" in explicit && "cpu" !in explicit) {
            explicit += "cpu"
        }

        if ("npu" in explicit && "cpu" !in explicit) {
            explicit += "cpu"
        }

        explicit += "auto"
        return explicit.distinct()
    }

    private fun validateModelPath(modelPath: String) {
        if (modelPath.isBlank()) {
            error("Model path is empty.")
        }

        val file = File(modelPath)
        if (!file.exists() || !file.isFile) {
            error("Model file is missing.")
        }
        if (file.length() <= 0L) {
            error("Model file is empty.")
        }

        val extension = file.extension.lowercase()
        if (extension != "litertlm" && extension != "task") {
            error("Model file type is not supported.")
        }
    }

    private fun toProbeMessage(error: Throwable): String {
        val raw = error.message.orEmpty().trim()
        if (raw.isBlank()) {
            return "NanoChat could not start this model on your device."
        }

        val lowercase = raw.lowercase()
        return when {
            "missing" in lowercase && "model" in lowercase && "path" in lowercase -> {
                "NanoChat could not locate the local model file."
            }

            "empty" in lowercase && "model" in lowercase -> {
                "This model file appears incomplete."
            }

            "unsupported" in lowercase && "file" in lowercase -> {
                "This model file format is not supported by the current runtime."
            }

            "permission" in lowercase -> {
                "NanoChat does not have access to this model file."
            }

            else -> raw
        }
    }
}
