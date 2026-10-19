package com.rpideas.secretmessage.preview.model

/** Bounds for locally composed content and for any payload a future transport delivers. */
object PayloadLimits {
    const val MAX_TEXT_CHARS = 2_000
    const val MAX_SEGMENTS = 400
    const val MAX_INLINE_GLYPHS = 24
    const val MAX_STROKES_PER_GLYPH = 32
    const val MAX_POINTS_PER_GLYPH = 2_400
    const val MAX_STROKES_PER_PICTURE = 96
    const val MAX_POINTS_PER_STROKE = 600
    const val MAX_POINTS_PER_MESSAGE = 12_000
    const val MIN_WIDTH_FRACTION = 0.002f
    const val MAX_WIDTH_FRACTION = 0.25f
    const val MIN_GLYPH_HEIGHT_DP = 20f
    const val MAX_GLYPH_HEIGHT_DP = 64f
}

/**
 * Strict checks applied before content is accepted. The preview keyboard produces printable ASCII
 * only, so text containing control, bidirectional-override or other non-ASCII characters is
 * rejected rather than displayed.
 */
object PayloadValidator {
    private val messageIdPattern = Regex("^[A-Za-z0-9-]{1,64}$")

    fun isValidMessageId(id: String): Boolean = messageIdPattern.matches(id)

    fun isPrintableAscii(text: String): Boolean = text.all { it in ' '..'~' }

    fun isValidStroke(stroke: VectorStroke): Boolean {
        if (stroke.points.isEmpty() || stroke.points.size > PayloadLimits.MAX_POINTS_PER_STROKE) return false
        val width = stroke.widthFraction
        if (!width.isFinite() || width < PayloadLimits.MIN_WIDTH_FRACTION || width > PayloadLimits.MAX_WIDTH_FRACTION) {
            return false
        }
        return stroke.points.all { point ->
            point.x.isFinite() && point.y.isFinite() &&
                point.x >= 0f && point.x <= 1f && point.y >= 0f && point.y <= 1f
        }
    }

    fun isValidGlyph(glyph: InlineSegment.DrawnGlyph): Boolean {
        val height = glyph.displayHeightDp
        if (!height.isFinite() || height < PayloadLimits.MIN_GLYPH_HEIGHT_DP || height > PayloadLimits.MAX_GLYPH_HEIGHT_DP) {
            return false
        }
        if (glyph.strokes.isEmpty() || glyph.strokes.size > PayloadLimits.MAX_STROKES_PER_GLYPH) return false
        if (!glyph.strokes.all { isValidStroke(it) }) return false
        return pointCount(glyph.strokes) <= PayloadLimits.MAX_POINTS_PER_GLYPH
    }

    fun isValidContent(content: MessageContent): Boolean {
        val hasSegments = content.inlineSegments.isNotEmpty()
        val hasPicture = content.fullPictureStrokes.isNotEmpty()
        if (hasSegments == hasPicture) return false
        return if (hasPicture) isValidPicture(content.fullPictureStrokes) else isValidSegments(content.inlineSegments)
    }

    fun isValidPayload(payload: DecryptedChatPayload): Boolean {
        if (!isValidMessageId(payload.messageId)) return false
        return when (payload) {
            is DecryptedChatPayload.ChatMessagePayload -> isValidContent(payload.content)
            is DecryptedChatPayload.DeliveryAck -> true
            is DecryptedChatPayload.ViewedAck -> true
            is DecryptedChatPayload.PurgeNowSync -> true
        }
    }

    fun pointCount(strokes: List<VectorStroke>): Int = strokes.sumOf { it.points.size }

    private fun isValidPicture(strokes: List<VectorStroke>): Boolean {
        if (strokes.size > PayloadLimits.MAX_STROKES_PER_PICTURE) return false
        if (!strokes.all { isValidStroke(it) }) return false
        return pointCount(strokes) <= PayloadLimits.MAX_POINTS_PER_MESSAGE
    }

    private fun isValidSegments(segments: List<InlineSegment>): Boolean {
        if (segments.size > PayloadLimits.MAX_SEGMENTS) return false
        var textChars = 0
        var glyphs = 0
        var points = 0
        var hasVisibleItem = false
        for (segment in segments) {
            when (segment) {
                is InlineSegment.EnglishText -> {
                    if (segment.text.isEmpty() || !isPrintableAscii(segment.text)) return false
                    textChars += segment.text.length
                    if (segment.text.isNotBlank()) hasVisibleItem = true
                }
                is InlineSegment.DrawnGlyph -> {
                    if (!isValidGlyph(segment)) return false
                    glyphs += 1
                    points += pointCount(segment.strokes)
                    hasVisibleItem = true
                }
            }
        }
        return hasVisibleItem &&
            textChars <= PayloadLimits.MAX_TEXT_CHARS &&
            glyphs <= PayloadLimits.MAX_INLINE_GLYPHS &&
            points <= PayloadLimits.MAX_POINTS_PER_MESSAGE
    }
}
