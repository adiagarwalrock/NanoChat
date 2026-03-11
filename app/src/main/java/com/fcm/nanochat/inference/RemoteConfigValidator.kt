package com.fcm.nanochat.inference

import com.fcm.nanochat.data.SettingsSnapshot

enum class RemoteConfigField(val displayName: String) {
    BASE_URL("Base URL"),
    MODEL_NAME("Model name"),
    API_KEY("API key")
}

object RemoteConfigValidator {
    fun missingFields(
        baseUrl: String,
        modelName: String,
        apiKey: String
    ): List<RemoteConfigField> {
        return buildList {
            if (baseUrl.isBlank()) add(RemoteConfigField.BASE_URL)
            if (modelName.isBlank()) add(RemoteConfigField.MODEL_NAME)
            if (apiKey.isBlank()) add(RemoteConfigField.API_KEY)
        }
    }

    fun missingFields(settings: SettingsSnapshot): List<RemoteConfigField> =
        missingFields(
            baseUrl = settings.baseUrl,
            modelName = settings.modelName,
            apiKey = settings.apiKey
        )
}