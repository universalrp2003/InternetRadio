package com.rpideas.secretmessage.preview.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadValidatorTest {
    private val stroke = VectorStroke(listOf(NormalizedPoint(0f, 0f), NormalizedPoint(1f, 1f)), 0.03f)
    private val glyph = InlineSegment.DrawnGlyph(listOf(stroke), 38f)

    @Test
    fun acceptsWellFormedTextGlyphAndPicturePayloads() {
        val inline = MessageContent(listOf(InlineSegment.EnglishText("Hi @ 5pm!"), glyph))
        val picture = MessageContent(fullPictureStrokes = listOf(stroke, VectorStroke(listOf(NormalizedPoint(0.5f, 0.5f)), 0.1f)))
        assertTrue(PayloadValidator.isValidContent(inline))
        assertTrue(PayloadValidator.isValidContent(picture))
        assertTrue(PayloadValidator.isValidPayload(DecryptedChatPayload.ChatMessagePayload("6f1c2b9e-1d2a-4f00-9c6b-6b0d5b9a4e21", 60, inline)))
        assertTrue(PayloadValidator.isValidPayload(DecryptedChatPayload.PurgeNowSync("abc-123")))
    }

    @Test
    fun rejectsMalformedIdentifiersAndNonAsciiOrControlText() {
        assertFalse(PayloadValidator.isValidMessageId(""))
        assertFalse(PayloadValidator.isValidMessageId("has space"))
        assertFalse(PayloadValidator.isValidMessageId("a".repeat(65)))
        listOf("\u202Eabc", "tab\there", "line\nbreak", "தமிழ்", "emoji \uD83D\uDE00").forEach { text ->
            assertFalse(text, PayloadValidator.isValidContent(MessageContent(listOf(InlineSegment.EnglishText(text)))))
        }
        assertFalse(PayloadValidator.isValidContent(MessageContent(listOf(InlineSegment.EnglishText("   ")))))
    }

    @Test
    fun rejectsOutOfRangeOrNonFiniteStrokeValues() {
        val badStrokes = listOf(
            VectorStroke(emptyList(), 0.03f),
            VectorStroke(listOf(NormalizedPoint(Float.NaN, 0f)), 0.03f),
            VectorStroke(listOf(NormalizedPoint(0f, 1.01f)), 0.03f),
            VectorStroke(listOf(NormalizedPoint(-0.01f, 0f)), 0.03f),
            VectorStroke(listOf(NormalizedPoint(0f, 0f)), 0f),
            VectorStroke(listOf(NormalizedPoint(0f, 0f)), Float.POSITIVE_INFINITY),
            VectorStroke(List(PayloadLimits.MAX_POINTS_PER_STROKE + 1) { NormalizedPoint(0f, 0f) }, 0.03f),
        )
        badStrokes.forEach { bad ->
            assertFalse(PayloadValidator.isValidStroke(bad))
            assertFalse(PayloadValidator.isValidContent(MessageContent(fullPictureStrokes = listOf(bad))))
        }
        assertFalse(PayloadValidator.isValidGlyph(InlineSegment.DrawnGlyph(listOf(stroke), 10f)))
        assertFalse(PayloadValidator.isValidGlyph(InlineSegment.DrawnGlyph(emptyList(), 38f)))
    }

    @Test
    fun rejectsContentThatIsBothOrNeitherTextAndPicture() {
        assertFalse(PayloadValidator.isValidContent(MessageContent()))
        assertFalse(PayloadValidator.isValidContent(MessageContent(listOf(glyph), listOf(stroke))))
    }

    @Test
    fun rejectsOversizedPayloads() {
        val tooManyGlyphs = MessageContent(List(PayloadLimits.MAX_INLINE_GLYPHS + 1) { glyph })
        assertFalse(PayloadValidator.isValidContent(tooManyGlyphs))

        val longText = MessageContent(listOf(InlineSegment.EnglishText("a".repeat(PayloadLimits.MAX_TEXT_CHARS + 1))))
        assertFalse(PayloadValidator.isValidContent(longText))

        val denseStroke = VectorStroke(List(PayloadLimits.MAX_POINTS_PER_STROKE) { NormalizedPoint(0.5f, 0.5f) }, 0.03f)
        val tooManyPoints = MessageContent(fullPictureStrokes = List(PayloadLimits.MAX_POINTS_PER_MESSAGE / PayloadLimits.MAX_POINTS_PER_STROKE + 1) { denseStroke })
        assertFalse(PayloadValidator.isValidContent(tooManyPoints))

        val tooManyStrokes = MessageContent(fullPictureStrokes = List(PayloadLimits.MAX_STROKES_PER_PICTURE + 1) { stroke })
        assertFalse(PayloadValidator.isValidContent(tooManyStrokes))
    }
}
