package com.fcm.nanochat.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
    val activeLocalModelId: String = "",
    val baseUrl: String = "",
    val modelName: String = "",
    val apiKey: String = "",
    val huggingFaceToken: String = "",
    val temperature: Double = 0.7,
    val topP: Double = 0.9,
    val contextLength: Int = 4096,
    val geminiNanoModelSizeBytes: Long = 0,
    val huggingFaceAccountJson: String = "",
    val thinkingEffort: ThinkingEffort = ThinkingEffort.MEDIUM,
    val acceleratorPreference: AcceleratorPreference = AcceleratorPreference.AUTO
)

enum class ThinkingEffort {
    NONE,
    LOW,
    MEDIUM,
    HIGH
}

enum class AcceleratorPreference {
    AUTO,
    CPU,
    GPU,
    NNAPI
}

data class AllowlistCacheSnapshot(
    val version: String = "",
    val json: String = "",
    val refreshedAtEpochMs: Long = 0L
)

class AppPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val secretStore: SharedPreferences? by
    lazy(LazyThreadSafetyMode.NONE) {
        runCatching { createSecretStore(appContext) }.getOrNull()
    }

    val settings: Flow<SettingsSnapshot> =
        appContext.dataStore.data.map { preferences ->
            SettingsSnapshot(
                inferenceMode = parseInferenceMode(preferences[Keys.inferenceMode]),
                activeLocalModelId = preferences[Keys.activeLocalModelId].orEmpty(),
                baseUrl = preferences[Keys.baseUrl].orEmpty(),
                modelName = preferences[Keys.modelName].orEmpty(),
                apiKey = readSecret(SecretKeys.apiKey),
                huggingFaceToken = readSecret(SecretKeys.huggingFaceToken),
                temperature = preferences[Keys.temperature] ?: DEFAULT_TEMPERATURE,
                topP = preferences[Keys.topP] ?: DEFAULT_TOP_P,
                contextLength = preferences[Keys.contextLength] ?: DEFAULT_CONTEXT_LENGTH,
                geminiNanoModelSizeBytes = preferences[Keys.geminiNanoModelSizeBytes] ?: 0,
                huggingFaceAccountJson = preferences[Keys.huggingFaceAccountJson] ?: "",
                thinkingEffort = parseThinkingEffort(preferences[Keys.thinkingEffort]),
                acceleratorPreference =
                    parseAccelerator(preferences[Keys.acceleratorPreference])
            )
        }

    val allowlistCache: Flow<AllowlistCacheSnapshot> =
        appContext.dataStore.data.map { preferences ->
            AllowlistCacheSnapshot(
                version = preferences[Keys.allowlistVersion].orEmpty(),
                json = preferences[Keys.allowlistJson].orEmpty(),
                refreshedAtEpochMs = preferences[Keys.allowlistLastRefreshEpochMs] ?: 0L
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

    suspend fun updateActiveLocalModelId(value: String?) {
        appContext.dataStore.edit { preferences ->
            val normalized = value?.trim().orEmpty()
            preferences[Keys.activeLocalModelId] = normalized
        }
    }

    suspend fun updateModelName(value: String) {
        appContext.dataStore.edit { it[Keys.modelName] = value.trim() }
    }

    fun updateApiKey(value: String) {
        writeSecretValue(SecretKeys.apiKey, value.trim())
    }

    fun updateHuggingFaceToken(value: String) {
        writeSecretValue(SecretKeys.huggingFaceToken, value.trim())
    }

    fun updateSecrets(apiKey: String, huggingFaceToken: String) {
        runCatching {
            secretStore
                ?.edit()
                ?.putString(SecretKeys.apiKey, apiKey.trim())
                ?.putString(SecretKeys.huggingFaceToken, huggingFaceToken.trim())
                ?.apply()
        }
    }

    suspend fun updateThinkingEffort(value: ThinkingEffort) {
        appContext.dataStore.edit { it[Keys.thinkingEffort] = value.name }
    }

    suspend fun updateAcceleratorPreference(value: AcceleratorPreference) {
        appContext.dataStore.edit { it[Keys.acceleratorPreference] = value.name }
    }

    suspend fun updateModelSettings(
        baseUrl: String,
        modelName: String,
        temperature: Double,
        topP: Double,
        contextLength: Int
    ) {
        appContext.dataStore.edit { preferences ->
            preferences[Keys.baseUrl] = normalizeBaseUrl(baseUrl)
            preferences[Keys.modelName] = modelName.trim()
            preferences[Keys.temperature] = temperature.coerceIn(0.0, 2.0)
            preferences[Keys.topP] = topP.coerceIn(0.0, 1.0)
            preferences[Keys.contextLength] = contextLength.coerceIn(512, 32768)
        }
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

    suspend fun updateHuggingFaceAccount(json: String) {
        appContext.dataStore.edit { it[Keys.huggingFaceAccountJson] = json }
    }

    suspend fun updateAllowlistCache(
        version: String,
        json: String,
        refreshedAtEpochMs: Long = System.currentTimeMillis()
    ) {
        appContext.dataStore.edit { preferences ->
            preferences[Keys.allowlistVersion] = version.trim()
            preferences[Keys.allowlistJson] = json
            preferences[Keys.allowlistLastRefreshEpochMs] = refreshedAtEpochMs
        }
    }

    suspend fun updateGeminiNanoModelSize(bytes: Long) {
        if (bytes <= 0) return
        appContext.dataStore.edit { it[Keys.geminiNanoModelSizeBytes] = bytes }
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
        appContext.dataStore.edit { preferences -> preferences[Keys.pinnedSessionIds] = emptySet() }
    }

    private fun parseInferenceMode(raw: String?) =
        raw?.let { runCatching { InferenceMode.valueOf(it) }.getOrNull() }
            ?: InferenceMode.REMOTE

    private fun parseThinkingEffort(raw: String?) =
        raw?.let { runCatching { ThinkingEffort.valueOf(it) }.getOrNull() }
            ?: ThinkingEffort.MEDIUM

    private fun parseAccelerator(raw: String?) =
        raw?.let { runCatching { AcceleratorPreference.valueOf(it) }.getOrNull() }
            ?: AcceleratorPreference.AUTO

    private fun normalizeBaseUrl(raw: String): String {
        return raw.trim().trimEnd('/')
    }

    private fun readSecret(key: String): String {
        return runCatching { secretStore?.getString(key, "").orEmpty() }.getOrDefault("")
    }

    private fun writeSecretValue(key: String, value: String) {
        runCatching { secretStore?.edit()?.putString(key, value)?.apply() }
    }

    private object Keys {
        val inferenceMode: Preferences.Key<String> = stringPreferencesKey("inference_mode")
        val activeLocalModelId: Preferences.Key<String> =
            stringPreferencesKey("active_local_model_id")
        val baseUrl: Preferences.Key<String> = stringPreferencesKey("base_url")
        val modelName: Preferences.Key<String> = stringPreferencesKey("model_name")
        val pinnedSessionIds: Preferences.Key<Set<String>> =
            stringSetPreferencesKey("pinned_session_ids")
        val temperature: Preferences.Key<Double> = doublePreferencesKey("temperature")
        val topP: Preferences.Key<Double> = doublePreferencesKey("top_p")
        val contextLength: Preferences.Key<Int> = intPreferencesKey("context_length")
        val geminiNanoModelSizeBytes: Preferences.Key<Long> =
            longPreferencesKey("gemini_nano_model_size_bytes")
        val huggingFaceAccountJson: Preferences.Key<String> =
            stringPreferencesKey("hugging_face_account_json")
        val thinkingEffort: Preferences.Key<String> = stringPreferencesKey("thinking_effort")
        val acceleratorPreference: Preferences.Key<String> =
            stringPreferencesKey("accelerator_preference")
        val allowlistVersion: Preferences.Key<String> = stringPreferencesKey("allowlist_version")
        val allowlistJson: Preferences.Key<String> = stringPreferencesKey("allowlist_json")
        val allowlistLastRefreshEpochMs: Preferences.Key<Long> =
            longPreferencesKey("allowlist_last_refresh_epoch_ms")
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

@Suppress("DEPRECATION")
private fun createSecretStore(context: Context): SharedPreferences {
    val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()

    return EncryptedSharedPreferences.create(
        context,
        "nanochat_secure_preferences",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}
