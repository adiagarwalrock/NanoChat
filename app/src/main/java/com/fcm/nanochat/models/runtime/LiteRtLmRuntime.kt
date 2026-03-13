package com.fcm.nanochat.models.runtime

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.fcm.nanochat.models.allowlist.AllowlistDefaultConfig
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import java.io.FileInputStream
import java.util.Locale

private data class StartupFileInspection(
    val path: String,
    val exists: Boolean,
    val sizeBytes: Long,
    val extension: String,
    val detectedFormat: String,
    val magicBytesHex: String
)

internal class LiteRtLmRuntime(
    private val engine: Engine,
    private val samplerConfig: SamplerConfig?
) : LocalModelRuntime {
    override fun generate(prompt: String): String {
        val request = prompt.trim()
        if (request.isBlank()) return ""

        return engine.createConversation(
            ConversationConfig(samplerConfig = samplerConfig)
        ).use { conversation ->
            conversation.sendMessage(request).toString().trim()
        }
    }

    override fun close() {
        runCatching { engine.close() }
    }

}

internal object LiteRtLmRuntimeFactory {
    fun create(
        context: Context,
        modelId: String,
        modelPath: String,
        config: AllowlistDefaultConfig,
        expectedFileName: String?,
        expectedFileType: String?,
        expectedSizeBytes: Long
    ): LocalModelRuntime {
        val trimmedPath = modelPath.trim()
        val debugEnabled = isDebugBuild(context)
        val inspection = inspectModelFile(trimmedPath, debugEnabled)
        logStartupInspection(
            modelId = modelId,
            inspection = inspection,
            expectedFileName = expectedFileName,
            expectedFileType = expectedFileType,
            expectedSizeBytes = expectedSizeBytes
        )
        validateModelFile(
            inspection = inspection,
            expectedFileName = expectedFileName,
            expectedFileType = expectedFileType,
            expectedSizeBytes = expectedSizeBytes
        )

        val backends = backendOrder(config)
        val errors = mutableListOf<String>()

        for (backend in backends) {
            val runtime = runCatching {
                createRuntime(
                    context = context,
                    modelPath = trimmedPath,
                    config = config,
                    backendLabel = backend
                )
            }.getOrElse { error ->
                errors += "${backend.ifBlank { "auto" }}: ${rootCauseSummary(error)}"
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

    fun probe(
        context: Context,
        modelId: String,
        modelPath: String,
        config: AllowlistDefaultConfig,
        expectedFileName: String?,
        expectedFileType: String?,
        expectedSizeBytes: Long
    ): String? {
        if (!isRuntimeClassAvailable()) {
            return "Local runtime is unavailable on this build."
        }

        val debugEnabled = isDebugBuild(context)
        val inspection = inspectModelFile(modelPath.trim(), debugEnabled)
        return runCatching {
            val runtime = create(
                context = context,
                modelId = modelId,
                modelPath = modelPath,
                config = config,
                expectedFileName = expectedFileName,
                expectedFileType = expectedFileType,
                expectedSizeBytes = expectedSizeBytes
            )
            runtime.close()
        }.exceptionOrNull()?.let { error ->
            probeFailureMessage(inspection, error)
        }
    }

    fun isRuntimeClassAvailable(): Boolean {
        return runCatching {
            Class.forName("com.google.ai.edge.litertlm.Engine")
        }.isSuccess
    }

    @OptIn(ExperimentalApi::class)
    private fun createRuntime(
        context: Context,
        modelPath: String,
        config: AllowlistDefaultConfig,
        backendLabel: String
    ): LocalModelRuntime {
        val backend = backendFromLabel(backendLabel)
        if (backendLabel == "npu") {
            ExperimentalFlags.npuLibrariesDir = context.applicationInfo.nativeLibraryDir
        }

        val engineConfig = EngineConfig(
            modelPath = modelPath,
            backend = backend,
            maxNumTokens = config.maxTokens.coerceAtLeast(256),
            cacheDir = if (modelPath.startsWith(TMP_MODEL_PATH_PREFIX)) {
                context.getExternalFilesDir(null)?.absolutePath
            } else {
                null
            }
        )

        val engine = Engine(engineConfig)
        engine.initialize()

        val samplerConfig = if (backendLabel == "npu") {
            null
        } else {
            SamplerConfig(
                topK = config.topK.coerceAtLeast(1),
                topP = config.topP.coerceIn(0.0, 1.0),
                temperature = config.temperature.coerceAtLeast(0.0)
            )
        }
        return LiteRtLmRuntime(engine = engine, samplerConfig = samplerConfig)
    }

    private fun backendFromLabel(label: String): Backend {
        return when (label) {
            "gpu" -> Backend.GPU()
            "npu" -> Backend.NPU()
            "auto" -> Backend.CPU()
            else -> Backend.CPU()
        }
    }

    private fun backendOrder(config: AllowlistDefaultConfig): List<String> {
        val explicit = config.acceleratorHints
            .map { it.lowercase(Locale.US) }
            .filter { it == "cpu" || it == "gpu" || it == "npu" }
            .distinct()
            .toMutableList()

        if (explicit.isEmpty()) {
            explicit += listOf("gpu", "cpu")
        }

        if ("cpu" !in explicit) {
            explicit += "cpu"
        }
        explicit += "auto"

        return explicit.distinct()
    }

    private fun inspectModelFile(modelPath: String, debugEnabled: Boolean): StartupFileInspection {
        val file = File(modelPath)
        val exists = file.exists() && file.isFile
        val sizeBytes = if (exists) file.length() else 0L
        val extension = file.extension.lowercase(Locale.US)
        val magicBytes = if (exists) readMagicBytes(file) else ByteArray(0)
        return StartupFileInspection(
            path = file.absolutePath,
            exists = exists,
            sizeBytes = sizeBytes,
            extension = extension,
            detectedFormat = detectFormat(extension, magicBytes),
            magicBytesHex = if (debugEnabled) {
                magicBytes.joinToString(" ") { byte ->
                    "%02X".format(byte.toInt() and 0xFF)
                }
            } else {
                ""
            }
        )
    }

    private fun validateModelFile(
        inspection: StartupFileInspection,
        expectedFileName: String?,
        expectedFileType: String?,
        expectedSizeBytes: Long
    ) {
        if (!inspection.exists) {
            error("Model file is missing.")
        }

        if (inspection.sizeBytes <= 0L) {
            error("Model file is empty.")
        }

        val file = File(inspection.path)
        if (file.name.endsWith(".part", ignoreCase = true)) {
            error("Model file points to an incomplete partial download.")
        }

        val normalizedExpectedName = expectedFileName?.trim().orEmpty()
        if (normalizedExpectedName.isNotBlank() && file.name != normalizedExpectedName) {
            error(
                "Model file name mismatch. Expected $normalizedExpectedName, got ${file.name}."
            )
        }

        val normalizedExpectedType = expectedFileType?.trim()?.lowercase(Locale.US).orEmpty()
        if (normalizedExpectedType.isNotBlank() && inspection.extension != normalizedExpectedType) {
            error(
                "Model file extension mismatch. Expected $normalizedExpectedType, got ${inspection.extension}."
            )
        }

        if (expectedSizeBytes > 0L && inspection.sizeBytes != expectedSizeBytes) {
            error(
                "Model file size mismatch. Expected $expectedSizeBytes bytes, got ${inspection.sizeBytes}."
            )
        }
    }

    private fun logStartupInspection(
        modelId: String,
        inspection: StartupFileInspection,
        expectedFileName: String?,
        expectedFileType: String?,
        expectedSizeBytes: Long
    ) {
        Log.d(
            TAG,
            "startup_file modelId=$modelId path=${inspection.path} exists=${inspection.exists} " +
                    "sizeBytes=${inspection.sizeBytes} extension=${inspection.extension} " +
                    "format=${inspection.detectedFormat} expectedName=${expectedFileName.orEmpty()} " +
                    "expectedType=${expectedFileType.orEmpty()} expectedSizeBytes=$expectedSizeBytes"
        )
        if (inspection.magicBytesHex.isNotBlank()) {
            Log.d(TAG, "startup_file_magic modelId=$modelId bytes=${inspection.magicBytesHex}")
        }
    }

    private fun readMagicBytes(file: File): ByteArray {
        return runCatching {
            val buffer = ByteArray(16)
            FileInputStream(file).use { input ->
                val read = input.read(buffer)
                if (read <= 0) {
                    ByteArray(0)
                } else {
                    buffer.copyOf(read)
                }
            }
        }.getOrDefault(ByteArray(0))
    }

    private fun detectFormat(extension: String, magicBytes: ByteArray): String {
        val isZip = magicBytes.size >= 4 &&
                magicBytes[0] == 0x50.toByte() &&
                magicBytes[1] == 0x4B.toByte() &&
                magicBytes[2] == 0x03.toByte() &&
                magicBytes[3] == 0x04.toByte()
        val isTflite = magicBytes.size >= 8 &&
                magicBytes[4] == 'T'.code.toByte() &&
                magicBytes[5] == 'F'.code.toByte() &&
                magicBytes[6] == 'L'.code.toByte() &&
                magicBytes[7] == '3'.code.toByte()

        return when {
            extension == "litertlm" -> "litertlm-package"
            isTflite || extension == "tflite" -> "tflite-flatbuffer"
            extension == "task" -> "mediapipe-task"
            isZip -> "zip-container"
            else -> "unknown"
        }
    }

    private fun probeFailureMessage(inspection: StartupFileInspection, error: Throwable): String {
        return "startup_validation_failed; " +
                "path=${inspection.path}; " +
                "extension=${inspection.extension.ifBlank { "unknown" }}; " +
                "format=${inspection.detectedFormat}; " +
                "sizeBytes=${inspection.sizeBytes}; " +
                "result=failed; " +
                "rootCause=${rootCauseSummary(error)}"
    }

    private fun rootCauseSummary(error: Throwable): String {
        val root = rootCause(error)
        val name = root.javaClass.simpleName
        val message = root.message?.trim().orEmpty()
        return if (message.isBlank()) name else "$name: $message"
    }

    private fun rootCause(error: Throwable): Throwable {
        var current = error
        while (current.cause != null && current.cause !== current) {
            current = current.cause!!
        }
        return current
    }

    private fun isDebugBuild(context: Context): Boolean {
        return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    private const val TMP_MODEL_PATH_PREFIX = "/data/local/tmp"
    private const val TAG = "LiteRtLmRuntime"
}
