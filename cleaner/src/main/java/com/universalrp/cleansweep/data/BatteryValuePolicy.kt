package com.universalrp.cleansweep.data

/** Android public BatteryManager current properties are microamps; magnitude is NOT evidence of different units. */
object BatteryValuePolicy {
    fun microAmpsToAmps(value: Int?): Float? = value?.takeIf { it != Int.MIN_VALUE && it.toLong() in -20_000_000L..20_000_000L }
        ?.let { it / 1_000_000f }

    /** ACTION_BATTERY_CHANGED voltage is millivolts; reject sentinels and implausible values. */
    fun voltageMillivoltsToVolts(value: Int?): Float? = value
        ?.takeIf { it in 2_000..12_000 }
        ?.div(1_000f)

    /** ACTION_BATTERY_CHANGED temperature is tenths of a degree Celsius; zero is valid. */
    fun temperatureTenthsToCelsius(value: Int?): Float? = value
        ?.takeIf { it in -300..900 }
        ?.div(10f)

    /**
     * Which Android plumbing a usable battery-current reading came from.
     *
     * Both sources report **microamps**, so the unit is never inferred from the magnitude —
     * only from which API supplied the number. [NONE] means the phone reported nothing
     * trustworthy, which the UI must show as "not reported" and never as a measured 0.
     */
    enum class CurrentSource {
        /** `BatteryManager.BATTERY_PROPERTY_CURRENT_NOW` — Android's documented counter. */
        PROPERTY,

        /**
         * The `current_now` / `current_average` extra of the `ACTION_BATTERY_CHANGED`
         * broadcast — public as `BatteryManager.EXTRA_CURRENT_NOW` only from API 34, but
         * present in the broadcast long before minSdk 26.
         */
        BROADCAST,

        /** Neither source produced a plausible value. */
        NONE,
    }

    /** A current reading plus an honest record of where it came from. */
    data class CurrentReading(val amps: Float?, val source: CurrentSource) {
        val isMeasured: Boolean get() = amps != null
    }

    /**
     * The one place that decides which battery-current reading the app may use.
     *
     * Older and OEM kernels answer the *property* with `0` or "unsupported" while the
     * *broadcast extra* still carries the real charge current — the OPPO A3s (Android
     * 8.1 / ColorOS) regression the owner reported in 2.42: the card showed `0.00 W` /
     * `-0.00 A` while the same phone's charge history recorded real 4.6–4.8 W sessions.
     *
     * This is a fallback, not a promise: a kernel that answers zero (or nothing) through
     * *both* paths has no current to report, and the answer is then [CurrentSource.NONE] —
     * the UI says "not reported", which is strictly better than printing a measured-looking
     * zero. The owner's OPPO A3s is exactly such a kernel; see the 2.43 note above.
     *
     * Order of trust:
     *  1. a real **non-zero** property value — Android's documented counter;
     *  2. a real **non-zero** broadcast value — the fallback that rescues those kernels;
     *  3. otherwise nothing.
     *
     * **A zero is never a measurement** (2.43). 2.42 briefly treated a broadcast zero as a
     * genuine trickle reading; on the owner's OPPO A3s the kernel answers zero through *both*
     * paths, which is indistinguishable from an unsupported sensor, and the card printed the
     * same fake `0.00 W` the owner reported. A phone whose counter really works reports a
     * trickle as a small non-zero value, so nothing honest is lost by refusing zero: an
     * unsupported sensor, and a kernel that cannot tell trickle from unsupported, both become
     * "not reported" instead of a measured-looking `0.00 W`.
     *
     * Both inputs go through [microAmpsToAmps], so plausibility is bounded once, here.
     */
    fun currentReading(propertyMicroAmps: Int?, broadcastMicroAmps: Int?): CurrentReading {
        val fromProperty = microAmpsToAmps(propertyMicroAmps)
        val fromBroadcast = microAmpsToAmps(broadcastMicroAmps)
        return when {
            fromProperty != null && fromProperty != 0f -> CurrentReading(fromProperty, CurrentSource.PROPERTY)
            fromBroadcast != null && fromBroadcast != 0f -> CurrentReading(fromBroadcast, CurrentSource.BROADCAST)
            else -> CurrentReading(null, CurrentSource.NONE)
        }
    }

    /** True when the reading only exists because the broadcast extra rescued it. */
    fun needsBroadcastFallback(propertyMicroAmps: Int?, broadcastMicroAmps: Int?): Boolean =
        currentReading(propertyMicroAmps, broadcastMicroAmps).source == CurrentSource.BROADCAST

    /** Existing integer-history schema must not turn a fractional 20.x/80.x sample into an exact 20/80 observation. */
    fun exactWholePercent(whole: Int, level: Int?, scale: Int?): Int? {
        if (whole !in 0..100) return null
        if (level == null || scale == null) return whole // Android's integer-capacity property / legacy integer samples.
        if (scale <= 0 || level !in 0..scale) return null
        val scaled = level.toLong() * 100
        return if (scaled % scale == 0L && scaled / scale == whole.toLong()) whole else null
    }
}
