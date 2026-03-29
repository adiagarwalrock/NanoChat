package com.fcm.nanochat.util

import com.fcm.nanochat.inference.InferenceMode

/**
 * Snapshot of an in-flight inference request, persisted so that if the process
 * dies mid-generation we can report the context on next cold start.
 */
data class InferenceCrashMarker(
    val mode: InferenceMode,
    val modelId: String?,
    val sessionId: Long,
    val requestId: Long,
    val stage: String,
    val visibleChars: Int
)
