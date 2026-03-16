package com.fcm.nanochat.data.repository

import com.fcm.nanochat.inference.InferenceMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatDefaultsTest {
    @Test
    fun `history window uses local and remote defaults`() {
        assertEquals(10, ChatDefaults.historyWindowFor(InferenceMode.AICORE))
        assertEquals(20, ChatDefaults.historyWindowFor(InferenceMode.DOWNLOADED))
        assertEquals(20, ChatDefaults.historyWindowFor(InferenceMode.REMOTE))
    }
}
