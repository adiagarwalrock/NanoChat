package com.fcm.nanochat.models.runtime

import android.content.Context
import com.fcm.nanochat.models.allowlist.AllowlistDefaultConfig

internal class MediaPipeLlmRuntime(
    private val runtime: Any
) : LocalModelRuntime {
    override fun generate(prompt: String): String {
        val method = runtime.javaClass.getMethod("generateResponse", String::class.java)
        val result = method.invoke(runtime, prompt)
        return (result as? String).orEmpty()
    }

    override fun close() {
        runCatching {
            runtime.javaClass.getMethod("close").invoke(runtime)
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
        val llmClass = Class.forName(LLM_CLASS)
        val optionsClass = Class.forName(OPTIONS_CLASS)

        val builder = optionsClass.getMethod("builder").invoke(null)
        invokeBuilder(builder, "setModelPath", modelPath)
        invokeBuilder(builder, "setMaxTokens", config.maxTokens.coerceAtLeast(256))
        invokeBuilder(builder, "setTopK", config.topK.coerceAtLeast(1))
        invokeBuilder(builder, "setTemperature", config.temperature.toFloat().coerceAtLeast(0f))

        val options = builder.javaClass.getMethod("build").invoke(builder)
        val runtime = llmClass
            .getMethod("createFromOptions", Context::class.java, optionsClass)
            .invoke(null, context, options)
            ?: error("Failed to create local runtime.")

        return MediaPipeLlmRuntime(runtime)
    }

    fun probe(context: Context, modelPath: String, config: AllowlistDefaultConfig): String? {
        return runCatching {
            val runtime = create(context, modelPath, config)
            runtime.close()
        }.exceptionOrNull()?.message
    }

    fun isRuntimeClassAvailable(): Boolean {
        return runCatching {
            Class.forName(LLM_CLASS)
        }.isSuccess
    }

    private fun invokeBuilder(builder: Any, methodName: String, value: Any) {
        val candidates = builder.javaClass.methods.filter {
            it.name == methodName && it.parameterCount == 1
        }
        val method = candidates.firstOrNull { candidate ->
            val parameter = candidate.parameterTypes.firstOrNull() ?: return@firstOrNull false
            isAssignable(parameter, value)
        } ?: candidates.firstOrNull()
        ?: error("Missing runtime option method: $methodName")

        method.invoke(builder, value)
    }

    private fun isAssignable(parameterType: Class<*>, value: Any): Boolean {
        if (parameterType.isAssignableFrom(value.javaClass)) return true
        return when (parameterType) {
            java.lang.Integer.TYPE, java.lang.Integer::class.java -> value is Int
            java.lang.Float.TYPE, java.lang.Float::class.java -> value is Float
            java.lang.Double.TYPE, java.lang.Double::class.java -> value is Double
            java.lang.Long.TYPE, java.lang.Long::class.java -> value is Long
            java.lang.Boolean.TYPE, java.lang.Boolean::class.java -> value is Boolean
            else -> false
        }
    }
}
