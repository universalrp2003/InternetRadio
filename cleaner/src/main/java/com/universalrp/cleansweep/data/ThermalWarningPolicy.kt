package com.universalrp.cleansweep.data

import java.util.Locale

/**
 * The 2.41 heat warning.
 *
 * One honest, owner-set threshold instead of scattered magic numbers. At or above
 * [WARN_BATTERY_C] the app says the battery is unusually warm and asks the owner to
 * review charging conditions. It never claims a cause, never calls the phone damaged,
 * and never repeats the same warning more often than [HUSH_MS]: a warning that fires
 * every minute is noise, and noise is not information.
 */
object ThermalWarningPolicy {

    /** Owner-set battery warning threshold (45 °C before 2.41, kept in step in Announcer). */
    const val WARN_BATTERY_C = 44.0f

    /** A CPU zone this hot is worth its own clearly labelled notice (unchanged in 2.41). */
    const val WARN_CPU_C = 75.0f

    /** The same warning is not announced again inside this window (unchanged in 2.41). */
    const val HUSH_MS = 3L * 60L * 60L * 1000L

    enum class Sensor { NONE, BATTERY, CPU }

    data class Decision(val warn: Boolean, val sensor: Sensor, val celsius: Float?)

    /**
     * Battery temperature (broadcast, then the kernel fallback) is preferred; a hot CPU
     * zone is reported separately and never described as a battery temperature. Nothing
     * reported at all means no warning and no invented number.
     */
    fun decide(batteryC: Float?, kernelBatteryC: Float?, cpuC: Float?): Decision {
        val battery = (batteryC ?: kernelBatteryC)?.takeIf { it.isFinite() }
        if (battery != null && battery >= WARN_BATTERY_C) return Decision(true, Sensor.BATTERY, battery)
        val cpu = cpuC?.takeIf { it.isFinite() }
        if (cpu != null && cpu >= WARN_CPU_C) return Decision(true, Sensor.CPU, cpu)
        return Decision(false, Sensor.NONE, null)
    }

    /** "44.1 °C", or null when the phone reported no temperature at all. */
    fun celsiusText(celsius: Float?): String? =
        celsius?.takeIf { it.isFinite() }?.let { "%.1f °C".format(Locale.US, it) }

    /**
     * True when this warning may be announced again. A missing/zero stamp announces once;
     * a clock that moved backwards must not silence the warning forever.
     */
    fun shouldAnnounce(lastAnnouncedMs: Long?, nowMs: Long, hushMs: Long = HUSH_MS): Boolean {
        if (lastAnnouncedMs == null || lastAnnouncedMs <= 0L) return true
        if (nowMs < lastAnnouncedMs) return true
        return nowMs - lastAnnouncedMs >= hushMs.coerceAtLeast(0L)
    }
}
