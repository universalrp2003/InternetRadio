package com.universalrp.cleansweep.data

/** Android public BatteryManager current properties are microamps; magnitude is NOT evidence of different units. */
object BatteryValuePolicy {
    fun microAmpsToAmps(value: Int?): Float? = value?.takeIf { it != Int.MIN_VALUE && it.toLong() in -20_000_000L..20_000_000L }
        ?.let { it / 1_000_000f }

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

        /** `BatteryManager.EXTRA_CURRENT` from the `ACTION_BATTERY_CHANGED` broadcast. */
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
     * `-0.00 A` while the same phone's charge history recorded real 4.6–4.8 W sessions,
     * because that history was written from a broadcast-derived sample.
     *
     * Order of trust:
     *  1. a real **non-zero** property value — Android's documented counter;
     *  2. a real **non-zero** broadcast value — the fallback that rescues those kernels;
     *  3. a broadcast **zero** — a genuine trickle/full measurement from the same healthd
     *     path that also supplies the voltage and temperature shown next to it;
     *  4. nothing. A bare property `0` with no broadcast corroboration is *not* reported as
     *     0 A, because on those kernels `0` means "unsupported" — and an unsupported sensor
     *     must never be rendered as a measured `0.00 W`.
     *
     * Both inputs go through [microAmpsToAmps], so plausibility is bounded once, here.
     */
    fun currentReading(propertyMicroAmps: Int?, broadcastMicroAmps: Int?): CurrentReading {
        val fromProperty = microAmpsToAmps(propertyMicroAmps)
        val fromBroadcast = microAmpsToAmps(broadcastMicroAmps)
        return when {
            fromProperty != null && fromProperty != 0f -> CurrentReading(fromProperty, CurrentSource.PROPERTY)
            fromBroadcast != null && fromBroadcast != 0f -> CurrentReading(fromBroadcast, CurrentSource.BROADCAST)
            fromBroadcast != null -> CurrentReading(fromBroadcast, CurrentSource.BROADCAST)
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
