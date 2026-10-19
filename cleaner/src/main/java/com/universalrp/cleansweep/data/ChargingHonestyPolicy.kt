package com.universalrp.cleansweep.data

import java.util.Locale
import kotlin.math.abs

/**
 * Honesty rules for the charging numbers shown on the battery card.
 *
 * Three different facts exist and the app must never blend them:
 *
 *  1. what the **charger adapter** outputs — an app cannot measure this at all;
 *  2. what the **phone itself** draws while the screen and processor run;
 *  3. what actually reaches the **battery** — the kernel's V_batt × I_batt, the only
 *     charging number Android exposes to an app.
 *
 * Every function below describes fact 3, and only when the phone really reports it. A
 * missing sensor stays "not reported" instead of becoming 0 W, a magnitude is never
 * relabelled as adapter output, and [screenNote] explains why the same cable can show
 * different battery-side watts with nothing wrong.
 */
object ChargingHonestyPolicy {

    /** Kernel spikes above this are not a charging reading; the app shows nothing instead. */
    const val MAX_PLAUSIBLE_WATTS = 150f

    /** Battery-side current beyond this is a sensor artefact, not a charging current. */
    const val MAX_PLAUSIBLE_AMPS = 20f

    /** Values below display precision are withheld instead of being rounded into fake 0.00 W. */
    const val MIN_DISPLAYABLE_WATTS = 0.05f

    /** Battery voltage below this is a missing/unusable sensor value, not a real reading. */
    private const val MIN_PLAUSIBLE_VOLTS = 2f

    /** A phone battery voltage above this is a sensor artefact. */
    private const val MAX_PLAUSIBLE_VOLTS = 12f

    enum class ScreenNote { NOT_CHARGING, SCREEN_ON_DRAWS_POWER, SCREEN_OFF_LOW_DRAW }

    /**
     * Battery-side charging power = measured battery voltage × measured battery current,
     * and only while current actually flows into the battery. Never the adapter's rating,
     * never a value derived from the battery percentage or from a nominal 5 V.
     */
    fun validatedBatteryVoltage(voltageV: Float?): Float? =
        voltageV?.takeIf { it.isFinite() && it in MIN_PLAUSIBLE_VOLTS..MAX_PLAUSIBLE_VOLTS }

    /** Reject open/short-circuit sentinels and absurd battery readings; zero °C is valid. */
    fun validatedBatteryTemperature(temperatureC: Float?): Float? =
        temperatureC?.takeIf { it.isFinite() && it in -30f..90f }

    fun batterySideWatts(
        charging: Boolean,
        powerConnected: Boolean,
        voltageV: Float?,
        currentA: Float?,
    ): Float? {
        if (!charging || !powerConnected) return null
        val volts = validatedBatteryVoltage(voltageV) ?: return null
        val amps = currentA?.takeIf { it.isFinite() } ?: return null
        val watts = volts * abs(amps)
        return watts.takeIf { it.isFinite() && it > MIN_DISPLAYABLE_WATTS && it <= MAX_PLAUSIBLE_WATTS }
    }

    /**
     * The battery-side current that may be shown, always as a magnitude (the sign only
     * means "in" or "out", never a different unit), or null when the phone does not
     * report a trustworthy value.
     */
    fun batterySideAmps(currentA: Float?): Float? =
        currentA?.takeIf { it.isFinite() && abs(it) <= MAX_PLAUSIBLE_AMPS }?.let { abs(it) }

    /** "8.42 W", or null — a missing reading is never rendered as "0.00 W". */
    fun wattsText(watts: Float?): String? =
        watts?.takeIf { it.isFinite() && it > MIN_DISPLAYABLE_WATTS && it <= MAX_PLAUSIBLE_WATTS }
            ?.let { "%.2f W".format(Locale.US, it) }

    fun ampsText(amps: Float?): String? =
        amps?.takeIf { it.isFinite() && it >= 0f }?.let { "%.2f A".format(Locale.US, it) }

    fun voltsText(volts: Float?): String? =
        validatedBatteryVoltage(volts)?.let { "%.2f V".format(Locale.US, it) }

    fun temperatureText(temperatureC: Float?): String? =
        validatedBatteryTemperature(temperatureC)?.let { "%.1f °C".format(Locale.US, it) }

    /** True only when a real measured value exists; used to choose the "measured" wording. */
    fun isMeasured(watts: Float?): Boolean =
        watts != null && watts.isFinite() && watts > MIN_DISPLAYABLE_WATTS && watts <= MAX_PLAUSIBLE_WATTS

    /**
     * Which screen-state note applies. Screen on/off changes how much power the phone
     * itself consumes; it does not change what the charger delivers, so this is a
     * measurement note and never a fault or a promise.
     */
    fun screenNote(powerConnected: Boolean, screenOn: Boolean): ScreenNote = when {
        !powerConnected -> ScreenNote.NOT_CHARGING
        screenOn -> ScreenNote.SCREEN_ON_DRAWS_POWER
        else -> ScreenNote.SCREEN_OFF_LOW_DRAW
    }
}
