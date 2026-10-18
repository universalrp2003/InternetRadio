package com.rpideas.secretmessage.wifitest

import com.rpideas.secretmessage.wifitest.RingPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

/** What a phone does with a call that arrives (1.1.0 phone test: both phones called each other). */
class RingPolicyTest {
    private val lower = "1111aaaa2222bbbb"
    private val higher = "ffff00001111eeee"
    private val other = "5555666677778888"

    private fun calling(link: String = RelayText.WAITING, stopping: Boolean = false) = ConnectionState.Hosting(
        invite = "secretmessage://chat?relay=00112233445566778899aabbccddeeff&token=00112233445566778899aabbccddeeff&v=2",
        internet = true,
        link = link,
        stopping = stopping,
        calling = "Name",
    )

    @Test
    fun anIdlePhoneShowsTheRequest() {
        assertEquals(Decision.SHOW, RingPolicy.decide(ConnectionState.Idle(null), false, null, higher, lower))
    }

    @Test
    fun whenBothPhonesCallEachOtherTheLowerDeviceJoinsAndTheHigherKeepsWaiting() {
        // Each phone decides alone and they agree: exactly one joins the other's call.
        assertEquals(Decision.ANSWER_NOW, RingPolicy.decide(calling(), true, higher, higher, lower))
        assertEquals(Decision.KEEP_CALLING, RingPolicy.decide(calling(), true, lower, lower, higher))
        // Before this phone's own call reached the relay, the same rule applies.
        assertEquals(Decision.ANSWER_NOW, RingPolicy.decide(calling(RelayText.CONNECTING), true, higher, higher, lower))
    }

    @Test
    fun aCallFromSomeoneElseOrWhileStoppingIsShownSoItCanBeAnswered() {
        assertEquals(Decision.SHOW, RingPolicy.decide(calling(), true, other, higher, lower))
        assertEquals(Decision.SHOW, RingPolicy.decide(calling(stopping = true), true, higher, higher, lower))
        // Waiting for an invite (no call) is interrupted by answering, too.
        val inviting = ConnectionState.Hosting("secretmessage://chat?relay=x", internet = true, link = RelayText.WAITING)
        assertEquals(Decision.SHOW, RingPolicy.decide(inviting, true, null, higher, lower))
    }

    @Test
    fun aChatThatIsStartingOrOpenIsBusy() {
        val prompt = SafetyPrompt("12345 67890 12345 67890", PeerStatus.KNOWN, higher)
        assertEquals(Decision.BUSY, RingPolicy.decide(calling(RelayText.PAIRED), true, higher, higher, lower))
        assertEquals(Decision.BUSY, RingPolicy.decide(ConnectionState.Joining(internet = true), true, null, higher, lower))
        assertEquals(Decision.BUSY, RingPolicy.decide(ConnectionState.Comparing(prompt), true, null, higher, lower))
        assertEquals(Decision.BUSY, RingPolicy.decide(ConnectionState.Connected(prompt), true, null, higher, lower))
        assertEquals(Decision.BUSY, RingPolicy.decide(ConnectionState.Ending("Ending"), true, null, higher, lower))
        assertEquals(Decision.BUSY, RingPolicy.decide(ConnectionState.Idle(null), true, null, higher, lower))
    }
}
