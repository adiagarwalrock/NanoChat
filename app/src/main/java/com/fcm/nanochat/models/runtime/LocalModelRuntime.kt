package com.fcm.nanochat.models.runtime

import kotlinx.coroutines.flow.Flow

sealed interface LocalRuntimeAttachment {
    data class ImageFile(val absolutePath: String) : LocalRuntimeAttachment
    data class AudioFile(val absolutePath: String) : LocalRuntimeAttachment
}

interface LocalModelRuntime {
    fun stream(
        sessionId: Long?,
        prompt: String,
        systemInstruction: String? = null,
        attachments: List<LocalRuntimeAttachment> = emptyList()
    ): Flow<String>
    fun getActiveSessionId(): Long? = null
    fun cancelActiveGeneration(reason: String)
    suspend fun close()
}
