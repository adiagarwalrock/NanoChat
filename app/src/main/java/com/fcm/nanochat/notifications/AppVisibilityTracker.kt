package com.fcm.nanochat.notifications

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppVisibilityTracker @Inject constructor() : DefaultLifecycleObserver {
    private val _isForeground = MutableStateFlow(false)
    val isForegroundFlow: StateFlow<Boolean> = _isForeground
    val isForeground: Boolean
        get() = _isForeground.value

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        _isForeground.value = true
    }

    override fun onStop(owner: LifecycleOwner) {
        _isForeground.value = false
    }
}
