package com.rpideas.secretmessage.wifitest

import com.rpideas.secretmessage.preview.ui.ChatWording
import com.rpideas.secretmessage.preview.ui.SettingsWording

/**
 * Words for the product screens in the published app. Kept short so the chat keeps its space (owner
 * request after the 0.4.0 Internet test); the app is honest that it has not been independently
 * security-reviewed, on the start screen and in About.
 */
internal object WifiWording {
    const val SUBTITLE = "End-to-end encrypted"
    const val NOT_REVIEWED = "Not independently security-reviewed · messages are never saved"

    val chat = ChatWording(
        subtitle = SUBTITLE,
        badge = "",
        noticeTitle = "",
        noticeBody = "",
        pairingLabel = "Safety code confirmed",
        footer = "",
        emptyTitle = "Connected",
        emptyBody = "Messages disappear from both phones after they are viewed and their timer ends.",
        settingsButtonDescription = "Settings",
    )

    val settings = SettingsWording(
        title = "Settings",
        timerNote = "60 to 300 seconds; it cannot be turned off. It starts on both phones when the other person " +
            "views your message. Undelivered messages are removed after 30 seconds.",
        toneNote = "The chosen tone plays when a message arrives. While a chat is open, a notification that says " +
            "only \"Secret Message\" and the chat state keeps it connected when you switch apps; it never shows " +
            "names or messages.",
        aboutTitle = "About Secret Message",
        about = { versionLabel ->
            "Secret Message $versionLabel sends text, drawn letters and pictures between two phones, end-to-end " +
                "encrypted with MLS (mls-rs with AWS-LC). Both people compare a 20-digit safety code before the " +
                "chat opens. Messages are never saved and disappear after they are viewed. Over the Internet the " +
                "phones connect through a relay that forwards only encrypted data and keeps nothing; on the same " +
                "Wi-Fi they can connect directly. This app has not been independently security-reviewed, and " +
                "mls-rs has not had a full third-party audit."
        },
    )
}
