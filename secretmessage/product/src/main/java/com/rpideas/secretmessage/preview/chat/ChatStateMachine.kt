package com.rpideas.secretmessage.preview.chat

import com.rpideas.secretmessage.preview.model.DecryptedChatPayload
import com.rpideas.secretmessage.preview.model.EphemeralMessage
import com.rpideas.secretmessage.preview.model.MessageContent
import com.rpideas.secretmessage.preview.model.MessageDeliveryState
import com.rpideas.secretmessage.preview.model.PayloadValidator

/** Result of one state change: the new in-memory list and payloads to send once, best-effort. */
data class ChatTransition(
    val messages: List<EphemeralMessage>,
    val outbound: List<DecryptedChatPayload> = emptyList(),
)

/**
 * Pure delivery/timer state machine adapted from the WhisperPad ChatViewModel.
 *
 * - A sent message starts a 30-second undelivered timer; only an authenticated DeliveryAck stops it.
 * - The 60–300 second countdown starts only when the recipient views a delivered message.
 * - Nothing is queued or retried; expired items are removed and a purge request is offered once.
 *
 * Only payloads from an authenticated transport may be passed to [receive]. The preview has none.
 */
object ChatStateMachine {
    fun send(
        messages: List<EphemeralMessage>,
        messageId: String,
        content: MessageContent,
        viewSeconds: Int,
        nowElapsedMillis: Long,
    ): ChatTransition {
        if (!PayloadValidator.isValidMessageId(messageId) || !PayloadValidator.isValidContent(content)) {
            return ChatTransition(messages)
        }
        if (messages.any { it.id == messageId }) return ChatTransition(messages)
        val duration = ChatTimerPolicy.clampViewSeconds(viewSeconds)
        val message = EphemeralMessage(
            id = messageId,
            isFromLocalUser = true,
            content = content,
            configuredViewSeconds = duration,
            deliveryState = MessageDeliveryState.SENDING_UNDELIVERED,
            deadlineElapsedMillis = ChatTimerPolicy.undeliveredDeadline(nowElapsedMillis),
        )
        val payload = DecryptedChatPayload.ChatMessagePayload(messageId, duration, content)
        return ChatTransition(bounded(messages + message), listOf(payload))
    }

    fun receive(
        messages: List<EphemeralMessage>,
        payload: DecryptedChatPayload,
        nowElapsedMillis: Long,
        recipientIsViewing: Boolean,
    ): ChatTransition {
        if (!PayloadValidator.isValidPayload(payload)) return ChatTransition(messages)
        return when (payload) {
            is DecryptedChatPayload.ChatMessagePayload ->
                receiveChat(messages, payload, nowElapsedMillis, recipientIsViewing)
            is DecryptedChatPayload.DeliveryAck ->
                ChatTransition(messages.map { markDelivered(it, payload.messageId, nowElapsedMillis) })
            is DecryptedChatPayload.ViewedAck ->
                ChatTransition(messages.map { startSenderCountdown(it, payload.messageId, nowElapsedMillis) })
            is DecryptedChatPayload.PurgeNowSync ->
                ChatTransition(messages.filterNot { it.id == payload.messageId })
        }
    }

    /** The recipient is looking at the chat: start countdowns for delivered messages and acknowledge views. */
    fun recipientViewing(messages: List<EphemeralMessage>, nowElapsedMillis: Long): ChatTransition {
        val outbound = mutableListOf<DecryptedChatPayload>()
        val updated = messages.map { message ->
            if (!message.isFromLocalUser && message.deliveryState == MessageDeliveryState.DELIVERED_WAITING_VIEW) {
                outbound.add(DecryptedChatPayload.ViewedAck(message.id, message.configuredViewSeconds))
                message.copy(
                    deliveryState = MessageDeliveryState.VIEWED_COUNTING_DOWN,
                    deadlineElapsedMillis = ChatTimerPolicy.viewDeadline(nowElapsedMillis, message.configuredViewSeconds),
                )
            } else {
                message
            }
        }
        return ChatTransition(updated, outbound)
    }

