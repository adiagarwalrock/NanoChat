package com.fcm.nanochat.models.allowlist

import android.content.Context

internal class BundledAllowlistSource(
    context: Context,
    private val assetName: String,
    private val bundledVersion: String
) : AllowlistSource {
    private val appContext = context.applicationContext

    override suspend fun load(): AllowlistPayload {
        val json = appContext.assets.open(assetName).bufferedReader().use { it.readText() }
        return AllowlistParser.parse(
            rawJson = json,
            fallbackVersion = bundledVersion,
            sourceType = AllowlistSourceType.BUNDLED
        )
    }
}
