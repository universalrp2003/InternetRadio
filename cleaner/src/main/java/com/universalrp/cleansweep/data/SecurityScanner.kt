package com.universalrp.cleansweep.data

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class Severity { HIGH, MEDIUM, LOW, INFO }

data class Finding(
    val id: String,
    val title: String,
    val detail: String,
    val severity: Severity,
    val count: Int = 0,
    val samples: List<String> = emptyList(),
    val fixHint: String? = null,
)

data class SecurityReport(
    val findings: List<Finding>,
    val score: Int,
    val appsChecked: Int,
    val scannedAtMs: Long,
) {
    val highCount: Int get() = findings.count { it.severity == Severity.HIGH }
    val mediumCount: Int get() = findings.count { it.severity == Severity.MEDIUM }
    val verdict: String
        get() = when {
            score >= 90 -> "Looking good"
            score >= 75 -> "A few things to check"
            score >= 55 -> "Needs attention"
            else -> "Act on the red items"
        }
}

/**
 * On-phone security review.
 *
 * These are checks of Android's own settings and of the permissions apps ask for.
 * That catches the things that actually cause trouble on a phone (apps that can
 * read your SMS, apps that can install other apps, rogue accessibility services,
 * notification readers). It is **not** a virus scanner: file hashes are not looked
 * up anywhere, because CleanSweep does not upload anything. The UI says this in
 * plain words as well, because claiming otherwise would be dishonest.
 */
object SecurityScanner {

    /**
     * "Which apps can read notifications" lives in this Settings.Secure key. The Java
     * constant is hidden from the public SDK, so the key is spelled out here.
     */
    private const val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"

    /** Accessibility services that belong to the phone itself. */
    private val SYSTEM_ACCESSIBILITY = listOf(
        "com.android.", "android.", "com.google.android.marvin", "com.google.android.accessibility",
        "com.samsung.accessibility", "com.miui.accessibility", "com.oppo.", "com.coloros.",
        "com.vivo.", "com.oneplus.", "com.huawei.", "com.transsion.",
    )

    private val KNOWN_STORES = listOf(
        "vending", "packageinstaller", "galaxyapps", "appstore", "market", "appgallery",
        "getapps", "huawei", "amazon", "aptoide", "samsung", "miui", "coloros", "vivo",
    )

