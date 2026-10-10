package com.universalrp.cleansweep.data

import org.junit.Assert.*
import org.junit.Test

class ReadingPolicyTest {
    @Test fun absentSecurityIsNotCheckedNotNinetyFive() { assertEquals("Not checked", ReadingPolicy.securityValue(null)) }
    @Test fun maximumScoreDoesNotCertifySafety() {
        assertEquals("100/100", ReadingPolicy.securityValue(insightReport()))
        assertTrue(ReadingPolicy.securityNote(insightReport()).contains("not proof"))
        assertFalse(insightReport().verdict.contains("safe", ignoreCase = true))
    }
    @Test fun partialSecurityHasNoTrustworthyNumericGrade() { assertEquals("Incomplete check", ReadingPolicy.securityValue(insightReport(unavailable = setOf("no_lock")))) }
    @Test fun absentAndInvalidPingAreNeverInvented() { assertEquals("Not measured", ReadingPolicy.pingValue(null)); assertEquals("Not measured", ReadingPolicy.pingValue(0)) }
    @Test fun observedPingIsPreserved() { assertEquals("73 ms", ReadingPolicy.pingValue(73)) }
    @Test fun memoryReadFailureIsUnknownNotZeroPercent() { assertNull(ReadingPolicy.ramUsedFraction(0, 0)); assertNull(ReadingPolicy.ramUsedFraction(null, null)) }
    @Test fun invalidMemoryCannotProduceHealthyFraction() { assertNull(ReadingPolicy.ramUsedFraction(100, -1)); assertNull(ReadingPolicy.ramUsedFraction(100, 101)) }
    @Test fun actualMemoryFractionUsesTheObservedCounters() { assertEquals(0.75f, ReadingPolicy.ramUsedFraction(100, 25)!!, 0.0001f) }
    @Test fun missingLatencyOrJitterOrNetworkHasUnknownQuality() {
        assertEquals(NetworkQualityPolicy.Grade.UNKNOWN, NetworkQualityPolicy.grade(null, null, true))
        assertEquals(NetworkQualityPolicy.Grade.UNKNOWN, NetworkQualityPolicy.grade(30, null, true))
        assertEquals(NetworkQualityPolicy.Grade.UNKNOWN, NetworkQualityPolicy.grade(30, 2, false))
    }
    @Test fun qualityRanksObservedLatencyNotIdleOrFastThroughput() {
        assertEquals(NetworkQualityPolicy.Grade.GOOD, NetworkQualityPolicy.grade(30, 2, true))
        assertEquals(NetworkQualityPolicy.Grade.FAIR, NetworkQualityPolicy.grade(90, 20, true))
        assertEquals(NetworkQualityPolicy.Grade.POOR, NetworkQualityPolicy.grade(180, 40, true))
    }
}
