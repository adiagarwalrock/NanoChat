package com.fcm.nanochat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fcm.nanochat.data.AppPreferences
import com.fcm.nanochat.model.SettingsScreenState
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferences: AppPreferences
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
                        saveNotice = current.saveNotice
                    )
                }
            }
        }
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

    fun save() {
        viewModelScope.launch {
            val current = _uiState.value
            preferences.updateBaseUrl(current.baseUrl)
            preferences.updateModelName(current.modelName)
            preferences.updateApiKey(current.apiKey)
            preferences.updateHuggingFaceToken(current.huggingFaceToken)
            _uiState.update { it.copy(saveNotice = "Settings saved.") }
        }
    }
}

class SettingsViewModelFactory(
    private val preferences: AppPreferences
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(preferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
