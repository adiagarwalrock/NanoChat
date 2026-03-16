package com.fcm.nanochat.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.fcm.nanochat.MainActivity
import com.fcm.nanochat.R
import com.fcm.nanochat.notifications.NotificationCoordinator
import java.util.Locale

/**
 * Foreground service that keeps the process alive while model downloads are in
 * progress. [ModelDownloadCoordinator] calls [start]/[stop] to manage the
 * service lifecycle; the service itself holds no download logic.
 *
 * The foreground notification mirrors download progress so the user can see it
 * in the status bar and notification shade even when the app is minimised.
 */
class ModelDownloadService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val modelName = intent.getStringExtra(EXTRA_MODEL_NAME) ?: "Model"
                startForegroundWithNotification(modelName)
            }
            ACTION_UPDATE_PROGRESS -> {
                val modelName = intent.getStringExtra(EXTRA_MODEL_NAME) ?: "Model"
                val downloaded = intent.getLongExtra(EXTRA_DOWNLOADED_BYTES, 0L)
                val total = intent.getLongExtra(EXTRA_TOTAL_BYTES, 0L)
                updateProgress(modelName, downloaded, total)
            }
            ACTION_COMPLETE -> {
                val modelName = intent.getStringExtra(EXTRA_MODEL_NAME) ?: "Model"
                val success = intent.getBooleanExtra(EXTRA_SUCCESS, true)
                onDownloadFinished(modelName, success)
            }
            ACTION_STOP -> {
                stopSelfGracefully()
            }
        }
        return START_NOT_STICKY
    }

    // ── Foreground plumbing ─────────────────────────────────────────────

    private fun startForegroundWithNotification(modelName: String) {
        val notification = buildProgressNotification(modelName, 0L, 0L)
        try {
            ServiceCompat.startForeground(
                this,
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            stopSelf()
        }
    }

    private fun updateProgress(modelName: String, downloaded: Long, total: Long) {
        val notification = buildProgressNotification(modelName, downloaded, total)
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE)
                    as android.app.NotificationManager
            manager.notify(FOREGROUND_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update foreground notification", e)
        }
    }

    private fun onDownloadFinished(modelName: String, success: Boolean) {
        stopSelfGracefully()
    }

    private fun stopSelfGracefully() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── Notification builders ───────────────────────────────────────────

    private fun buildProgressNotification(
        modelName: String,
        downloadedBytes: Long,
        totalBytes: Long
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_SERVICE,
            Intent(this, MainActivity::class.java)
                .putExtra(NotificationCoordinator.EXTRA_OPEN_MODELS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = getString(R.string.notification_model_download_title, modelName)
        val progressText = when {
            totalBytes > 0 -> getString(
                R.string.notification_model_download_progress,
                formatBytes(downloadedBytes),
                formatBytes(totalBytes)
            )
            else -> getString(R.string.notification_model_download_indeterminate)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_MODEL_DOWNLOADS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(progressText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setContentIntent(contentIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (totalBytes > 0) {
            val progress = ((downloadedBytes * 100) / totalBytes).toInt()
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val unit = 1024.0
        val exp = (Math.log(bytes.toDouble()) / Math.log(unit)).toInt()
        val prefix = "KMGTPE"[exp - 1]
        return String.format(
            Locale.US,
            "%.1f %sB",
            bytes / Math.pow(unit, exp.toDouble()),
            prefix
        )
    }

    companion object {
        private const val TAG = "ModelDownloadService"
        private const val CHANNEL_MODEL_DOWNLOADS = "model_downloads"
        private const val FOREGROUND_NOTIFICATION_ID = 9_001
        private const val REQUEST_CODE_SERVICE = 202

        const val ACTION_START = "com.fcm.nanochat.action.DOWNLOAD_START"
        const val ACTION_UPDATE_PROGRESS = "com.fcm.nanochat.action.DOWNLOAD_PROGRESS"
        const val ACTION_COMPLETE = "com.fcm.nanochat.action.DOWNLOAD_COMPLETE"
        const val ACTION_STOP = "com.fcm.nanochat.action.DOWNLOAD_STOP"

        const val EXTRA_MODEL_NAME = "extra_model_name"
        const val EXTRA_DOWNLOADED_BYTES = "extra_downloaded_bytes"
        const val EXTRA_TOTAL_BYTES = "extra_total_bytes"
        const val EXTRA_SUCCESS = "extra_success"

        fun start(context: Context, modelDisplayName: String) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MODEL_NAME, modelDisplayName)
            }
            context.startForegroundService(intent)
        }

        fun updateProgress(
            context: Context,
            modelDisplayName: String,
            downloadedBytes: Long,
            totalBytes: Long
        ) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_UPDATE_PROGRESS
                putExtra(EXTRA_MODEL_NAME, modelDisplayName)
                putExtra(EXTRA_DOWNLOADED_BYTES, downloadedBytes)
                putExtra(EXTRA_TOTAL_BYTES, totalBytes)
            }
            context.startService(intent)
        }

        fun complete(context: Context, modelDisplayName: String, success: Boolean) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_COMPLETE
                putExtra(EXTRA_MODEL_NAME, modelDisplayName)
                putExtra(EXTRA_SUCCESS, success)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
