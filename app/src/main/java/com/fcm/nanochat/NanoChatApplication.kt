package com.fcm.nanochat

import android.app.Application
import com.fcm.nanochat.data.repository.LocalModelRepository
import com.fcm.nanochat.notifications.NotificationCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NanoChatApplication : Application() {
    @Inject
    lateinit var localModelRepository: LocalModelRepository

    @Inject
    lateinit var notificationCoordinator: NotificationCoordinator

    override fun onCreate() {
        super.onCreate()
        notificationCoordinator.ensureChannels()
        localModelRepository.reconcile()
    }
}
