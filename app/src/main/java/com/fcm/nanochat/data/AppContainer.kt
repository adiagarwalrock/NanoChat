package com.fcm.nanochat.data

import android.content.Context
import com.fcm.nanochat.data.db.AppDatabase
import com.fcm.nanochat.data.repository.ChatRepository
import com.fcm.nanochat.inference.LocalInferenceClient
import com.fcm.nanochat.inference.RemoteInferenceClient
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.create(appContext)
    val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

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
