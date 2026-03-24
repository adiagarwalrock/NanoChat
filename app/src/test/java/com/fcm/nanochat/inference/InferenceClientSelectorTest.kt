package com.fcm.nanochat.inference

import com.fcm.nanochat.data.SettingsSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertSame
import org.junit.Test

class InferenceClientSelectorTest {
    @Test
    fun `select returns local client for AICORE`() {
        val clients = testClients()

        val selected = InferenceClientSelector.select(
            InferenceMode.AICORE,
            clients.local,
            clients.downloaded,
            clients.remote
        )

        assertSame(clients.local, selected)
    }

    @Test
    fun `select returns downloaded client for DOWNLOADED`() {
        val clients = testClients()

        val selected = InferenceClientSelector.select(
            InferenceMode.DOWNLOADED,
            clients.local,
            clients.downloaded,
            clients.remote
        )

        assertSame(clients.downloaded, selected)
    }

    @Test
    fun `select returns remote client for REMOTE`() {
        val clients = testClients()

        val selected = InferenceClientSelector.select(
            InferenceMode.REMOTE,
            clients.local,
            clients.downloaded,
            clients.remote
        )

        assertSame(clients.remote, selected)
    }

    private fun testClients(): TestClients {
        return TestClients(
            local = FakeClient("local"),
            downloaded = FakeClient("downloaded"),
            remote = FakeClient("remote")
        )
    }

    private data class TestClients(
        val local: FakeClient,
        val downloaded: FakeClient,
        val remote: FakeClient
    )

    private class FakeClient(
        private val name: String
    ) : InferenceClient {
        override suspend fun availability(settings: SettingsSnapshot): BackendAvailability =
            BackendAvailability.Available

        override suspend fun capabilities(settings: SettingsSnapshot): InferenceCapabilities =
            InferenceCapabilities.defaultTextOnly()

        override fun streamChat(request: InferenceRequest): Flow<String> = emptyFlow()

        override fun toString(): String = name
    }
}
