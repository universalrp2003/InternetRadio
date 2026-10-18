package com.rpideas.secretmessage.wifitest

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * This install's test identity and the phones it has verified, as in the LAN smoke test APK.
 *
 * The identity text (`v1:<device ID>:<secret key hex>:<public key hex>`) is created by the native
 * library and stored encrypted with AES-256-GCM under an Android Keystore key that never leaves
 * Keystore. The MLS signing key itself is a software key that is decrypted into app memory for each
 * session. Remembered phones are stored unencrypted. Uninstalling the app deletes both.
 */
internal class IdentityStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** This install's identity text, created or given a new key when needed. Worker threads only. */
    @Synchronized
    fun loadOrCreate(newKey: Boolean = false): String {
        val stored = prefs.getString(PREF_IDENTITY, null)
        if (stored != null && !newKey) return unwrap(stored)
        val deviceId = prefs.getString(PREF_DEVICE_ID, null) ?: randomHex(8)
        val identity = NativeBridge.nativeGenerateIdentity(deviceId)
        check(identity.startsWith("v1:")) { identity }
        val saved = prefs.edit()
            .putString(PREF_DEVICE_ID, deviceId)
            .putString(PREF_IDENTITY, wrap(identity))
            .commit()
        check(saved) { "the test identity could not be saved" }
        return identity
    }

    fun pins(): String = prefs.getString(PREF_PEER_PINS, "") ?: ""

    /** This install's device ID, or null before the first chat created it. */
    fun deviceId(): String? = prefs.getString(PREF_DEVICE_ID, null)

    fun remember(pin: String) {
        prefs.edit().putString(PREF_PEER_PINS, PeerPins.remember(pins(), pin)).apply()
    }

    fun forgetPhones() {
        prefs.edit().remove(PREF_PEER_PINS).apply()
    }

    /** Forgets one remembered phone's key (from the contact list). */
    fun forgetPhone(deviceId: String) {
        prefs.edit().putString(PREF_PEER_PINS, PeerPins.forget(pins(), deviceId)).apply()
    }

    fun summary(): String {
        val deviceId = prefs.getString(PREF_DEVICE_ID, null)
        val remembered = PeerPins.count(pins())
        return if (deviceId == null || !prefs.contains(PREF_IDENTITY)) {
            "no identity yet (it is created with the first chat); remembered phones: $remembered"
        } else {
            "device ${PeerPins.formatDeviceId(deviceId)}, key stored encrypted with an Android Keystore key; " +
                "remembered phones: $remembered"
        }
    }

    private fun wrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)
        (keyStore.getKey(WRAP_KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                WRAP_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun wrap(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val sealed = cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
        val encoder = Base64.getEncoder()
        return encoder.encodeToString(cipher.iv) + ":" + encoder.encodeToString(sealed)
    }

    private fun unwrap(stored: String): String {
        val parts = stored.split(':', limit = 2)
        check(parts.size == 2) { "the stored test identity is malformed; reset this phone's key" }
        val decoder = Base64.getDecoder()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(GCM_TAG_BITS, decoder.decode(parts[0])))
        return String(cipher.doFinal(decoder.decode(parts[1])), StandardCharsets.UTF_8)
    }

    companion object {
        private const val PREFS = "secretmessage_wifi_test"
        private const val PREF_DEVICE_ID = "device_id"
        private const val PREF_IDENTITY = "identity_v1"
        private const val PREF_PEER_PINS = "peer_pins_v1"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val WRAP_KEY_ALIAS = "secretmessage_wifi_test_identity_wrap"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128

        fun randomHex(byteCount: Int): String {
            val bytes = ByteArray(byteCount)
            SecureRandom().nextBytes(bytes)
            val digits = "0123456789abcdef"
            return bytes.joinToString("") { byte ->
                val value = byte.toInt() and 0xff
                "${digits[value shr 4]}${digits[value and 0x0f]}"
            }
        }
    }
}
