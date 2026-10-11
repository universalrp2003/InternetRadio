package com.universalrp.cleansweep.data

/** Android public BatteryManager current properties are microamps; magnitude is NOT evidence of different units. */
object BatteryValuePolicy {
    fun microAmpsToAmps(value: Int?): Float? = value?.takeIf { it != Int.MIN_VALUE && it.toLong() in -20_000_000L..20_000_000L }
        ?.let { it / 1_000_000f }

    /** Existing integer-history schema must not turn a fractional 20.x/80.x sample into an exact 20/80 observation. */
    fun exactWholePercent(whole: Int, level: Int?, scale: Int?): Int? {
        if (whole !in 0..100) return null
        if (level == null || scale == null) return whole // Android's integer-capacity property / legacy integer samples.
        if (scale <= 0 || level !in 0..scale) return null
        val scaled = level.toLong() * 100
        return if (scaled % scale == 0L && scaled / scale == whole.toLong()) whole else null
    }
}
