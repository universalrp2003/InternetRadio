package com.oorarivippu.voiceannouncer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineReminderPolicyTest {
    @Test
    fun onlySupportedIntervalsAreStored() {
        assertEquals(OfflineReminderPolicy.DEFAULT_INTERVAL_MINUTES, OfflineReminderPolicy.normalizeInterval(-1))
        assertEquals(OfflineReminderPolicy.DEFAULT_INTERVAL_MINUTES, OfflineReminderPolicy.normalizeInterval(7))
        OfflineReminderPolicy.supportedIntervalsMinutes.forEach { interval ->
            assertEquals(interval, OfflineReminderPolicy.normalizeInterval(interval))
        }
    }

    @Test
    fun reminderRunsOnlyWhileTrackingWithKnownOfflineStateAndEnabledInterval() {
        assertTrue(OfflineReminderPolicy.shouldSchedule(true, false, 15))
        assertFalse(OfflineReminderPolicy.shouldSchedule(false, false, 15))
        assertFalse(OfflineReminderPolicy.shouldSchedule(true, true, 15))
        assertFalse(OfflineReminderPolicy.shouldSchedule(true, null, 15))
        assertFalse(OfflineReminderPolicy.shouldSchedule(true, false, 0))
    }

    @Test
    fun defaultsFollowTheOwnersRequestForOnePointOne() {
        assertEquals(5, OfflineReminderPolicy.DEFAULT_INTERVAL_MINUTES)
        assertEquals(5, AppStateSnapshot().offlineReminderIntervalMinutes)
        assertFalse(AppStateSnapshot().announceSameCitySubArea)
    }

    @Test
    fun speechSpeedHasThreeChoicesAndKeepsTheOldRateAsNormal() {
        assertEquals(0.95f, SpeechRatePolicy.androidRate(SpeechRatePolicy.DEFAULT_PERCENT), 0.0001f)
        assertEquals(SpeechRatePolicy.DEFAULT_PERCENT, SpeechRatePolicy.normalize(123))
        assertEquals(80, SpeechRatePolicy.normalize(80))
        assertEquals(110, SpeechRatePolicy.normalize(110))
    }
}
