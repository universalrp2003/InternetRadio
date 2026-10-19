package com.oorarivippu.voiceannouncer

import android.location.Location
import kotlin.math.max

/**
 * Detects whether the user is actively traveling (e.g., on a bike, scooter, bus, or car)
 * versus staying in the same routine place or experiencing stationary GPS drift.
 */
class TravelStateDetector {

    companion object {
        // 10 km/h (~2.78 m/s) reliably distinguishes bike/car travel from walking/indoor GPS drift
        const val VEHICLE_SPEED_THRESHOLD_KMH = 10.0f
        // Keep active travel state for 3 minutes after a red-light or brief traffic stop
        const val VEHICLE_MOTION_GRACE_PERIOD_MS = 3 * 60 * 1000L
    }

    private var lastFixLocation: Location? = null
    private var lastAnnouncedLocation: Location? = null
    private var initialMovementAnchor: Location? = null
    private var lastVehicleSpeedTimestampMs: Long = 0L

    data class MotionEvaluation(
        val currentSpeedKmh: Float,
        val distanceFromLastAnnouncedMeters: Float,
        val isTravelingByVehicleOrMovedFar: Boolean
    )

    fun evaluate(
        newLocation: Location,
        minDistanceMeters: Int,
        bikeCarDetectionEnabled: Boolean
    ): MotionEvaluation {
        val nowMs = System.currentTimeMillis()

        // 1. Compute speed in km/h from GPS Doppler speed or fallback delta distance/time
        var speedMps = if (newLocation.hasSpeed()) newLocation.speed else 0f
        val prevLoc = lastFixLocation
        if (speedMps <= 0.2f && prevLoc != null) {
            val dtSeconds = max(1L, (newLocation.time - prevLoc.time) / 1000L)
            val distMeters = newLocation.distanceTo(prevLoc)
            // Ignore unrealistic GPS jumps (> 55 m/s = ~200 km/h)
            val computedMps = distMeters / dtSeconds.toFloat()
            if (computedMps in 0.5f..55.0f && newLocation.accuracy <= 40f) {
                speedMps = computedMps
            }
        }
        lastFixLocation = newLocation

        val speedKmh = speedMps * 3.6f
        if (speedKmh >= VEHICLE_SPEED_THRESHOLD_KMH) {
            lastVehicleSpeedTimestampMs = nowMs
        }

        // Before the first announcement, establish a movement baseline instead of treating
        // "no last announced place" as proof that the user has already travelled far.
        val anchor = lastAnnouncedLocation ?: initialMovementAnchor
        if (anchor == null) initialMovementAnchor = Location(newLocation)
        val distanceFromAnchor = anchor?.distanceTo(newLocation) ?: 0f

        // 3. Determine whether announcement is allowed
        val recentlyInVehicleMotion = (nowMs - lastVehicleSpeedTimestampMs) <= VEHICLE_MOTION_GRACE_PERIOD_MS
        val movedBeyondThreshold = anchor != null && distanceFromAnchor >= minDistanceMeters.toFloat()

        val isTraveling = if (!bikeCarDetectionEnabled) {
            movedBeyondThreshold
        } else {
            // When Bike/Car mode is ON, require a real displacement from the initial or last-
            // announced location; speed alone must not trigger a place announcement at startup.
            (recentlyInVehicleMotion && anchor != null && distanceFromAnchor >= 200f) ||
                movedBeyondThreshold
        }

        return MotionEvaluation(
            currentSpeedKmh = speedKmh,
            distanceFromLastAnnouncedMeters = distanceFromAnchor,
            isTravelingByVehicleOrMovedFar = isTraveling
        )
    }

    fun markPlaceAnnounced(location: Location) {
        lastAnnouncedLocation = Location(location)
    }

    fun reset() {
        lastFixLocation = null
        lastAnnouncedLocation = null
        initialMovementAnchor = null
        lastVehicleSpeedTimestampMs = 0L
    }
}
