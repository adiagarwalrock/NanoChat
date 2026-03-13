package com.fcm.nanochat.inference

import com.fcm.nanochat.model.ChatRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptFormatterTest {
    @Test
    fun `historyWindow keeps the most recent turns`() {
        val history = (1..12).map { index ->
            ChatTurn(
                role = if (index % 2 == 0) ChatRole.ASSISTANT else ChatRole.USER,
                content = "turn-$index"
            )
        }

        val result = PromptFormatter.historyWindow(history, maxTurns = 4)

        assertEquals(listOf("turn-9", "turn-10", "turn-11", "turn-12"), result.map { it.content })
    }

    @Test
    fun `flattenForAicore includes prompt and speaker labels`() {
        val prompt = PromptFormatter.flattenForAicore(
            history = listOf(
                ChatTurn(ChatRole.USER, "Summarize this"),
                ChatTurn(ChatRole.ASSISTANT, "Sure")
            ),
            prompt = "What changed?",
            maxTurns = 10
        )

        assertTrue(prompt.contains("User: Summarize this"))
        assertTrue(prompt.contains("Assistant: Sure"))
        assertTrue(prompt.endsWith("Assistant:"))
    }

    @Test
    fun `flattenForDownloadedModel emits chatml structure`() {
        val prompt = PromptFormatter.flattenForDownloadedModel(
            history = listOf(
                ChatTurn(ChatRole.USER, "Hello"),
                ChatTurn(ChatRole.ASSISTANT, "Hi there")
            ),
            prompt = "How are you?"
        )

        assertTrue(prompt.contains("<|user|>\nHello"))
        assertTrue(prompt.contains("<|assistant|>\nHi there"))
        assertTrue(prompt.endsWith("<|assistant|>"))
    }
}
