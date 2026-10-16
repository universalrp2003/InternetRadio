package com.rpideas.secretmessage.preview.model

/*
 * WhisperPad product data models adapted for the Secret Message preview.
 *
 * Coordinates are normalized to 0.0..1.0 so strokes render at any size. Nothing here is persisted:
 * messages exist only in memory and are removed by the timer rules in ChatStateMachine.
 */

/** A point on a drawing surface, normalized to 0.0..1.0 on both axes. */
data class NormalizedPoint(
    val x: Float,
    val y: Float,
)

/**
 * One continuous pen stroke. [widthFraction] is the line width relative to the height of the
 * surface it was drawn on, so a letter keeps its proportions when shown smaller or larger.
 */
data class VectorStroke(
    val points: List<NormalizedPoint>,
    val widthFraction: Float,
)

/** An item in a composed message: typed English text or a hand-drawn glyph in any script. */
sealed class InlineSegment {
    data class EnglishText(val text: String) : InlineSegment()

    /** Strokes drawn on the square letter pad and shown inline at [displayHeightDp]. */
    data class DrawnGlyph(
        val strokes: List<VectorStroke>,
        val displayHeightDp: Float,
    ) : InlineSegment()
}

/** A message is either inline text/glyph segments or one standalone picture, never both. */
data class MessageContent(
    val inlineSegments: List<InlineSegment> = emptyList(),
    val fullPictureStrokes: List<VectorStroke> = emptyList(),
)

/**
 * Application payloads that a future authenticated MLS transport would carry inside MLS
 * application messages. Timers use relative durations only: no wall-clock timestamps are sent.
 */
sealed class DecryptedChatPayload {
    abstract val messageId: String

    data class ChatMessagePayload(
        override val messageId: String,
        val viewDurationSeconds: Int,
        val content: MessageContent,
    ) : DecryptedChatPayload()

    data class DeliveryAck(
        override val messageId: String,
    ) : DecryptedChatPayload()

    data class ViewedAck(
        override val messageId: String,
        val enforcedDurationSeconds: Int,
    ) : DecryptedChatPayload()

    data class PurgeNowSync(
        override val messageId: String,
    ) : DecryptedChatPayload()
}

enum class MessageDeliveryState {
    /** Sender copy waiting for an authenticated delivery acknowledgement; removed after 30 seconds. */
    SENDING_UNDELIVERED,

    /** Delivered (or received) and waiting for the recipient to view it. */
    DELIVERED_WAITING_VIEW,

    /** Viewed by the recipient; the 60–300 second countdown is running on both phones. */
    VIEWED_COUNTING_DOWN,
}

/** In-memory UI representation of an ephemeral message. Never written to disk. */
data class EphemeralMessage(
    val id: String,
    val isFromLocalUser: Boolean,
    val content: MessageContent,
    val configuredViewSeconds: Int,
    val deliveryState: MessageDeliveryState,
    /** Elapsed-realtime deadline in milliseconds, or null while waiting to be viewed. */
    val deadlineElapsedMillis: Long?,
)
