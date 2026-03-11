package com.fcm.nanochat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fcm.nanochat.data.AppPreferences
import com.fcm.nanochat.data.repository.ChatRepository
import com.fcm.nanochat.model.SettingsScreenState
import com.fcm.nanochat.model.UsageStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferences: AppPreferences,
    private val repository: ChatRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsScreenState())
    val uiState: StateFlow<SettingsScreenState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.settings.collect { snapshot ->
                _uiState.update { current ->
                    current.copy(
                        baseUrl = snapshot.baseUrl,
                        modelName = snapshot.modelName,
                        apiKey = snapshot.apiKey,
                        huggingFaceToken = snapshot.huggingFaceToken,
                        temperature = snapshot.temperature,
                        topP = snapshot.topP,
                        contextLength = snapshot.contextLength,
                        saveNotice = current.saveNotice,
                        clearNotice = current.clearNotice
                    )
                }
            }
        }

        refreshStats()
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
        _uiState.update { it.copy(huggingFaceToken = value, saveNotice = null) }
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

    fun save() {
        viewModelScope.launch {
            val current = _uiState.value
            preferences.updateBaseUrl(current.baseUrl)
            preferences.updateModelName(current.modelName)
            preferences.updateApiKey(current.apiKey)
            preferences.updateHuggingFaceToken(current.huggingFaceToken)
            preferences.updateTemperature(current.temperature)
            preferences.updateTopP(current.topP)
            preferences.updateContextLength(current.contextLength)
            _uiState.update { it.copy(saveNotice = "Settings saved.", clearNotice = null) }
        }
    }

    fun refreshStats() {
        viewModelScope.launch {
            val stats = runCatching { repository.usageStats() }.getOrElse { UsageStats() }
            _uiState.update { it.copy(stats = stats) }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            runCatching {
                repository.clearAllData()
                repository.createSession()
            }
            refreshStats()
            _uiState.update { it.copy(clearNotice = "History cleared.", saveNotice = null) }
        }
    }
}

class SettingsViewModelFactory(
    private val preferences: AppPreferences,
    private val repository: ChatRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(preferences, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
