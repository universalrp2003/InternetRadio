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

    @Test fun batteryVoltageAndTemperatureUseDocumentedAndroidBroadcastUnitsAndBounds() {
        assertEquals(4.36f, BatteryValuePolicy.voltageMillivoltsToVolts(4_360)!!, 0.0001f)
        assertNull(BatteryValuePolicy.voltageMillivoltsToVolts(null))
        assertNull(BatteryValuePolicy.voltageMillivoltsToVolts(-1))
        assertNull(BatteryValuePolicy.voltageMillivoltsToVolts(999_999))
        assertEquals(0f, BatteryValuePolicy.temperatureTenthsToCelsius(0)!!, 0f)
        assertEquals(-5f, BatteryValuePolicy.temperatureTenthsToCelsius(-50)!!, 0f)
        assertNull(BatteryValuePolicy.temperatureTenthsToCelsius(-1_000))
        assertNull(BatteryValuePolicy.temperatureTenthsToCelsius(9_999))
    }

    @Test fun batterySensorProvenanceMatchesReportedValues() {
        val reading = BatteryReading(
            percent = 50, temperatureC = 32f, voltageV = 4.1f, currentA = null,
            averageCurrentA = null, powerW = null, charging = false,
            pluggedLabel = "Not plugged in", statusLabel = "Discharging", healthLabel = "Good",
            technology = "Li-ion", chargeCounterMah = null, estimatedCapacityMah = null,
            currentSignNote = "Not reported",
        )
        assertEquals(BatteryMeasurementSource.ANDROID_BATTERY_BROADCAST, reading.temperatureSource)
        assertEquals(BatteryMeasurementSource.ANDROID_BATTERY_BROADCAST, reading.voltageSource)
        assertEquals(BatteryMeasurementSource.UNAVAILABLE, reading.copy(temperatureC = null).temperatureSource)
        assertEquals(BatteryMeasurementSource.UNAVAILABLE, reading.copy(voltageV = null).voltageSource)
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

    // ------------------------------------------------- 2.42 battery-current source policy

    @Test fun theDocumentedCounterWinsWhenItReallyReportsACurrent() {
        val reading = BatteryValuePolicy.currentReading(propertyMicroAmps = 1_100_000, broadcastMicroAmps = 900_000)
        assertEquals(BatteryValuePolicy.CurrentSource.PROPERTY, reading.source)
        assertEquals(1.1f, reading.amps!!, 0.000001f)
        assertTrue(reading.isMeasured)
        assertFalse(BatteryValuePolicy.needsBroadcastFallback(1_100_000, 900_000))
    }

    /** The owner's OPPO A3s: the property answers 0 while the broadcast carries the real charge current. */
    @Test fun aZeroOrUnsupportedPropertyFallsBackToTheBroadcastExtra() {
        val zeroProperty = BatteryValuePolicy.currentReading(0, 1_100_000)
        assertEquals(BatteryValuePolicy.CurrentSource.BROADCAST, zeroProperty.source)
        assertEquals(1.1f, zeroProperty.amps!!, 0.000001f)
        val missingProperty = BatteryValuePolicy.currentReading(Int.MIN_VALUE, 1_100_000)
        assertEquals(BatteryValuePolicy.CurrentSource.BROADCAST, missingProperty.source)
        val nullProperty = BatteryValuePolicy.currentReading(null, -1_100_000)
        assertEquals(-1.1f, nullProperty.amps!!, 0.000001f)
        assertTrue(BatteryValuePolicy.needsBroadcastFallback(0, 1_100_000))
    }

    /** An unsupported sensor must never be rendered as a measured 0.00 W. */
    @Test fun aBarePropertyZeroWithNoBroadcastIsNotReportedAsZeroAmps() {
        for (unsupported in listOf<Int?>(null, 0, Int.MIN_VALUE, Int.MAX_VALUE)) {
            val reading = BatteryValuePolicy.currentReading(unsupported, null)
            assertNull("property=$unsupported", reading.amps)
            assertEquals(BatteryValuePolicy.CurrentSource.NONE, reading.source)
            assertFalse(reading.isMeasured)
            assertNull(ChargingHonestyPolicy.batterySideWatts(true, true, 4.36f, reading.amps))
            assertNull(ChargingHonestyPolicy.wattsText(ChargingHonestyPolicy.batterySideWatts(true, true, 4.36f, reading.amps)))
        }
        val bothMissing = BatteryValuePolicy.currentReading(Int.MIN_VALUE, Int.MIN_VALUE)
        assertNull(bothMissing.amps); assertFalse(BatteryValuePolicy.needsBroadcastFallback(0, null))
    }

    /**
     * 2.43: the owner's OPPO A3s answers zero through BOTH paths, which is indistinguishable
     * from an unsupported sensor — so a zero is never a measurement, from either source.
     */
    @Test fun aZeroFromEverySourceIsNotAMeasurementButAnImplausibleValueIsRefused() {
        val allZero = BatteryValuePolicy.currentReading(0, 0)
        assertNull(allZero.amps)
        assertEquals(BatteryValuePolicy.CurrentSource.NONE, allZero.source)
        assertFalse(allZero.isMeasured)
        assertNull(ChargingHonestyPolicy.batterySideWatts(true, true, 4.36f, allZero.amps))
        assertNull(BatteryValuePolicy.currentReading(null, 0).amps)
        // A working counter reports a trickle as a small NON-zero value, which is still used.
        assertEquals(0.02f, BatteryValuePolicy.currentReading(20_000, null).amps!!, 0.000001f)
        // 50 A is a sensor artefact, not a charging current: refused from either source.
        assertNull(BatteryValuePolicy.currentReading(50_000_000, null).amps)
        val rescued = BatteryValuePolicy.currentReading(50_000_000, 1_000_000)
        assertEquals(1f, rescued.amps!!, 0.000001f)
        assertEquals(BatteryValuePolicy.CurrentSource.BROADCAST, rescued.source)
    }

    /**
     * Ties the fix to the owner's own evidence: 92 % on the A3s reported 4.36 V with a property
     * current of 0, while its charge history recorded real 4.6–4.8 W sessions. The broadcast
     * fallback reproduces a session in that range instead of "0.00 W / -0.00 A".
     */
    @Test fun theOppoA3sReadingProducesTheWattsItsOwnChargeHistoryRecorded() {
        val reading = BatteryValuePolicy.currentReading(0, 1_100_000)
        val watts = ChargingHonestyPolicy.batterySideWatts(charging = true, powerConnected = true, voltageV = 4.36f, currentA = reading.amps)
        assertNotNull(watts)
        assertTrue("watts=$watts", watts!! in 4.5f..5.0f)
        assertEquals("4.80 W", ChargingHonestyPolicy.wattsText(watts))
        assertEquals("1.10 A", ChargingHonestyPolicy.ampsText(ChargingHonestyPolicy.batterySideAmps(reading.amps)))
        // Unchanged: not charging means no charging watts, whatever the current source reported.
        assertNull(ChargingHonestyPolicy.batterySideWatts(false, true, 4.36f, reading.amps))
    }
}
