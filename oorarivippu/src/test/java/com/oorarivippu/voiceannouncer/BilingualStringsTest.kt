package com.oorarivippu.voiceannouncer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            "Pudukkottai, Town Centre.",
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
            assertTrue(strings.voiceStatusSelectedUnavailable.isNotBlank())
            assertTrue(strings.voiceStatusNetworkRequired.isNotBlank())
            assertTrue(strings.voiceStatusUnavailable.isNotBlank())
            assertTrue(strings.voiceChoiceSubtitle.isNotBlank())
            assertTrue(strings.voiceChoiceSystemDefault.isNotBlank())
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
            "Pudukkottai district.",
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
    fun placeAnnouncementsSpeakOnlyThePlaceNames() {
        assertEquals("ஆலங்குடி.", place("ta", newTown = true, town = "ஆலங்குடி", area = null))
        assertEquals(
            "ஆலங்குடி, சந்தைப்பேட்டை.",
            place("ta", newTown = true, town = "ஆலங்குடி", area = "சந்தைப்பேட்டை")
        )
        assertEquals(
            "ஆலங்குடி, அடுத்த பகுதி கம்பர் தெரு.",
            place("ta", newTown = false, town = "ஆலங்குடி", area = "கம்பர் தெரு")
        )
        assertEquals("Alangudi.", place("en", newTown = true, town = "Alangudi", area = null))
        assertEquals(
            "Kambar Street, Alangudi.",
            place("en", newTown = false, town = "Alangudi", area = "Kambar Street")
        )
        // The sub-area is left out when the option is off or it only repeats the town name.
        assertEquals(
            "Alangudi.",
            place("en", newTown = true, town = "Alangudi", area = "Santhappettai", includeArea = false)
        )
        assertEquals("ஆலங்குடி.", place("ta", newTown = true, town = "ஆலங்குடி", area = "ஆலங்குடி"))
        assertEquals("Alangudi.", place("en", newTown = true, town = "Alangudi", area = "ALANGUDI"))
        assertEquals("புதுக்கோட்டை மாவட்டம்.", BilingualStrings.buildDistrictAnnouncement("ta", "புதுக்கோட்டை"))
    }

    @Test
    fun districtWordIsSpokenOnceAndOneTownIsNotSpokenInTwoScripts() {
        assertEquals("Karur district.", BilingualStrings.buildDistrictAnnouncement("en", "Karur District"))
        assertEquals("கரூர் மாவட்டம்.", BilingualStrings.buildDistrictAnnouncement("ta", "கரூர் மாவட்டம்"))
        assertEquals("Alangudi.", place("en", newTown = true, town = "Alangudi", area = "ஆலங்குடி"))
        assertEquals("ஆலங்குடி.", place("ta", newTown = true, town = "ஆலங்குடி", area = "Alangudi"))
        assertEquals(
            "It is 5:20 PM. Your current location is Alangudi.",
            BilingualStrings.buildWhereAmIAnnouncement("en", "Alangudi", "ஆலங்குடி", "5:20 PM")
        )
    }

    @Test
    fun repeatedPlaceAnnouncementsHaveNoFillerWords() {
        val fillerWords = listOf(
            "Entering", "தற்போது", "வந்துள்ளீர்கள்", "ஊருக்குள்", "பகுதிக்கு",
            "மாவட்டத்திற்குள்", "வந்தடைந்தீர்கள்"
        )
        // "unknown" exercises the Tamil default used for any non-English voice language.
        val announcements = listOf("ta", "en", "unknown").flatMap { language ->
            listOf(
                place(language, newTown = true, town = "Alangudi", area = null),
                place(language, newTown = true, town = "Alangudi", area = "Santhappettai"),
                place(language, newTown = false, town = "Alangudi", area = "Kambar Street"),
                BilingualStrings.buildDistrictAnnouncement(language, "Pudukkottai")
            )
        }
        announcements.forEach { text ->
            fillerWords.forEach { word ->
                assertFalse("\"$text\" still contains \"$word\"", text.contains(word))
            }
            // A district or landmark sentence may be appended, so each part ends a sentence.
            assertTrue("\"$text\" must end with a full stop", text.endsWith("."))
        }
    }

    private fun place(
        language: String,
        newTown: Boolean,
        town: String,
        area: String?,
        includeArea: Boolean = true
    ): String = BilingualStrings.buildSpokenAnnouncement(
        voiceLang = language,
        isNewCityOrVillage = newTown,
        cityOrVillage = town,
        subArea = area,
        includeSubArea = includeArea
    )

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
