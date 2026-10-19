package com.oorarivippu.voiceannouncer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsVoicePolicyTest {
    @Test
    fun offersEveryVoiceOfTheLanguageBestQualityFirst() {
        // Owner request for 1.1.0: more voices to choose from (1.0.x showed at most four).
        val voices = listOf(
            option("ta-local-1", "ta", network = false, quality = 300),
            option("ta-local-2", "ta", network = false, quality = 400),
            option("ta-local-3", "ta", network = false, quality = 300),
            option("ta-online-1", "ta", network = true, quality = 500),
            option("ta-online-2", "ta", network = true, quality = 400)
        )

        val choices = TtsVoicePolicy.choicesForLanguage(voices, "ta", selectedVoiceName = "")

        assertEquals(5, choices.size)
        assertEquals("ta-online-1", choices.first().name)
        // Among equal quality, the voice that works without internet comes first.
        assertEquals(listOf("ta-online-1", "ta-local-2", "ta-online-2", "ta-local-1", "ta-local-3"), choices.map { it.name })
        assertTrue(choices.any { !it.requiresNetworkConnection })
        assertTrue(choices.any { it.requiresNetworkConnection })
    }

    @Test
    fun keepsTheSelectedVoiceFirstAndDoesNotMixLanguages() {
        val voices = listOf(
            option("ta-local", "ta", network = false),
            option("ta-online-1", "ta", network = true, quality = 500),
            option("ta-online-2", "ta", network = true),
            option("ta-online-3", "ta", network = true),
            option("en-local", "en", network = false, quality = 500)
        )

        val choices = TtsVoicePolicy.choicesForLanguage(voices, "ta", selectedVoiceName = "ta-online-3")

        assertEquals("ta-online-3", choices.first().name)
        assertEquals(4, choices.size)
        assertTrue(choices.all { it.languageCode == "ta" })
    }

    @Test
    fun qualityLabelsFollowAndroidRatings() {
        assertEquals(2, TtsVoicePolicy.qualityLevel(500))
        assertEquals(1, TtsVoicePolicy.qualityLevel(400))
        assertEquals(0, TtsVoicePolicy.qualityLevel(300))
        assertEquals(0, TtsVoicePolicy.qualityLevel(100))
    }

    private fun option(name: String, language: String, network: Boolean, quality: Int = 300) = TtsVoiceOption(
        name = name,
        languageCode = language,
        displayName = name,
        requiresNetworkConnection = network,
        quality = quality
    )
}
