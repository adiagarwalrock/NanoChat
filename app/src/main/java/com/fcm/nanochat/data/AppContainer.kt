package com.fcm.nanochat.data

import android.content.Context
import com.fcm.nanochat.data.db.AppDatabase
import com.fcm.nanochat.data.repository.ChatRepository
import com.fcm.nanochat.inference.LocalInferenceClient
import com.fcm.nanochat.inference.RemoteInferenceClient
import okhttp3.OkHttpClient

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.create(appContext)
    val httpClient = OkHttpClient.Builder().build()

    val preferences = AppPreferences(appContext)

    private val localInferenceClient = LocalInferenceClient()
    private val remoteInferenceClient = RemoteInferenceClient(httpClient)

    val chatRepository = ChatRepository(
        database = database,
        preferences = preferences,
        localInferenceClient = localInferenceClient,
        remoteInferenceClient = remoteInferenceClient
    )
}
