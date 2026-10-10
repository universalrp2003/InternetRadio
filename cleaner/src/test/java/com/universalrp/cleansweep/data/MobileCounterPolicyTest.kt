package com.universalrp.cleansweep.data

import org.junit.Assert.*
import org.junit.Test

class MobileCounterPolicyTest {
    @Test fun firstCounterDoesNotPretendToObserveTheWholeDay() {
        val result = MobileCounterPolicy.observe(MobileCounterState(), 1000, "today", 1000, 1)
        assertEquals(0L, result.todayBytes); assertEquals(0L, result.packBytes)
    }
    @Test fun subsequentReadingCountsOnlyObservedMobileDelta() {
        val result = MobileCounterPolicy.observe(MobileCounterState(100, 10, 5, "today", 100, 1), 140, "today", 200, 1)
        assertEquals(50L, result.packBytes); assertEquals(45L, result.todayBytes); assertFalse(result.resetDetected)
    }
    @Test fun lowerRebootCounterPreservesEarlierObservedPackTraffic() {
        val result = MobileCounterPolicy.observe(MobileCounterState(1000, 300, 80, "today", 1000, 1), 10, "today", 10, 2)
        assertEquals(310L, result.packBytes); assertEquals(90L, result.todayBytes); assertTrue(result.resetDetected)
    }
    @Test fun explicitBootChangeIsDetectedEvenWhenNewCounterHasOvertakenOldOne() {
        val result = MobileCounterPolicy.observe(MobileCounterState(100, 1, 1, "today", 100, 5), 500, "today", 200, 6)
        assertTrue(result.resetDetected); assertEquals(501L, result.packBytes)
    }
    @Test fun midnightSpanningDeltaIsNotAssignedToTheNewDay() {
        val first = MobileCounterPolicy.observe(MobileCounterState(100, 100, 40, "yesterday", 100, 1), 110, "today", 200, 1)
        assertEquals(0L, first.todayBytes); assertEquals(110L, first.packBytes)
        val second = MobileCounterPolicy.observe(first.state, 120, "today", 300, 1); assertEquals(10L, second.todayBytes)
    }
    @Test fun legacyBaselineMigrationDoesNotDoubleCountNewDelta() {
        val state = MobileCounterPolicy.migrate(100, 150, 0, 50, null, null, "today")
        val result = MobileCounterPolicy.observe(state, 150, "today", 300, 1)
        assertEquals(150L, result.packBytes); assertEquals(100L, result.todayBytes)
    }
    @Test fun overflowCannotWrapIntoNegativeUsage() {
        val result = MobileCounterPolicy.observe(MobileCounterState(0, Long.MAX_VALUE - 1, Long.MAX_VALUE - 1, "today"), 100, "today", 100, 1)
        assertEquals(Long.MAX_VALUE, result.packBytes); assertEquals(Long.MAX_VALUE, result.todayBytes)
    }
    @Test fun unsupportedCounterIsUnknownNotZeroAndDoesNotDestroyState() {
        val state = MobileCounterState(100, 50, 20, "today")
        val result = MobileCounterPolicy.observe(state, -1, "today", 500, null)
        assertEquals(state, result.state); assertNull(result.packBytes); assertNull(result.todayBytes)
    }
}
