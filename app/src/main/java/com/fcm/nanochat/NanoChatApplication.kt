package com.fcm.nanochat

import android.app.Application
import com.fcm.nanochat.data.repository.LocalModelRepository
import com.fcm.nanochat.models.runtime.ModelLifecycleCoordinator
import com.fcm.nanochat.notifications.NotificationCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NanoChatApplication : Application() {
    @Inject
    lateinit var localModelRepository: LocalModelRepository

    @Inject
    lateinit var notificationCoordinator: NotificationCoordinator

    @Inject
    lateinit var lifecycleCoordinator: ModelLifecycleCoordinator

    override fun onCreate() {
        super.onCreate()
        notificationCoordinator.ensureChannels()
        lifecycleCoordinator.initialize()
        localModelRepository.reconcile()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        lifecycleCoordinator.onLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        lifecycleCoordinator.onTrimMemory(level)
    }
}
