package com.universalrp.cleansweep.notifications

import org.junit.Assert.*
import org.junit.Test

/** Source-only prepared cases; must run in both authorized build variants before publication. */
class NotificationReadingPolicyTest {
    @Test fun senderSanitized() { assertEquals("Kavi", NotificationReadingPolicy.name("  Kavi\n ")) }
    @Test fun senderBounded() { assertEquals(80, NotificationReadingPolicy.name("a".repeat(100))!!.length) }
    @Test fun senderOnlyByDefault() { assertFalse(NotificationReadingPolicy.Rule("app.example").fullMessage) }
    @Test fun allSendersOnlyWhenChosen() { assertTrue(NotificationReadingPolicy.matches(NotificationReadingPolicy.Rule("app.example"), "Kavi")) }
    @Test fun emptyContactFilterReadsNobody() { assertFalse(NotificationReadingPolicy.matches(NotificationReadingPolicy.Rule("app.example", contactsOnly = true), "Kavi")) }
    @Test fun selectedNamesMatch() { assertTrue(NotificationReadingPolicy.matches(NotificationReadingPolicy.Rule("app.example", contactsOnly = true, senders = setOf("KAVI", "Selvi")), " Kavi ")) }
    @Test fun unselectedNamesRejected() { assertFalse(NotificationReadingPolicy.matches(NotificationReadingPolicy.Rule("app.example", contactsOnly = true, senders = setOf("Kavi")), "Other")) }
    @Test fun freshTimestampOnly() { assertTrue(NotificationReadingPolicy.fresh(1_000, 1_100)); assertFalse(NotificationReadingPolicy.fresh(1_000, 1_000 + NotificationReadingPolicy.MAX_AGE_MS + 1)) }
    @Test fun unknownAndFutureTimeRejected() { assertFalse(NotificationReadingPolicy.fresh(0, 10)); assertFalse(NotificationReadingPolicy.fresh(100, 99)) }
    @Test fun otpAndPasswordsSuppressed() { assertTrue(NotificationReadingPolicy.sensitive("Your OTP is 123456")); assertTrue(NotificationReadingPolicy.sensitive("password reset")); assertTrue(NotificationReadingPolicy.sensitive("கடவுச்சொல்")) }
    @Test fun ordinaryTextNotSensitive() { assertFalse(NotificationReadingPolicy.sensitive("See you tomorrow")) }
    @Test fun speechOmitsAppTimeCountAndBodyByDefault() { assertEquals("Kavi", NotificationReadingPolicy.speech("Kavi", "Hello", false)); assertEquals("Kavi. Hello", NotificationReadingPolicy.speech("Kavi", "Hello", true)) }
}
