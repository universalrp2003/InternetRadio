package com.universalrp.cleansweep.data

import android.content.Context
import android.os.SystemClock
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

/** All history is private and excluded from Android backup. No external storage/provider/server. */
internal object InsightsStore {
    private const val MAX_BYTES = 2_000_000L
    private val errors = mutableMapOf<String, String>()
    private fun file(context: Context, name: String) = File(context.noBackupFilesDir, "liveguard-$name.json")
    @Synchronized fun read(context: Context, name: String): String? = try {
        val base = file(context, name)
        if (!base.exists() && !File(base.path + ".bak").exists()) null else {
            require(base.length() <= MAX_BYTES && File(base.path + ".bak").length() <= MAX_BYTES)
            AtomicFile(base).readFully().toString(Charsets.UTF_8).also { errors.remove(name) }
        }
    } catch (e: Exception) { errors[name] = "Local history could not be read. Clear it to establish a new baseline."; null }

    @Synchronized fun write(context: Context, name: String, raw: String): Boolean {
        val atomic = AtomicFile(file(context, name))
        var stream: FileOutputStream? = null
        return try {
            val bytes = raw.toByteArray(Charsets.UTF_8); require(bytes.size <= MAX_BYTES)
            stream = atomic.startWrite(); stream.write(bytes); atomic.finishWrite(stream)
            errors.remove(name); true
        } catch (e: Exception) {
            stream?.let { runCatching { atomic.failWrite(it) } }
            errors[name] = "Local history could not be saved. Current readings may be lost when the process stops."; false
        }
    }
    @Synchronized fun clear(context: Context, name: String) {
        runCatching { AtomicFile(file(context, name)).delete() }
        if (file(context, name).exists()) errors[name] = "Local history could not be deleted." else errors.remove(name)
    }
    @Synchronized fun warning(name: String): String? = errors[name]
}

object ChargeHistoryRepo {
    private const val NAME = "charging"
    private var path = ""
    private var cache = ChargeHistoryState()
    private var lastWriteElapsed = 0L
    private fun flags(context: Context) = context.getSharedPreferences("liveguard_insights", Context.MODE_PRIVATE)
    fun enabled(context: Context) = flags(context).getBoolean("charge_history", false)
    @Synchronized fun load(context: Context): ChargeHistoryState {
        val current = context.noBackupFilesDir.path
        if (path != current) { cache = InsightsJson.readCharge(InsightsStore.read(context, NAME)); path = current }
        return cache
    }
    @Synchronized fun setEnabled(context: Context, value: Boolean) {
        flags(context).edit().putBoolean("charge_history", value).apply()
        if (!value) interrupt(context)
    }
    @Synchronized fun record(context: Context, battery: BatteryReading, plugEvent: Boolean = false, unplugEvent: Boolean = false) {
        if (!enabled(context)) return
        val elapsed = SystemClock.elapsedRealtime()
        val before = load(context)
        val sample = ChargeSample(System.currentTimeMillis(), elapsed,
            plugEvent || (!unplugEvent && battery.powerConnected), battery.percent.takeIf { it >= 0 },
            battery.powerW?.toDouble(), battery.temperatureC?.toDouble(), plugEvent, unplugEvent)
        cache = ChargeHistoryPolicy.observe(before, sample)
        // Aggregation is in memory; disk is written at most once a minute, or on a connection/end event.
        if (plugEvent || unplugEvent || before.active?.id != cache.active?.id || elapsed - lastWriteElapsed >= 60_000 || elapsed < lastWriteElapsed) {
            if (InsightsStore.write(context, NAME, InsightsJson.charge(cache))) lastWriteElapsed = elapsed
        }
    }
    @Synchronized fun interrupt(context: Context, expectedId: String? = null) {
        val state = load(context)
        state.active?.let { active ->
            if (expectedId != null && active.id != expectedId) return
            cache = ChargeHistoryState(sessions = (state.sessions + active.copy(endedAtMs = active.lastAtMs, interrupted = true, thresholdGap = true)).takeLast(ChargeHistoryPolicy.MAX_SESSIONS))
            InsightsStore.write(context, NAME, InsightsJson.charge(cache))
        }
    }
    @Synchronized fun clear(context: Context) { cache = ChargeHistoryState(); path = context.noBackupFilesDir.path; InsightsStore.clear(context, NAME) }
    fun warning(): String? = InsightsStore.warning(NAME)
}

