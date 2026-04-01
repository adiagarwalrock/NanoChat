package com.fcm.nanochat.util

import android.util.Log
import com.fcm.nanochat.inference.InferenceMode
import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around Firebase Crashlytics that enriches crash reports with
 * inference-specific context (mode, model, session, stage).
 *
 * All methods are safe to call even when Crashlytics is unconfigured –
 * failures are silently logged.
 */
@Singleton
class CrashReporter @Inject constructor() {

    private val crashlytics: FirebaseCrashlytics?
        get() = runCatching { FirebaseCrashlytics.getInstance() }.getOrNull()

    /** Record a breadcrumb string visible in the Crashlytics "Logs" tab. */
    fun logBreadcrumb(message: String) {
        try {
            crashlytics?.log(message)
        } catch (e: Throwable) {
            Log.w(TAG, "logBreadcrumb failed", e)
        }
    }

    /** Record a non-fatal exception with an accompanying message. */
    fun recordNonFatal(error: Throwable, message: String) {
        try {
            crashlytics?.let { fc ->
                fc.log(message)
                fc.recordException(error)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "recordNonFatal failed", e)
        }
    }

    /**
     * Set custom keys describing the active inference context.
     * These keys appear on every subsequent crash/non-fatal until cleared.
     */
    fun setInferenceContext(
        mode: InferenceMode,
        modelId: String?,
        sessionId: Long,
        requestId: Long
    ) {
        try {
            crashlytics?.let { fc ->
                fc.setCustomKey("inference_mode", mode.name)
                fc.setCustomKey("inference_model_id", modelId.orEmpty())
                fc.setCustomKey("inference_session_id", sessionId)
                fc.setCustomKey("inference_request_id", requestId)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "setInferenceContext failed", e)
        }
    }

    /** Update the stage/visibleChars keys while streaming. */
    fun updateInferenceStage(stage: String, visibleChars: Int) {
        try {
            crashlytics?.let { fc ->
                fc.setCustomKey("inference_stage", stage)
                fc.setCustomKey("inference_visible_chars", visibleChars)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "updateInferenceStage failed", e)
        }
    }

    /** Remove inference keys after generation completes or fails. */
    fun clearInferenceContext() {
        try {
            crashlytics?.let { fc ->
                fc.setCustomKey("inference_mode", "")
                fc.setCustomKey("inference_model_id", "")
                fc.setCustomKey("inference_session_id", -1L)
                fc.setCustomKey("inference_request_id", -1L)
                fc.setCustomKey("inference_stage", "")
                fc.setCustomKey("inference_visible_chars", -1)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "clearInferenceContext failed", e)
        }
    }

    /**
     * Report that the previous inference session terminated without cleanup.
     * Sends a non-fatal with the surviving marker data.
     */
    fun recordUncleanInferenceTermination(marker: InferenceCrashMarker) {
        try {
            crashlytics?.let { fc ->
                fc.log(
                    "Unclean inference termination: mode=${marker.mode} " +
                            "modelId=${marker.modelId.orEmpty()} stage=${marker.stage} " +
                            "visibleChars=${marker.visibleChars}"
                )
                fc.setCustomKey("unclean_mode", marker.mode.name)
                fc.setCustomKey("unclean_model_id", marker.modelId.orEmpty())
                fc.setCustomKey("unclean_stage", marker.stage)
                fc.setCustomKey("unclean_visible_chars", marker.visibleChars)
                fc.recordException(
                    RuntimeException(
                        "Unclean inference termination: mode=${marker.mode} stage=${marker.stage}"
                    )
                )
            }
        } catch (e: Throwable) {
            Log.w(TAG, "recordUncleanInferenceTermination failed", e)
        }
    }

    private companion object {
        const val TAG = "CrashReporter"
    }
}
