package com.fcm.nanochat.data.repository

import com.fcm.nanochat.inference.InferenceMode

internal object ChatDefaults {
    const val defaultSessionTitle = "New chat"
    private const val maxSessionTitleLength = 32

    fun normalizedSessionTitle(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return defaultSessionTitle
        return if (trimmed.length <= maxSessionTitleLength) {
            trimmed
        } else {
            trimmed.take(maxSessionTitleLength - 3) + "..."
        }
    }

    fun historyWindowFor(mode: InferenceMode): Int {
        return when (mode) {
            InferenceMode.AICORE -> 10
            InferenceMode.REMOTE -> 20
        }
    }
}