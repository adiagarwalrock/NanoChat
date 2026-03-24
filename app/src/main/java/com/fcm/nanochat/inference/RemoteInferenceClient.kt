package com.fcm.nanochat.inference

import com.fcm.nanochat.data.SettingsSnapshot
import com.fcm.nanochat.data.ThinkingEffort
import com.fcm.nanochat.data.media.ChatMediaStore
import com.fcm.nanochat.model.ChatRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class RemoteInferenceClient(
    private val httpClient: OkHttpClient,
    private val mediaStore: ChatMediaStore
) : InferenceClient {
    override suspend fun availability(settings: SettingsSnapshot): BackendAvailability {
        val firstMissingField = RemoteConfigValidator.missingFields(settings).firstOrNull()
        return if (firstMissingField == null) {
            BackendAvailability.Available
        } else {
            BackendAvailability.Unavailable(configurationMessage(firstMissingField))
        }
    }

    override suspend fun capabilities(settings: SettingsSnapshot): InferenceCapabilities {
        return InferenceCapabilities(
            textGeneration = SupportedState.supported(),
            visionUnderstanding = SupportedState.supported(),
            audioTranscription = SupportedState.supported(),
            streaming = SupportedState.supported()
        )
    }

    override fun streamChat(request: InferenceRequest): Flow<String> = callbackFlow {
        when (val availability = availability(request.settings)) {
            is BackendAvailability.Unavailable -> {
                close(InferenceException.Configuration(availability.message))
                return@callbackFlow
            }

            BackendAvailability.Available -> Unit
        }

        val outputChannel = channel

        val apiUrl = RemoteApiUrlResolver.chatCompletionsUrl(request.settings.baseUrl)
        val body = runCatching {
            buildRequestBody(request).toString()
                .toRequestBody("application/json".toMediaType())
        }.getOrElse { error ->
            close(
                if (error is InferenceException) {
                    error
                } else {
                    InferenceException.UploadFailure("Failed to prepare media request.", error)
                }
            )
            return@callbackFlow
        }

        val apiKey = request.settings.apiKey.trim()

        val httpRequest = Request.Builder()
            .url(apiUrl)
            .header("Authorization", "Bearer $apiKey")
            .header("x-goog-api-key", apiKey)
            .header("Accept", "text/event-stream")
            .post(body)
            .build()

        val call = httpClient.newCall(httpRequest)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (call.isCanceled()) {
                    close()
                    return
                }
                close(InferenceException.RemoteFailure("Remote request failed.", e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        close(
                            InferenceException.RemoteFailure(
                                "Remote API returned HTTP ${response.code}."
                            )
                        )
                        return
                    }

                    val source = response.body.source()
                    streamEvents(source)
                }
            }

            fun streamEvents(source: BufferedSource) {
                try {
                    val eventDataLines = mutableListOf<String>()

                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break

                        if (line.isEmpty()) {
                            val shouldContinue = consumeEvent(eventDataLines)
                            eventDataLines.clear()
                            if (!shouldContinue) {
                                break
                            }
                            continue
                        }

                        if (line.startsWith(":")) {
                            continue
                        }

                        if (line.startsWith("data:")) {
                            eventDataLines += line.removePrefix("data:").trimStart()
                        }
                    }

                    if (eventDataLines.isNotEmpty()) {
                        consumeEvent(eventDataLines)
                    }

                    close()
                } catch (e: Exception) {
                    close(InferenceException.RemoteFailure("Failed to parse remote stream.", e))
                }
            }

            fun consumeEvent(eventDataLines: List<String>): Boolean {
                if (eventDataLines.isEmpty()) return true

                val payload = eventDataLines.joinToString(separator = "\n").trim()
                if (payload.isEmpty()) return true
                if (payload == "[DONE]") return false

                val delta = parseDelta(payload)
                if (delta.isEmpty()) return true

                val sendResult = outputChannel.trySend(delta)
                if (sendResult.isSuccess || call.isCanceled()) {
                    return true
                }

                close(InferenceException.RemoteFailure("Remote stream closed before delivering all content."))
                return false
            }
        })

        awaitClose { call.cancel() }
    }

    override suspend fun transcribeAudio(
        request: AudioTranscriptionRequest
    ): AudioTranscriptionResult {
        when (val availability = availability(request.settings)) {
            is BackendAvailability.Unavailable -> {
                throw InferenceException.Configuration(availability.message)
            }

            BackendAvailability.Available -> Unit
        }

        val attachment = request.audioAttachment
        val audioFile = mediaStore.resolveFile(attachment.relativePath)
            ?: throw InferenceException.MissingFile("Selected audio file is missing.")

        val transcriptionModel = request.settings.transcriptionModelName
            .trim()
            .ifBlank { request.settings.modelName.trim() }
            .ifBlank {
                throw InferenceException.Configuration(
                    "Set a transcription model in Settings, or configure a remote model name."
                )
            }

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", transcriptionModel)
            .addFormDataPart(
                "file",
                attachment.displayName,
                audioFile.readBytes().toRequestBody(attachment.mimeType.toMediaType())
            )
            .build()

        val apiKey = request.settings.apiKey.trim()
        val apiUrl = RemoteApiUrlResolver.audioTranscriptionsUrl(request.settings.baseUrl)
        val httpRequest = Request.Builder()
            .url(apiUrl)
            .header("Authorization", "Bearer $apiKey")
            .header("x-goog-api-key", apiKey)
            .post(body)
            .build()

        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw InferenceException.UploadFailure(
                            "Audio transcription failed with HTTP ${response.code}."
                        )
                    }
                    val responseBody = response.body.string()
                    val root = JSONObject(responseBody)
                    val transcript = root.optString("text")
                        .ifBlank { root.optString("transcript") }
                        .trim()
                    if (transcript.isBlank()) {
                        throw InferenceException.TranscriptionFailure(
                            "Remote transcription returned no text."
                        )
                    }
                    AudioTranscriptionResult(transcript = transcript)
                }
            }.getOrElse { error ->
                when (error) {
                    is InferenceException -> throw error
                    is IOException -> throw InferenceException.UploadFailure(
                        "Audio upload failed.",
                        error
                    )

                    else -> throw InferenceException.TranscriptionFailure(
                        "Failed to parse transcription response.",
                        error
                    )
                }
            }
        }
    }

    private fun buildRequestBody(request: InferenceRequest): JSONObject {
        val messages = JSONArray()

        val supportsThinking = supportsReasoningParam(request.settings.modelName)
        val systemContent = PromptFormatter.applyThinkingInstruction(
            systemPrompt = "You are NanoChat, a helpful AI assistant. Reply in clean Markdown.",
            effort = request.settings.thinkingEffort,
            supportsThinking = supportsThinking
        )

        messages.put(
            JSONObject()
                .put("role", "system")
                .put("content", systemContent)
        )

        request.history.forEach { turn ->
            messages.put(
                JSONObject()
                    .put("role", if (turn.role == ChatRole.USER) "user" else "assistant")
                    .put("content", turn.content)
            )
        }

        val userContent = if (request.imageAttachment == null) {
            request.prompt
        } else {
            val imageDataUrl = buildImageDataUrl(request.imageAttachment)
            JSONArray()
                .put(
                    JSONObject()
                        .put("type", "text")
                        .put(
                            "text",
                            request.prompt.ifBlank { "Describe the attached image." }
                        )
                )
                .put(
                    JSONObject()
                        .put("type", "image_url")
                        .put(
                            "image_url",
                            JSONObject().put("url", imageDataUrl)
                        )
                )
        }

        messages.put(
            JSONObject()
                .put("role", "user")
                .put("content", userContent)
        )

        return JSONObject()
            .put("model", request.settings.modelName)
            .put("stream", true)
            .put("messages", messages)
            .apply {
                val effort = request.settings.thinkingEffort
                if (effort != ThinkingEffort.NONE && supportsReasoningParam(request.settings.modelName)) {
                    put("reasoning_effort", when (effort) {
                        ThinkingEffort.LOW -> "low"
                        ThinkingEffort.MEDIUM -> "medium"
                        ThinkingEffort.HIGH -> "high"
                        ThinkingEffort.NONE -> null
                    })
                }
                if (request.settings.temperature > 0) {
                    put("temperature", request.settings.temperature)
                }
                if (request.settings.topP > 0) {
                    put("top_p", request.settings.topP)
                }
                if (request.settings.contextLength > 0) {
                    put("max_tokens", request.settings.contextLength)
                }
            }
    }

    private fun parseDelta(payload: String): String {
        val root = JSONObject(payload)
        val choices = root.optJSONArray("choices") ?: return ""
        if (choices.length() == 0) return ""

        val delta = choices.optJSONObject(0)?.optJSONObject("delta") ?: return ""

        val reasoningText = extractText(delta.opt("reasoning_content"))
        val visibleText = extractText(delta.opt("content"))

        return buildString {
            if (reasoningText.isNotBlank()) {
                append("<think>")
                append(reasoningText)
                append("</think>")
            }
            append(visibleText)
        }
    }

    private fun extractText(jsonValue: Any?): String {
        return when (jsonValue) {
            is String -> jsonValue
            is JSONArray -> buildString {
                for (index in 0 until jsonValue.length()) {
                    val item = jsonValue.opt(index)
                    if (item is JSONObject) {
                        val text = item.optString("text")
                        if (text.isNotBlank()) {
                            append(text)
                        }
                    }
                }
            }

            else -> ""
        }
    }

    private fun supportsReasoningParam(modelName: String): Boolean {
        val normalized = modelName.trim().lowercase()
        if (normalized.isBlank()) return false
        return listOf("o1", "o3", "deepseek", "reason", "think").any { normalized.contains(it) }
    }

    private fun buildImageDataUrl(attachment: InferenceImageAttachment): String {
        return runCatching {
            mediaStore.toDataUrl(
                relativePath = attachment.relativePath,
                mimeType = attachment.mimeType
            )
        }.getOrElse { error ->
            throw InferenceException.MissingFile(
                error.message ?: "Attached image is missing."
            )
        }
    }

    private fun configurationMessage(field: RemoteConfigField): String {
        return when (field) {
            RemoteConfigField.BASE_URL -> "Set a base URL in Settings."
            RemoteConfigField.MODEL_NAME -> "Set a model name in Settings."
            RemoteConfigField.API_KEY -> "Add an API key in Settings."
        }
    }
}
