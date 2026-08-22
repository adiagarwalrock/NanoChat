package com.fcm.nanochat.models.download

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelFileChangeTest {
    @Test
    fun `model file change releases runtime before clearing cache and deleting files`() = runTest {
        val events = mutableListOf<String>()

        changeModelFilesAfterRuntimeRelease(
            modelId = "publisher/model",
            releaseRuntime = { events += "release:$it" },
            clearRuntimeCache = { events += "clear:$it" }
        ) {
            events += "delete-files"
        }

        assertEquals(
            listOf(
                "release:publisher/model",
                "clear:publisher/model",
                "delete-files"
            ),
            events
        )
    }
}
