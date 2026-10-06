package com.universalrp.cleansweep.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import com.universalrp.cleansweep.data.BatteryReader
import com.universalrp.cleansweep.voice.Announcer

/**
 * The instant broadcasts CleanSweep listens to, so it can speak up the moment something
 * happens instead of waiting for the hourly pass:
 *
 *  * **Wi-Fi ⇄ mobile data changed** — "You are now on mobile data, which uses your plan."
 *    (off by default; a phone that switches on its own would otherwise talk all day)
 *  * **Unplugged early** — the charger came out before 90%, and only after the hour the user
 *    set (06:00 by default) so it never fires while they sleep.
 *  * **Plugged in but not charging** — a worn cable or charger often reports "connected" and
 *    still delivers nothing; said once, a few seconds later, when that is provably the case.
 *
 * Every one of them sits behind its own switch in Settings → Voice & daily watch, and behind
 * the quiet-hours window for anything the user did not ask for right now.
 */
class SystemEventWatcher : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val app = context?.applicationContext ?: return
        when (intent?.action) {
            ConnectivityManager.CONNECTIVITY_ACTION -> networkChange(app)
            Intent.ACTION_POWER_DISCONNECTED -> unplugged(app)
            Intent.ACTION_POWER_CONNECTED -> idleChargerCheck(app)
        }
    }

    /** Wi-Fi ⇄ mobile data. Off by default: the switch lives in the voice screen. */
    private fun networkChange(context: Context) {
        if (!Announcer.allows(context, Announcer.Event.NETWORK_CHANGE)) return
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return
        val caps = try {
            connectivity.activeNetwork?.let { connectivity.getNetworkCapabilities(it) }
        } catch (e: Exception) {
            null
        }
        val onWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val onMobile = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val now = when {
            onMobile -> "mobile"
            onWifi -> "wifi"
            else -> "none"
        }
        val prefs = Announcer.prefs(context)
        val before = prefs.getString(KEY_LAST_TRANSPORT, "")
        if (before == now) return
        prefs.edit().putString(KEY_LAST_TRANSPORT, now).apply()
        // Nothing to say the first time we ever look, or while going offline.
        if (before.isNullOrBlank() || now == "none") return
        when (now) {
            "mobile" -> Announcer.speak(
                context,
                "You are now on mobile data. This uses your data plan.",
                "இப்போது மொபைல் டேட்டாவில் உள்ளீர்கள். இது உங்கள் தரவுத் திட்டத்தைப் பயன்படுத்தும்.",
                Announcer.Event.NETWORK_CHANGE,
            )
            "wifi" -> Announcer.speak(
                context,
                "You are back on Wi-Fi.",
                "நீங்கள் மீண்டும் Wi-Fi-ல் உள்ளீர்கள்.",
                Announcer.Event.NETWORK_CHANGE,
            )
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
        Announcer.speak(
            context,
            "Charger removed at ${battery.percent} percent. If you can, keep it plugged in a little longer.",
            "சார்ஜர் ${battery.percent} சதவீதத்திலேயே கழற்றப்பட்டது. முடிந்தால் இன்னும் சிறிது நேரம் இணைத்து வைக்கவும்.",
            Announcer.Event.UNPLUGGED_EARLY,
        )
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
                    Announcer.speak(
                        context,
                        "The charger is connected but the battery is not charging. Check the cable or the plug.",
                        "சார்ஜர் இணைக்கப்பட்டுள்ளது, ஆனால் பேட்டரி சார்ஜ் ஆகவில்லை. கேபிள் அல்லது பிளக்கைச் சரிபார்க்கவும்.",
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

    companion object {
        private const val KEY_LAST_TRANSPORT = "last_transport"
    }
}
