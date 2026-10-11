package com.universalrp.cleansweep.data

import org.junit.Assert.*
import org.junit.Test

class ChargeHistoryPolicyTest {
    private fun sample(elapsed: Long, percent: Int? = 30, watts: Double? = 10.0, connected: Boolean = true,
                       wall: Long = 1_000_000 + elapsed, plug: Boolean = false, unplug: Boolean = false, temp: Double? = 35.0) =
        ChargeSample(wall, elapsed, connected, percent, watts, temp, plug, unplug)
    @Test fun missingSensorsStayUnknownNotRatedWattsOrTemperature() {
        var state = ChargeHistoryPolicy.observe(ChargeHistoryState(), sample(0, watts = null, temp = null))
        state = ChargeHistoryPolicy.observe(state, sample(30_000, watts = null, temp = null))
        assertNull(state.active!!.averageWatts); assertNull(state.active!!.peakWatts); assertNull(state.active!!.maxTemperatureC)
        assertEquals(0, state.active!!.measuredPowerMs)
    }
    @Test fun observedUnplugClosesTheSessionWithoutChangingAnyMonitorPreference() {
        val start = ChargeHistoryPolicy.observe(ChargeHistoryState(), sample(0, percent = 20, plug = true))
        val state = ChargeHistoryPolicy.observe(start, sample(60_000, percent = 25, connected = false, unplug = true))
        assertNull(state.active); assertEquals(25, state.sessions.single().endPercent)
        assertTrue(state.sessions.single().endObserved); assertFalse(state.sessions.single().partial)
    }
    @Test fun enablingMidChargeCannotInventAnEarlierPlugTime() {
        val state = ChargeHistoryPolicy.observe(ChargeHistoryState(), sample(1000, percent = 60))
        assertFalse(state.active!!.observedFromPlug); assertTrue(state.active!!.partial)
        assertNull(state.active!!.twentyToEightyMs)
    }
    @Test fun measuredAverageUsesOnlyShortValidSampledIntervals() {
        var state = ChargeHistoryPolicy.observe(ChargeHistoryState(), sample(0, watts = 10.0))
        state = ChargeHistoryPolicy.observe(state, sample(30_000, watts = 20.0))
        assertEquals(15.0, state.active!!.averageWatts!!, 0.0001)
        assertEquals(20.0, state.active!!.peakWatts!!, 0.0001); assertEquals(30_000, state.active!!.measuredPowerMs)
    }
    @Test fun wallClockChangeDoesNotChangeObservedDuration() {
        var state = ChargeHistoryPolicy.observe(ChargeHistoryState(), sample(0, wall = 100_000))
        state = ChargeHistoryPolicy.observe(state, sample(60_000, wall = 1000))
        assertEquals(60_000, state.active!!.durationMs)
    }
    @Test fun rebootCannotJoinDifferentElapsedClocks() {
        val before = ChargeHistoryPolicy.observe(ChargeHistoryState(), sample(100_000, plug = true))
        val state = ChargeHistoryPolicy.observe(before, sample(10, wall = 2_000_000))
        assertTrue(state.sessions.single().interrupted); assertFalse(state.sessions.single().endObserved)
        assertFalse(state.active!!.observedFromPlug); assertNotEquals(state.sessions.single().id, state.active!!.id)
    }
    @Test fun longGapCannotInventEnergyOrTwentyToEightyTime() {
        var state = ChargeHistoryPolicy.observe(ChargeHistoryState(), sample(0, percent = 20))
        state = ChargeHistoryPolicy.observe(state, sample(180_000, percent = 80))
        assertTrue(state.active!!.interrupted); assertNull(state.active!!.twentyToEightyMs)
        assertNull(state.active!!.averageWatts); assertEquals(0, state.active!!.measuredPowerMs)
    }
    @Test fun invalidReadingsRemainNullNotZero() {
        val active = ChargeHistoryPolicy.observe(ChargeHistoryState(), sample(0, percent = 999, watts = Double.NaN, temp = Double.POSITIVE_INFINITY)).active!!
        assertNull(active.startPercent); assertNull(active.peakWatts); assertNull(active.maxTemperatureC)
        val second = ChargeHistoryPolicy.observe(ChargeHistoryState(), sample(0, watts = -1.0)).active!!
        assertNull(second.peakWatts)
    }
    @Test fun sessionsAndRetentionAreBounded() {
        var state = ChargeHistoryState()
        for (i in 0 until 40) {
            state = ChargeHistoryPolicy.observe(state, sample(i * 60_000L, plug = true))
            state = ChargeHistoryPolicy.observe(state, sample(i * 60_000L + 30_000, connected = false, unplug = true))
        }
        assertEquals(30, state.sessions.size)
        state = ChargeHistoryPolicy.observe(state, sample(3_000_000, connected = false, wall = 1_000_000 + ChargeHistoryPolicy.RETENTION_MS + 4_000_000))
        assertTrue(state.sessions.isEmpty())
    }
    @Test fun chartPointsAreBoundedWithoutLosingFullSessionAggregate() {
        var state = ChargeHistoryState()
        for (i in 0..400) state = ChargeHistoryPolicy.observe(state, sample(i * 30_000L, watts = 10.0))
        assertEquals(240, state.active!!.samples.size); assertEquals(12_000_000, state.active!!.measuredPowerMs)
        assertEquals(10.0, state.active!!.averageWatts!!, 0.0001)
    }
    @Test fun twentyToEightyRequiresBothObservedThresholdsAndContinuousCoverage() {
        var state = ChargeHistoryPolicy.observe(ChargeHistoryState(), sample(0, percent = 10))
        listOf(20, 40, 60, 80).forEachIndexed { i, percent -> state = ChargeHistoryPolicy.observe(state, sample((i + 1) * 60_000L, percent = percent)) }
        assertEquals(180_000L, state.active!!.twentyToEightyMs)
        var skipped = ChargeHistoryPolicy.observe(ChargeHistoryState(), sample(0, percent = 19))
        skipped = ChargeHistoryPolicy.observe(skipped, sample(30_000, percent = 21))
        skipped = ChargeHistoryPolicy.observe(skipped, sample(60_000, percent = 81))
        assertNull(skipped.active!!.twentyToEightyMs) // Do not invent exact 20/80 readings from skipped thresholds.
    }
    @Test fun fullOrPausedButPhysicallyConnectedStillFormsAnUnknownPowerSession() {
        var state = ChargeHistoryPolicy.observe(ChargeHistoryState(), sample(0, percent = 100, watts = null, plug = true))
        state = ChargeHistoryPolicy.observe(state, sample(60_000, percent = 100, watts = null))
        assertNotNull(state.active); assertNull(state.active!!.averageWatts); assertNull(state.active!!.peakWatts)
    }
    @Test fun rebootWithLongerNewUptimeStillStartsANewPartialSession() {
        var state = ChargeHistoryPolicy.observe(ChargeHistoryState(), sample(1000, plug = true).copy(bootCount = 1))
        state = ChargeHistoryPolicy.observe(state, sample(2000).copy(bootCount = 2))
        assertEquals(1, state.sessions.size); assertTrue(state.sessions.single().interrupted)
        assertEquals(0L, state.active!!.durationMs); assertFalse(state.active!!.observedFromPlug)
    }
    @Test fun retentionPrunesOnReadAndGapDoesNotEraseAlreadyObservedInterval() {
        var state = ChargeHistoryPolicy.observe(ChargeHistoryState(), sample(0, percent = 20))
        state = ChargeHistoryPolicy.observe(state, sample(60_000, percent = 80))
        state = ChargeHistoryPolicy.observe(state, sample(300_000, percent = 90))
        assertEquals(60_000L, state.sessions.single().twentyToEightyMs)
        assertEquals(0L, state.active!!.durationMs)
        assertEquals(ChargeHistoryState(), ChargeHistoryPolicy.prune(state, 1_000_000 + 300_000 + ChargeHistoryPolicy.RETENTION_MS + 1))
    }

}
