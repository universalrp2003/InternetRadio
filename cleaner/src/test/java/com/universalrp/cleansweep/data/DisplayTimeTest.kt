package com.universalrp.cleansweep.data

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class DisplayTimeTest {
    private val utc = ZoneId.of("UTC")
    private fun at(time: String) = Instant.parse("2026-10-11T${time}Z").toEpochMilli()
    @Test fun midnightIsTwelveAm() { assertEquals("12:00 AM", DisplayTime.clock(at("00:00:00"), zone = utc)) }
    @Test fun noonIsTwelvePm() { assertEquals("12:00 PM", DisplayTime.clock(at("12:00:00"), zone = utc)) }
    @Test fun afternoonDoesNotUseTwentyFourHourTime() { assertEquals("3:25:12 PM", DisplayTime.clock(at("15:25:12"), seconds = true, zone = utc)) }
    @Test fun morningHasAmSuffix() { assertEquals("9:07 AM", DisplayTime.clock(at("09:07:00"), zone = utc)) }
    @Test fun phoneTimezoneIsRespected() { assertEquals("5:30 AM", DisplayTime.clock(at("00:00:00"), zone = ZoneId.of("Asia/Calcutta"))) }
    @Test fun datedReadingsIncludeYearAndAmPm() { assertEquals("11 Oct 2026 · 3:25 PM", DisplayTime.dated(at("15:25:00"), zone = utc)) }
    @Test fun tamilDateStillHasExplicitAmPm() { val result = DisplayTime.dated(at("15:25:12"), tamil = true, seconds = true, zone = utc); assertTrue(result.contains("2026")); assertTrue(result.endsWith("3:25:12 PM")) }
    @Test fun scheduledHoursUseSamePolicyRegardlessOfDefaultLocale() {
        val original = Locale.getDefault()
        try { Locale.setDefault(Locale.forLanguageTag("ta-IN")); assertEquals("12:00 AM", DisplayTime.hour(0)); assertEquals("12:00 PM", DisplayTime.hour(12)); assertEquals("11:00 PM", DisplayTime.hour(23)) } finally { Locale.setDefault(original) }
    }
}
