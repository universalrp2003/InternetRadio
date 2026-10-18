package com.oorarivippu.voiceannouncer

import android.speech.tts.Voice
import java.util.Locale

/** A voice advertised by the device's currently selected Android TTS engine. */
data class TtsVoiceOption(
    val name: String,
    val languageCode: String,
    val displayName: String,
    val requiresNetworkConnection: Boolean,
    /** Android's quality rating ([Voice.QUALITY_NORMAL] 300 … [Voice.QUALITY_VERY_HIGH] 500). */
    val quality: Int = Voice.QUALITY_NORMAL
)

internal object TtsVoicePolicy {
    private val supportedLanguages = setOf("ta", "en")

    fun fromAndroidVoices(voices: Iterable<Voice>, appLanguage: String): List<TtsVoiceOption> {
        val displayLocale = Locale.forLanguageTag(if (appLanguage == "ta") "ta" else "en")
        val ordered = voices
            .filter { it.locale.language in supportedLanguages }
            .distinctBy { it.name }
            .sortedWith(
                compareBy<Voice>(
                    { if (it.locale.language == "ta") 0 else 1 },
                    { if (it.locale.country.equals("IN", ignoreCase = true)) 0 else 1 },
                    // Best quality first (owner request for 1.1.0), then voices that work offline.
                    { -it.quality },
                    { it.isNetworkConnectionRequired },
                    { it.name.lowercase(Locale.ROOT) }
                )
            )
        val voiceNumbers = mutableMapOf<String, Int>()

        return ordered.map { voice ->
            val language = voice.locale.language
            val number = (voiceNumbers[language] ?: 0) + 1
            voiceNumbers[language] = number
            TtsVoiceOption(
                name = voice.name,
                languageCode = language,
                displayName = displayName(voice, number, displayLocale),
                requiresNetworkConnection = voice.isNetworkConnectionRequired,
                quality = voice.quality
            )
        }
    }

    /**
     * Every voice of the language that the phone offers, best quality first (owner request for
     * 1.1.0: more voices to choose from; 1.0.x showed at most four). The chosen voice stays listed.
     */
    fun choicesForLanguage(
        voices: List<TtsVoiceOption>,
        languageCode: String,
        selectedVoiceName: String
    ): List<TtsVoiceOption> {
        val matching = voices.filter { it.languageCode == languageCode }
        val selected = matching.firstOrNull { it.name == selectedVoiceName }
        val ordered = matching.sortedWith(
            compareBy<TtsVoiceOption>({ -it.quality }, { it.requiresNetworkConnection })
        )
        return (listOfNotNull(selected) + ordered).distinctBy { it.name }.take(MAX_VOICE_CHOICES)
    }

    /** Which of the three quality labels a voice gets. */
    fun qualityLevel(quality: Int): Int = when {
        quality >= Voice.QUALITY_VERY_HIGH -> 2
        quality >= Voice.QUALITY_HIGH -> 1
        else -> 0
    }

    private fun displayName(voice: Voice, number: Int, displayLocale: Locale): String {
        val variant = voice.name
            .substringAfter('#', "")
            .replace(Regex("(?i)-(?:local|network)$"), "")
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { token ->
                token.replaceFirstChar { character -> character.uppercase() }
            }
            .takeIf { it.isNotBlank() }
            ?: "Voice $number"
        return "${voice.locale.getDisplayName(displayLocale)} · $variant"
    }

    private const val MAX_VOICE_CHOICES = 40
}
