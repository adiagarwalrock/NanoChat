package com.fcm.nanochat

import android.app.Application
import com.fcm.nanochat.data.repository.LocalModelRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NanoChatApplication : Application() {
    @Inject lateinit var localModelRepository: LocalModelRepository

    override fun onCreate() {
        super.onCreate()
        localModelRepository.reconcile()
    }
}
