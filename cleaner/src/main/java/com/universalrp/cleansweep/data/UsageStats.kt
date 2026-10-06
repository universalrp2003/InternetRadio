package com.universalrp.cleansweep.data

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.Build

/**
 * How much data has gone through the phone today, split into mobile and Wi-Fi.
 *
 * Two sources, and the screen says which one is in use:
 *
 *  * **NetworkStatsManager** — the accurate, per-day, per-network numbers the system keeps.
 *    Android only hands these to apps the user has granted "Usage access", which is why the
 *    screen asks for it explicitly (Settings → Special access → Usage access → CleanSweep).
 *  * **TrafficStats** — always available, but only counts since the phone was switched on,
 *    so it can under-report a long day. It is used as a fallback and labelled as such.
 */
data class DataAppUse(val label: String, val pkg: String, val bytes: Long)

data class DataUsageReport(
    val hasUsageAccess: Boolean,
    val todayMobileBytes: Long?,
    val todayWifiBytes: Long?,
    val todayAppMobile: List<DataAppUse>,
    val todayAppWifi: List<DataAppUse>,
    val sinceBootRxBytes: Long,
    val sinceBootTxBytes: Long,
    val sinceBootMobileBytes: Long,
    val sinceBootWifiBytes: Long,
    val note: String,
) {
    val todayTotalBytes: Long? get() = when {
        todayMobileBytes == null && todayWifiBytes == null -> null
        else -> (todayMobileBytes ?: 0L) + (todayWifiBytes ?: 0L)
    }
}

object UsageStats {

    /** Midnight today, local time. */
    private fun startOfToday(): Long {
        val now = java.util.Calendar.getInstance()
        now.set(java.util.Calendar.HOUR_OF_DAY, 0)
        now.set(java.util.Calendar.MINUTE, 0)
        now.set(java.util.Calendar.SECOND, 0)
        now.set(java.util.Calendar.MILLISECOND, 0)
        return now.timeInMillis
    }

    fun hasUsageAccess(context: Context): Boolean = try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        }
        mode == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
        false
    }

    fun read(context: Context): DataUsageReport {
        val hasAccess = hasUsageAccess(context)
        val start = startOfToday()
        val end = System.currentTimeMillis()

        var todayMobile: Long? = null
        var todayWifi: Long? = null
        var appMobile = emptyList<DataAppUse>()
        var appWifi = emptyList<DataAppUse>()

        val manager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager

        if (hasAccess && manager != null) {
            todayMobile = queryDevice(manager, ConnectivityManager.TYPE_MOBILE, start, end)
            todayWifi = queryDevice(manager, ConnectivityManager.TYPE_WIFI, start, end)
            appMobile = topApps(context, manager, ConnectivityManager.TYPE_MOBILE, start, end)
            appWifi = topApps(context, manager, ConnectivityManager.TYPE_WIFI, start, end)
        }

        return DataUsageReport(
            hasUsageAccess = hasAccess,
            todayMobileBytes = todayMobile,
            todayWifiBytes = todayWifi,
            todayAppMobile = appMobile,
            todayAppWifi = appWifi,
            sinceBootRxBytes = TrafficStats.getTotalRxBytes().takeIf { it >= 0 } ?: 0L,
            sinceBootTxBytes = TrafficStats.getTotalTxBytes().takeIf { it >= 0 } ?: 0L,
            sinceBootMobileBytes = TrafficStats.getMobileRxBytes().takeIf { it >= 0 } ?: 0L,
            sinceBootWifiBytes = (
                (TrafficStats.getTotalRxBytes().takeIf { it >= 0 } ?: 0L) -
                    (TrafficStats.getMobileRxBytes().takeIf { it >= 0 } ?: 0L) +
                    ((TrafficStats.getTotalTxBytes().takeIf { it >= 0 } ?: 0L) -
                        (TrafficStats.getMobileTxBytes().takeIf { it >= 0 } ?: 0L))
                ).coerceAtLeast(0L),
            note = if (hasAccess) {
                "Numbers come from Android's own per-app network accounting, from midnight today."
            } else {
                "Without Usage access Android only reveals the totals since the phone was switched " +
                    "on — and it hides per-app details completely. Allow it and reopen this screen " +
                    "for today's exact mobile and Wi-Fi figures."
            },
        )
    }

    private fun queryDevice(
        manager: NetworkStatsManager,
        type: Int,
        start: Long,
        end: Long,
    ): Long? = try {
        val bucket = manager.querySummaryForDevice(type, null, start, end)
        (bucket.rxBytes + bucket.txBytes).coerceAtLeast(0L)
    } catch (e: Exception) {
        null
    }

    /** Per-app usage for today, biggest first. */
    private fun topApps(
        context: Context,
        manager: NetworkStatsManager,
        type: Int,
        start: Long,
        end: Long,
    ): List<DataAppUse> = try {
        val perUid = HashMap<Int, Long>()
        val stats: NetworkStats = manager.querySummary(type, null, start, end)
        val bucket = NetworkStats.Bucket()
        while (stats.hasNextBucket()) {
            stats.getNextBucket(bucket)
            perUid[bucket.uid] = (perUid[bucket.uid] ?: 0L) +
                (bucket.rxBytes + bucket.txBytes).coerceAtLeast(0L)
        }
        stats.close()
        val pm = context.packageManager
        perUid.entries
            .sortedByDescending { it.value }
            .take(8)
            .mapNotNull { (uid, bytes) ->
                if (bytes <= 0L) return@mapNotNull null
                val pkg = pm.getPackagesForUid(uid)?.firstOrNull() ?: return@mapNotNull null
                val label = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (e: Exception) {
                    pkg
                }
                DataAppUse(label, pkg, bytes)
            }
    } catch (e: Exception) {
        emptyList()
    }
}
