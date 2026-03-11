package com.fcm.nanochat

import android.app.Application
import com.fcm.nanochat.data.AppContainer

class NanoChatApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
