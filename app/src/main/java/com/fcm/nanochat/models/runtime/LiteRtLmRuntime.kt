package com.fcm.nanochat.models.runtime

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.fcm.nanochat.models.allowlist.AllowlistDefaultConfig
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.runBlocking

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
    private val generationCounter = AtomicLong(0L)
    private val activeConversation = AtomicReference<ActiveConversation?>(null)

    override fun stream(prompt: String, systemInstruction: String?): Flow<String> = callbackFlow {
        val request = prompt.trim()
        if (request.isBlank()) {
            channel.close()
            return@callbackFlow
        }

        cancelAndDrainActiveConversation(reason = "superseded")

        val generationId = generationCounter.incrementAndGet()
        val gate = StreamLifecycleGate()
        val normalizedSystemInstruction =
                systemInstruction?.trim()?.takeIf { it.isNotBlank() }?.let {
                    Contents.of(listOf(Content.Text(it)))
                }

        val conversation =
                engine.createConversation(
                        ConversationConfig(
                                samplerConfig = samplerConfig,
                                systemInstruction = normalizedSystemInstruction
                        )
                )
        val active =
                ActiveConversation(
                        generationId = generationId,
                        conversation = conversation,
                        gate = gate
                )
        activeConversation.set(active)
        Log.d(TAG, "Starting local generation generationId=$generationId")

        val contents = Contents.of(listOf(Content.Text(request)))

        val callback =
                object : MessageCallback {
                    override fun onMessage(message: Message) {
                        if (!isCurrentGeneration(generationId, conversation)) {
                            return
                        }
                        val chunk = message.toString()
                        if (chunk != "null") {
                            trySend(chunk)
                        }
                    }

                    override fun onDone() {
                        if (!finishGeneration(generationId, conversation, reason = "done")) {
                            return
                        }
                        channel.close()
                    }

                    override fun onError(throwable: Throwable) {
                        if (!finishGeneration(generationId, conversation, reason = "error")) {
                            return
                        }
                        channel.close(throwable)
                    }
                }

        runCatching { conversation.sendMessageAsync(contents, callback) }.onFailure { error ->
            if (!finishGeneration(generationId, conversation, reason = "send_message_failure")) {
                return@onFailure
            }
            channel.close(error)
        }

        awaitClose { cancelActiveGeneration("await_close") }
    }

    override fun cancelActiveGeneration(reason: String) {
        val active = activeConversation.get() ?: return
        if (!active.gate.tryCancel()) return

        Log.d(
                TAG,
                "Cancelling active local generation generationId=${active.generationId} reason=$reason"
        )
        runCatching { active.conversation.cancelProcess() }
    }

    override suspend fun close() {
        cancelAndDrainActiveConversation(reason = "close")
        runCatching { engine.close() }
    }

    private companion object {
        const val TAG = "LiteRtLmRuntime"
        const val SUPERSEDE_DRAIN_POLL_MS = 50L
        const val SUPERSEDE_DRAIN_ATTEMPTS = 20
    }

    private data class ActiveConversation(
            val generationId: Long,
            val conversation: Conversation,
            val gate: StreamLifecycleGate
    )

    private fun isCurrentGeneration(generationId: Long, conversation: Conversation): Boolean {
        val active = activeConversation.get() ?: return false
        return active.generationId == generationId && active.conversation === conversation
    }

    private fun finishGeneration(
            generationId: Long,
            conversation: Conversation,
            reason: String
    ): Boolean {
        val active = activeConversation.get() ?: return false
        if (active.generationId != generationId) return false
        if (active.conversation !== conversation) return false
        if (!active.gate.tryFinalize()) return false

        activeConversation.compareAndSet(active, null)
        Log.d(TAG, "Finalizing local generation generationId=$generationId reason=$reason")
        runCatching { conversation.close() }
        return true
    }

    private suspend fun cancelAndDrainActiveConversation(reason: String) {
        if (activeConversation.get() == null) {
            return
        }
        cancelActiveGeneration(reason)

        repeat(SUPERSEDE_DRAIN_ATTEMPTS) {
            if (activeConversation.get() == null) {
                return
            }
            delay(SUPERSEDE_DRAIN_POLL_MS)
        }

        val stale = activeConversation.getAndSet(null) ?: return
        if (!stale.gate.tryFinalize()) {
            return
        }

        Log.w(
                TAG,
                "Forcing local generation finalization generationId=${stale.generationId} reason=$reason"
        )
        runCatching { stale.conversation.cancelProcess() }
        runCatching { stale.conversation.close() }
    }
}

