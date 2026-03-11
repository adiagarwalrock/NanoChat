package com.fcm.nanochat.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
    val huggingFaceToken: String = ""
)

class AppPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val secretStore by lazy { createSecretStore(appContext) }

    val settings: Flow<SettingsSnapshot> =
        appContext.dataStore.data.map { preferences ->
            SettingsSnapshot(
                inferenceMode = preferences[Keys.inferenceMode]?.let(InferenceMode::valueOf)
                    ?: InferenceMode.REMOTE,
                baseUrl = preferences[Keys.baseUrl].orEmpty(),
                modelName = preferences[Keys.modelName].orEmpty(),
                apiKey = secretStore.getString(SecretKeys.apiKey, "").orEmpty(),
                huggingFaceToken = secretStore.getString(SecretKeys.huggingFaceToken, "").orEmpty()
            )
        }

    suspend fun updateInferenceMode(mode: InferenceMode) {
        appContext.dataStore.edit { it[Keys.inferenceMode] = mode.name }
    }

    suspend fun updateBaseUrl(value: String) {
        appContext.dataStore.edit { it[Keys.baseUrl] = value.trim() }
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

    private object Keys {
        val inferenceMode: Preferences.Key<String> = stringPreferencesKey("inference_mode")
        val baseUrl: Preferences.Key<String> = stringPreferencesKey("base_url")
        val modelName: Preferences.Key<String> = stringPreferencesKey("model_name")
    }

    private object SecretKeys {
        const val apiKey = "api_key"
        const val huggingFaceToken = "hugging_face_token"
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
