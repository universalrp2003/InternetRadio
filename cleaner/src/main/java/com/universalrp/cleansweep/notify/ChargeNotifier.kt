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
import com.universalrp.cleansweep.data.DataUsageTracker
import com.universalrp.cleansweep.data.batteryTimeLabel
import com.universalrp.cleansweep.data.batteryTimeMinutes
import com.universalrp.cleansweep.voice.Announcer

/**
 * The ongoing mobile data & charging status card and spoken announcements.
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
                    "Mobile data & battery status",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Live mobile data usage, custom data pack balance and battery metrics"
                    setShowBadge(false)
                }
            )
        } catch (e: Exception) {
            // Channel already exists.
        }
    }

    /**
     * Ongoing notification card:
     * Prominently displays today's real mobile data usage,
     * pack status (Used / Quota & Remaining GB left), and secondary battery/charging stats.
     */
    fun build(context: Context, battery: BatteryReading): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val dataUsage = try {
            DataUsageTracker.getUsageInfo(context)
        } catch (e: Exception) {
            null
        }

        val isUnlimited = dataUsage?.isUnlimited5g == true
        val title = if (dataUsage != null && !isUnlimited) {
            "Today's Mobile Data: ${dataUsage.formattedToday} • Pack: ${dataUsage.formattedRemaining} left"
        } else if (dataUsage != null && isUnlimited) {
            if (battery.charging) "Unlimited 5G • Charging ${battery.percent}%" else "Unlimited 5G Plan • Battery ${battery.percent}%"
        } else if (battery.charging) {
            val watts = battery.powerW
            if (watts != null) {
                "Charging at %.1f W • %d%%".format(watts, battery.percent.coerceAtLeast(0))
            } else {
                "Charging • ${battery.percent}%"
            }
        } else {
            "${battery.percent}% • ${battery.statusLabel}"
        }

        val timeLine = try {
            batteryTimeLabel(battery)
        } catch (e: Exception) {
            null
        } ?: "Battery: ${battery.percent}%"

        val netQuality = try {
            com.universalrp.cleansweep.data.LiveNetworkQuality.measure(context)
        } catch (e: Exception) {
            null
        }

        val details = buildString {
            if (netQuality != null && (netQuality.isWifi || netQuality.isMobile)) {
                val netType = if (netQuality.isWifi) "Wi-Fi" else "Mobile Data"
                val pingInfo = netQuality.pingMs?.let { "Ping: ${it}ms" } ?: ""
                val jitterInfo = netQuality.jitterMs?.let { " • Jitter: ${it}ms" } ?: ""
                append("[$netType] Quality: ${netQuality.labelEnglish} ($pingInfo$jitterInfo)\n")
                append("Live: ↓ ${netQuality.formattedRxSpeed} • ↑ ${netQuality.formattedTxSpeed}\n")
            }
            if (dataUsage != null && !isUnlimited) {
                append("Pack Used: ${dataUsage.formattedPackTotal} / ${dataUsage.formattedPackLimit} • Left: ${dataUsage.formattedRemaining}\n")
            } else if (isUnlimited) {
                append("Unlimited 5G Active • Quota counting paused\n")
            }
            if (battery.charging) {
                append("Charging: $timeLine")
                battery.powerW?.let { append(" • %.1f W".format(it)) }
                battery.currentA?.let { append(" • %.2f A".format(it)) }
                battery.voltageV?.let { append(" • %.2f V".format(it)) }
                battery.temperatureC?.let { append(" • %.1f °C".format(it)) }
            } else {
                append("Battery: ${battery.percent}% • ${battery.statusLabel}")
                battery.temperatureC?.let { append(" • %.1f °C".format(it)) }
            }
        }

        val hint = when {
            dataUsage != null -> "Mobile data usage is strictly tracked from cellular networks (zero Wi-Fi)."
            !battery.charging -> "Unplugged — monitoring active."
            battery.percent >= 90 -> "Above 90% most phones trickle-charge; leaving it plugged overnight is fine."
            battery.percent >= 80 -> "Above 80% charging slows down on purpose — this is normal for lithium batteries."
            else -> "Open CleanSweep for CPU temperature, battery health and security."
        }

        val stopIntent = Intent(context, ChargeMonitorService::class.java).apply {
            action = ChargeMonitorService.ACTION_STOP_SERVICE
        }
        val stopPending = PendingIntent.getService(
            context,
            4202,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val (netIconRes, netIconColor) = when (netQuality?.grade) {
            com.universalrp.cleansweep.data.LiveNetworkQuality.QualityGrade.TOP_QUALITY ->
                Pair(R.drawable.ic_stat_net_gold, 0xFFFFD700.toInt())
            com.universalrp.cleansweep.data.LiveNetworkQuality.QualityGrade.MEDIUM_QUALITY ->
                Pair(R.drawable.ic_stat_net_green, 0xFF00E676.toInt())
            com.universalrp.cleansweep.data.LiveNetworkQuality.QualityGrade.BAD_QUALITY ->
                Pair(R.drawable.ic_stat_net_red, 0xFFFF3B30.toInt())
            else ->
                Pair(R.drawable.ic_stat_data, 0xFF38BDF8.toInt())
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(netIconRes)
            .setColor(netIconColor)
            .setContentTitle(title)
            .setContentText(details)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$details\n$hint"))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(openApp)
            .addAction(0, "Stop", stopPending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * The plainest possible card. Used only as a last resort when the battery broadcast
     * cannot be read at all.
     */
    fun buildFallback(context: Context): Notification {
        channel(context)
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dataUsage = try {
            DataUsageTracker.getUsageInfo(context)
        } catch (e: Exception) {
            null
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_data)
            .setContentTitle("CleanSweep Monitoring")
            .setContentText("CleanSweep is monitoring mobile data and device stats.")
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

    /** The status bar pill: shows today's mobile data or charging watts in the status bar. */
    fun updatePill(context: Context, battery: BatteryReading) {
        val wanted = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(ChargeMonitorService.PILL_KEY, false)
        if (!wanted || !StatusPill.canDraw(context)) {
            StatusPill.remove()
            return
        }
        val dataUsage = try {
            DataUsageTracker.getUsageInfo(context)
        } catch (e: Exception) {
            null
        }
        val isUnlimited = dataUsage?.isUnlimited5g == true
        val text = when {
            isUnlimited && battery.charging && battery.powerW != null ->
                "5G • ⚡ %.1f W".format(battery.powerW)
            isUnlimited ->
                "5G Unlimited"
            dataUsage != null && battery.charging && battery.powerW != null ->
                "${dataUsage.formattedToday} • ⚡ %.1f W".format(battery.powerW)
            dataUsage != null ->
                dataUsage.formattedToday
            battery.charging && battery.powerW != null ->
                "⚡ %.1f W".format(battery.powerW)
            battery.charging && battery.currentA != null ->
                "⚡ %.2f A".format(battery.currentA)
            else ->
                "${battery.percent}%"
        }

        val packPercent = if (dataUsage != null && dataUsage.packLimitBytes > 0 && !isUnlimited) {
            val consumed = (dataUsage.packTotalMobileBytes.toDouble() / dataUsage.packLimitBytes.toDouble()) * 100.0
            consumed.coerceIn(0.0, 100.0).toInt()
        } else {
            null
        }

        StatusPill.update(context, text, packPercent)
    }

    /**
     * Says what the charger is doing, once per plug-in.
     */
    fun announcePluggedIn(context: Context, delayMs: Long = 800) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_PLUG_ANNOUNCE, 0L)
        // Deduplicate plug-ins within 4 seconds
        if (System.currentTimeMillis() - last < 4_000L) return
        prefs.edit().putLong(KEY_LAST_PLUG_ANNOUNCE, System.currentTimeMillis()).apply()

        // Give a short pause for the hardware connection to establish
        try {
            if (delayMs > 0) Thread.sleep(delayMs)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return
        }
        val battery = read(app) ?: return

        // 1. Overheat check on plug-in: warn immediately if battery is dangerously hot
        val tempC = battery.temperatureC
        if (tempC != null && tempC >= 42.0f) {
            val tempRound = tempC.toInt()
            val warnTamil = "எச்சரிக்கை! போன் மிக அதிக வெப்பமாக உள்ளது ($tempRound டிகிரி). வேகமாக சார்ஜ் செய்வதைத் தவிர்க்கவும் அல்லது சார்ஜரை அகற்றவும்."
            Announcer.speakTamil(app, warnTamil, Announcer.Event.OVERHEAT)
            return
        }

        val percent = battery.percent.coerceAtLeast(0)
        val watts = battery.powerW
        // Remaining time estimate: only announce when charging is stable and verified
        val currentA = battery.currentA
        val timeEstimate = try {
            val mins = batteryTimeMinutes(battery)
            if (mins != null && mins in 2..480 && currentA != null && currentA > 0.25f && percent in 5..94) {
                val h = mins / 60
                val m = mins % 60
                if (h > 0) "$h மணி $m நிமிடம்" else "$m நிமிடம்"
            } else null
        } catch (e: Exception) {
            null
        }

        // Spoken immediately in Tamil on plug-in:
        val tamil = buildString {
            if (watts != null && watts < 4.0f && percent < 90) {
                append("மெதுவான சார்ஜிங்: $percent சதவீதம்.")
            } else {
                append("சார்ஜர் இணைக்கப்பட்டது: $percent சதவீதம்.")
            }
            if (timeEstimate != null) {
                append(" நிறைவடைய $timeEstimate.")
            }
        }
        Announcer.speakTamil(app, tamil, Announcer.Event.CHARGING)
    }
}
