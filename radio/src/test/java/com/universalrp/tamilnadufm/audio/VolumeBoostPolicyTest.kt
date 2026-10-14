package com.universalrp.tamilnadufm.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeBoostPolicyTest {
    @Test fun boostIsBoundedToTheRequestedZeroToTenDbRange() {
        assertEquals(0f, VolumeBoostPolicy.clamp(-2f), 0f)
        assertEquals(4.5f, VolumeBoostPolicy.clamp(4.5f), 0f)
        assertEquals(10f, VolumeBoostPolicy.clamp(12f), 0f)
        assertEquals(0f, VolumeBoostPolicy.clamp(Float.NaN), 0f)
        assertEquals(0f, VolumeBoostPolicy.clamp(Float.POSITIVE_INFINITY), 0f)
    }

    @Test fun tinyValuesStayOffUntilTheyExceedTheNoiseFloor() {
        assertFalse(VolumeBoostPolicy.isActive(0.05f))
        assertTrue(VolumeBoostPolicy.isActive(0.1f))
    }

    @Test fun legacyGainUsesMillibels() {
        assertEquals(0, VolumeBoostPolicy.toMillibels(0f))
        assertEquals(150, VolumeBoostPolicy.toMillibels(1.5f))
        assertEquals(1000, VolumeBoostPolicy.toMillibels(10f))
        assertEquals(1000, VolumeBoostPolicy.toMillibels(11f))
    }
}
