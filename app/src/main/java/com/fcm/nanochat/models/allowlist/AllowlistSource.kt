package com.fcm.nanochat.models.allowlist

internal interface AllowlistSource {
    suspend fun load(): AllowlistPayload
}
