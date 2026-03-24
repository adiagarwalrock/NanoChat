package com.fcm.nanochat.data.repository

import android.net.Uri
import com.fcm.nanochat.data.AppPreferences
import com.fcm.nanochat.data.SettingsSnapshot
import com.fcm.nanochat.data.db.AppDatabase
import com.fcm.nanochat.data.db.ChatMessageEntity
import com.fcm.nanochat.data.db.ChatMessagePartEntity
import com.fcm.nanochat.data.db.ChatMessagePartState
import com.fcm.nanochat.data.db.ChatMessagePartType
import com.fcm.nanochat.data.db.ChatMessageWithParts
import com.fcm.nanochat.data.db.ChatSessionEntity
import com.fcm.nanochat.data.media.ChatMediaStore
import com.fcm.nanochat.inference.AudioTranscriptionRequest
import com.fcm.nanochat.inference.AudioTranscriptionResult
import com.fcm.nanochat.inference.BackendAvailability
import com.fcm.nanochat.inference.ChatTurn
import com.fcm.nanochat.inference.GeminiNanoStatus
import com.fcm.nanochat.inference.InferenceAudioAttachment
import com.fcm.nanochat.inference.InferenceCapabilities
import com.fcm.nanochat.inference.InferenceClient
import com.fcm.nanochat.inference.InferenceClientSelector
import com.fcm.nanochat.inference.InferenceException
import com.fcm.nanochat.inference.InferenceImageAttachment
import com.fcm.nanochat.inference.InferenceMode
import com.fcm.nanochat.inference.InferenceRequest
import com.fcm.nanochat.inference.LocalInferenceClient
import com.fcm.nanochat.model.ChatMessage
import com.fcm.nanochat.model.ChatRole
import com.fcm.nanochat.model.ChatSession
import com.fcm.nanochat.model.ComposerAttachment
import com.fcm.nanochat.model.ComposerAttachmentType
import com.fcm.nanochat.model.MessagePart
import com.fcm.nanochat.model.MessagePartState
import com.fcm.nanochat.model.UsageStats
import com.fcm.nanochat.models.registry.ActiveModelStatus
import com.fcm.nanochat.models.runtime.RuntimeLoadPhase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class ChatRepository(
    private val database: AppDatabase,
    private val preferences: AppPreferences,
    private val mediaStore: ChatMediaStore,
    private val localModelRepository: LocalModelRepository,
    private val localInferenceClient: LocalInferenceClient,
    private val downloadedInferenceClient: InferenceClient,
    private val remoteInferenceClient: InferenceClient
) {
    fun observeSettings(): Flow<SettingsSnapshot> = preferences.settings

    fun observePinnedSessionIds(): Flow<Set<Long>> = preferences.pinnedSessionIds

    fun observeActiveLocalModelStatus(): Flow<ActiveModelStatus> =
        combine(
            localModelRepository.activeModelStatus,
            localModelRepository.runtimeLoadState
        ) { baseStatus, runtimeState ->
            val activeModelId = baseStatus.modelId?.trim()?.lowercase().orEmpty()
            if (activeModelId.isBlank()) return@combine baseStatus

            val displayName = baseStatus.displayName?.trim().orEmpty().ifBlank { "Local model" }
            val runtimeModelId = runtimeState.modelId?.trim()?.lowercase().orEmpty()
            val sameModel = runtimeModelId == activeModelId

            when (runtimeState.phase) {
                RuntimeLoadPhase.LOADING -> {
                    baseStatus.unready(
                        if (sameModel) preparingModelMessage(displayName)
                        else notReadyYetMessage(displayName)
                    )
                }

                RuntimeLoadPhase.LOADED -> {
                    if (sameModel) {
                        baseStatus.copy(ready = true, message = "Ready for local chat")
                    } else {
                        baseStatus.unready(willPrepareOnStartMessage(displayName))
                    }
                }

                RuntimeLoadPhase.EJECTED, RuntimeLoadPhase.IDLE -> {
                    baseStatus.unready(notLoadedMessage(displayName))
                }

                RuntimeLoadPhase.FAILED -> {
                    baseStatus.unready(
                        if (runtimeModelId.isBlank() || sameModel)
                            prepareFailedMessage(displayName)
                        else notReadyYetMessage(displayName)
                    )
                }
            }
        }

    fun observeActiveLocalModelCapabilities(): Flow<LocalModelCapabilities> =
        combine(localModelRepository.records, localModelRepository.activeModelStatus) { records,
                                                                                        status ->
            val activeId = status.modelId?.trim()?.lowercase().orEmpty()
            val record = records.firstOrNull { it.modelId.equals(activeId, ignoreCase = true) }
            LocalModelCapabilities(
                modelId = activeId.ifBlank { null },
                supportsThinking = record?.allowlistedModel?.supportsThinking ?: false,
                promptFamily = record?.allowlistedModel?.promptFamily,
                supportedAccelerators =
                    record?.allowlistedModel?.defaultConfig?.acceleratorHints
                        ?: emptyList()
            )
        }

    fun observeSessions(): Flow<List<ChatSession>> =
        database.sessionDao().observeSessions().map { sessions ->
            sessions.map(ChatSessionEntity::toModel)
        }

    fun observeMessages(sessionId: Long): Flow<List<ChatMessage>> =
        database.messageDao().observeMessages(sessionId).map { messages ->
            messages.map { it.toModel(mediaStore) }
        }

    suspend fun ensureSession(): Long {
        val latest = database.sessionDao().latestSession()
        return latest?.id ?: createSession()
    }

    suspend fun createSession(title: String = ChatDefaults.defaultSessionTitle): Long {
        val now = System.currentTimeMillis()
        return database.sessionDao()
            .insert(
                ChatSessionEntity(
                    title = ChatDefaults.normalizedSessionTitle(title),
                    createdAt = now,
                    updatedAt = now
                )
            )
    }

    suspend fun renameSession(sessionId: Long, title: String) {
        val normalized = ChatDefaults.normalizedSessionTitle(title)
        database.sessionDao().updateSession(sessionId, normalized, System.currentTimeMillis())
    }

    suspend fun deleteSession(sessionId: Long) {
        val sessionParts = database.messageDao().sessionParts(sessionId)
        database.sessionDao().deleteSession(sessionId)
        preferences.setSessionPinned(sessionId, false)
        cleanupOrphanedFiles(sessionParts)
    }

    suspend fun setSessionPinned(sessionId: Long, pinned: Boolean) {
        preferences.setSessionPinned(sessionId, pinned)
    }

    suspend fun importImageAttachment(uri: Uri): ComposerAttachment {
        val imported = mediaStore.importImage(uri)
        return imported.toComposerAttachment()
    }

    suspend fun importAudioAttachment(uri: Uri): ComposerAttachment {
        val imported = mediaStore.importAudio(uri)
        return imported.toComposerAttachment()
    }

    suspend fun importCapturedImage(tempAbsolutePath: String): ComposerAttachment {
        val imported = mediaStore.importCapturedImage(tempAbsolutePath)
        return imported.toComposerAttachment()
    }

    suspend fun discardAttachment(attachment: ComposerAttachment) {
        mediaStore.deleteRelativePath(attachment.relativePath)
    }

    suspend fun saveUserMessage(
        sessionId: Long,
        content: String,
        settings: SettingsSnapshot,
        parts: List<MessagePartWrite> = emptyList()
    ): Long {
        val now = System.currentTimeMillis()
        updateSessionTitleIfNeeded(sessionId, content, now)
        val messageId = database.messageDao()
            .insert(
                ChatMessageEntity(
                    sessionId = sessionId,
                    role = ChatRole.USER,
                    content = content,
                    inferenceMode = settings.inferenceMode,
                    modelName = messageModelName(settings),
                    temperature = settings.temperature,
                    topP = settings.topP,
                    contextLength = settings.contextLength,
                    createdAt = now,
                    updatedAt = now
                )
            )
        replaceMessageParts(messageId, parts)
        return messageId
    }

    suspend fun insertAssistantPlaceholder(sessionId: Long, settings: SettingsSnapshot): Long {
        val now = System.currentTimeMillis()
        return database.messageDao()
            .insert(
                ChatMessageEntity(
                    sessionId = sessionId,
                    role = ChatRole.ASSISTANT,
                    content = "",
                    inferenceMode = settings.inferenceMode,
                    modelName = messageModelName(settings),
                    temperature = settings.temperature,
                    topP = settings.topP,
                    contextLength = settings.contextLength,
                    createdAt = now,
                    updatedAt = now
                )
            )
    }

    suspend fun updateAssistantMessage(messageId: Long, content: String) {
        database.messageDao().updateContent(messageId, content, System.currentTimeMillis())
    }

    suspend fun replaceMessageParts(
        messageId: Long,
        parts: List<MessagePartWrite>
    ) {
        val now = System.currentTimeMillis()
        database.messageDao().deleteMessageParts(messageId)
        if (parts.isEmpty()) return

        database.messageDao().insertParts(
            parts.mapIndexed { index, part ->
                ChatMessagePartEntity(
                    messageId = messageId,
                    partIndex = index,
                    partType = part.partType,
                    relativePath = part.relativePath,
                    mimeType = part.mimeType,
                    displayName = part.displayName,
                    sizeBytes = part.sizeBytes,
                    widthPx = part.widthPx,
                    heightPx = part.heightPx,
                    durationMs = part.durationMs,
                    sourceMessageId = part.sourceMessageId,
                    state = part.state,
                    createdAt = now,
                    updatedAt = now
                )
            }
        )
    }

    suspend fun deleteMessage(messageId: Long) {
        val parts = database.messageDao().messageParts(messageId)
        database.messageDao().deleteMessage(messageId)
        cleanupOrphanedFiles(parts)
    }

    suspend fun setInferenceMode(mode: InferenceMode) {
        val previous = preferences.settings.first().inferenceMode
        if (previous == InferenceMode.DOWNLOADED && mode != InferenceMode.DOWNLOADED) {
            downloadedInferenceClient.release()
        }
        preferences.updateInferenceMode(mode)
    }

    suspend fun updateSettings(
        baseUrl: String,
        modelName: String,
        transcriptionModelName: String,
        apiKey: String,
        temperature: Double,
        topP: Double,
        contextLength: Int
    ) {
        preferences.updateModelSettings(
            baseUrl = baseUrl,
            modelName = modelName,
            transcriptionModelName = transcriptionModelName,
            temperature = temperature,
            topP = topP,
            contextLength = contextLength
        )
        preferences.updateSecrets(apiKey = apiKey)
    }

    suspend fun settingsSnapshot(): SettingsSnapshot = preferences.settings.first()

    suspend fun geminiNanoStatus(): GeminiNanoStatus = localInferenceClient.geminiStatus()

    fun downloadGeminiNano(): Flow<GeminiNanoStatus> = localInferenceClient.downloadModel()

    suspend fun saveGeminiNanoModelSize(bytes: Long) {
        preferences.updateGeminiNanoModelSize(bytes)
    }

    suspend fun backendAvailability(
        mode: InferenceMode,
        settings: SettingsSnapshot
    ): BackendAvailability = buildClient(mode).availability(settings)

    suspend fun backendCapabilities(
        mode: InferenceMode,
        settings: SettingsSnapshot
    ): InferenceCapabilities = buildClient(mode).capabilities(settings)

    suspend fun prepareSelectedLocalModel(settings: SettingsSnapshot? = null): String? {
        val selectedModelId =
            localModelRepository.activeModelStatus.value.modelId?.trim().orEmpty().lowercase()
        if (selectedModelId.isBlank()) {
            return "Choose a local model from Model Library."
        }
        return localModelRepository.prepareModelInMemory(selectedModelId, settings)
    }

    suspend fun recentTurnsFor(mode: InferenceMode, sessionId: Long): List<ChatTurn> {
        val limit = ChatDefaults.historyWindowFor(mode)

        return database.messageDao()
            .latestMessages(sessionId, limit)
            .asReversed()
            .map(ChatMessageEntity::toTurn)
    }

    fun streamResponse(
        mode: InferenceMode,
        history: List<ChatTurn>,
        prompt: String,
        settings: SettingsSnapshot,
        imageAttachment: ComposerAttachment? = null,
        sessionId: Long? = null
    ): Flow<String> {
        if (imageAttachment?.type == ComposerAttachmentType.AUDIO) {
            throw InferenceException.UnsupportedModality(
                "Audio attachments use the transcription flow."
            )
        }
        val image = imageAttachment
            ?.takeIf { it.type == ComposerAttachmentType.IMAGE }
            ?.let {
                InferenceImageAttachment(
                    relativePath = it.relativePath,
                    mimeType = it.mimeType
                )
            }

        return buildClient(mode)
            .streamChat(
                InferenceRequest(
                    history = history,
                    prompt = prompt,
                    settings = settings,
                    imageAttachment = image,
                    activeDownloadedModelId = settings.activeLocalModelId,
                    sessionId = sessionId
                )
            )
    }

    suspend fun transcribeAudio(
        mode: InferenceMode,
        settings: SettingsSnapshot,
        attachment: ComposerAttachment
    ): AudioTranscriptionResult {
        if (attachment.type != ComposerAttachmentType.AUDIO) {
            throw InferenceException.UnsupportedModality(
                "Only audio attachments can be transcribed."
            )
        }
        val client = buildClient(mode)
        return client.transcribeAudio(
            AudioTranscriptionRequest(
                settings = settings,
                audioAttachment = InferenceAudioAttachment(
                    relativePath = attachment.relativePath,
                    mimeType = attachment.mimeType,
                    displayName = attachment.displayName
                )
            )
        )
    }

    fun buildClient(mode: InferenceMode): InferenceClient {
        return InferenceClientSelector.select(
            mode = mode,
            local = localInferenceClient,
            downloaded = downloadedInferenceClient,
            remote = remoteInferenceClient
        )
    }

    suspend fun clearAllData() {
        preferences.clearPinnedSessions()
        database.messageDao().deleteAll()
        database.sessionDao().deleteAll()
        mediaStore.clearAll()
    }

    suspend fun usageStats(): UsageStats {
        val sessionCount = database.sessionDao().countSessions()
        val userCount = database.messageDao().countByRole(ChatRole.USER)
        val assistantCount = database.messageDao().countByRole(ChatRole.ASSISTANT)
        return UsageStats(
            sessionCount = sessionCount,
            messagesSent = userCount,
            messagesReceived = assistantCount
        )
    }

    private suspend fun updateSessionTitleIfNeeded(sessionId: Long, content: String, now: Long) {
        database.sessionDao()
            .updateSession(sessionId, ChatDefaults.normalizedSessionTitle(content), now)
    }

    private fun messageModelName(settings: SettingsSnapshot): String {
        if (settings.inferenceMode != InferenceMode.DOWNLOADED) return settings.modelName

        val activeId = settings.activeLocalModelId.trim().lowercase()
        if (activeId.isBlank()) return settings.modelName

        return localModelRepository.records.value
            .firstOrNull { it.modelId.equals(activeId, ignoreCase = true) }
            ?.displayName
            ?.ifBlank { settings.modelName }
            ?: settings.modelName
    }

    private suspend fun cleanupOrphanedFiles(parts: List<ChatMessagePartEntity>) {
        val candidates = parts.mapNotNull { it.relativePath }.distinct()
        candidates.forEach { relativePath ->
            val inUseCount = database.messageDao().countPartsByRelativePath(relativePath)
            if (inUseCount <= 0L) {
                mediaStore.deleteRelativePath(relativePath)
            }
        }
    }
}

