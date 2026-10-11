package com.universalrp.cleansweep.ai

/** Exact IDs only: no namespace stripping, free/preview/reasoning suffix removal or nearest-name score. */
object ArenaRatingPolicy {
    const val CATEGORY = "full"
    const val CURRENT_WINDOW_MS = 7L * 24 * 60 * 60 * 1000
    data class Score(val modelId: String, val rating: Double, val lower: Double?, val upper: Double?)
    fun exact(modelId: String, rows: List<Score>): Score? = rows.filter { it.modelId == modelId.trim() }.singleOrNull()
    fun valid(value: Double): Boolean = value.isFinite() && value in 0.0..10_000.0
    fun current(changedAt: Long, now: Long): Boolean = changedAt > 0 && now >= changedAt && now - changedAt <= CURRENT_WINDOW_MS
}
