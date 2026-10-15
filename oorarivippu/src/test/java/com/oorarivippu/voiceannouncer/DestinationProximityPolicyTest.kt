package com.oorarivippu.voiceannouncer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DestinationProximityPolicyTest {
    @Test
    fun firstFixSkipsAlreadyPassedDistanceAlerts() {
        val result = DestinationProximityPolicy.evaluate(
            currentDistanceMeters = 4_000f,
            previousDistanceMeters = null,
            announcedMask = 0
        )
        assertNull(result.alertDistanceMeters)
    }

    @Test
    fun crossingThresholdEmitsItOnlyOnce() {
        val first = DestinationProximityPolicy.evaluate(
            currentDistanceMeters = 9_800f,
            previousDistanceMeters = 10_200f,
            announcedMask = 0
        )
        assertEquals(10_000, first.alertDistanceMeters)

        val repeated = DestinationProximityPolicy.evaluate(
            currentDistanceMeters = 9_500f,
            previousDistanceMeters = 9_800f,
            announcedMask = first.updatedMask
        )
        assertNull(repeated.alertDistanceMeters)
    }

    @Test
    fun crossingFiveKilometersAfterTenKilometersEmitsNextMilestone() {
        val tenKilometers = DestinationProximityPolicy.evaluate(
            currentDistanceMeters = 9_900f,
            previousDistanceMeters = 10_100f,
            announcedMask = 0
        )
        val fiveKilometers = DestinationProximityPolicy.evaluate(
            currentDistanceMeters = 4_900f,
            previousDistanceMeters = 5_100f,
            announcedMask = tenKilometers.updatedMask
        )
        assertEquals(5_000, fiveKilometers.alertDistanceMeters)
    }

    @Test
    fun longGapSpeaksOnlyNearestCrossedMilestone() {
        val result = DestinationProximityPolicy.evaluate(
            currentDistanceMeters = 1_500f,
            previousDistanceMeters = 12_000f,
            announcedMask = 0
        )
        assertEquals(2_000, result.alertDistanceMeters)
    }

    @Test
    fun arrivalIsOneTimeAndConsumesEarlierMilestones() {
        val result = DestinationProximityPolicy.evaluate(
            currentDistanceMeters = 350f,
            previousDistanceMeters = 700f,
            announcedMask = 0
        )
        assertEquals(500, result.alertDistanceMeters)

        val repeated = DestinationProximityPolicy.evaluate(
            currentDistanceMeters = 200f,
            previousDistanceMeters = 350f,
            announcedMask = result.updatedMask
        )
        assertNull(repeated.alertDistanceMeters)
    }
}
