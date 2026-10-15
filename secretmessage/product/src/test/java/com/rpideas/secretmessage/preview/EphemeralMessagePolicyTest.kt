package com.rpideas.secretmessage.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EphemeralMessagePolicyTest {
    @Test
    fun previewTtlIsSixtySecondsFromLocalSend() {
        val sentAt = 12_345L
        val expiresAt = EphemeralMessagePolicy.expiresAt(sentAt)

        assertEquals(sentAt + 60_000L, expiresAt)
        assertFalse(EphemeralMessagePolicy.isExpired(expiresAt - 1L, expiresAt))
        assertTrue(EphemeralMessagePolicy.isExpired(expiresAt, expiresAt))
    }

    @Test
    fun visibleMessagesExcludesExpiredItems() {
        val active = EphemeralPreviewMessage(
            id = 1L,
            text = "local-only preview text",
            drawing = emptyList(),
            expiresAtElapsedRealtime = 9_001L,
        )
        val expired = EphemeralPreviewMessage(
            id = 2L,
            text = null,
            drawing = listOf(listOf(PreviewPoint(0.1f, 0.2f))),
            expiresAtElapsedRealtime = 8_999L,
        )

        assertEquals(listOf(active), EphemeralMessagePolicy.visibleMessages(listOf(active, expired), 9_000L))
    }

    @Test
    fun remainingTimeRoundsUpAndNeverGoesNegative() {
        assertEquals(60, EphemeralMessagePolicy.remainingSeconds(0L, 60_000L))
        assertEquals(1, EphemeralMessagePolicy.remainingSeconds(59_001L, 60_000L))
        assertEquals(0, EphemeralMessagePolicy.remainingSeconds(60_000L, 60_000L))
        assertEquals(0, EphemeralMessagePolicy.remainingSeconds(61_000L, 60_000L))
    }
}
