package com.rpideas.secretmessage.wifitest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class InviteAndTextTest {
    private val token = "0123456789abcdef0123456789abcdef"

    @Test
    fun invitesRoundTripAndAreFoundInSharedText() {
        val invite = InviteLink("192.168.1.20", 45123, token)
        assertEquals(invite, InviteLink.parse(invite.toUri()))
        val shared = "Secret Message Wi-Fi TEST invite (same Wi-Fi only, one chat):\n${invite.toUri()}\nThanks"
        assertEquals(invite, InviteLink.parse(shared))
        assertEquals(invite, InviteLink.parse("Open ${invite.toUri()}."))
    }

    @Test
    fun badInvitesAreRefusedWithAReason() {
        val cases = mapOf(
            "secretmessage://join?host=192.168.1.2&port=4000&token=$token&v=6" to "smoke test",
            "hello" to "No Secret Message Wi-Fi invite",
            "secretmessage://chat?host=192.168.1.2&port=4000&token=$token&v=2" to "different Wi-Fi test build",
            "secretmessage://chat?host=8.8.8.8&port=4000&token=$token&v=1" to "private Wi-Fi address",
            "secretmessage://chat?host=192.168.1.2&port=70000&token=$token&v=1" to "port",
            "secretmessage://chat?host=192.168.1.2&port=4000&token=xyz&v=1" to "token",
        )
        cases.forEach { (text, reason) ->
            try {
                InviteLink.parse(text)
                fail("accepted $text")
            } catch (refused: IllegalArgumentException) {
                assertTrue("${refused.message} should mention $reason", refused.message.orEmpty().contains(reason))
            }
        }
    }

    @Test
    fun onlyPrivateIpv4AddressesAreAccepted() {
        listOf("10.0.0.1", "172.16.5.4", "172.31.255.255", "192.168.0.10").forEach { assertTrue(it, InviteLink.isPrivateIpv4(it)) }
        listOf("172.32.0.1", "8.8.8.8", "192.169.1.1", "256.1.1.1", "10.0.0", "10.0.0.1.2", "a.b.c.d", "").forEach {
            assertFalse(it, InviteLink.isPrivateIpv4(it))
        }
    }

    @Test
    fun rememberedPhonesKeepOneKeyPerDevice() {
        var pins = ""
        pins = PeerPins.remember(pins, "aaaaaaaaaaaaaaaa 0101")
        pins = PeerPins.remember(pins, "bbbbbbbbbbbbbbbb 0202")
        pins = PeerPins.remember(pins, "aaaaaaaaaaaaaaaa 0303")
        assertEquals(2, PeerPins.count(pins))
        assertTrue(pins.contains("aaaaaaaaaaaaaaaa 0303") && !pins.contains("0101"))
        assertEquals(pins, PeerPins.remember(pins, "malformed"))
        assertEquals("0123 4567 89ab cdef", PeerPins.formatDeviceId("0123456789abcdef"))
    }

    @Test
    fun promptsAndResultsReadPlainly() {
        assertEquals(
            SafetyPrompt("12345 67890 12345 67890", PeerStatus.CHANGED, "0123456789abcdef"),
            SafetyPrompt.parse("12345 67890 12345 67890|CHANGED|0123456789abcdef"),
        )
        assertNull(SafetyPrompt.parse("no prompt"))
        assertEquals(
            "You ended the chat.",
            SessionText.lastResult("PASS: …", "Ended on this phone. Sent 3 (3 delivered), received 2, in 40 s."),
        )
        assertEquals(
            "The other phone ended the chat.",
            SessionText.chatEnd("Ended on the other phone. Sent 1 (1 delivered), received 1, in 9 s."),
        )
        val dropped = SessionText.chatEnd(
            "Stopped. Could not read a typed test message: Connection reset by peer (os error 104). The connection " +
                "was closed. Keep the test app open on both phones until the test ends: on Android 15 and later, an " +
                "app loses its connections a few seconds after it leaves the screen (screen lock or switching apps). " +
                "Sent 1 (1 delivered), received 1, in 38 s.",
        )
        assertTrue(dropped, dropped.startsWith("The chat stopped: Could not read a typed test message"))
        assertTrue(dropped, dropped.endsWith(SessionText.CONNECTION_HINT) && !dropped.contains("Keep the test app"))
        assertEquals(
            "The other phone reported that the safety codes differ, so no test messages were exchanged.",
            SessionText.lastResult(
                "STOPPED: the other phone reported that the safety codes differ, so no test messages were exchanged. TEST ONLY.",
                null,
            ),
        )
        assertEquals(
            "You stopped waiting for the other phone.",
            SessionText.lastResult("FAIL: Stopped waiting for the other phone to join.", null),
        )
        assertTrue(SessionText.lastResult("FAIL: Timed out waiting for a LAN peer to join the test invite.", null).startsWith("Nobody joined"))
    }
}
