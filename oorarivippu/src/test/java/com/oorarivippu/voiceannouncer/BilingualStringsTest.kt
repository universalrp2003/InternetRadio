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
            assertTrue(strings.statusInternetUnavailable.isNotBlank())
            assertTrue(strings.internetUnavailableDetails.isNotBlank())
            assertTrue(strings.offlineReminderTitle.isNotBlank())
            assertTrue(strings.offlineReminderSubtitle.isNotBlank())
            assertTrue(strings.statusCallPaused.isNotBlank())
            assertTrue(strings.destinationAlertExplanation.isNotBlank())
            assertTrue(strings.headsetWhereAmISubtitle.isNotBlank())
        }
    }

    @Test
    fun destinationAndDistrictAnnouncementsHaveLocalizedText() {
        assertEquals(
            "Pudukkottai is 5 kilometres away.",
            BilingualStrings.buildDestinationAnnouncement("en", "Pudukkottai", 5_000, arrived = false)
        )
        assertEquals(
            "Entering Pudukkottai district.",
            BilingualStrings.buildDistrictAnnouncement("en", "Pudukkottai")
        )
        assertEquals(
            "Approaching NH 44.",
            BilingualStrings.buildHighwayLandmarkAnnouncement("en", "NH 44")
        )
        assertTrue(
            BilingualStrings.buildDestinationAnnouncement("ta", "புதுக்கோட்டை", 500, arrived = true)
                .contains("வந்தடைந்துவிட்டீர்கள்")
        )
        assertEquals(
            "It is 5:20 PM. Your current location is Town Centre, Alangudi.",
            BilingualStrings.buildWhereAmIAnnouncement("en", "Alangudi", "Town Centre", "5:20 PM")
        )
    }

    @Test
    fun offlineReminderClearlySaysInternetIsUnavailableInBothLanguages() {
        assertTrue(BilingualStrings.buildOfflineReminderAnnouncement("en").contains("Internet is unavailable"))
        assertTrue(BilingualStrings.buildOfflineReminderAnnouncement("ta").contains("இணைய இணைப்பு இல்லை"))
    }

    @Test
    fun testVoiceUsesSamplePlaceWhenCurrentPlaceIsUnknown() {
        assertEquals(
            "Voice announcer is active. Current place: Alangudi.",
            BilingualStrings.buildTestSpeech("en", BilingualStrings.ENGLISH.unknownPlace)
        )
    }
}
