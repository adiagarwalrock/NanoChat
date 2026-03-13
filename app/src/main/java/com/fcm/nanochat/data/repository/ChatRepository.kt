package com.fcm.nanochat.data.repository

import com.fcm.nanochat.data.AppPreferences
import com.fcm.nanochat.data.SettingsSnapshot
import com.fcm.nanochat.data.db.AppDatabase
import com.fcm.nanochat.data.db.ChatMessageEntity
import com.fcm.nanochat.data.db.ChatSessionEntity
import com.fcm.nanochat.inference.BackendAvailability
import com.fcm.nanochat.inference.ChatTurn
import com.fcm.nanochat.inference.GeminiNanoStatus
import com.fcm.nanochat.inference.InferenceClient
import com.fcm.nanochat.inference.InferenceClientSelector
import com.fcm.nanochat.inference.InferenceMode
import com.fcm.nanochat.inference.InferenceRequest
import com.fcm.nanochat.inference.LocalInferenceClient
import com.fcm.nanochat.model.ChatMessage
import com.fcm.nanochat.model.ChatRole
import com.fcm.nanochat.model.ChatSession
import com.fcm.nanochat.model.UsageStats
import com.fcm.nanochat.models.registry.ActiveModelStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class ChatRepository(
    private val database: AppDatabase,
    private val preferences: AppPreferences,
    private val localModelRepository: LocalModelRepository,
    private val localInferenceClient: LocalInferenceClient,
    private val downloadedInferenceClient: InferenceClient,
    private val remoteInferenceClient: InferenceClient
) {
    fun observeSettings(): Flow<SettingsSnapshot> = preferences.settings

    fun observePinnedSessionIds(): Flow<Set<Long>> = preferences.pinnedSessionIds

    fun observeActiveLocalModelStatus(): Flow<ActiveModelStatus> =
        localModelRepository.activeModelStatus

    fun observeSessions(): Flow<List<ChatSession>> =
        database.sessionDao().observeSessions().map { sessions ->
            sessions.map(ChatSessionEntity::toModel)
        }

    fun observeMessages(sessionId: Long): Flow<List<ChatMessage>> =
        database.messageDao().observeMessages(sessionId).map { messages ->
            messages.map(ChatMessageEntity::toModel)
        }

    suspend fun ensureSession(): Long {
        val latest = database.sessionDao().latestSession()
        return latest?.id ?: createSession()
    }

    suspend fun createSession(title: String = ChatDefaults.defaultSessionTitle): Long {
        val now = System.currentTimeMillis()
        return database.sessionDao().insert(
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
        database.sessionDao().deleteSession(sessionId)
        preferences.setSessionPinned(sessionId, false)
    }

    suspend fun setSessionPinned(sessionId: Long, pinned: Boolean) {
        preferences.setSessionPinned(sessionId, pinned)
    }

    suspend fun saveUserMessage(
        sessionId: Long,
        content: String,
        settings: SettingsSnapshot
    ): Long {
        val now = System.currentTimeMillis()
        updateSessionTitleIfNeeded(sessionId, content, now)
        return database.messageDao().insert(
            ChatMessageEntity(
                sessionId = sessionId,
                role = ChatRole.USER,
                content = content,
                inferenceMode = settings.inferenceMode,
                modelName = settings.modelName,
                temperature = settings.temperature,
                topP = settings.topP,
                contextLength = settings.contextLength,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun insertAssistantPlaceholder(
        sessionId: Long,
        settings: SettingsSnapshot
    ): Long {
        val now = System.currentTimeMillis()
        return database.messageDao().insert(
            ChatMessageEntity(
                sessionId = sessionId,
                role = ChatRole.ASSISTANT,
                content = "",
                inferenceMode = settings.inferenceMode,
                modelName = settings.modelName,
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

    suspend fun deleteMessage(messageId: Long) {
        database.messageDao().deleteMessage(messageId)
    }

    suspend fun setInferenceMode(mode: InferenceMode) {
        val previous = preferences.settings.first().inferenceMode
        if (previous == InferenceMode.DOWNLOADED && mode != InferenceMode.DOWNLOADED) {
            downloadedInferenceClient.release()
        }
        preferences.updateInferenceMode(mode)
    }

    fun releaseDownloadedRuntime() {
        downloadedInferenceClient.release()
    }

    suspend fun updateSettings(
        baseUrl: String,
        modelName: String,
        apiKey: String,
        huggingFaceToken: String,
        temperature: Double,
        topP: Double,
        contextLength: Int
    ) {
        preferences.updateModelSettings(
            baseUrl = baseUrl,
            modelName = modelName,
            temperature = temperature,
            topP = topP,
            contextLength = contextLength
        )
        preferences.updateSecrets(apiKey = apiKey, huggingFaceToken = huggingFaceToken)
    }

    suspend fun settingsSnapshot(): SettingsSnapshot = preferences.settings.first()

    suspend fun geminiNanoStatus(): GeminiNanoStatus = localInferenceClient.geminiStatus()

    fun downloadGeminiNano(): Flow<GeminiNanoStatus> = localInferenceClient.downloadModel()

    suspend fun saveGeminiNanoModelSize(bytes: Long) {
        preferences.updateGeminiNanoModelSize(bytes)
    }

    suspend fun backendAvailability(mode: InferenceMode, settings: SettingsSnapshot): BackendAvailability =
        buildClient(mode).availability(settings)

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
        settings: SettingsSnapshot
    ): Flow<String> {
        return buildClient(mode).streamChat(
            InferenceRequest(
                history = history,
                prompt = prompt,
                settings = settings
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
        database.sessionDao().updateSession(
            sessionId,
            ChatDefaults.normalizedSessionTitle(content),
            now
        )
    }
}

private fun ChatSessionEntity.toModel(): ChatSession {
    return ChatSession(
        id = id,
        title = title,
        updatedAt = updatedAt,
        isPinned = false
    )
}

private fun ChatMessageEntity.toModel(): ChatMessage {
    return ChatMessage(
        id = id,
        sessionId = sessionId,
        role = role,
        content = content,
        inferenceMode = inferenceMode,
        modelName = modelName,
        temperature = temperature,
        topP = topP,
        contextLength = contextLength
    )
}

private fun ChatMessageEntity.toTurn(): ChatTurn {
    return ChatTurn(role = role, content = content)
}
