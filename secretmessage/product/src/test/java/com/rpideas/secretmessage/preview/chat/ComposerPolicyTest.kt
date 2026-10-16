package com.rpideas.secretmessage.preview.chat

import com.rpideas.secretmessage.preview.model.InlineSegment
import com.rpideas.secretmessage.preview.model.NormalizedPoint
import com.rpideas.secretmessage.preview.model.PayloadLimits
import com.rpideas.secretmessage.preview.model.VectorStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerPolicyTest {
    private fun glyph(points: Int = 2, height: Float = 38f): InlineSegment.DrawnGlyph {
        val strokePoints = List(points) { index -> NormalizedPoint(index / (points + 1f), 0.5f) }
        return InlineSegment.DrawnGlyph(listOf(VectorStroke(strokePoints, 0.03f)), height)
    }

    @Test
    fun typingAppendsToTrailingTextAndBackspaceRemovesCharactersThenGlyphs() {
        var segments = ComposerPolicy.type(emptyList(), "hi")
        segments = ComposerPolicy.type(segments, " there")
        assertEquals(listOf(InlineSegment.EnglishText("hi there")), segments)

        val withGlyph = ComposerPolicy.insertGlyph(segments, glyph())
        val afterGlyph = ComposerPolicy.type(withGlyph, "!")
        assertEquals(3, afterGlyph.size)
        assertEquals(InlineSegment.EnglishText("!"), afterGlyph.last())

        val noBang = ComposerPolicy.backspace(afterGlyph)
        assertEquals(2, noBang.size)
        assertTrue(noBang.last() is InlineSegment.DrawnGlyph)
        val noGlyph = ComposerPolicy.backspace(noBang)
        assertEquals(listOf(InlineSegment.EnglishText("hi there")), noGlyph)
        assertEquals(listOf(InlineSegment.EnglishText("hi ther")), ComposerPolicy.backspace(noGlyph))
        assertTrue(ComposerPolicy.backspace(emptyList()).isEmpty())
    }

    @Test
    fun typingIgnoresNonPrintableTextAndStopsAtTextLimit() {
        assertTrue(ComposerPolicy.type(emptyList(), "\u0007\u202E").isEmpty())
        assertEquals(listOf(InlineSegment.EnglishText("ok")), ComposerPolicy.type(emptyList(), "o\nk"))

        val full = ComposerPolicy.type(emptyList(), "a".repeat(PayloadLimits.MAX_TEXT_CHARS + 50))
        assertEquals(PayloadLimits.MAX_TEXT_CHARS, ComposerPolicy.textLength(full))
        assertEquals(full, ComposerPolicy.type(full, "b"))
    }

    @Test
    fun glyphInsertionStopsAtGlyphLimitAndPointBudget() {
        var segments = emptyList<InlineSegment>()
        repeat(PayloadLimits.MAX_INLINE_GLYPHS + 3) { segments = ComposerPolicy.insertGlyph(segments, glyph()) }
        assertEquals(PayloadLimits.MAX_INLINE_GLYPHS, ComposerPolicy.glyphCount(segments))
        assertFalse(ComposerPolicy.canInsertGlyph(segments))

        val heavy = glyph(points = PayloadLimits.MAX_POINTS_PER_STROKE)
        var budget = emptyList<InlineSegment>()
        repeat(30) { budget = ComposerPolicy.insertGlyph(budget, heavy) }
        assertTrue(ComposerPolicy.pointCount(budget) <= PayloadLimits.MAX_POINTS_PER_MESSAGE)
        assertEquals(PayloadLimits.MAX_POINTS_PER_MESSAGE / PayloadLimits.MAX_POINTS_PER_STROKE, ComposerPolicy.glyphCount(budget))

        val tooTall = glyph(height = 200f)
        assertTrue(ComposerPolicy.insertGlyph(emptyList(), tooTall).isEmpty())
    }

    @Test
    fun prepareForSendTrimsOuterWhitespaceAndRejectsBlankMessages() {
        val draft = listOf(InlineSegment.EnglishText("  hello "), glyph(), InlineSegment.EnglishText(" bye  "))
        val prepared = ComposerPolicy.prepareForSend(draft)
        assertEquals(InlineSegment.EnglishText("hello "), prepared.first())
        assertEquals(InlineSegment.EnglishText(" bye"), prepared.last())
        assertTrue(ComposerPolicy.isSendable(draft))

        assertFalse(ComposerPolicy.isSendable(listOf(InlineSegment.EnglishText("    "))))
        assertTrue(ComposerPolicy.prepareForSend(listOf(InlineSegment.EnglishText("   "))).isEmpty())
        assertTrue(ComposerPolicy.isSendable(listOf(glyph())))
        assertFalse(ComposerPolicy.isSendable(emptyList()))
    }
}
