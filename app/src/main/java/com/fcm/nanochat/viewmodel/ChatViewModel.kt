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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository
) : ViewModel() {
    private val selectedSessionId = MutableStateFlow<Long?>(null)
    private val draft = MutableStateFlow("")
    private val notice = MutableStateFlow<String?>(null)
    private val isSending = MutableStateFlow(false)
    private var sendJob: Job? = null
    private var lastUserPrompt: String? = null

    private val settings = repository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsSnapshot())

    private val sessions = repository.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val pinnedSessionIds = repository.observePinnedSessionIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

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
        isSending,
        notice
    ) { sessionUi, draftValue, settingsValue, isSendingValue, noticeValue ->
        ChatScreenState(
            sessions = sessionUi.sessions,
            selectedSessionId = sessionUi.selectedSessionId,
            messages = sessionUi.messages,
            draft = draftValue,
            inferenceMode = settingsValue.inferenceMode,
            isSending = isSendingValue,
            notice = noticeValue
        )
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

    fun setInferenceMode(mode: InferenceMode) {
        viewModelScope.launch {
            repository.setInferenceMode(mode)
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

        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            isSending.value = true
            notice.value = null
            lastUserPrompt = prompt

            val mode = settings.value.inferenceMode
            val snapshot = settings.value
            val availability = repository.backendAvailability(mode, snapshot)
            if (availability is BackendAvailability.Unavailable) {
                notice.value = availability.message
                isSending.value = false
                return@launch
            }

            val history = repository.recentTurnsFor(mode, sessionId)
            repository.saveUserMessage(sessionId, prompt)
            val assistantMessageId = repository.insertAssistantPlaceholder(sessionId)
            draft.value = ""
            val assembler = StreamingMessageAssembler()

            runCatching {
                repository.streamResponse(mode, history, prompt, snapshot).collect { delta ->
                    val content = assembler.append(mode, delta)
                    if (mode == InferenceMode.REMOTE) {
                        repository.updateAssistantMessage(assistantMessageId, content)
                    }
                }
                if (mode == InferenceMode.AICORE) {
                    repository.updateAssistantMessage(assistantMessageId, assembler.current())
                }
            }.onFailure { error ->
                val message = userFacingError(error).ifBlank { "Unable to complete the request." }
                notice.value = message
                repository.updateAssistantMessage(assistantMessageId, message)
            }

            isSending.value = false
        }
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
