package com.universalrp.cleansweep.widget

import org.junit.Assert.*
import org.junit.Test

class WidgetReadingPolicyTest {
    @Test fun integerCapacityDoesNotAcquireInventedDecimalPlaces() {
        assertEquals("77%", WidgetReadingPolicy.percent(77))
        assertEquals("77%", WidgetReadingPolicy.percent(77, 77, 100))
    }
    @Test fun extraBroadcastResolutionIsUsedOnlyWhenActuallyReported() {
        assertEquals("77.4%", WidgetReadingPolicy.percent(77, 774, 1_000))
        assertEquals("77.45%", WidgetReadingPolicy.percent(77, 7_745, 10_000))
    }
    @Test fun requestedFiveDigitsRequireARealFineScaleBroadcast() {
        assertEquals("77.45678%", WidgetReadingPolicy.percent(77, 7_745_678, 10_000_000))
    }
    @Test fun invalidBroadcastScaleOrLevelCannotInventAPercent() {
        assertEquals("77%", WidgetReadingPolicy.percent(77, 101, 100))
        assertEquals("77%", WidgetReadingPolicy.percent(77, 0, 0))
    }
    @Test fun unknownReadingsDoNotBecomeZeroOrHealthy() {
        assertEquals("— %", WidgetReadingPolicy.percent(-1)); assertEquals("— %", WidgetReadingPolicy.percent(null))
        assertEquals("— W", WidgetReadingPolicy.watts(true, true, null))
    }
    @Test fun batterySidePowerIsRoundedForDisplayNotCalledFiveDigitAccuracy() {
        assertEquals("4.12 W", WidgetReadingPolicy.watts(true, true, 4.123456f))
    }
    @Test fun fullPausedAndUnpluggedAreNotChargingPower() {
        assertEquals("— W", WidgetReadingPolicy.watts(true, false, 4f))
        assertEquals("— W", WidgetReadingPolicy.watts(false, false, 4f))
    }
    @Test fun nonFiniteNegativeAndOutOfRangePowerAreUnavailable() {
        for (v in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 151f)) assertEquals("— W", WidgetReadingPolicy.watts(true, true, v))
    }
    @Test fun reportedZeroRemainsDifferentFromUnknown() {
        assertEquals("0%", WidgetReadingPolicy.percent(0)); assertEquals("0.00 W", WidgetReadingPolicy.watts(true, true, 0f))
    }
    @Test fun newWidgetDefaultsToBothAndItsViewButtonCyclesSafely() {
        assertEquals(WidgetReadingPolicy.Mode.BOTH, WidgetReadingPolicy.Mode.parse(null))
        assertEquals(WidgetReadingPolicy.Mode.BOTH, WidgetReadingPolicy.Mode.parse("invalid"))
        assertEquals(WidgetReadingPolicy.Mode.WATTS, WidgetReadingPolicy.Mode.BOTH.next())
        assertEquals(WidgetReadingPolicy.Mode.PERCENT, WidgetReadingPolicy.Mode.WATTS.next())
        assertEquals(WidgetReadingPolicy.Mode.BOTH, WidgetReadingPolicy.Mode.PERCENT.next())
    }
    @Test fun rawCurrentSignAndSensorUnknownsArePreserved() {
        assertEquals("-500 mA", WidgetReadingPolicy.current(-0.5f))
        assertEquals("— mA", WidgetReadingPolicy.current(Float.NaN)); assertEquals("— °C", WidgetReadingPolicy.temperature(null))
    }
    @Test fun voltageAndTemperatureUseAvailableResolutionAndPlausibilityGuards() {
        assertEquals("4.123 V", WidgetReadingPolicy.voltage(4.123f)); assertEquals("35.2 °C", WidgetReadingPolicy.temperature(35.2f))
        assertEquals("— V", WidgetReadingPolicy.voltage(99f)); assertEquals("— °C", WidgetReadingPolicy.temperature(Float.POSITIVE_INFINITY))
    }
}
