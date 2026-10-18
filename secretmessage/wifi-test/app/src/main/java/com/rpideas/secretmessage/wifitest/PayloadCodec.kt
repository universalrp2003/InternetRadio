package com.rpideas.secretmessage.wifitest

import com.rpideas.secretmessage.preview.model.DecryptedChatPayload
import com.rpideas.secretmessage.preview.model.InlineSegment
import com.rpideas.secretmessage.preview.model.MessageContent
import com.rpideas.secretmessage.preview.model.NormalizedPoint
import com.rpideas.secretmessage.preview.model.PayloadLimits
import com.rpideas.secretmessage.preview.model.VectorStroke
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.util.Base64
import kotlin.math.roundToInt

/**
 * Turns the app's chat payloads into the text that the MLS session carries, and back.
 *
 * The bytes are a small versioned binary format, sent as Base64 text inside one MLS application
 * message, so only the MLS layer encrypts and authenticates them. Coordinates are rounded to
 * 1/65535 of the pad. [decode] rejects anything malformed or oversized before the chat sees it;
 * the chat then applies its own content rules (PayloadValidator).
 */
internal object PayloadCodec {
    /** Longest encoded payload the Wi-Fi session accepts (its limit is 96 KiB of text). */
    const val MAX_ENCODED_CHARS = 96 * 1024

    private const val FORMAT = 1
    private const val KIND_CHAT = 1
    private const val KIND_DELIVERED = 2
    private const val KIND_VIEWED = 3
    private const val KIND_PURGE = 4
    /** A doorbell card (1.1.0), handled by the app and never shown in the chat; 1.0.0 refuses it. */
    private const val KIND_CARD = 5
    private const val CARD_BYTES = 2 + 16 + 32
    private const val CONTENT_INLINE = 1
    private const val CONTENT_PICTURE = 2
    private const val SEGMENT_TEXT = 1
    private const val SEGMENT_GLYPH = 2
    private const val SCALE = 65535f
    private const val MAX_ID_BYTES = 64
    private val MAX_STROKES = maxOf(PayloadLimits.MAX_STROKES_PER_PICTURE, PayloadLimits.MAX_STROKES_PER_GLYPH)

    fun encode(payload: DecryptedChatPayload): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeByte(FORMAT)
            when (payload) {
                is DecryptedChatPayload.ChatMessagePayload -> {
                    out.writeByte(KIND_CHAT)
                    writeId(out, payload.messageId)
                    out.writeShort(payload.viewDurationSeconds.coerceIn(0, 0xFFFF))
                    writeContent(out, payload.content)
                }
                is DecryptedChatPayload.DeliveryAck -> {
                    out.writeByte(KIND_DELIVERED)
                    writeId(out, payload.messageId)
                }
                is DecryptedChatPayload.ViewedAck -> {
                    out.writeByte(KIND_VIEWED)
                    writeId(out, payload.messageId)
                    out.writeShort(payload.enforcedDurationSeconds.coerceIn(0, 0xFFFF))
                }
                is DecryptedChatPayload.PurgeNowSync -> {
                    out.writeByte(KIND_PURGE)
                    writeId(out, payload.messageId)
                }
            }
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    /** The payload in [text], or null if it is not a well-formed payload of this format. */
    fun decode(text: String): DecryptedChatPayload? {
        if (text.isEmpty() || text.length > MAX_ENCODED_CHARS) return null
        return try {
            val bytes = Base64.getDecoder().decode(text)
            val input = DataInputStream(ByteArrayInputStream(bytes))
            if (input.readUnsignedByte() != FORMAT) return null
            val payload = when (input.readUnsignedByte()) {
                KIND_CHAT -> {
                    val id = readId(input)
                    val seconds = input.readUnsignedShort()
                    DecryptedChatPayload.ChatMessagePayload(id, seconds, readContent(input) ?: return null)
                }
                KIND_DELIVERED -> DecryptedChatPayload.DeliveryAck(readId(input))
                KIND_VIEWED -> {
                    val id = readId(input)
                    DecryptedChatPayload.ViewedAck(id, input.readUnsignedShort())
                }
                KIND_PURGE -> DecryptedChatPayload.PurgeNowSync(readId(input))
                else -> return null
            }
            if (input.available() != 0) null else payload
        } catch (malformed: IllegalArgumentException) {
            null
        } catch (truncated: EOFException) {
            null
        } catch (unreadable: IOException) {
            null
        }
    }

    /** [card] as payload text: format, kind 5, the 16-byte inbox ID and the 32-byte inbox key. */
    fun encodeCard(card: ContactCard): String {
        require(card.isValid()) { "contact card" }
        val bytes = byteArrayOf(FORMAT.toByte(), KIND_CARD.toByte()) +
            ContactBook.hexBytes(card.inboxId) + ContactBook.hexBytes(card.inboxKey)
        return Base64.getEncoder().encodeToString(bytes)
    }

