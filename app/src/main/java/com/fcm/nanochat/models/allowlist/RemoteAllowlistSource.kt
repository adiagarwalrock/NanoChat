package com.fcm.nanochat.models.allowlist

import okhttp3.OkHttpClient
import okhttp3.Request

internal class RemoteAllowlistSource(
    private val httpClient: OkHttpClient,
    private val url: String,
    private val fallbackVersion: String
) : AllowlistSource {
    override suspend fun load(): AllowlistPayload {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Remote allowlist fetch failed (HTTP ${response.code}).")
            }
            val json = response.body?.string().orEmpty()
            if (json.isBlank()) {
                error("Remote allowlist response was empty.")
            }
            return AllowlistParser.parse(
                rawJson = json,
                fallbackVersion = fallbackVersion,
                sourceType = AllowlistSourceType.REMOTE
            )
        }
    }
}
