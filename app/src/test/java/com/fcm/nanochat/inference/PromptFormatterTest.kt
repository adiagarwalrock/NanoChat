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
    fun `formatDownloadedPrompt uses qwen family template without marker mixing`() {
        val formatted = PromptFormatter.formatDownloadedPrompt(
            history = listOf(
                ChatTurn(ChatRole.USER, "Hello"),
                ChatTurn(ChatRole.ASSISTANT, "Hi there")
            ),
            prompt = "How are you?",
            modelId = "litert-community/Qwen2.5-1.5B-Instruct"
        )

        assertEquals(DownloadedPromptFamily.QWEN, formatted.family)
        assertEquals(
            "You are NanoChat, a concise and helpful local assistant.",
            formatted.systemInstruction
        )
        assertEquals(
            "How are you?",
            formatted.userMessage
        )
        assertTrue("<|user|>" !in formatted.userMessage)
        assertTrue("<|assistant|>" !in formatted.userMessage)
        assertTrue("<|im_start|>" !in formatted.userMessage)
        assertTrue("<|im_end|>" !in formatted.userMessage)
        assertTrue("System:" !in formatted.userMessage)
        assertTrue("User:" !in formatted.userMessage)
    }

    @Test
    fun `formatDownloadedPrompt uses family specific system prompt`() {
        val gemma = PromptFormatter.formatDownloadedPrompt(
            history = listOf(
                ChatTurn(ChatRole.USER, "Hello"),
                ChatTurn(ChatRole.ASSISTANT, "Hi there")
            ),
            prompt = "How are you?",
            modelId = "google/gemma-3n-E2B-it-litert-lm"
        )
        val deepseek = PromptFormatter.formatDownloadedPrompt(
            history = emptyList(),
            prompt = "Explain this.",
            modelId = "litert-community/DeepSeek-R1-Distill-Qwen-1.5B"
        )

        assertEquals(DownloadedPromptFamily.GEMMA, gemma.family)
        assertEquals(
            "You are NanoChat running locally on Gemma. Keep answers clear and actionable.",
            gemma.systemInstruction
        )
        assertTrue(gemma.userMessage.endsWith("User: How are you?"))
        assertEquals(DownloadedPromptFamily.DEEPSEEK, deepseek.family)
        assertEquals(
            "You are NanoChat. Be concise, and show reasoning only when needed.",
            deepseek.systemInstruction
        )
        assertEquals("Explain this.", deepseek.userMessage)
    }

    @Test
    fun `flattenForDownloadedModel for qwen keeps only user message payload`() {
        val prompt = PromptFormatter.flattenForDownloadedModel(
            history = listOf(
                ChatTurn(ChatRole.USER, "Hello"),
                ChatTurn(ChatRole.ASSISTANT, "<|assistant|>\nAssistant: Hi there")
            ),
            prompt = "How are you?",
            modelId = "litert-community/Qwen2.5-1.5B-Instruct"
        )

        assertEquals("How are you?", prompt)
        assertTrue("Assistant:" !in prompt)
        assertTrue("User:" !in prompt)
    }
}
