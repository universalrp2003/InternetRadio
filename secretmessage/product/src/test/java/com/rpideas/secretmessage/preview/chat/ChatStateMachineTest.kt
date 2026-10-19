package com.rpideas.secretmessage.preview.chat

import com.rpideas.secretmessage.preview.model.DecryptedChatPayload
import com.rpideas.secretmessage.preview.model.EphemeralMessage
import com.rpideas.secretmessage.preview.model.InlineSegment
import com.rpideas.secretmessage.preview.model.MessageContent
import com.rpideas.secretmessage.preview.model.MessageDeliveryState
import com.rpideas.secretmessage.preview.model.NormalizedPoint
import com.rpideas.secretmessage.preview.model.VectorStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatStateMachineTest {
    private val text = MessageContent(inlineSegments = listOf(InlineSegment.EnglishText("hello")))
    private val picture = MessageContent(
        fullPictureStrokes = listOf(
            VectorStroke(listOf(NormalizedPoint(0.1f, 0.2f), NormalizedPoint(0.4f, 0.6f)), 0.03f),
        ),
    )

    @Test
    fun sendStartsThirtySecondUndeliveredTimerAndExpiresWithoutDelivery() {
        val sent = ChatStateMachine.send(emptyList(), "m-1", text, 120, 1_000L)
        val message = sent.messages.single()

        assertTrue(message.isFromLocalUser)
        assertEquals(MessageDeliveryState.SENDING_UNDELIVERED, message.deliveryState)
        assertEquals(31_000L, message.deadlineElapsedMillis)
        assertEquals(listOf(DecryptedChatPayload.ChatMessagePayload("m-1", 120, text)), sent.outbound)

        assertEquals(1, ChatStateMachine.expire(sent.messages, 30_999L).messages.size)
        val expired = ChatStateMachine.expire(sent.messages, 31_000L)
        assertTrue(expired.messages.isEmpty())
        assertEquals(listOf(DecryptedChatPayload.PurgeNowSync("m-1")), expired.outbound)
    }

    @Test
    fun deliveryAckStopsUndeliveredTimerAndWaitsForView() {
        val sent = ChatStateMachine.send(emptyList(), "m-1", text, 90, 0L).messages
        val delivered = ChatStateMachine.receive(sent, DecryptedChatPayload.DeliveryAck("m-1"), 5_000L, false)

        val message = delivered.messages.single()
        assertEquals(MessageDeliveryState.DELIVERED_WAITING_VIEW, message.deliveryState)
        assertNull(message.deadlineElapsedMillis)
        assertTrue(delivered.outbound.isEmpty())
        assertEquals(1, ChatStateMachine.expire(delivered.messages, 10_000_000L).messages.size)
    }

    @Test
    fun lateDeliveryAckAfterExpiryDoesNotResurrectMessage() {
        val sent = ChatStateMachine.send(emptyList(), "m-1", text, 90, 0L).messages
        val lateAck = ChatStateMachine.receive(sent, DecryptedChatPayload.DeliveryAck("m-1"), 30_000L, false)
        assertEquals(MessageDeliveryState.SENDING_UNDELIVERED, lateAck.messages.single().deliveryState)
        assertTrue(ChatStateMachine.expire(lateAck.messages, 30_000L).messages.isEmpty())

        val afterRemoval = ChatStateMachine.receive(emptyList(), DecryptedChatPayload.DeliveryAck("m-1"), 31_000L, false)
        assertTrue(afterRemoval.messages.isEmpty())
    }

    @Test
    fun viewedAckStartsSenderCountdownWithOwnConfiguredDuration() {
        val sent = ChatStateMachine.send(emptyList(), "m-1", text, 90, 0L).messages
        val delivered = ChatStateMachine.receive(sent, DecryptedChatPayload.DeliveryAck("m-1"), 1_000L, false).messages
        val viewed = ChatStateMachine.receive(delivered, DecryptedChatPayload.ViewedAck("m-1", 300), 40_000L, false)

        val message = viewed.messages.single()
        assertEquals(MessageDeliveryState.VIEWED_COUNTING_DOWN, message.deliveryState)
        assertEquals(40_000L + 90_000L, message.deadlineElapsedMillis)

        val repeated = ChatStateMachine.receive(viewed.messages, DecryptedChatPayload.ViewedAck("m-1", 300), 70_000L, false)
        assertEquals(130_000L, repeated.messages.single().deadlineElapsedMillis)
        assertEquals(1, ChatStateMachine.expire(viewed.messages, 129_999L).messages.size)
        assertTrue(ChatStateMachine.expire(viewed.messages, 130_000L).messages.isEmpty())
    }

    @Test
    fun incomingMessageWhileViewingAcknowledgesDeliveryAndViewAndStartsCountdown() {
        val payload = DecryptedChatPayload.ChatMessagePayload("r-1", 120, text)
        val received = ChatStateMachine.receive(emptyList(), payload, 2_000L, true)

        val message = received.messages.single()
        assertFalse(message.isFromLocalUser)
        assertEquals(MessageDeliveryState.VIEWED_COUNTING_DOWN, message.deliveryState)
        assertEquals(122_000L, message.deadlineElapsedMillis)
        assertEquals(
            listOf(DecryptedChatPayload.DeliveryAck("r-1"), DecryptedChatPayload.ViewedAck("r-1", 120)),
            received.outbound,
        )
    }

    @Test
    fun incomingMessageWhileNotViewingWaitsUntilRecipientViews() {
        val payload = DecryptedChatPayload.ChatMessagePayload("r-1", 60, picture)
        val received = ChatStateMachine.receive(emptyList(), payload, 0L, false)

        assertEquals(MessageDeliveryState.DELIVERED_WAITING_VIEW, received.messages.single().deliveryState)
        assertNull(received.messages.single().deadlineElapsedMillis)
        assertEquals(listOf(DecryptedChatPayload.DeliveryAck("r-1")), received.outbound)
        assertEquals(1, ChatStateMachine.expire(received.messages, 10_000_000L).messages.size)

        val viewed = ChatStateMachine.recipientViewing(received.messages, 500_000L)
        assertEquals(MessageDeliveryState.VIEWED_COUNTING_DOWN, viewed.messages.single().deliveryState)
        assertEquals(560_000L, viewed.messages.single().deadlineElapsedMillis)
        assertEquals(listOf(DecryptedChatPayload.ViewedAck("r-1", 60)), viewed.outbound)
        assertTrue(ChatStateMachine.recipientViewing(viewed.messages, 501_000L).outbound.isEmpty())
    }

    @Test
    fun viewDurationIsClampedBetweenSixtyAndThreeHundredSeconds() {
        assertEquals(60, ChatStateMachine.send(emptyList(), "a", text, 5, 0L).messages.single().configuredViewSeconds)
        assertEquals(300, ChatStateMachine.send(emptyList(), "b", text, 9_999, 0L).messages.single().configuredViewSeconds)

        val tooShort = ChatStateMachine.receive(
            emptyList(),
            DecryptedChatPayload.ChatMessagePayload("c", 1, text),
            0L,
            true,
        )
        assertEquals(60_000L, tooShort.messages.single().deadlineElapsedMillis)
        val tooLong = ChatStateMachine.receive(
            emptyList(),
            DecryptedChatPayload.ChatMessagePayload("d", 100_000, text),
            0L,
            true,
        )
        assertEquals(300_000L, tooLong.messages.single().deadlineElapsedMillis)
    }

    @Test
    fun purgeNowSyncRemovesMessageImmediately() {
        val sent = ChatStateMachine.send(emptyList(), "m-1", text, 60, 0L).messages
        val received = ChatStateMachine.receive(sent, DecryptedChatPayload.ChatMessagePayload("r-1", 60, text), 0L, false).messages

        val purged = ChatStateMachine.receive(received, DecryptedChatPayload.PurgeNowSync("r-1"), 1L, false)
        assertEquals(listOf("m-1"), purged.messages.map { it.id })
        assertTrue(purged.outbound.isEmpty())
    }

    @Test
    fun duplicateAndInvalidIncomingPayloadsAreIgnored() {
        val first = ChatStateMachine.receive(emptyList(), DecryptedChatPayload.ChatMessagePayload("r-1", 60, text), 0L, false)
        val duplicate = ChatStateMachine.receive(
            first.messages,
            DecryptedChatPayload.ChatMessagePayload("r-1", 300, picture),
            10L,
            true,
        )
        assertEquals(first.messages, duplicate.messages)
        assertTrue(duplicate.outbound.isEmpty())

        val invalidPayloads = listOf(
            DecryptedChatPayload.ChatMessagePayload("bad id!", 60, text),
            DecryptedChatPayload.ChatMessagePayload("r-2", 60, MessageContent(listOf(InlineSegment.EnglishText("\u202Eevil")))),
            DecryptedChatPayload.ChatMessagePayload(
                "r-3",
                60,
                MessageContent(fullPictureStrokes = listOf(VectorStroke(listOf(NormalizedPoint(1.5f, 0f)), 0.03f))),
            ),
            DecryptedChatPayload.ChatMessagePayload("r-4", 60, MessageContent()),
            DecryptedChatPayload.DeliveryAck(""),
        )
        invalidPayloads.forEach { payload ->
            val result = ChatStateMachine.receive(first.messages, payload, 20L, true)
            assertEquals(first.messages, result.messages)
            assertTrue(result.outbound.isEmpty())
        }
    }

    @Test
    fun acknowledgementsForRemoteOrUnknownMessagesAreIgnored() {
        val remote = ChatStateMachine.receive(emptyList(), DecryptedChatPayload.ChatMessagePayload("r-1", 60, text), 0L, false).messages

        val forgedDelivery = ChatStateMachine.receive(remote, DecryptedChatPayload.DeliveryAck("r-1"), 1L, false)
        assertEquals(remote, forgedDelivery.messages)
        val forgedView = ChatStateMachine.receive(remote, DecryptedChatPayload.ViewedAck("r-1", 60), 1L, false)
        assertEquals(remote, forgedView.messages)
        val unknown = ChatStateMachine.receive(remote, DecryptedChatPayload.ViewedAck("nope", 60), 1L, false)
        assertEquals(remote, unknown.messages)
    }

    @Test
    fun sendRejectsInvalidContentWithoutQueueing() {
        val empty = ChatStateMachine.send(emptyList(), "m-1", MessageContent(), 60, 0L)
        assertTrue(empty.messages.isEmpty())
        assertTrue(empty.outbound.isEmpty())

        val both = MessageContent(text.inlineSegments, picture.fullPictureStrokes)
        assertTrue(ChatStateMachine.send(emptyList(), "m-2", both, 60, 0L).outbound.isEmpty())

        val existing = ChatStateMachine.send(emptyList(), "m-3", text, 60, 0L).messages
        val sameId = ChatStateMachine.send(existing, "m-3", picture, 60, 1L)
        assertEquals(existing, sameId.messages)
        assertTrue(sameId.outbound.isEmpty())
    }

    @Test
    fun memoryIsBoundedToNewestMessages() {
        var messages = emptyList<EphemeralMessage>()
        repeat(MAX_MESSAGES_IN_MEMORY + 5) { index ->
            messages = ChatStateMachine.send(messages, "m-$index", text, 60, index.toLong()).messages
        }
        assertEquals(MAX_MESSAGES_IN_MEMORY, messages.size)
        assertEquals("m-5", messages.first().id)
        assertEquals("m-${MAX_MESSAGES_IN_MEMORY + 4}", messages.last().id)
    }

    @Test
    fun remainingSecondsRoundsUpAndNeverGoesNegative() {
        assertEquals(30, ChatTimerPolicy.remainingSeconds(0L, 30_000L))
        assertEquals(1, ChatTimerPolicy.remainingSeconds(29_001L, 30_000L))
        assertEquals(0, ChatTimerPolicy.remainingSeconds(30_000L, 30_000L))
        assertEquals(0, ChatTimerPolicy.remainingSeconds(31_000L, 30_000L))
    }

    @Test
    fun viewTimerSnapsToThirtySecondStepsWithinLimits() {
        assertEquals(60, ChatTimerPolicy.snapViewSeconds(0))
        assertEquals(60, ChatTimerPolicy.snapViewSeconds(74))
        assertEquals(90, ChatTimerPolicy.snapViewSeconds(75))
        assertEquals(180, ChatTimerPolicy.snapViewSeconds(181))
        assertEquals(300, ChatTimerPolicy.snapViewSeconds(301))
        assertEquals(30_000L, UNDELIVERED_TTL_MILLIS)
        assertEquals(60, MIN_VIEW_SECONDS)
        assertEquals(300, MAX_VIEW_SECONDS)
    }
}
