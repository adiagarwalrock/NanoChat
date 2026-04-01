package com.fcm.nanochat.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.fcm.nanochat.inference.InferenceMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists a lightweight marker while an inference request is in flight.
 * If the process is killed (native crash, OOM, etc.) before [clear] is called,
 * the marker survives and [consumeUncleanMarker] returns it on the next cold start.
 *
 * Storage: a private [SharedPreferences] file committed synchronously so the
 * marker is on disk before the native runtime is invoked.
 */
@Singleton
class InferenceCrashMarkerStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Write the marker atomically before streaming begins. */
    fun markStarted(
        mode: InferenceMode,
        modelId: String?,
        sessionId: Long,
        requestId: Long
    ) {
        prefs.edit()
            .putString(KEY_MODE, mode.name)
            .putString(KEY_MODEL_ID, modelId.orEmpty())
            .putLong(KEY_SESSION_ID, sessionId)
            .putLong(KEY_REQUEST_ID, requestId)
            .putString(KEY_STAGE, "started")
            .putInt(KEY_VISIBLE_CHARS, 0)
            .putBoolean(KEY_ACTIVE, true)
            .commit() // commit (not apply) so data is on disk before native call
    }

    /** Update progress while streaming. Uses apply for speed. */
    fun markProgress(stage: String, visibleChars: Int) {
        prefs.edit()
            .putString(KEY_STAGE, stage)
            .putInt(KEY_VISIBLE_CHARS, visibleChars)
            .apply()
    }

    /** Clear the marker on successful completion or handled error. */
    fun clear() {
        prefs.edit()
            .putBoolean(KEY_ACTIVE, false)
            .apply()
    }

    /**
     * Call once on cold start. Returns the marker if the previous inference
     * session ended without [clear] being called, then removes it.
     */
    fun consumeUncleanMarker(): InferenceCrashMarker? {
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return null

        val modeName = prefs.getString(KEY_MODE, null) ?: return null
        val mode = runCatching { InferenceMode.valueOf(modeName) }.getOrNull() ?: return null

        val marker = InferenceCrashMarker(
            mode = mode,
            modelId = prefs.getString(KEY_MODEL_ID, null)?.ifBlank { null },
            sessionId = prefs.getLong(KEY_SESSION_ID, -1L),
            requestId = prefs.getLong(KEY_REQUEST_ID, -1L),
            stage = prefs.getString(KEY_STAGE, "unknown") ?: "unknown",
            visibleChars = prefs.getInt(KEY_VISIBLE_CHARS, 0)
        )

        prefs.edit().putBoolean(KEY_ACTIVE, false).commit()
        Log.w(TAG, "Consumed unclean inference marker: mode=${marker.mode} stage=${marker.stage}")
        return marker
    }

    private companion object {
        const val TAG = "InferenceCrashMarkerStore"
        const val PREFS_NAME = "inference_crash_marker"
        const val KEY_ACTIVE = "active"
        const val KEY_MODE = "mode"
        const val KEY_MODEL_ID = "model_id"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_REQUEST_ID = "request_id"
        const val KEY_STAGE = "stage"
        const val KEY_VISIBLE_CHARS = "visible_chars"
    }
}
