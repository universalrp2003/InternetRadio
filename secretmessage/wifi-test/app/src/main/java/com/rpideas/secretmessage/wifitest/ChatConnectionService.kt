package com.rpideas.secretmessage.wifitest

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Keeps the chat's connection while a person briefly switches apps. Since Android 15 an app loses
 * its network connections a few seconds after it leaves the screen unless it runs a foreground
 * service. The owner chose this over ending the chat (17 October 2026). The notification says only
 * "Secret Message" and the chat state; it never shows names or messages.
 */
class ChatConnectionService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: DEFAULT_TEXT
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(this, text), type)
            running = true
        } catch (refused: RuntimeException) {
            // Android refused the foreground service; the chat still works while the app is on screen.
            running = false
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "chat_connection"
        private const val NOTIFICATION_ID = 41
        private const val EXTRA_TEXT = "text"
        private const val DEFAULT_TEXT = "Chat open"

        @Volatile
        private var running = false

        /** Starts the service with [text]; call while the app is on screen. */
        fun show(context: Context, text: String) {
            val intent = Intent(context, ChatConnectionService::class.java).putExtra(EXTRA_TEXT, text)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (refused: RuntimeException) {
                // For example ForegroundServiceStartNotAllowedException; the chat works on screen.
            }
        }

        /** Changes the notification's text, if the service runs and notifications are allowed. */
        fun update(context: Context, text: String) {
            if (!running) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification(context, text))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ChatConnectionService::class.java))
        }

        private fun notification(context: Context, text: String): Notification {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(CHANNEL_ID, "Chat connection", NotificationManager.IMPORTANCE_LOW)
                channel.description = "Shown while a Secret Message chat is starting or open. It never shows names or messages."
                channel.setShowBadge(false)
                manager.createNotificationChannel(channel)
            }
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, WifiTestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle("Secret Message")
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build()
        }
    }
}