    /** The doorbell card in [text], or null if [text] is anything else. */
    fun decodeCard(text: String): ContactCard? {
        if (text.isEmpty() || text.length > 128) return null
        val bytes = try {
            Base64.getDecoder().decode(text)
        } catch (malformed: IllegalArgumentException) {
            return null
        }
        if (bytes.size != CARD_BYTES || bytes[0].toInt() != FORMAT || bytes[1].toInt() != KIND_CARD) return null
        val card = ContactCard(ContactBook.hex(bytes.copyOfRange(2, 18)), ContactBook.hex(bytes.copyOfRange(18, CARD_BYTES)))
        return card.takeIf { it.isValid() }
    }

    private fun writeId(out: DataOutputStream, id: String) {
        val bytes = id.toByteArray(Charsets.US_ASCII)
        require(bytes.size in 1..MAX_ID_BYTES) { "message ID length" }
        out.writeByte(bytes.size)
        out.write(bytes)
    }

    private fun readId(input: DataInputStream): String {
        val length = input.readUnsignedByte()
        require(length in 1..MAX_ID_BYTES) { "message ID length" }
        val bytes = ByteArray(length)
        input.readFully(bytes)
        return String(bytes, Charsets.US_ASCII)
    }

    private fun writeContent(out: DataOutputStream, content: MessageContent) {
        if (content.fullPictureStrokes.isNotEmpty()) {
            out.writeByte(CONTENT_PICTURE)
            writeStrokes(out, content.fullPictureStrokes)
            return
        }
        out.writeByte(CONTENT_INLINE)
        out.writeShort(content.inlineSegments.size)
        content.inlineSegments.forEach { segment ->
            when (segment) {
                is InlineSegment.EnglishText -> {
                    val bytes = segment.text.toByteArray(Charsets.UTF_8)
                    out.writeByte(SEGMENT_TEXT)
                    out.writeShort(bytes.size)
                    out.write(bytes)
                }
                is InlineSegment.DrawnGlyph -> {
                    out.writeByte(SEGMENT_GLYPH)
                    out.writeFloat(segment.displayHeightDp)
                    writeStrokes(out, segment.strokes)
                }
            }
        }
    }

    private fun readContent(input: DataInputStream): MessageContent? {
        when (input.readUnsignedByte()) {
            CONTENT_PICTURE -> {
                val strokes = readStrokes(input) ?: return null
                return MessageContent(fullPictureStrokes = strokes)
            }
            CONTENT_INLINE -> {
                val count = input.readUnsignedShort()
                if (count > PayloadLimits.MAX_SEGMENTS) return null
                val segments = ArrayList<InlineSegment>(count)
                repeat(count) {
                    segments.add(readSegment(input) ?: return null)
                }
                return MessageContent(inlineSegments = segments)
            }
            else -> return null
        }
    }

    private fun readSegment(input: DataInputStream): InlineSegment? {
        when (input.readUnsignedByte()) {
            SEGMENT_TEXT -> {
                val length = input.readUnsignedShort()
                if (length > PayloadLimits.MAX_TEXT_CHARS * 4) return null
                val bytes = ByteArray(length)
                input.readFully(bytes)
                return InlineSegment.EnglishText(String(bytes, Charsets.UTF_8))
            }
            SEGMENT_GLYPH -> {
                val height = input.readFloat()
                val strokes = readStrokes(input) ?: return null
                return InlineSegment.DrawnGlyph(strokes, height)
            }
            else -> return null
        }
    }

    private fun writeStrokes(out: DataOutputStream, strokes: List<VectorStroke>) {
        out.writeShort(strokes.size)
        strokes.forEach { stroke ->
            out.writeFloat(stroke.widthFraction)
            out.writeShort(stroke.points.size)
            stroke.points.forEach { point ->
                out.writeShort(quantize(point.x))
                out.writeShort(quantize(point.y))
            }
        }
    }

    private fun readStrokes(input: DataInputStream): List<VectorStroke>? {
        val count = input.readUnsignedShort()
        if (count > MAX_STROKES) return null
        val strokes = ArrayList<VectorStroke>(count)
        var totalPoints = 0
        repeat(count) {
            val width = input.readFloat()
            val pointCount = input.readUnsignedShort()
            totalPoints += pointCount
            if (pointCount > PayloadLimits.MAX_POINTS_PER_STROKE || totalPoints > PayloadLimits.MAX_POINTS_PER_MESSAGE) {
                return null
            }
            val points = ArrayList<NormalizedPoint>(pointCount)
            repeat(pointCount) {
                val x = input.readUnsignedShort() / SCALE
                val y = input.readUnsignedShort() / SCALE
                points.add(NormalizedPoint(x, y))
            }
            strokes.add(VectorStroke(points, width))
        }
        return strokes
    }

    private fun quantize(value: Float): Int =
        if (value.isNaN()) 0 else (value.coerceIn(0f, 1f) * SCALE).roundToInt()
}
