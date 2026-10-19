package com.universalrp.cleansweep.data

import android.content.Context
import android.content.SharedPreferences
import com.universalrp.cleansweep.voice.Announcer
import java.time.LocalDate

/**
 * v2.47: 4G and 5G, counted separately.
 *
 * Why this file exists
 * --------------------
 * Android reports one mobile byte counter. To show "4G 812 MB left · 5G Unlimited" the app
 * samples the cumulative counter (see [com.universalrp.cleansweep.notify.RadioWatchService])
 * and files each delta under whatever radio was active during that sample:
 *
 * ```
 * delta = cumulativeToday − lastCumulativeToday
 * if (radio == NR) fiveGBytesToday += delta else fourGBytesToday += delta
 * ```
 *
 * Whether 5G should be charged at all is an operator question, not a phone question, so the
 * rules below encode the published 2026 terms exactly:
 *
 *  * **Jio** — unlimited True 5G needs a ₹349+ recharge that also gives **2 GB/day**.
 *    Cheap plans (₹666 with 1.5 GB/day, for example) do *not* qualify: 5G spends the quota.
 *  * **Airtel** — unlimited 5G needs an eligible ₹349+ unlimited pack **and** a one-time
 *    claim in the Airtel Thanks app, is capped at **300 GB per 28-day cycle**, and excludes
 *    hotspot sharing.
 *
 * The user's own switch (Auto / On / Off) always wins, because operator terms change.
 */
object NetworkSplit {

    const val PREFS = "cleansweep_net_split"

    /** Count 4G and 5G separately. Off = one combined counter (the old behaviour). */
    const val KEY_SEPARATE = "split_enabled"

    /** Announce 4G ⇄ 5G changes. This is separate from the Wi-Fi ⇄ mobile switch. */
    const val KEY_ANNOUNCE = "radio_change_announce"

    /** "AUTO" | "ON" | "OFF" */
    const val KEY_POLICY = "fiveg_policy"

    /** "JIO" | "AIRTEL" | "OTHER" */
    const val KEY_OPERATOR = "operator"

    /** Plan price in rupees and the daily allowance in MB, used only for the rule check. */
    const val KEY_PRICE_INR = "plan_price_inr"
    const val KEY_DAILY_MB = "plan_daily_mb"

    /** Airtel: claimed in the Thanks app. */
    const val KEY_CLAIMED = "fiveg_claimed"

    /**
     * Announcer volume, 0…1. Lives in the same prefs file as the voice switches so the
     * speaker path can read it without touching DataStore.
     */
    const val KEY_VOLUME_4G5G = "voice_volume"

    // Counters, per local day.
    const val KEY_4G_TODAY = "bytes_4g_today"
    const val KEY_5G_TODAY = "bytes_5g_today"
    const val KEY_DAY = "counter_day"

    /** Cumulative mobile bytes at the last sample, so a delta can be computed. */
    const val KEY_LAST_CUMULATIVE = "last_cumulative"

    const val MB = 1_048_576.0
    const val JIO_MIN_PRICE = 349
    const val JIO_MIN_DAILY_MB = 2.0 * 1024
    const val AIRTEL_MIN_PRICE = 349
    const val AIRTEL_MIN_DAILY_MB = 2.0 * 1024
    const val AIRTEL_FUP_MB = 300.0 * 1024

    data class Status(
        val separate: Boolean,
        val fiveGUnlimited: Boolean,
        val unlimitedReason: String,
        val fourGUsedMb: Double,
        val fiveGUsedMb: Double,
        val dailyLimitMb: Double,
        val fourGLeftMb: Double,
        val fiveGLeftMb: Double,
        val combinedLeftMb: Double,
        val savedBy5GMb: Double,
    )

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // -------------------------------------------------------------- switches
    fun separateEnabled(context: Context) = prefs(context).getBoolean(KEY_SEPARATE, true)

