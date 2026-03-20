package com.fcm.nanochat.models.runtime

import kotlinx.coroutines.flow.Flow

interface LocalModelRuntime {
    fun stream(sessionId: Long?, prompt: String, systemInstruction: String? = null): Flow<String>
    fun getActiveSessionId(): Long? = null
    fun cancelActiveGeneration(reason: String)
    suspend fun close()
}
