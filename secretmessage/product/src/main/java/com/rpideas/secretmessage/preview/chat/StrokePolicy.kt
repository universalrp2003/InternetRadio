package com.rpideas.secretmessage.preview.chat

import com.rpideas.secretmessage.preview.model.NormalizedPoint
import com.rpideas.secretmessage.preview.model.PayloadLimits

/** Pure helpers for capturing pen strokes on the drawing pad. */
object StrokePolicy {
    /** Points closer than this (in normalized units) are skipped to keep strokes compact. */
    const val MIN_POINT_DISTANCE = 0.004f

    fun normalize(x: Float, y: Float, widthPx: Int, heightPx: Int): NormalizedPoint {
        val width = widthPx.coerceAtLeast(1).toFloat()
        val height = heightPx.coerceAtLeast(1).toFloat()
        return NormalizedPoint((x / width).coerceIn(0f, 1f), (y / height).coerceIn(0f, 1f))
    }

    /** A single tap is kept as a one-point stroke, so dots such as the Tamil pulli survive. */
    fun shouldAppend(points: List<NormalizedPoint>, point: NormalizedPoint): Boolean {
        if (points.size >= PayloadLimits.MAX_POINTS_PER_STROKE) return false
        val last = points.lastOrNull() ?: return true
        val dx = point.x - last.x
        val dy = point.y - last.y
        return dx * dx + dy * dy >= MIN_POINT_DISTANCE * MIN_POINT_DISTANCE
    }

    /** Convert the slider's line width on a pad of known height to a resolution-independent fraction. */
    fun widthFraction(lineWidthDp: Float, surfaceHeightDp: Float): Float =
        (lineWidthDp / surfaceHeightDp.coerceAtLeast(1f))
            .coerceIn(PayloadLimits.MIN_WIDTH_FRACTION, PayloadLimits.MAX_WIDTH_FRACTION)
}
