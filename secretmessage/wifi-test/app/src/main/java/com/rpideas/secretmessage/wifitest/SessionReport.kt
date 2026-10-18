package com.rpideas.secretmessage.wifitest

import com.rpideas.secretmessage.preview.model.DecryptedChatPayload
import com.rpideas.secretmessage.preview.model.InlineSegment

/**
 * Facts about the last chat for the copied test report: how it went and how many payloads of each
 * kind passed. It holds counts only, never message text or drawings, and lives only in memory.
 */
internal class SessionReport {
    private var role = "none"
    private var connection = "Not reached"
    private var safety = "Not reached"
    private var peer = "Not reached"
    private var result = "No chat yet"
    private var chatSummary: String? = null
    private var reachedChat = false
    private val sentCounts = Counts()
    private val receivedCounts = Counts()
    private var refused = 0
    private var engineAcknowledgements = 0
    private var sendFailures = 0
    private var lastSendFailure: String? = null
    private var cardSent = false
    private var cardReceived = false
    private var ring = "none"

    @Synchronized
    fun start(role: String) {
        this.role = role
        connection = "Not reached"
        safety = "Not reached"
        peer = "Not reached"
        result = "Running"
        chatSummary = null
        reachedChat = false
        sentCounts.clear()
        receivedCounts.clear()
        refused = 0
        engineAcknowledgements = 0
        sendFailures = 0
        lastSendFailure = null
        cardSent = false
        cardReceived = false
        ring = "none"
    }

    /** This phone's doorbell went to the other phone inside the chat (from 1.1.0). */
    @Synchronized
    fun cardSent() {
        cardSent = true
    }

    @Synchronized
    fun cardReceived() {
        cardReceived = true
    }

    /** How ringing a remembered phone went (calls only). */
    @Synchronized
    fun rang(result: String) {
        ring = result
    }

    /** How the chat travelled: the relay's timings and how it ended, or the direct Wi-Fi link. */
    @Synchronized
    fun connection(text: String) {
        connection = text
    }

    @Synchronized
    fun prompted(prompt: SafetyPrompt) {
        safety = "Shown on this phone: ${prompt.code} (not answered)"
        peer = "${prompt.peerStatus} (device ${PeerPins.formatDeviceId(prompt.peerDevice)})"
    }

    @Synchronized
    fun answered(codesMatch: Boolean, code: String) {
        safety = (if (codesMatch) "Confirmed on this phone: " else "Reported different on this phone: ") + code
    }

    @Synchronized
    fun connected() {
        reachedChat = true
    }

    @Synchronized
    fun sent(payload: DecryptedChatPayload) = sentCounts.add(payload)

    @Synchronized
    fun received(payload: DecryptedChatPayload) = receivedCounts.add(payload)

    @Synchronized
    fun refused() {
        refused++
    }

    @Synchronized
    fun engineAcknowledged() {
        engineAcknowledgements++
    }

    @Synchronized
    fun sendFailed(reason: String) {
        sendFailures++
        lastSendFailure = reason
    }

    @Synchronized
    fun chatEnded(summary: String) {
        chatSummary = summary
    }

    @Synchronized
    fun chatSummary(): String? = chatSummary

    @Synchronized
    fun finished(result: String) {
        this.result = result
    }

    /** The report lines after the device lines; `open` describes a chat that is still running. */
    @Synchronized
    fun lines(open: Boolean): List<String> = listOf(
        "Role: $role",
        "Connection: $connection",
        "Last session: $result",
        "Safety code check: $safety",
        "Other phone: $peer",
        "Chat: " + when {
            chatSummary != null -> chatSummary
            open && reachedChat -> "Open"
            reachedChat -> "Ended without a summary"
            else -> "Not reached"
        },
        "Sent: ${sentCounts.describe()}",
        "Received: ${receivedCounts.describe()}",
        "Engine acknowledgements: $engineAcknowledgements; refused payloads: $refused; " +
            "send failures: $sendFailures" + (lastSendFailure?.let { " (last: $it)" } ?: ""),
        "Doorbell cards: sent ${if (cardSent) "yes" else "no"}, received ${if (cardReceived) "yes" else "no"}; " +
            "ring: $ring",
    )

    private class Counts {
        var messages = 0
        var withText = 0
        var withDrawnLetters = 0
        var pictures = 0
        var deliveryNotices = 0
        var viewNotices = 0
        var removals = 0

        fun clear() {
            messages = 0
            withText = 0
            withDrawnLetters = 0
            pictures = 0
            deliveryNotices = 0
            viewNotices = 0
            removals = 0
        }

        fun add(payload: DecryptedChatPayload) {
            when (payload) {
                is DecryptedChatPayload.ChatMessagePayload -> {
                    messages++
                    val segments = payload.content.inlineSegments
                    if (payload.content.fullPictureStrokes.isNotEmpty()) pictures++
                    if (segments.any { it is InlineSegment.EnglishText }) withText++
                    if (segments.any { it is InlineSegment.DrawnGlyph }) withDrawnLetters++
                }
                is DecryptedChatPayload.DeliveryAck -> deliveryNotices++
                is DecryptedChatPayload.ViewedAck -> viewNotices++
                is DecryptedChatPayload.PurgeNowSync -> removals++
            }
        }

        fun describe(): String =
            "$messages messages ($withText with text, $withDrawnLetters with drawn letters, $pictures pictures), " +
                "$deliveryNotices delivery notices, $viewNotices view notices, $removals removal notices"
    }
}
