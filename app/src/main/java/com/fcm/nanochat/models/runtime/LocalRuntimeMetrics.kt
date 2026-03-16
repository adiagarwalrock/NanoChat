package com.fcm.nanochat.models.runtime

data class LocalRuntimeMetrics(
    val modelId: String,
    val initDurationMs: Long,
    val timeToFirstTokenMs: Long,
    val generationDurationMs: Long,
    val tokensPerSecond: Double,
    val backend: String,
    val measuredAtEpochMs: Long = System.currentTimeMillis()
)

interface LocalRuntimeTelemetry {
    fun onMetrics(metrics: LocalRuntimeMetrics)
}

class InMemoryLocalRuntimeTelemetry : LocalRuntimeTelemetry {
    @Volatile
    private var latestMetrics: LocalRuntimeMetrics? = null

    override fun onMetrics(metrics: LocalRuntimeMetrics) {
        latestMetrics = metrics
    }

    fun latest(): LocalRuntimeMetrics? = latestMetrics
}
