package com.universalrp.pulseeq.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.universalrp.pulseeq.MainActivity
import com.universalrp.pulseeq.R
import com.universalrp.pulseeq.audio.EqCore
import com.universalrp.pulseeq.audio.DeviceEq
import com.universalrp.pulseeq.audio.PlayerEngine

/**
 * Keeps PulseEQ alive in the background.
 *
 * A foreground service with a persistent notification — the only thing Android
 * allows to stay running indefinitely — plus an optional partial wake lock so the
 * DSP survives Doze. When the built-in player is active this is a media playback
 * service; otherwise it is a "special use" keep-alive for the audio effect.
 *
 * Nothing here is a trick to dodge battery rules: if the user (or a battery saver)
 * stops the service, the equalizer genuinely stops, and the notification tells them.
 */
class EqService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            EqCore.settings(this).serviceWanted = false
            EqCore.player(this).stop(notify = false)
            stopSelf()
            return START_NOT_STICKY
        }

        val settings = EqCore.settings(this)
        val player = EqCore.player(this)

        startForegroundSafely(player.state == PlayerEngine.State.PLAYING)

        if (settings.systemEqWanted) {
            EqCore.startGlobal()
        }
        EqCore.refreshFromSettings(this)
        acquireWakeLockIfWanted()

        player.onStateChanged = { state ->
            updateNotification(state == PlayerEngine.State.PLAYING)
        }

        // START_STICKY: if Android kills us for memory, bring the EQ back.
        return START_STICKY
    }

    private fun startForegroundSafely(isPlaying: Boolean) {
        val notification = buildNotification(isPlaying)
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val type = if (isPlaying) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                }
                startForeground(NOTIFICATION_ID, notification, type)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (t: Throwable) {
            // Some OEM builds refuse special-use services; the service still runs
            // in the background until the system decides otherwise.
        }
    }

    private fun updateNotification(isPlaying: Boolean) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification(isPlaying))
        } catch (t: Throwable) {
            // Ignored.
        }
    }

    private fun acquireWakeLockIfWanted() {
        val settings = EqCore.settings(this)
        if (!settings.serviceWanted) return
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PulseEQ::dsp").apply {
                    setReferenceCounted(false)
                    acquire(12L * 60L * 60L * 1000L) // renewed on every service start
                }
            }
        } catch (t: Throwable) {
            // Wake locks are a best effort; the service itself is what matters.
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (t: Throwable) {
            // Ignored.
        }
        wakeLock = null
    }

    private fun buildNotification(isPlaying: Boolean): Notification {
        val settings = EqCore.settings(this)
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, EqService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val status = when {
            settings.bypass -> "Bypassed — audio untouched"
            isPlaying -> "Playing with the 20-band equalizer"
            DeviceEq.globalActive -> "System-wide effect attached"
            else -> "Preset: ${settings.presetName} • waiting for audio"
        }
        val detail = when {
            isPlaying -> "LED spectrum live • tap to open PulseEQ"
            settings.systemEqWanted && !DeviceEq.globalActive ->
                "Your ROM blocked the system effect — use the PulseEQ player for guaranteed EQ"
            else -> "Tap to open PulseEQ • Stop to release the effect"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_eq)
            .setContentTitle("PulseEQ • ${settings.presetName}")
            .setContentText(status)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$status\n$detail"))
            .setContentIntent(open)
            .addAction(
                NotificationCompat.Action.Builder(
                    IconCompat.createWithResource(this, R.drawable.ic_stat_eq),
                    "Stop",
                    stop,
                ).build()
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = getString(R.string.notification_channel_description)
                    setShowBadge(false)
                }
            )
        }
    }

    override fun onDestroy() {
        EqCore.player(this).onStateChanged = null
        EqCore.stopGlobal()
        DeviceEq.releaseAllSessions()
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "pulseeq_status"
        const val NOTIFICATION_ID = 4201
        const val ACTION_STOP = "com.universalrp.pulseeq.STOP"

        fun start(context: Context) {
            val intent = Intent(context, EqService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (t: Throwable) {
                // Background start restrictions; the UI start path still works.
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, EqService::class.java))
            } catch (t: Throwable) {
                // Ignored.
            }
        }
    }

}
