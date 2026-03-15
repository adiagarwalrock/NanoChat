package com.fcm.nanochat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fcm.nanochat.data.AcceleratorPreference
import com.fcm.nanochat.data.AppPreferences
import com.fcm.nanochat.data.ThinkingEffort
import com.fcm.nanochat.data.network.HuggingFaceWhoAmIParser
import com.fcm.nanochat.data.repository.ChatRepository
import com.fcm.nanochat.inference.GeminiNanoStatus
import com.fcm.nanochat.model.GeminiNanoStatusUi
import com.fcm.nanochat.model.HuggingFaceAccountUi
import com.fcm.nanochat.model.SettingsScreenState
import com.fcm.nanochat.model.UsageStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val repository: ChatRepository,
    private val httpClient: OkHttpClient
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsScreenState())
    val uiState: StateFlow<SettingsScreenState> = _uiState.asStateFlow()
    private var tokenValidationJob: Job? = null
    private var lastValidatedToken: String = ""

    init {
        viewModelScope.launch {
            preferences.settings.collect { snapshot ->
                val token = snapshot.huggingFaceToken.trim()
                val cachedAccount = snapshot.huggingFaceAccountJson.takeIf { it.isNotBlank() }
                    ?.let { runCatching { HuggingFaceWhoAmIParser.parseAccount(it) }.getOrNull() }
                _uiState.update { current ->
                    current.copy(
                        baseUrl = snapshot.baseUrl,
                        modelName = snapshot.modelName,
                        apiKey = snapshot.apiKey,
                        huggingFaceToken = snapshot.huggingFaceToken,
                        temperature = snapshot.temperature,
                        topP = snapshot.topP,
                        contextLength = snapshot.contextLength,
                        thinkingEffort = snapshot.thinkingEffort,
                        acceleratorPreference = snapshot.acceleratorPreference,
                        geminiStatus = _uiState.value.geminiStatus.copy(
                            lastKnownModelSizeBytes = snapshot.geminiNanoModelSizeBytes
                        ),
                        huggingFaceAccount = when {
                            token.isBlank() -> HuggingFaceAccountUi()
                            cachedAccount != null -> cachedAccount.toUi(
                                isValid = true,
                                message = "Connected"
                            )

                            else -> current.huggingFaceAccount
                        },
                        saveNotice = current.saveNotice,
                        clearNotice = current.clearNotice
                    )
                }

                if (token.isNotBlank() && token != lastValidatedToken) {
                    scheduleHuggingFaceValidation(token = token, withDelay = false)
                }
            }
        }

        refreshStats()
        refreshGeminiStatus()
    }

    fun updateBaseUrl(value: String) {
        _uiState.update { it.copy(baseUrl = value, saveNotice = null) }
    }

    fun updateModelName(value: String) {
        _uiState.update { it.copy(modelName = value, saveNotice = null) }
    }

    fun updateApiKey(value: String) {
        _uiState.update { it.copy(apiKey = value, saveNotice = null) }
    }

    fun updateHuggingFaceToken(value: String) {
        _uiState.update {
            it.copy(
                huggingFaceToken = value,
                saveNotice = null,
                huggingFaceAccount = HuggingFaceAccountUi()
            )
        }

        val trimmed = value.trim()
        if (trimmed.isBlank()) {
            tokenValidationJob?.cancel()
            lastValidatedToken = ""
            viewModelScope.launch { preferences.updateHuggingFaceAccount("") }
            return
        }

        scheduleHuggingFaceValidation(token = trimmed, withDelay = true)
    }

    fun updateTemperature(value: Double) {
        val clamped = value.coerceIn(0.0, 2.0)
        _uiState.update { it.copy(temperature = clamped, saveNotice = null) }
    }

    fun updateTopP(value: Double) {
        val clamped = value.coerceIn(0.0, 1.0)
        _uiState.update { it.copy(topP = clamped, saveNotice = null) }
    }

    fun updateContextLength(value: Int) {
        val clamped = value.coerceIn(512, 32768)
        _uiState.update { it.copy(contextLength = clamped, saveNotice = null) }
    }

    fun updateThinkingEffort(value: ThinkingEffort) {
        _uiState.update { it.copy(thinkingEffort = value, saveNotice = null) }
        viewModelScope.launch { preferences.updateThinkingEffort(value) }
    }

    fun updateAcceleratorPreference(value: AcceleratorPreference) {
        _uiState.update { it.copy(acceleratorPreference = value, saveNotice = null) }
        viewModelScope.launch { preferences.updateAcceleratorPreference(value) }
    }

    fun save() {
        viewModelScope.launch {
            val current = _uiState.value
            preferences.updateModelSettings(
                baseUrl = current.baseUrl,
                modelName = current.modelName,
                temperature = current.temperature,
                topP = current.topP,
                contextLength = current.contextLength
            )
            preferences.updateSecrets(
                apiKey = current.apiKey,
                huggingFaceToken = current.huggingFaceToken
            )
            preferences.updateThinkingEffort(current.thinkingEffort)
            preferences.updateAcceleratorPreference(current.acceleratorPreference)
            _uiState.update { it.copy(saveNotice = "Settings saved.", clearNotice = null) }
        }
    }

    fun validateHuggingFaceToken() {
        scheduleHuggingFaceValidation(
            token = _uiState.value.huggingFaceToken.trim(),
            withDelay = false
        )
    }

    fun refreshStats() {
        viewModelScope.launch {
            val stats = runCatching { repository.usageStats() }.getOrElse { UsageStats() }
            _uiState.update { it.copy(stats = stats) }
        }
    }

    fun refreshGeminiStatus() {
        viewModelScope.launch {
            val status = runCatching { repository.geminiNanoStatus() }.getOrElse { error ->
                GeminiNanoStatus(
                    supported = false,
                    downloaded = false,
                    downloading = false,
                    downloadable = false,
                    bytesDownloaded = null,
                    bytesToDownload = null,
                    message = error.message ?: "Unable to check Gemini Nano status."
                )
            }

            _uiState.update { current ->
                current.copy(
                    geminiStatus = mapStatus(
                        status,
                        current.geminiStatus.lastKnownModelSizeBytes
                    )
                )
            }
        }
    }

    fun downloadGeminiNano() {
        viewModelScope.launch {
            repository.downloadGeminiNano().collect { status ->
                if (status.downloaded && status.bytesToDownload != null) {
                    repository.saveGeminiNanoModelSize(status.bytesToDownload)
                }
                _uiState.update { current ->
                    val lastKnown = when {
                        status.bytesToDownload != null -> status.bytesToDownload
                        status.bytesDownloaded != null && status.bytesDownloaded > 0 -> status.bytesDownloaded
                        else -> current.geminiStatus.lastKnownModelSizeBytes
                    }
                    current.copy(geminiStatus = mapStatus(status, lastKnown))
                }
            }
        }
    }

    private fun mapStatus(status: GeminiNanoStatus, lastKnownSize: Long): GeminiNanoStatusUi {
        return GeminiNanoStatusUi(
            supported = status.supported,
            downloaded = status.downloaded,
            downloading = status.downloading,
            downloadable = status.downloadable,
            bytesDownloaded = status.bytesDownloaded,
            bytesToDownload = status.bytesToDownload,
            lastKnownModelSizeBytes = lastKnownSize,
            message = status.message
        )
    }

    private fun com.fcm.nanochat.data.network.HuggingFaceWhoAmI.toUi(
        isValid: Boolean,
        message: String?
    ): HuggingFaceAccountUi {
        return HuggingFaceAccountUi(
            isValidating = false,
            isValid = isValid,
            username = name,
            fullName = fullName,
            email = email,
            emailVerified = emailVerified,
            avatarUrl = avatarUrl,
            profileUrl = profileUrl,
            isPro = isPro,
            tokenName = tokenName,
            tokenRole = tokenRole,
            message = message
        )
    }

    private fun scheduleHuggingFaceValidation(token: String, withDelay: Boolean) {
        tokenValidationJob?.cancel()
        if (token.isBlank()) {
            _uiState.update { it.copy(huggingFaceAccount = HuggingFaceAccountUi()) }
            return
        }

        tokenValidationJob = viewModelScope.launch {
            if (withDelay) {
                delay(600)
            }

            _uiState.update { current ->
                if (current.huggingFaceToken.trim() != token) {
                    current
                } else {
                    current.copy(
                        huggingFaceAccount = current.huggingFaceAccount.copy(
                            isValidating = true,
                            isValid = false,
                            message = null
                        )
                    )
                }
            }

            val accountState = fetchHuggingFaceAccount(token)

            _uiState.update { current ->
                if (current.huggingFaceToken.trim() != token) current
                else current.copy(huggingFaceAccount = accountState)
            }

            if (_uiState.value.huggingFaceToken.trim() == token) {
                lastValidatedToken = token
            }
        }
    }

    private suspend fun fetchHuggingFaceAccount(token: String): HuggingFaceAccountUi {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(HUGGING_FACE_WHOAMI_URL)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .get()
                .build()

            try {
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body.string()
                    if (!response.isSuccessful) {
                        val serverMessage = HuggingFaceWhoAmIParser.parseError(body)
                        val message = when {
                            response.code == 401 -> "Token is invalid or expired."
                            !serverMessage.isNullOrBlank() -> serverMessage
                            else -> "Unable to validate Hugging Face token (HTTP ${response.code})."
                        }
                        return@withContext invalidHuggingFaceAccount(message)
                    }

                    val account = HuggingFaceWhoAmIParser.parseAccount(body)
                    preferences.updateHuggingFaceAccount(body)
                    return@withContext account.toUi(isValid = true, message = "Connected")
                }
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    throw error
                }

                return@withContext invalidHuggingFaceAccount(
                    error.message ?: "Failed to validate Hugging Face token."
                )
            }
        }
    }

    private fun invalidHuggingFaceAccount(message: String): HuggingFaceAccountUi {
        return HuggingFaceAccountUi(
            isValidating = false,
            isValid = false,
            username = null,
            fullName = null,
            email = null,
            emailVerified = false,
            avatarUrl = null,
            profileUrl = null,
            isPro = false,
            tokenName = null,
            tokenRole = null,
            message = message
        )
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            val wasCleared = try {
                repository.clearAllData()
                repository.createSession()
                true
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                false
            }

            refreshStats()
            _uiState.update {
                val clearNotice = if (wasCleared) {
                    "History cleared."
                } else {
                    "Unable to clear history. Try again."
                }
                it.copy(clearNotice = clearNotice, saveNotice = null)
            }
        }
    }
}

private const val HUGGING_FACE_WHOAMI_URL = "https://huggingface.co/api/whoami-v2"
