package com.universalrp.cleansweep.data

/**
 * Testable routing policy. Public Settings actions first; guarded vendor/activity fallbacks
 * only where Android has no public per-app action. Never fall back to the Settings homepage.
 */
object SecuritySettingsRoutes {
    const val APP_INFO = "android.settings.APPLICATION_DETAILS_SETTINGS"
    const val ACCESSIBILITY = "android.settings.ACCESSIBILITY_SETTINGS"
    const val NOTIFICATION_ACCESS = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
    const val NOTIFICATION_DETAIL = "android.settings.NOTIFICATION_LISTENER_DETAIL_SETTINGS"
    const val UNKNOWN_SOURCES = "android.settings.MANAGE_UNKNOWN_APP_SOURCES"
    const val USAGE_ACCESS = "android.settings.USAGE_ACCESS_SETTINGS"
    const val OVERLAY = "android.settings.action.MANAGE_OVERLAY_PERMISSION"
    const val SECURITY = "android.settings.SECURITY_SETTINGS"
    const val DEVELOPER = "android.settings.APPLICATION_DEVELOPMENT_SETTINGS"
    const val SYSTEM_UPDATE = "android.settings.SYSTEM_UPDATE_SETTINGS"
    const val DEVICE_INFO = "android.settings.DEVICE_INFO_SETTINGS"
    const val SCREEN_LOCK = "android.app.action.SET_NEW_PASSWORD"
    const val SMS_PERMISSIONS = "android.intent.action.MANAGE_APP_PERMISSIONS"

    /**
     * 2.42: the AOSP editor for **one** permission of **one** app, rather than the app's whole
     * permission list. Candidate only — a ROM that does not expose it fails to resolve and the
     * launcher tries the next route, which is the normal documented fallback here.
     */
    const val PERMISSION_EDITOR = "android.intent.action.MANAGE_APP_PERMISSION"
    const val EXTRA_PERMISSION = "android.intent.extra.PERMISSION_NAME"

    /**
     * Routing targets only: these name *another* app's permission page that the owner is asked
     * to review. Live Guard never declares, requests or holds an SMS permission itself, and
     * `tools/verify_cleaner_core_manifest.py` fails the build if the manifest ever does.
     */
    const val PERMISSION_SMS_GROUP = "android.permission-group.SMS"
    const val PERMISSION_READ_SMS = "android.permission.READ_SMS"

    const val EXTRA_PACKAGE = "android.intent.extra.PACKAGE_NAME"
    const val EXTRA_LISTENER = "android.provider.extra.NOTIFICATION_LISTENER_COMPONENT_NAME"
    const val EXTRA_HIGHLIGHT = ":settings:fragment_args_key"

    data class Destination(
        val action: String,
        val dataPackage: String? = null,
        val activityPackage: String? = null,
        val activityClass: String? = null,
        val extras: Map<String, String> = emptyMap(),
        /** False means a category/app page with another manual step, not the exact toggle. */
        val direct: Boolean = false,
    )

    fun isXiaomi(manufacturer: String, brand: String = ""): Boolean =
        listOf(manufacturer, brand).any { value ->
            listOf("xiaomi", "redmi", "poco").any { value.contains(it, ignoreCase = true) }
        }

    /**
     * ColorOS and its derivatives. 2.42: recognised so the owner's OPPO A3s gets its own
     * wording and route order. No ColorOS component name is invented — ColorOS keeps the AOSP
     * permission editor reachable, so its route *is* the AOSP one, and a made-up activity
     * would only waste a fallback step before App info.
     */
    fun isColoros(manufacturer: String, brand: String = ""): Boolean =
        listOf(manufacturer, brand).any { value ->
            listOf("oppo", "realme", "oneplus", "coloros").any { value.contains(it, ignoreCase = true) }
        }

