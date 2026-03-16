package com.fcm.nanochat.inference

import org.junit.Assert.assertEquals
import org.junit.Test

class GeneratedTextSanitizerTest {
    @Test
    fun `removes assistant prefixes and control tags`() {
        val input = "<|assistant|>\nAssistant: Hello from NanoChat"

        val output = GeneratedTextSanitizer.sanitize(input)

        assertEquals("Hello from NanoChat", output)
    }

    @Test
    fun `preserves think blocks by default`() {
        val input = "<think>reasoning that should stay collapsible</think>\nFinal answer"

        val output = GeneratedTextSanitizer.sanitize(input)

        assertEquals(input, output)
    }

    @Test
    fun `removes think blocks when requested`() {
        val input = "<think>reasoning that should stay hidden</think>\nFinal answer"

        val output = GeneratedTextSanitizer.sanitize(input, preserveThinkingBlocks = false)

        assertEquals("Final answer", output)
    }

    @Test
    fun `removes chat template role artifacts`() {
        val input = "<|im_start|>assistant\nHello from Qwen<|im_end|>"

        val output = GeneratedTextSanitizer.sanitize(input)

        assertEquals("Hello from Qwen", output.trim())
    }

    @Test
    fun `returns empty for control-only chunks`() {
        val input = "<|im_start|>assistant\n<|im_end|>"

        val output = GeneratedTextSanitizer.sanitize(input)

        assertEquals("", output)
    }
}

