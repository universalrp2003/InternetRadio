package com.universalrp.cleansweep.data

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Everything CleanSweep can honestly learn about the apps installed on this phone:
 * size, when it was installed, when it was last opened, who installed it, and which
 * permissions it asks for.
 *
 * The phone's own Android APIs only. No list is downloaded, nothing is uploaded —
 * the "bloatware" and "suspicious" tags below are explained in the UI so you can
 * see exactly why an app got a tag.
 */
data class AppRow(
    val pkg: String,
    val label: String,
    val isSystem: Boolean,
    val isUpdatedSystem: Boolean,
    val apkBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
    val firstInstallMs: Long,
    val updatedMs: Long,
    val lastUsedMs: Long?,
    val hasLauncher: Boolean,
    val installer: String?,
    val permissions: List<String>,
    val riskyPermissions: List<String>,
    val tags: List<String>,
    val canUninstall: Boolean,
) {
    val totalBytes: Long get() = if (dataBytes > 0L) dataBytes + cacheBytes else apkBytes + cacheBytes
    val isUnused: Boolean get() = lastUsedMs == null || lastUsedMs < System.currentTimeMillis() - 30L * 24 * 3600 * 1000
    val isPreinstalled: Boolean get() = isSystem && !isUpdatedSystem
    val isSideloaded: Boolean get() = !isSystem && installer == null
    val isKnownBloatStub: Boolean get() = pkg in BloatRules.KNOWN_STUB_PACKAGES
}

data class AppInventoryReport(
    val rows: List<AppRow>,
    val usageAccessGranted: Boolean,
    val scannedAtMs: Long,
) {
    val totalBytes: Long get() = rows.sumOf { it.totalBytes }
    val preinstalledCount: Int get() = rows.count { it.isPreinstalled }
    val unusedCount: Int get() = rows.count { it.isUnused && !it.isSystem }
    val sideloadedCount: Int get() = rows.count { it.isSideloaded }
    val riskyCount: Int get() = rows.count { it.riskyPermissions.isNotEmpty() }
    val stubCount: Int get() = rows.count { it.isKnownBloatStub }
}

object BloatRules {

    /**
     * Facebook's stub apps are bundled by several vendors and cannot be removed
     * without disabling them first. This is a fact about those packages, not a
     * guess about your phone.
     */
    val KNOWN_STUB_PACKAGES = setOf(
        "com.facebook.appmanager",
        "com.facebook.services",
        "com.facebook.system",
        "com.facebook.katana",
        "com.linkedin.android",
        "com.netflix.partner.activation",
    )

    /** Installers that mean "came from an app store" rather than a file. */
    private val TRUSTED_INSTALLER_HINTS = listOf(
        "vending", "packageinstaller", "galaxyapps", "appstore", "appstore",
        "market", "appgallery", "getapps", "microsoft", "android",
    )

    /** Permissions that deserve a second look, with a plain-language label. */
    val RISKY_PERMISSIONS: Map<String, String> = mapOf(
        "android.permission.READ_SMS" to "can read your SMS",
        "android.permission.RECEIVE_SMS" to "can receive your SMS",
        "android.permission.SEND_SMS" to "can send SMS (costs money)",
        "android.permission.READ_CALL_LOG" to "can read your call history",
        "android.permission.WRITE_CALL_LOG" to "can change your call history",
        "android.permission.PROCESS_OUTGOING_CALLS" to "can see who you call",
        "android.permission.CALL_PHONE" to "can make calls by itself",
        "android.permission.READ_CONTACTS" to "can read your contacts",
        "android.permission.WRITE_CONTACTS" to "can change your contacts",
        "android.permission.RECORD_AUDIO" to "can use the microphone",
        "android.permission.CAMERA" to "can use the camera",
        "android.permission.ACCESS_FINE_LOCATION" to "can track precise location",
        "android.permission.ACCESS_BACKGROUND_LOCATION" to "can track location in the background",
        "android.permission.REQUEST_INSTALL_PACKAGES" to "can install other apps",
        "android.permission.SYSTEM_ALERT_WINDOW" to "can draw over other apps",
        "android.permission.PACKAGE_USAGE_STATS" to "can see which apps you use",
        "android.permission.MANAGE_EXTERNAL_STORAGE" to "can read all your files",
        "android.permission.READ_EXTERNAL_STORAGE" to "can read your files",
        "android.permission.WRITE_EXTERNAL_STORAGE" to "can change your files",
        "android.permission.READ_PHONE_STATE" to "can read phone identity",
        "android.permission.READ_PHONE_NUMBERS" to "can read your phone number",
        "android.permission.BODY_SENSORS" to "can read body sensors",
        "android.permission.GET_ACCOUNTS" to "can see your accounts",
        "android.permission.QUERY_ALL_PACKAGES" to "can list every installed app",
    )

    fun riskyLabels(permissions: List<String>): List<String> =
        permissions.mapNotNull { RISKY_PERMISSIONS[it] }.distinct()

    fun isTrustedInstaller(installer: String?): Boolean {
        if (installer == null) return false
        val lower = installer.lowercase()
        return TRUSTED_INSTALLER_HINTS.any { lower.contains(it) }
    }
}

object AppInventoryLoader {