object SecurityHistoryRepo {
    private const val NAME = "security"
    private var path = ""
    private var cache = SecurityHistoryState()
    private fun flags(context: Context) = context.getSharedPreferences("liveguard_insights", Context.MODE_PRIVATE)
    fun enabled(context: Context) = flags(context).getBoolean("security_history", false)
    @Synchronized fun load(context: Context): SecurityHistoryState {
        val current = context.noBackupFilesDir.path
        if (path != current) { cache = InsightsJson.readSecurity(InsightsStore.read(context, NAME)); path = current }
        return cache
    }
    @Synchronized fun setEnabled(context: Context, value: Boolean) {
        flags(context).edit().putBoolean("security_history", value).apply()
    }
    fun packages(apps: List<AppRow>): List<ObservedPackage> = apps.map { ObservedPackage(it.pkg, it.label, it.firstInstallMs, it.updatedMs, it.isSystem) }
    @Synchronized fun record(context: Context, report: SecurityReport, apps: List<AppRow>) {
        if (!enabled(context)) return
        cache = SecurityHistoryPolicy.observe(load(context), SecurityHistoryPolicy.snapshot(report, packages(apps)))
        InsightsStore.write(context, NAME, InsightsJson.security(cache))
    }
    @Synchronized fun review(context: Context, observation: SecurityObservation, expected: Boolean) {
        val current = load(context)
        val next = SecurityHistoryPolicy.review(current, observation, expected, System.currentTimeMillis())
        // Explicit review choices can be saved even with passive history OFF; don't retain event text then.
        cache = if (enabled(context)) next else next.copy(events = current.events)
        InsightsStore.write(context, NAME, InsightsJson.security(cache))
    }
    @Synchronized fun clear(context: Context) { cache = SecurityHistoryState(); path = context.noBackupFilesDir.path; InsightsStore.clear(context, NAME) }
    fun warning(): String? = InsightsStore.warning(NAME)
}

object DataBudgetRepo {
    private const val NAME = "mobile-days"
    private var path = ""
    private var cache: List<DailyDataPoint> = emptyList()
    private var lastWriteElapsed = 0L
    private fun flags(context: Context) = context.getSharedPreferences("liveguard_insights", Context.MODE_PRIVATE)
    fun enabled(context: Context) = flags(context).getBoolean("data_history", false)
    fun setEnabled(context: Context, value: Boolean) { flags(context).edit().putBoolean("data_history", value).apply() }
    fun config(context: Context) = DataBudgetConfig(flags(context).getString("expires_on", null))
    fun setExpiry(context: Context, date: String?) {
        require(date == null || DataBudgetPolicy.dateOrNull(date) != null)
        flags(context).edit().apply { if (date == null) remove("expires_on") else putString("expires_on", date) }.apply()
    }
    @Synchronized fun load(context: Context): List<DailyDataPoint> {
        val current = context.noBackupFilesDir.path
        if (path != current) { cache = InsightsJson.readData(InsightsStore.read(context, NAME)); path = current }
        return cache
    }
    @Synchronized fun recordToday(context: Context, usage: DataUsageTracker.UsageInfo) {
        if (!enabled(context)) return
        val point = DailyDataPoint(java.time.Instant.ofEpochMilli(usage.capturedAtMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString(), usage.todayMobileBytes.takeIf { usage.readingAvailable }, false,
            usage.source, usage.capturedAtMs)
        cache = DataBudgetPolicy.merge(load(context), point)
        val elapsed = SystemClock.elapsedRealtime()
        if (elapsed - lastWriteElapsed >= 60_000 || elapsed < lastWriteElapsed) {
            if (InsightsStore.write(context, NAME, InsightsJson.data(cache))) lastWriteElapsed = elapsed
        }
    }
    @Synchronized fun merge(context: Context, points: List<DailyDataPoint>): List<DailyDataPoint> {
        var next = load(context)
        for (point in points) next = DataBudgetPolicy.merge(next, point)
        if (enabled(context)) { cache = next; InsightsStore.write(context, NAME, InsightsJson.data(cache)) }
        return next // Read-only history remains usable in this screen when saving is off.
    }
    @Synchronized fun clear(context: Context) { cache = emptyList(); path = context.noBackupFilesDir.path; InsightsStore.clear(context, NAME) }
    fun warning(): String? = InsightsStore.warning(NAME)
}
