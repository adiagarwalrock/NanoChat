package com.fcm.nanochat.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReasoningContentParserTest {
    @Test
    fun `split extracts closed think block`() {
        val parsed = ReasoningContentParser.split(
            "<think>Check soil temperature first.</think>\n1. Plant potatoes"
        )

        assertEquals("Check soil temperature first.", parsed.thinking)
        assertEquals("1. Plant potatoes", parsed.visible)
    }

    @Test
    fun `split hides open think block from visible output`() {
        val parsed = ReasoningContentParser.split("<think>Working through the plan")

        assertEquals("Working through the plan", parsed.thinking)
        assertEquals("", parsed.visible)
    }

    @Test
    fun `split returns visible text unchanged when no reasoning exists`() {
        val parsed = ReasoningContentParser.split("Final answer only")

        assertNull(parsed.thinking)
        assertEquals("Final answer only", parsed.visible)
    }
}
