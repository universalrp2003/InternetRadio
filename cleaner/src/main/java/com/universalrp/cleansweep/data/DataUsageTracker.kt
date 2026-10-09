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
 * and cumulative billing cycle/pack usage with a custom user-configurable pack limit in GB
 * (supports any variable plan: 1.5GB, 6GB, 12GB, 25GB, etc.).
 *
 * Strictly measures mobile cellular traffic (TYPE_MOBILE) - zero Wi-Fi traffic is included.
 */
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
    )

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isUnlimited5g(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_UNLIMITED_5G, false)

    fun setUnlimited5g(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_UNLIMITED_5G, enabled).apply()
        // Also persist in the main app prefs as backup so an update never loses it
        context.getSharedPreferences("cleansweep_state", Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_UNLIMITED_5G, enabled).apply()
    }

    fun getPackLimitGb(context: Context): Float {
        return getPrefs(context).getFloat(KEY_PACK_LIMIT_GB, DEFAULT_PACK_LIMIT_GB)
    }

    fun setPackLimitGb(context: Context, limitGb: Float) {
        val safe = if (limitGb > 0f) limitGb else DEFAULT_PACK_LIMIT_GB
        getPrefs(context).edit().putFloat(KEY_PACK_LIMIT_GB, safe).apply()
        context.getSharedPreferences("cleansweep_state", Context.MODE_PRIVATE)
            .edit().putFloat(KEY_PACK_LIMIT_GB, safe).apply()
    }

    fun resetPackUsage(context: Context, newLimitGb: Float? = null) {
        val now = System.currentTimeMillis()
        val prefs = getPrefs(context)
        val manager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
        val hasAccess = UsageStats.hasUsageAccess(context)

        val currentCumulativeMobile = if (hasAccess && manager != null) {
            queryMobileTotal(manager, 0L, now)
        } else {
            TrafficStats.getMobileRxBytes().coerceAtLeast(0L) + TrafficStats.getMobileTxBytes().coerceAtLeast(0L)
        }

        val editor = prefs.edit()
            .putLong(KEY_PACK_START_MS, now)
            .putLong(KEY_PACK_BASELINE_BYTES, currentCumulativeMobile)

        if (newLimitGb != null && newLimitGb > 0f) {
            editor.putFloat(KEY_PACK_LIMIT_GB, newLimitGb)
            context.getSharedPreferences("cleansweep_state", Context.MODE_PRIVATE)
                .edit().putFloat(KEY_PACK_LIMIT_GB, newLimitGb).apply()
        }
        editor.apply()
        context.getSharedPreferences("cleansweep_state", Context.MODE_PRIVATE)
            .edit()
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
            // Strict mobile-only fallback via TrafficStats
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

        val fallbackPrefs = context.getSharedPreferences("cleansweep_state", Context.MODE_PRIVATE)
        val limitGb = if (prefs.contains(KEY_PACK_LIMIT_GB)) {
            prefs.getFloat(KEY_PACK_LIMIT_GB, DEFAULT_PACK_LIMIT_GB)
        } else {
            fallbackPrefs.getFloat(KEY_PACK_LIMIT_GB, DEFAULT_PACK_LIMIT_GB)
        }
        val isUnlimited = prefs.getBoolean(KEY_UNLIMITED_5G, false) || fallbackPrefs.getBoolean(KEY_UNLIMITED_5G, false)
        val limitBytes = (limitGb.toDouble() * 1024.0 * 1024.0 * 1024.0).toLong()
        val remainingBytes = (limitBytes - packMobile).coerceAtLeast(0L)
        val progressRatio = if (limitBytes > 0L) {
            (packMobile.toFloat() / limitBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f

        val formattedLimit = if (limitGb % 1.0f == 0.0f) {
            "%.0f GB".format(limitGb)
        } else {
            "%.1f GB".format(limitGb)
        }

        return UsageInfo(
            hasUsageAccess = hasAccess,
            todayMobileBytes = todayMobile,
            packTotalMobileBytes = packMobile,
            packStartDateMs = packStart,
            packLimitGb = limitGb,
            packLimitBytes = limitBytes,
            packRemainingBytes = remainingBytes,
            formattedToday = formatBytesSafe(todayMobile),
            formattedPackTotal = formatBytesSafe(packMobile),
            formattedRemaining = formatBytesSafe(remainingBytes),
            formattedPackLimit = formattedLimit,
            progressRatio = progressRatio,
            isUnlimited5g = isUnlimited,
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
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        val mb = bytes / (1024.0 * 1024.0)
        return if (gb >= 1.0) {
            "%.2f GB".format(gb)
        } else if (mb >= 1.0) {
            "%.1f MB".format(mb)
        } else {
            "%.0f KB".format(bytes / 1024.0)
        }
    }
}
