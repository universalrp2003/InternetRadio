package com.universalrp.vocalremover.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessingPolicyTest {
    private val healthy = SafetySnapshot(
        thermal = ThermalLevel.NORMAL,
        batteryTemperatureC = 32f,
        cpuTemperatureC = null,
        availableRamBytes = 2L * 1024 * 1024 * 1024,
        availableStorageBytes = 2L * 1024 * 1024 * 1024,
    )

    @Test fun storageReserveMatchesTheLowStoragePauseGuardrail() {
        assertEquals(96L * 1024L * 1024L, ProcessingPolicy.STORAGE_SAFETY_RESERVE_BYTES)
        assertEquals(ProcessingPolicy.STORAGE_SAFETY_RESERVE_BYTES, ProcessingPolicy.LOW_STORAGE_PAUSE_BYTES)
    }

    @Test fun profilesBoundThreadsAndNeverChangeModelQuality() {
        assertEquals(1, ProcessingPolicy.profileThreads(ProcessingProfile.SLOW, 8))
        assertEquals(2, ProcessingPolicy.profileThreads(ProcessingProfile.BALANCED, 8))
        assertEquals(4, ProcessingPolicy.profileThreads(ProcessingProfile.FAST, 8))
        assertEquals(1, ProcessingPolicy.profileThreads(ProcessingProfile.FAST, 1))
    }

    @Test fun moderateThermalStatusThrottlesEvenFastProfile() {
        val decision = ProcessingPolicy.decide(
            ProcessingProfile.FAST,
            8,
            healthy.copy(thermal = ThermalLevel.MODERATE),
        )
        assertFalse(decision.pause)
        assertEquals(1, decision.threads)
        assertTrue(decision.interChunkDelayMs > 0)
    }

    @Test fun severeThermalStatusPausesAndUnknownIsNotTreatedAsHot() {
        val severe = ProcessingPolicy.decide(
            ProcessingProfile.FAST,
            8,
            healthy.copy(thermal = ThermalLevel.SEVERE),
        )
        assertTrue(severe.pause)
        assertEquals(PauseReason.THERMAL, severe.reason)

        val unknown = ProcessingPolicy.decide(
            ProcessingProfile.FAST,
            8,
            healthy.copy(thermal = ThermalLevel.UNKNOWN, batteryTemperatureC = null),
        )
        assertFalse(unknown.pause)
        assertEquals(4, unknown.threads)
    }

    @Test fun lowMemoryPausesButDegreeReadingsAreInformational() {
        val lowRam = ProcessingPolicy.decide(
            ProcessingProfile.FAST,
            8,
            healthy.copy(availableRamBytes = 200L * 1024 * 1024),
        )
        assertTrue(lowRam.pause)
        assertEquals(PauseReason.LOW_MEMORY, lowRam.reason)

        val warmSensors = ProcessingPolicy.decide(
            ProcessingProfile.FAST,
            8,
            healthy.copy(batteryTemperatureC = 48f, cpuTemperatureC = 90f),
        )
        assertFalse(warmSensors.pause)
        assertEquals(4, warmSensors.threads)
    }

    @Test fun lowMemoryPausesAndModerateHeadroomThrottles() {
        val lowRam = ProcessingPolicy.decide(
            ProcessingProfile.FAST,
            8,
            healthy.copy(availableRamBytes = 400L * 1024 * 1024),
        )
        assertTrue(lowRam.pause)
        assertEquals(PauseReason.LOW_MEMORY, lowRam.reason)

        val tightRam = ProcessingPolicy.decide(
            ProcessingProfile.FAST,
            8,
            healthy.copy(availableRamBytes = 700L * 1024 * 1024),
        )
        assertFalse(tightRam.pause)
        assertEquals(1, tightRam.threads)
    }

    @Test fun zeroAvailableRamOrStorageIsARealSafetySignal() {
        val noRam = ProcessingPolicy.decide(
            ProcessingProfile.BALANCED,
            4,
            healthy.copy(availableRamBytes = 0L),
        )
        assertTrue(noRam.pause)
        assertEquals(PauseReason.LOW_MEMORY, noRam.reason)

        val noStorage = ProcessingPolicy.decide(
            ProcessingProfile.BALANCED,
            4,
            healthy.copy(availableStorageBytes = 0L),
        )
        assertTrue(noStorage.pause)
        assertEquals(PauseReason.STORAGE, noStorage.reason)
    }

    @Test fun missingMetricsRemainUnknownAndDoNotCreateFalseAlarms() {
        val decision = ProcessingPolicy.decide(
            ProcessingProfile.SLOW,
            4,
            SafetySnapshot(ThermalLevel.UNKNOWN, null, null, null, null),
        )
        assertFalse(decision.pause)
        assertEquals(1, decision.threads)
    }

    @Test fun lowMemoryAutoResumeRequiresSustainedHeadroomAndTheLowMemoryReason() {
        val gate = LowMemoryAutoResumeGate(requiredBytes = 100L, stableDurationMs = 15L)
        assertFalse(gate.shouldResume(0L, PauseReason.LOW_MEMORY, 100L))
        assertFalse(gate.shouldResume(14L, PauseReason.LOW_MEMORY, 100L))
        assertTrue(gate.shouldResume(15L, PauseReason.LOW_MEMORY, 100L))

        gate.reset()
        assertFalse(gate.shouldResume(20L, PauseReason.USER, 100L))
        assertFalse(gate.shouldResume(40L, PauseReason.LOW_MEMORY, null))
    }

    @Test fun lowMemoryAutoResumeTimerResetsWhenHeadroomDrops() {
        val gate = LowMemoryAutoResumeGate(requiredBytes = 100L, stableDurationMs = 15L)
        assertFalse(gate.shouldResume(0L, PauseReason.LOW_MEMORY, 100L))
        assertFalse(gate.shouldResume(10L, PauseReason.LOW_MEMORY, 99L))
        assertFalse(gate.shouldResume(20L, PauseReason.LOW_MEMORY, 100L))
        assertFalse(gate.shouldResume(34L, PauseReason.LOW_MEMORY, 100L))
        assertTrue(gate.shouldResume(35L, PauseReason.LOW_MEMORY, 100L))
    }

    @Test fun lowMemoryAutoResumeTimerResetsAfterMonitoringGap() {
        val gate = LowMemoryAutoResumeGate(requiredBytes = 100L, stableDurationMs = 15L, maxSampleGapMs = 5L)
        assertFalse(gate.shouldResume(0L, PauseReason.LOW_MEMORY, 100L))
        assertFalse(gate.shouldResume(4L, PauseReason.LOW_MEMORY, 100L))
        assertFalse(gate.shouldResume(8L, PauseReason.LOW_MEMORY, 100L))
        assertFalse(gate.shouldResume(12L, PauseReason.LOW_MEMORY, 100L))
        assertTrue(gate.shouldResume(16L, PauseReason.LOW_MEMORY, 100L))
        gate.reset()
        assertFalse(gate.shouldResume(20L, PauseReason.LOW_MEMORY, 100L))
        assertFalse(gate.shouldResume(30L, PauseReason.LOW_MEMORY, 100L))
    }
}
