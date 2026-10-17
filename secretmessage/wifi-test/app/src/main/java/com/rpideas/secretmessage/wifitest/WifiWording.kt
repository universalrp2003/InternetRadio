package com.rpideas.secretmessage.wifitest

import com.rpideas.secretmessage.preview.ui.ChatWording
import com.rpideas.secretmessage.preview.ui.SettingsWording

/** Words for the product screens in this test build: connected, but not security-reviewed. */
internal object WifiWording {
    const val TEST_LABEL = "TEST BUILD"
    const val BADGE = "NOT REVIEWED"
    const val NOTICE_TITLE = "TEST BUILD · NOT SECURITY-REVIEWED"
    const val NOTICE_BODY =
        "Messages travel inside an MLS session that starts only after both people confirm the same safety code: " +
            "over the Internet through the owner's test relay, which passes along only encrypted data, or directly " +
            "on the same Wi-Fi. No security expert has reviewed this build. Do not send anything private."

    val chat = ChatWording(
        subtitle = TEST_LABEL,
        badge = BADGE,
        noticeTitle = NOTICE_TITLE,
        noticeBody = NOTICE_BODY,
        pairingLabel = "Safety code confirmed",
        footer = "Kept only in memory · unsent messages are removed after 30 seconds · viewed messages " +
            "disappear on both phones when their timer ends",
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
                "engine. Two phones connect over the Internet through the owner's test relay, which forwards only " +
                "encrypted data and keeps nothing, or directly on the same Wi-Fi, and the chat opens only after both " +
                "people confirm a 20-digit safety code. It has not been reviewed by a security expert, saves no " +
                "messages and is not published. Do not use it for private messages."
        },
    )
}