    suspend fun scan(
        context: Context,
        apps: List<AppRow>,
    ): SecurityReport = withContext(Dispatchers.IO) {
        val findings = mutableListOf<Finding>()
        val pm = context.packageManager

        // ---------------------------------------------------- usability checks
        val accessibility = enabledServices(
            context, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).filter { name -> SYSTEM_ACCESSIBILITY.none { name.startsWith(it) } }
        if (accessibility.isNotEmpty()) {
            findings.add(
                Finding(
                    id = "accessibility",
                    title = "Accessibility services are switched on",
                    detail = "An accessibility service can read everything on your screen and tap " +
                        "buttons for you. Legitimate ones exist (screen readers, auto-clickers), but " +
                        "malware loves this permission more than any other.",
                    severity = Severity.HIGH,
                    count = accessibility.size,
                    samples = accessibility.map { it.substringAfterLast('/') },
                    fixHint = "Settings → Accessibility → turn off anything you did not install on purpose.",
                )
            )
        }

        val listeners = enabledServices(
            context, ENABLED_NOTIFICATION_LISTENERS
        ).filter { name -> SYSTEM_ACCESSIBILITY.none { name.startsWith(it) } }
        if (listeners.isNotEmpty()) {
            findings.add(
                Finding(
                    id = "notification_listeners",
                    title = "Apps can read your notifications",
                    detail = "Notification access lets an app read every notification — including " +
                        "banking and OTP messages.",
                    severity = Severity.HIGH,
                    count = listeners.size,
                    samples = listeners.map { it.substringAfterLast('/') },
                    fixHint = "Settings → Notifications → Notification access.",
                )
            )
        }

        val admins = activeAdmins(context)
        if (admins.isNotEmpty()) {
            findings.add(
                Finding(
                    id = "device_admin",
                    title = "Apps with device-admin rights",
                    detail = "Device administrators can lock the phone, wipe it, or block " +
                        "uninstalling themselves.",
                    severity = Severity.MEDIUM,
                    count = admins.size,
                    samples = admins,
                    fixHint = "Settings → Security → Device admin apps.",
                )
            )
        }

        val installers = apps.filter { "android.permission.REQUEST_INSTALL_PACKAGES" in it.permissions }
            .filter { AppInventoryLoader.canInstallPackages(context, it.pkg) }
        if (installers.isNotEmpty()) {
            findings.add(
                Finding(
                    id = "install_other_apps",
                    title = "Apps allowed to install other apps",
                    detail = "With \"install unknown apps\" allowed, an app can silently drop new " +
                        "APKs on your phone.",
                    severity = Severity.HIGH,
                    count = installers.size,
                    samples = installers.take(6).map { it.label },
                    fixHint = "Settings → Apps → Special access → Install unknown apps.",
                )
            )
        }

        val usageApps = apps.filter {
            "android.permission.PACKAGE_USAGE_STATS" in it.permissions &&
                AppInventoryLoader.hasUsageAccess(context, it.pkg)
        }
        if (usageApps.size > 3) {
            findings.add(
                Finding(
                    id = "usage_access",
                    title = "${usageApps.size} apps have usage access",
                    detail = "Usage access reveals which apps you open and when. A couple are normal " +
                        "(launchers, digital wellbeing); a crowd is worth reviewing.",
                    severity = Severity.LOW,
                    count = usageApps.size,
                    samples = usageApps.take(6).map { it.label },
                    fixHint = "Settings → Apps → Special access → Usage access.",
                )
            )
        }

        val overlay = apps.filter { "android.permission.SYSTEM_ALERT_WINDOW" in it.permissions }
        if (overlay.size > 6) {
            findings.add(
                Finding(
                    id = "overlay",
                    title = "${overlay.size} apps ask to draw over other apps",
                    detail = "The overlay permission is what fake login screens use. Only a few apps " +
                        "genuinely need it (screen recorders, floating timers).",
                    severity = Severity.MEDIUM,
                    count = overlay.size,
                    samples = overlay.take(6).map { it.label },
                    fixHint = "Settings → Apps → Special access → Display over other apps.",
                )
            )
        }

        // ------------------------------------------------------ trust checks
        val sideloaded = apps.filter { it.isSideloaded }
        if (sideloaded.isNotEmpty()) {
            findings.add(
                Finding(
                    id = "sideloaded",
                    title = "${sideloaded.size} apps were not installed from an app store",
                    detail = "An app installed from a file has not been reviewed by any store. That is " +
                        "normal for betas and mods — just make sure you know where each one came from.",
                    severity = Severity.MEDIUM,
                    count = sideloaded.size,
                    samples = sideloaded.take(8).map { it.label },
                    fixHint = "Open each one below and uninstall anything you do not recognise.",
                )
            )
        }

        val smsApps = apps.filter {
            it.permissions.any { p ->
                p == "android.permission.READ_SMS" || p == "android.permission.RECEIVE_SMS"
            } && !it.isSystem
        }
        if (smsApps.isNotEmpty()) {
            findings.add(
                Finding(
                    id = "sms_readers",
                    title = "${smsApps.size} installed apps can read your SMS",
                    detail = "SMS is where OTPs arrive. Only your messaging app should need this.",
                    severity = Severity.HIGH,
                    count = smsApps.size,
                    samples = smsApps.take(6).map { it.label },
                    fixHint = "Revoke SMS permission for anything that is not your SMS app.",
                )
            )
        }

        val stubs = apps.filter { it.isKnownBloatStub }
        if (stubs.isNotEmpty()) {
            findings.add(
                Finding(
                    id = "facebook_stubs",
                    title = "Preinstalled Facebook components",
                    detail = "Vendors bundle Facebook's service stubs so its apps install faster. They " +
                        "run in the background even if you never open Facebook.",
                    severity = Severity.LOW,
                    count = stubs.size,
                    samples = stubs.map { it.label },
                    fixHint = "Disable them from their app page (they cannot be uninstalled on most ROMs).",
                )
            )
        }

        val suspiciousInstallers = apps.filter { app ->
            if (app.isSystem || app.installer == null) return@filter false
            val lower = app.installer.lowercase()
            KNOWN_STORES.none { lower.contains(it) }
        }
        if (suspiciousInstallers.isNotEmpty()) {
            findings.add(
                Finding(
                    id = "odd_installer",
                    title = "${suspiciousInstallers.size} apps came from an unusual installer",
                    detail = "The installer package is the app that put this one on your phone. " +
                        "Browser or file-manager installs show up here.",
                    severity = Severity.LOW,
                    count = suspiciousInstallers.size,
                    samples = suspiciousInstallers.take(6).map { "${it.label} ← ${it.installer}" },
                )
            )
        }

        // ------------------------------------------------------ device checks
        if (!isScreenLocked(context)) {
            findings.add(
                Finding(
                    id = "no_lock",
                    title = "No screen lock set",
                    detail = "Without a PIN, pattern or password, anyone holding the phone can read " +
                        "your messages and install apps.",
                    severity = Severity.MEDIUM,
                    fixHint = "Settings → Security → Screen lock.",
                )
            )
        }

        if (developerOptionsEnabled(context)) {
            findings.add(
                Finding(
                    id = "developer_options",
                    title = "Developer options are enabled",
                    detail = "That is perfectly fine if you turned them on. It also means USB " +
                        "debugging may be available to a connected computer.",
                    severity = Severity.INFO,
                    fixHint = "Settings → System → Developer options (turn off if you do not use it).",
                )
            )
        }

        val patchAge = securityPatchAgeMonths(context)
        if (patchAge != null && patchAge > 12) {
            findings.add(
                Finding(
                    id = "old_patch",
                    title = "Security patch is ${patchAge} months old",
                    detail = "Android security updates fix real, known bugs. An old patch level means " +
                        "those bugs are still there on this phone.",
                    severity = Severity.MEDIUM,
                    fixHint = "Settings → About phone → System update.",
                )
            )
        }

        val debugInstalled = apps.count { app -> app.label.lowercase().contains("root") && !app.isSystem }
        if (debugInstalled > 0) {
            findings.add(
                Finding(
                    id = "root_tools",
                    title = "$debugInstalled root-related app(s) installed",
                    detail = "Root tools and \"game hackers\" ask for deep access. They only work on " +
                        "rooted phones — and they can read everything on one.",
                    severity = Severity.LOW,
                    count = debugInstalled,
                )
            )
        }

        findings.add(
            Finding(
                id = "no_hash_lookup",
                title = "No file-hash virus check",
                detail = "CleanSweep deliberately has no internet permission, so it cannot compare " +
                    "your files against online virus databases. Everything above is checked on the " +
                    "phone itself. For a file-hash scan, use Play Protect or an online scanner.",
                severity = Severity.INFO,
            )
        )

        val score = score(findings)
        SecurityReport(
            findings = findings.sortedByDescending { it.severity.ordinal * -1 },
            score = score,
            appsChecked = apps.size,
            scannedAtMs = System.currentTimeMillis(),
        )
    }

