package com.universalrp.cleansweep.ai

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import javax.crypto.spec.SecretKeySpec

class AiSecretStorageTest {
    private val testKey = SecretKeySpec(ByteArray(32) { (it * 7).toByte() }, "AES")

    @Test fun apiKeyIsCiphertextAndRoundTripsWithTheSamePreferenceBinding() {
        val secret = "sk-proj-this-is-only-test-data"
        val encrypted = AiSecretStorage.encryptPayload(secret, "saved_openai", testKey)

        assertTrue(encrypted.startsWith(AiSecretStorage.PREFIX))
        assertFalse(encrypted.contains(secret))
        assertEquals(secret, AiSecretStorage.decryptPayload(encrypted, "saved_openai", testKey))
    }

    @Test fun randomizedIvAndPreferenceKeyBindingPreventCiphertextReuse() {
        val first = AiSecretStorage.encryptPayload("test-secret", "saved_gemini", testKey)
        val second = AiSecretStorage.encryptPayload("test-secret", "saved_gemini", testKey)

        assertNotEquals(first, second)
        assertNull(AiSecretStorage.decryptPayload(first, "saved_groq", testKey))
    }

    @Test fun diagnosticStringsRedactApiKeysAndCustomUrlCredentials() {
        val secret = "sk-proj-only-test-secret"
        val endpoint = "https://free-user:free-password@free.example/v1?api_key=free-endpoint-token"
        val config = AiConfig(
            apiKey = secret,
            baseUrl = "https://user:password@example.invalid/v1",
            freeEndpointUrl = endpoint,
        )
        val storageResult = AiSecretStorage.ReadResult(secret, protectedAtRest = true, hadStoredValue = true)
        val diagnostic = config.toString()

        assertFalse(diagnostic.contains(secret))
        assertFalse(diagnostic.contains("password"))
        assertFalse(diagnostic.contains("free-endpoint-token"))
        assertFalse(diagnostic.contains(endpoint))
        assertTrue(diagnostic.contains("freeEndpointUrl=<redacted>"))
        assertFalse(storageResult.toString().contains(secret))
    }

    @Test fun damagedCiphertextIsRejected() {
        val encrypted = AiSecretStorage.encryptPayload("test-secret", "virustotal_key", testKey)
        val payload = encrypted.removePrefix(AiSecretStorage.PREFIX)
        val changed = payload.dropLast(1) + if (payload.last() == 'A') "B" else "A"

        assertNull(AiSecretStorage.decryptPayload(AiSecretStorage.PREFIX + changed, "virustotal_key", testKey))
    }

    @Test fun legacyPlaintextMigratesToCiphertextBeforeReturningTheInMemorySecret() {
        val secret = "legacy-openai-test-key"
        val prefs = memoryPreferences(mapOf("saved_openai" to secret))

        val result = AiSecretStorage.readWithKey(prefs, "saved_openai") { testKey }
        val persisted = prefs.getString("saved_openai", null)

        assertEquals(secret, result.value)
        assertTrue(result.protectedAtRest)
        assertTrue(result.hadStoredValue)
        assertTrue(persisted!!.startsWith(AiSecretStorage.PREFIX))
        assertFalse(persisted.contains(secret))
        assertEquals(secret, AiSecretStorage.decryptPayload(persisted, "saved_openai", testKey))
    }

    @Test fun encryptedPreferencesLoadWithoutRewritingTheCiphertext() {
        val secret = "saved-gemini-test-key"
        val encrypted = AiSecretStorage.encryptPayload(secret, "saved_gemini", testKey)
        val prefs = memoryPreferences(mapOf("saved_gemini" to encrypted))

        val result = AiSecretStorage.readWithKey(prefs, "saved_gemini") { testKey }

        assertEquals(secret, result.value)
        assertTrue(result.protectedAtRest)
        assertEquals(encrypted, prefs.getString("saved_gemini", null))
    }

    @Test fun unreadableEncryptedValueIsNotReturnedOrDiscarded() {
        val encrypted = AiSecretStorage.encryptPayload("cannot-read", "saved_openai", testKey)
        val prefs = memoryPreferences(mapOf("saved_openai" to encrypted))
        val wrongKey = SecretKeySpec(ByteArray(32) { (it * 3).toByte() }, "AES")

        val result = AiSecretStorage.readWithKey(prefs, "saved_openai") { wrongKey }

        assertEquals("", result.value)
        assertFalse(result.protectedAtRest)
        assertTrue(result.hadStoredValue)
        assertEquals(encrypted, prefs.getString("saved_openai", null))
    }

    @Test fun forgetRemovesOnlyTheSelectedProviderCredential() {
        val openAi = AiSecretStorage.encryptPayload("openai-secret", "saved_openai", testKey)
        val gemini = AiSecretStorage.encryptPayload("gemini-secret", "saved_gemini", testKey)
        val prefs = memoryPreferences(mapOf("saved_openai" to openAi, "saved_gemini" to gemini))

        assertTrue(AiSecretStorage.remove(prefs, "saved_openai"))

        assertNull(prefs.getString("saved_openai", null))
        assertEquals(gemini, prefs.getString("saved_gemini", null))
    }

    private fun memoryPreferences(initial: Map<String, String>): SharedPreferences {
        val values = initial.toMutableMap()
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java),
        ) { _, method, args ->
            val parameters = args ?: emptyArray()
            when (method.name) {
                "putString" -> {
                    val key = parameters[0] as String
                    val value = parameters[1] as String?
                    if (value == null) values.remove(key) else values[key] = value
                    editor
                }
                "remove" -> {
                    values.remove(parameters[0] as String)
                    editor
                }
                "clear" -> { values.clear(); editor }
                "commit" -> true
                "apply" -> Unit
                "toString" -> "InMemoryPreferenceEditor"
                else -> throw UnsupportedOperationException("Unused preference editor method: ${method.name}")
            }
        } as SharedPreferences.Editor

        return Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, args ->
            val parameters = args ?: emptyArray()
            when (method.name) {
                "getString" -> values[parameters[0] as String] ?: parameters[1] as String?
                "getAll" -> values.toMap()
                "contains" -> values.containsKey(parameters[0] as String)
                "edit" -> editor
                "toString" -> "InMemorySharedPreferences"
                else -> throw UnsupportedOperationException("Unused SharedPreferences method: ${method.name}")
            }
        } as SharedPreferences
    }
}
