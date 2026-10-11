package com.universalrp.cleansweep.data

import org.junit.Assert.*
import org.junit.Test

/** Source-only prepared cases; must run in both authorized build variants before publication. */
class FeatureSetupPolicyTest {
    @Test fun configuredStatus() { assertEquals(FeatureSetupPolicy.Status.READY, FeatureSetupPolicy.status(true)) }
    @Test fun offChoiceWinsOverMissingPermissions() { assertEquals(FeatureSetupPolicy.Status.OFF_BY_CHOICE, FeatureSetupPolicy.status(false, false, false)) }
    @Test fun accessRequired() { assertEquals(FeatureSetupPolicy.Status.NEEDS_ACCESS, FeatureSetupPolicy.status(true, access = false)) }
    @Test fun configurationRequired() { assertEquals(FeatureSetupPolicy.Status.NOT_CONFIGURED, FeatureSetupPolicy.status(true, configured = false)) }
    @Test fun onlyPendingConfigurationsCount() { val rows = FeatureSetupPolicy.Status.entries.map { FeatureSetupPolicy.Feature(it.name, it.name, it, "") }; assertEquals(2, FeatureSetupPolicy.pending(rows).size) }
    @Test fun noStealthNotification() { assertFalse(FeatureSetupPolicy.remind(false, true, 2, 0, 1000)) }
    @Test fun permissionAndPendingNeeded() { assertFalse(FeatureSetupPolicy.remind(true, false, 2, 0, 1000)); assertFalse(FeatureSetupPolicy.remind(true, true, 0, 0, 1000)) }
    @Test fun oncePerDayAndClockSafe() { assertFalse(FeatureSetupPolicy.remind(true, true, 1, 1000, 999)); assertFalse(FeatureSetupPolicy.remind(true, true, 1, 1000, 1100)); assertTrue(FeatureSetupPolicy.remind(true, true, 1, 1000, 1000 + 86400000)) }
}
