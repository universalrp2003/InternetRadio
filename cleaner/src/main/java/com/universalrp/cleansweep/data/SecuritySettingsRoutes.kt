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
                if (pkg != null) {
                    result += Destination(SMS_PERMISSIONS, extras = mapOf(EXTRA_PACKAGE to pkg))
                    if (isXiaomi(manufacturer, brand)) {
                        // MIUI/HyperOS varies by release. These activities may be missing
                        // or non-exported: the launcher catches that and tries App info.
                        for (name in listOf("PermissionsEditorActivity", "AppPermissionsEditorActivity")) {
                            result += Destination(
                                "miui.intent.action.APP_PERM_EDITOR",
                                activityPackage = "com.miui.securitycenter",
                                activityClass = "com.miui.permcenter.permissions.$name",
                                extras = mapOf("extra_pkgname" to pkg),
                            )
                        }
                    }
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
            "sms_readers", "banking_sms_readers" -> "App info / App permissions → $selected → Permissions → SMS. Revoke only if unnecessary; verified messaging, banking and UPI apps may need it. On MIUI/HyperOS choose Permission management / App permissions."
            "sideloaded", "odd_installer", "root_tools" -> "App info → $selected. Verify its source and purpose; uninstall only if you recognise it as unwanted."
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
                "MIUI / HyperOS: Settings → Apps → Manage apps → select the affected app → App permissions / Other permissions. Menu names vary by ROM."
            manufacturer.contains("samsung", ignoreCase = true) ->
                "One UI: Settings → Security and privacy → Permission manager, or Apps → select the affected app → Permissions."
            else -> "Settings → Apps → select the affected app → Permissions / Special app access. Menu names vary by manufacturer."
        }
        return header + path + "\nReview each finding before changing a permission. A 100/100 score is not proof that the phone is malware-free, and trusted services may legitimately keep powerful access."
    }
}
