package com.fcm.nanochat.inference

import com.fcm.nanochat.data.SettingsSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalInferenceClientMultimodalTest {
    @Test
    fun `capabilities reports text only support`() = runTest {
        val client = LocalInferenceClient()

        val capabilities = client.capabilities(SettingsSnapshot())

        assertTrue(capabilities.textGeneration.supported)
        assertTrue(capabilities.streaming.supported)
        assertFalse(capabilities.visionUnderstanding.supported)
        assertFalse(capabilities.audioTranscription.supported)
    }

    @Test
    fun `streamChat rejects image attachments with unsupported modality`() = runTest {
        val client = LocalInferenceClient()
        val request = InferenceRequest(
            history = emptyList(),
            prompt = "Describe image",
            settings = SettingsSnapshot(),
            imageAttachment = InferenceImageAttachment(
                relativePath = "images/photo.jpg",
                mimeType = "image/jpeg"
            )
        )

        val error = runCatching {
            client.streamChat(request).first()
        }.exceptionOrNull()

        assertTrue(error is InferenceException.UnsupportedModality)
    }
}
