package com.universalrp.cleansweep.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch

/**
 * Starts and stops the charging monitor the moment the cable goes in or comes out, so the
 * status-bar card (and the little watt reading) are there without opening the app first.
 *
 * Runtime power receivers are registered while the app/its existing monitor is active.
 * Android8+ can restrict/defer manifest implicit-power delivery without a live owner.
 * Visual monitoring is ignored while its switches are off; independent voice jobs remain gated.
 */
class ChargingWatcher : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val app = context?.applicationContext ?: return
        val action = intent?.action ?: return
        if (com.universalrp.cleansweep.data.ChargeHistoryRepo.enabled(app) &&
            (action == Intent.ACTION_POWER_CONNECTED || action == Intent.ACTION_POWER_DISCONNECTED)) {
            val pending = goAsync()
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    com.universalrp.cleansweep.data.ChargeHistoryRepo.record(app,
                        com.universalrp.cleansweep.data.BatteryReader.read(app),
                        plugEvent = action == Intent.ACTION_POWER_CONNECTED,
                        unplugEvent = action == Intent.ACTION_POWER_DISCONNECTED)
                } catch (e: Exception) { /* A history failure must not break the existing monitor. */ }
                finally { pending.finish() }
            }
        }
        val prefs = app.getSharedPreferences(ChargeMonitorService.PILL_PREFS, Context.MODE_PRIVATE)
        // Visual monitoring is independent from voice. SystemEventWatcher schedules short
        // enabled-event voice jobs even when every card/pill/monitor switch is off.
        val persistent = ChargeMonitorService.persistentEnabled(app)
        val wanted = persistent ||
            prefs.getBoolean(ChargeMonitorService.PILL_KEY, com.universalrp.cleansweep.data.MonitorDefaultsPolicy.STATUS_PILL) ||
            prefs.getBoolean(ChargeMonitorService.CARD_KEY, com.universalrp.cleansweep.data.MonitorDefaultsPolicy.CHARGE_CARD)
        if (!wanted) return

        val service = Intent(app, ChargeMonitorService::class.java)
        if (action == Intent.ACTION_POWER_DISCONNECTED) {
            if (persistent) {
                // Do NOT tear down an opted-in data/meter monitor on unplug. Its battery
                // receiver restores the normal pill immediately; the job also covers a dead service.
                com.universalrp.cleansweep.work.ChargerWatchWorker.schedule(app)
            } else {
                com.universalrp.cleansweep.work.ChargerWatchWorker.cancel(app)
                app.stopService(service)
                ChargeNotifier.clear(app)
            }
            return
        }
        // v2.6: a broadcast receiver may no longer start a foreground service on Android 12+.
        // Starting one anyway and swallowing the refusal is why plugging in the charger used
        // to do nothing at all. A receiver *is* allowed to schedule a job, so the job does it:
        // it starts the service when the system lets it, and shows the very same charging card
        // from inside itself when it does not.
        com.universalrp.cleansweep.work.ChargerWatchWorker.schedule(app)
    }
}
