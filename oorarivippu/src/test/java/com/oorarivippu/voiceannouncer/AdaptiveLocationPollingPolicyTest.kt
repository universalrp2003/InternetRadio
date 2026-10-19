package com.oorarivippu.voiceannouncer

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveLocationPollingPolicyTest {
    @Test
    fun selectsStationaryCityAndHighwayIntervals() {
        val policy = AdaptiveLocationPollingPolicy()
        assertEquals(45_000L, policy.intervalFor(0f, movementDetected = false))
        assertEquals(15_000L, policy.intervalFor(20f, movementDetected = true))
        assertEquals(5_000L, policy.intervalFor(55f, movementDetected = true))
    }

    @Test
    fun usesHysteresisAroundHighwayThreshold() {
        val policy = AdaptiveLocationPollingPolicy()
        policy.intervalFor(50f, movementDetected = true)
        assertEquals(5_000L, policy.intervalFor(40f, movementDetected = true))
        assertEquals(15_000L, policy.intervalFor(34f, movementDetected = true))
    }

    @Test
    fun detectedMovementPreventsStationaryPolling() {
        val policy = AdaptiveLocationPollingPolicy()
        policy.intervalFor(0f, movementDetected = false)
        assertEquals(15_000L, policy.intervalFor(0f, movementDetected = true))
    }
}
