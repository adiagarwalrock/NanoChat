package com.fcm.nanochat.inference

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteApiUrlResolverTest {
    @Test
    fun `chatCompletionsUrl appends endpoint when base url does not include it`() {
        val resolved = RemoteApiUrlResolver.chatCompletionsUrl("https://api.openai.com/v1/")

        assertEquals("https://api.openai.com/v1/chat/completions", resolved)
    }

    @Test
    fun `chatCompletionsUrl preserves endpoint when it already exists`() {
        val resolved = RemoteApiUrlResolver.chatCompletionsUrl(
            "https://example.com/custom/chat/completions"
        )

        assertEquals("https://example.com/custom/chat/completions", resolved)
    }
}