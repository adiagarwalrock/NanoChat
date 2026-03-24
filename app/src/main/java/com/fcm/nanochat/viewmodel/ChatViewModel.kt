package com.fcm.nanochat.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fcm.nanochat.data.SettingsSnapshot
import com.fcm.nanochat.data.ThinkingEffort
import com.fcm.nanochat.data.db.ChatMessagePartState
import com.fcm.nanochat.data.db.ChatMessagePartType
import com.fcm.nanochat.data.repository.ChatRepository
import com.fcm.nanochat.data.repository.LocalModelCapabilities
import com.fcm.nanochat.data.repository.MessagePartWrite
import com.fcm.nanochat.inference.BackendAvailability
import com.fcm.nanochat.inference.GeneratedTextSanitizer
import com.fcm.nanochat.inference.InferenceCapabilities
import com.fcm.nanochat.inference.InferenceException
import com.fcm.nanochat.inference.InferenceMode
import com.fcm.nanochat.model.ChatMessage
import com.fcm.nanochat.model.ChatScreenState
import com.fcm.nanochat.model.ChatSession
import com.fcm.nanochat.model.ComposerAttachment
import com.fcm.nanochat.model.ComposerAttachmentType
import com.fcm.nanochat.models.registry.ActiveModelStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(private val repository: ChatRepository) : ViewModel() {
    private val defaultLocalModelCapabilities =
            LocalModelCapabilities(modelId = null, supportsThinking = false, promptFamily = null)
    private val generationMutex = Mutex()
    private val selectedSessionId = MutableStateFlow<Long?>(null)
    private val draft = MutableStateFlow("")
    private val draftAttachment = MutableStateFlow<ComposerAttachment?>(null)
    private val isPreparingAttachment = MutableStateFlow(false)
    private val backendCapabilities =
        MutableStateFlow(InferenceCapabilities.defaultTextOnly())
    private val notice = MutableStateFlow<String?>(null)
    private val isSending = MutableStateFlow(false)
    private var sendJob: Job? = null
    private var activeRequestId: Long = 0
    private var activeAssistantMessageId: Long? = null
    private var activeAssistantPreview: String = ""
    private val cancelledRequestIds = mutableSetOf<Long>()
    private var lastUserRequest: PendingUserRequest? = null

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
                selected?.takeIf { id -> sessionList.any { it.id == id } }
                    ?: sessionList.firstOrNull()?.id
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

    private val orderedSessions =
        combine(sessions, pinnedSessionIds) { sessionList, pinnedIds ->
            sessionList
                .map { session -> session.copy(isPinned = pinnedIds.contains(session.id)) }
                .sortedWith(
                    compareByDescending<ChatSession> { it.isPinned }
                        .thenByDescending { it.updatedAt }
                )
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val sessionState =
        combine(orderedSessions, messages, activeSessionId) { ordered,
                                                              messageList,
                                                              selectedId ->
                SessionUiState(
                    sessions = ordered,
                        selectedSessionId = selectedId,
                    messages = messageList
                )
            }

    private val sessionComposerState =
        combine(sessionState, draft, draftAttachment, isPreparingAttachment) { sessionUi,
                                                                               draftValue,
                                                                               attachment,
                                                                               isPreparingAttachmentValue ->
            SessionComposerState(
                sessionUi = sessionUi,
                draft = draftValue,
                draftAttachment = attachment,
                isPreparingAttachment = isPreparingAttachmentValue
            )
        }

    private val baseUiState: StateFlow<ChatScreenState> =
        combine(
            sessionComposerState,
            settings,
            activeLocalModelStatus,
            isSending,
            backendCapabilities
        ) { sessionComposer,
            settingsValue,
            activeLocalModel,
            isSendingValue,
            capabilities ->
            ChatScreenState(
                sessions = sessionComposer.sessionUi.sessions,
                selectedSessionId = sessionComposer.sessionUi.selectedSessionId,
                messages = sessionComposer.sessionUi.messages,
                draft = sessionComposer.draft,
                draftAttachment = sessionComposer.draftAttachment,
                isPreparingAttachment = sessionComposer.isPreparingAttachment,
                inferenceMode = settingsValue.inferenceMode,
                capabilities = capabilities,
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
        settings
            .onEach { snapshot ->
                val capabilities = withContext(Dispatchers.IO) {
                    repository.backendCapabilities(snapshot.inferenceMode, snapshot)
                }
                backendCapabilities.value = capabilities
            }
            .launchIn(viewModelScope)

        viewModelScope.launch { selectedSessionId.value = repository.ensureSession() }
    }

    fun updateDraft(value: String) {
        draft.value = value
    }

    fun importImageAttachment(uri: Uri) {
        importAttachment { repository.importImageAttachment(uri) }
    }

    fun importAudioAttachment(uri: Uri) {
        importAttachment { repository.importAudioAttachment(uri) }
    }

    fun importCapturedImage(tempAbsolutePath: String) {
        importAttachment { repository.importCapturedImage(tempAbsolutePath) }
    }

    fun removeDraftAttachment() {
        val attachment = draftAttachment.value ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.discardAttachment(attachment)
            }
            draftAttachment.value = null
        }
    }

    fun postNotice(message: String) {
        notice.value = message.takeIf { it.isNotBlank() }
    }

    fun createSession() {
        viewModelScope.launch {
            draftAttachment.value?.let { attachment ->
                withContext(Dispatchers.IO) {
                    repository.discardAttachment(attachment)
                }
            }
            selectedSessionId.value = repository.createSession()
            draft.value = ""
            draftAttachment.value = null
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
            val updatedSnapshot = withContext(Dispatchers.IO) { repository.settingsSnapshot() }

            if (mode == InferenceMode.DOWNLOADED) {
                val preparationError =
                    withContext(Dispatchers.IO) {
                        repository.prepareSelectedLocalModel(updatedSnapshot)
                    }
                if (!preparationError.isNullOrBlank() && shouldSurfaceNotice(preparationError)
                ) {
                    notice.value = preparationError
                    return@launch
                }
            }

            when (val availability =
                            withContext(Dispatchers.IO) {
                                repository.backendAvailability(mode, updatedSnapshot)
                            }
            ) {
                is BackendAvailability.Unavailable -> {
                    notice.value = availability.message.takeIf { shouldSurfaceNotice(it) }
                }
                BackendAvailability.Available -> notice.value = null
            }
        }
    }

    fun retryLastMessage() {
        val last = lastUserRequest ?: return
        draft.value = last.prompt
        draftAttachment.value = last.attachment
        sendMessage()
    }

    fun sendMessage() {
        val prompt = draft.value.trim()
        val attachment = draftAttachment.value
        val sessionId = activeSessionId.value ?: return
        if ((prompt.isBlank() && attachment == null) || isSending.value || isPreparingAttachment.value) {
            return
        }

        sendJob?.cancel()
        val requestId = ++activeRequestId
        cancelledRequestIds.remove(requestId)
        isSending.value = true
        notice.value = null
        lastUserRequest = PendingUserRequest(prompt = prompt, attachment = attachment)

        sendJob =
                viewModelScope.launch {
                    generationMutex.withLock {
                        executeGeneration(
                            requestId = requestId,
                            sessionId = sessionId,
                            prompt = prompt,
                            attachment = attachment
                        )
                    }
                }
    }

    private suspend fun executeGeneration(
        requestId: Long,
        sessionId: Long,
        prompt: String,
        attachment: ComposerAttachment?
    ) {
        var assistantMessageId: Long? = null
        val assembler = StreamingMessageAssembler()
        var watchdogJob: Job? = null
        var hasVisibleContent = false
        var watchdogTriggered = false

        try {
            val snapshot = settings.value
            val mode = snapshot.inferenceMode
            Log.d(TAG, "chat_generation_started requestId=$requestId mode=${mode.name}")

            if (!checkAvailability(mode, snapshot, attachment)) return

            val history = withContext(Dispatchers.IO) { repository.recentTurnsFor(mode, sessionId) }
            val (createdUserMessageId, assistantId) =
                withContext(Dispatchers.IO) {
                    val persistedPrompt = messageContentForPersistence(prompt, attachment)
                    val userId =
                        repository.saveUserMessage(
                            sessionId = sessionId,
                            content = persistedPrompt,
                            settings = snapshot,
                            parts = buildUserParts(attachment)
                        )
                    val assistantIdLocal =
                        repository.insertAssistantPlaceholder(sessionId, snapshot)
                    userId to assistantIdLocal
                }

            assistantMessageId = assistantId
            activeAssistantMessageId = assistantId
            activeAssistantPreview = ""
            draft.value = ""
            draftAttachment.value = null

            if (attachment?.type == ComposerAttachmentType.AUDIO) {
                val transcript =
                    withContext(Dispatchers.IO) {
                        repository.transcribeAudio(
                            mode = mode,
                            settings = snapshot,
                            attachment = attachment
                        )
                    }
                ensureRequestActive(requestId)
                withContext(Dispatchers.IO) {
                    repository.updateAssistantMessage(assistantId, transcript.transcript)
                    repository.replaceMessageParts(
                        messageId = assistantId,
                        parts = listOf(
                            MessagePartWrite(
                                partType = ChatMessagePartType.TRANSCRIPT,
                                displayName = null,
                                sourceMessageId = createdUserMessageId,
                                state = ChatMessagePartState.COMPLETED
                            )
                        )
                    )
                }
                return
            }

            val parentJob = currentCoroutineContext()[Job]
            watchdogJob =
                viewModelScope.launch {
                    delay(firstTokenWatchdogMs(mode))
                    if (!hasVisibleContent && isCurrentRequest(requestId)) {
                        watchdogTriggered = true
                        parentJob?.cancel(
                            GenerationWatchdogTimeout(
                                "No response arrived in time. Retry last, or reselect this model."
                            )
                        )
                    }
                }

            var lastPersistTimeMs = 0L
            var lastPersistedLength = 0
            var lastPersistedSnapshot = ""

            repository.streamResponse(
                mode = mode,
                history = history,
                prompt = prompt,
                settings = snapshot,
                imageAttachment = attachment,
                sessionId = sessionId
            ).collect { delta ->
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
                        repository.updateAssistantMessage(assistantId, filtered)
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
                    repository.updateAssistantMessage(assistantId, finalContent)
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
        snapshot: SettingsSnapshot,
        attachment: ComposerAttachment?
    ): Boolean {
        val capabilities = withContext(Dispatchers.IO) {
            repository.backendCapabilities(mode, snapshot)
        }
        backendCapabilities.value = capabilities

        if (attachment != null) {
            val unsupportedReason = when (attachment.type) {
                ComposerAttachmentType.IMAGE -> capabilities.visionUnderstanding
                    .takeIf { !it.supported }
                    ?.reasonIfUnsupported

                ComposerAttachmentType.AUDIO -> capabilities.audioTranscription
                    .takeIf { !it.supported }
                    ?.reasonIfUnsupported
            }
            if (!unsupportedReason.isNullOrBlank()) {
                notice.value = unsupportedReason.takeIf { shouldSurfaceNotice(it) }
                return false
            }
        }

        if (mode == InferenceMode.DOWNLOADED) {
            val error =
                withContext(Dispatchers.IO) {
                    repository.prepareSelectedLocalModel(snapshot)
                }
            if (!error.isNullOrBlank()) {
                notice.value = error.takeIf { shouldSurfaceNotice(it) }
                return false
            }
        }

        val availability =
            withContext(Dispatchers.IO) { repository.backendAvailability(mode, snapshot) }
        if (availability is BackendAvailability.Unavailable) {
            notice.value = availability.message.takeIf { shouldSurfaceNotice(it) }
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
                    shouldSurfaceNotice(it)
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
                message.takeIf { shouldSurfaceNotice(it) }
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

    private fun importAttachment(importer: suspend () -> ComposerAttachment) {
        if (isSending.value) return

        viewModelScope.launch {
            isPreparingAttachment.value = true
            notice.value = null
            try {
                val previousAttachment = draftAttachment.value
                val imported = withContext(Dispatchers.IO) { importer() }
                if (previousAttachment != null) {
                    withContext(Dispatchers.IO) {
                        repository.discardAttachment(previousAttachment)
                    }
                }
                draftAttachment.value = imported
            } catch (error: Throwable) {
                val message = userFacingError(error).ifBlank { "Unable to attach media." }
                notice.value = message.takeIf { shouldSurfaceNotice(it) }
            } finally {
                isPreparingAttachment.value = false
            }
        }
    }

    private fun buildUserParts(attachment: ComposerAttachment?): List<MessagePartWrite> {
        if (attachment == null) return emptyList()
        return when (attachment.type) {
            ComposerAttachmentType.IMAGE -> listOf(
                MessagePartWrite(
                    partType = ChatMessagePartType.IMAGE,
                    relativePath = attachment.relativePath,
                    mimeType = attachment.mimeType,
                    displayName = attachment.displayName,
                    sizeBytes = attachment.sizeBytes,
                    widthPx = attachment.widthPx,
                    heightPx = attachment.heightPx,
                    state = ChatMessagePartState.READY
                )
            )

            ComposerAttachmentType.AUDIO -> listOf(
                MessagePartWrite(
                    partType = ChatMessagePartType.AUDIO,
                    relativePath = attachment.relativePath,
                    mimeType = attachment.mimeType,
                    displayName = attachment.displayName,
                    sizeBytes = attachment.sizeBytes,
                    durationMs = attachment.durationMs,
                    state = ChatMessagePartState.READY
                )
            )
        }
    }

    private fun messageContentForPersistence(
        prompt: String,
        attachment: ComposerAttachment?
    ): String {
        if (attachment == null) return prompt
        return prompt
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
            is InferenceException.UnsupportedModality -> error.message.orEmpty()
            is InferenceException.MissingPermission -> error.message.orEmpty()
            is InferenceException.MissingFile -> error.message.orEmpty()
            is InferenceException.TranscriptionFailure -> error.message.orEmpty()
            is InferenceException.UploadFailure -> error.message.orEmpty()
            else -> error.message ?: "Inference failed."
        }
    }

    private fun shouldSurfaceNotice(message: String): Boolean {
        return message.isNotBlank()
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
        const val LOCAL_FIRST_TOKEN_WATCHDOG_MS = 65_000L
        const val REMOTE_FIRST_TOKEN_WATCHDOG_MS = 25_000L
        const val CANCELLATION_MESSAGE = "Generation cancelled."
        const val WATCHDOG_TIMEOUT_MESSAGE =
                "No response arrived in time. Retry last, or reselect the local model."
    }
}

private class GenerationWatchdogTimeout(message: String) : CancellationException(message)

private data class SessionUiState(
        val sessions: List<ChatSession>,
        val selectedSessionId: Long?,
        val messages: List<ChatMessage>
)

private data class SessionComposerState(
    val sessionUi: SessionUiState,
    val draft: String,
    val draftAttachment: ComposerAttachment?,
    val isPreparingAttachment: Boolean
)

private data class PendingUserRequest(
    val prompt: String,
    val attachment: ComposerAttachment?
)
