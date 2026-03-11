package com.fcm.nanochat.viewmodel

import com.fcm.nanochat.inference.InferenceMode
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingMessageAssemblerTest {
    @Test
    fun `remote mode appends deltas`() {
        val assembler = StreamingMessageAssembler()

        assembler.append(InferenceMode.REMOTE, "Hel")
        val result = assembler.append(InferenceMode.REMOTE, "lo")

        assertEquals("Hello", result)
        assertEquals("Hello", assembler.current())
    }

    @Test
    fun `aicore mode replaces with buffered final chunk`() {
        val assembler = StreamingMessageAssembler()

        assembler.append(InferenceMode.AICORE, "partial")
        val result = assembler.append(InferenceMode.AICORE, "final answer")

        assertEquals("final answer", result)
        assertEquals("final answer", assembler.current())
    }
}
