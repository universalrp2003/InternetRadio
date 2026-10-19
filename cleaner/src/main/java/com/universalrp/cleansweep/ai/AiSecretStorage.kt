package com.universalrp.cleansweep.ai

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.content.Context
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts user-provided credentials before they are written to SharedPreferences.
 * The AES key is created in Android Keystore and is not stored alongside the ciphertext.
 * There is deliberately no plaintext persistence fallback if Keystore is unavailable.
 */
internal object AiSecretStorage {
    internal const val PREFIX = "keystore.aesgcm.v1:"
    private const val KEY_ALIAS = "com.universalrp.cleansweep.ai-secrets.v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12
    private val lock = Any()

    internal data class ReadResult(
        val value: String,
        /** False means legacy plaintext could not be migrated, or ciphertext could not be read. */
        val protectedAtRest: Boolean,
        val hadStoredValue: Boolean,
    ) {
        override fun toString(): String =
            "ReadResult(value=${if (value.isBlank()) "<empty>" else "<redacted>"}, " +
                "protectedAtRest=$protectedAtRest, hadStoredValue=$hadStoredValue)"
    }

    /** Reads an encrypted value and upgrades legacy plaintext atomically on first access. */
    @Suppress("UNUSED_PARAMETER")
    fun read(context: Context, prefs: SharedPreferences, preferenceKey: String): ReadResult =
        readWithKey(prefs, preferenceKey, ::androidKeystoreKey)

    /** Injectable key supplier keeps migration behavior testable without Android Keystore. */
    internal fun readWithKey(
        prefs: SharedPreferences,
        preferenceKey: String,
        keyProvider: () -> SecretKey,
    ): ReadResult = synchronized(lock) {
        val stored = prefs.getString(preferenceKey, null)
            ?: return@synchronized ReadResult("", protectedAtRest = true, hadStoredValue = false)
        if (stored.isBlank()) {
            return@synchronized ReadResult("", protectedAtRest = true, hadStoredValue = false)
        }

        if (stored.startsWith(PREFIX)) {
            val plaintext = runCatching {
                decryptPayload(stored, preferenceKey, keyProvider())
            }.getOrNull()
            return@synchronized if (plaintext == null) {
                // Keep the encrypted blob for diagnosis/recovery, but never treat it as a key.
                ReadResult("", protectedAtRest = false, hadStoredValue = true)
            } else {
                ReadResult(plaintext, protectedAtRest = true, hadStoredValue = true)
            }
        }

        // Legacy release builds stored API keys in clear text. Replace that value only after
        // encryption succeeds and SharedPreferences confirms the ciphertext write.
        val encrypted = runCatching {
            encryptPayload(stored, preferenceKey, keyProvider())
        }.getOrNull()
        if (encrypted != null && prefs.edit().putString(preferenceKey, encrypted).commit()) {
            ReadResult(stored, protectedAtRest = true, hadStoredValue = true)
        } else {
            // Do not intentionally leave a plaintext credential on disk when Keystore or
            // preferences are unavailable. The caller may use this in-memory value once and
            // the UI can report that secure persistence failed; retrying storage is explicit.
            runCatching { prefs.edit().remove(preferenceKey).commit() }
            ReadResult(stored, protectedAtRest = false, hadStoredValue = true)
        }
    }

    /** Stores only ciphertext; a blank value forgets the secret instead of saving an empty blob. */
    fun write(prefs: SharedPreferences, preferenceKey: String, value: String): Boolean = synchronized(lock) {
        if (value.isBlank()) return@synchronized prefs.edit().remove(preferenceKey).commit()
        val encrypted = runCatching {
            encryptPayload(value, preferenceKey, androidKeystoreKey())
        }.getOrNull() ?: return@synchronized false
        prefs.edit().putString(preferenceKey, encrypted).commit()
    }

    fun remove(prefs: SharedPreferences, preferenceKey: String): Boolean = synchronized(lock) {
        prefs.edit().remove(preferenceKey).commit()
    }

    private fun androidKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    /** Package-internal codec used by JVM tests with an ephemeral software key. */
    internal fun encryptPayload(plaintext: String, preferenceKey: String, key: SecretKey): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(preferenceKey.toByteArray(StandardCharsets.UTF_8))
        val encrypted = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        val iv = cipher.iv
        require(iv.size == IV_BYTES) { "Unexpected AES-GCM IV size" }
        val payload = ByteBuffer.allocate(iv.size + encrypted.size).put(iv).put(encrypted).array()
        return PREFIX + Base64.getEncoder().encodeToString(payload)
    }

    /** Returns null for the wrong preference key, damaged data, or a different Keystore key. */
    internal fun decryptPayload(encoded: String, preferenceKey: String, key: SecretKey): String? = runCatching {
        require(encoded.startsWith(PREFIX))
        val payload = Base64.getDecoder().decode(encoded.removePrefix(PREFIX))
        require(payload.size > IV_BYTES + GCM_TAG_BITS / 8)
        val buffer = ByteBuffer.wrap(payload)
        val iv = ByteArray(IV_BYTES).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(preferenceKey.toByteArray(StandardCharsets.UTF_8))
        String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }.getOrNull()
}