    /**
     * Remove expired messages. Each removal offers one purge request so a peer that holds a copy
     * can drop it too; if the transport is unavailable the request is simply lost, never queued.
     */
    fun expire(messages: List<EphemeralMessage>, nowElapsedMillis: Long): ChatTransition {
        val expired = messages.filter { ChatTimerPolicy.isExpired(nowElapsedMillis, it.deadlineElapsedMillis) }
        if (expired.isEmpty()) return ChatTransition(messages)
        val expiredIds = expired.map { it.id }.toSet()
        return ChatTransition(
            messages = messages.filterNot { it.id in expiredIds },
            outbound = expired.map { DecryptedChatPayload.PurgeNowSync(it.id) },
        )
    }

    private fun receiveChat(
        messages: List<EphemeralMessage>,
        payload: DecryptedChatPayload.ChatMessagePayload,
        nowElapsedMillis: Long,
        recipientIsViewing: Boolean,
    ): ChatTransition {
        if (messages.any { it.id == payload.messageId }) return ChatTransition(messages)
        val duration = ChatTimerPolicy.clampViewSeconds(payload.viewDurationSeconds)
        val message = EphemeralMessage(
            id = payload.messageId,
            isFromLocalUser = false,
            content = payload.content,
            configuredViewSeconds = duration,
            deliveryState = if (recipientIsViewing) {
                MessageDeliveryState.VIEWED_COUNTING_DOWN
            } else {
                MessageDeliveryState.DELIVERED_WAITING_VIEW
            },
            deadlineElapsedMillis = if (recipientIsViewing) {
                ChatTimerPolicy.viewDeadline(nowElapsedMillis, duration)
            } else {
                null
            },
        )
        val outbound = mutableListOf<DecryptedChatPayload>(DecryptedChatPayload.DeliveryAck(payload.messageId))
        if (recipientIsViewing) outbound.add(DecryptedChatPayload.ViewedAck(payload.messageId, duration))
        return ChatTransition(bounded(messages + message), outbound)
    }

    private fun markDelivered(message: EphemeralMessage, messageId: String, nowElapsedMillis: Long): EphemeralMessage {
        val stillWaiting = message.id == messageId &&
            message.isFromLocalUser &&
            message.deliveryState == MessageDeliveryState.SENDING_UNDELIVERED &&
            !ChatTimerPolicy.isExpired(nowElapsedMillis, message.deadlineElapsedMillis)
        return if (stillWaiting) {
            message.copy(deliveryState = MessageDeliveryState.DELIVERED_WAITING_VIEW, deadlineElapsedMillis = null)
        } else {
            message
        }
    }

    private fun startSenderCountdown(message: EphemeralMessage, messageId: String, nowElapsedMillis: Long): EphemeralMessage {
        if (message.id != messageId || !message.isFromLocalUser) return message
        val awaitingView = message.deliveryState == MessageDeliveryState.DELIVERED_WAITING_VIEW ||
            (
                message.deliveryState == MessageDeliveryState.SENDING_UNDELIVERED &&
                    !ChatTimerPolicy.isExpired(nowElapsedMillis, message.deadlineElapsedMillis)
                )
        if (!awaitingView) return message
        // The sender's own configured duration governs; the peer's echo cannot extend it.
        return message.copy(
            deliveryState = MessageDeliveryState.VIEWED_COUNTING_DOWN,
            deadlineElapsedMillis = ChatTimerPolicy.viewDeadline(nowElapsedMillis, message.configuredViewSeconds),
        )
    }

    private fun bounded(messages: List<EphemeralMessage>): List<EphemeralMessage> =
        if (messages.size <= MAX_MESSAGES_IN_MEMORY) messages else messages.takeLast(MAX_MESSAGES_IN_MEMORY)
}
