package com.fcm.nanochat

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fcm.nanochat.data.SettingsSnapshot
import com.fcm.nanochat.data.media.ChatMediaStore
import com.fcm.nanochat.inference.AudioTranscriptionRequest
import com.fcm.nanochat.inference.InferenceAudioAttachment
import com.fcm.nanochat.inference.InferenceException
import com.fcm.nanochat.inference.RemoteInferenceClient
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteInferenceClientMultimodalTest {
    @Test
    fun capabilities_reportsVisionAndAudioSupport() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val client = RemoteInferenceClient(OkHttpClient(), ChatMediaStore(context))

        val capabilities = client.capabilities(SettingsSnapshot())

        assertTrue(capabilities.textGeneration.supported)
        assertTrue(capabilities.visionUnderstanding.supported)
        assertTrue(capabilities.audioTranscription.supported)
        assertTrue(capabilities.streaming.supported)
    }

    @Test
    fun transcribeAudio_missingFileMapsToMissingFileError() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val client = RemoteInferenceClient(OkHttpClient(), ChatMediaStore(context))
        val request = AudioTranscriptionRequest(
            settings = SettingsSnapshot(
                baseUrl = "https://api.openai.com/v1",
                modelName = "gpt-4.1-mini",
                apiKey = "test-key"
            ),
            audioAttachment = InferenceAudioAttachment(
                relativePath = "audio/does_not_exist.m4a",
                mimeType = "audio/mp4",
                displayName = "does_not_exist.m4a"
            )
        )

        val error = runCatching { client.transcribeAudio(request) }.exceptionOrNull()
        assertTrue(error is InferenceException.MissingFile)
        assertFalse(error?.message.isNullOrBlank())
    }
}
