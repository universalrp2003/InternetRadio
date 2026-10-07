package com.universalrp.cleansweep.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.universalrp.cleansweep.MainActivity
import com.universalrp.cleansweep.R
import com.universalrp.cleansweep.data.BatteryReader
import com.universalrp.cleansweep.data.BatteryReading
import com.universalrp.cleansweep.data.batteryTimeLabel
import com.universalrp.cleansweep.voice.Announcer

/**
 * The charging card and the spoken plug-in line, in one place.
 *
 * There are two ways CleanSweep can watch a charger, and both must show the same thing:
 *
 *  * [ChargeMonitorService] — the plain foreground service. Best case: a live card that
 *    updates every five seconds while the charger is in.
 *  * [com.universalrp.cleansweep.work.ChargerWatchWorker] — the road that always works.
 *    Android 12 and newer refuse to let a broadcast receiver start a foreground service, so
 *    the plug-in broadcast hands the work to a job, which is allowed to run, and the job
 *    keeps the same card and readings alive from inside itself.
 *
 * Nothing here needs a new permission: the numbers come from the system battery broadcast and
 * the card goes to the notification channel the app already has.
 */
object ChargeNotifier {

    const val CHANNEL_ID = "cleansweep_charging"
    const val NOTIFICATION_ID = 4201

    /** Same prefs file the service and the switches use, so a receiver can read them. */
    private const val PREFS = ChargeMonitorService.PILL_PREFS
    private const val KEY_LAST_PLUG_ANNOUNCE = "last_plug_announce"

    fun read(context: Context): BatteryReading? = try {
        BatteryReader.read(context)
    } catch (e: Exception) {
        null
    }

    fun channel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        try {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Charging status",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Charging power, battery level and time to full while the " +
                        "charger is connected"
                    setShowBadge(false)
                }
            )
        } catch (e: Exception) {
            // Channel already exists.
        }
    }

    /**
     * The ongoing card: charging power in watts, current, voltage, temperature and how long
     * is left. It cannot be swiped away, and it disappears the moment the charger comes out.
     */
    fun build(context: Context, battery: BatteryReading): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
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

        val timeLine = try {
            batteryTimeLabel(battery)
        } catch (e: Exception) {
            null
        } ?: "Time estimate not available yet"

        val details = buildString {
            append(timeLine)
            battery.currentA?.let { append(" • %.2f A".format(it)) }
            battery.voltageV?.let { append(" • %.2f V".format(it)) }
            battery.temperatureC?.let { append(" • %.1f °C".format(it)) }
        }

        val hint = when {
            !battery.charging -> "Unplugged — the reading stops here."
            battery.percent >= 90 -> "Above 90% most phones trickle-charge; leaving it plugged " +
                "overnight is fine."
            battery.percent >= 80 -> "Above 80% charging slows down on purpose — this is normal " +
                "for lithium batteries."
            else -> "Open CleanSweep for CPU temperature, battery health and security."
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle(title)
            .setContentText(details)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$details\n$hint"))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * The plainest possible card. Used only as a last resort when the battery broadcast
     * cannot be read at all — starting a foreground service without a valid notification is
     * a crash, so there is always this to hand.
     */
    fun buildFallback(context: Context): Notification {
        channel(context)
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle("Charging")
            .setContentText("CleanSweep is reading the charging numbers.")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** Shows (or refreshes) the card. Silently does nothing when notifications are off. */
    fun post(context: Context) {
        val battery = read(context) ?: return
        if (!battery.charging) {
            clear(context)
            return
        }
        channel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        try {
            manager.notify(NOTIFICATION_ID, build(context, battery))
        } catch (e: Exception) {
            // Notifications switched off by the user: the voice line still works.
        }
        updatePill(context, battery)
    }

    fun clear(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        try {
            manager?.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            // Nothing to cancel.
        }
        StatusPill.remove()
    }

    /** The little watt reading in the status bar, when the user switched it on and allowed it. */
    fun updatePill(context: Context, battery: BatteryReading) {
        val wanted = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(ChargeMonitorService.PILL_KEY, false)
        if (!wanted || !StatusPill.canDraw(context) || !battery.charging) {
            StatusPill.remove()
            return
        }
        val text = when {
            battery.powerW != null -> "\u26A1 %.1f W".format(battery.powerW)
            battery.currentA != null -> "\u26A1 %.2f A".format(battery.currentA)
            else -> "\u26A1 ${battery.percent}%"
        }
        StatusPill.update(context, text)
    }

    /**
     * Says what the charger is doing, once per plug-in.
     *
     * A phone reports no current for the first few seconds after the plug goes in, so this
     * waits before it reads — otherwise the announcement would always be "not charging yet".
     * The dedupe marker lives in prefs because the service and the fallback job can both ask
     * for the line, and the user must not hear it twice.
     */
    fun announcePluggedIn(context: Context, delayMs: Long = 7_000) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_PLUG_ANNOUNCE, 0L)
        if (System.currentTimeMillis() - last < 90_000L) return
        prefs.edit().putLong(KEY_LAST_PLUG_ANNOUNCE, System.currentTimeMillis()).apply()

        // The caller is a worker or a service scope, never the main thread, but guard anyway.
        try {
            if (delayMs > 0) Thread.sleep(delayMs)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return
        }
        val battery = read(app) ?: return
        if (!battery.charging) return

        val percent = battery.percent.coerceAtLeast(0)
        val watts = battery.powerW
        val timeLine = try {
            batteryTimeLabel(battery)
        } catch (e: Exception) {
            null
        }
        val english = buildString {
            append("Charging started at $percent percent.")
            if (watts != null) append(" " + "%.1f".format(watts) + " watts now.")
            if (timeLine != null) append(" $timeLine.")
        }
        val tamil = buildString {
            append("சார்ஜ் தொடங்கியது — $percent சதவீதம்.")
            if (watts != null) append(" இப்போது " + "%.1f".format(watts) + " வாட்ஸ்.")
        }
        Announcer.speak(app, english, tamil, Announcer.Event.CHARGING)
    }
}
