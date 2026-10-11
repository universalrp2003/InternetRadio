package com.universalrp.cleansweep.data

import org.junit.Assert.*
import org.junit.Test

class PermissionObservationPolicyTest {
    private val sms = "android.permission.READ_SMS"
    private val usage = "android.permission.PACKAGE_USAGE_STATS"
    private val overlay = "android.permission.SYSTEM_ALERT_WINDOW"
    @Test fun missingManifestFlagsNeverGrantOrRevokeEveryDeclaredPermission() {
        val result = PermissionObservationPolicy.observe(listOf(sms), null, emptyMap())
        assertTrue(result.granted.isEmpty()); assertTrue(result.notGranted.isEmpty()); assertTrue("sms_readers" in result.unavailableChecks)
    }
    @Test fun actualRuntimeGrantFlagsDetermineObservedSmsAccess() {
        assertEquals(listOf(sms), PermissionObservationPolicy.observe(listOf(sms), setOf(sms), emptyMap()).granted)
        assertEquals(listOf(sms), PermissionObservationPolicy.observe(listOf(sms), emptySet(), emptyMap()).notGranted)
    }
    @Test fun specialAccessCanBeGrantedIndependentlyOfManifestGrantBit() {
        val result = PermissionObservationPolicy.observe(listOf(usage), emptySet(), mapOf(usage to true))
        assertEquals(listOf(usage), result.granted); assertTrue(result.notGranted.isEmpty())
    }
    @Test fun specialAccessCanBeOffDespiteManifestGrantBit() {
        val result = PermissionObservationPolicy.observe(listOf(overlay), setOf(overlay), mapOf(overlay to false))
        assertTrue(result.granted.isEmpty()); assertEquals(listOf(overlay), result.notGranted)
    }
    @Test fun appOpReadFailureIsUnknownNotARevocation() {
        val result = PermissionObservationPolicy.observe(listOf(overlay), setOf(overlay), mapOf(overlay to null))
        assertTrue(result.granted.isEmpty()); assertTrue(result.notGranted.isEmpty()); assertTrue("overlay" in result.unavailableChecks)
    }
    @Test fun unrelatedSpecialFailureDoesNotDestroyKnownSmsObservation() {
        val result = PermissionObservationPolicy.observe(listOf(sms, overlay), setOf(sms), mapOf(overlay to null))
        assertEquals(listOf(sms), result.granted); assertEquals(setOf("overlay"), result.unavailableChecks)
    }
}
