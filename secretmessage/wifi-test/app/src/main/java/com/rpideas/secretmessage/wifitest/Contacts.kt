package com.rpideas.secretmessage.wifitest

import android.content.Context
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * A phone's doorbell, exchanged inside the MLS chat when it opens (never shown in the chat).
 * [inboxId] names the phone's inbox at the relay; [inboxKey] lets the two phones derive a key that
 * only they know, so only a remembered phone can ring and the relay cannot read or fake a ring.
 */
internal data class ContactCard(val inboxId: String, val inboxKey: String) {
    fun isValid(): Boolean = HEX32.matches(inboxId) && HEX64.matches(inboxKey)

    companion object {
        val HEX32 = Regex("^[0-9a-f]{32}$")
        val HEX64 = Regex("^[0-9a-f]{64}$")
    }
}

/** A remembered phone: its device ID, the local name chosen on this phone, and its doorbell. */
internal data class Contact(val deviceId: String, val name: String, val card: ContactCard?) {
    /** The local name, or the device ID if no name was given. */
    val label: String get() = name.ifBlank { tr("Phone {0}", PeerPins.formatDeviceId(deviceId)) }
}

/** What a ring carries: the relay room the caller waits in and the token for the MLS session. */
internal data class RingInvite(val contact: Contact, val room: String, val token: String)

/**
 * Remembered phones with their local names and doorbells, and this install's own doorbell. Kept
 * in app-private storage until Forget or Reset (owner request for 1.1.0). The own doorbell gets a
 * new inbox and key when this phone's key is reset.
 *
 * Ring format (76 bytes): pair ID (8) ‖ nonce (12) ‖ AES-256-GCM(room 16 ‖ token 16 ‖ time 8),
 * with the recipient's inbox ID as associated data. The pair key is HMAC-SHA256 over both phones'
 * inbox keys in sorted order, so either phone derives the same key and nobody else can.
 */
