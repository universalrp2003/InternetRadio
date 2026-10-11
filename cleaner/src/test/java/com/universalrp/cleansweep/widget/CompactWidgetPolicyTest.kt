package com.universalrp.cleansweep.widget

import org.junit.Assert.*
import org.junit.Test

/** Source-only prepared cases; must run in both authorized build variants before publication. */
class CompactWidgetPolicyTest {
    @Test fun batteryDefault() { assertEquals(CompactWidgetPolicy.Profile.BATTERY, CompactWidgetPolicy.defaultFor("com.example.CleanSweepWidget")) }
    @Test fun dataDefault() { assertEquals(CompactWidgetPolicy.Profile.DATA, CompactWidgetPolicy.defaultFor("com.example.CleanSweepDataWidget")) }
    @Test fun phoneDefault() { assertEquals(CompactWidgetPolicy.Profile.PHONE, CompactWidgetPolicy.defaultFor("com.example.CleanSweepPhoneWidget")) }
    @Test fun unknownDefaultSafe() { assertEquals(CompactWidgetPolicy.Profile.BATTERY, CompactWidgetPolicy.defaultFor("unknown")) }
    @Test fun threeViewCycle() { assertEquals(CompactWidgetPolicy.Profile.BATTERY, CompactWidgetPolicy.Profile.BATTERY.next().next().next()) }
    @Test fun invalidSavedValueUsesChosenDefault() { assertEquals(CompactWidgetPolicy.Profile.DATA, CompactWidgetPolicy.parse("old-watts", CompactWidgetPolicy.Profile.DATA)) }
    @Test fun wifiAndUnlimitedUsePhoneDetails() { assertFalse(CompactWidgetPolicy.meteredData(true, false)); assertFalse(CompactWidgetPolicy.meteredData(false, true)); assertTrue(CompactWidgetPolicy.meteredData(false, false)) }
    @Test fun percentIsNotInvented() { assertEquals("77%", CompactWidgetPolicy.wholePercent(77)); assertEquals("— %", CompactWidgetPolicy.wholePercent(null)); assertEquals("— %", CompactWidgetPolicy.wholePercent(101)) }
}
