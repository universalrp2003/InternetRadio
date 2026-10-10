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

        // 1. Refresh widget immediately on startup
        try {
            CleanSweepWidget.refresh(app)
        } catch (e: Exception) {
            // Widget refresh error ignored
        }

        // 2. Ensure hourly health and intruder scan worker is active
        try {
            HealthWatchWorker.schedule(app)
        } catch (e: Exception) {
            // WorkManager error ignored
        }

        // 3. Check if persistent monitoring is enabled by the user and restore service
        val prefs = app.getSharedPreferences(ChargeMonitorService.PILL_PREFS, Context.MODE_PRIVATE)
        val persistentWanted = prefs.getBoolean(ChargeMonitorService.PERSISTENT_KEY, false)
        val battery = try {
            BatteryReader.read(app)
        } catch (e: Exception) {
            null
        }

        if (persistentWanted || (battery?.charging == true && ChargeMonitorService.cardEnabled(app))) {
            try {
                ChargeMonitorService.start(app)
            } catch (e: Exception) {
                // If Android restricts background start immediately after boot, ChargerWatchWorker handles it
                com.universalrp.cleansweep.work.ChargerWatchWorker.schedule(app)
            }
        }
    }
}
