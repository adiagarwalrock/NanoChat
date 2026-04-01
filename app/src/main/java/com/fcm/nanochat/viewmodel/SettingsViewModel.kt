package com.fcm.nanochat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fcm.nanochat.data.AcceleratorPreference
import com.fcm.nanochat.data.AppPreferences
import com.fcm.nanochat.data.ThinkingEffort
import com.fcm.nanochat.data.repository.ChatRepository
import com.fcm.nanochat.inference.GeminiNanoStatus
import com.fcm.nanochat.model.GeminiNanoStatusUi
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

    init {
        viewModelScope.launch {
            var isFirstEmission = true
            preferences.settings.collect { snapshot ->
                _uiState.update { current ->
                    if (isFirstEmission) {
                        current.copy(
                            baseUrl = snapshot.baseUrl,
                            modelName = snapshot.modelName,
                            inferenceMode = snapshot.inferenceMode,
                            activeLocalModelId = snapshot.activeLocalModelId,
                            apiKey = snapshot.apiKey,
                            temperature = snapshot.temperature,
                            topP = snapshot.topP,
                            contextLength = snapshot.contextLength,
                            thinkingEffort = snapshot.thinkingEffort,
                            acceleratorPreference = snapshot.acceleratorPreference,
                            geminiStatus = current.geminiStatus.copy(
                                lastKnownModelSizeBytes = snapshot.geminiNanoModelSizeBytes
                            ),
                            saveNotice = current.saveNotice,
                            clearNotice = current.clearNotice
                        )
                    } else {
                        current.copy(
                            inferenceMode = snapshot.inferenceMode,
                            activeLocalModelId = snapshot.activeLocalModelId,
                            thinkingEffort = snapshot.thinkingEffort,
                            acceleratorPreference = snapshot.acceleratorPreference,
                            geminiStatus = current.geminiStatus.copy(
                                lastKnownModelSizeBytes = snapshot.geminiNanoModelSizeBytes
                            )
                        )
                    }
                }
                isFirstEmission = false
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
                apiKey = current.apiKey
            )
            preferences.updateThinkingEffort(current.thinkingEffort)
            preferences.updateAcceleratorPreference(current.acceleratorPreference)
            _uiState.update { it.copy(saveNotice = "Settings saved.", clearNotice = null) }
        }
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
