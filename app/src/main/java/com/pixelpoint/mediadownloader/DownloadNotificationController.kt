package com.pixelpoint.mediadownloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class DownloadNotificationController(private val service: Service) {
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = service.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "下载任务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "显示后台媒体下载任务状态"
        }
        manager.createNotificationChannel(channel)
    }

    fun startForeground(task: DownloadTask, message: String, indeterminate: Boolean) {
        ServiceCompat.startForeground(
            service,
            NOTIFICATION_ID,
            build(task, message, indeterminate),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        )
    }

    fun update(task: DownloadTask, message: String, indeterminate: Boolean) {
        val manager = service.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, build(task, message, indeterminate))
    }

    private fun build(
        task: DownloadTask,
        message: String,
        indeterminate: Boolean
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            service,
            0,
            Intent(service, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val cancelIntent = PendingIntent.getService(
            service,
            1,
            Intent(service, MediaDownloadService::class.java).setAction(DownloadServiceContract.ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(service, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_elephant)
            .setContentTitle(task.title)
            .setContentText(message)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(task.status == DownloadStatus.Downloading || task.status == DownloadStatus.Processing)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (indeterminate) {
            builder.setProgress(100, 0, true)
                .addAction(R.drawable.ic_notification_elephant, "取消", cancelIntent)
        } else {
            val notificationProgress = (task.progress * 100).toInt().coerceIn(0, 100)
            if (task.status == DownloadStatus.Downloading || task.status == DownloadStatus.Processing) {
                builder.setProgress(100, notificationProgress, false)
                    .addAction(R.drawable.ic_notification_elephant, "取消", cancelIntent)
            } else {
                builder.setProgress(0, 0, false)
            }
        }

        return builder.build()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "media_downloads"
    }
}
