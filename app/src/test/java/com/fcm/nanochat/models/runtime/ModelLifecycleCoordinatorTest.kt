package com.fcm.nanochat.models.runtime

import android.content.ComponentCallbacks2
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelLifecycleCoordinatorTest {
    @Suppress("DEPRECATION")
    @Test
    fun `ordinary task switch retains model but pressure ejects it`() {
        assertFalse(shouldEjectForTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN))
        assertFalse(shouldEjectForTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW))
        assertTrue(shouldEjectForTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL))
        assertTrue(shouldEjectForTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND))
        assertTrue(shouldEjectForTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE))
    }
}
