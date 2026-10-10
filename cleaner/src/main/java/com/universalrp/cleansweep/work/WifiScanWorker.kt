package com.universalrp.cleansweep.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.universalrp.cleansweep.MainActivity
import com.universalrp.cleansweep.R
import com.universalrp.cleansweep.data.NetworkScanner
import com.universalrp.cleansweep.voice.Announcer
import java.util.concurrent.TimeUnit

/**
 * Background Wi-Fi scanner: runs periodically (e.g. 15m) when connected to unmetered/Wi-Fi.
 * Compares detected MAC addresses against verified/known devices and alerts user with notification & Tamil TTS.
 */
class WifiScanWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = Announcer.prefs(ctx)
        val bgScanEnabled = prefs.getBoolean(KEY_BG_WIFI_SCAN, true)
        if (!bgScanEnabled) return Result.success()

        val wifi = NetworkScanner.details(ctx)
        if (!wifi.connected || wifi.transport != "Wi-Fi" && wifi.transport != "Ethernet") {
            return Result.success()
        }

        val report = try {
            NetworkScanner.sweep(ctx) { _, _ -> }
        } catch (e: Exception) {
            return Result.success()
        }

        if (report.devices.isEmpty()) return Result.success()

        val verified = prefs.getStringSet("verified_wifi_macs", emptySet()) ?: emptySet()
        val known = (prefs.getStringSet(KEY_SEEN_MACS, emptySet()) ?: emptySet()) + verified

        val fresh = report.devices.filter {
            it.mac != null && it.mac !in known && !it.isSelf && !it.isGateway
        }

        if (known.isNotEmpty() && fresh.isNotEmpty()) {
            val count = fresh.size
            val tamilMsg = if (count == 1) {
                "எச்சரிக்கை! உங்கள் வைஃபை நெட்வொர்க்கில் ஒரு புதிய சாதனம் இணைந்துள்ளது."
            } else {
                "எச்சரிக்கை! உங்கள் வைஃபை நெட்வொர்க்கில் $count புதிய சாதனங்கள் இணைந்துள்ளன."
            }
            val engMsg = if (count == 1) {
                "Alert: 1 new unrecognized device joined your Wi-Fi network."
            } else {
                "Alert: $count new unrecognized devices joined your Wi-Fi network."
            }

            notifyIntruder(ctx, engMsg, tamilMsg)
            Announcer.speakTamil(ctx, tamilMsg, Announcer.Event.NEW_DEVICE)
        }

        // Update seen macs
        val allSeen = known + report.devices.mapNotNull { it.mac }
        prefs.edit().putStringSet(KEY_SEEN_MACS, allSeen).apply()

        return Result.success()
    }

    private fun notifyIntruder(ctx: Context, engMsg: String, tamilMsg: String) {
        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_WIFI_INTRUDER,
                        "Wi-Fi Network Alerts",
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply { description = "Alerts when an unknown device joins your Wi-Fi" }
                )
            } catch (e: Exception) {
                // Channel already exists
            }
        }

        val open = PendingIntent.getActivity(
            ctx,
            91,
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(ctx, CHANNEL_WIFI_INTRUDER)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle("CleanSweep Wi-Fi Alert")
            .setContentText(tamilMsg)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$tamilMsg\n$engMsg"))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // Ignored
        }
    }

    companion object {
        const val UNIQUE_NAME = "cleansweep_bg_wifi_scanner"
        const val KEY_BG_WIFI_SCAN = "bg_wifi_scan_enabled"
        private const val KEY_SEEN_MACS = "wifi_seen_macs"
        private const val CHANNEL_WIFI_INTRUDER = "cs_wifi_intruder"
        private const val NOTIFICATION_ID = 7109

        fun schedule(context: Context) {
            cancel(context)
        }

        fun cancel(context: Context) {
            try {
                WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_NAME)
            } catch (e: Exception) {
                // Nothing to cancel
            }
        }
    }
}
