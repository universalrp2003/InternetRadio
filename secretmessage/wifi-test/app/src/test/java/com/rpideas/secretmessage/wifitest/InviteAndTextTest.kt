package com.rpideas.secretmessage.wifitest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class InviteAndTextTest {
    private val token = "0123456789abcdef0123456789abcdef"

    private val room = "fedcba9876543210fedcba9876543210"

    @Test
    fun invitesRoundTripAndAreFoundInSharedText() {
        val wifi = InviteLink.Wifi("192.168.1.20", 45123, token)
        assertEquals(wifi, InviteLink.parse(wifi.toUri()))
        val shared = "Secret Message TEST invite (same Wi-Fi only, one chat):\n${wifi.toUri()}\nThanks"
        assertEquals(wifi, InviteLink.parse(shared))
        assertEquals(wifi, InviteLink.parse("Open ${wifi.toUri()}."))

        val internet = InviteLink.Internet(room, token)
        assertEquals("secretmessage://chat?relay=$room&token=$token&v=2", internet.toUri())
        assertEquals(internet, InviteLink.parse("Secret Message TEST invite (one chat):\n${internet.toUri()}"))
    }

    @Test
    fun badInvitesAreRefusedWithAReason() {
        val cases = mapOf(
            "secretmessage://join?host=192.168.1.2&port=4000&token=$token&v=6" to "smoke test",
            "hello" to "No Secret Message invite",
            "secretmessage://chat?host=192.168.1.2&port=4000&token=$token&v=1" to "different version of Secret Message",
            "secretmessage://chat?relay=$room&token=$token&v=3" to "different version of Secret Message",
            "secretmessage://chat?host=8.8.8.8&port=4000&token=$token&v=2" to "private Wi-Fi address",
            "secretmessage://chat?host=192.168.1.2&port=70000&token=$token&v=2" to "port",
            "secretmessage://chat?host=192.168.1.2&port=4000&token=xyz&v=2" to "token",
            "secretmessage://chat?relay=$room&token=xyz&v=2" to "token",
            "secretmessage://chat?relay=abc&token=$token&v=2" to "relay room",
            "secretmessage://chat?relay=${room.uppercase()}&token=$token&v=2" to "relay room",
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
        val relayTimeout = RelayText.timedOut(host = true, waitMillis = 180_000)
        assertEquals("Nobody joined within 3 minutes. Start a new chat and send the new invite.", relayTimeout)
        assertEquals(relayTimeout, SessionText.lastResult("FAIL: $relayTimeout", null))
    }

    @Test
    fun reportsSayWhereARelayChatWent() {
        val pass = "PASS: both people confirmed the same MLS safety code, then two separate devices exchanged fixed " +
            "MLS test messages directly over local Wi-Fi. This phone now remembers the other phone's test key. TEST ONLY."
        val relayed = SessionText.forReport(pass, internet = true)
        assertTrue(relayed, relayed.contains("through the Internet relay") && !relayed.contains("local Wi-Fi"))
        assertFalse(relayed, relayed.contains("TEST ONLY"))
        val direct = SessionText.forReport(pass, internet = false)
        assertTrue(direct, direct.contains("directly over local Wi-Fi") && !direct.contains("TEST ONLY"))
        assertEquals("FAIL: offline", SessionText.forReport("FAIL: offline", internet = true))
    }

    @Test
    fun relayEventsReadPlainly() {
        assertTrue(RelayText.closed(4001, "The other phone left").startsWith("The other phone left"))
        assertTrue(RelayText.closed(4002, "Time limit").contains("time limit"))
        assertEquals("The relay connection closed (code 1011: oops).", RelayText.closed(1011, "oops"))
        assertTrue(RelayText.failed(guest = true, httpCode = 404, error = "ProtocolException", message = null).contains("no longer waiting"))
        assertTrue(RelayText.failed(guest = false, httpCode = 409, error = "ProtocolException", message = null).contains("already in use"))
        val offline = RelayText.failed(guest = true, httpCode = null, error = "UnknownHostException", message = "no address")
        assertEquals(
            "Could not reach the Secret Message relay (UnknownHostException: no address). Check that this phone is online.",
            offline,
        )
        assertEquals(
            "Internet relay: connected in 0.4 s; other phone joined after 21.3 s; ended: The relay connection closed.",
            RelayText.summary(400, 21_300, RelayText.closed(1000, "")),
        )
        assertEquals("Internet relay: not connected", RelayText.summary(null, null, null))
        assertTrue(RelayLink.RELAY_URL.startsWith("wss://") && RelayLink.RELAY_URL.endsWith(".workers.dev"))
    }
}
