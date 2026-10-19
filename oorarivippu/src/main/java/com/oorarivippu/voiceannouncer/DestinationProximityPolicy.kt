package com.oorarivippu.voiceannouncer

/** Progressively emits one nearest upcoming distance milestone and never repeats it. */
internal object DestinationProximityPolicy {
    const val TEN_KILOMETERS = 10_000f
    const val FIVE_KILOMETERS = 5_000f
    const val TWO_KILOMETERS = 2_000f
    const val ARRIVAL_RADIUS_METERS = 500f

    private const val TEN_KM_BIT = 1
    private const val FIVE_KM_BIT = 1 shl 1
    private const val TWO_KM_BIT = 1 shl 2
    private const val ARRIVAL_BIT = 1 shl 3
    private const val ALL_BITS = TEN_KM_BIT or FIVE_KM_BIT or TWO_KM_BIT or ARRIVAL_BIT

    data class Result(
        val updatedMask: Int,
        /** Null means no new audible milestone; ARRIVAL_RADIUS_METERS means arrival. */
        val alertDistanceMeters: Int?
    )

    fun evaluate(
        currentDistanceMeters: Float,
        previousDistanceMeters: Float?,
        announcedMask: Int,
    ): Result {
        val distance = currentDistanceMeters.takeIf { it.isFinite() && it >= 0f } ?: return Result(announcedMask, null)
        var mask = announcedMask and ALL_BITS

        // On service start/restart, treat already-passed approach distances as missed rather
        // than firing a burst of stale alerts. Arrival is still useful when starting nearby.
        if (previousDistanceMeters == null) {
            if (distance <= TEN_KILOMETERS) mask = mask or TEN_KM_BIT
            if (distance <= FIVE_KILOMETERS) mask = mask or FIVE_KM_BIT
            if (distance <= TWO_KILOMETERS) mask = mask or TWO_KM_BIT
            if (distance <= ARRIVAL_RADIUS_METERS && (mask and ARRIVAL_BIT) == 0) {
                return Result(mask or ARRIVAL_BIT, ARRIVAL_RADIUS_METERS.toInt())
            }
            return Result(mask, null)
        }

        val previous = previousDistanceMeters.takeIf { it.isFinite() && it >= 0f } ?: return evaluate(
            currentDistanceMeters = distance,
            previousDistanceMeters = null,
            announcedMask = mask
        )
        if (distance <= ARRIVAL_RADIUS_METERS && (mask and ARRIVAL_BIT) == 0) {
            return Result(mask or ALL_BITS, ARRIVAL_RADIUS_METERS.toInt())
        }

        val crossed = mutableListOf<Float>()
        fun checkCrossing(threshold: Float, bit: Int) {
            if ((mask and bit) == 0 && previous > threshold && distance <= threshold) {
                mask = mask or bit
                crossed += threshold
            }
        }
        checkCrossing(TEN_KILOMETERS, TEN_KM_BIT)
        checkCrossing(FIVE_KILOMETERS, FIVE_KM_BIT)
        checkCrossing(TWO_KILOMETERS, TWO_KM_BIT)

        // If a long GPS gap skips multiple milestones, speak only the closest one.
        val nearest = crossed.minOrNull()
        return Result(mask, nearest?.toInt())
    }
}
