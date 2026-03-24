package com.fcm.nanochat

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fcm.nanochat.data.db.AppDatabase
import com.fcm.nanochat.data.db.ChatMessageEntity
import com.fcm.nanochat.data.db.ChatMessagePartEntity
import com.fcm.nanochat.data.db.ChatMessagePartState
import com.fcm.nanochat.data.db.ChatMessagePartType
import com.fcm.nanochat.data.db.ChatSessionEntity
import com.fcm.nanochat.inference.InferenceMode
import com.fcm.nanochat.model.ChatRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatMessagePartsPersistenceTest {
    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun messageParts_roundTripWithTextImageAudioAndTranscript() = runBlocking {
        val now = System.currentTimeMillis()
        val sessionId = database.sessionDao().insert(
            ChatSessionEntity(
                title = "Session",
                createdAt = now,
                updatedAt = now
            )
        )
        val messageId = database.messageDao().insert(
            ChatMessageEntity(
                sessionId = sessionId,
                role = ChatRole.USER,
                content = "Please transcribe this audio.",
                inferenceMode = InferenceMode.REMOTE,
                modelName = "gpt-4.1-mini",
                temperature = 0.7,
                topP = 0.9,
                contextLength = 4096,
                createdAt = now,
                updatedAt = now
            )
        )

        database.messageDao().insertParts(
            listOf(
                ChatMessagePartEntity(
                    messageId = messageId,
                    partIndex = 0,
                    partType = ChatMessagePartType.IMAGE,
                    relativePath = "images/picture.jpg",
                    mimeType = "image/jpeg",
                    displayName = "picture.jpg",
                    sizeBytes = 1234L,
                    widthPx = 640,
                    heightPx = 480,
                    durationMs = null,
                    sourceMessageId = null,
                    state = ChatMessagePartState.READY,
                    createdAt = now,
                    updatedAt = now
                ),
                ChatMessagePartEntity(
                    messageId = messageId,
                    partIndex = 1,
                    partType = ChatMessagePartType.AUDIO,
                    relativePath = "audio/voice.m4a",
                    mimeType = "audio/mp4",
                    displayName = "voice.m4a",
                    sizeBytes = 5678L,
                    widthPx = null,
                    heightPx = null,
                    durationMs = 5_000L,
                    sourceMessageId = null,
                    state = ChatMessagePartState.READY,
                    createdAt = now,
                    updatedAt = now
                ),
                ChatMessagePartEntity(
                    messageId = messageId,
                    partIndex = 2,
                    partType = ChatMessagePartType.TRANSCRIPT,
                    relativePath = null,
                    mimeType = null,
                    displayName = "Transcript",
                    sizeBytes = null,
                    widthPx = null,
                    heightPx = null,
                    durationMs = null,
                    sourceMessageId = messageId,
                    state = ChatMessagePartState.COMPLETED,
                    createdAt = now,
                    updatedAt = now
                )
            )
        )

        val messages = database.messageDao().observeMessages(sessionId).first()
        assertEquals(1, messages.size)
        assertEquals("Please transcribe this audio.", messages.first().message.content)

        val parts = messages.first().parts.sortedBy { it.partIndex }
        assertEquals(3, parts.size)
        assertEquals(ChatMessagePartType.IMAGE, parts[0].partType)
        assertEquals(ChatMessagePartType.AUDIO, parts[1].partType)
        assertEquals(ChatMessagePartType.TRANSCRIPT, parts[2].partType)
        assertEquals("audio/voice.m4a", parts[1].relativePath)
        assertEquals(5_000L, parts[1].durationMs)
        assertTrue(parts[2].sourceMessageId == messageId)
    }
}
