package com.fcm.nanochat.models.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelMemoryHeadroomTest {
    @Test
    fun `model load retains system memory reserve`() {
        val gib = 1024L * 1024L * 1024L

        assertTrue(
            hasMemoryHeadroom(
                availableBytes = 3L * gib,
                lowMemoryThresholdBytes = 256L * 1024L * 1024L,
                expectedModelBytes = 2L * gib
            )
        )
        assertFalse(
            hasMemoryHeadroom(
                availableBytes = 2L * gib,
                lowMemoryThresholdBytes = 256L * 1024L * 1024L,
                expectedModelBytes = 2L * gib
            )
        )
        assertTrue(
            hasMemoryHeadroom(
                availableBytes = 1L,
                lowMemoryThresholdBytes = 1L,
                expectedModelBytes = 0L
            )
        )
    }
}