private fun ActiveModelStatus.unready(message: String): ActiveModelStatus {
    return copy(ready = false, message = message)
}

data class LocalModelCapabilities(
    val modelId: String?,
    val supportsThinking: Boolean,
    val promptFamily: String?,
    val supportedAccelerators: List<String> = emptyList()
)

data class MessagePartWrite(
    val partType: ChatMessagePartType,
    val relativePath: String? = null,
    val mimeType: String? = null,
    val displayName: String? = null,
    val sizeBytes: Long? = null,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val durationMs: Long? = null,
    val sourceMessageId: Long? = null,
    val state: ChatMessagePartState = ChatMessagePartState.READY
)

private fun preparingModelMessage(displayName: String): String {
    return "Selected $displayName. Preparing local model..."
}

private fun notReadyYetMessage(displayName: String): String {
    return "Selected $displayName is not ready yet. NanoChat is preparing it now."
}

private fun willPrepareOnStartMessage(displayName: String): String {
    return "Selected $displayName is not ready yet. NanoChat will prepare it when local chat starts."
}

private fun notLoadedMessage(displayName: String): String {
    return "Selected $displayName is not loaded. Open Model Library and tap Use model."
}

private fun prepareFailedMessage(displayName: String): String {
    return "Selected $displayName failed to prepare for local chat. Tap Use model to retry."
}

