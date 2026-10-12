package com.universalrp.cleansweep.data

import org.junit.Assert.*
import org.junit.Test

/**
 * 2.42 owner decision: the ongoing charging meter, the background battery-health monitor and
 * the security hash check's system-app scope default ON. These tests hold the defaults and the
 * re-arm rule; they cannot prove that an OEM lets a service survive, which still needs a phone.
 */
class MonitorDefaultsPolicyTest {

    @Test fun theOngoingMeterAndTheBackgroundMonitorDefaultOn() {
        assertTrue(MonitorDefaultsPolicy.CHARGE_CARD)
        assertTrue(MonitorDefaultsPolicy.PERSISTENT_MONITOR)
    }

    @Test fun preinstalledSystemAppsAreInTheSecurityScopeByDefault() {
        assertTrue(MonitorDefaultsPolicy.MALWARE_INCLUDE_SYSTEM)
        assertTrue(MonitorDefaultsPolicy.SECURITY_INCLUDE_SYSTEM)
        assertTrue(MonitorDefaultsPolicy.CACHE_INCLUDE_SYSTEM)
    }

    /**
     * The floating pill needs "Display over other apps", and that access is never granted
     * automatically, so defaulting it ON would promise a reading the phone has not allowed.
     */
    @Test fun theOverlayPillStaysOptIn() {
        assertFalse(MonitorDefaultsPolicy.STATUS_PILL)
        assertNotEquals(MonitorDefaultsPolicy.STATUS_PILL, MonitorDefaultsPolicy.CHARGE_CARD)
    }

    @Test fun automaticOnlineDailySummariesStayASeparateConsent() {
        assertFalse(MonitorDefaultsPolicy.DAILY_ONLINE_SUMMARIES)
    }

    /**
     * "after update turn on automatically status bar led meter": an update or a reboot must
     * re-arm the card even when the owner never wrote the preference. A saved OFF still wins,
     * because it arrives here already resolved against the defaults.
     */
    @Test fun anUpdateOrRebootRearmsTheCardWhenNothingWasEverWritten() {
        // Nothing written: both readers resolve to the defaults.
        assertTrue(MonitorDefaultsPolicy.rearmAfterUpdateOrBoot(
            MonitorDefaultsPolicy.CHARGE_CARD, MonitorDefaultsPolicy.PERSISTENT_MONITOR))
        // The owner turned the card off but kept background monitoring: still re-armed.
        assertTrue(MonitorDefaultsPolicy.rearmAfterUpdateOrBoot(false, true))
        assertTrue(MonitorDefaultsPolicy.rearmAfterUpdateOrBoot(true, false))
        // Both explicitly off: an OFF choice is respected, never overwritten by an update.
        assertFalse(MonitorDefaultsPolicy.rearmAfterUpdateOrBoot(false, false))
    }

    @Test fun theHourlyHealthWatchIsArmedByAnyPartOfTheBackgroundWatch() {
        assertTrue(MonitorDefaultsPolicy.healthWatchWanted(voiceOn = false, dailyScanOn = false, persistentMonitor = true))
        assertTrue(MonitorDefaultsPolicy.healthWatchWanted(voiceOn = true, dailyScanOn = false, persistentMonitor = false))
        assertTrue(MonitorDefaultsPolicy.healthWatchWanted(voiceOn = false, dailyScanOn = true, persistentMonitor = false))
        // Everything off means no hourly wake-up at all: a default is not a permanent service.
        assertFalse(MonitorDefaultsPolicy.healthWatchWanted(voiceOn = false, dailyScanOn = false, persistentMonitor = false))
    }
}
