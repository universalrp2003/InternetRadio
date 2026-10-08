package com.universalrp.cleansweep.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import com.universalrp.cleansweep.data.BatteryReader
import com.universalrp.cleansweep.voice.Announcer

/**
 * The instant broadcasts CleanSweep listens to, so it can speak up the moment something
 * happens instead of waiting for the hourly pass:
 *
 *  * **Unplugged early** — the charger came out before 90%, and only after the hour the user
 *    set (06:00 by default) so it never fires while they sleep.
 *  * **Plugged in but not charging** — a worn cable or charger often reports "connected" and
 *    still delivers nothing; said once, a few seconds later, when that is provably the case.
 *
 * v2.10: the Wi-Fi ⇄ mobile-data warning used to live here, but Android 8+ never delivers
 * CONNECTIVITY_ACTION to manifest receivers, so it was dead on every supported phone. It
 * now lives in the hourly [com.universalrp.cleansweep.work.HealthWatchWorker] pass instead.
 *
 * Every one of them sits behind its own switch in Settings → Voice & daily watch, and behind
 * the quiet-hours window for anything the user did not ask for right now.
 */
class SystemEventWatcher : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val app = context?.applicationContext ?: return
        when (intent?.action) {
            Intent.ACTION_POWER_DISCONNECTED -> unplugged(app)
            Intent.ACTION_POWER_CONNECTED -> pluggedIn(app)
        }
    }

    /** The cable came out before the charge was finished. */
    private fun unplugged(context: Context) {
        if (!Announcer.allows(context, Announcer.Event.UNPLUGGED_EARLY)) return
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        if (hour < Announcer.unplugStartHour(context)) return
        val battery = try {
            BatteryReader.read(context)
        } catch (e: Exception) {
            return
        }
        if (battery.percent >= Announcer.UNPLUG_BEFORE_PERCENT || battery.percent <= 0) return
        Announcer.speakTamil(
            context,
            "சார்ஜர் அகற்றப்பட்டது: ${battery.percent} சதவீதம்.",
            Announcer.Event.UNPLUGGED_EARLY,
        )
    }

    /**
     * The charger went in. v2.6: this is the trigger the user asked for — the app comes awake,
     * shows the charging card with the real watts, and says what is happening, all while the
     * phone is in the background. The job does the work (a receiver is not allowed to start a
     * foreground service on Android 12+), and it is the same job the charging watcher uses, so
     * the two receivers cannot double up.
     */
    private fun pluggedIn(context: Context) {
        if (ChargeMonitorService.cardEnabled(context) ||
            Announcer.allows(context, Announcer.Event.CHARGING)
        ) {
            com.universalrp.cleansweep.work.ChargerWatchWorker.schedule(context)
        }
        idleChargerCheck(context)
    }

    /**
     * "Connected but not charging": an aged cable or a weak charger often shows as plugged in
     * while delivering nothing. Wait a few seconds for the reading to settle, then speak only
     * if the battery really is not charging and no current is flowing.
     */
    private fun idleChargerCheck(context: Context) {
        if (!Announcer.allows(context, Announcer.Event.CHARGER_IDLE)) return
        val pending = goAsync()
        Thread {
            try {
                Thread.sleep(25_000)
                val battery = try {
                    BatteryReader.read(context)
                } catch (e: Exception) {
                    null
                }
                val noCurrent = battery?.currentA?.let { it > -0.05f && it < 0.05f } ?: false
                if (battery != null && battery.charging && noCurrent && battery.percent < 100) {
                    Announcer.speakTamil(
                        context,
                        "சார்ஜர் இணைக்கப்பட்டுள்ளது, ஆனால் சார்ஜ் ஆகவில்லை. கேபிளை சரிபார்க்கவும்.",
                        Announcer.Event.CHARGER_IDLE,
                    )
                }
            } catch (e: Exception) {
                // Nothing to do: the next plug-in will try again.
            } finally {
                try {
                    pending.finish()
                } catch (e: Exception) {
                    // Already finished.
                }
            }
        }.start()
    }
}
