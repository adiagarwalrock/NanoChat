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
    fun `removes think blocks from output`() {
        val input = "<think>reasoning that should stay hidden</think>\nFinal answer"

        val output = GeneratedTextSanitizer.sanitize(input)

        assertEquals("Final answer", output)
    }
}
