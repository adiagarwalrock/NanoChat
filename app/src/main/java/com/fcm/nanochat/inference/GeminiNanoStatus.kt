package com.fcm.nanochat.inference

data class GeminiNanoStatus(
    val supported: Boolean,
    val downloaded: Boolean,
    val downloading: Boolean,
    val downloadable: Boolean,
    val bytesDownloaded: Long?,
    val bytesToDownload: Long?,
    val message: String?
)
