package com.fcm.nanochat.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadedModelInferenceClientWatchdogTest {
    @Test
    fun `visibilityState reports callback without visible text`() {
        val state = visibilityState(firstRawCallbackAtMs = 120L, firstVisibleTokenAtMs = 0L)

        assertEquals(LocalGenerationVisibilityState.CALLBACK_NO_VISIBLE, state)
    }

    @Test
    fun `visibilityState reports visible output once token appears`() {
        val state = visibilityState(firstRawCallbackAtMs = 120L, firstVisibleTokenAtMs = 640L)

        assertEquals(LocalGenerationVisibilityState.VISIBLE_STARTED, state)
    }

    @Test
    fun `no-visible watchdog only triggers before first visible token`() {
        val shouldTrigger = shouldTriggerNoVisibleWatchdog(
            firstVisibleTokenAtMs = 0L,
            startedAtMs = 1_000L,
            nowMs = 20_000L,
            thresholdMs = 15_000L
        )
        val shouldNotTriggerAfterVisible = shouldTriggerNoVisibleWatchdog(
            firstVisibleTokenAtMs = 1_700L,
            startedAtMs = 1_000L,
            nowMs = 20_000L,
            thresholdMs = 15_000L
        )

        assertTrue(shouldTrigger)
        assertFalse(shouldNotTriggerAfterVisible)
    }

    @Test
    fun `visible stall watchdog triggers only after visible output begins`() {
        val shouldTriggerAfterVisible = shouldTriggerVisibleStallWatchdog(
            firstVisibleTokenAtMs = 5_000L,
            lastVisibleProgressAtMs = 6_000L,
            nowMs = 20_000L,
            thresholdMs = 10_000L
        )
        val shouldNotTriggerWithoutVisible = shouldTriggerVisibleStallWatchdog(
            firstVisibleTokenAtMs = 0L,
            lastVisibleProgressAtMs = 6_000L,
            nowMs = 20_000L,
            thresholdMs = 10_000L
        )
        val shouldNotTriggerBeforeThreshold = shouldTriggerVisibleStallWatchdog(
            firstVisibleTokenAtMs = 5_000L,
            lastVisibleProgressAtMs = 14_500L,
            nowMs = 20_000L,
            thresholdMs = 10_000L
        )

        assertTrue(shouldTriggerAfterVisible)
        assertFalse(shouldNotTriggerWithoutVisible)
        assertFalse(shouldNotTriggerBeforeThreshold)
    }
}
