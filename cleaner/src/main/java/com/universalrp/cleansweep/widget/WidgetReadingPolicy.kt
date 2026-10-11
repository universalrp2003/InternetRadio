package com.universalrp.cleansweep.widget

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.log10

/** Display precision is NOT sensor accuracy. Never derive fractional SoC from an estimated capacity. */
object WidgetReadingPolicy {
    enum class Mode { BOTH, WATTS, PERCENT;
        fun next(): Mode = entries[(ordinal + 1) % entries.size]
        companion object { fun parse(value: String?): Mode = entries.firstOrNull { it.name == value } ?: BOTH }
    }

    fun percent(wholePercent: Int?, reportedLevel: Int? = null, reportedScale: Int? = null): String {
        if (reportedLevel != null && reportedScale != null && reportedScale > 0 && reportedLevel in 0..reportedScale) {
            // Only additional resolution actually present in the battery broadcast is shown.
            val step = 100.0 / reportedScale
            val digits = ceil(-log10(step)).toInt().coerceIn(0, 5)
            return String.format(Locale.US, "%.${digits}f%%", reportedLevel.toDouble() * 100.0 / reportedScale)
        }
        return wholePercent?.takeIf { it in 0..100 }?.let { "$it%" } ?: "— %"
    }

    fun watts(powerConnected: Boolean, activelyCharging: Boolean, value: Float?): String =
        if (!powerConnected || !activelyCharging) "— W" else value?.takeIf { it.isFinite() && it in 0.0f..150.0f }
            ?.let { String.format(Locale.US, "%.2f W", it) } ?: "— W"

    fun temperature(value: Float?): String = value?.takeIf { it.isFinite() && it in -30f..90f }
        ?.let { String.format(Locale.US, "%.1f °C", it) } ?: "— °C"
    fun current(value: Float?): String = value?.takeIf { it.isFinite() && it in -20f..20f }
        ?.let { String.format(Locale.US, "%.0f mA", it * 1000) } ?: "— mA"
    fun voltage(value: Float?): String = value?.takeIf { it.isFinite() && it in 1f..20f }
        ?.let { String.format(Locale.US, "%.3f V", it) } ?: "— V"
}
