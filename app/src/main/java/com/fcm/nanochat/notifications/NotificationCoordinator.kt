package com.fcm.nanochat.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fcm.nanochat.MainActivity
import com.fcm.nanochat.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationCoordinator @Inject constructor(
    @param:ApplicationContext private val appContext: Context
) {
    private val notificationManager = NotificationManagerCompat.from(appContext)

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val downloadChannel = NotificationChannel(
            CHANNEL_MODEL_DOWNLOADS,
            appContext.getString(R.string.channel_model_downloads_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = appContext.getString(R.string.channel_model_downloads_desc)
            setShowBadge(false)
        }

        val chatChannel = NotificationChannel(
            CHANNEL_CHAT_RESPONSES,
            appContext.getString(R.string.channel_chat_responses_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = appContext.getString(R.string.channel_chat_responses_desc)
        }

        notificationManager.createNotificationChannels(listOf(downloadChannel, chatChannel))
    }

    fun notifyModelDownload(
        modelId: String,
        displayName: String,
        downloadedBytes: Long,
        totalBytes: Long,
        status: DownloadStatus
    ) {
        if (!canPostNotifications()) return

        val isFinal = status == DownloadStatus.Completed || status == DownloadStatus.Failed
        val progressText = when {
            status == DownloadStatus.Validating ->
                appContext.getString(R.string.notification_model_validating)

            status == DownloadStatus.Paused ->
                appContext.getString(R.string.download_paused)

            status == DownloadStatus.Failed ->
                appContext.getString(R.string.notification_model_download_failed)

            totalBytes > 0 ->
                appContext.getString(
                    R.string.notification_model_download_progress,
                    formatBytes(downloadedBytes),
                    formatBytes(totalBytes)
                )

            else -> appContext.getString(R.string.notification_model_download_indeterminate)
        }

        val title = when (status) {
            DownloadStatus.Completed ->
                appContext.getString(R.string.notification_model_download_complete, displayName)

            else ->
                appContext.getString(R.string.notification_model_download_title, displayName)
        }

        val contentIntent = PendingIntent.getActivity(
            appContext,
            REQUEST_CODE_MODELS,
            Intent(appContext, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_MODELS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(appContext, CHANNEL_MODEL_DOWNLOADS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(progressText)
            .setOngoing(!isFinal && status != DownloadStatus.Paused)
            .setAutoCancel(isFinal)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)

        when (status) {
            DownloadStatus.Downloading, DownloadStatus.Queued -> {
                val determinate = totalBytes > 0
                val progress =
                    if (determinate) ((downloadedBytes * 100) / totalBytes).toInt() else 0
                builder.setProgress(if (determinate) 100 else 0, progress, !determinate)
            }

            DownloadStatus.Validating -> builder.setProgress(0, 0, true)
            else -> builder.setProgress(0, 0, false)
        }

        notificationManager.notify(modelDownloadNotificationId(modelId), builder.build())
    }

    fun cancelModelDownload(modelId: String) {
        notificationManager.cancel(modelDownloadNotificationId(modelId))
    }

    fun notifyChatResponse(sessionId: Long, sessionTitle: String, preview: String) {
        if (!canPostNotifications()) return

        val trimmedPreview = preview.trim().take(MAX_PREVIEW_LENGTH)
        val intent = Intent(appContext, MainActivity::class.java)
            .putExtra(EXTRA_SESSION_ID, sessionId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pendingIntent = PendingIntent.getActivity(
            appContext,
            sessionId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(appContext, CHANNEL_CHAT_RESPONSES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(
                appContext.getString(R.string.notification_chat_ready_title, sessionTitle)
            )
            .setContentText(
                if (trimmedPreview.isNotBlank()) trimmedPreview
                else appContext.getString(R.string.notification_chat_ready_body)
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(chatNotificationId(sessionId), builder.build())
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        val granted =
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        return granted
    }

    private fun modelDownloadNotificationId(modelId: String): Int {
        return MODEL_NOTIFICATION_BASE + (modelId.hashCode() and 0x7FFFFFFF)
    }

    private fun chatNotificationId(sessionId: Long): Int {
        return CHAT_NOTIFICATION_BASE + (sessionId.hashCode() and 0x7FFFFFFF)
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val unit = 1024.0
        val exp = (Math.log(bytes.toDouble()) / Math.log(unit)).toInt()
        val prefix = "KMGTPE"[exp - 1]
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp.toDouble()), prefix)
    }

    enum class DownloadStatus {
        Queued,
        Downloading,
        Validating,
        Paused,
        Failed,
        Completed
    }

    companion object {
        const val EXTRA_SESSION_ID = "com.fcm.nanochat.extra.SESSION_ID"
        const val EXTRA_OPEN_MODELS = "com.fcm.nanochat.extra.OPEN_MODELS"

        private const val CHANNEL_MODEL_DOWNLOADS = "model_downloads"
        private const val CHANNEL_CHAT_RESPONSES = "chat_responses"
        private const val MODEL_NOTIFICATION_BASE = 10_000
        private const val CHAT_NOTIFICATION_BASE = 100_000
        private const val REQUEST_CODE_MODELS = 201
        private const val MAX_PREVIEW_LENGTH = 120
    }
}
