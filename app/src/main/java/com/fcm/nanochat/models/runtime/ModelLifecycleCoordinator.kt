package com.fcm.nanochat.models.runtime

import android.content.ComponentCallbacks2
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelLifecycleCoordinator @Inject constructor(
    private val runtimeManager: ModelRuntimeManager
) : DefaultLifecycleObserver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var backgroundEjectionJob: Job? = null

    /**
     * Initializes the coordinator by attaching to the process lifecycle.
     */
    fun initialize() {
        Log.d(TAG, "Initializing ModelLifecycleCoordinator")
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        Log.d(TAG, "App returned to foreground, cancelling background ejection timer")
        backgroundEjectionJob?.cancel()
        backgroundEjectionJob = null
    }

    override fun onStop(owner: LifecycleOwner) {
        Log.d(
            TAG,
            "App entered background, starting ejection timer ($BACKGROUND_EJECT_TIMEOUT_MS ms)"
        )
        backgroundEjectionJob?.cancel()
        backgroundEjectionJob = scope.launch {
            delay(BACKGROUND_EJECT_TIMEOUT_MS)
            Log.i(TAG, "Background timeout reached, ejecting local model to free memory")
            runtimeManager.release(reason = RuntimeReleaseReason.EJECTED)
        }
    }

    /**
     * Handles severe memory pressure signals from the system.
     */
    fun onLowMemory() {
        Log.w(TAG, "System low memory signal, ejecting local model immediately")
        scope.launch {
            runtimeManager.release(reason = RuntimeReleaseReason.EJECTED)
        }
    }

    /**
     * Handles granular memory pressure signals.
     */
    fun onTrimMemory(level: Int) {
        @Suppress("DEPRECATION")
        val isCritical = level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE

        // We avoid ejecting on TRIM_MEMORY_UI_HIDDEN (20) to allow quick task switching
        // without losing the loaded model. onStop handles the 5-minute timeout.
        if (isCritical) {
            Log.w(TAG, "Memory trim level $level (critical), ejecting local model immediately")
            scope.launch {
                runtimeManager.release(reason = RuntimeReleaseReason.EJECTED)
            }
        } else {
            Log.d(TAG, "Memory trim level $level (non-critical), ignoring immediate ejection")
        }
    }

    companion object {
        private const val TAG = "ModelLifecycleCoord"
        private const val BACKGROUND_EJECT_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes
    }
}
