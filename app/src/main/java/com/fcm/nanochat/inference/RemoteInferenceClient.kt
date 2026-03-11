package com.fcm.nanochat.inference

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
    override suspend fun availability(settings: com.fcm.nanochat.data.SettingsSnapshot): BackendAvailability {
        return when {
            settings.baseUrl.isBlank() -> BackendAvailability.Unavailable("Set a base URL in Settings.")
            settings.modelName.isBlank() -> BackendAvailability.Unavailable("Set a model name in Settings.")
            settings.apiKey.isBlank() -> BackendAvailability.Unavailable("Add an API key in Settings.")
            else -> BackendAvailability.Available
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

        val baseUrl = request.settings.baseUrl.trim().trimEnd('/')
        val apiUrl = if (baseUrl.endsWith("/chat/completions")) {
            baseUrl
        } else {
            "$baseUrl/chat/completions"
        }

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
                        if (!line.startsWith("data: ")) continue

                        val payload = line.removePrefix("data: ").trim()
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
    }

    private fun parseDelta(payload: String): String {
        val root = JSONObject(payload)
        val choices = root.optJSONArray("choices") ?: return ""
        if (choices.length() == 0) return ""
        val delta = choices.getJSONObject(0).optJSONObject("delta") ?: return ""
        return delta.optString("content", "")
    }
}