internal class StreamLifecycleGate {
    private val cancelled = AtomicBoolean(false)
    private val finalized = AtomicBoolean(false)

    fun tryCancel(): Boolean = cancelled.compareAndSet(false, true)

    fun tryFinalize(): Boolean = finalized.compareAndSet(false, true)
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
            val runtime =
                    runCatching {
                        createRuntime(
                                context = context,
                                modelPath = trimmedPath,
                                config = config,
                                backendLabel = backend
                        )
                    }
                            .getOrElse { error ->
                                errors +=
                                        "${backend.ifBlank { "auto" }}: ${rootCauseSummary(error)}"
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
            val runtime =
                    create(
                            context = context,
                            modelId = modelId,
                            modelPath = modelPath,
                            config = config,
                            expectedFileName = expectedFileName,
                            expectedFileType = expectedFileType,
                            expectedSizeBytes = expectedSizeBytes
                    )
            kotlinx.coroutines.runBlocking { runtime.close() }
        }
                .exceptionOrNull()
                ?.let { error -> probeFailureMessage(inspection, error) }
    }

    fun isRuntimeClassAvailable(): Boolean {
        return runCatching { Class.forName("com.google.ai.edge.litertlm.Engine") }.isSuccess
    }

    @OptIn(ExperimentalApi::class)
    private fun createRuntime(
            context: Context,
            modelPath: String,
            config: AllowlistDefaultConfig,
            backendLabel: String
    ): LocalModelRuntime {
        val backend = backendFromLabel(backendLabel, context.applicationInfo.nativeLibraryDir)

        val engineConfig =
                EngineConfig(
                        modelPath = modelPath,
                        backend = backend,
                        maxNumTokens = config.maxTokens.coerceAtLeast(256),
                        cacheDir =
                                if (modelPath.startsWith(TMP_MODEL_PATH_PREFIX)) {
                                    context.getExternalFilesDir(null)?.absolutePath
                                } else {
                                    null
                                }
                )

        val engine = Engine(engineConfig)
        engine.initialize()

        val samplerConfig =
                if (backendLabel == "npu") {
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

    @OptIn(ExperimentalApi::class)
    private fun backendFromLabel(label: String, nativeLibraryDir: String): Backend {
        return when (label) {
            "gpu" -> Backend.GPU()
            "npu" -> Backend.NPU(nativeLibraryDir = nativeLibraryDir)
            else -> Backend.CPU()
        }
    }

    private fun backendOrder(config: AllowlistDefaultConfig): List<String> {
        val explicit =
                config.acceleratorHints
                        .map { it.lowercase(Locale.US) }
                        .filter { it in listOf("cpu", "gpu", "npu") }
                        .distinct()
                        .toMutableList()

        if (explicit.isEmpty()) explicit.addAll(listOf("gpu", "cpu"))
        if (config.strictAccelerator) return explicit.distinct()

        if ("cpu" !in explicit) explicit.add("cpu")
        explicit.add("auto")

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
                magicBytesHex =
                        if (debugEnabled) {
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
            error("Model file name mismatch. Expected $normalizedExpectedName, got ${file.name}.")
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
                }
                .getOrDefault(ByteArray(0))
    }

    private fun detectFormat(extension: String, magicBytes: ByteArray): String {
        val isZip =
                magicBytes.size >= 4 &&
                        magicBytes
                                .take(4)
                                .toByteArray()
                                .contentEquals(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
        val isTflite =
                magicBytes.size >= 8 &&
                        magicBytes
                                .copyOfRange(4, 8)
                                .contentEquals(
                                        byteArrayOf(
                                                'T'.code.toByte(),
                                                'F'.code.toByte(),
                                                'L'.code.toByte(),
                                                '3'.code.toByte()
                                        )
                                )

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
        if (root is UnsatisfiedLinkError) {
            val msg = root.message?.trim().orEmpty()
            return if (msg.contains("libLiteRtTopKOpenClSampler.so")) {
                "GPU acceleration is unavailable on this device (missing OpenCL sampler library)."
            } else {
                "Native dependency error: $msg"
            }
        }
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
