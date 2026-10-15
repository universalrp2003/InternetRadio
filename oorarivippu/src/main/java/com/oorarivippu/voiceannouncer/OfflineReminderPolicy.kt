package com.oorarivippu.voiceannouncer

/** Pure policy for opting into periodic, on-device no-internet status reminders. */
object OfflineReminderPolicy {
    const val DEFAULT_INTERVAL_MINUTES = 15
    val supportedIntervalsMinutes = listOf(0, 5, 10, 15, 30)

    fun normalizeInterval(minutes: Int): Int =
        minutes.takeIf { it in supportedIntervalsMinutes } ?: DEFAULT_INTERVAL_MINUTES

    fun shouldSchedule(
        trackingEnabled: Boolean,
        internetAvailable: Boolean?,
        intervalMinutes: Int
    ): Boolean = trackingEnabled && internetAvailable == false && intervalMinutes > 0
}