private fun ChatMessageEntity.toTurn(): ChatTurn {
    return ChatTurn(role = role, content = content)
}

private fun ChatSessionEntity.toModel(): ChatSession {
    return ChatSession(id = id, title = title, updatedAt = updatedAt, isPinned = false)
}

private fun ChatMessageWithParts.toModel(mediaStore: ChatMediaStore): ChatMessage {
    val orderedParts = parts.sortedBy { it.partIndex }
    val modelParts = buildList {
        if (message.content.isNotBlank()) {
            add(MessagePart.TextPart(text = message.content))
        }

        orderedParts.forEach { part ->
            when (part.partType) {
                ChatMessagePartType.IMAGE -> {
                    val relativePath = part.relativePath ?: return@forEach
                    add(
                        MessagePart.ImagePart(
                            relativePath = relativePath,
                            absolutePath = mediaStore.resolveAbsolutePath(relativePath),
                            mimeType = part.mimeType.orEmpty(),
                            displayName = part.displayName,
                            sizeBytes = part.sizeBytes,
                            widthPx = part.widthPx,
                            heightPx = part.heightPx,
                            state = part.state.toModelState()
                        )
                    )
                }

                ChatMessagePartType.AUDIO -> {
                    val relativePath = part.relativePath ?: return@forEach
                    add(
                        MessagePart.AudioPart(
                            relativePath = relativePath,
                            absolutePath = mediaStore.resolveAbsolutePath(relativePath),
                            mimeType = part.mimeType.orEmpty(),
                            displayName = part.displayName,
                            sizeBytes = part.sizeBytes,
                            durationMs = part.durationMs,
                            state = part.state.toModelState()
                        )
                    )
                }

                ChatMessagePartType.TRANSCRIPT -> {
                    add(
                        MessagePart.TranscriptPart(
                            sourceMessageId = part.sourceMessageId,
                            label = part.displayName,
                            state = part.state.toModelState()
                        )
                    )
                }
            }
        }
    }

    return ChatMessage(
        id = message.id,
        sessionId = message.sessionId,
        role = message.role,
        content = message.content,
        parts = modelParts,
        inferenceMode = message.inferenceMode,
        modelName = message.modelName,
        temperature = message.temperature,
        topP = message.topP,
        contextLength = message.contextLength
    )
}

private fun ChatMessagePartState.toModelState(): MessagePartState {
    return when (this) {
        ChatMessagePartState.READY -> MessagePartState.READY
        ChatMessagePartState.PENDING -> MessagePartState.PENDING
        ChatMessagePartState.FAILED -> MessagePartState.FAILED
        ChatMessagePartState.COMPLETED -> MessagePartState.COMPLETED
    }
}

private fun com.fcm.nanochat.data.media.ImportedMediaAsset.toComposerAttachment(): ComposerAttachment {
    return ComposerAttachment(
        type = type,
        relativePath = relativePath,
        absolutePath = absolutePath,
        mimeType = mimeType,
        displayName = displayName,
        sizeBytes = sizeBytes,
        widthPx = widthPx,
        heightPx = heightPx,
        durationMs = durationMs
    )
}
