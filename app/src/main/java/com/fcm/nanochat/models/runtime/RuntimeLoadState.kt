package com.fcm.nanochat.models.runtime

enum class RuntimeLoadPhase {
    IDLE,
    LOADING,
    LOADED,
    FAILED,
    EJECTED
}

enum class RuntimeReleaseReason {
    GENERIC,
    EJECTED
}

data class RuntimeLoadState(
    val phase: RuntimeLoadPhase = RuntimeLoadPhase.IDLE,
    val modelId: String? = null,
    val message: String? = null,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)
