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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
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

    private val messages = selectedSessionId.flatMapLatest { sessionId ->
        if (sessionId == null) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            repository.observeMessages(sessionId)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<ChatScreenState> =
        combine(
            sessions,
            pinnedSessionIds,
            messages,
            draft,
            settings,
            isSending,
            notice,
            selectedSessionId
        ) { values ->
            val sessionsValue = values[0] as List<ChatSession>
            val pinnedIds = values[1] as Set<Long>
            val messagesValue = values[2] as List<ChatMessage>
            val draftValue = values[3] as String
            val settingsValue = values[4] as SettingsSnapshot
            val isSendingValue = values[5] as Boolean
            val noticeValue = values[6] as String?
            val selectedSessionIdValue = values[7] as Long?

            val orderedSessions = sessionsValue
                .map { session -> session.copy(isPinned = pinnedIds.contains(session.id)) }
                .sortedWith(compareByDescending<ChatSession> { it.isPinned }.thenByDescending { it.updatedAt })

            val activeSessionId =
                if (selectedSessionIdValue != null && orderedSessions.any { it.id == selectedSessionIdValue }) {
                    selectedSessionIdValue
                } else {
                    orderedSessions.firstOrNull()?.id
                }

            ChatScreenState(
                sessions = orderedSessions,
                selectedSessionId = activeSessionId,
                messages = messagesValue.map { message ->
                    message.copy(
                        isStreaming = isSendingValue &&
                            message.role == ChatRole.ASSISTANT &&
                            message.id == messagesValue.lastOrNull()?.id
                    )
                },
                draft = draftValue,
                inferenceMode = settingsValue.inferenceMode,
                isSending = isSendingValue,
                notice = noticeValue
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatScreenState())

    init {
        viewModelScope.launch {
            val sessionId = repository.ensureSession()
            selectedSessionId.value = sessionId
        }
    }

    fun updateDraft(value: String) {
        draft.value = value
    }

    fun createSession() {
        viewModelScope.launch {
            val sessionId = repository.createSession()
            selectedSessionId.value = sessionId
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
            val wasSelected = selectedSessionId.value == sessionId
            repository.deleteSession(sessionId)
            if (wasSelected) {
                selectedSessionId.value = null
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
            val availability = repository.backendAvailability(mode, settings.value)
            if (availability is BackendAvailability.Unavailable) {
                notice.value = availability.message
            } else {
                notice.value = null
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
        val sessionId = selectedSessionId.value ?: return
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
                val message = when (error) {
                    is InferenceException.Configuration -> error.message
                    is InferenceException.BackendUnavailable -> error.message
                    is InferenceException.Busy -> "AICore is busy or quota-limited. Wait a moment and retry."
                    is InferenceException.RemoteFailure -> error.message
                    else -> error.message ?: "Inference failed."
                }
                notice.value = message
                repository.updateAssistantMessage(assistantMessageId, "Unable to complete the request.")
            }

            isSending.value = false
        }
    }
}

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
