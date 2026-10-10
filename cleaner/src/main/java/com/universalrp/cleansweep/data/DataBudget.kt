package com.universalrp.cleansweep.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/** A mobile-only daily observation. Null is unknown, not a zero-use day. */
data class DailyDataPoint(
    val day: String,
    val bytes: Long?,
    val complete: Boolean,
    val source: String,
    val observedAtMs: Long,
)

data class DataBudgetConfig(val expiresOn: String? = null)

enum class BudgetStatus { NEED_EXPIRY, EXPIRED, UNAVAILABLE, PARTIAL, UNLIMITED, READY, EXHAUSTED }

data class DataBudgetResult(
    val status: BudgetStatus,
    val daysRemaining: Long? = null,
    val dailyBudgetBytes: Long? = null,
    val averageDailyBytes: Long? = null,
    val forecastDays: Long? = null,
    val completeDays: Int = 0,
)

object DataBudgetPolicy {
    fun dateOrNull(text: String?): LocalDate? = try {
        text?.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }?.let(LocalDate::parse)
    } catch (e: Exception) { null }

    /** Expiry is inclusive, in the PHONE'S local calendar, not a 24-hour/DST division. */
    fun calculate(
        today: LocalDate,
        config: DataBudgetConfig,
        remainingBytes: Long?,
        readingComplete: Boolean,
        unlimited: Boolean,
        history: List<DailyDataPoint>,
        packStartDay: LocalDate? = null,
    ): DataBudgetResult {
        if (unlimited) return DataBudgetResult(BudgetStatus.UNLIMITED)
        if (remainingBytes == null || remainingBytes < 0) return DataBudgetResult(BudgetStatus.UNAVAILABLE)
        if (!readingComplete) return DataBudgetResult(BudgetStatus.PARTIAL)
        val expiry = dateOrNull(config.expiresOn) ?: return DataBudgetResult(BudgetStatus.NEED_EXPIRY)
        val days = ChronoUnit.DAYS.between(today, expiry) + 1L
        if (days <= 0) return DataBudgetResult(BudgetStatus.EXPIRED, daysRemaining = 0)
        val samples = history.distinctBy { it.day }.filter { point ->
            val day = dateOrNull(point.day)
            day != null && day < today && (packStartDay == null || day >= packStartDay) &&
                point.complete && point.bytes != null && point.bytes >= 0
        }.sortedByDescending { it.day }.take(7)
        // Divide before adding to avoid Long overflow on malformed/extreme counters.
        val average = if (samples.isEmpty()) null else
            (samples.sumOf { it.bytes!!.toDouble() } / samples.size).coerceAtMost(Long.MAX_VALUE.toDouble()).toLong()
        val forecast = if (samples.size >= 3 && average != null && average > 0)
            ceil(remainingBytes.toDouble() / average).takeIf { it.isFinite() && it <= 36_500 }?.toLong() else null
        return DataBudgetResult(
            status = if (remainingBytes == 0L) BudgetStatus.EXHAUSTED else BudgetStatus.READY,
            daysRemaining = days,
            dailyBudgetBytes = remainingBytes / days,
            averageDailyBytes = average,
            forecastDays = forecast,
            completeDays = samples.size,
        )
    }

    fun merge(history: List<DailyDataPoint>, point: DailyDataPoint): List<DailyDataPoint> {
        if (dateOrNull(point.day) == null || point.bytes?.let { it < 0 } == true) return history
        val old = history.firstOrNull { it.day == point.day }
        // A failed/partial re-read must not replace a complete dated observation with a fake zero.
        if (old != null && old.complete && !point.complete) return history
        return (history.filterNot { it.day == point.day } + point).sortedBy { it.day }.takeLast(90)
    }
}
