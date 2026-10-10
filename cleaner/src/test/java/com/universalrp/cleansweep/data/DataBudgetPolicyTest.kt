package com.universalrp.cleansweep.data

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class DataBudgetPolicyTest {
    private val today = LocalDate.of(2026, 10, 10)
    private fun calculate(expiry: String? = "2026-10-12", remaining: Long? = 900, complete: Boolean = true, unlimited: Boolean = false,
                          history: List<DailyDataPoint> = emptyList(), start: LocalDate? = null) =
        DataBudgetPolicy.calculate(today, DataBudgetConfig(expiry), remaining, complete, unlimited, history, start)
    private fun point(day: String, bytes: Long? = 100, complete: Boolean = true) = DailyDataPoint(day, bytes, complete, "Android mobile", 1000)
    @Test fun expiryIncludesTodayRatherThanDividingByZero() { val result = calculate("2026-10-10"); assertEquals(1L, result.daysRemaining); assertEquals(900L, result.dailyBudgetBytes) }
    @Test fun localCalendarDaysDoNotDependOnTwentyFourHourDstArithmetic() {
        val result = DataBudgetPolicy.calculate(LocalDate.of(2026, 3, 7), DataBudgetConfig("2026-03-9"), 900, true, false, emptyList())
        assertEquals(BudgetStatus.NEED_EXPIRY, result.status)
        val valid = DataBudgetPolicy.calculate(LocalDate.of(2026, 3, 7), DataBudgetConfig("2026-03-09"), 900, true, false, emptyList())
        assertEquals(3L, valid.daysRemaining); assertEquals(300L, valid.dailyBudgetBytes)
    }
    @Test fun expiredPeriodIsNotAutomaticallyRechargedOrForecast() { val result = calculate("2026-10-09"); assertEquals(BudgetStatus.EXPIRED, result.status); assertNull(result.dailyBudgetBytes); assertNull(result.forecastDays) }
    @Test fun partialCounterCannotSupportPreciseBudgetOrForecast() { val result = calculate(complete = false); assertEquals(BudgetStatus.PARTIAL, result.status); assertNull(result.dailyBudgetBytes) }
    @Test fun unavailableIsDifferentFromActuallyMeasuredZeroRemaining() { assertEquals(BudgetStatus.UNAVAILABLE, calculate(remaining = null).status); assertEquals(BudgetStatus.EXHAUSTED, calculate(remaining = 0).status) }
    @Test fun unlimitedPreferenceDisablesProjectionWithoutCarrierCertification() { val result = calculate(unlimited = true); assertEquals(BudgetStatus.UNLIMITED, result.status); assertNull(result.forecastDays) }
    @Test fun missingOrInvalidExpiryIsNotGuessedAsThirtyDays() { assertEquals(BudgetStatus.NEED_EXPIRY, calculate(expiry = null).status); assertEquals(BudgetStatus.NEED_EXPIRY, calculate("2026-02-30").status) }
    @Test fun averageUsesOnlyCompletePastDaysNotUnknownPartialOrToday() {
        val result = calculate(history = listOf(point("2026-10-06", 100), point("2026-10-07", 200), point("2026-10-08", 300),
            point("2026-10-09", null), point("2026-10-05", 50_000, false), point("2026-10-10", 99_999)))
        assertEquals(3, result.completeDays); assertEquals(200L, result.averageDailyBytes); assertEquals(5L, result.forecastDays)
    }
    @Test fun fewerThanThreeCompleteDaysCannotForecastRunOut() { val result = calculate(history = listOf(point("2026-10-08"), point("2026-10-09"))); assertNull(result.forecastDays); assertNotNull(result.dailyBudgetBytes) }
    @Test fun measuredZeroDaysAreValidButNotAFabricatedRunOutDate() { val result = calculate(history = listOf(point("2026-10-07", 0), point("2026-10-08", 0), point("2026-10-09", 0))); assertEquals(0L, result.averageDailyBytes); assertNull(result.forecastDays) }
    @Test fun packResetExcludesOlderCyclesFromThePaceEstimate() {
        val result = calculate(history = listOf(point("2026-10-06"), point("2026-10-07"), point("2026-10-08"), point("2026-10-09")), start = LocalDate.of(2026, 10, 9))
        assertEquals(1, result.completeDays); assertNull(result.forecastDays)
    }
    @Test fun historyIsBoundedAndFailedRereadDoesNotEraseCompleteDay() {
        var history = listOf(point("2026-10-09", 500))
        history = DataBudgetPolicy.merge(history, point("2026-10-09", null, false)); assertEquals(500L, history.single().bytes)
        assertEquals(history, DataBudgetPolicy.merge(history, point("bad-date")))
        for (i in 1..120) history = DataBudgetPolicy.merge(history, point(today.minusDays(i.toLong()).toString()))
        assertEquals(90, history.size)
    }
}
