package com.universalrp.cleansweep.data

import android.os.SystemClock
import kotlin.math.roundToInt

/** A battery percentage observation paired with monotonic elapsed-realtime time. */
data class ChargingObservationPoint(val elapsedMs: Long, val percent: Double)

data class ChargingTimeEstimate(val minutesToFull: Int)

/**
 * Time-to-full is estimated only from a recent, observed rise in the battery's own state of
 * charge. A single current/watt reading or a guessed pack capacity is not enough evidence.
 */
object ChargingEtaPolicy {
    const val MIN_OBSERVATION_SPAN_MS = 5L * 60L * 1000L
    const val MAX_OBSERVATION_SPAN_MS = 20L * 60L * 1000L
    const val MAX_POINT_GAP_MS = 2L * 60L * 1000L
    const val MIN_LEVEL_GAIN_PERCENT = 2.0
    const val MAX_ESTIMATE_MINUTES = 14 * 60
    const val MIN_POINTS = 3

    fun estimate(
        observations: List<ChargingObservationPoint>,
        nowElapsedMs: Long,
        currentPercent: Double?,
        powerConnected: Boolean,
        charging: Boolean,
    ): ChargingTimeEstimate? {
        if (!powerConnected || !charging || currentPercent == null || !currentPercent.isFinite() ||
            currentPercent !in 0.0..99.5 || nowElapsedMs < 0
        ) return null

        val recent = observations.asSequence()
            .filter { it.elapsedMs <= nowElapsedMs && nowElapsedMs - it.elapsedMs <= MAX_OBSERVATION_SPAN_MS }
            .filter { it.percent.isFinite() && it.percent in 0.0..100.0 }
            .sortedBy { it.elapsedMs }
            .distinctBy { it.elapsedMs }
            .toList()
        if (recent.size < MIN_POINTS) return null

        val first = recent.first()
        val last = recent.last()
        val spanMs = last.elapsedMs - first.elapsedMs
        if (spanMs !in MIN_OBSERVATION_SPAN_MS..MAX_OBSERVATION_SPAN_MS ||
            nowElapsedMs - last.elapsedMs > MAX_POINT_GAP_MS ||
            last.percent < currentPercent - 1.0 || currentPercent - last.percent > 2.0
        ) return null
        if (recent.zipWithNext().any { (before, after) ->
                after.elapsedMs - before.elapsedMs > MAX_POINT_GAP_MS || after.percent < before.percent - 0.25
            }
        ) return null

        val gain = last.percent - first.percent
        if (gain < MIN_LEVEL_GAIN_PERCENT) return null
        val percentPerMinute = gain / (spanMs / 60_000.0)
        if (!percentPerMinute.isFinite() || percentPerMinute <= 0.0) return null
        val rawMinutes = (100.0 - currentPercent) / percentPerMinute
        if (!rawMinutes.isFinite() || rawMinutes <= 0.0 || rawMinutes > MAX_ESTIMATE_MINUTES) return null
        return ChargingTimeEstimate(rawMinutes.roundToInt().coerceIn(1, MAX_ESTIMATE_MINUTES))
    }
}

/** In-memory observations only: nothing is saved, backed up, or uploaded by this tracker. */
class ChargingEtaTracker {
    private val observations = ArrayDeque<ChargingObservationPoint>()

    @Synchronized
    fun observe(
        elapsedMs: Long,
        percent: Double?,
        powerConnected: Boolean,
        charging: Boolean,
    ): ChargingTimeEstimate? {
        if (!powerConnected || !charging || percent == null || !percent.isFinite() || percent !in 0.0..100.0 || elapsedMs < 0) {
            observations.clear()
            return null
        }
        val previous = observations.lastOrNull()
        if (previous != null && (elapsedMs <= previous.elapsedMs ||
                elapsedMs - previous.elapsedMs > ChargingEtaPolicy.MAX_POINT_GAP_MS ||
                percent < previous.percent - 0.25
            )
        ) observations.clear()

        val last = observations.lastOrNull()
        if (last == null || elapsedMs - last.elapsedMs >= 30_000L || kotlin.math.abs(percent - last.percent) >= 0.25) {
            observations.addLast(ChargingObservationPoint(elapsedMs, percent))
        }
        while (observations.isNotEmpty() && elapsedMs - observations.first().elapsedMs > ChargingEtaPolicy.MAX_OBSERVATION_SPAN_MS) {
            observations.removeFirst()
        }
        while (observations.size > 48) observations.removeFirst()
        return ChargingEtaPolicy.estimate(observations.toList(), elapsedMs, percent, powerConnected, charging)
    }

    @Synchronized
    fun estimate(
        elapsedMs: Long,
        percent: Double?,
        powerConnected: Boolean,
        charging: Boolean,
    ): ChargingTimeEstimate? = ChargingEtaPolicy.estimate(
        observations = observations.toList(),
        nowElapsedMs = elapsedMs,
        currentPercent = percent,
        powerConnected = powerConnected,
        charging = charging,
    )

    @Synchronized
    fun clear() = observations.clear()
}

/** Process-local live charging estimator. BatteryReader calls [observe] on each fresh sample. */
object LiveChargingEta {
    private val tracker = ChargingEtaTracker()

    fun observe(battery: BatteryReading, elapsedMs: Long = SystemClock.elapsedRealtime()) {
        tracker.observe(
            elapsedMs = elapsedMs,
            percent = battery.observedStateOfChargePercent(),
            powerConnected = battery.powerConnected,
            charging = battery.charging,
        )
    }

    fun estimate(battery: BatteryReading, elapsedMs: Long = SystemClock.elapsedRealtime()): ChargingTimeEstimate? =
        tracker.estimate(
            elapsedMs = elapsedMs,
            percent = battery.observedStateOfChargePercent(),
            powerConnected = battery.powerConnected,
            charging = battery.charging,
        )

    private fun BatteryReading.observedStateOfChargePercent(): Double? {
        val level = reportedLevel
        val scale = reportedScale
        if (level != null && scale != null && scale > 0 && level in 0..scale) {
            return (level.toDouble() * 100.0 / scale).takeIf { it.isFinite() && it in 0.0..100.0 }
        }
        return percent.toDouble().takeIf { percent in 0..100 }
    }
}
