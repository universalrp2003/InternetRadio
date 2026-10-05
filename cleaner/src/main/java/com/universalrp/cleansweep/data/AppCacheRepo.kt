package com.universalrp.cleansweep.data

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Process
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppCacheInfo(
    val pkg: String,
    val label: String,
    val cacheBytes: Long,
    val isSystem: Boolean,
    val selected: Boolean = true,
)

/** Reads per-app cache sizes via StorageStatsManager (needs Usage Access). */
object AppCacheRepo {

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            // Ignored.
        }
    }

    suspend fun load(
        context: Context,
        includeSystem: Boolean,
    ): List<AppCacheInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val ssm = context.getSystemService(Context.STORAGE_STATS_SERVICE)
            as android.app.usage.StorageStatsManager

        val apps = try {
            pm.getInstalledApplications(0)
        } catch (e: Exception) {
            emptyList()
        }

        val result = mutableListOf<AppCacheInfo>()
        for (ai in apps) {
            if (ai.packageName == context.packageName) continue
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystem && !includeSystem) continue

            val cacheBytes = try {
                ssm.queryStatsForPackage(
                    android.os.storage.StorageManager.UUID_DEFAULT,
                    ai.packageName,
                    Process.myUserHandle(),
                ).cacheBytes
            } catch (e: Exception) {
                -1L // Not readable for this app (common for some system apps).
            }

            if (cacheBytes < 32 * 1024L) continue
            result.add(
                AppCacheInfo(
                    pkg = ai.packageName,
                    label = ai.loadLabel(pm).toString(),
                    cacheBytes = cacheBytes,
                    isSystem = isSystem,
                )
            )
        }
        result.sortedByDescending { it.cacheBytes }.take(300)
    }
}