    /** Findings are ordered worst-first, so sort on a numeric weight instead. */
    private fun score(findings: List<Finding>): Int {
        var score = 100
        findings.forEach { finding ->
            score -= when (finding.severity) {
                Severity.HIGH -> 15
                Severity.MEDIUM -> 7
                Severity.LOW -> 3
                Severity.INFO -> 0
            }
        }
        return score.coerceIn(0, 100)
    }

    private fun enabledServices(context: Context, key: String): List<String> = try {
        val raw = Settings.Secure.getString(context.contentResolver, key) ?: ""
        raw.split(':').map { it.trim() }.filter { it.isNotEmpty() }
    } catch (e: Exception) {
        emptyList()
    }

    private fun activeAdmins(context: Context): List<String> = try {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        @Suppress("DEPRECATION")
        dpm?.activeAdmins?.map { (it as ComponentName).packageName }?.distinct().orEmpty()
    } catch (e: Exception) {
        emptyList()
    }

    private fun isScreenLocked(context: Context): Boolean = try {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE)
            as? android.app.KeyguardManager
        km?.isDeviceSecure ?: true
    } catch (e: Exception) {
        true
    }

    private fun developerOptionsEnabled(context: Context): Boolean = try {
        Settings.Global.getInt(
            context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0
        ) == 1
    } catch (e: Exception) {
        false
    }

    private fun securityPatchAgeMonths(context: Context): Int? {
        val patch = Build.VERSION.SECURITY_PATCH ?: return null
        return try {
            val parts = patch.split("-")
            if (parts.size < 2) return null
            val year = parts[0].toInt()
            val month = parts[1].toInt()
            val now = java.util.Calendar.getInstance()
            val nowMonths = now.get(java.util.Calendar.YEAR) * 12 + now.get(java.util.Calendar.MONTH) + 1
            (nowMonths - (year * 12 + month)).coerceAtLeast(0)
        } catch (e: Exception) {
            null
        }
    }

    /** Used by the apps screen to explain a tag. */
    fun isSystemPackage(pkg: String): Boolean {
        val prefixes = listOf(
            "com.android.", "com.google.android.", "android.", "com.miui.", "com.coloros.",
            "com.oppo.", "com.vivo.", "com.samsung.", "com.huawei.", "com.transsion.",
        )
        return prefixes.any { pkg.startsWith(it) }
    }

    @Suppress("unused")
    private fun flagOf(ai: ApplicationInfo): Boolean =
        (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0

    @Suppress("unused")
    private fun pmOf(context: Context): PackageManager = context.packageManager
}
