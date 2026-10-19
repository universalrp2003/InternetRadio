package com.rpideas.secretmessage.preview.ui

import com.rpideas.secretmessage.preview.chat.UNDELIVERED_TTL_MILLIS

/**
 * Words on the chat screen that differ between builds. The local-only preview uses [Preview]; the
 * app that connects two phones passes its own wording, so these screens never describe a connection
 * they do not have. A blank [badge], notice ([noticeTitle] and [noticeBody]) or [footer] is left out.
 */
internal data class ChatWording(
    val subtitle: String,
    val badge: String,
    val noticeTitle: String,
    val noticeBody: String,
    val pairingLabel: String,
    val footer: String,
    val emptyTitle: String,
    val emptyBody: String,
    val settingsButtonDescription: String,
) {
    companion object {
        val Preview = ChatWording(
            subtitle = "LOCAL UI PREVIEW",
            badge = "NOT SECURE",
            noticeTitle = "NOT SECURE · LOCAL PREVIEW ONLY",
            noticeBody = "This build does not connect to another phone or encrypt messages. Do not enter sensitive content.",
            pairingLabel = "Pairing not enabled",
            footer = "No connection in this preview · unsent messages are removed after " +
                "${UNDELIVERED_TTL_MILLIS / 1_000L} seconds · not encrypted",
            emptyTitle = "Your preview chat",
            emptyBody = "Type with the built-in English keyboard, or open the drawing pad to draw letters in any " +
                "language or a picture. Nothing leaves this phone; unsent messages are removed after 30 seconds.",
            settingsButtonDescription = "Preview settings",
        )
    }
}

/** Words on the settings screen that differ between builds; see [ChatWording]. */
internal data class SettingsWording(
    val title: String,
    val timerNote: String,
    val toneNote: String,
    val aboutTitle: String,
    val about: (versionLabel: String) -> String,
) {
    companion object {
        val Preview = SettingsWording(
            title = "Preview settings",
            timerNote = "Choose 60 to 300 seconds; the timer cannot be turned off. In the planned secure app the countdown " +
                "starts only when the other phone views an authenticated delivery. This preview cannot deliver " +
                "messages, so the countdown never starts here.",
            toneNote = "Six original tones generated on this phone. They play at the notification volume. " +
                "Message notifications are not enabled in this preview and would never show message content.",
            aboutTitle = "What this preview is",
            about = { versionLabel ->
                "Secret Message $versionLabel is a local-only debug preview of the WhisperPad product screens: " +
                    "built-in keyboard, drawing pad, timers, tones and app lock. No MLS transport, pairing or network " +
                    "permission is included, and no message is saved. It is not a secure messenger."
            },
        )
    }
}
