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

/**
 * On-phone security review.
 *
 * These are checks of Android's own settings and of the permissions apps currently
 * hold (granted, not merely declared — a revoked permission must not show up here).
 * That catches the things that actually cause trouble on a phone (apps that can
 * read your SMS, apps that can install other apps, rogue accessibility services,
 * notification readers). File-hash malware lookups live in [MalwareCheck] instead:
 * hashes only, never the files themselves.
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
        // v2.11: F-Droid and Aurora are legitimate stores, and Xiaomi's own store
        // reports as com.xiaomi.discover — flagging them cried wolf on every phone
        // that uses them.
        "fdroid", "xiaomi", "aurora", "microsoft",
    )

    /** App names that mean a root tool — word-boundary, so "Rootless" never matches. */
    private val ROOT_TOOL_NAME =
        Regex("(?i)\\broot\\b|rooted|magisk|supersu|kingroot|apatch|kernelsu")

    /**
     * Recognized banking and UPI payment apps that legitimately require SMS permission
     * to perform SIM binding, account verification, and transaction tracking.
     */
    private val BANKING_OR_PAYMENT_HINTS = listOf(
        "bank", "upi", "paytm", "phonepe", "gpay", "bhim", "cred", "yono", "imobile",
        "kotak", "axis", "hdfc", "icici", "pnb", "bob", "canara", "unionbank", "indusind",
        "slice", "jupiter", "fi.money", "mobikwik", "freecharge", "navi", "bajaj", "postpe"
    )

    fun isBankingOrPaymentApp(pkg: String, label: String): Boolean {
        val lowerPkg = pkg.lowercase()
        val lowerLabel = label.lowercase()
        return BANKING_OR_PAYMENT_HINTS.any { hint ->
            lowerPkg.contains(hint) || lowerLabel.contains(hint)
        }
    }

    suspend fun scan(
        context: Context,
        apps: List<AppRow>,
        includesSystemApps: Boolean = true,
    ): SecurityReport = withContext(Dispatchers.IO) {
        val findings = mutableListOf<Finding>()
        val unavailable = mutableSetOf<String>()
        if (apps.isEmpty()) unavailable += SecurityHistoryPolicy.appChecks() + setOf("accessibility", "notification_listeners", "device_admin")
        unavailable += apps.flatMap { it.unavailablePermissionChecks }
        if (!includesSystemApps) unavailable += setOf("facebook_stubs", "root_tools")
        val pm = context.packageManager

        // ---------------------------------------------------- usability checks
        val accessibilityRead = enabledServices(context, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        if (accessibilityRead == null) unavailable += "accessibility"
        val accessibility = accessibilityRead.orEmpty().filter { name -> SYSTEM_ACCESSIBILITY.none { name.startsWith(it) } }
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
                    samples = accessibility.map { serviceApp(context, it)?.label ?: it },
                    affectedApps = accessibility.mapNotNull { serviceApp(context, it) },
                    fixHint = "Settings → Accessibility → turn off anything you did not install on purpose.",
                )
            )
        }

        val listenersRead = enabledServices(context, ENABLED_NOTIFICATION_LISTENERS)
        if (listenersRead == null) unavailable += "notification_listeners"
        val listeners = listenersRead.orEmpty().filter { name -> SYSTEM_ACCESSIBILITY.none { name.startsWith(it) } }
        if (listeners.isNotEmpty()) {
            findings.add(
                Finding(
                    id = "notification_listeners",
                    title = "Apps can read your notifications",
                    detail = "Notification access lets an app read every notification — including " +
                        "banking and OTP messages.",
                    severity = Severity.HIGH,
                    count = listeners.size,
                    samples = listeners.map { serviceApp(context, it)?.label ?: it },
                    affectedApps = listeners.mapNotNull { serviceApp(context, it) },
                    fixHint = "Settings → Notifications → Notification access.",
                )
            )
        }

        // com.google.android.gms is Find My Device — a device admin on every GMS phone by
        // design, not something the user did wrong. Only anything *else* is worth flagging.
        val adminsRead = activeAdmins(context)
        if (adminsRead == null) unavailable += "device_admin"
        val admins = adminsRead.orEmpty().filter { it.packageName != "com.google.android.gms" }
        if (admins.isNotEmpty()) {
            findings.add(
                Finding(
                    id = "device_admin",
                    title = "Apps with device-admin rights",
                    detail = "Device administrators can lock the phone, wipe it, or block " +
                        "uninstalling themselves.",
                    severity = Severity.MEDIUM,
                    count = admins.size,
                    samples = admins.map { labelFor(context, it.packageName) ?: it.packageName },
                    affectedApps = admins.map {
                        FindingApp(labelFor(context, it.packageName) ?: it.packageName, it.packageName, it.flattenToString())
                    },
                    fixHint = "Settings → Security → Device admin apps.",
                )
            )
        }

        // v2.8: grantedPermissions is the live AppOps state — a revoked installer vanishes.
        // v2.9: the OS and its stores install apps by design; only user apps count here.
        val installers = apps.filter {
            !it.isSystem && "android.permission.REQUEST_INSTALL_PACKAGES" in it.grantedPermissions
        }.filter {
            val allowed = AppInventoryLoader.canInstallPackages(context, it.pkg)
            if (allowed == null) unavailable += "install_other_apps"
            allowed == true
        }
        if (installers.isNotEmpty()) {
            findings.add(
                Finding(
                    id = "install_other_apps",
                    title = "Apps allowed to install other apps",
                    detail = "Install unknown apps access lets an app request installation of APKs. Android normally still asks you to confirm. Review sources you no longer need.",
                    severity = Severity.HIGH,
                    count = installers.size,
                    samples = installers.map { it.label },
                    affectedApps = installers.map { FindingApp(it.label, it.pkg) },
                    fixHint = "Settings → Apps → Special access → Install unknown apps.",
                )
            )
        }

        // v2.9: system apps (Settings, System UI) hold usage access by design and are
        // never listed — only apps the user installed count here.
        val usageApps = apps.filter {
            !it.isSystem &&
                "android.permission.PACKAGE_USAGE_STATS" in it.grantedPermissions &&
                run {
                    val allowed = AppInventoryLoader.hasUsageAccess(context, it.pkg)
                    if (allowed == null) unavailable += "usage_access"
                    allowed == true
                }
        }
        if (usageApps.size > 3) {
            findings.add(
                Finding(
                    id = "usage_access",
                    title = "${usageApps.size} apps have usage access",
                    detail = "Usage access reveals which apps you open and when. A couple are normal " +
                        "(launchers, digital wellbeing); a crowd is worth reviewing. System " +
                        "apps are never listed here.",
                    severity = Severity.LOW,
                    count = usageApps.size,
                    samples = usageApps.map { it.label },
                    affectedApps = usageApps.map { FindingApp(it.label, it.pkg) },
                    fixHint = "Settings → Apps → Special access → Usage access.",
                )
            )
        }

        // v2.9: the OS itself needs overlay (system dialogs, volume panel, permission
        // prompts) — flagging Settings and System UI as MEDIUM risk is noise the user
        // cannot act on, and revoking those can break the phone. Only user apps count;
        // one or two chosen apps are LOW, a crowd is MEDIUM.
        val overlay = apps.filter {
            !it.isSystem && "android.permission.SYSTEM_ALERT_WINDOW" in it.grantedPermissions
        }
        if (overlay.isNotEmpty()) {
            findings.add(
                Finding(
                    id = "overlay",
                    title = if (overlay.size == 1) "1 app can draw over other apps"
                    else "${overlay.size} apps can draw over other apps",
                    detail = "The overlay permission is what fake login screens use. One or two " +
                        "apps you chose yourself (chat heads, screen recorders, floating " +
                        "timers) are usually fine; a crowd is worth reviewing. System apps " +
                        "such as Settings and System UI need this to show dialogs and " +
                        "alerts, so they are never listed here — and anything you switched " +
                        "off in Settings is excluded too.",
                    severity = if (overlay.size > 6) Severity.MEDIUM else Severity.LOW,
                    count = overlay.size,
                    samples = overlay.map { it.label },
                    affectedApps = overlay.map { FindingApp(it.label, it.pkg) },
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
                    title = if (sideloaded.size == 1) "1 app was not installed from an app store"
                    else "${sideloaded.size} apps were not installed from an app store",
                    detail = "An app installed from a file has not been reviewed by any store. That is " +
                        "normal for betas and mods — just make sure you know where each one came from.",
                    severity = Severity.MEDIUM,
                    count = sideloaded.size,
                    samples = sideloaded.map { it.label },
                    affectedApps = sideloaded.map { FindingApp(it.label, it.pkg) },
                    fixHint = "Open each one below and uninstall anything you do not recognise.",
                )
            )
        }

        // Granted permissions, not declared ones: an app the user already cut off from SMS
        // must disappear from this finding (v2.7 — \"revoked but still showing\").
        // v2.8: the default SMS app is supposed to read SMS — flagging it HIGH is a false
        // alarm, so it is excluded and named in the detail line instead.
        // Also distinguish banking/UPI applications that legitimately need SMS to verify
        // bank accounts and receive OTPs (tagged INFO/review rather than high-risk malware alarm).
        val defaultSmsPkg = try { android.provider.Telephony.Sms.getDefaultSmsPackage(context) } catch (e: Exception) {
            unavailable += setOf("sms_readers", "banking_sms_readers"); null
        }
        val allSmsApps = apps.filter {
            it.pkg != defaultSmsPkg &&
                it.grantedPermissions.any { p ->
                    p == "android.permission.READ_SMS" || p == "android.permission.RECEIVE_SMS"
                } && !it.isSystem
        }
        val (bankingSmsApps, unexpectedSmsApps) = allSmsApps.partition {
            isBankingOrPaymentApp(it.pkg, it.label)
        }

        if (unexpectedSmsApps.isNotEmpty()) {
            val defaultNote = if (defaultSmsPkg != null) " Your default SMS app is not listed." else ""
            findings.add(
                Finding(
                    id = "sms_readers",
                    title = if (unexpectedSmsApps.size == 1) "1 non-banking app can read your SMS"
                    else "${unexpectedSmsApps.size} non-banking apps can read your SMS",
                    detail = "SMS is where OTPs arrive. Only your messaging app and trusted banking apps should need this.$defaultNote",
                    severity = Severity.HIGH,
                    count = unexpectedSmsApps.size,
                    samples = unexpectedSmsApps.map { it.label },
                    affectedApps = unexpectedSmsApps.map { FindingApp(it.label, it.pkg) },
                    fixHint = "Revoke SMS permission for anything that is not your SMS app.",
                )
            )
        }

        if (bankingSmsApps.isNotEmpty()) {
            findings.add(
                Finding(
                    id = "banking_sms_readers",
                    title = if (bankingSmsApps.size == 1) "1 banking/payment app has SMS permission"
                    else "${bankingSmsApps.size} banking/payment apps have SMS permission",
                    detail = "Banking & UPI payment apps legitimately use SMS for SIM verification and OTPs. Kept under weekly reminder check.",
                    severity = Severity.INFO,
                    count = bankingSmsApps.size,
                    samples = bankingSmsApps.map { it.label },
                    affectedApps = bankingSmsApps.map { FindingApp(it.label, it.pkg) },
                    fixHint = "Verify that this is the official banking/payment app. Keep SMS only if needed for verification or OTPs; revoke if unused.",
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
                    affectedApps = stubs.map { FindingApp(it.label, it.pkg) },
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
                    title = if (suspiciousInstallers.size == 1) "1 app came from an unusual installer"
                    else "${suspiciousInstallers.size} apps came from an unusual installer",
                    detail = "The installer package is the app that put this one on your phone. " +
                        "Browser or file-manager installs show up here. The Play Store, " +
                        "F-Droid, Aurora and your phone maker's own store are never listed.",
                    severity = Severity.LOW,
                    count = suspiciousInstallers.size,
                    samples = suspiciousInstallers.map { "${it.label} ← ${it.installer}" },
                    affectedApps = suspiciousInstallers.map { FindingApp(it.label, it.pkg) },
                    fixHint = "Open the listed app’s information page and verify its installation source.",
                )
            )
        }

        // ------------------------------------------------------ device checks
        val locked = isScreenLocked(context)
        if (locked == null) unavailable += "no_lock"
        if (locked == false) {
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

        val developer = developerOptionsEnabled(context)
        if (developer == null) unavailable += "developer_options"
        if (developer == true) {
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
        if (patchAge == null) unavailable += "old_patch"
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

        // v2.9: word-boundary match — "Rootless Launcher" famously needs no root,
        // and the old substring check flagged it. The card now names the apps too.
        val rootTools = apps.filter { app -> !app.isSystem && ROOT_TOOL_NAME.containsMatchIn(app.label) }
        if (rootTools.isNotEmpty()) {
            findings.add(
                Finding(
                    id = "root_tools",
                    title = if (rootTools.size == 1) "1 root-related app installed"
                    else "${rootTools.size} root-related apps installed",
                    detail = "Root tools and \"game hackers\" ask for deep access. They only work on " +
                        "rooted phones — and they can read everything on one.",
                    severity = Severity.LOW,
                    count = rootTools.size,
                    samples = rootTools.map { it.label },
                    affectedApps = rootTools.map { FindingApp(it.label, it.pkg) },
                    fixHint = "Review the listed app. A root-related name alone does not prove that the phone is rooted.",
                )
            )
        }

        findings.add(
            Finding(
                id = "no_hash_lookup",
                title = "Malware hash check is on this screen",
                detail = "Use the “Malware hash check” card above to check installed app hashes " +
                    "against MalwareBazaar, and optionally VirusTotal with your key. Only hashes, " +
                    "never app files, are sent. Unchecked or unknown hashes do not prove an app is safe.",
                severity = Severity.INFO,
            )
        )

        val score = score(findings)
        val report = SecurityReport(
            findings = findings.sortedByDescending { it.severity.ordinal * -1 },
            score = score,
            appsChecked = apps.size,
            scannedAtMs = System.currentTimeMillis(),
            unavailableChecks = unavailable,
        )
        runCatching { SecurityHistoryRepo.record(context, report, apps) }
        report
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

    private fun enabledServices(context: Context, key: String): List<String>? = try {
        val raw = Settings.Secure.getString(context.contentResolver, key) ?: ""
        raw.split(':').map { it.trim() }.filter { it.isNotEmpty() }
    } catch (e: Exception) {
        null
    }

    private fun activeAdmins(context: Context): List<ComponentName>? = try {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        @Suppress("DEPRECATION")
        if (dpm == null) null else dpm.activeAdmins?.distinct().orEmpty()
    } catch (e: Exception) {
        null
    }

    private fun serviceApp(context: Context, rawComponent: String): FindingApp? {
        val component = ComponentName.unflattenFromString(rawComponent) ?: return null
        return FindingApp(
            label = labelFor(context, component.packageName) ?: component.packageName,
            packageName = component.packageName,
            componentName = component.flattenToString(),
        )
    }

    /** The package the user chose as their messaging app (Settings → Apps → Default apps). */
    private fun defaultSmsPackage(context: Context): String? = try {
        android.provider.Telephony.Sms.getDefaultSmsPackage(context)
    } catch (e: Exception) {
        null
    }

    /** Display label for a package, or null when it cannot be resolved. */
    private fun labelFor(context: Context, pkg: String): String? = try {
        val pm = context.packageManager
        pm.getApplicationInfo(pkg, 0).loadLabel(pm)?.toString()
    } catch (e: Exception) {
        null
    }

    private fun isScreenLocked(context: Context): Boolean? = try {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE)
            as? android.app.KeyguardManager
        km?.isDeviceSecure
    } catch (e: Exception) {
        null
    }

    private fun developerOptionsEnabled(context: Context): Boolean? = try {
        Settings.Global.getInt(
            context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0
        ) == 1
    } catch (e: Exception) {
        null
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
