package com.oorarivippu.voiceannouncer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BilingualStringsTest {

    @Test
    fun unknownAppLanguageUsesTamil() {
        assertEquals(BilingualStrings.TAMIL.appTitle, BilingualStrings.forLang("ta").appTitle)
        assertEquals(BilingualStrings.TAMIL.appTitle, BilingualStrings.forLang("unknown").appTitle)
    }

    @Test
    fun englishCityAnnouncementCanIncludeSubArea() {
        assertEquals(
            "Entering Pudukkottai, Town Centre.",
            BilingualStrings.buildSpokenAnnouncement(
                voiceLang = "en",
                isNewCityOrVillage = true,
                cityOrVillage = "Pudukkottai",
                subArea = "Town Centre",
                includeSubArea = true
            )
        )
    }

    @Test
    fun bothAppLanguagesExplainTtsAvailabilityAndFallbacks() {
        listOf(BilingualStrings.TAMIL, BilingualStrings.ENGLISH).forEach { strings ->
            assertTrue(strings.voiceStatusNotChecked.isNotBlank())
            assertTrue(strings.voiceStatusAvailable.isNotBlank())
            assertTrue(strings.voiceStatusFallback.isNotBlank())
            assertTrue(strings.voiceStatusUnavailable.isNotBlank())
        }
    }

    @Test
    fun testVoiceUsesSamplePlaceWhenCurrentPlaceIsUnknown() {
        assertEquals(
            "Voice announcer is active. Current place: Alangudi.",
            BilingualStrings.buildTestSpeech("en", BilingualStrings.ENGLISH.unknownPlace)
        )
    }
}
