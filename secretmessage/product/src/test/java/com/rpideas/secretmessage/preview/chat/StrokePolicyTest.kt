package com.rpideas.secretmessage.preview.chat

import com.rpideas.secretmessage.preview.model.NormalizedPoint
import com.rpideas.secretmessage.preview.model.PayloadLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokePolicyTest {
    @Test
    fun normalizeClampsToTheDrawingSurface() {
        assertEquals(NormalizedPoint(0.5f, 0.25f), StrokePolicy.normalize(50f, 25f, 100, 100))
        assertEquals(NormalizedPoint(0f, 1f), StrokePolicy.normalize(-10f, 400f, 100, 100))
        assertEquals(NormalizedPoint(1f, 1f), StrokePolicy.normalize(5f, 5f, 0, 0))
    }

    @Test
    fun tapsAreKeptWhileTinyMovementsAndExcessPointsAreSkipped() {
        assertTrue(StrokePolicy.shouldAppend(emptyList(), NormalizedPoint(0.2f, 0.2f)))
        val start = listOf(NormalizedPoint(0.2f, 0.2f))
        assertFalse(StrokePolicy.shouldAppend(start, NormalizedPoint(0.201f, 0.201f)))
        assertTrue(StrokePolicy.shouldAppend(start, NormalizedPoint(0.21f, 0.2f)))

        val full = List(PayloadLimits.MAX_POINTS_PER_STROKE) { NormalizedPoint(it / 1_000f, 0f) }
        assertFalse(StrokePolicy.shouldAppend(full, NormalizedPoint(0.9f, 0.9f)))
    }

    @Test
    fun lineWidthBecomesAResolutionIndependentFraction() {
        assertEquals(0.05f, StrokePolicy.widthFraction(5f, 100f), 0.0001f)
        assertEquals(PayloadLimits.MAX_WIDTH_FRACTION, StrokePolicy.widthFraction(500f, 100f), 0.0001f)
        assertEquals(PayloadLimits.MIN_WIDTH_FRACTION, StrokePolicy.widthFraction(0f, 100f), 0.0001f)
    }
}
