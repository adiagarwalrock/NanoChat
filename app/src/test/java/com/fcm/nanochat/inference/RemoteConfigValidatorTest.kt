package com.fcm.nanochat.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteConfigValidatorTest {
    @Test
    fun `missingFields returns empty list when all required values are present`() {
        val missing = RemoteConfigValidator.missingFields(
            baseUrl = "https://api.openai.com/v1",
            modelName = "gpt-4.1-mini",
            apiKey = "token"
        )

        assertTrue(missing.isEmpty())
    }

    @Test
    fun `missingFields returns all missing fields in stable order`() {
        val missing = RemoteConfigValidator.missingFields(
            baseUrl = "",
            modelName = "",
            apiKey = ""
        )

        assertEquals(
            listOf(
                RemoteConfigField.BASE_URL,
                RemoteConfigField.MODEL_NAME,
                RemoteConfigField.API_KEY
            ),
            missing
        )
    }
}