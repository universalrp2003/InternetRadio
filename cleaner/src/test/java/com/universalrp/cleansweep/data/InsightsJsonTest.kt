package com.universalrp.cleansweep.data

import org.junit.Assert.*
import org.junit.Test

class InsightsJsonTest {
    @Test fun unknownChargingSensorsRoundTripAsNullNotZero() {
        val state = ChargeHistoryPolicy.observe(ChargeHistoryState(), ChargeSample(1000, 0, true, null, null, null))
        assertEquals(state, InsightsJson.readCharge(InsightsJson.charge(state)))
        assertNull(InsightsJson.readCharge(InsightsJson.charge(state)).active!!.averageWatts)
    }
    @Test fun measuredAggregateAndElapsedTimesSurvivePersistence() {
        var state = ChargeHistoryPolicy.observe(ChargeHistoryState(), ChargeSample(1000, 0, true, 20, 10.0, 35.0, true))
        state = ChargeHistoryPolicy.observe(state, ChargeSample(2000, 30_000, true, 30, 20.0, 36.0))
        val restored = InsightsJson.readCharge(InsightsJson.charge(state)); assertEquals(state, restored)
        assertEquals(15.0, restored.active!!.averageWatts!!, 0.0001)
    }
    @Test fun securityIdentityCoverageBaselineAndReviewRoundTrip() {
        var state = SecurityHistoryPolicy.observe(SecurityHistoryState(), insightSnapshot(findings = listOf(insightFinding(component = "org.example.reader/Service"))))
        state = SecurityHistoryPolicy.review(state, state.latest!!.observations.single(), true, 1200)
        assertEquals(state, InsightsJson.readSecurity(InsightsJson.security(state)))
    }
    @Test fun dailyZeroAndUnavailableStayDistinctAfterPersistence() {
        val points = listOf(DailyDataPoint("2026-10-08", 0, true, "Android", 1000), DailyDataPoint("2026-10-09", null, false, "unavailable", 2000))
        val restored = InsightsJson.readData(InsightsJson.data(points)); assertEquals(points, restored)
        assertEquals(0L, restored[0].bytes); assertNull(restored[1].bytes)
    }
    @Test fun corruptOrFutureSchemaCannotGenerateFakeResolutions() {
        assertEquals(SecurityHistoryState(), InsightsJson.readSecurity("not-json"))
        assertEquals(ChargeHistoryState(), InsightsJson.readCharge("{\"schema\":99}"))
        assertTrue(InsightsJson.readData(null).isEmpty())
        val baseline = SecurityHistoryPolicy.observe(InsightsJson.readSecurity("bad"), insightSnapshot(findings = listOf(insightFinding())))
        assertEquals(listOf(SecurityChangeKind.BASELINE), baseline.events.map { it.kind })
    }
    @Test fun identityKeyTamperingIsNotAcceptedAsTrustedLocalBinding() {
        val state = SecurityHistoryPolicy.observe(SecurityHistoryState(), insightSnapshot(findings = listOf(insightFinding())))
        val raw = InsightsJson.security(state).replace("accessibility|org.example.reader|", "forged-key")
        assertEquals(SecurityHistoryState(), InsightsJson.readSecurity(raw))
    }
}
