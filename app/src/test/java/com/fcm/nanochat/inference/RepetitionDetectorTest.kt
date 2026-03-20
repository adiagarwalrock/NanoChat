package com.fcm.nanochat.inference

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepetitionDetectorTest {

    @Test
    fun `detects single character repetition loop`() {
        val output = "##" + "#".repeat(100)
        assertTrue(RepetitionDetector.isLikelyDegenerate(output))
    }

    @Test
    fun `ignores normal text`() {
        val output =
            "This is a normal sentence. It has some punctuation, like commas and periods. It is definitely longer than 64 characters, but it should not be flagged."
        assertFalse(RepetitionDetector.isLikelyDegenerate(output))
    }

    @Test
    fun `detects short phrase repetition loop`() {
        val phrase = "be prepared for potential threats "
        val output = "Here is some initial text. " + phrase.repeat(5)
        assertTrue(RepetitionDetector.isLikelyDegenerate(output))
    }

    @Test
    fun `ignores natural repetition`() {
        // Words repeating naturally shouldn't trigger if it's less than the block threshold
        val output =
            "I really really really want to go to the park today. It is really fun to go to the park."
        assertFalse(RepetitionDetector.isLikelyDegenerate(output))
    }

    @Test
    fun `detects long phrase repetition loop`() {
        val phrase = "Wait, let's think about this carefully before we proceed. "
        val output = "My reasoning is as follows: " + phrase.repeat(4)
        assertTrue(RepetitionDetector.isLikelyDegenerate(output))
    }
}
