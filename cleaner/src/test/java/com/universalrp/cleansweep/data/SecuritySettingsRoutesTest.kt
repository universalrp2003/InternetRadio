package com.universalrp.cleansweep.data

import org.junit.Assert.*
import org.junit.Test

class SecuritySettingsRoutesTest {
    private val first = FindingApp("Same visible name", "com.example.first", "com.example.first/.Listener")
    private val second = FindingApp("Same visible name", "com.example.second", "com.example.second/.Listener")

    @Test fun everyActionableScannerIdHasATargetedRoute() {
        val ids = listOf(
            "accessibility", "notification_listeners", "device_admin", "install_other_apps",
            "usage_access", "overlay", "sideloaded", "sms_readers", "banking_sms_readers",
            "facebook_stubs", "odd_installer", "no_lock", "developer_options", "old_patch", "root_tools",
        )
        for (sdk in listOf(26, 29, 30, 33, 36)) {
            for (id in ids) {
                val routes = SecuritySettingsRoutes.destinations(id, first, sdk)
                assertTrue("$id on API $sdk", routes.isNotEmpty())
                assertTrue(routes.none { it.action == "android.settings.SETTINGS" })
                assertFalse(SecuritySettingsRoutes.instructions(id).contains("no automatic settings action"))
            }
        }
    }

    @Test fun duplicateLabelsCannotSendManageToTheWrongPackage() {
        val route = SecuritySettingsRoutes.destinations("install_other_apps", second, 36).first()
        assertEquals(SecuritySettingsRoutes.UNKNOWN_SOURCES, route.action)
        assertEquals(second.packageName, route.dataPackage)
        assertNotEquals(first.packageName, route.dataPackage)
        assertTrue(route.direct)
    }

    @Test fun notificationDetailReceivesTheExactServiceComponent() {
        val route = SecuritySettingsRoutes.destinations("notification_listeners", second, 30).first()
        assertEquals(SecuritySettingsRoutes.NOTIFICATION_DETAIL, route.action)
        assertEquals(second.componentName, route.extras[SecuritySettingsRoutes.EXTRA_LISTENER])
        assertTrue(route.direct)
        val legacy = SecuritySettingsRoutes.destinations("notification_listeners", second, 26).first()
        assertEquals(SecuritySettingsRoutes.NOTIFICATION_ACCESS, legacy.action)
        assertFalse(legacy.direct)
    }

    @Test fun protectedAccessibilityDetailIsNotPretendedToBePublic() {
        val routes = SecuritySettingsRoutes.destinations("accessibility", second, 36)
        assertEquals(SecuritySettingsRoutes.ACCESSIBILITY, routes.first().action)
        assertEquals(second.componentName, routes.first().extras[SecuritySettingsRoutes.EXTRA_HIGHLIGHT])
        assertFalse(routes.first().direct)
        assertTrue(routes.none { it.action.contains("ACCESSIBILITY_DETAILS") })
    }

    @Test fun overlayHonoursAndroidElevenPerAppRestriction() {
        val old = SecuritySettingsRoutes.destinations("overlay", first, 29).first()
        assertEquals(first.packageName, old.dataPackage)
        assertTrue(old.direct)
        val current = SecuritySettingsRoutes.destinations("overlay", first, 30).first()
        assertEquals(SecuritySettingsRoutes.OVERLAY, current.action)
        assertNull(current.dataPackage)
        assertFalse(current.direct)
        assertTrue(SecuritySettingsRoutes.instructions("overlay", first).contains("select this app", ignoreCase = true))
    }

    @Test fun usageAccessCarriesThePackageUri() {
        val route = SecuritySettingsRoutes.destinations("usage_access", second, 26).first()
        assertEquals(SecuritySettingsRoutes.USAGE_ACCESS, route.action)
        assertEquals(second.packageName, route.dataPackage)
    }

    @Test fun smsHasPerAppPermissionAndXiaomiFallbacks() {
        val routes = SecuritySettingsRoutes.destinations("sms_readers", second, 36, "Xiaomi", "POCO")
        assertEquals(SecuritySettingsRoutes.SMS_PERMISSIONS, routes.first().action)
        assertEquals(second.packageName, routes.first().extras[SecuritySettingsRoutes.EXTRA_PACKAGE])
        val miui = routes.first { it.activityPackage == "com.miui.securitycenter" }
        assertEquals(second.packageName, miui.extras["extra_pkgname"])
        assertEquals(SecuritySettingsRoutes.APP_INFO, routes.last().action)
        assertEquals(second.packageName, routes.last().dataPackage)
        assertTrue(SecuritySettingsRoutes.destinations("banking_sms_readers", second, 26, "samsung")
            .none { it.activityPackage == "com.miui.securitycenter" })
    }

    @Test fun appReviewFindingsOpenSelectedAppInfoNotGlobalSettings() {
        for (id in listOf("sideloaded", "facebook_stubs", "odd_installer", "root_tools")) {
            val route = SecuritySettingsRoutes.destinations(id, second, 36).single()
            assertEquals(SecuritySettingsRoutes.APP_INFO, route.action)
            assertEquals(second.packageName, route.dataPackage)
            assertTrue(SecuritySettingsRoutes.destinations(id, null, 36).isEmpty())
        }
    }

    @Test fun deviceAdminOpensTheAdminCategoryWithoutEnablingAnAdmin() {
        val routes = SecuritySettingsRoutes.destinations("device_admin", first, 26)
        assertTrue(routes.first().activityClass!!.contains("DeviceAdminSettings"))
        assertEquals("com.android.settings", routes.first().activityPackage)
        assertTrue(routes.none { it.action == "android.app.action.ADD_DEVICE_ADMIN" })
        assertTrue(routes.none { it.action == "android.settings.SETTINGS" })
    }

    @Test fun lockAndFirmwareHaveDedicatedDestinations() {
        assertEquals(SecuritySettingsRoutes.SCREEN_LOCK,
            SecuritySettingsRoutes.destinations("no_lock", sdk = 26).first().action)
        val update = SecuritySettingsRoutes.destinations("old_patch", sdk = 36, manufacturer = "Xiaomi")
        assertEquals(SecuritySettingsRoutes.SYSTEM_UPDATE, update.first().action)
        assertTrue(update.any { it.activityPackage == "com.android.updater" })
        assertEquals(SecuritySettingsRoutes.DEVICE_INFO, update.last().action)
    }

    @Test fun unknownAndInformationalFindingsDoNotLaunchTheSettingsHomepage() {
        assertTrue(SecuritySettingsRoutes.destinations("no_hash_lookup", sdk = 36).isEmpty())
        assertTrue(SecuritySettingsRoutes.destinations("future_unknown_finding", sdk = 36).isEmpty())
        assertEquals(SecuritySettingsRoutes.APP_INFO,
            SecuritySettingsRoutes.destinations("future_unknown_finding", second, 36).single().action)
    }

    @Test fun deviceGuideIsCautiousAndRecognisesRedmiAndPoco() {
        assertTrue(SecuritySettingsRoutes.isXiaomi("unknown", "Redmi"))
        assertTrue(SecuritySettingsRoutes.isXiaomi("unknown", "POCO"))
        val guide = SecuritySettingsRoutes.deviceGuidance("Xiaomi", "Redmi", "Example", "15")
        assertTrue(guide.contains("MIUI / HyperOS"))
        assertTrue(guide.contains("not proof"))
        assertFalse(guide.contains("achieve 100/100"))
    }
}