    /**
     * Routes that open **this app's own** permission page, most specific first.
     *
     * The 2.42 fix: the SMS finding used to fire the generic all-permissions action first,
     * carrying the package only as the `android.intent.extra.PACKAGE_NAME` extra. On
     * MIUI/HyperOS that action is claimed by the security centre, which ignores the extra and
     * shows its own "Other permissions" hub — a different window from the one promised, which
     * is exactly what the owner's iMobile screenshot showed. So now:
     *
     *  1. on Xiaomi, the ROM's own *package-scoped* permission editor comes first;
     *  2. then the AOSP editor for the exact permission, where the ROM exposes it;
     *  3. then the AOSP per-app permission list (this is also the ColorOS/One UI route);
     *  4. then App info, which every ROM has and whose "Permissions" row is that same page.
     *
     * Every candidate carries the package **both** as the `package:` data URI and as the extra,
     * because ROMs differ in which one they read; sending only the extra is what let a ROM show
     * an unscoped hub. A candidate the ROM does not expose simply fails to resolve and the
     * launcher tries the next one, ending in the honest manual-path text.
     */
    fun appPermissionRoutes(
        pkg: String,
        permissionNames: List<String>,
        manufacturer: String = "",
        brand: String = "",
    ): List<Destination> {
        val result = mutableListOf<Destination>()
        if (isXiaomi(manufacturer, brand)) {
            // MIUI/HyperOS varies by release. These activities may be missing or non-exported:
            // the launcher catches that and tries the next candidate.
            for (name in listOf("PermissionsEditorActivity", "AppPermissionsEditorActivity")) {
                result += Destination(
                    "miui.intent.action.APP_PERM_EDITOR",
                    dataPackage = pkg,
                    activityPackage = "com.miui.securitycenter",
                    activityClass = "com.miui.permcenter.permissions.$name",
                    extras = mapOf("extra_pkgname" to pkg),
                )
            }
        }
        for (permission in permissionNames) {
            result += Destination(
                PERMISSION_EDITOR,
                dataPackage = pkg,
                extras = mapOf(EXTRA_PACKAGE to pkg, EXTRA_PERMISSION to permission),
                direct = true,
            )
        }
        result += Destination(SMS_PERMISSIONS, dataPackage = pkg, extras = mapOf(EXTRA_PACKAGE to pkg))
        result += Destination(APP_INFO, dataPackage = pkg)
        return result
    }

    fun destinations(
        findingId: String,
        app: FindingApp? = null,
        sdk: Int,
        manufacturer: String = "",
        brand: String = "",
    ): List<Destination> {
        val pkg = app?.packageName?.takeIf { it.isNotBlank() }
        val highlight = (app?.componentName ?: pkg)?.let { mapOf(EXTRA_HIGHLIGHT to it) }.orEmpty()
        val result = mutableListOf<Destination>()
        fun category(action: String) = Destination(action, extras = highlight)
        when (findingId) {
            "accessibility" -> {
                // ACCESSIBILITY_DETAILS_SETTINGS is signature-permission protected, NOT a
                // third-party deep link. Open the correct category and highlight the service.
                result += category(ACCESSIBILITY)
            }
            "notification_listeners" -> {
                if (sdk >= 30 && app?.componentName != null) {
                    result += Destination(
                        NOTIFICATION_DETAIL,
                        extras = mapOf(EXTRA_LISTENER to app.componentName),
                        direct = true,
                    )
                }
                result += category(NOTIFICATION_ACCESS)
            }
            "install_other_apps" -> {
                if (pkg != null) result += Destination(UNKNOWN_SOURCES, dataPackage = pkg, direct = true)
                result += category(UNKNOWN_SOURCES)
            }
            "usage_access" -> {
                if (pkg != null) result += Destination(USAGE_ACCESS, dataPackage = pkg, direct = true)
                result += category(USAGE_ACCESS)
            }
            "overlay" -> {
                // Since Android 11 this public action ignores package: URIs. Do not claim
                // to open a per-app toggle that Android reserves for privileged apps.
                if (pkg != null && sdk < 30) result += Destination(OVERLAY, dataPackage = pkg, direct = true)
                result += category(OVERLAY)
            }
            "device_admin" -> {
                result += Destination(
                    "android.intent.action.MAIN",
                    activityPackage = "com.android.settings",
                    activityClass = "com.android.settings.Settings\$DeviceAdminSettingsActivity",
                    extras = highlight,
                )
                result += Destination(
                    "android.intent.action.MAIN",
                    activityPackage = "com.android.settings",
                    activityClass = "com.android.settings.DeviceAdminSettings",
                    extras = highlight,
                )
                result += category(SECURITY)
            }
            "sms_readers", "banking_sms_readers" -> {
                // 2.42: open this app's own permission page — and the SMS permission itself
                // where the ROM exposes an editor for it — instead of an unscoped hub. See
                // [appPermissionRoutes] for why the order changed and what each ROM does.
                if (pkg != null) {
                    result += appPermissionRoutes(
                        pkg,
                        listOf(PERMISSION_READ_SMS, PERMISSION_SMS_GROUP),
                        manufacturer,
                        brand,
                    )
                }
            }
            "no_lock" -> {
                result += Destination(SCREEN_LOCK, direct = true)
                result += category(SECURITY)
            }
            "developer_options" -> result += Destination(DEVELOPER, direct = true)
            "old_patch" -> {
                result += Destination(SYSTEM_UPDATE, direct = true)
                if (isXiaomi(manufacturer, brand)) {
                    result += Destination(
                        "android.intent.action.MAIN",
                        activityPackage = "com.android.updater",
                        activityClass = "com.android.updater.MainActivity",
                        direct = true,
                    )
                }
                result += category(DEVICE_INFO)
            }
            // These findings concern the APP, not a global permission. App info lets
            // the owner inspect permissions, uninstall or disable it manually.
            "sideloaded", "facebook_stubs", "odd_installer", "root_tools" -> Unit
            else -> Unit
        }
        if (pkg != null) result += Destination(APP_INFO, dataPackage = pkg)
        return result.distinct()
    }