    suspend fun load(
        context: Context,
        includeSystem: Boolean,
    ): AppInventoryReport = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val usageAccess = AppCacheRepo.hasUsageAccess(context)
        val stats = if (usageAccess) usageStats(context) else emptyMap()
        val storageStats = context.getSystemService(Context.STORAGE_STATS_SERVICE)
            as? StorageStatsManager

        val packages: List<PackageInfo> = try {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        } catch (e: Exception) {
            emptyList()
        }

        val rows = mutableListOf<AppRow>()
        for (info in packages) {
            val ai = info.applicationInfo ?: continue
            if (ai.packageName == context.packageName) continue
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val updatedSystem = (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (isSystem && !includeSystem) continue

            val permissions = info.requestedPermissions?.toList().orEmpty()

            var dataBytes = 0L
            var cacheBytes = 0L
            if (storageStats != null) {
                try {
                    val s = storageStats.queryStatsForPackage(
                        StorageManager.UUID_DEFAULT,
                        ai.packageName,
                        Process.myUserHandle(),
                    )
                    dataBytes = s.dataBytes + s.appBytes
                    cacheBytes = s.cacheBytes
                } catch (e: Exception) {
                    // Some system packages cannot be measured; leave 0.
                }
            }
            var apkBytes = 0L
            ai.sourceDir?.let { path ->
                apkBytes = try {
                    java.io.File(path).length()
                } catch (e: Exception) {
                    0L
                }
            }
            ai.publicSourceDir?.let { path -> if (apkBytes <= 0L) {
                apkBytes = try { java.io.File(path).length() } catch (e: Exception) { 0L }
            } }

            val installer = installerOf(pm, ai.packageName)
            val lastUsed = stats[ai.packageName]
            val risky = BloatRules.riskyLabels(permissions)
            val launcher = hasLauncherActivity(pm, ai.packageName)

            val tags = mutableListOf<String>()
            if (isSystem && !updatedSystem) tags.add("Preinstalled")
            if (updatedSystem) tags.add("Bundled (updated)")
            if (ai.packageName in BloatRules.KNOWN_STUB_PACKAGES) tags.add("Bloatware stub")
            if (!isSystem && (lastUsed == null || lastUsed < System.currentTimeMillis() - 30L * 24 * 3600 * 1000)) {
                tags.add("Unused 30+ days")
            }
            if (!isSystem && !BloatRules.isTrustedInstaller(installer)) tags.add("Sideloaded")
            if (!launcher && !isSystem) tags.add("No launcher icon")
            if (risky.isNotEmpty()) tags.add("Sensitive permissions")

            rows.add(
                AppRow(
                    pkg = ai.packageName,
                    label = ai.loadLabel(pm).toString(),
                    isSystem = isSystem,
                    isUpdatedSystem = updatedSystem,
                    apkBytes = apkBytes,
                    dataBytes = dataBytes,
                    cacheBytes = cacheBytes,
                    firstInstallMs = info.firstInstallTime,
                    updatedMs = info.lastUpdateTime,
                    lastUsedMs = lastUsed,
                    hasLauncher = launcher,
                    installer = installer,
                    permissions = permissions,
                    riskyPermissions = risky,
                    tags = tags,
                    canUninstall = !isSystem,
                )
            )
        }

        AppInventoryReport(
            rows = rows.sortedWith(
                compareByDescending<AppRow> { it.isKnownBloatStub }
                    .thenByDescending { it.riskyPermissions.size }
                    .thenByDescending { it.totalBytes }
                    .thenBy { it.label.lowercase() }
            ),
            usageAccessGranted = usageAccess,
            scannedAtMs = System.currentTimeMillis(),
        )
    }

    private fun usageStats(context: Context): Map<String, Long> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()
        val end = System.currentTimeMillis()
        val begin = end - 30L * 24 * 3600 * 1000
        return try {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_MONTHLY, begin, end)
                .orEmpty()
                .groupBy { it.packageName }
                .mapValues { entry -> entry.value.maxOf { it.lastTimeUsed } }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun installerOf(pm: PackageManager, pkg: String): String? = try {
        if (Build.VERSION.SDK_INT >= 30) {
            pm.getInstallSourceInfo(pkg).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(pkg)
        }
    } catch (e: Exception) {
        null
    }

    private fun hasLauncherActivity(pm: PackageManager, pkg: String): Boolean = try {
        pm.getLaunchIntentForPackage(pkg) != null
    } catch (e: Exception) {
        false
    }

    /** Whether this app is allowed to install other apps right now (AppOps check). */
    fun canInstallPackages(context: Context, pkg: String): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        return try {
            val uid = context.packageManager.getApplicationInfo(pkg, 0).uid
            val mode = if (Build.VERSION.SDK_INT >= 29) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_REQUEST_INSTALL_PACKAGES, uid, pkg
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_REQUEST_INSTALL_PACKAGES, uid, pkg)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    /** Whether this app currently has "usage access" granted. */
    fun hasUsageAccess(context: Context, pkg: String): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        return try {
            val uid = context.packageManager.getApplicationInfo(pkg, 0).uid
            val mode = if (Build.VERSION.SDK_INT >= 29) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, pkg)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, pkg)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }
}
