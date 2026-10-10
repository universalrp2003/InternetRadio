package com.universalrp.cleansweep.data

/** Samples use elapsed time for durations; wall-clock adjustments must not change the result. */
data class ChargeSample(
    val atMs: Long,
    val elapsedMs: Long,
    val connected: Boolean,
    val percent: Int?,
    val watts: Double?,
    val temperatureC: Double?,
    val connectionEvent: Boolean = false,
    val disconnectionEvent: Boolean = false,
    val bootCount: Int? = null,
)

data class ChargeSession(
    val id: String,
    val startedAtMs: Long,
    val lastAtMs: Long,
    val startedElapsedMs: Long,
    val lastElapsedMs: Long,
    val endedAtMs: Long? = null,
    val startPercent: Int? = null,
    val endPercent: Int? = null,
    val samples: List<ChargeSample> = emptyList(),
    val lastSample: ChargeSample? = null,
    val observedFromPlug: Boolean = false,
    val endObserved: Boolean = false,
    val interrupted: Boolean = false,
    val measuredPowerMs: Long = 0,
    val measuredEnergyWh: Double = 0.0,
    val peakWatts: Double? = null,
    val maxTemperatureC: Double? = null,
    val atTwentyElapsedMs: Long? = null,
    val atEightyElapsedMs: Long? = null,
    val thresholdGap: Boolean = false,
    val bootCount: Int? = null,
) {
    val durationMs: Long get() = (lastElapsedMs - startedElapsedMs).coerceAtLeast(0)
    val averageWatts: Double? get() = if (measuredPowerMs > 0)
        measuredEnergyWh / (measuredPowerMs / 3_600_000.0) else null
    val twentyToEightyMs: Long? get() = if (!thresholdGap && atTwentyElapsedMs != null && atEightyElapsedMs != null)
        (atEightyElapsedMs - atTwentyElapsedMs).takeIf { it >= 0 } else null
    val partial: Boolean get() = !observedFromPlug || !endObserved || interrupted
}

data class ChargeHistoryState(val active: ChargeSession? = null, val sessions: List<ChargeSession> = emptyList())

object ChargeHistoryPolicy {
    const val MAX_GAP_MS = 120_000L
    const val MAX_POINTS = 240
    const val MAX_SESSIONS = 30
    const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000

    fun prune(state: ChargeHistoryState, nowMs: Long): ChargeHistoryState = state.copy(
        active = state.active?.takeIf { it.lastAtMs >= nowMs - RETENTION_MS },
        sessions = state.sessions.filter { (it.endedAtMs ?: it.lastAtMs) >= nowMs - RETENTION_MS }.takeLast(MAX_SESSIONS),
    )

    fun observe(state: ChargeHistoryState, input: ChargeSample): ChargeHistoryState {
        if (input.atMs <= 0 || input.elapsedMs < 0) return state
        val sample = input.copy(
            percent = input.percent?.takeIf { it in 0..100 },
            watts = input.watts?.takeIf { it.isFinite() && it in 0.0..150.0 },
            temperatureC = input.temperatureC?.takeIf { it.isFinite() && it in -30.0..90.0 },
        )
        var active = state.active
        var sessions = state.sessions.filter { (it.endedAtMs ?: it.lastAtMs) >= input.atMs - RETENTION_MS }
        var disrupted = false
        if (active != null && (sample.elapsedMs < active.lastElapsedMs || sample.elapsedMs - active.lastElapsedMs > MAX_GAP_MS ||
            (sample.bootCount != null && active.bootCount != null && sample.bootCount != active.bootCount))) {
            // Reboot OR a missed interval may hide unplug/replug. Close at the last observation.
            // Do not join boots or fill the gap, even when the new boot's uptime overtook the old one.
            sessions = sessions + active.copy(endedAtMs = active.lastAtMs, interrupted = true,
                endObserved = false, thresholdGap = active.thresholdGap || active.atEightyElapsedMs == null)
            active = null; disrupted = true
        }
        if (active != null && sample.connected && sample.elapsedMs == active.lastElapsedMs) return state
        if (!sample.connected) {
            if (active != null) {
                val gap = sample.elapsedMs - active.lastElapsedMs
                val knownEnd = sample.disconnectionEvent || gap <= MAX_GAP_MS
                sessions = sessions + active.copy(
                    endedAtMs = if (knownEnd) sample.atMs else active.lastAtMs,
                    lastAtMs = if (knownEnd) sample.atMs else active.lastAtMs,
                    lastElapsedMs = if (knownEnd) sample.elapsedMs else active.lastElapsedMs,
                    endPercent = if (knownEnd) sample.percent ?: active.endPercent else active.endPercent,
                    endObserved = knownEnd,
                    interrupted = active.interrupted || gap > MAX_GAP_MS,
                    thresholdGap = active.thresholdGap || gap > MAX_GAP_MS,
                )
            }
            return ChargeHistoryState(sessions = sessions.takeLast(MAX_SESSIONS))
        }
        if (active == null) {
            active = ChargeSession(
                id = "${sample.atMs}-${sample.elapsedMs}", startedAtMs = sample.atMs, lastAtMs = sample.atMs,
                startedElapsedMs = sample.elapsedMs, lastElapsedMs = sample.elapsedMs,
                startPercent = sample.percent, endPercent = sample.percent, samples = listOf(sample), lastSample = sample,
                observedFromPlug = sample.connectionEvent, interrupted = disrupted, bootCount = sample.bootCount,
                peakWatts = sample.watts, maxTemperatureC = sample.temperatureC,
                atTwentyElapsedMs = sample.elapsedMs.takeIf { sample.percent == 20 },
            )
        } else {
            val delta = sample.elapsedMs - active.lastElapsedMs
            val previous = active.lastSample ?: active.samples.lastOrNull()
            val gap = delta > MAX_GAP_MS
            val validPower = !gap && previous?.watts != null && sample.watts != null
            val extraEnergy = if (validPower) (previous!!.watts!! + sample.watts!!) / 2.0 * delta / 3_600_000.0 else 0.0
            val points = if (sample.elapsedMs - (active.samples.lastOrNull()?.elapsedMs ?: 0L) >= 30_000 || sample.percent != active.endPercent || sample.connectionEvent)
                (active.samples + sample).takeLast(MAX_POINTS) else active.samples
            val at20 = active.atTwentyElapsedMs ?: sample.elapsedMs.takeIf {
                sample.percent == 20 && !gap
            }
            val at80 = active.atEightyElapsedMs ?: sample.elapsedMs.takeIf {
                at20 != null && active.endPercent != null && active.endPercent < 80 && sample.percent == 80 && !gap
            }
            active = active.copy(
                lastAtMs = sample.atMs, lastElapsedMs = sample.elapsedMs, endPercent = sample.percent ?: active.endPercent,
                samples = points, lastSample = sample, interrupted = active.interrupted || gap,
                measuredPowerMs = active.measuredPowerMs + if (validPower) delta else 0,
                measuredEnergyWh = active.measuredEnergyWh + extraEnergy,
                peakWatts = listOfNotNull(active.peakWatts, sample.watts).maxOrNull(),
                maxTemperatureC = listOfNotNull(active.maxTemperatureC, sample.temperatureC).maxOrNull(),
                atTwentyElapsedMs = at20, atEightyElapsedMs = at80,
                thresholdGap = active.thresholdGap || (gap && (active.atTwentyElapsedMs != null || (active.endPercent ?: 101) < 20)),
            )
        }
        return ChargeHistoryState(active, sessions.takeLast(MAX_SESSIONS))
    }
}