internal class ContactBook(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val pinsSource = IdentityStore(context.applicationContext)
    private val mutableContacts = MutableStateFlow(load())
    val contacts: StateFlow<List<Contact>> = mutableContacts.asStateFlow()

    /** This phone's inbox secret (only the inbox owner sends it, to the relay). */
    @Synchronized
    fun ownSecret(): String {
        prefs.getString(KEY_SECRET, null)?.takeIf { ContactCard.HEX64.matches(it) }?.let { return it }
        val secret = IdentityStore.randomHex(32)
        val key = IdentityStore.randomHex(32)
        prefs.edit().putString(KEY_SECRET, secret).putString(KEY_OWN_KEY, key).apply()
        return secret
    }

    /** This phone's doorbell, created on first use. */
    @Synchronized
    fun ownCard(): ContactCard {
        val secret = ownSecret()
        val key = prefs.getString(KEY_OWN_KEY, null) ?: IdentityStore.randomHex(32).also {
            prefs.edit().putString(KEY_OWN_KEY, it).apply()
        }
        return ContactCard(inboxIdFor(secret), key)
    }

    /** A new inbox and key for this phone (with Reset key); remembered phones are kept. */
    @Synchronized
    fun renewOwnCard() {
        prefs.edit().remove(KEY_SECRET).remove(KEY_OWN_KEY).apply()
        ownCard()
    }

    /** Re-reads the remembered phones, for example after a chat remembered a new one. */
    fun refresh() {
        mutableContacts.value = load()
    }

    fun find(deviceId: String): Contact? = mutableContacts.value.firstOrNull { it.deviceId == deviceId }

    /** True if some remembered phone can be rung or can ring this phone. */
    fun anyDoorbell(): Boolean = mutableContacts.value.any { it.card != null }

    @Synchronized
    fun rename(deviceId: String, name: String) {
        update(deviceId) { it.copy(name = name.trim().take(MAX_NAME_CHARS)) }
    }

    @Synchronized
    fun saveCard(deviceId: String, card: ContactCard) {
        if (!card.isValid() || deviceId.isBlank()) return
        update(deviceId) { it.copy(card = card) }
    }

    /** Forgets one phone: its name, doorbell and remembered key. */
    @Synchronized
    fun forget(deviceId: String) {
        val kept = stored().filterKeys { it != deviceId }
        save(kept)
        pinsSource.forgetPhone(deviceId)
        refresh()
    }

    /** Forgets every phone (with Forget remembered phones). */
    @Synchronized
    fun forgetAll() {
        prefs.edit().remove(KEY_CONTACTS).apply()
        refresh()
    }

    /** A ring for [contact], or null if it has no doorbell yet. */
    fun sealRing(contact: Contact, room: String, token: String, nowMillis: Long): ByteArray? {
        val card = contact.card ?: return null
        return seal(ownCard().inboxKey, card, room, token, nowMillis)
    }

    /**
     * The invite in a ring that arrived at this phone's inbox, or null if no remembered phone sent
     * it, it is older or newer than five minutes, or it was seen before.
     */
    fun openRing(ring: ByteArray, nowMillis: Long): RingInvite? =
        open(ownCard(), mutableContacts.value, ring, nowMillis, seenNonces)

    private fun update(deviceId: String, change: (Contact) -> Contact) {
        val all = stored().toMutableMap()
        val current = all[deviceId] ?: Contact(deviceId, "", null)
        all[deviceId] = change(current)
        save(all)
        refresh()
    }

    /** Remembered phones (from the verified keys) merged with their names and doorbells. */
    private fun load(): List<Contact> {
        val saved = stored()
        val remembered = pinsSource.pins().lineSequence()
            .map { it.trim().substringBefore(' ') }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
        return remembered.map { deviceId -> saved[deviceId] ?: Contact(deviceId, "", null) }
    }

    private fun stored(): Map<String, Contact> {
        val text = prefs.getString(KEY_CONTACTS, null) ?: return emptyMap()
        return runCatching {
            val array = JSONArray(text)
            (0 until array.length()).associate { index ->
                val item = array.getJSONObject(index)
                val deviceId = item.getString("device")
                val card = if (item.has("inbox") && item.has("key")) {
                    ContactCard(item.getString("inbox"), item.getString("key")).takeIf { it.isValid() }
                } else {
                    null
                }
                deviceId to Contact(deviceId, item.optString("name", ""), card)
            }
        }.getOrDefault(emptyMap())
    }

    private fun save(all: Map<String, Contact>) {
        val array = JSONArray()
        all.values.forEach { contact ->
            val item = JSONObject().put("device", contact.deviceId).put("name", contact.name)
            contact.card?.let { item.put("inbox", it.inboxId).put("key", it.inboxKey) }
            array.put(item)
        }
        prefs.edit().putString(KEY_CONTACTS, array.toString()).apply()
    }

    companion object {
        private const val PREFS = "secretmessage_contacts"
        private const val KEY_SECRET = "own_inbox_secret"
        private const val KEY_OWN_KEY = "own_inbox_key"
        private const val KEY_CONTACTS = "contacts_v1"
        const val MAX_NAME_CHARS = 40
        private const val PAIR_ID_BYTES = 8
        private const val NONCE_BYTES = 12
        private const val PLAIN_BYTES = 40
        const val RING_BYTES = PAIR_ID_BYTES + NONCE_BYTES + PLAIN_BYTES + 16
        private const val MAX_CLOCK_SKEW_MILLIS = 5 * 60_000L
        private val seenNonces = SeenNonces()

        /** Seals a ring for [recipient] with this phone's inbox key ([ownKey]). */
        fun seal(
            ownKey: String,
            recipient: ContactCard,
            room: String,
            token: String,
            nowMillis: Long,
            nonce: ByteArray = ByteArray(NONCE_BYTES).also { SecureRandom().nextBytes(it) },
        ): ByteArray {
            require(nonce.size == NONCE_BYTES) { "nonce size" }
            val pairKey = pairKey(ownKey, recipient.inboxKey)
            val plain = ByteBuffer.allocate(PLAIN_BYTES)
                .put(hexBytes(room))
                .put(hexBytes(token))
                .putLong(nowMillis)
                .array()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(hmac(pairKey, "ring key"), "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(hexBytes(recipient.inboxId))
            return pairId(pairKey) + nonce + cipher.doFinal(plain)
        }

        /** Opens a ring that arrived at [own]'s inbox from one of [contacts]; null if it is not valid. */
        fun open(own: ContactCard, contacts: List<Contact>, ring: ByteArray, nowMillis: Long, seen: SeenNonces): RingInvite? {
            if (ring.size != RING_BYTES) return null
            val id = ring.copyOfRange(0, PAIR_ID_BYTES)
            for (contact in contacts) {
                val card = contact.card ?: continue
                val pairKey = pairKey(own.inboxKey, card.inboxKey)
                if (!MessageDigest.isEqual(pairId(pairKey), id)) continue
                val nonce = ring.copyOfRange(PAIR_ID_BYTES, PAIR_ID_BYTES + NONCE_BYTES)
                val plain = try {
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(hmac(pairKey, "ring key"), "AES"), GCMParameterSpec(128, nonce))
                    cipher.updateAAD(hexBytes(own.inboxId))
                    cipher.doFinal(ring, PAIR_ID_BYTES + NONCE_BYTES, ring.size - PAIR_ID_BYTES - NONCE_BYTES)
                } catch (forged: Exception) {
                    return null
                }
                val buffer = ByteBuffer.wrap(plain)
                val room = ByteArray(16).also { buffer.get(it) }
                val token = ByteArray(16).also { buffer.get(it) }
                val sentAt = buffer.getLong()
                if (kotlin.math.abs(nowMillis - sentAt) > MAX_CLOCK_SKEW_MILLIS) return null
                if (!seen.firstTime(hex(nonce), nowMillis)) return null
                return RingInvite(contact, hex(room), hex(token))
            }
            return null
        }

        /** The inbox ID the relay checks: SHA-256("secretmessage inbox id v1" ‖ secret), first 16 bytes. */
        fun inboxIdFor(secretHex: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update("secretmessage inbox id v1".toByteArray(StandardCharsets.UTF_8))
            digest.update(hexBytes(secretHex))
            return hex(digest.digest().copyOf(16))
        }

        fun pairKey(keyA: String, keyB: String): ByteArray {
            val (first, second) = if (keyA <= keyB) keyA to keyB else keyB to keyA
            return hmac(hexBytes(first) + hexBytes(second), "secretmessage pair v1")
        }

        private fun pairId(pairKey: ByteArray): ByteArray = hmac(pairKey, "kid").copyOf(PAIR_ID_BYTES)

        private fun hmac(key: ByteArray, label: String): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            return mac.doFinal(label.toByteArray(StandardCharsets.UTF_8))
        }

        fun hexBytes(hex: String): ByteArray {
            require(hex.length % 2 == 0 && hex.all { it in '0'..'9' || it in 'a'..'f' }) { "not lowercase hex" }
            return ByteArray(hex.length / 2) { index -> hex.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
        }

        fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

/** Ring nonces seen in the last ten minutes, so a copied ring cannot ring twice. */
internal class SeenNonces {
    private val seen = LinkedHashMap<String, Long>()

    @Synchronized
    fun firstTime(nonce: String, nowMillis: Long): Boolean {
        val iterator = seen.entries.iterator()
        while (iterator.hasNext()) {
            if (nowMillis - iterator.next().value > WINDOW_MILLIS) iterator.remove()
        }
        if (seen.containsKey(nonce)) return false
        if (seen.size >= MAX_ENTRIES) seen.remove(seen.keys.first())
        seen[nonce] = nowMillis
        return true
    }

    private companion object {
        const val WINDOW_MILLIS = 10 * 60_000L
        const val MAX_ENTRIES = 512
    }
}

/** The app's single contact book. */
internal object Contacts {
    @Volatile
    private var book: ContactBook? = null

    fun book(context: Context): ContactBook =
        book ?: synchronized(this) {
            book ?: ContactBook(context.applicationContext).also { book = it }
        }
}
