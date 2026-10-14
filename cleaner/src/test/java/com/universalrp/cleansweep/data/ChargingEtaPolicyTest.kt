package com.universalrp.cleansweep.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChargingEtaPolicyTest {
    private fun point(minutes: Long, percent: Double) = ChargingObservationPoint(minutes * 60_000L, percent)

    @Test fun noEstimateUntilThereIsEnoughRecentObservedRise() {
        assertNull(ChargingEtaPolicy.estimate(
            observations = listOf(point(0, 40.0), point(2, 41.0)),
            nowElapsedMs = 2 * 60_000L,
            currentPercent = 41.0,
            powerConnected = true,
            charging = true,
        ))
        assertNull(ChargingEtaPolicy.estimate(
            observations = listOf(point(0, 40.0), point(5, 41.0), point(10, 41.5)),
            nowElapsedMs = 10 * 60_000L,
            currentPercent = 41.5,
            powerConnected = true,
            charging = true,
        ))
    }

    @Test fun stableRecentChargeRiseProducesLabeledEstimateData() {
        val estimate = ChargingEtaPolicy.estimate(
            observations = listOf(
                point(0, 40.0),
                point(2, 40.8),
                point(4, 41.6),
                point(6, 42.4),
                point(8, 43.2),
                point(10, 44.0),
            ),
            nowElapsedMs = 10 * 60_000L,
            currentPercent = 44.0,
            powerConnected = true,
            charging = true,
        )
        assertEquals(140, estimate?.minutesToFull)
    }

    @Test fun fullUnpluggedPausedOrStaleReadingsDoNotProduceAnEta() {
        val observations = listOf(point(0, 40.0), point(5, 42.0), point(10, 44.0))
        assertNull(ChargingEtaPolicy.estimate(observations, 10 * 60_000L, 44.0, false, true))
        assertNull(ChargingEtaPolicy.estimate(observations, 10 * 60_000L, 44.0, true, false))
        assertNull(ChargingEtaPolicy.estimate(observations, 10 * 60_000L, 100.0, true, false))
        assertNull(ChargingEtaPolicy.estimate(observations, 14 * 60_000L, 44.0, true, true))
    }

    @Test fun interruptedOrNonMonotonicObservationsAreRejected() {
        val longGap = listOf(point(0, 40.0), point(6, 42.0), point(10, 44.0))
        val decline = listOf(point(0, 40.0), point(5, 43.0), point(10, 42.0), point(15, 44.0))
        assertNull(ChargingEtaPolicy.estimate(longGap, 10 * 60_000L, 44.0, true, true))
        assertNull(ChargingEtaPolicy.estimate(decline, 15 * 60_000L, 44.0, true, true))
    }

    @Test fun trackerStartsOverAfterDisconnectOrAnUnobservedGap() {
        val tracker = ChargingEtaTracker()
        for (minute in 0L..10L) {
            tracker.observe(minute * 60_000L, 40.0 + 0.4 * minute, true, true)
        }
        assertEquals(140, tracker.estimate(10 * 60_000L, 44.0, true, true)?.minutesToFull)

        assertNull(tracker.observe(11 * 60_000L, null, false, false))
        assertNull(tracker.estimate(11 * 60_000L, 44.0, true, true))
        assertNull(tracker.observe(12 * 60_000L, 45.0, true, true))
        assertNull(tracker.observe(15 * 60_000L, 46.0, true, true))
        assertNull(tracker.estimate(15 * 60_000L, 46.0, true, true))
    }
}
