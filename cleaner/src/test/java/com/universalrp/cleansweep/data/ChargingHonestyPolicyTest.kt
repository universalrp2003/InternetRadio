package com.universalrp.cleansweep.data

import org.junit.Assert.*
import org.junit.Test

/** 2.41 honest watts/amps: battery-side only, measured only, never guessed or relabelled. */
class ChargingHonestyPolicyTest {
    @Test fun wattsRequireBothRealMeasurements() {
        assertEquals(10f, ChargingHonestyPolicy.batterySideWatts(true, true, 4f, -2.5f)!!, 0.0001f)
        assertNull(ChargingHonestyPolicy.batterySideWatts(true, true, null, 2.5f))
        assertNull(ChargingHonestyPolicy.batterySideWatts(true, true, 4f, null))
    }
    @Test fun dischargingOrUnpluggedNeverShowsChargingWatts() {
        assertNull(ChargingHonestyPolicy.batterySideWatts(false, true, 4f, -1.5f))
        assertNull(ChargingHonestyPolicy.batterySideWatts(true, false, 4f, 1.5f))
        assertNull(ChargingHonestyPolicy.batterySideWatts(false, false, 4f, 0f))
    }
    @Test fun implausibleKernelValuesAreRefusedRatherThanShown() {
        assertNull(ChargingHonestyPolicy.batterySideWatts(true, true, 4f, -500f))
        assertNull(ChargingHonestyPolicy.batterySideWatts(true, true, 0.4f, 2f))
        assertNull(ChargingHonestyPolicy.batterySideWatts(true, true, 4f, Float.NaN))
        assertEquals(120f, ChargingHonestyPolicy.batterySideWatts(true, true, 4f, -30f)!!, 0.0001f)
    }
    @Test fun ampsAreBatterySideMagnitudeAndUnreportedStaysUnknown() {
        assertEquals(1.51f, ChargingHonestyPolicy.batterySideAmps(-1.51f)!!, 0.0001f)
        assertEquals(1.51f, ChargingHonestyPolicy.batterySideAmps(1.51f)!!, 0.0001f)
        assertNull(ChargingHonestyPolicy.batterySideAmps(null))
        assertNull(ChargingHonestyPolicy.batterySideAmps(35f))
    }
    @Test fun aMissingReadingNeverBecomesZero() {
        assertNull(ChargingHonestyPolicy.wattsText(null))
        assertNull(ChargingHonestyPolicy.ampsText(null))
        assertNull(ChargingHonestyPolicy.voltsText(null))
        assertNull(ChargingHonestyPolicy.wattsText(0f))
        assertNull(ChargingHonestyPolicy.wattsText(0.02f))
        assertNull(ChargingHonestyPolicy.wattsText(151f))
    }
    @Test fun valuesAreFormattedWithTheirOwnUnitOnly() {
        assertEquals("8.42 W", ChargingHonestyPolicy.wattsText(8.421f))
        assertEquals("1.51 A", ChargingHonestyPolicy.ampsText(1.507f))
        assertEquals("4.35 V", ChargingHonestyPolicy.voltsText(4.351f))
        assertEquals("25.0 °C", ChargingHonestyPolicy.temperatureText(25f))
    }

    @Test fun voltageAndTemperatureSentinelsAreNotDisplayedAsMeasurements() {
        for (voltage in listOf(null, Float.NaN, 0f, 1.9f, 12.1f, Float.POSITIVE_INFINITY)) {
            assertNull(ChargingHonestyPolicy.validatedBatteryVoltage(voltage))
            assertNull(ChargingHonestyPolicy.voltsText(voltage))
        }
        for (temperature in listOf(null, Float.NaN, -30.1f, 90.1f, Float.POSITIVE_INFINITY)) {
            assertNull(ChargingHonestyPolicy.validatedBatteryTemperature(temperature))
            assertNull(ChargingHonestyPolicy.temperatureText(temperature))
        }
        assertEquals(0f, ChargingHonestyPolicy.validatedBatteryTemperature(0f)!!, 0f)
    }
    @Test fun measuredFlagDistinguishesARealReadingFromAnAbsentSensor() {
        assertTrue(ChargingHonestyPolicy.isMeasured(8.4f))
        assertFalse(ChargingHonestyPolicy.isMeasured(0f))
        assertFalse(ChargingHonestyPolicy.isMeasured(0.02f))
        assertFalse(ChargingHonestyPolicy.isMeasured(null))
    }
    @Test fun screenNoteFollowsTheCableAndTheScreenNotTheCharger() {
        assertEquals(
            ChargingHonestyPolicy.ScreenNote.SCREEN_ON_DRAWS_POWER,
            ChargingHonestyPolicy.screenNote(powerConnected = true, screenOn = true),
        )
        assertEquals(
            ChargingHonestyPolicy.ScreenNote.SCREEN_OFF_LOW_DRAW,
            ChargingHonestyPolicy.screenNote(powerConnected = true, screenOn = false),
        )
        assertEquals(
            ChargingHonestyPolicy.ScreenNote.NOT_CHARGING,
            ChargingHonestyPolicy.screenNote(powerConnected = false, screenOn = true),
        )
    }
}
