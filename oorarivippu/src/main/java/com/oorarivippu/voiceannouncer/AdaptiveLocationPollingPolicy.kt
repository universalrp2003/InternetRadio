package com.oorarivippu.voiceannouncer

/**
 * A conservative polling schedule: low-rate checks at rest, medium in ordinary traffic,
 * and five-second checks at highway speed. This is a policy, not a measured battery-saving
 * guarantee; OEM location batching can change the observed cadence.
 */
internal class AdaptiveLocationPollingPolicy {
    enum class Profile { STATIONARY, CITY_TRAFFIC, HIGHWAY }

    companion object {
        const val STATIONARY_INTERVAL_MS = 45_000L
        const val CITY_INTERVAL_MS = 15_000L
        const val HIGHWAY_INTERVAL_MS = 5_000L

        private const val ENTER_CITY_SPEED_KMH = 4f
        private const val ENTER_HIGHWAY_SPEED_KMH = 45f
        private const val LEAVE_HIGHWAY_SPEED_KMH = 35f
        private const val ENTER_STATIONARY_SPEED_KMH = 1.5f
    }

    var profile: Profile = Profile.CITY_TRAFFIC
        private set

    fun intervalFor(speedKmh: Float, movementDetected: Boolean): Long {
        val safeSpeed = speedKmh.takeIf { it.isFinite() && it >= 0f } ?: 0f
        profile = when (profile) {
            Profile.STATIONARY -> when {
                safeSpeed >= ENTER_HIGHWAY_SPEED_KMH -> Profile.HIGHWAY
                safeSpeed >= ENTER_CITY_SPEED_KMH || movementDetected -> Profile.CITY_TRAFFIC
                else -> Profile.STATIONARY
            }
            Profile.CITY_TRAFFIC -> when {
                safeSpeed >= ENTER_HIGHWAY_SPEED_KMH -> Profile.HIGHWAY
                safeSpeed <= ENTER_STATIONARY_SPEED_KMH && !movementDetected -> Profile.STATIONARY
                else -> Profile.CITY_TRAFFIC
            }
            Profile.HIGHWAY -> when {
                safeSpeed < LEAVE_HIGHWAY_SPEED_KMH &&
                    safeSpeed <= ENTER_STATIONARY_SPEED_KMH && !movementDetected -> Profile.STATIONARY
                safeSpeed < LEAVE_HIGHWAY_SPEED_KMH -> Profile.CITY_TRAFFIC
                else -> Profile.HIGHWAY
            }
        }
        return when (profile) {
            Profile.STATIONARY -> STATIONARY_INTERVAL_MS
            Profile.CITY_TRAFFIC -> CITY_INTERVAL_MS
            Profile.HIGHWAY -> HIGHWAY_INTERVAL_MS
        }
    }

    fun reset() {
        profile = Profile.CITY_TRAFFIC
    }
}
