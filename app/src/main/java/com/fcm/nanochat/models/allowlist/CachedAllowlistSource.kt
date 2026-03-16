package com.fcm.nanochat.models.allowlist

import com.fcm.nanochat.data.AppPreferences
import kotlinx.coroutines.flow.first

internal class CachedAllowlistSource(
    private val preferences: AppPreferences
) {
    suspend fun loadOrNull(): AllowlistPayload? {
        val cache = preferences.allowlistCache.first()
        if (cache.version.isBlank() || cache.json.isBlank()) {
            return null
        }

        return runCatching {
            AllowlistParser.parse(
                rawJson = cache.json,
                fallbackVersion = cache.version,
                sourceType = AllowlistSourceType.CACHED,
                refreshedAtEpochMs = cache.refreshedAtEpochMs
            )
        }.getOrNull()
    }
}
