package com.universalrp.cleansweep.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.universalrp.cleansweep.data.BatteryReader
import com.universalrp.cleansweep.data.DataUsageTracker
import com.universalrp.cleansweep.widget.CleanSweepWidget
import com.universalrp.cleansweep.work.HealthWatchWorker

/**
 * Handles device boot and app updates (BOOT_COMPLETED / MY_PACKAGE_REPLACED).
 * Automatically restores background monitoring, home screen widget,
 * and hourly health watch without requiring the user to open the app manually.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val app = context?.applicationContext ?: return
        FloatingVolumeControl.sync(
            app,
            app.getSharedPreferences(FloatingVolumeControl.PREFS, Context.MODE_PRIVATE)
                .getBoolean(FloatingVolumeControl.KEY_ENABLED, false),
        )

        // 1. Refresh widget immediately on startup
        try {
            CleanSweepWidget.refresh(app)
        } catch (e: Exception) {
            // Widget refresh error ignored
        }

        // 2. Arm the hourly health and intruder scan worker. It is cheap and respects Doze,
        //    and 2.42 arms it whenever any part of the background watch is wanted — the
        //    battery-health monitor's own switch included, not only voice and the daily check.
        try {
            HealthWatchWorker.schedule(app)
        } catch (e: Exception) {
            // WorkManager error ignored
        }

        // 3. Re-arm the ongoing charging meter / background monitor.
        //    2.42: both default ON, so this must also run when the owner never wrote the
        //    preference. Before this, MY_PACKAGE_REPLACED and BOOT_COMPLETED only restored the
        //    card when the switch had been tapped on by hand, so the status-bar meter stayed
        //    dark after every update until the app was opened again. A saved OFF is still an
        //    OFF: these readers resolve against MonitorDefaultsPolicy, never against a literal.
        val cardEnabled = ChargeMonitorService.cardEnabled(app)
        val persistentWanted = ChargeMonitorService.persistentEnabled(app)
        val battery = try {
            BatteryReader.read(app)
        } catch (e: Exception) {
            null
        }

        if (com.universalrp.cleansweep.data.MonitorDefaultsPolicy.rearmAfterUpdateOrBoot(cardEnabled, persistentWanted) ||
            (battery?.powerConnected == true && cardEnabled)
        ) {
            try {
                if (!ChargeMonitorService.start(app)) {
                    com.universalrp.cleansweep.work.ChargerWatchWorker.schedule(app)
                }
            } catch (e: Exception) {
                // If Android restricts background start immediately after boot, ChargerWatchWorker handles it
                com.universalrp.cleansweep.work.ChargerWatchWorker.schedule(app)
            }
        }

        // 4. v2.47: re-arm the 4G/5G watch if the owner turned on split counting or
        //    radio-change announcements. Without this the counters silently stop at reboot
        //    and the announcement switch looks broken until the app is opened again.
        if (com.universalrp.cleansweep.notify.RadioWatchService.needed(app)) {
            runCatching { com.universalrp.cleansweep.notify.RadioWatchService.start(app) }
        }
    }
}
