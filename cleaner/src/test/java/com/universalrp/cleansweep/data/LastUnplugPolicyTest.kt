package com.universalrp.cleansweep.data

import org.junit.Assert.*
import org.junit.Test

/** Source-only prepared cases; must run in both authorized build variants before publication. */
class LastUnplugPolicyTest {
    @Test fun explicitEventCreatesRecord() { assertEquals(LastUnplug(1000, 77), LastUnplugPolicy.observation(1000, 77, true)) }
    @Test fun openingUnpluggedDoesNotCreateTime() { assertNull(LastUnplugPolicy.observation(1000, 77, false)) }
    @Test fun missingPercentStaysUnknown() { assertNull(LastUnplugPolicy.observation(1000, null, true)!!.percent) }
    @Test fun invalidPercentStaysUnknown() { assertNull(LastUnplugPolicy.observation(1000, 101, true)!!.percent) }
    @Test fun invalidTimeRejected() { assertNull(LastUnplugPolicy.observation(0, 77, true)) }
    @Test fun futureClockRecordNotCurrent() { assertFalse(LastUnplugPolicy.usable(LastUnplug(1000, 77), 999)) }
    @Test fun unknownRecordNotCurrent() { assertFalse(LastUnplugPolicy.usable(null, 2000)) }
    @Test fun datedRecordUsableWithoutRestamping() { assertTrue(LastUnplugPolicy.usable(LastUnplug(1000, 77), 1000)); assertTrue(LastUnplugPolicy.usable(LastUnplug(1000, 77), 5000)) }
}
