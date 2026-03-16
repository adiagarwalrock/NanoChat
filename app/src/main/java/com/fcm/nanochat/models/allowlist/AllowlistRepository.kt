package com.fcm.nanochat.models.allowlist

import com.fcm.nanochat.data.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AllowlistRepository internal constructor(
    private val preferences: AppPreferences,
    private val bundledSource: AllowlistSource,
    private val cachedSource: CachedAllowlistSource,
    private val remoteSource: AllowlistSource?
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _snapshot = MutableStateFlow(
        AllowlistSnapshot(
            version = AllowlistVersion(value = "", refreshedAtEpochMs = 0L),
            sourceType = AllowlistSourceType.BUNDLED,
            models = emptyList(),
            lastRefreshError = null
        )
    )
    val snapshot: StateFlow<AllowlistSnapshot> = _snapshot.asStateFlow()

    init {
        scope.launch {
            loadInitialAllowlist()
            refreshFromRemote()
        }
    }

    fun refresh() {
        scope.launch {
            refreshFromRemote()
        }
    }

    fun currentModelOrNull(modelId: String?): AllowlistedModel? {
        val normalizedId = modelId?.trim()?.lowercase().orEmpty()
        if (normalizedId.isBlank()) return null
        return snapshot.value.models.firstOrNull { it.id == normalizedId }
    }

    private suspend fun loadInitialAllowlist() {
        val bundled = bundledSource.load()
        val cached = cachedSource.loadOrNull()

        val selected = when {
            cached == null -> bundled
            isVersionAtLeast(cached.version, bundled.version) -> cached
            else -> bundled
        }

        _snapshot.value = selected.toSnapshot(lastRefreshError = null)
    }

    private suspend fun refreshFromRemote() {
        val remote = remoteSource ?: return

        runCatching { remote.load() }
            .onSuccess { payload ->
                val current = _snapshot.value
                val shouldReplace = current.models.isEmpty() ||
                        isVersionAtLeast(payload.version, current.version.value)

                if (shouldReplace) {
                    _snapshot.value = payload.toSnapshot(lastRefreshError = null)
                }

                preferences.updateAllowlistCache(
                    version = payload.version,
                    json = payload.rawJson,
                    refreshedAtEpochMs = payload.refreshedAtEpochMs
                )
            }
            .onFailure { error ->
                _snapshot.update {
                    it.copy(lastRefreshError = error.message ?: "Unable to refresh model catalog.")
                }
            }
    }

    private fun AllowlistPayload.toSnapshot(lastRefreshError: String?): AllowlistSnapshot {
        return AllowlistSnapshot(
            version = AllowlistVersion(
                value = version,
                refreshedAtEpochMs = refreshedAtEpochMs
            ),
            sourceType = sourceType,
            models = models,
            lastRefreshError = lastRefreshError
        )
    }

    private fun isVersionAtLeast(candidate: String, baseline: String): Boolean {
        val candidateTokens = candidate.versionTokens()
        val baselineTokens = baseline.versionTokens()
        val maxSize = maxOf(candidateTokens.size, baselineTokens.size)

        for (index in 0 until maxSize) {
            val left = candidateTokens.getOrElse(index) { 0 }
            val right = baselineTokens.getOrElse(index) { 0 }
            if (left > right) return true
            if (left < right) return false
        }

        return true
    }

    private fun String.versionTokens(): List<Int> {
        return split('_', '-', '.', '/', 'v', 'V')
            .mapNotNull { it.toIntOrNull() }
            .ifEmpty { listOf(0) }
    }
}
