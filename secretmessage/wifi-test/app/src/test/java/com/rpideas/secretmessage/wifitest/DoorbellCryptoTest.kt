package com.rpideas.secretmessage.wifitest

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The 1.1.0 doorbell: inbox IDs as the relay checks them, cards, and encrypted rings. */
class DoorbellCryptoTest {
    private val now = 1_790_000_000_000L
    private val room = "00112233445566778899aabbccddeeff"
    private val token = "ffeeddccbbaa99887766554433221100"
    private val nonce = ByteArray(12) { (it + 1).toByte() }

    private val alice = ContactCard(
        ContactBook.inboxIdFor("0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20"),
        "a1".repeat(32),
    )
    private val bob = ContactCard(
        ContactBook.inboxIdFor("2122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f40"),
        "b2".repeat(32),
    )
    private val carol = ContactCard(
        ContactBook.inboxIdFor("4142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f60"),
        "c3".repeat(32),
    )

    @Test
    fun inboxIdMatchesTheRelayDerivation() {
        // Computed independently with Python hashlib and Node crypto; the relay derives the same.
        assertEquals("d605539ea13d5954cb264cd065186b55", alice.inboxId)
    }

    @Test
    fun bothPhonesDeriveTheSamePairKey() {
        assertArrayEquals(ContactBook.pairKey(alice.inboxKey, bob.inboxKey), ContactBook.pairKey(bob.inboxKey, alice.inboxKey))
    }

    @Test
    fun aRingFromARememberedPhoneOpensWithItsRoomAndToken() {
        val ring = ContactBook.seal(alice.inboxKey, bob, room, token, now, nonce)
        assertEquals(ContactBook.RING_BYTES, ring.size)
        val opened = ContactBook.open(bob, listOf(Contact("aaaa000000000001", "Alice", alice)), ring, now + 30_000, SeenNonces())
        assertNotNull(opened)
        assertEquals("aaaa000000000001", opened!!.contact.deviceId)
        assertEquals(room, opened.room)
        assertEquals(token, opened.token)
    }

    @Test
    fun aRingIsRefusedByTheWrongPhoneAfterTamperingOrFromAStranger() {
        val ring = ContactBook.seal(alice.inboxKey, bob, room, token, now, nonce)
        val aliceContact = listOf(Contact("aaaa000000000001", "", alice))
        // Carol does not share Alice's pair key; Bob's ring does not open at Alice's own inbox.
        assertNull(ContactBook.open(carol, aliceContact, ring, now, SeenNonces()))
        assertNull(ContactBook.open(alice, listOf(Contact("bbbb000000000002", "", bob)), ring, now, SeenNonces()))
        val tampered = ring.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertNull(ContactBook.open(bob, aliceContact, tampered, now, SeenNonces()))
        val strangerRing = ContactBook.seal(carol.inboxKey, bob, room, token, now, nonce)
        assertNull(ContactBook.open(bob, aliceContact, strangerRing, now, SeenNonces()))
        assertNull(ContactBook.open(bob, aliceContact, ring.copyOf(ring.size - 1), now, SeenNonces()))
    }

    @Test
    fun oldRingsAndRepeatedRingsAreRefused() {
        val ring = ContactBook.seal(alice.inboxKey, bob, room, token, now, nonce)
        val contacts = listOf(Contact("aaaa000000000001", "", alice))
        assertNull(ContactBook.open(bob, contacts, ring, now + 5 * 60_000 + 1, SeenNonces()))
        assertNull(ContactBook.open(bob, contacts, ring, now - 5 * 60_000 - 1, SeenNonces()))
        val seen = SeenNonces()
        assertNotNull(ContactBook.open(bob, contacts, ring, now, seen))
        assertNull(ContactBook.open(bob, contacts, ring, now + 1_000, seen))
    }

    @Test
    fun cardsRoundTripAndOtherPayloadsAreNotCards() {
        val text = PayloadCodec.encodeCard(alice)
        assertEquals(alice, PayloadCodec.decodeCard(text))
        // A card is never mistaken for a chat payload, and chat payloads are never cards.
        assertNull(PayloadCodec.decode(text))
        assertNull(PayloadCodec.decodeCard("AQI="))
        assertNull(PayloadCodec.decodeCard("not base64!"))
        assertNull(PayloadCodec.decodeCard(""))
    }

    @Test
    fun forgettingOnePhoneKeepsTheOthers() {
        val pins = "aaaa000000000001 0a0b\nbbbb000000000002 0c0d\n"
        assertEquals("bbbb000000000002 0c0d\n", PeerPins.forget(pins, "aaaa000000000001"))
        assertEquals("", PeerPins.forget("aaaa000000000001 0a0b\n", "aaaa000000000001"))
    }
}
