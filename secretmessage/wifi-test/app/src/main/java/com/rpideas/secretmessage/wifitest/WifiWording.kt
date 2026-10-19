package com.rpideas.secretmessage.wifitest

import com.rpideas.secretmessage.preview.ui.ChatWording
import com.rpideas.secretmessage.preview.ui.SettingsWording

/**
 * Words for the product screens in the published app. Kept short so the chat keeps its space (owner
 * request after the 0.4.0 Internet test); the app is honest that it has not been independently
 * security-reviewed, on the start screen and in About. From 1.1.0 they follow the app's language,
 * so they are built each time they are read.
 */
internal object WifiWording {
    const val SUBTITLE = "End-to-end encrypted"
    const val NOT_REVIEWED = "Not independently security-reviewed · messages are never saved"

    val chat: ChatWording
        get() = ChatWording(
            subtitle = tr(SUBTITLE),
            badge = "",
            noticeTitle = "",
            noticeBody = "",
            pairingLabel = tr("Safety code confirmed"),
            footer = "",
            emptyTitle = tr("Connected"),
            emptyBody = tr("Messages disappear from both phones after they are viewed and their timer ends."),
            settingsButtonDescription = "Settings",
        )

    val settings: SettingsWording
        get() = SettingsWording(
            title = tr("Settings"),
            timerNote = tr(
                "60 to 300 seconds; it cannot be turned off. It starts on both phones when the other person " +
                    "views your message. Undelivered messages are removed after 30 seconds.",
            ),
            toneNote = tr(
                "The chosen tone plays when a message arrives. While a chat is open or the app is ready for chats, " +
                    "a notification that says only \"Secret Message\" and its state keeps the connection when you " +
                    "switch apps; it never shows names or messages.",
            ),
            aboutTitle = tr("About Secret Message"),
            about = { versionLabel ->
                tr(
                    "Secret Message {0} sends text, drawn letters and pictures between two phones, end-to-end " +
                        "encrypted with MLS (mls-rs with AWS-LC). Both people compare a 20-digit safety code before " +
                        "the first chat; remembered phones with an unchanged key connect directly and can call each " +
                        "other. Messages are never saved and disappear after they are viewed. Over the Internet the " +
                        "phones connect through a relay that forwards only encrypted data and keeps nothing; on the " +
                        "same Wi-Fi they can connect directly. This app has not been independently security-reviewed, " +
                        "and mls-rs has not had a full third-party audit.",
                    versionLabel,
                )
            },
        )
}
