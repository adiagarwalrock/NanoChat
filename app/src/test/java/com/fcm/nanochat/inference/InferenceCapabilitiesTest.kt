package com.fcm.nanochat.inference

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InferenceCapabilitiesTest {
    @Test
    fun `defaultTextOnly marks text and streaming supported`() {
        val capabilities = InferenceCapabilities.defaultTextOnly()

        assertTrue(capabilities.textGeneration.supported)
        assertTrue(capabilities.streaming.supported)
    }

    @Test
    fun `defaultTextOnly marks vision and audio unsupported`() {
        val capabilities = InferenceCapabilities.defaultTextOnly("unsupported")

        assertFalse(capabilities.visionUnderstanding.supported)
        assertFalse(capabilities.audioTranscription.supported)
    }
}
