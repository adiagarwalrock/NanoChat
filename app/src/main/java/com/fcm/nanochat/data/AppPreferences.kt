package com.fcm.nanochat.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.fcm.nanochat.inference.InferenceMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "nanochat_preferences")

data class SettingsSnapshot(
    val inferenceMode: InferenceMode = InferenceMode.REMOTE,
    val baseUrl: String = "",
    val modelName: String = "",
    val apiKey: String = "",
    val huggingFaceToken: String = "",
    val temperature: Double = AppPreferences.DEFAULT_TEMPERATURE,
    val topP: Double = AppPreferences.DEFAULT_TOP_P,
    val contextLength: Int = AppPreferences.DEFAULT_CONTEXT_LENGTH
)

class AppPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val secretStore by lazy { createSecretStore(appContext) }

    val settings: Flow<SettingsSnapshot> =
        appContext.dataStore.data.map { preferences ->
            SettingsSnapshot(
                inferenceMode = parseInferenceMode(preferences[Keys.inferenceMode]),
                baseUrl = preferences[Keys.baseUrl].orEmpty(),
                modelName = preferences[Keys.modelName].orEmpty(),
                apiKey = secretStore.getString(SecretKeys.apiKey, "").orEmpty(),
                huggingFaceToken = secretStore.getString(SecretKeys.huggingFaceToken, "").orEmpty(),
                temperature = preferences[Keys.temperature] ?: DEFAULT_TEMPERATURE,
                topP = preferences[Keys.topP] ?: DEFAULT_TOP_P,
                contextLength = preferences[Keys.contextLength] ?: DEFAULT_CONTEXT_LENGTH
            )
        }

    val pinnedSessionIds: Flow<Set<Long>> =
        appContext.dataStore.data.map { preferences ->
            preferences[Keys.pinnedSessionIds]
                .orEmpty()
                .mapNotNull(String::toLongOrNull)
                .toSet()
        }

    suspend fun updateInferenceMode(mode: InferenceMode) {
        appContext.dataStore.edit { it[Keys.inferenceMode] = mode.name }
    }

    suspend fun updateBaseUrl(value: String) {
        appContext.dataStore.edit { it[Keys.baseUrl] = normalizeBaseUrl(value) }
    }

    suspend fun updateModelName(value: String) {
        appContext.dataStore.edit { it[Keys.modelName] = value.trim() }
    }

    suspend fun updateApiKey(value: String) {
        secretStore.edit().putString(SecretKeys.apiKey, value.trim()).apply()
    }

    suspend fun updateHuggingFaceToken(value: String) {
        secretStore.edit().putString(SecretKeys.huggingFaceToken, value.trim()).apply()
    }

    suspend fun updateTemperature(value: Double) {
        appContext.dataStore.edit { it[Keys.temperature] = value }
    }

    suspend fun updateTopP(value: Double) {
        appContext.dataStore.edit { it[Keys.topP] = value }
    }

    suspend fun updateContextLength(value: Int) {
        val clamped = value.coerceIn(512, 32768)
        appContext.dataStore.edit { it[Keys.contextLength] = clamped }
    }

    suspend fun setSessionPinned(sessionId: Long, pinned: Boolean) {
        appContext.dataStore.edit { preferences ->
            val current = preferences[Keys.pinnedSessionIds].orEmpty().toMutableSet()
            val encoded = sessionId.toString()
            if (pinned) current.add(encoded) else current.remove(encoded)
            preferences[Keys.pinnedSessionIds] = current
        }
    }

    suspend fun clearPinnedSessions() {
        appContext.dataStore.edit { preferences ->
            preferences[Keys.pinnedSessionIds] = emptySet()
        }
    }

    private fun parseInferenceMode(raw: String?): InferenceMode {
        return raw?.let {
            runCatching { InferenceMode.valueOf(it) }
                .getOrDefault(InferenceMode.REMOTE)
        } ?: InferenceMode.REMOTE
    }

    private fun normalizeBaseUrl(raw: String): String {
        return raw.trim().trimEnd('/')
    }

    private object Keys {
        val inferenceMode: Preferences.Key<String> = stringPreferencesKey("inference_mode")
        val baseUrl: Preferences.Key<String> = stringPreferencesKey("base_url")
        val modelName: Preferences.Key<String> = stringPreferencesKey("model_name")
        val pinnedSessionIds: Preferences.Key<Set<String>> = stringSetPreferencesKey("pinned_session_ids")
        val temperature: Preferences.Key<Double> = doublePreferencesKey("temperature")
        val topP: Preferences.Key<Double> = doublePreferencesKey("top_p")
        val contextLength: Preferences.Key<Int> = intPreferencesKey("context_length")
    }

    private object SecretKeys {
        const val apiKey: String = "api_key"
        const val huggingFaceToken: String = "hugging_face_token"
    }

    companion object {
        const val DEFAULT_TEMPERATURE = 0.7
        const val DEFAULT_TOP_P = 0.9
        const val DEFAULT_CONTEXT_LENGTH = 4096
    }
}

private fun createSecretStore(context: Context): SharedPreferences {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    return EncryptedSharedPreferences.create(
        context,
        "nanochat_secure_preferences",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}
