package com.rpideas.secretmessage.preview.chat

/** An unacknowledged sender copy is removed 30 seconds after Send. There is no offline queue. */
const val UNDELIVERED_TTL_MILLIS = 30_000L

/** The recipient-view countdown is user-selected from 60 to 300 seconds and cannot be turned off. */
const val MIN_VIEW_SECONDS = 60
const val MAX_VIEW_SECONDS = 300
const val DEFAULT_VIEW_SECONDS = 60
const val VIEW_SECONDS_STEP = 30

/** Upper bound on in-memory messages; the oldest are removed first. */
const val MAX_MESSAGES_IN_MEMORY = 40

/** All times are Android elapsed-realtime milliseconds, never the wall clock. */
object ChatTimerPolicy {
    fun clampViewSeconds(seconds: Int): Int = seconds.coerceIn(MIN_VIEW_SECONDS, MAX_VIEW_SECONDS)

    /** Clamp, then snap to the 30-second steps offered by the settings slider. */
    fun snapViewSeconds(seconds: Int): Int {
        val clamped = clampViewSeconds(seconds)
        val steps = (clamped - MIN_VIEW_SECONDS + VIEW_SECONDS_STEP / 2) / VIEW_SECONDS_STEP
        return clampViewSeconds(MIN_VIEW_SECONDS + steps * VIEW_SECONDS_STEP)
    }

    fun undeliveredDeadline(sentAtElapsedMillis: Long): Long = sentAtElapsedMillis + UNDELIVERED_TTL_MILLIS

    fun viewDeadline(viewedAtElapsedMillis: Long, viewSeconds: Int): Long =
        viewedAtElapsedMillis + clampViewSeconds(viewSeconds) * 1_000L

    fun isExpired(nowElapsedMillis: Long, deadlineElapsedMillis: Long?): Boolean =
        deadlineElapsedMillis != null && nowElapsedMillis >= deadlineElapsedMillis

    fun remainingSeconds(nowElapsedMillis: Long, deadlineElapsedMillis: Long): Int {
        val remainingMillis = (deadlineElapsedMillis - nowElapsedMillis).coerceAtLeast(0L)
        return ((remainingMillis + 999L) / 1_000L).toInt()
    }
}
