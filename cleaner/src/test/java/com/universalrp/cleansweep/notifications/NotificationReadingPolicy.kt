package com.universalrp.cleansweep.notifications

import java.util.Locale

/** Pure local policy. App-supplied sender names are not verified contact identities. */
object NotificationReadingPolicy {
    const val MAX_BODY = 800
    const val MAX_AGE_MS = 120_000L
    data class Rule(val packageName: String, val fullMessage: Boolean = false, val contactsOnly: Boolean = false,
        val senders: Set<String> = emptySet())
    fun name(value: String?): String? = value?.replace(Regex("[\\p{Cntrl}]"), " ")?.trim()?.take(80)?.takeIf { it.isNotBlank() }
    fun aggregateTitle(value: String): Boolean = Regex("(?i)^\\d+\\s+(?:new\\s+)?(?:messages?|notifications?|chats?)\\b|^\\d+\\s*செய்தி").containsMatchIn(value.trim())
    fun matches(rule: Rule, sender: String): Boolean = !rule.contactsOnly || rule.senders.any { it.trim().equals(sender.trim(), true) }
    fun fresh(posted: Long, now: Long): Boolean = posted > 0 && now >= posted && now - posted <= MAX_AGE_MS
    fun sensitive(text: String): Boolean = Regex("(?i)\\b(otp|one[- ]time|verification code|security code|password|passcode|pin|upi)\\b|கடவுச்சொல்|ஒருமுறை.*குறியீடு|சரிபார்ப்ப.*குறியீடு").containsMatchIn(text) || text.trim().matches(Regex("[0-9]{4,8}"))
    fun speech(sender: String, body: String?, full: Boolean): String =
        name(sender).orEmpty() + if (full && !body.isNullOrBlank()) ". " + body.trim().take(MAX_BODY) else ""
    fun normalizedSender(value: String): String = value.trim().lowercase(Locale.ROOT)
}
