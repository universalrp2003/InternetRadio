package com.universalrp.cleansweep.notify

import org.junit.Assert.*
import org.junit.Test

class StatusPillContentTest {
    @Test fun chargingOverridesEveryNetworkAndPackField() {
        val view = StatusPillContent.resolve(
            powerConnected = true, powerW = 8.4f, mobileData = "654 MB",
            unlimited5g = true, batteryPercent = 84, packPercent = 98,
            hideDataOnWifiOrUnlimited = true,
        )
        assertEquals("8.4 W", view.text)
        assertFalse(view.showNetworkMeter)
        assertNull(view.packPercent)
        assertFalse(view.text.contains("5G"))
        assertFalse(view.text.contains("MB"))
        assertFalse(view.text.contains("%"))
    }

    @Test fun unavailableOrInvalidPowerDoesNotFallBackToAmpsDataOrBattery() {
        for (watts in listOf(null, Float.NaN, Float.POSITIVE_INFINITY, -3f)) {
            val view = StatusPillContent.resolve(true, watts, "200 MB", batteryPercent = 100)
            assertEquals("— W", view.text)
            assertFalse(view.showNetworkMeter)
            assertNull(view.packPercent)
        }
        assertEquals("0.0 W", StatusPillContent.resolve(true, 0f).text)
    }

    @Test fun unplugRestoresTheNormalViewWithoutChangingPreferences() {
        fun normal() = StatusPillContent.resolve(false, null, "700 MB", packPercent = 92)
        val before = normal()
        val during = StatusPillContent.resolve(true, 6f, "700 MB", packPercent = 92)
        val after = normal()
        assertEquals(before, after)
        assertEquals("700 MB", after.text)
        assertTrue(after.showNetworkMeter)
        assertEquals(92, after.packPercent)
        assertFalse(during.showNetworkMeter)
        assertNull(during.packPercent)
    }

    @Test fun wifiHidePreferenceSurvivesCharging() {
        val before = StatusPillContent.resolve(false, null, "700 MB", hideDataOnWifiOrUnlimited = true)
        val charging = StatusPillContent.resolve(true, 7.1f, "700 MB", hideDataOnWifiOrUnlimited = true)
        val after = StatusPillContent.resolve(false, null, "700 MB", hideDataOnWifiOrUnlimited = true)
        assertEquals("", before.text)
        assertTrue(before.showNetworkMeter)
        assertEquals("7.1 W", charging.text)
        assertEquals(before, after)
    }

    @Test fun unlimitedPlanReturnsAfterUnplugWithNoQuotaAlert() {
        val view = StatusPillContent.resolve(false, null, "123 MB", unlimited5g = true, packPercent = 100)
        assertEquals("5G Unlimited", view.text)
        assertTrue(view.showNetworkMeter)
        assertNull(view.packPercent)
    }

    @Test fun normalFallbackAndQuotaClampingArePreserved() {
        assertEquals("65%", StatusPillContent.resolve(false, null, batteryPercent = 65).text)
        assertEquals("—", StatusPillContent.resolve(false, null).text)
        assertEquals(100, StatusPillContent.resolve(false, null, packPercent = 150).packPercent)
        assertEquals(0, StatusPillContent.resolve(false, null, packPercent = -5).packPercent)
    }
}
