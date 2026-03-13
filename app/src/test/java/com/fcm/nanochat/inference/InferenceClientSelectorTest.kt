package com.fcm.nanochat.inference

import com.fcm.nanochat.data.SettingsSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertSame
import org.junit.Test

class InferenceClientSelectorTest {
    @Test
    fun `select returns local client for AICORE`() {
        val local = FakeClient("local")
        val downloaded = FakeClient("downloaded")
        val remote = FakeClient("remote")

        val selected =
            InferenceClientSelector.select(InferenceMode.AICORE, local, downloaded, remote)

        assertSame(local, selected)
    }

    @Test
    fun `select returns downloaded client for DOWNLOADED`() {
        val local = FakeClient("local")
        val downloaded = FakeClient("downloaded")
        val remote = FakeClient("remote")

        val selected =
            InferenceClientSelector.select(InferenceMode.DOWNLOADED, local, downloaded, remote)

        assertSame(downloaded, selected)
    }

    @Test
    fun `select returns remote client for REMOTE`() {
        val local = FakeClient("local")
        val downloaded = FakeClient("downloaded")
        val remote = FakeClient("remote")

        val selected =
            InferenceClientSelector.select(InferenceMode.REMOTE, local, downloaded, remote)

        assertSame(remote, selected)
    }

    private class FakeClient(
        private val name: String
    ) : InferenceClient {
        override suspend fun availability(settings: SettingsSnapshot): BackendAvailability =
            BackendAvailability.Available

        override fun streamChat(request: InferenceRequest): Flow<String> = emptyFlow()

        override fun toString(): String = name
    }
}
