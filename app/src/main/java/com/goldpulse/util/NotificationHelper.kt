package com.goldpulse.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.goldpulse.MainActivity
import com.goldpulse.R

object NotificationHelper {
    const val ALERT_CHANNEL_ID = "goldpulse_alerts"
    const val ONGOING_CHANNEL_ID = "goldpulse_ongoing"
    const val ONGOING_NOTIFICATION_ID = 9001

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val alerts = NotificationChannel(
            ALERT_CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }

        val ongoing = NotificationChannel(
            ONGOING_CHANNEL_ID,
            context.getString(R.string.notification_persistent_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_persistent_channel_description)
        }

        manager.createNotificationChannel(alerts)
        manager.createNotificationChannel(ongoing)
    }

    fun showAlert(context: Context, title: String, body: String) {
        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify((System.currentTimeMillis() % 100000).toInt(), notification)
    }

    fun buildOngoingNotification(context: Context, title: String, body: String) =
        NotificationCompat.Builder(context, ONGOING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent(context))
            .build()

    fun updateOngoing(context: Context, body: String) {
        val notification = buildOngoingNotification(
            context = context,
            title = context.getString(R.string.notification_persistent_title),
            body = body
        )
        NotificationManagerCompat.from(context).notify(ONGOING_NOTIFICATION_ID, notification)
    }

    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
