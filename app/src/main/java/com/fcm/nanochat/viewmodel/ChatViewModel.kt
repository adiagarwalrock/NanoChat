package com.fcm.nanochat.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fcm.nanochat.data.SettingsSnapshot
import com.fcm.nanochat.data.ThinkingEffort
import com.fcm.nanochat.data.repository.ChatRepository
import com.fcm.nanochat.data.repository.LocalModelCapabilities
import com.fcm.nanochat.inference.BackendAvailability
import com.fcm.nanochat.inference.GeneratedTextSanitizer
import com.fcm.nanochat.inference.InferenceException
import com.fcm.nanochat.inference.InferenceMode
import com.fcm.nanochat.model.ChatMessage
import com.fcm.nanochat.model.ChatRole
import com.fcm.nanochat.model.ChatScreenState
import com.fcm.nanochat.model.ChatSession
import com.fcm.nanochat.models.registry.ActiveModelStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(private val repository: ChatRepository) : ViewModel() {
    private val defaultLocalModelCapabilities =
            LocalModelCapabilities(modelId = null, supportsThinking = false, promptFamily = null)
    private val generationMutex = Mutex()
    private val selectedSessionId = MutableStateFlow<Long?>(null)
    private val draft = MutableStateFlow("")
    private val notice = MutableStateFlow<String?>(null)
    private val isSending = MutableStateFlow(false)
    private var sendJob: Job? = null
    private var activeRequestId: Long = 0
    private var activeAssistantMessageId: Long? = null
    private var activeAssistantPreview: String = ""
    private val cancelledRequestIds = mutableSetOf<Long>()
    private var lastUserPrompt: String? = null

    private val settings =
            repository
                    .observeSettings()
                    .stateIn(
                            viewModelScope,
                            SharingStarted.WhileSubscribed(5_000),
                            SettingsSnapshot()
                    )

    private val sessions =
            repository
                    .observeSessions()
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val pinnedSessionIds =
            repository
                    .observePinnedSessionIds()
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val activeLocalModelStatus =
            repository
                    .observeActiveLocalModelStatus()
                    .stateIn(
                            viewModelScope,
                            SharingStarted.WhileSubscribed(5_000),
                            ActiveModelStatus(
                                    modelId = null,
                                    displayName = null,
                                    ready = false,
                                    message = "Choose a local model from Model Library."
                            )
                    )

    private val activeLocalModelCapabilities =
            repository
                    .observeActiveLocalModelCapabilities()
                    .stateIn(
                            viewModelScope,
                            SharingStarted.WhileSubscribed(5_000),
                            defaultLocalModelCapabilities
                    )

    private val activeSessionId =
            combine(selectedSessionId, sessions) { selected, sessionList ->
                        if (selected != null && sessionList.any { it.id == selected }) {
                            selected
                        } else {
                            sessionList.firstOrNull()?.id
                        }
                    }
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val messages =
            activeSessionId
                    .flatMapLatest { sessionId ->
                        if (sessionId == null) {
                            flowOf(emptyList())
                        } else {
                            repository.observeMessages(sessionId)
                        }
                    }
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val sessionState =
            combine(sessions, pinnedSessionIds, messages, activeSessionId, isSending) {
                    sessionList,
                    pinnedIds,
                    messageList,
                    selectedId,
                    sending ->
                val orderedSessions =
                        sessionList
                                .map { session ->
                                    session.copy(isPinned = pinnedIds.contains(session.id))
                                }
                                .sortedWith(
                                        compareByDescending<ChatSession> { it.isPinned }
                                                .thenByDescending { it.updatedAt }
                                )

                val uiMessages =
                        messageList.map { message ->
                            message.copy(
                                    isStreaming =
                                            sending &&
                                                    message.role == ChatRole.ASSISTANT &&
                                                    message.id == messageList.lastOrNull()?.id
                            )
                        }

                SessionUiState(
                        sessions = orderedSessions,
                        selectedSessionId = selectedId,
                        messages = uiMessages
                )
            }

    private val baseUiState: StateFlow<ChatScreenState> =
            combine(sessionState, draft, settings, activeLocalModelStatus, isSending) {
                            sessionUi,
                            draftValue,
                            settingsValue,
                            activeLocalModel,
                            isSendingValue ->
                        ChatScreenState(
                                sessions = sessionUi.sessions,
                                selectedSessionId = sessionUi.selectedSessionId,
                                messages = sessionUi.messages,
                                draft = draftValue,
                                inferenceMode = settingsValue.inferenceMode,
                                activeLocalModelName = activeLocalModel.displayName,
                                isLocalModelReady = activeLocalModel.ready,
                                localModelStatusMessage = activeLocalModel.message,
                                isSending = isSendingValue,
                                notice = null
                        )
                    }
                    .stateIn(
                            viewModelScope,
                            SharingStarted.WhileSubscribed(5_000),
                            ChatScreenState()
                    )

    val uiState: StateFlow<ChatScreenState> =
            combine(baseUiState, activeLocalModelCapabilities, notice) {
                            base,
                            capabilities,
                            noticeValue ->
                        base.copy(
                                localModelSupportsThinking = capabilities.supportsThinking,
                                localModelSupportedAccelerators =
                                        capabilities.supportedAccelerators,
                                notice = noticeValue
                        )
                    }
                    .stateIn(
                            viewModelScope,
                            SharingStarted.WhileSubscribed(5_000),
                            ChatScreenState()
                    )

    init {
        viewModelScope.launch { selectedSessionId.value = repository.ensureSession() }
    }

    fun updateDraft(value: String) {
        draft.value = value
    }

    fun createSession() {
        viewModelScope.launch {
            selectedSessionId.value = repository.createSession()
            draft.value = ""
            notice.value = null
        }
    }

    fun selectSession(sessionId: Long) {
        selectedSessionId.value = sessionId
    }

    fun renameSession(sessionId: Long, title: String) {
        viewModelScope.launch { repository.renameSession(sessionId, title) }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            val wasSelected = activeSessionId.value == sessionId
            repository.deleteSession(sessionId)
            if (wasSelected) {
                selectedSessionId.value = repository.ensureSession()
            }
        }
    }

    fun setSessionPinned(sessionId: Long, pinned: Boolean) {
        viewModelScope.launch { repository.setSessionPinned(sessionId, pinned) }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch { repository.deleteMessage(messageId) }
    }

    fun setInferenceMode(mode: InferenceMode) {
        viewModelScope.launch {
            if (settings.value.inferenceMode == InferenceMode.DOWNLOADED &&
                            mode != InferenceMode.DOWNLOADED
            ) {
                sendJob?.cancel()
            }
            withContext(Dispatchers.IO) { repository.setInferenceMode(mode) }

            if (mode == InferenceMode.DOWNLOADED) {
                val preparationError =
                        withContext(Dispatchers.IO) { repository.prepareSelectedLocalModel() }
                if (!preparationError.isNullOrBlank() && shouldSurfaceNotice(mode, preparationError)
                ) {
                    notice.value = preparationError
                    return@launch
                }
            }

            when (val availability =
                            withContext(Dispatchers.IO) {
                                repository.backendAvailability(mode, settings.value)
                            }
            ) {
                is BackendAvailability.Unavailable -> {
                    notice.value = availability.message.takeIf { shouldSurfaceNotice(mode, it) }
                }
                BackendAvailability.Available -> notice.value = null
            }
        }
    }

    fun clearNotice() {
        notice.value = null
    }

    fun retryLastMessage() {
        val prompt = lastUserPrompt ?: return
        draft.value = prompt
        sendMessage()
    }

    fun sendMessage() {
        val prompt = draft.value.trim()
        val sessionId = activeSessionId.value ?: return
        if (prompt.isBlank() || isSending.value) return

        sendJob?.cancel()
        val requestId = ++activeRequestId
        cancelledRequestIds.remove(requestId)
        isSending.value = true
        notice.value = null
        lastUserPrompt = prompt

        sendJob =
                viewModelScope.launch {
                    generationMutex.withLock { executeGeneration(requestId, sessionId, prompt) }
                }
    }

    private suspend fun executeGeneration(requestId: Long, sessionId: Long, prompt: String) {
        var assistantMessageId: Long? = null
        val assembler = StreamingMessageAssembler()
        var watchdogJob: Job? = null
        var hasVisibleContent = false
        var watchdogTriggered = false

        try {
            val snapshot = settings.value
            val mode = snapshot.inferenceMode
            Log.d(TAG, "chat_generation_started requestId=$requestId mode=${mode.name}")

            if (!checkAvailability(mode, snapshot)) return

            val history = withContext(Dispatchers.IO) { repository.recentTurnsFor(mode, sessionId) }
            assistantMessageId =
                withContext(Dispatchers.IO) {
                    repository.saveUserMessage(sessionId, prompt, snapshot)
                    repository.insertAssistantPlaceholder(sessionId, snapshot)
                }

            activeAssistantMessageId = assistantMessageId
            activeAssistantPreview = ""
            draft.value = ""

            val parentJob = coroutineContext[Job]
            watchdogJob =
                viewModelScope.launch {
                    delay(firstTokenWatchdogMs(mode))
                    if (!hasVisibleContent && isCurrentRequest(requestId)) {
                        watchdogTriggered = true
                        parentJob?.cancel(
                            GenerationWatchdogTimeout(
                                "No response arrived in time. Retry or reselect this model."
                            )
                        )
                    }
                }

            var lastPersistTimeMs = 0L
            var lastPersistedLength = 0
            var lastPersistedSnapshot = ""

            repository.streamResponse(mode, history, prompt, snapshot).collect { delta ->
                ensureRequestActive(requestId)
                val content = assembler.append(mode, delta)
                val filtered = filterThinking(content, snapshot.thinkingEffort)
                activeAssistantPreview = filtered

                if (filtered.isNotBlank() && !hasVisibleContent) {
                    Log.d(
                        TAG,
                        "chat_generation_first_visible requestId=$requestId mode=${mode.name}"
                    )
                    hasVisibleContent = true
                }

                if (mode != InferenceMode.AICORE &&
                    shouldPersistStreamSnapshot(
                        filtered,
                        lastPersistTimeMs,
                        lastPersistedLength
                    )
                ) {
                    withContext(Dispatchers.IO) {
                        repository.updateAssistantMessage(assistantMessageId!!, filtered)
                    }
                    lastPersistTimeMs = System.currentTimeMillis()
                    lastPersistedLength = filtered.length
                    lastPersistedSnapshot = filtered
                }
            }

            ensureRequestActive(requestId)
            val finalContent = filterThinking(assembler.current(), snapshot.thinkingEffort)
            if (finalContent.isBlank()) {
                throw InferenceException.BackendUnavailable(
                    "Local model produced no visible text. Try again or reselect this model."
                )
            }

            if (mode == InferenceMode.AICORE || finalContent != lastPersistedSnapshot) {
                withContext(Dispatchers.IO) {
                    repository.updateAssistantMessage(assistantMessageId!!, finalContent)
                }
            }
        } catch (cancellation: CancellationException) {
            handleGenerationCancellation(
                requestId,
                assistantMessageId,
                watchdogTriggered,
                cancellation
            )
        } catch (error: Throwable) {
            handleGenerationError(requestId, assistantMessageId, error)
        } finally {
            cleanupGeneration(requestId, assistantMessageId, watchdogJob)
        }
    }

    private suspend fun checkAvailability(
        mode: InferenceMode,
        snapshot: SettingsSnapshot
    ): Boolean {
        if (mode == InferenceMode.DOWNLOADED) {
            val error = withContext(Dispatchers.IO) { repository.prepareSelectedLocalModel() }
            if (!error.isNullOrBlank()) {
                notice.value = error.takeIf { shouldSurfaceNotice(mode, it) }
                return false
            }
        }

        val availability =
            withContext(Dispatchers.IO) { repository.backendAvailability(mode, snapshot) }
        if (availability is BackendAvailability.Unavailable) {
            notice.value = availability.message.takeIf { shouldSurfaceNotice(mode, it) }
            return false
        }
        return true
    }

    private suspend fun handleGenerationCancellation(
        requestId: Long,
        assistantMessageId: Long?,
        watchdogTriggered: Boolean,
        cancellation: CancellationException
    ) {
        Log.d(
            TAG,
            "chat_generation_cancelled requestId=$requestId reason=${cancellation.message.orEmpty()} watchdog=$watchdogTriggered"
        )
        val content =
            when {
                activeAssistantPreview.isNotBlank() -> activeAssistantPreview
                watchdogTriggered || cancellation is GenerationWatchdogTimeout ->
                    WATCHDOG_TIMEOUT_MESSAGE

                else -> CANCELLATION_MESSAGE
            }

        assistantMessageId?.let { id ->
            withContext(NonCancellable + Dispatchers.IO) {
                repository.updateAssistantMessage(id, content)
            }
        }

        if (watchdogTriggered || cancellation is GenerationWatchdogTimeout) {
            notice.value =
                WATCHDOG_TIMEOUT_MESSAGE.takeIf {
                    shouldSurfaceNotice(settings.value.inferenceMode, it)
                }
        }
    }

    private suspend fun handleGenerationError(
        requestId: Long,
        assistantMessageId: Long?,
        error: Throwable
    ) {
        Log.e(
            TAG,
            "chat_generation_failed requestId=$requestId message=${error.message.orEmpty()}",
            error
        )
        val message = userFacingError(error).ifBlank { "Unable to complete the request." }
        val fallbackContent = activeAssistantPreview.takeIf { it.isNotBlank() }

        notice.value =
            if (fallbackContent == null)
                message.takeIf { shouldSurfaceNotice(settings.value.inferenceMode, it) }
            else null

        assistantMessageId?.let { id ->
            withContext(Dispatchers.IO) {
                repository.updateAssistantMessage(id, fallbackContent ?: message)
            }
        }
    }

    private fun cleanupGeneration(requestId: Long, assistantMessageId: Long?, watchdogJob: Job?) {
        Log.d(
            TAG,
            "chat_generation_finished requestId=$requestId isCurrent=${activeRequestId == requestId}"
        )
        watchdogJob?.cancel()
        if (activeAssistantMessageId == assistantMessageId) {
            activeAssistantMessageId = null
            activeAssistantPreview = ""
        }
        cancelledRequestIds.remove(requestId)
        if (activeRequestId == requestId) {
            isSending.value = false
        }
    }

    fun stopGeneration() {
        if (!isSending.value) return

        val requestId = activeRequestId
        cancelledRequestIds += requestId
        activeRequestId = requestId + 1

        val assistantMessageId = activeAssistantMessageId
        val partialContent = activeAssistantPreview

        sendJob?.cancel(CancellationException(CANCELLATION_MESSAGE))
        if (assistantMessageId != null && partialContent.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.updateAssistantMessage(assistantMessageId, partialContent)
            }
        }

        isSending.value = false
        notice.value = null
    }

    private fun shouldPersistStreamSnapshot(
            content: String,
            lastPersistTimeMs: Long,
            lastPersistedLength: Int
    ): Boolean {
        if (content.isEmpty()) return false

        if (lastPersistTimeMs == 0L) return true

        val elapsedMs = System.currentTimeMillis() - lastPersistTimeMs
        val lengthGrowth = content.length - lastPersistedLength
        return elapsedMs >= STREAM_PERSIST_INTERVAL_MS ||
                lengthGrowth >= STREAM_PERSIST_MIN_LENGTH_DELTA
    }

    private fun isCurrentRequest(requestId: Long): Boolean {
        return activeRequestId == requestId && !cancelledRequestIds.contains(requestId)
    }

    private fun ensureRequestActive(requestId: Long) {
        if (!isCurrentRequest(requestId)) {
            throw CancellationException("Generation request was superseded.")
        }
    }

    private fun firstTokenWatchdogMs(mode: InferenceMode): Long {
        return when (mode) {
            InferenceMode.DOWNLOADED -> LOCAL_FIRST_TOKEN_WATCHDOG_MS
            InferenceMode.AICORE, InferenceMode.REMOTE -> REMOTE_FIRST_TOKEN_WATCHDOG_MS
        }
    }

    private fun userFacingError(error: Throwable): String {
        return when (error) {
            is InferenceException.Configuration -> error.message.orEmpty()
            is InferenceException.BackendUnavailable -> error.message.orEmpty()
            is InferenceException.Busy ->
                    "AICore is busy or quota-limited. Wait a moment and retry."
            is InferenceException.RemoteFailure -> error.message.orEmpty()
            else -> error.message ?: "Inference failed."
        }
    }

    private fun shouldSurfaceNotice(mode: InferenceMode, message: String): Boolean {
        return message.isNotBlank() && mode != InferenceMode.DOWNLOADED
    }

    private fun filterThinking(content: String, effort: ThinkingEffort): String {
        return if (effort == ThinkingEffort.NONE) {
            GeneratedTextSanitizer.sanitize(raw = content, preserveThinkingBlocks = false).trim()
        } else {
            content
        }
    }

    private companion object {
        const val TAG = "ChatViewModel"
        const val STREAM_PERSIST_INTERVAL_MS = 180L
        const val STREAM_PERSIST_MIN_LENGTH_DELTA = 24
        const val LOCAL_FIRST_TOKEN_WATCHDOG_MS = 18_000L
        const val REMOTE_FIRST_TOKEN_WATCHDOG_MS = 25_000L
        const val CANCELLATION_MESSAGE = "Generation cancelled."
        const val WATCHDOG_TIMEOUT_MESSAGE =
                "No response arrived in time. Retry last, or reselect the local model."
        val THINKING_BLOCK_REGEX = Regex("(?is)<think>.*?</think>")
    }
}

private class GenerationWatchdogTimeout(message: String) : CancellationException(message)

private data class SessionUiState(
        val sessions: List<ChatSession>,
        val selectedSessionId: Long?,
        val messages: List<ChatMessage>
)

class ChatViewModelFactory(private val repository: ChatRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return ChatViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
