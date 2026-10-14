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
import com.universalrp.cleansweep.data.ChargingHonestyPolicy
import com.universalrp.cleansweep.data.DataUsageTracker
import com.universalrp.cleansweep.data.ThermalWarningPolicy
import com.universalrp.cleansweep.data.LiveNetworkQuality
import com.universalrp.cleansweep.data.Lang
import com.universalrp.cleansweep.data.DisplayTime
import com.universalrp.cleansweep.data.LiveChargingEta
import com.universalrp.cleansweep.data.batteryTimeMinutes
import com.universalrp.cleansweep.data.formatMinutes
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
        BatteryReader.read(context).also { runCatching { com.universalrp.cleansweep.data.ChargeHistoryRepo.record(context, it) } }
    } catch (e: Exception) {
        null
    }

    /** True while the screen is on. A phone without a readable PowerManager is treated as screen-on: that is the wording that promises less. */
    private fun screenOn(context: Context): Boolean = try {
        (context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager)?.isInteractive ?: true
    } catch (e: Exception) {
        true
    }

    fun channel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        try {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    Lang.forContext(context, "Mobile data & battery status"),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = Lang.forContext(context, "Observed mobile data usage, configured quota and battery readings")
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
        val language = Lang.languageIn(context)
        fun t(text: String, vararg args: Any) = Lang.translate(language, text, *args)
        val percent = com.universalrp.cleansweep.widget.WidgetReadingPolicy.percent(battery.percent, battery.reportedLevel, battery.reportedScale)
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val dataUsage = if (battery.powerConnected) null else try {
            DataUsageTracker.getUsageInfo(context)
        } catch (e: Exception) {
            null
        }

        val isUnlimited = dataUsage?.isUnlimited5g == true
        val chargeStatusText = when {
            battery.percent >= 100 -> t("Full")
            battery.charging -> LiveChargingEta.estimate(battery)?.let { t("ETA %s", formatMinutes(it.minutesToFull)) }
            else -> null
        }
        val chargingContent = StatusPillContent.resolve(
            powerConnected = battery.powerConnected,
            powerW = battery.powerW,
            charging = battery.charging,
            chargeStatusText = chargeStatusText,
            estimatingText = t("Estimating"),
            unavailableText = t("Unavailable"),
        )
        val title = when {
            battery.powerConnected -> chargingContent.text // Measurement/unit, not an English-only sentence.
            dataUsage != null && !dataUsage.readingAvailable -> t("Mobile usage unavailable · Battery %s", percent)
            dataUsage != null && !isUnlimited -> t("Mobile data today: %s · Pack left: %s", dataUsage.formattedToday, dataUsage.formattedRemaining)
            isUnlimited -> t("Unlimited 5G · user-set · Battery %s", percent)
            else -> t("Battery %s · %s", percent, t(battery.statusLabel))
        }
        val minutes = runCatching { batteryTimeMinutes(battery) }.getOrNull()
        val duration = minutes?.let { formatMinutes(it) }
        val timeLine = when {
            battery.percent == 100 -> t("Full")
            battery.charging && duration != null -> t("Estimated to 100% from recent charging: %s", duration)
            battery.charging -> t("Estimating charging time; more observations are needed.")
            battery.powerConnected -> t("Charging time unavailable while the battery is not charging.")
            else -> t("Battery %s · %s", percent, t(battery.statusLabel))
        }
        val netQuality = if (battery.powerConnected) null else runCatching { LiveNetworkQuality.measure(context) }.getOrNull()
        val details = buildString {
            if (netQuality != null && (netQuality.isWifi || netQuality.isMobile)) {
                val type = t(if (netQuality.isWifi) "Wi-Fi" else "Mobile data")
                appendLine(t("%s · measured quality: %s", type, t(netQuality.labelEnglish)))
                netQuality.pingMs?.let { append(t("Ping: %s ms", it)) }
                netQuality.jitterMs?.let { append(t(" · Jitter: %s ms", it)) }
                appendLine()
                appendLine(t("Live traffic: ↓ %s · ↑ %s", netQuality.formattedRxSpeed, netQuality.formattedTxSpeed))
            }
            if (dataUsage != null && !isUnlimited) {
                appendLine(if (!dataUsage.readingAvailable) t("Mobile statistics unavailable")
                    else t("Pack used: %s / %s · left: %s", dataUsage.formattedPackTotal, dataUsage.formattedPackLimit, dataUsage.formattedRemaining))
            } else if (isUnlimited) appendLine(t("Unlimited 5G mode is user-set; quota display is hidden."))
            if (battery.powerConnected) {
                append(t("Charger connected: %s", timeLine))
                // 2.41 honest watts/amps: each number is named for what it actually is —
                // measured at the battery — and a sensor that reported nothing is left out
                // instead of being replaced by a different unit or by zero.
                val measuredWatts = ChargingHonestyPolicy.batterySideWatts(
                    battery.charging,
                    battery.powerConnected,
                    battery.voltageV,
                    battery.currentA,
                )
                ChargingHonestyPolicy.wattsText(measuredWatts)?.let {
                    appendLine()
                    append(t("Charging power measured at the battery: %s", it))
                }
                ChargingHonestyPolicy.ampsText(ChargingHonestyPolicy.batterySideAmps(battery.currentA))?.let {
                    appendLine()
                    append(t("Battery-side current: %s", it))
                }
                ChargingHonestyPolicy.voltsText(battery.voltageV)?.let {
                    appendLine()
                    append(t("Battery voltage (Android broadcast): %s", it))
                }
                battery.temperatureC?.let { appendLine(); append(t("Battery temperature (Android broadcast): %s", "%.1f °C".format(java.util.Locale.US, it))) }
            } else {
                append(t("Battery %s · %s", percent, t(battery.statusLabel)))
                battery.temperatureC?.let { append(" · "); append(t("Battery temperature (Android broadcast): %s", "%.1f °C".format(java.util.Locale.US, it))) }
            }
            append("\n" + t("Card updated: %s", DisplayTime.clock(System.currentTimeMillis(), seconds = true)))
        }
        val hint = t(when {
            battery.powerConnected && battery.powerW == null -> "Charging power is not reported by this phone. No wattage is guessed."
            battery.powerConnected -> "Watts are measured at the battery, not at the charger adapter."
            dataUsage != null && !dataUsage.readingAvailable -> "Mobile data statistics are unavailable; no usage or remaining quota is guessed."
            dataUsage?.isPartial == true -> "Partial observed mobile counter; gaps/reboots can miss traffic. Not the carrier's official balance. Wi-Fi excluded."
            dataUsage != null -> "Mobile data usage excludes Wi-Fi and is not the carrier's official balance."
            else -> "Monitoring active. Open Live Guard for available phone readings."
        })
        // 2.41: a 44 °C battery (owner-set threshold) gets one plain notice — no cause is
        // claimed and no diagnosis is made — and the card never repeats it silently.
        val thermal = ThermalWarningPolicy.decide(battery.temperatureC, null, null)
        val heatLine = when {
            !thermal.warn -> null
            thermal.sensor == ThermalWarningPolicy.Sensor.CPU -> t(
                "A heat sensor is at or above its warning level (%s). Let the phone cool before heavy use; this is a temperature notice, not a diagnosis.",
                ThermalWarningPolicy.celsiusText(thermal.celsius) ?: "—",
            )
            else -> t(
                "Battery temperature is at or above 44 °C (%s). Unusually warm — review charging conditions; this is a temperature notice, not a diagnosis.",
                ThermalWarningPolicy.celsiusText(thermal.celsius) ?: "—",
            )
        }
        // 2.41 screen-on/off note: the phone's own draw changes with the screen, the cable does not.
        val screenLine = when (ChargingHonestyPolicy.screenNote(battery.powerConnected, screenOn(context))) {
            ChargingHonestyPolicy.ScreenNote.SCREEN_ON_DRAWS_POWER -> t(
                "Screen is on, so the phone itself is drawing power. The charging figure is measured at the battery and can read lower than with the screen off — it is not the charger's output.",
            )
            ChargingHonestyPolicy.ScreenNote.SCREEN_OFF_LOW_DRAW -> t(
                "Screen is off, so the phone is drawing little. The charging figure is measured at the battery, not the charger's output.",
            )
            ChargingHonestyPolicy.ScreenNote.NOT_CHARGING -> null
        }
        val hintText = listOfNotNull(heatLine, hint, screenLine).joinToString("\n")

        val stopIntent = Intent(context, ChargeMonitorService::class.java).apply {
            action = ChargeMonitorService.ACTION_STOP_SERVICE
        }
        val stopPending = PendingIntent.getService(
            context,
            4202,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val (netIconRes, netIconColor) = if (battery.powerConnected) {
            Pair(R.drawable.ic_stat_battery, 0xFF38BDF8.toInt())
        } else when (netQuality?.grade) {
            com.universalrp.cleansweep.data.LiveNetworkQuality.QualityGrade.TOP_QUALITY ->
                Pair(R.drawable.ic_stat_net_green, 0xFF00E676.toInt()) // Very Good Quality = Green
            com.universalrp.cleansweep.data.LiveNetworkQuality.QualityGrade.MEDIUM_QUALITY ->
                Pair(R.drawable.ic_stat_net_gold, 0xFFFFD600.toInt())  // Medium Quality = Yellow
            com.universalrp.cleansweep.data.LiveNetworkQuality.QualityGrade.BAD_QUALITY ->
                Pair(R.drawable.ic_stat_net_red, 0xFFFF3B30.toInt())   // Bad Quality = Red
            else ->
                Pair(R.drawable.ic_stat_data, 0xFF38BDF8.toInt())
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(netIconRes)
            .setColor(netIconColor)
            .setContentTitle(title)
            .setContentText(if (battery.powerConnected) t("Battery-side charging power") else details)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$details\n$hintText"))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(openApp)
            .addAction(0, t("Stop"), stopPending)
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
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_data)
            .setContentTitle(Lang.forContext(context, "Live Guard monitor"))
            .setContentText(Lang.forContext(context, "Battery reading unavailable. Monitoring is active; no value is guessed."))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** Shows (or refreshes) the card. Silently does nothing when notifications are off. */
    fun post(context: Context, stillCurrent: () -> Boolean = { true }) {
        if (!stillCurrent()) return
        val battery = read(context) ?: return
        if (!battery.powerConnected && !ChargeMonitorService.persistentEnabled(context)) {
            clear(context)
            return
        }
        channel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        try {
            val notification = build(context, battery)
            if (!stillCurrent()) return
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // Notifications switched off by the user: the voice line still works.
        }
        updatePill(context, battery, stillCurrent)
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

    /** Charger connected = watts only; unplugged = restore the configured data + D/U view. */
    fun updatePill(context: Context, battery: BatteryReading, stillCurrent: () -> Boolean = { true }) {
        val wanted = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(ChargeMonitorService.PILL_KEY, com.universalrp.cleansweep.data.MonitorDefaultsPolicy.STATUS_PILL)
        if (!stillCurrent()) return
        if (!wanted || !StatusPill.canDraw(context)) {
            StatusPill.remove()
            return
        }
        if (battery.powerConnected) {
            val language = Lang.languageIn(context)
            fun t(text: String, vararg args: Any) = Lang.translate(language, text, *args)
            val chargeStatusText = when {
                battery.percent >= 100 -> t("Full")
                battery.charging -> LiveChargingEta.estimate(battery)?.let { t("ETA %s", formatMinutes(it.minutesToFull)) }
                else -> null
            }
            val content = StatusPillContent.resolve(
                powerConnected = true,
                powerW = battery.powerW,
                charging = battery.charging,
                chargeStatusText = chargeStatusText,
                estimatingText = t("Estimating"),
                unavailableText = t("Unavailable"),
            )
            StatusPill.update(context, content.text, showNetworkMeter = false, stillCurrent = stillCurrent)
            // Keep accounting alive even though its text is hidden. Do this AFTER the
            // immediate mode switch, so a slow stats query cannot leave the old wide pill up.
            runCatching { DataUsageTracker.getUsageInfo(context) }
            return
        }
        val dataUsage = runCatching { DataUsageTracker.getUsageInfo(context) }.getOrNull()
        val isUnlimited = dataUsage?.isUnlimited5g == true
        val packPercent = if (dataUsage != null && dataUsage.packLimitBytes > 0 && !isUnlimited) {
            ((dataUsage.packTotalMobileBytes.toDouble() / dataUsage.packLimitBytes) * 100.0)
                .coerceIn(0.0, 100.0).toInt()
        } else null
        val netQuality = runCatching { LiveNetworkQuality.measure(context) }.getOrNull()
        if (!stillCurrent()) return // A plug/unplug arrived while the stats/ping query ran.
        val content = StatusPillContent.resolve(
            powerConnected = false,
            powerW = null,
            mobileData = dataUsage?.formattedToday,
            unlimited5g = isUnlimited,
            batteryPercent = battery.percent,
            packPercent = packPercent,
            hideDataOnWifiOrUnlimited = StatusPill.isHideDataOnWifi(context) &&
                (netQuality?.isWifi == true || isUnlimited),
        )
        StatusPill.update(
            context,
            content.text,
            content.packPercent,
            netQuality?.grade,
            netQuality?.rxSpeedBytesPerSec ?: 0L,
            netQuality?.txSpeedBytesPerSec ?: 0L,
            showNetworkMeter = content.showNetworkMeter,
            stillCurrent = stillCurrent,
        )
    }

    /**
     * Says what the charger is doing, once per plug-in.
     */
    suspend fun announcePluggedIn(context: Context, delayMs: Long = 800) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Wait without blocking a thread, then de-duplicate by physical connection, not a four-second window.
        if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
        val token = prefs.getString("voice_connection_token", "").orEmpty().ifBlank {
            java.util.UUID.randomUUID().toString().also { prefs.edit().putString("voice_connection_token", it).apply() }
        }
        if (prefs.getString("voice_last_spoken_connection", "") == token) return
        val battery = read(app) ?: return
        if (!battery.powerConnected) return

        // 1. Overheat check on plug-in: the single owner-set threshold (44 °C in 2.41,
        // 42 °C before) says the battery is unusually warm — no cause is claimed.
        val thermalAtPlug = ThermalWarningPolicy.decide(battery.temperatureC, null, null)
        if (thermalAtPlug.warn && thermalAtPlug.sensor == ThermalWarningPolicy.Sensor.BATTERY) {
            val tempRound = thermalAtPlug.celsius?.toInt() ?: 0
            val warnTamil = "எச்சரிக்கை! போன் மிக அதிக வெப்பமாக உள்ளது ($tempRound டிகிரி). வேகமாக சார்ஜ் செய்வதைத் தவிர்க்கவும் அல்லது சார்ஜரை அகற்றவும்."
            Announcer.speakAndWait(app, "The battery is unusually warm at plug-in. Please review charging conditions.", warnTamil,
                Announcer.Event.OVERHEAT, onStarted = { prefs.edit().putString("voice_last_spoken_connection", token).apply() },
                requestKey = "plug:$token")
            return
        }

        val percent = battery.percent
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
                append(if (percent in 0..100) "சார்ஜர் இணைக்கப்பட்டது: $percent சதவீதம்." else "சார்ஜர் இணைக்கப்பட்டது. பேட்டரி அளவு தெரியவில்லை.")
            }
            if (timeEstimate != null) {
                append(" நிறைவடைய $timeEstimate.")
            }
        }
        Announcer.speakAndWait(app, "Charger connected" + (if (battery.percent in 0..100) ": ${battery.percent} percent." else ". Battery level not reported."),
            tamil, Announcer.Event.CHARGING, onStarted = {
                prefs.edit().putString("voice_last_spoken_connection", token).putLong(KEY_LAST_PLUG_ANNOUNCE, System.currentTimeMillis()).apply()
            }, requestKey = "plug:$token")
    }
}