    fun instructions(findingId: String, app: FindingApp? = null): String {
        val selected = app?.let { "${it.label} (${it.packageName})" } ?: "the app listed in this finding"
        return when (findingId) {
            "accessibility" -> "Accessibility → Downloaded/installed services → $selected. Review the named service; keep accessibility tools you intentionally use."
            "notification_listeners" -> "Notification access → $selected → review notification access."
            "device_admin" -> "Security / Security and privacy → More security settings → Device admin apps → $selected. Do not disable a trusted work administrator or device-finding service blindly."
            "install_other_apps" -> "Install unknown apps → $selected → Allow from this source. Turn it off only if you do not need that installer."
            "usage_access" -> "Usage access → $selected → Permit usage access. Keep access for tools you intentionally use."
            "overlay" -> "Display over other apps → $selected. Android 11+ may open the app list; select this app. On MIUI/HyperOS it may be under Apps → Manage apps → app → Other permissions."
            "sms_readers", "banking_sms_readers" -> "This app's own permission page → $selected → Permissions → SMS. The button opens that app's page, not a list of other apps and not the ROM's global permission hub; if your ROM opens something else, use this manual path. Revoke only if unnecessary; verified messaging, banking and UPI apps may need it. On MIUI/HyperOS choose Apps → Manage apps → the app → App permissions. On ColorOS choose Settings → Apps → App management → the app → Permissions."
            "app_info", "sideloaded", "odd_installer", "root_tools" -> "App info → $selected. Verify its source and purpose; uninstall only if you recognise it as unwanted."
            "facebook_stubs" -> "App info → $selected → Disable, if available and you do not need it. Do not remove unrelated system components."
            "no_lock" -> "Security / Passwords and security → Screen lock → set a PIN, pattern or password."
            "developer_options" -> "System / Additional settings → Developer options. Turn off debugging/options only if you do not use them."
            "old_patch" -> "About phone → System update / MIUI or HyperOS version. Check for an official update; this app cannot know whether your manufacturer offers one."
            else -> "This finding has no automatic settings action. Review its explanation and rescan after any manual change."
        }
    }

    fun deviceGuidance(manufacturer: String, brand: String, model: String, androidVersion: String): String {
        val header = "Phone: $manufacturer $model (Android $androidVersion)\n"
        val path = when {
            isXiaomi(manufacturer, brand) ->
                "MIUI / HyperOS: Settings → Apps → Manage apps → select the affected app → App permissions. 'Other permissions' is a different, ROM-specific page: it lists MIUI's own extras such as autostart and pop-up windows, not the app's Android permissions. Menu names vary by ROM."
            isColoros(manufacturer, brand) ->
                "ColorOS / Realme UI / OxygenOS: Settings → Apps → App management → select the affected app → Permissions (or App permissions). Older ColorOS calls it Settings → Security → App permission management. Menu names vary by ROM."
            manufacturer.contains("samsung", ignoreCase = true) ->
                "One UI: Settings → Security and privacy → Permission manager, or Apps → select the affected app → Permissions."
            else -> "Settings → Apps → select the affected app → Permissions / Special app access. Menu names vary by manufacturer."
        }
        return header + path + "\nReview each finding before changing a permission. A 100/100 score is not proof that the phone is malware-free, and trusted services may legitimately keep powerful access."
    }
}
