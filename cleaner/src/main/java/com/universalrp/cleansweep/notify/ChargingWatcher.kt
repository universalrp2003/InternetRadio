package com.universalrp.cleansweep.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.universalrp.cleansweep.voice.Announcer

/**
 * Starts and stops the charging monitor the moment the cable goes in or comes out, so the
 * status-bar card (and the little watt reading) are there without opening the app first.
 *
 * ACTION_POWER_CONNECTED / DISCONNECTED are two of the few broadcasts an app may still
 * receive from the manifest, and they are ignored completely while both switches are off.
 */
class ChargingWatcher : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val app = context?.applicationContext ?: return
        val action = intent?.action ?: return
        val prefs = app.getSharedPreferences(ChargeMonitorService.PILL_PREFS, Context.MODE_PRIVATE)
        // The service runs when the user wants any of the three: the status-bar card, the
        // watt pill, or the spoken "charging started" line.
        val voiceOn = prefs.getBoolean(Announcer.KEY_ON, true) &&
            prefs.getBoolean(Announcer.KEY_CHARGE, true)
        val wanted = prefs.getBoolean(ChargeMonitorService.PILL_KEY, false) ||
            prefs.getBoolean(ChargeMonitorService.CARD_KEY, true) ||
            voiceOn
        if (!wanted) return

        val service = Intent(app, ChargeMonitorService::class.java)
        if (action == Intent.ACTION_POWER_DISCONNECTED) {
            app.stopService(service)
            StatusPill.remove()
            return
        }
        ChargeMonitorService.sync(app, true)
    }
}
