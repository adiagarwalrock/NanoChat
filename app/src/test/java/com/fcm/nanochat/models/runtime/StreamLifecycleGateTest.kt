package com.fcm.nanochat.models.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class StreamLifecycleGateTest {
    @Test
    fun `tryFinalize only succeeds once`() {
        val gate = StreamLifecycleGate()

        assertTrue(gate.tryFinalize())
        assertFalse(gate.tryFinalize())
    }

    @Test
    fun `only one concurrent finalizer wins`() {
        val gate = StreamLifecycleGate()
        val startLatch = CountDownLatch(1)
        val threads = mutableListOf<Thread>()
        val winners = mutableListOf<Boolean>()

        repeat(8) {
            threads += thread(start = true) {
                startLatch.await()
                synchronized(winners) {
                    winners += gate.tryFinalize()
                }
            }
        }

        startLatch.countDown()
        threads.forEach { it.join() }

        assertEquals(1, winners.count { it })
    }

    @Test
    fun `tryCancel only succeeds once`() {
        val gate = StreamLifecycleGate()

        assertFalse(gate.isCancelled())
        assertTrue(gate.tryCancel())
        assertTrue(gate.isCancelled())
        assertFalse(gate.tryCancel())
    }
}
