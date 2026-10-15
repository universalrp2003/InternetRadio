package com.oorarivippu.voiceannouncer

import org.junit.Assert.assertEquals
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
    fun testVoiceUsesSamplePlaceWhenCurrentPlaceIsUnknown() {
        assertEquals(
            "Voice announcer is active. Current place: Alangudi.",
            BilingualStrings.buildTestSpeech("en", BilingualStrings.ENGLISH.unknownPlace)
        )
    }
}
