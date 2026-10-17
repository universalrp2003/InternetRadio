package com.rpideas.secretmessage.wifitest

import com.rpideas.secretmessage.preview.ui.ChatWording
import com.rpideas.secretmessage.preview.ui.SettingsWording

/** Words for the product screens in this Wi-Fi test build: connected, but not security-reviewed. */
internal object WifiWording {
    const val TEST_LABEL = "WI-FI TEST BUILD"
    const val BADGE = "NOT REVIEWED"
    const val NOTICE_TITLE = "TEST BUILD · NOT SECURITY-REVIEWED"
    const val NOTICE_BODY =
        "Messages go directly between two phones on the same Wi-Fi, inside an MLS session that starts only after " +
            "both people confirm the same safety code. No security expert has reviewed this build. Do not send " +
            "anything private."

    val chat = ChatWording(
        subtitle = TEST_LABEL,
        badge = BADGE,
        noticeTitle = NOTICE_TITLE,
        noticeBody = NOTICE_BODY,
        pairingLabel = "Safety code confirmed",
        footer = "Same Wi-Fi only · kept only in memory · unsent messages are removed after 30 seconds · " +
            "viewed messages disappear on both phones when their timer ends",
        emptyTitle = "You are connected",
        emptyBody = "Type with the built-in English keyboard, or open the drawing pad to draw letters in any " +
            "language or a picture. Messages are never saved; each one disappears from both phones after it is " +
            "viewed and its timer ends, or when the chat ends.",
        settingsButtonDescription = "Settings",
    )

    val settings = SettingsWording(
        title = "Settings",
        timerNote = "Choose 60 to 300 seconds; the timer cannot be turned off. It starts on both phones when the " +
            "other person views your message. Undelivered messages are removed after 30 seconds.",
        toneNote = "Six original tones generated on this phone; the chosen tone plays when a message arrives. While a " +
            "chat is starting or open, a notification that says only \"Secret Message\" and the chat state keeps " +
            "the connection when you switch apps. It never shows names or messages.",
        aboutTitle = "What this test build is",
        about = { versionLabel ->
            "Secret Message $versionLabel is a TEST build of the WhisperPad screens connected to the mls-rs MLS " +
                "engine. Two phones on the same Wi-Fi connect directly after both people confirm a 20-digit safety " +
                "code. It has not been reviewed by a security expert, works only on the same Wi-Fi, saves no " +
                "messages and is not published. Do not use it for private messages."
        },
    )
}
