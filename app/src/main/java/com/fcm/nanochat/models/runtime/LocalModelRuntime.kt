package com.fcm.nanochat.models.runtime

import kotlinx.coroutines.flow.Flow

interface LocalModelRuntime {
    fun stream(prompt: String, systemInstruction: String? = null): Flow<String>
    fun cancelActiveGeneration(reason: String)
    fun close()
}
