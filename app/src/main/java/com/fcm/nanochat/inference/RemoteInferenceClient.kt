package com.fcm.nanochat.inference

import com.fcm.nanochat.data.SettingsSnapshot
import com.fcm.nanochat.model.ChatRole
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class RemoteInferenceClient(
    private val httpClient: OkHttpClient
) : InferenceClient {
    override suspend fun availability(settings: SettingsSnapshot): BackendAvailability {
        val firstMissingField = RemoteConfigValidator.missingFields(settings).firstOrNull()
        return if (firstMissingField == null) {
            BackendAvailability.Available
        } else {
            BackendAvailability.Unavailable(configurationMessage(firstMissingField))
        }
    }

    override fun streamChat(request: InferenceRequest): Flow<String> = callbackFlow {
        when (val availability = availability(request.settings)) {
            is BackendAvailability.Unavailable -> {
                close(InferenceException.Configuration(availability.message))
                return@callbackFlow
            }

            BackendAvailability.Available -> Unit
        }

        val apiUrl = RemoteApiUrlResolver.chatCompletionsUrl(request.settings.baseUrl)
        val body = buildRequestBody(request).toString()
            .toRequestBody("application/json".toMediaType())

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

                    val source = response.body?.source()
                    if (source == null) {
                        close(InferenceException.RemoteFailure("Remote API returned an empty response body."))
                        return
                    }

                    streamEvents(source)
                }
            }

            fun streamEvents(source: BufferedSource) {
                try {
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue

                        val payload = line.removePrefix("data:").trim()
                        if (payload.isEmpty()) continue
                        if (payload == "[DONE]") break

                        val delta = parseDelta(payload)
                        if (delta.isNotEmpty()) {
                            trySend(delta)
                        }
                    }
                    close()
                } catch (e: Exception) {
                    close(InferenceException.RemoteFailure("Failed to parse remote stream.", e))
                }
            }
        })

        awaitClose { call.cancel() }
    }

    private fun buildRequestBody(request: InferenceRequest): JSONObject {
        val messages = JSONArray()

        request.history.forEach { turn ->
            messages.put(
                JSONObject()
                    .put("role", if (turn.role == ChatRole.USER) "user" else "assistant")
                    .put("content", turn.content)
            )
        }

        messages.put(
            JSONObject()
                .put("role", "user")
                .put("content", request.prompt)
        )

        return JSONObject()
            .put("model", request.settings.modelName)
            .put("stream", true)
            .put("messages", messages)
            .apply {
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
        val content = delta.opt("content") ?: return ""

        return when (content) {
            is String -> content
            is JSONArray -> buildString {
                for (index in 0 until content.length()) {
                    val item = content.opt(index)
                    if (item is JSONObject) {
                        append(item.optString("text"))
                    }
                }
            }

            else -> ""
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
