package com.universalrp.cleansweep.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.universalrp.cleansweep.MainActivity
import com.universalrp.cleansweep.R
import com.universalrp.cleansweep.data.BatteryReader
import com.universalrp.cleansweep.data.batteryTimeLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The status-bar charging card the user asked for.
 *
 * While the charger is connected a low-importance ongoing notification shows the real
 * numbers: charging power in watts, how much current is going in, battery temperature,
 * the percentage and an estimated time to full. Unplug and it goes away on its own.
 *
 * The estimate is honest arithmetic, not a promise: remaining charge ÷ charging current
 * at the current rate. Charging slows down above ~80% (that is how lithium batteries
 * work), so the notification also says when it is in that slow final phase.
 */
class ChargeMonitorService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var ticker: Job? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = plugged != 0 ||
                status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            if (!charging) {
                // Unplugged: the notification has done its job.
                stopSelf()
            } else {
                update()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        startForeground(NOTIFICATION_ID, buildNotification())
        ticker = scope.launch {
            while (isActive) {
                delay(5_000)
                update()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        update()
        return START_STICKY
    }

    private fun update() {
        val notification = buildNotification()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // If notifications were switched off, there is nothing to show.
        }
    }

    private fun buildNotification(): Notification {
        val battery = BatteryReader.read(this)
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = when {
            battery.charging && battery.percent >= 100 -> "Battery full — unplug to save power"
            battery.charging -> {
                val watts = battery.powerW
                if (watts != null) {
                    "Charging at %.1f W • %d%%".format(watts, battery.percent.coerceAtLeast(0))
                } else {
                    "Charging • ${battery.percent}%"
                }
            }
            else -> "${battery.percent}% • ${battery.statusLabel}"
        }

        val timeLine = batteryTimeLabel(battery) ?: "Time estimate not available yet"
        val details = buildString {
            append(timeLine)
            battery.currentA?.let { append(" • %.2f A".format(it)) }
            battery.voltageV?.let { append(" • %.2f V".format(it)) }
            battery.temperatureC?.let { append(" • %.1f °C".format(it)) }
        }

        val hint = when {
            !battery.charging -> "Unplugged — stopping."
            battery.percent >= 90 -> "Above 90% most phones trickle-charge; leaving it plugged overnight is fine."
            battery.percent >= 80 -> "Above 80% charging slows down on purpose — this is normal for lithium batteries."
            else -> "Open CleanSweep for CPU temperature, battery health and security."
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle(title)
            .setContentText(details)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$details\n$hint")
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Charging status",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Charging power, battery level and time to full while the charger is connected"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // Already gone.
        }
        scope.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "cleansweep_charging"
        const val NOTIFICATION_ID = 4201

        /** Starts or stops the monitor to match the current charger state. */
        fun sync(context: Context, enabled: Boolean) {
            val intent = Intent(context, ChargeMonitorService::class.java)
            if (!enabled) {
                context.stopService(intent)
                return
            }
            val battery = BatteryReader.read(context)
            if (!battery.charging) {
                context.stopService(intent)
                return
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Android 12+ can refuse a background start; the notification simply
                // appears the next time the app is opened while charging.
            }
        }
    }
}
