package com.universalrp.tamilnadufm.audio

/** Shared bounds/conversion for the user's session-level volume boost. */
internal object VolumeBoostPolicy {
    const val MAX_DB = 10f
    private const val MIN_DB = 0f
    private const val ACTIVE_THRESHOLD_DB = 0.05f

    fun clamp(db: Float): Float =
        if (db.isFinite()) db.coerceIn(MIN_DB, MAX_DB) else MIN_DB

    fun isActive(db: Float): Boolean = clamp(db) > ACTIVE_THRESHOLD_DB

    /** Android LoudnessEnhancer expects millibels: 100 millibels = 1 dB. */
    fun toMillibels(db: Float): Int = (clamp(db) * 100f).toInt()
}
