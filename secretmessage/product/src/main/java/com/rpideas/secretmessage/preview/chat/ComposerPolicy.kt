package com.rpideas.secretmessage.preview.chat

import com.rpideas.secretmessage.preview.model.InlineSegment
import com.rpideas.secretmessage.preview.model.PayloadLimits
import com.rpideas.secretmessage.preview.model.PayloadValidator

/** Pure editing rules for the inline composer: typed English text plus hand-drawn glyphs. */
object ComposerPolicy {
    fun textLength(segments: List<InlineSegment>): Int =
        segments.sumOf { segment -> if (segment is InlineSegment.EnglishText) segment.text.length else 0 }

    fun glyphCount(segments: List<InlineSegment>): Int = segments.count { it is InlineSegment.DrawnGlyph }

    fun pointCount(segments: List<InlineSegment>): Int =
        segments.sumOf { segment ->
            if (segment is InlineSegment.DrawnGlyph) PayloadValidator.pointCount(segment.strokes) else 0
        }

    /** Append printable ASCII from the built-in keyboard, up to the message text limit. */
    fun type(segments: List<InlineSegment>, typed: String): List<InlineSegment> {
        val accepted = typed.filter { PayloadValidator.isPrintableAscii(it.toString()) }
        if (accepted.isEmpty()) return segments
        val room = PayloadLimits.MAX_TEXT_CHARS - textLength(segments)
        if (room <= 0) return segments
        val addition = accepted.take(room)
        val last = segments.lastOrNull()
        if (last is InlineSegment.EnglishText) {
            return segments.dropLast(1) + InlineSegment.EnglishText(last.text + addition)
        }
        if (segments.size >= PayloadLimits.MAX_SEGMENTS) return segments
        return segments + InlineSegment.EnglishText(addition)
    }

    /** Remove the last typed character, or the last inline glyph. */
    fun backspace(segments: List<InlineSegment>): List<InlineSegment> {
        val last = segments.lastOrNull() ?: return segments
        return when (last) {
            is InlineSegment.EnglishText -> {
                val shorter = last.text.dropLast(1)
                if (shorter.isEmpty()) segments.dropLast(1) else segments.dropLast(1) + InlineSegment.EnglishText(shorter)
            }
            is InlineSegment.DrawnGlyph -> segments.dropLast(1)
        }
    }

    fun canInsertGlyph(segments: List<InlineSegment>, glyphPoints: Int = 0): Boolean =
        glyphCount(segments) < PayloadLimits.MAX_INLINE_GLYPHS &&
            segments.size < PayloadLimits.MAX_SEGMENTS &&
            pointCount(segments) + glyphPoints <= PayloadLimits.MAX_POINTS_PER_MESSAGE

    /** Insert a valid glyph at the end; content beyond the limits is refused, not truncated. */
    fun insertGlyph(segments: List<InlineSegment>, glyph: InlineSegment.DrawnGlyph): List<InlineSegment> {
        if (!PayloadValidator.isValidGlyph(glyph)) return segments
        if (!canInsertGlyph(segments, PayloadValidator.pointCount(glyph.strokes))) return segments
        return segments + glyph
    }

    /** Trim outer whitespace and drop empty text so a sent message never starts or ends with blanks. */
    fun prepareForSend(segments: List<InlineSegment>): List<InlineSegment> {
        val trimmed = segments.toMutableList()
        val first = trimmed.firstOrNull()
        if (first is InlineSegment.EnglishText) {
            trimmed[0] = InlineSegment.EnglishText(first.text.trimStart())
        }
        val last = trimmed.lastOrNull()
        if (last is InlineSegment.EnglishText) {
            trimmed[trimmed.lastIndex] = InlineSegment.EnglishText(last.text.trimEnd())
        }
        return trimmed.filterNot { it is InlineSegment.EnglishText && it.text.isEmpty() }
    }

    fun isSendable(segments: List<InlineSegment>): Boolean =
        prepareForSend(segments).any { segment ->
            segment is InlineSegment.DrawnGlyph || (segment is InlineSegment.EnglishText && segment.text.isNotBlank())
        }
}
