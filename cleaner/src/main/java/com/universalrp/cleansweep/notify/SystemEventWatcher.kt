package com.universalrp.cleansweep.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.universalrp.cleansweep.voice.Announcer
import com.universalrp.cleansweep.work.PowerVoiceWorker

/** Power events hand speech to a bounded worker: no 25-second goAsync sleep or abandoned TTS init. */
class SystemEventWatcher : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val app = context?.applicationContext ?: return
        val prefs = Announcer.prefs(app)
        when (intent?.action) {
            Intent.ACTION_POWER_CONNECTED -> {
                val now = System.currentTimeMillis()
                val previous = prefs.getLong("voice_power_event_ms", 0)
                val repeat = prefs.getBoolean("voice_power_connected", false) && now >= previous && now - previous < 1_000
                val token = if (repeat) prefs.getString("voice_connection_token", "").orEmpty() else java.util.UUID.randomUUID().toString()
                prefs.edit().putString("voice_connection_token", token).putBoolean("voice_power_connected", true)
                    .putLong("voice_power_event_ms", now).apply()
                if (Announcer.enabled(app) && Announcer.allows(app, Announcer.Event.CHARGING)) PowerVoiceWorker.schedule(app, "plug", token)
                if (Announcer.enabled(app) && Announcer.allows(app, Announcer.Event.CHARGER_IDLE)) PowerVoiceWorker.schedule(app, "idle", token)
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                val token = prefs.getString("voice_connection_token", "").orEmpty().ifBlank { java.util.UUID.randomUUID().toString() }
                prefs.edit().putString("voice_connection_token", token).putBoolean("voice_power_connected", false).apply()
                if (Announcer.enabled(app) && Announcer.allows(app, Announcer.Event.UNPLUGGED_EARLY)) PowerVoiceWorker.schedule(app, "unplug", token)
            }
        }
    }
}
