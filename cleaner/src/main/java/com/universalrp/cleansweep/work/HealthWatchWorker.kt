package com.universalrp.cleansweep.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.universalrp.cleansweep.MainActivity
import com.universalrp.cleansweep.R
import com.universalrp.cleansweep.ai.AiClient
import com.universalrp.cleansweep.ai.AiSettings
import com.universalrp.cleansweep.data.AppInventoryLoader
import com.universalrp.cleansweep.data.AppLang
import com.universalrp.cleansweep.data.BatteryReader
import com.universalrp.cleansweep.data.DeviceHealthReader
import com.universalrp.cleansweep.data.HealthSnapshot
import com.universalrp.cleansweep.data.JunkKind
import com.universalrp.cleansweep.data.Lang
import com.universalrp.cleansweep.data.ScanEngine
import com.universalrp.cleansweep.data.ScanSettings
import com.universalrp.cleansweep.data.SecurityScanner
import com.universalrp.cleansweep.data.Severity
import com.universalrp.cleansweep.data.batteryTimeLabel
import com.universalrp.cleansweep.data.formatBytes
import com.universalrp.cleansweep.data.hasAllFilesAccess
import com.universalrp.cleansweep.voice.Announcer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * The hourly watch. One worker does three jobs, so the phone wakes up once, not three times:
 *
 *  1. **Warnings** — battery low while unplugged, battery full while charging, and a phone
 *     that is running hot (battery or CPU). Each is said once and then hushed for hours, so
 *     the user is informed rather than nagged.
 *  2. **The daily brief** — once a day at the hour the user chose, CleanSweep takes a full
 *     look (health, storage, security findings, and junk if it is allowed to read the
 *     storage), asks the AI for a short summary when a key is set, says a *short* line out
 *     loud and posts the details as a notification.
 *  3. **Nothing at all** when the user switched it off — every part is behind its own switch
 *     in Settings, and quiet hours silence the voice even when an event does fire.
 *
 * The work needs no new permission: battery, thermal and storage numbers come from the
 * system and the kernel, notifications already have their permission, and the junk scan only
 * runs when the user has granted "All files access".
 */
class HealthWatchWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        // A worker can start in a fresh process, so read the language from prefs first.
        Lang.set(Lang.languageIn(ctx))
        // The user tapped "Run the daily check now": do it whatever the hour, whatever the
        // daily switch and however many briefs today has already had. Without this the
        // button quietly did nothing before the configured evening hour — which is exactly
        // what the phone showed.
        val forced = inputData.getBoolean(KEY_FORCE, false)
        try {
            if (!forced) warnings(ctx)
            daily(ctx, forced)
        } catch (e: Exception) {
            // Never let a bad read kill the schedule: try again next hour.
            return Result.retry()
        }
        return Result.success()
    }

    // ------------------------------------------------------------------ warnings

    private suspend fun warnings(ctx: Context) {
        // Wi-Fi ⇄ mobile data changed — first, so a bad battery read cannot skip it.
        networkTransportChanged(ctx)
        val battery = try {
            BatteryReader.read(ctx)
        } catch (e: Exception) {
            return
        }
        val health = try {
            DeviceHealthReader.read(ctx)
        } catch (e: Exception) {
            null
        }
        val now = System.currentTimeMillis()
        val prefs = Announcer.prefs(ctx)

        // Battery low — only while it is actually running on the battery.
        if (!battery.charging && battery.percent in 1 until Announcer.LOW_PERCENT) {
            val last = prefs.getLong(Announcer.KEY_LAST_LOW, 0L)
            if (now - last > 6 * 60 * 60 * 1000L) {
                prefs.edit().putLong(Announcer.KEY_LAST_LOW, now).apply()
                Announcer.speakTamil(
                    ctx,
                    "பேட்டரி குறைவு: ${battery.percent} சதவீதம். சார்ஜ் செய்யவும்.",
                    Announcer.Event.BATTERY_LOW,
                )
            }
        }

        // Battery full — worth a word only while it is still on the cable.
        if (battery.charging && battery.percent >= 100) {
            val last = prefs.getLong(Announcer.KEY_LAST_FULL, 0L)
            if (now - last > 6 * 60 * 60 * 1000L) {
                prefs.edit().putLong(Announcer.KEY_LAST_FULL, now).apply()
                Announcer.speakTamil(
                    ctx,
                    "பேட்டரி முழுமை அடைந்தது. சார்ஜரை அகற்றவும்.",
                    Announcer.Event.BATTERY_FULL,
                )
            }
        }

        // Storage nearly full — the one warning that gets more painful the longer it waits.
        val storage = health?.storage
        if (storage != null && storage.free in 1 until Announcer.LOW_STORAGE_BYTES) {
            val last = prefs.getLong(Announcer.KEY_LAST_STORAGE, 0L)
            if (now - last > 12 * 60 * 60 * 1000L) {
                prefs.edit().putLong(Announcer.KEY_LAST_STORAGE, now).apply()
                val freeGb = "%.1f".format(storage.free / 1024.0 / 1024.0 / 1024.0)
                Announcer.speak(
                    ctx,
                    "Storage is nearly full — only $freeGb gigabytes free. Open CleanSweep and clean some junk.",
                    "சேமிப்பு கிட்டத்தட்ட நிரம்பிவிட்டது — $freeGb ஜிகாபைட் மட்டுமே காலி. " +
                        "சுத்தம் செய்பவரைத் திறந்து குப்பையை அகற்றவும்.",
                    Announcer.Event.STORAGE_LOW,
                )
            }
        }

        // Battery health: how much of the original capacity is left. Said at most once a week,
        // because it is a slow fact, not an emergency.
        val healthPercent = health?.battery?.estimatedCapacityMah?.takeIf { it > 0f }?.let { _ ->
            try {
                val design = designCapacityMah(ctx)
                val estimated = health.battery.estimatedCapacityMah ?: 0f
                if (design != null && design > 0f) (estimated / design * 100f).toInt() else null
            } catch (e: Exception) {
                null
            }
        }
        if (healthPercent != null && healthPercent in 1..84) {
            val last = prefs.getLong(Announcer.KEY_LAST_HEALTH, 0L)
            if (now - last > 7 * 24 * 60 * 60 * 1000L) {
                prefs.edit().putLong(Announcer.KEY_LAST_HEALTH, now).apply()
                Announcer.speak(
                    ctx,
                    "Battery health notice: your battery now holds about $healthPercent percent of its " +
                        "original capacity, so it drains faster than when it was new.",
                    "பேட்டரி ஆரோக்கியம்: உங்கள் பேட்டரி புதியதாக இருந்ததில் சுமார் $healthPercent " +
                        "சதவீதம் மட்டுமே தேக்க முடிகிறது, எனவே விரைவாகக் குறையும்.",
                    Announcer.Event.BATTERY_HEALTH,
                )
            }
        }

        // Running hot — battery or CPU.
        val batteryHot = battery.temperatureC?.let { it >= Announcer.HOT_BATTERY_C } == true
        val cpuHot = (health?.cpuTempC ?: 0f) >= Announcer.HOT_CPU_C
        if (batteryHot || cpuHot) {
            val last = prefs.getLong(Announcer.KEY_LAST_HOT, 0L)
            if (now - last > 3 * 60 * 60 * 1000L) {
                prefs.edit().putLong(Announcer.KEY_LAST_HOT, now).apply()
                val batteryText = battery.temperatureC?.let { "%.0f".format(it) } ?: "?"
                val cpuText = health?.cpuTempC?.let { "%.0f".format(it) } ?: "?"
                val whichTa = if (batteryHot && cpuHot) "பேட்டரி மற்றும் செயலி" else if (batteryHot) "பேட்டரி" else "செயலி"
                val plugWarning = if (battery.charging) " வேகமாக சார்ஜ் செய்வதைத் தவிர்க்கவும்." else ""
                Announcer.speakTamil(
                    ctx,
                    "போன் அதிக வெப்பமாக உள்ளது: $whichTa ${if (batteryHot) batteryText else cpuText} டிகிரி.$plugWarning",
                    Announcer.Event.OVERHEAT,
                )
            }
        }

        // Weekly high security risks check (remind once a week)
        val lastSecReminder = prefs.getLong("last_sec_reminder_ms", 0L)
        if (now - lastSecReminder > 7 * 24 * 60 * 60 * 1000L) {
            val highRisks = try {
                val apps = AppInventoryLoader.load(ctx, includeSystem = false).rows
                val report = SecurityScanner.scan(ctx, apps)
                report.findings.filter { it.severity == Severity.HIGH }
            } catch (e: Exception) {
                emptyList()
            }
            if (highRisks.isNotEmpty()) {
                prefs.edit().putLong("last_sec_reminder_ms", now).apply()
                val riskCount = highRisks.size
                prefs.edit().putString("widget_security_alert", "$riskCount critical security alert${if (riskCount > 1) "s" else ""}").apply()
                Announcer.speakTamil(
                    ctx,
                    "பாதுகாப்பு நினைவூட்டல்: $riskCount முக்கிய பாதுகாப்பு அமைப்புகளை சரிபார்க்கவும்.",
                    Announcer.Event.TEST,
                )
            } else {
                prefs.edit().remove("widget_security_alert").apply()
            }
        }
    }

    /**
     * Wi-Fi ⇄ mobile data. v2.10: this used to listen to CONNECTIVITY_ACTION from the
     * manifest — but Android 8+ never delivers that broadcast to manifest receivers,
     * so the warning was dead on every supported phone. The hourly pass checks the
     * transport itself instead: late by minutes, but alive on every Android version.
     * Off by default: the switch lives in the voice screen.
     */
    private fun networkTransportChanged(ctx: Context) {
        if (!Announcer.allows(ctx, Announcer.Event.NETWORK_CHANGE)) return
        val connectivity = ctx.getSystemService(Context.CONNECTIVITY_SERVICE)
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
        // Same key the old receiver used, so a stored value carries over.
        val prefs = Announcer.prefs(ctx)
        val before = prefs.getString(KEY_LAST_TRANSPORT, "")
        if (before == now) return
        prefs.edit().putString(KEY_LAST_TRANSPORT, now).apply()
        // Nothing to say the first time we ever look, or while going offline.
        if (before.isNullOrBlank() || now == "none") return
        when (now) {
            "mobile" -> Announcer.speak(
                ctx,
                "You are now on mobile data. This uses your data plan.",
                "இப்போது மொபைல் டேட்டாவில் உள்ளீர்கள். இது உங்கள் தரவுத் திட்டத்தைப் பயன்படுத்தும்.",
                Announcer.Event.NETWORK_CHANGE,
            )
            "wifi" -> Announcer.speak(
                ctx,
                "You are back on Wi-Fi.",
                "நீங்கள் மீண்டும் Wi-Fi-ல் உள்ளீர்கள்.",
                Announcer.Event.NETWORK_CHANGE,
            )
        }
    }

    // ------------------------------------------------------------------ the daily brief

    private suspend fun daily(ctx: Context, forced: Boolean = false) {
        if (!forced) {
            if (!Announcer.dailyScanOn(ctx)) return
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            if (hour < Announcer.dailyHour(ctx)) return
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            if (Announcer.lastDailyDate(ctx) == today) return
        }
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        val tamil = Lang.languageIn(ctx) == AppLang.TA

        // ---- facts (all on-device)
        val health: HealthSnapshot? = try {
            DeviceHealthReader.read(ctx)
        } catch (e: Exception) {
            null
        }
        val battery = health?.battery ?: try {
            BatteryReader.read(ctx)
        } catch (e: Exception) {
            null
        }
        val storage = health?.storage
        val apps = try {
            AppInventoryLoader.load(ctx, includeSystem = true).rows
        } catch (e: Exception) {
            emptyList()
        }
        val security = try {
            SecurityScanner.scan(ctx, apps)
        } catch (e: Exception) {
            null
        }
        val junk = if (hasAllFilesAccess()) {
            try {
                val root = Environment.getExternalStorageDirectory()
                ScanEngine(ctx).scan(root, ScanSettings(), JunkKind.entries.toSet()) { }
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        val high = security?.findings?.count { it.severity == Severity.HIGH } ?: 0
        val batteryTemp = battery?.temperatureC?.let { "%.0f".format(it) } ?: "?"
        val cpuTemp = health?.cpuTempC?.let { "%.0f".format(it) } ?: "?"
        val freeGb = storage?.free?.let { "%.1f".format(it / 1024.0 / 1024.0 / 1024.0) } ?: "?"
        val freeRam = health?.device?.availableRamBytes?.let { (it / 1024.0 / 1024.0).toInt().toString() } ?: "?"
        val timeToFull = battery?.let { batteryTimeLabelOrNull(it) }

        // ---- the one line that is spoken
        val spoken: Pair<String, String> = when {
            high > 0 -> ("Important: $high security setting${if (high == 1) "" else "s"} need attention. " +
                "Open CleanSweep to see them.") to
                ("முக்கியம்: $high பாதுகாப்பு அமைப்புகளில் கவனம் தேவை. " +
                    "விவரங்களுக்கு CleanSweep-ஐத் திறக்கவும்.")
            (battery?.percent ?: 100) < Announcer.LOW_PERCENT && battery?.charging != true ->
                ("Daily brief. Battery is low at ${battery?.percent ?: 0} percent, and " +
                    "${junk?.totalBytes?.formatBytes() ?: "0 B"} of junk is waiting.") to
                    ("தினசரி சுருக்கம். பேட்டரி ${battery?.percent ?: 0} சதவீதம் மட்டுமே உள்ளது; " +
                        "${junk?.totalBytes?.formatBytes() ?: "0 B"} குப்பை காத்திருக்கிறது.")
            (junk?.totalBytes ?: 0L) > 1024L * 1024L * 1024L ->
                ("Daily brief. Your phone is fine, but ${junk?.totalBytes?.formatBytes()} of junk is ready to clean.") to
                    ("தினசரி சுருக்கம். போன் நன்றாக உள்ளது; ஆனால் ${junk?.totalBytes?.formatBytes()} குப்பை சுத்தம் செய்யத் தயார்.")
            else -> ("Daily brief. Battery ${battery?.percent ?: 0} percent, storage ${freeGb} gigabytes free, " +
                "nothing urgent.") to
                ("தினசரி சுருக்கம். பேட்டரி ${battery?.percent ?: 0} சதவீதம், சேமிப்பு ${freeGb} ஜிகாபைட் காலி, " +
                    "அவசரம் எதுவும் இல்லை.")
        }

        // ---- the full text (notification body + the app's "last brief" card)
        val full = buildString {
            appendLine(if (tamil) "தினசரி அறிக்கை" else "Daily brief")
            appendLine((if (tamil) "பேட்டரி" else "Battery") + ": ${battery?.percent ?: "?"}% — " +
                (battery?.statusLabel ?: "?") + ", $batteryTemp°C" +
                (timeToFull?.let { " ($it)" } ?: ""))
            appendLine((if (tamil) "CPU வெப்பம்" else "CPU temperature") + ": $cpuTemp°C")
            appendLine((if (tamil) "சேமிப்பு காலி" else "Storage free") + ": $freeGb GB" +
                (storage?.let { " / %.1f GB".format(it.total / 1024.0 / 1024.0 / 1024.0) } ?: ""))
            appendLine((if (tamil) "RAM காலி" else "RAM free") + ": $freeRam MB")
            appendLine(
                (if (tamil) "குப்பை" else "Junk") + ": " +
                    (junk?.let { "${it.totalCount} (${it.totalBytes.formatBytes()})" }
                        ?: (if (tamil) "அனுமதி இல்லை" else "not scanned (no file access)"))
            )
            appendLine(
                (if (tamil) "பாதுகாப்பு மதிப்பெண்" else "Security score") + ": " +
                    (security?.let { "${it.score}/100, ${it.findings.size} " +
                        (if (tamil) "கண்டுபிடிப்புகள்" else "findings") } ?: "?")
            )
        }.trim()

        // ---- ask the AI for a friendlier short version, and keep the honest text if it fails
        var spokenFinal = if (tamil) spoken.second else spoken.first
        val config = try {
            AiSettings.autoComplete(AiSettings.load(ctx))
        } catch (e: Exception) {
            null
        }
        if (config != null && config.ready) {
            val prompt = buildString {
                appendLine("Facts about this Android phone right now:")
                appendLine(full)
                appendLine()
                appendLine(
                    "Write the daily brief in 2 short sentences, under 40 words, plain " +
                        "language, no bullet points, no markdown. Say the single most useful " +
                        "thing first. " +
                        if (tamil) "Write it in Tamil." else "Write it in English."
                )
            }
            val result = try {
                AiClient.ask(
                    config,
                    "You are CleanSweep, a phone-health assistant. Be brief, concrete and calm. " +
                        "Never invent a number that is not in the facts.",
                    prompt,
                )
            } catch (e: Exception) {
                null
            }
            if (result?.ok == true && result.text.isNotBlank()) {
                spokenFinal = Announcer.shorten(result.text, 320)
            }
        }

        // A hand-run brief keeps the day free, so the evening brief still arrives.
        Announcer.saveBrief(ctx, today, full, markDay = !forced)
        notify(ctx, full, spokenFinal)
        Announcer.speak(
            ctx,
            spokenFinal,
            spokenFinal,
            Announcer.Event.DAILY,
        )
    }

    /**
     * The capacity the battery was designed with, straight from the kernel when the phone
     * exposes it. Returns null when it is unknown — in that case CleanSweep says nothing
     * about battery health rather than guessing.
     */
    private fun designCapacityMah(ctx: Context): Float? {
        val candidates = listOf(
            "/sys/class/power_supply/battery/charge_full_design",
            "/sys/class/power_supply/BATTERY/charge_full_design",
        )
        for (path in candidates) {
            try {
                val raw = File(path).readText().trim().toFloatOrNull() ?: continue
                val mah = if (raw > 100_000f) raw / 1000f else raw
                if (mah in 500f..20_000f) return mah
            } catch (e: Exception) {
                // Try the next one.
            }
        }
        return null
    }

    private fun batteryTimeLabelOrNull(battery: com.universalrp.cleansweep.data.BatteryReading): String? =
        try {
            batteryTimeLabel(battery)
        } catch (e: Exception) {
            null
        }


    // ------------------------------------------------------------------ notification

    private fun notify(ctx: Context, full: String, short: String) {
        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL,
                        "Daily phone brief",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply { description = "Once a day: battery, temperature, storage, junk and security" }
                )
            } catch (e: Exception) {
                // Channel already there.
            }
        }
        val open = PendingIntent.getActivity(
            ctx,
            41,
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle("CleanSweep daily brief")
            .setContentText(short)
            .setStyle(NotificationCompat.BigTextStyle().bigText(full))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // Notifications switched off: the spoken line and the in-app card still work.
        }
    }

    companion object {
        const val CHANNEL = "cleansweep_daily"
        const val NOTIFICATION_ID = 7103
        private const val UNIQUE = "cleansweep_health_watch"

        /** Set when the worker was started by the "Run the daily check now" button. */
        const val KEY_FORCE = "force_run"

        /** Last Wi-Fi/mobile transport the hourly pass saw (moved from SystemEventWatcher). */
        private const val KEY_LAST_TRANSPORT = "last_transport"

        /**
         * One hourly wake-up. WorkManager keeps it across reboots and respects Doze, and an
         * hourly period cannot be missed by more than a little — which is all a daily brief
         * and a battery warning need.
         */
        fun schedule(context: Context) {
            try {
                val request = PeriodicWorkRequestBuilder<HealthWatchWorker>(1, TimeUnit.HOURS)
                    .setConstraints(Constraints.NONE)
                    .build()
                WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                    UNIQUE,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
            } catch (e: Exception) {
                // If WorkManager is unavailable the rest of the app still works.
            }
        }

        /**
         * Runs the daily brief immediately (the "Run the daily check now" button). The app
         * reads the result back from the prefs the worker writes, so no callback is needed —
         * and no hand-built WorkerParameters, which Android does not allow.
         */
        suspend fun runNow(context: Context): Boolean = try {
            val oneOff = androidx.work.OneTimeWorkRequestBuilder<HealthWatchWorker>()
                .setInputData(androidx.work.workDataOf(KEY_FORCE to true))
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueue(oneOff)
                .result
                .get()
            true
        } catch (e: Exception) {
            false
        }

        fun cancel(context: Context) {
            try {
                WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE)
            } catch (e: Exception) {
                // Nothing to cancel.
            }
        }

    }
}
