package com.fcm.nanochat.util

import android.util.Log
import com.fcm.nanochat.inference.InferenceMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Structured logger for inference-lifecycle events.
 * Writes to Logcat under a dedicated tag so events can be collected via
 * `adb logcat -s InferenceDump` for post-mortem analysis.
 *
 * This intentionally does **not** persist to disk or send to a remote service;
 * it exists for local diagnostics and crash-correlation breadcrumbs.
 */
@Singleton
class InferenceDumpLogger @Inject constructor() {

    /**
     * Write a single structured inference event.
     *
     * @param event             Short event name (e.g. "generation_started").
     * @param mode              Inference mode at the time of the event.
     * @param modelId           Model identifier, nullable for AICORE/REMOTE.
     * @param sessionId         Chat session id.
     * @param requestId         Per-generation request counter.
     * @param stage             Current pipeline stage.
     * @param visibleChars      Visible character count at this point.
     * @param watchdogTriggered Whether the first-token watchdog fired.
     * @param throwable         Optional associated exception.
     * @param marker            Optional crash marker snapshot.
     */
    fun writeInferenceEvent(
        event: String,
        mode: InferenceMode? = null,
        modelId: String? = null,
        sessionId: Long? = null,
        requestId: Long? = null,
        stage: String? = null,
        visibleChars: Int? = null,
        watchdogTriggered: Boolean? = null,
        throwable: Throwable? = null,
        marker: InferenceCrashMarker? = null
    ) {
        val parts = buildList {
            add("event=$event")
            mode?.let { add("mode=${it.name}") }
            modelId?.let { add("modelId=$it") }
            sessionId?.let { add("sessionId=$it") }
            requestId?.let { add("requestId=$it") }
            stage?.let { add("stage=$it") }
            visibleChars?.let { add("visibleChars=$it") }
            watchdogTriggered?.let { add("watchdog=$it") }
            throwable?.let { add("error=${it.javaClass.simpleName}:${it.message?.take(120).orEmpty()}") }
            marker?.let { add("marker_stage=${it.stage}") }
        }

        val line = parts.joinToString(" ")

        if (throwable != null) {
            Log.w(TAG, line, throwable)
        } else {
            Log.d(TAG, line)
        }
    }

    private companion object {
        const val TAG = "InferenceDump"
    }
}
