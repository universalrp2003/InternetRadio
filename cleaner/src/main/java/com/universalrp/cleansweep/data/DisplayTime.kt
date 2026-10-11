package com.universalrp.cleansweep.data

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Display only. Machine-readable dates/feed parsing/elapsed durations keep their original formats. */
object DisplayTime {
    fun clock(atMs: Long, seconds: Boolean = false, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(atMs).atZone(zone).format(DateTimeFormatter.ofPattern(if (seconds) "h:mm:ss a" else "h:mm a", Locale.ENGLISH))

    fun dated(atMs: Long, tamil: Boolean = false, seconds: Boolean = false, zone: ZoneId = ZoneId.systemDefault()): String {
        val time = Instant.ofEpochMilli(atMs).atZone(zone)
        val date = time.format(DateTimeFormatter.ofPattern("d MMM yyyy", if (tamil) Locale.forLanguageTag("ta-IN") else Locale.ENGLISH))
        return date + " · " + clock(atMs, seconds, zone)
    }

    fun hour(hour: Int): String = LocalTime.of(hour.coerceIn(0, 23), 0)
        .format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
}