    fun setSeparate(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_SEPARATE, on).apply()
        mirror(context)
    }

    fun announceEnabled(context: Context) = prefs(context).getBoolean(KEY_ANNOUNCE, false)

    fun setAnnounce(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ANNOUNCE, on).apply()
        mirror(context)
    }

    fun policy(context: Context) = prefs(context).getString(KEY_POLICY, "AUTO") ?: "AUTO"

    fun setPolicy(context: Context, value: String) {
        prefs(context).edit().putString(KEY_POLICY, value).apply()
        mirror(context)
    }

    /** The radio split settings are read by the voice path too; keep the plain files in step. */
    private fun mirror(context: Context) {
        val p = prefs(context)
        context.getSharedPreferences(Announcer.PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ANNOUNCE, p.getBoolean(KEY_ANNOUNCE, false))
            .putFloat(KEY_VOLUME_4G5G, p.getFloat(KEY_VOLUME_4G5G, 1f))
            .apply()
    }

    // ---------------------------------------------------------------- rules
    /** True when the operator's published terms make 5G free on this plan. */
    fun qualifies(context: Context): Boolean {
        val p = prefs(context)
        val price = p.getInt(KEY_PRICE_INR, 0)
        val dailyMb = p.getFloat(KEY_DAILY_MB, 0f).toDouble()
        val claimed = p.getBoolean(KEY_CLAIMED, false)
        val operator = p.getString(KEY_OPERATOR, "OTHER") ?: "OTHER"

        return when (operator) {
            "JIO" -> price >= JIO_MIN_PRICE && dailyMb >= JIO_MIN_DAILY_MB
            "AIRTEL" -> price >= AIRTEL_MIN_PRICE && dailyMb >= AIRTEL_MIN_DAILY_MB && claimed
            else -> false
        }
    }

    fun fiveGUnlimited(context: Context): Boolean = when (policy(context)) {
        "ON" -> true
        "OFF" -> false
        else -> qualifies(context)
    }

    fun reason(context: Context): String {
        val p = prefs(context)
        return when (policy(context)) {
            "ON" -> "5G is set to unlimited by you"
            "OFF" -> "5G is set to count towards your data by you"
            else -> when (p.getString(KEY_OPERATOR, "OTHER")) {
                "JIO" -> if (qualifies(context))
                    "Jio plan qualifies — 5G is not counted"
                else
                    "Jio needs ₹349+ with 2 GB/day for unlimited 5G, so 5G counts"
                "AIRTEL" -> when {
                    !p.getBoolean(KEY_CLAIMED, false) ->
                        "Claim unlimited 5G in the Airtel Thanks app"
                    qualifies(context) -> "Airtel plan qualifies — 5G is not counted"
                    else -> "Airtel needs a ₹349+ unlimited pack, so 5G counts"
                }
                else -> "5G usage counts towards your data plan"
            }
        }
    }

    // -------------------------------------------------------------- counters
    fun resetDayIfNeeded(context: Context) {
        val p = prefs(context)
        val today = LocalDate.now().toString()
        if (p.getString(KEY_DAY, "") != today) {
            p.edit()
                .putString(KEY_DAY, today)
                .putLong(KEY_4G_TODAY, 0L)
                .putLong(KEY_5G_TODAY, 0L)
                .putLong(KEY_LAST_CUMULATIVE, -1L)
                .apply()
        }
    }

    /** Adds a sampled delta to the right counter. [is5g] comes from the radio listener. */
    fun addDelta(context: Context, deltaBytes: Long, is5g: Boolean) {
        if (deltaBytes <= 0) return
        val p = prefs(context)
        val key = if (is5g) KEY_5G_TODAY else KEY_4G_TODAY
        p.edit().putLong(key, p.getLong(key, 0L) + deltaBytes).apply()
    }

    fun lastCumulative(context: Context): Long = prefs(context).getLong(KEY_LAST_CUMULATIVE, -1L)

    fun setLastCumulative(context: Context, value: Long) {
        prefs(context).edit().putLong(KEY_LAST_CUMULATIVE, value).apply()
    }

    fun fourGBytes(context: Context): Long = prefs(context).getLong(KEY_4G_TODAY, 0L)

    fun fiveGBytes(context: Context): Long = prefs(context).getLong(KEY_5G_TODAY, 0L)

    /**
     * The figures the mobile screen shows. [dailyLimitMb] is the pack allowance the user
     * already enters in DataUsageTracker (GB × 1024); 0 means "no pack set".
     */
    fun status(context: Context, dailyLimitMb: Double): Status {
        val unlimited = fiveGUnlimited(context)
        val fourG = fourGBytes(context) / MB
        val fiveG = fiveGBytes(context) / MB
        val counted = fourG + if (unlimited) 0.0 else fiveG
        val limit = if (dailyLimitMb > 0) dailyLimitMb else 0.0
        val left = if (limit > 0) (limit - counted).coerceAtLeast(0.0) else 0.0

        return Status(
            separate = separateEnabled(context),
            fiveGUnlimited = unlimited,
            unlimitedReason = reason(context),
            fourGUsedMb = fourG,
            fiveGUsedMb = fiveG,
            dailyLimitMb = limit,
            fourGLeftMb = if (limit > 0) (limit - fourG).coerceAtLeast(0.0) else 0.0,
            fiveGLeftMb = if (unlimited) Double.POSITIVE_INFINITY
            else if (limit > 0) (limit - counted).coerceAtLeast(0.0) else 0.0,
            combinedLeftMb = left,
            savedBy5GMb = if (unlimited) fiveG else 0.0,
        )
    }

    /** Matches the operator apps: MB under a gigabyte, GB above it. */
    fun format(mb: Double): String = when {
        mb.isInfinite() -> "Unlimited"
        mb >= 1024 -> String.format("%.2f GB", mb / 1024.0)
        else -> String.format("%.2f MB", mb)
    }
}
