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
import com.fcm.nanochat.models.runtime.RuntimeLoadPhase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
            combine(localModelRepository.records, localModelRepository.activeModelStatus) {
                    records,
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
                messages.map(ChatMessageEntity::toModel)
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
        return database.messageDao()
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

    suspend fun updateSettings(
            baseUrl: String,
            modelName: String,
            apiKey: String,
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
            sessionId: Long? = null
    ): Flow<String> {
        return buildClient(mode)
                .streamChat(
                        InferenceRequest(
                                history = history,
                                prompt = prompt,
                                settings = settings,
                            activeDownloadedModelId = settings.activeLocalModelId,
                            sessionId = sessionId
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
