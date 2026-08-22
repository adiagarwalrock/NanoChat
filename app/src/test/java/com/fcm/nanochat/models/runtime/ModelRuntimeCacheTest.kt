package com.fcm.nanochat.models.runtime

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ModelRuntimeCacheTest {
    private val root = Files.createTempDirectory("litertlm-cache-test").toFile()
    private val cache = ModelRuntimeCache(rootDirectory = root, maxEntries = 2)

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `cache keys are normalized stable and safe`() {
        val first = cache.directoryFor("  Google/Gemma-3-1B  ")
        val second = cache.directoryFor("google/gemma-3-1b")

        assertEquals(first, second)
        assertTrue(first.name.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `models with the same artifact filename stay isolated`() {
        val first = cache.prepare("publisher/model-one").directory!!
        val second = cache.prepare("publisher/model-two").directory!!

        first.resolve("model_multimodal.litertlm").writeText("one")
        second.resolve("model_multimodal.litertlm").writeText("two")

        assertNotEquals(first, second)
        assertEquals("one", first.resolve("model_multimodal.litertlm").readText())
        assertEquals("two", second.resolve("model_multimodal.litertlm").readText())
    }

    @Test
    fun `prepare recreates a missing cache directory`() {
        val directory = cache.directoryFor("publisher/model")
        assertFalse(directory.exists())

        val preparation = cache.prepare("publisher/model")

        assertEquals(RuntimeCacheState.COLD, preparation.state)
        assertTrue(directory.isDirectory)
    }

    @Test
    fun `clear removes only the requested model cache`() {
        val first = cache.prepare("publisher/model-one").directory!!
        val second = cache.prepare("publisher/model-two").directory!!
        first.resolve("cache.bin").writeText("one")
        second.resolve("cache.bin").writeText("two")

        assertTrue(cache.clear("publisher/model-one"))

        assertFalse(first.exists())
        assertTrue(second.resolve("cache.bin").exists())
    }

    @Test
    fun `successful initialization keeps only two newest model caches`() {
        val first = cache.prepare("publisher/model-one").directory!!
        first.resolve("cache.bin").writeText("one")
        cache.markInitialized("publisher/model-one")

        val second = cache.prepare("publisher/model-two").directory!!
        second.resolve("cache.bin").writeText("two")
        cache.markInitialized("publisher/model-two")

        first.setLastModified(1_000L)
        second.setLastModified(2_000L)

        val third = cache.prepare("publisher/model-three").directory!!
        third.resolve("cache.bin").writeText("three")
        cache.markInitialized("publisher/model-three")

        assertFalse(first.exists())
        assertTrue(second.exists())
        assertTrue(third.exists())
    }

    @Test
    fun `warm cache failure clears and retries once`() {
        var attempts = 0
        var recoveries = 0
        val result = initializeWithCacheRecovery(
            initialPreparation = RuntimeCachePreparation(
                directory = root,
                state = RuntimeCacheState.WARM,
                sizeBytes = 1L
            ),
            recoverCache = {
                recoveries += 1
                RuntimeCachePreparation(root, RuntimeCacheState.COLD, 0L)
            }
        ) {
            attempts += 1
            if (attempts == 1) error("bad warm cache")
            "ready"
        }

        assertEquals("ready", result.value)
        assertEquals(RuntimeCacheState.RECOVERED, result.state)
        assertEquals(2, attempts)
        assertEquals(1, recoveries)
    }

    @Test
    fun `cold cache failure is not retried`() {
        var attempts = 0
        var recoveries = 0

        val failure = runCatching {
            initializeWithCacheRecovery(
                initialPreparation = RuntimeCachePreparation(
                    directory = root,
                    state = RuntimeCacheState.COLD,
                    sizeBytes = 0L
                ),
                recoverCache = {
                    recoveries += 1
                    RuntimeCachePreparation(root, RuntimeCacheState.COLD, 0L)
                }
            ) {
                attempts += 1
                error("initialization failed")
            }
        }.exceptionOrNull()

        assertEquals("initialization failed", failure?.message)
        assertEquals(1, attempts)
        assertEquals(0, recoveries)
    }

    @Test
    fun `failed recovery preserves original error and does not loop`() {
        var attempts = 0
        val initialError = IllegalStateException("first backend sequence failed")
        val recoveryError = IllegalArgumentException("recovery failed")

        val thrown = runCatching {
            initializeWithCacheRecovery(
                initialPreparation = RuntimeCachePreparation(
                    directory = root,
                    state = RuntimeCacheState.WARM,
                    sizeBytes = 1L
                ),
                recoverCache = {
                    RuntimeCachePreparation(root, RuntimeCacheState.COLD, 0L)
                }
            ) {
                attempts += 1
                if (attempts == 1) throw initialError else throw recoveryError
            }
        }.exceptionOrNull()

        assertSame(initialError, thrown)
        assertEquals(2, attempts)
        assertSame(recoveryError, initialError.suppressed.single())
    }
}
