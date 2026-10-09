package com.universalrp.cleansweep.data

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.Build
import java.util.Calendar

/**
 * Tracks daily mobile data usage (auto-resets daily at midnight)
 * and cumulative billing cycle/pack usage (resettable by user for 6GB/12GB recharges).
 */
object DataUsageTracker {

    private const val PREFS = "cleansweep_data_pack"
    private const val KEY_PACK_START_MS = "pack_start_ms"
    private const val KEY_PACK_BASELINE_BYTES = "pack_baseline_bytes"
    private const val KEY_LAST_DAY = "last_recorded_day"
    private const val KEY_TODAY_BASELINE_BYTES = "today_baseline_bytes"

    data class UsageInfo(
        val hasUsageAccess: Boolean,
        val todayMobileBytes: Long,
        val packTotalMobileBytes: Long,
        val packStartDateMs: Long,
        val formattedToday: String,
        val formattedPackTotal: String,
    )

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun resetPackUsage(context: Context) {
        val now = System.currentTimeMillis()
        val prefs = getPrefs(context)
        val manager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
        val hasAccess = UsageStats.hasUsageAccess(context)

        val currentCumulativeMobile = if (hasAccess && manager != null) {
            queryMobileTotal(manager, 0L, now)
        } else {
            TrafficStats.getMobileRxBytes().coerceAtLeast(0L) + TrafficStats.getMobileTxBytes().coerceAtLeast(0L)
        }

        prefs.edit()
            .putLong(KEY_PACK_START_MS, now)
            .putLong(KEY_PACK_BASELINE_BYTES, currentCumulativeMobile)
            .apply()
    }

    fun getUsageInfo(context: Context): UsageInfo {
        val prefs = getPrefs(context)
        val now = System.currentTimeMillis()
        val hasAccess = UsageStats.hasUsageAccess(context)
        val manager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager

        // Today midnight calculation
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val todayMidnight = cal.timeInMillis
        val todayKey = "day_${cal.get(Calendar.YEAR)}_${cal.get(Calendar.DAY_OF_YEAR)}"

        var packStart = prefs.getLong(KEY_PACK_START_MS, 0L)
        if (packStart == 0L) {
            packStart = todayMidnight
            prefs.edit().putLong(KEY_PACK_START_MS, packStart).apply()
        }

        var todayMobile = 0L
        var packMobile = 0L

        if (hasAccess && manager != null) {
            todayMobile = queryMobileTotal(manager, todayMidnight, now)
            packMobile = queryMobileTotal(manager, packStart, now)
        } else {
            // Fallback via TrafficStats and recorded baselines
            val currentMobile = (TrafficStats.getMobileRxBytes().coerceAtLeast(0L) +
                TrafficStats.getMobileTxBytes().coerceAtLeast(0L))

            val lastDay = prefs.getString(KEY_LAST_DAY, "")
            var todayBaseline = prefs.getLong(KEY_TODAY_BASELINE_BYTES, -1L)
            if (lastDay != todayKey || todayBaseline < 0L) {
                todayBaseline = currentMobile
                prefs.edit()
                    .putString(KEY_LAST_DAY, todayKey)
                    .putLong(KEY_TODAY_BASELINE_BYTES, todayBaseline)
                    .apply()
            }
            todayMobile = if (currentMobile >= todayBaseline) (currentMobile - todayBaseline) else currentMobile

            val packBaseline = prefs.getLong(KEY_PACK_BASELINE_BYTES, 0L)
            packMobile = if (currentMobile >= packBaseline) (currentMobile - packBaseline) else currentMobile
        }

        return UsageInfo(
            hasUsageAccess = hasAccess,
            todayMobileBytes = todayMobile,
            packTotalMobileBytes = packMobile,
            packStartDateMs = packStart,
            formattedToday = formatBytesSafe(todayMobile),
            formattedPackTotal = formatBytesSafe(packMobile),
        )
    }

    private fun queryMobileTotal(manager: NetworkStatsManager, start: Long, end: Long): Long = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val bucket = manager.querySummaryForDevice(ConnectivityManager.TYPE_MOBILE, null, start, end)
            (bucket.rxBytes + bucket.txBytes).coerceAtLeast(0L)
        } else 0L
    } catch (e: Exception) {
        0L
    }

    fun formatBytesSafe(bytes: Long): String {
        if (bytes <= 0L) return "0 MB"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            "%.2f GB".format(mb / 1024.0)
        } else {
            "%.1f MB".format(mb)
        }
    }
}
