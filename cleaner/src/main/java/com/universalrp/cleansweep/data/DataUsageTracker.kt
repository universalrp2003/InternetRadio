package com.universalrp.cleansweep.data

import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.SystemClock
import android.provider.Settings
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

/** Device-wide MOBILE traffic only. Carrier billing/zero-rating is not exposed by these APIs. */
object DataUsageTracker {
    private const val PREFS = "cleansweep_data_pack"
    private const val KEY_PACK_START_MS = "pack_start_ms"
    private const val KEY_PACK_BASELINE_BYTES = "pack_baseline_bytes"
    private const val KEY_PACK_LIMIT_GB = "pack_limit_gb"
    private const val KEY_UNLIMITED_5G = "unlimited_5g_enabled"
    private const val KEY_LAST_DAY = "last_recorded_day"
    private const val KEY_TODAY_BASELINE_BYTES = "today_baseline_bytes"
    const val DEFAULT_PACK_LIMIT_GB = 12.0f

    data class UsageInfo(
        val hasUsageAccess: Boolean,
        val todayMobileBytes: Long,
        val packTotalMobileBytes: Long,
        val packStartDateMs: Long,
        val packLimitGb: Float,
        val packLimitBytes: Long,
        val packRemainingBytes: Long,
        val formattedToday: String,
        val formattedPackTotal: String,
        val formattedRemaining: String,
        val formattedPackLimit: String,
        val progressRatio: Float,
        val isUnlimited5g: Boolean = false,
        val readingAvailable: Boolean = true,
        val isPartial: Boolean = false,
        val source: String = "Android mobile network statistics",
        val capturedAtMs: Long = 0,
    )

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun isUnlimited5g(context: Context) = prefs(context).getBoolean(KEY_UNLIMITED_5G, false)
    fun setUnlimited5g(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_UNLIMITED_5G, enabled).apply()
        context.getSharedPreferences("cleansweep_state", Context.MODE_PRIVATE).edit().putBoolean(KEY_UNLIMITED_5G, enabled).apply()
    }
    fun getPackLimitGb(context: Context) = prefs(context).getFloat(KEY_PACK_LIMIT_GB, DEFAULT_PACK_LIMIT_GB)
    fun setPackLimitGb(context: Context, limitGb: Float) {
        if (!limitGb.isFinite() || limitGb <= 0 || limitGb > 100_000) return
        prefs(context).edit().putFloat(KEY_PACK_LIMIT_GB, limitGb).apply()
        context.getSharedPreferences("cleansweep_state", Context.MODE_PRIVATE).edit().putFloat(KEY_PACK_LIMIT_GB, limitGb).apply()
    }

    @Synchronized fun resetPackUsage(context: Context, newLimitGb: Float? = null) {
        if (newLimitGb != null) setPackLimitGb(context, newLimitGb)
        setPeriodStart(context, System.currentTimeMillis())
    }
    @Synchronized fun setPackStartDay(context: Context, day: LocalDate) {
        require(DataBudgetPolicy.validPeriodStart(day.toString(), LocalDate.now()) != null)
        setPeriodStart(context, day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
    }
    private fun setPeriodStart(context: Context, atMs: Long) {
        // Account for today's already-observed delta before moving the shared fallback baseline.
        getUsageInfo(context)
        val current = mobileCounter()
        val boot = runCatching { Settings.Global.getInt(context.contentResolver, "boot_count") }.getOrNull()
        val edit = prefs(context).edit().putLong(KEY_PACK_START_MS, atMs).putLong("observed_pack_bytes", 0)
            .putLong("observed_uptime", SystemClock.elapsedRealtime()).putInt("observed_boot", boot ?: -1)
        if (current != null) edit.putLong(KEY_PACK_BASELINE_BYTES, current).putLong("last_mobile_counter", current)
        else edit.remove(KEY_PACK_BASELINE_BYTES).remove("last_mobile_counter")
        edit.apply()
    }

    @Synchronized fun getUsageInfo(context: Context): UsageInfo {
        val store = prefs(context)
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        val todayKey = "${calendar.get(Calendar.YEAR)}${calendar.get(Calendar.DAY_OF_YEAR)}" // Preserve the previous counter's day key on upgrade.
        calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
        val midnight = calendar.timeInMillis
        var start = store.getLong(KEY_PACK_START_MS, 0)
        if (start <= 0) { start = midnight; store.edit().putLong(KEY_PACK_START_MS, start).apply() }
        val access = UsageStats.hasUsageAccess(context)
        val manager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
        val fullToday = if (access && manager != null) queryMobileTotal(manager, midnight, now) else null
        val fullPack = if (access && manager != null) queryMobileTotal(manager, start, now) else null
        var available = fullToday != null && fullPack != null
        val partial = !available
        var today = fullToday ?: 0
        var pack = fullPack ?: 0
        if (!available) {
            val current = mobileCounter()
            available = current != null
            if (current != null) {
                val last = store.getLong("last_mobile_counter", -1).takeIf { it >= 0 }
                val uptime = SystemClock.elapsedRealtime()
                val boot = runCatching { Settings.Global.getInt(context.contentResolver, "boot_count") }.getOrNull()
                val oldDay = store.getString(KEY_LAST_DAY, "").orEmpty()
                val previous = MobileCounterPolicy.migrate(last, current,
                    store.getLong(KEY_PACK_BASELINE_BYTES, current), store.getLong(KEY_TODAY_BASELINE_BYTES, current),
                    store.getLong("observed_pack_bytes", -1).takeIf { it >= 0 }, store.getLong("observed_today_bytes", -1).takeIf { it >= 0 },
                    oldDay, store.getLong("observed_uptime", 0), store.getInt("observed_boot", -1).takeIf { it >= 0 })
                val observed = MobileCounterPolicy.observe(previous, current, todayKey, uptime, boot)
                pack = observed.packBytes ?: 0; today = observed.todayBytes ?: 0
                store.edit().putLong("last_mobile_counter", current).putLong("observed_pack_bytes", pack).putLong("observed_today_bytes", today)
                    .putLong("observed_uptime", uptime).putInt("observed_boot", boot ?: -1).putString(KEY_LAST_DAY, todayKey)
                    .apply { if (oldDay != todayKey) putLong(KEY_TODAY_BASELINE_BYTES, current) }.apply()
            }
        }
        val fallback = context.getSharedPreferences("cleansweep_state", Context.MODE_PRIVATE)
        val configured = if (store.contains(KEY_PACK_LIMIT_GB)) store.getFloat(KEY_PACK_LIMIT_GB, DEFAULT_PACK_LIMIT_GB) else fallback.getFloat(KEY_PACK_LIMIT_GB, DEFAULT_PACK_LIMIT_GB)
        val limitGb = configured.takeIf { it.isFinite() && it > 0 && it <= 100_000 } ?: DEFAULT_PACK_LIMIT_GB
        val unlimited = store.getBoolean(KEY_UNLIMITED_5G, false) || fallback.getBoolean(KEY_UNLIMITED_5G, false)
        val limit = (limitGb * 1024.0 * 1024.0 * 1024.0).toLong()
        val remaining = (limit - pack).coerceAtLeast(0)
        val result = UsageInfo(access, today, pack, start, limitGb, limit, remaining,
            if (available) formatBytesSafe(today) else "Not measured", if (available) formatBytesSafe(pack) else "Not measured",
            if (available) formatBytesSafe(remaining) else "Not measured", "%.1f GB".format(limitGb),
            if (available && limit > 0) (pack.toFloat() / limit).coerceIn(0f, 1f) else 0f, unlimited, available, partial,
            if (!available) "Mobile statistics unavailable" else if (partial) "Observed mobile counter (partial)" else "Android mobile network statistics", now)
        runCatching { DataBudgetRepo.recordToday(context, result) }
        return result
    }

    /** Complete past local-calendar days only; unknown days remain null. Never includes Wi-Fi. */
    fun recentDays(context: Context, count: Int = 7): List<DailyDataPoint> {
        if (!UsageStats.hasUsageAccess(context)) return emptyList()
        val manager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager ?: return emptyList()
        val today = LocalDate.now(); val zone = ZoneId.systemDefault(); val now = System.currentTimeMillis()
        return (count.coerceIn(1, 30) downTo 1).map { offset ->
            val day = today.minusDays(offset.toLong())
            val bytes = queryMobileTotal(manager, day.atStartOfDay(zone).toInstant().toEpochMilli(), day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
            DailyDataPoint(day.toString(), bytes, bytes != null, "Android mobile network statistics", now)
        }
    }
    private fun mobileCounter(): Long? = sumOrNull(TrafficStats.getMobileRxBytes(), TrafficStats.getMobileTxBytes())
    private fun queryMobileTotal(manager: NetworkStatsManager, start: Long, end: Long): Long? = try {
        require(start <= end)
        val bucket = manager.querySummaryForDevice(ConnectivityManager.TYPE_MOBILE, null, start, end)
        sumOrNull(bucket.rxBytes, bucket.txBytes)
    } catch (e: Exception) { null }
    private fun sumOrNull(rx: Long, tx: Long): Long? = if (rx < 0 || tx < 0 || rx > Long.MAX_VALUE - tx) null else rx + tx
    fun formatBytesSafe(bytes: Long): String = when {
        bytes <= 0 -> "0 MB"
        bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        else -> "%.0f KB".format(bytes / 1024.0)
    }
}
