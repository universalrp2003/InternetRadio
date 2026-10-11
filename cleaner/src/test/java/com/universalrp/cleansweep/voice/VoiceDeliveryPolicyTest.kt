package com.universalrp.cleansweep.voice

import org.junit.Assert.*
import org.junit.Test

class VoiceDeliveryPolicyTest {
    @Test fun masterOffBlocksEvenExplicitManualTests() {
        assertEquals(VoiceDeliveryPolicy.Stage.DISABLED, VoiceDeliveryPolicy.blocked(false, true, false, true))
    }
    @Test fun disabledEventIsNotReportedAsSpeechSuccess() {
        assertEquals(VoiceDeliveryPolicy.Stage.EVENT_OFF, VoiceDeliveryPolicy.blocked(true, false, false, false))
    }
    @Test fun quietHoursBlockAutomaticSpeechDespiteAnEnabledEvent() {
        assertEquals(VoiceDeliveryPolicy.Stage.QUIET_HOURS, VoiceDeliveryPolicy.blocked(true, true, true, false))
    }
    @Test fun explicitManualSpeechBypassesQuietHoursButNotMaster() {
        assertNull(VoiceDeliveryPolicy.blocked(true, true, true, true))
        assertEquals(VoiceDeliveryPolicy.Stage.DISABLED, VoiceDeliveryPolicy.blocked(false, true, true, true))
    }
    @Test fun allowedDaytimeRequestIsNotBlocked() {
        assertNull(VoiceDeliveryPolicy.blocked(true, true, false, false))
    }
    @Test fun negativeEngineStatusesAreNotAvailableLanguages() {
        for (code in listOf(-1, -2, -3)) assertFalse(VoiceDeliveryPolicy.validStatus(code))
        for (code in 0..2) assertTrue(VoiceDeliveryPolicy.validStatus(code))
    }
    @Test fun wifiMobileTransitionsAreObservedInEitherDirection() {
        assertTrue(VoiceDeliveryPolicy.changedTransport("wifi", "mobile")); assertTrue(VoiceDeliveryPolicy.changedTransport("mobile", "wifi"))
    }
    @Test fun initialUnknownOfflineAndSameTransportDoNotInventASwitch() {
        for (pair in listOf(null to "wifi", "none" to "mobile", "wifi" to "none", "wifi" to "wifi"))
            assertFalse(VoiceDeliveryPolicy.changedTransport(pair.first, pair.second))
    }
    @Test fun cooldownIsReadyAtItsExactBoundaryAndForNoPreviousPlayback() {
        assertTrue(VoiceDeliveryPolicy.cooldownReady(1000, 0, 600)); assertTrue(VoiceDeliveryPolicy.cooldownReady(1000, 400, 600))
        assertFalse(VoiceDeliveryPolicy.cooldownReady(999, 400, 600))
    }
    @Test fun clockRollbackDoesNotSilenceAnEventForWeeks() {
        assertTrue(VoiceDeliveryPolicy.cooldownReady(100, 10000, 1000))
    }
    @Test fun networkFallbackIsBoundedAndRequiresAnInstalledOfflineVoice() {
        assertTrue(VoiceDeliveryPolicy.retryOffline(true, false, true))
        assertFalse(VoiceDeliveryPolicy.retryOffline(true, true, true)); assertFalse(VoiceDeliveryPolicy.retryOffline(true, false, false))
        assertFalse(VoiceDeliveryPolicy.retryOffline(false, false, true))
    }
    @Test fun diagnosticDefaultsDoNotClaimPlaybackOrIncludeUtteranceText() {
        val snapshot = VoiceDeliveryStatus()
        val observers = VoiceObserverStatus()
        assertEquals(0, observers.owners); assertNull(observers.power); assertNull(observers.network); assertNull(observers.battery)
        assertEquals(VoiceDeliveryPolicy.Stage.NOT_REQUESTED, snapshot.stage); assertNull(snapshot.tamilAvailable)
        assertNull(snapshot.mediaVolume); assertEquals("", snapshot.event); assertEquals(0L, snapshot.atMs)
    }
}
