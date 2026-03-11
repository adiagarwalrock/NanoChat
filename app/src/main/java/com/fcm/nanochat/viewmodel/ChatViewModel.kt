package com.fcm.nanochat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fcm.nanochat.data.repository.ChatRepository
import com.fcm.nanochat.inference.BackendAvailability
import com.fcm.nanochat.inference.InferenceException
import com.fcm.nanochat.inference.InferenceMode
import com.fcm.nanochat.model.ChatScreenState
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.fcm.nanochat.data.SettingsSnapshot())

    private val sessions = repository.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
            messages,
            draft,
            settings,
            isSending,
            notice,
            selectedSessionId
        ) { values ->
            val sessionsValue = values[0] as List<com.fcm.nanochat.model.ChatSession>
            val messagesValue = values[1] as List<com.fcm.nanochat.model.ChatMessage>
            val draftValue = values[2] as String
            val settingsValue = values[3] as com.fcm.nanochat.data.SettingsSnapshot
            val isSendingValue = values[4] as Boolean
            val noticeValue = values[5] as String?
            val selectedSessionIdValue = values[6] as Long?

            ChatScreenState(
                sessions = sessionsValue,
                selectedSessionId = selectedSessionIdValue ?: sessionsValue.firstOrNull()?.id,
                messages = messagesValue.map { message ->
                    message.copy(
                        isStreaming = isSendingValue &&
                            message.role == com.fcm.nanochat.model.ChatRole.ASSISTANT &&
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

