package com.fcm.nanochat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fcm.nanochat.data.SettingsSnapshot
import com.fcm.nanochat.data.repository.ChatRepository
import com.fcm.nanochat.inference.BackendAvailability
import com.fcm.nanochat.inference.InferenceException
import com.fcm.nanochat.inference.InferenceMode
import com.fcm.nanochat.model.ChatMessage
import com.fcm.nanochat.model.ChatRole
import com.fcm.nanochat.model.ChatScreenState
import com.fcm.nanochat.model.ChatSession
import com.fcm.nanochat.models.registry.ActiveModelStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val repository: ChatRepository
) : ViewModel() {
    private val selectedSessionId = MutableStateFlow<Long?>(null)
    private val draft = MutableStateFlow("")
    private val notice = MutableStateFlow<String?>(null)
    private val isSending = MutableStateFlow(false)
    private var sendJob: Job? = null
    private var activeRequestId: Long = 0
    private var lastUserPrompt: String? = null

    private val settings = repository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsSnapshot())

    private val sessions = repository.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val pinnedSessionIds = repository.observePinnedSessionIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val activeLocalModelStatus = repository.observeActiveLocalModelStatus()
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

    private val activeSessionId = combine(selectedSessionId, sessions) { selected, sessionList ->
        if (selected != null && sessionList.any { it.id == selected }) {
            selected
        } else {
            sessionList.firstOrNull()?.id
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val messages = activeSessionId.flatMapLatest { sessionId ->
        if (sessionId == null) {
            flowOf(emptyList())
        } else {
            repository.observeMessages(sessionId)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val sessionState = combine(
        sessions,
        pinnedSessionIds,
        messages,
        activeSessionId,
        isSending
    ) { sessionList, pinnedIds, messageList, selectedId, sending ->
        val orderedSessions = sessionList
            .map { session -> session.copy(isPinned = pinnedIds.contains(session.id)) }
            .sortedWith(compareByDescending<ChatSession> { it.isPinned }.thenByDescending { it.updatedAt })

        val uiMessages = messageList.map { message ->
            message.copy(
                isStreaming = sending &&
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

    val uiState: StateFlow<ChatScreenState> = combine(
        sessionState,
        draft,
        settings,
        activeLocalModelStatus,
        isSending
    ) { sessionUi, draftValue, settingsValue, activeLocalModel, isSendingValue ->
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
    }.combine(notice) { baseState, noticeValue ->
        baseState.copy(notice = noticeValue)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatScreenState())

    init {
        viewModelScope.launch {
            selectedSessionId.value = repository.ensureSession()
        }
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
        viewModelScope.launch {
            repository.renameSession(sessionId, title)
        }
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
        viewModelScope.launch {
            repository.setSessionPinned(sessionId, pinned)
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            repository.deleteMessage(messageId)
        }
    }

    fun setInferenceMode(mode: InferenceMode) {
        viewModelScope.launch {
            if (
                settings.value.inferenceMode == InferenceMode.DOWNLOADED &&
                mode != InferenceMode.DOWNLOADED
            ) {
                sendJob?.cancel()
            }
            repository.setInferenceMode(mode)
            if (mode == InferenceMode.DOWNLOADED && !activeLocalModelStatus.value.ready) {
                notice.value = activeLocalModelStatus.value.message
                return@launch
            }
            when (val availability = repository.backendAvailability(mode, settings.value)) {
                is BackendAvailability.Unavailable -> notice.value = availability.message
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

        if (
            settings.value.inferenceMode == InferenceMode.DOWNLOADED &&
            !activeLocalModelStatus.value.ready
        ) {
            notice.value = activeLocalModelStatus.value.message
            return
        }

        val requestId = ++activeRequestId
        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            isSending.value = true
            notice.value = null
            lastUserPrompt = prompt
            var assistantMessageId: Long? = null

            try {
                val snapshot = settings.value
                val mode = snapshot.inferenceMode
                val availability = repository.backendAvailability(mode, snapshot)
                if (availability is BackendAvailability.Unavailable) {
                    notice.value = availability.message
                    return@launch
                }

                val history = repository.recentTurnsFor(mode, sessionId)
                repository.saveUserMessage(sessionId, prompt, snapshot)
                val createdAssistantMessageId =
                    repository.insertAssistantPlaceholder(sessionId, snapshot)
                assistantMessageId = createdAssistantMessageId
                draft.value = ""

                val assembler = StreamingMessageAssembler()
                var lastPersistTimeMs = 0L
                var lastPersistedLength = 0
                var lastPersistedSnapshot = ""

                repository.streamResponse(mode, history, prompt, snapshot).collect { delta ->
                    val content = assembler.append(mode, delta)
                    if (
                        mode != InferenceMode.AICORE &&
                        shouldPersistStreamSnapshot(content, lastPersistTimeMs, lastPersistedLength)
                    ) {
                        repository.updateAssistantMessage(createdAssistantMessageId, content)
                        lastPersistTimeMs = System.currentTimeMillis()
                        lastPersistedLength = content.length
                        lastPersistedSnapshot = content
                    }
                }

                val finalContent = assembler.current()
                if (mode == InferenceMode.AICORE || finalContent != lastPersistedSnapshot) {
                    repository.updateAssistantMessage(createdAssistantMessageId, finalContent)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                val message = userFacingError(error).ifBlank { "Unable to complete the request." }
                notice.value = message
                assistantMessageId?.let { messageId ->
                    repository.updateAssistantMessage(messageId, message)
                }
            } finally {
                if (activeRequestId == requestId) {
                    isSending.value = false
                }
            }
        }
    }

    fun stopGeneration() {
        if (!isSending.value) return
        sendJob?.cancel()
        if (settings.value.inferenceMode == InferenceMode.DOWNLOADED) {
            repository.releaseDownloadedRuntime()
        }
        isSending.value = false
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
        return elapsedMs >= STREAM_PERSIST_INTERVAL_MS || lengthGrowth >= STREAM_PERSIST_MIN_LENGTH_DELTA
    }

    private fun userFacingError(error: Throwable): String {
        return when (error) {
            is InferenceException.Configuration -> error.message.orEmpty()
            is InferenceException.BackendUnavailable -> error.message.orEmpty()
            is InferenceException.Busy -> "AICore is busy or quota-limited. Wait a moment and retry."
            is InferenceException.RemoteFailure -> error.message.orEmpty()
            else -> error.message ?: "Inference failed."
        }
    }

    private companion object {
        const val STREAM_PERSIST_INTERVAL_MS = 180L
        const val STREAM_PERSIST_MIN_LENGTH_DELTA = 24
    }
}

private data class SessionUiState(
    val sessions: List<ChatSession>,
    val selectedSessionId: Long?,
    val messages: List<ChatMessage>
)

class ChatViewModelFactory(
    private val repository: ChatRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
