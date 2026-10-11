package com.universalrp.cleansweep.data

import org.junit.Assert.*
import org.junit.Test

class BatteryValuePolicyTest {
    @Test fun smallMicroampValuesDoNotGetMistakenForMilliamps() {
        assertEquals(0.005f, BatteryValuePolicy.microAmpsToAmps(5_000)!!, 0.000001f)
        assertEquals(-0.005f, BatteryValuePolicy.microAmpsToAmps(-5_000)!!, 0.000001f)
        assertEquals(2f, BatteryValuePolicy.microAmpsToAmps(2_000_000)!!, 0.000001f)
    }
    @Test fun unsupportedCurrentIsNotZeroButMeasuredZeroRemainsZero() {
        assertNull(BatteryValuePolicy.microAmpsToAmps(null)); assertNull(BatteryValuePolicy.microAmpsToAmps(Int.MIN_VALUE))
        assertNull(BatteryValuePolicy.microAmpsToAmps(Int.MAX_VALUE)); assertEquals(0f, BatteryValuePolicy.microAmpsToAmps(0)!!, 0f)
    }
    @Test fun fractionalReportsCannotClaimExactTwentyOrEightyThresholds() {
        assertNull(BatteryValuePolicy.exactWholePercent(20, 20_789, 100_000))
        assertNull(BatteryValuePolicy.exactWholePercent(80, 80_999, 100_000))
    }
    @Test fun genuineIntegerBoundariesAndLegacyCapacityReportsRemainSupported() {
        assertEquals(20, BatteryValuePolicy.exactWholePercent(20, 20_000, 100_000))
        assertEquals(80, BatteryValuePolicy.exactWholePercent(80, 80, 100))
        assertEquals(20, BatteryValuePolicy.exactWholePercent(20, null, null))
        assertNull(BatteryValuePolicy.exactWholePercent(-1, null, null)); assertNull(BatteryValuePolicy.exactWholePercent(20, 120, 100))
    }
}
