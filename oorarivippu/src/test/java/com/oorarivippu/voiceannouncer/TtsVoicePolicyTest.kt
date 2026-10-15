package com.oorarivippu.voiceannouncer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsVoicePolicyTest {
    @Test
    fun offersUpToFourChoicesIncludingLocalAndNetworkVoices() {
        val voices = listOf(
            option("ta-local-1", "ta", network = false),
            option("ta-local-2", "ta", network = false),
            option("ta-local-3", "ta", network = false),
            option("ta-online-1", "ta", network = true),
            option("ta-online-2", "ta", network = true)
        )

        val choices = TtsVoicePolicy.choicesForLanguage(voices, "ta", selectedVoiceName = "")

        assertEquals(4, choices.size)
        assertTrue(choices.any { !it.requiresNetworkConnection })
        assertTrue(choices.any { it.requiresNetworkConnection })
    }

    @Test
    fun keepsTheSelectedVoiceVisibleAndDoesNotMixLanguages() {
        val voices = listOf(
            option("ta-local", "ta", network = false),
            option("ta-online-1", "ta", network = true),
            option("ta-online-2", "ta", network = true),
            option("ta-online-3", "ta", network = true),
            option("en-local", "en", network = false)
        )

        val choices = TtsVoicePolicy.choicesForLanguage(
            voices = voices,
            languageCode = "ta",
            selectedVoiceName = "ta-online-3"
        )

        assertTrue(choices.any { it.name == "ta-online-3" })
        assertTrue(choices.all { it.languageCode == "ta" })
        assertTrue(choices.size <= 4)
    }

    private fun option(name: String, language: String, network: Boolean) = TtsVoiceOption(
        name = name,
        languageCode = language,
        displayName = name,
        requiresNetworkConnection = network
    )
}
