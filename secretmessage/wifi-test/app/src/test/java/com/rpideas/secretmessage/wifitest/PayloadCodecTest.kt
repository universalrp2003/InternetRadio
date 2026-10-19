package com.rpideas.secretmessage.wifitest

import com.rpideas.secretmessage.preview.model.DecryptedChatPayload
import com.rpideas.secretmessage.preview.model.InlineSegment
import com.rpideas.secretmessage.preview.model.MessageContent
import com.rpideas.secretmessage.preview.model.NormalizedPoint
import com.rpideas.secretmessage.preview.model.PayloadLimits
import com.rpideas.secretmessage.preview.model.PayloadValidator
import com.rpideas.secretmessage.preview.model.VectorStroke
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadCodecTest {
    // Coordinates on the 1/65535 grid survive the round trip exactly.
    private val stroke = VectorStroke(
        points = listOf(NormalizedPoint(0f, 0f), NormalizedPoint(32768 / 65535f, 16384 / 65535f), NormalizedPoint(1f, 1f)),
        widthFraction = 0.05f,
    )

    @Test
    fun textAndDrawnLettersRoundTrip() {
        val payload = DecryptedChatPayload.ChatMessagePayload(
            messageId = "0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0",
            viewDurationSeconds = 120,
            content = MessageContent(
                inlineSegments = listOf(
                    InlineSegment.EnglishText("Hello | there"),
                    InlineSegment.DrawnGlyph(listOf(stroke), 32f),
                    InlineSegment.EnglishText("!"),
                ),
            ),
        )
        assertEquals(payload, PayloadCodec.decode(PayloadCodec.encode(payload)))
    }

    @Test
    fun picturesAndNoticesRoundTrip() {
        val picture = DecryptedChatPayload.ChatMessagePayload(
            "picture-1",
            300,
            MessageContent(fullPictureStrokes = listOf(stroke, stroke.copy(widthFraction = 0.25f))),
        )
        val payloads = listOf(
            picture,
            DecryptedChatPayload.DeliveryAck("picture-1"),
            DecryptedChatPayload.ViewedAck("picture-1", 60),
            DecryptedChatPayload.PurgeNowSync("picture-1"),
        )
        payloads.forEach { assertEquals(it, PayloadCodec.decode(PayloadCodec.encode(it))) }
    }

    @Test
    fun coordinatesKeepAboutFourDecimalPlaces() {
        val odd = VectorStroke(listOf(NormalizedPoint(0.123456f, 0.987654f)), 0.01f)
        val payload = DecryptedChatPayload.ChatMessagePayload("odd", 60, MessageContent(fullPictureStrokes = listOf(odd)))
        val decoded = PayloadCodec.decode(PayloadCodec.encode(payload)) as DecryptedChatPayload.ChatMessagePayload
        val point = decoded.content.fullPictureStrokes.single().points.single()
        assertEquals(0.123456f, point.x, 1f / 65535f)
        assertEquals(0.987654f, point.y, 1f / 65535f)
        assertTrue(PayloadValidator.isValidPayload(decoded))
    }

    @Test
    fun theLargestAllowedPictureFitsTheWifiSession() {
        val strokes = List(PayloadLimits.MAX_POINTS_PER_MESSAGE / PayloadLimits.MAX_POINTS_PER_STROKE) { index ->
            VectorStroke(
                points = List(PayloadLimits.MAX_POINTS_PER_STROKE) { point ->
                    NormalizedPoint((point % 100) / 100f, (index % 20) / 20f)
                },
                widthFraction = 0.02f,
            )
        }
        val payload = DecryptedChatPayload.ChatMessagePayload(
            "big-picture",
            300,
            MessageContent(fullPictureStrokes = strokes),
        )
        assertTrue(PayloadValidator.isValidPayload(payload))
        val encoded = PayloadCodec.encode(payload)
        assertTrue("encoded size ${encoded.length}", encoded.length <= PayloadCodec.MAX_ENCODED_CHARS)
        assertEquals(payload.content.fullPictureStrokes.size, (PayloadCodec.decode(encoded) as DecryptedChatPayload.ChatMessagePayload).content.fullPictureStrokes.size)
    }

    @Test
    fun malformedOrOversizedTextIsRefused() {
        val good = PayloadCodec.encode(DecryptedChatPayload.DeliveryAck("id-1"))
        val bytes = Base64.getDecoder().decode(good)
        val encoder = Base64.getEncoder()
        assertNull(PayloadCodec.decode(""))
        assertNull(PayloadCodec.decode("not base64 !!"))
        assertNull(PayloadCodec.decode(encoder.encodeToString(bytes.copyOf(bytes.size - 1))))
        assertNull(PayloadCodec.decode(encoder.encodeToString(bytes + byteArrayOf(0))))
        assertNull(PayloadCodec.decode(encoder.encodeToString(byteArrayOf(2) + bytes.copyOfRange(1, bytes.size))))
        assertNull(PayloadCodec.decode(encoder.encodeToString(byteArrayOf(1, 9, 1, 65))))
        assertNull(PayloadCodec.decode("A".repeat(PayloadCodec.MAX_ENCODED_CHARS + 4)))
        // A stroke claiming more points than allowed is refused before anything is allocated for it.
        val tooManyPoints = byteArrayOf(1, 1, 1, 65, 0, 60, 2, 0, 1, 0x3c, 0x23, 0xd7.toByte(), 0x0a, 0x7f, 0x7f)
        assertNull(PayloadCodec.decode(encoder.encodeToString(tooManyPoints)))
        assertNotNull(PayloadCodec.decode(good))
    }
}
