package com.fcm.nanochat.inference

import com.fcm.nanochat.data.ThinkingEffort
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
    fun `formatDownloadedPrompt keeps payload free of chat template markers`() {
        val formatted = PromptFormatter.formatDownloadedPrompt(
            history = emptyList(),
            prompt = "How are you?",
            promptFamily = "litert-community/Qwen2.5-1.5B-Instruct"
        )

        assertEquals(DownloadedPromptFamily.QWEN, formatted.family)
        assertTrue(formatted.systemInstruction.contains("Reply in clean Markdown"))
        assertEquals("How are you?", formatted.userMessage)
        assertTrue("<|user|>" !in formatted.userMessage)
        assertTrue("<|assistant|>" !in formatted.userMessage)
        assertTrue("<|im_start|>" !in formatted.userMessage)
        assertTrue("<|im_end|>" !in formatted.userMessage)
        assertTrue("System:" !in formatted.userMessage)
    }

    @Test
    fun `formatDownloadedPrompt uses generic transcript for history`() {
        val formatted = PromptFormatter.formatDownloadedPrompt(
            history = listOf(
                ChatTurn(ChatRole.USER, "Hello"),
                ChatTurn(ChatRole.ASSISTANT, "Hi there")
            ),
            prompt = "How are you?",
            promptFamily = "google/gemma-3n-E2B-it-litert-lm"
        )

        assertEquals(DownloadedPromptFamily.GEMMA, formatted.family)
        assertTrue(formatted.userMessage.contains("Conversation so far:"))
        assertTrue(formatted.userMessage.contains("Assistant: Hi there"))
        assertTrue(formatted.userMessage.endsWith("Latest user message:\nHow are you?"))
    }

    @Test
    fun `formatDownloadedPrompt requests tagged reasoning when supported`() {
        val formatted = PromptFormatter.formatDownloadedPrompt(
            history = emptyList(),
            prompt = "Explain this.",
            promptFamily = "litert-community/DeepSeek-R1-Distill-Qwen-1.5B",
            thinkingEffort = ThinkingEffort.HIGH,
            supportsThinking = true
        )

        assertEquals(DownloadedPromptFamily.DEEPSEEK, formatted.family)
        assertTrue(formatted.systemInstruction.contains("<think>...</think>"))
        assertEquals("Explain this.", formatted.userMessage)
    }

    @Test
    fun `flattenForDownloadedModel uses user message payload only`() {
        val prompt = PromptFormatter.flattenForDownloadedModel(
            history = emptyList(),
            prompt = "How are you?",
            promptFamily = "litert-community/Qwen2.5-1.5B-Instruct"
        )

        assertEquals("How are you?", prompt)
        assertTrue("Assistant:" !in prompt)
        assertTrue("User:" !in prompt)
    }
}
