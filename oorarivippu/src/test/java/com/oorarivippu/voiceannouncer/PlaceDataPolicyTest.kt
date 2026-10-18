package com.oorarivippu.voiceannouncer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceDataPolicyTest {
    @Test
    fun ignoresPlotNumbersContainingSlashes() {
        assertEquals(
            "Main Street",
            PlaceDataPolicy.cleanSubArea(
                candidates = listOf("1/242", "Main Street"),
                cityOrVillage = "Alangudi"
            )
        )
    }

    @Test
    fun ignoresOtherNumericAddressComponentsAndCityDuplicates() {
        assertEquals(
            "Old Bus Stand",
            PlaceDataPolicy.cleanSubArea(
                candidates = listOf("12-4", "Alangudi", "Old Bus Stand"),
                cityOrVillage = "Alangudi"
            )
        )
    }

    @Test
    fun detectsHighwayAndTollLandmarkNamesButIgnoresOrdinaryStreetNames() {
        assertEquals("NH 44", PlaceDataPolicy.highwayLandmark(listOf("NH 44", "Main Street")))
        assertEquals("சுங்கச்சாவடி", PlaceDataPolicy.highwayLandmark(listOf("சுங்கச்சாவடி")))
        assertEquals(null, PlaceDataPolicy.highwayLandmark(listOf("Main Street", "Alangudi Road")))
    }

    @Test
    fun returnsEmptyWhenOnlyNumberOrCityIsResolved() {
        assertEquals(
            "",
            PlaceDataPolicy.cleanSubArea(
                candidates = listOf("1/242", "Pudukkottai"),
                cityOrVillage = "Pudukkottai"
            )
        )
    }

    @Test
    fun filtersPlusCodesFromCityAndSubAreaNames() {
        assertNull(PlaceDataPolicy.cleanPlaceName("9X5H+3HF"))
        assertNull(PlaceDataPolicy.cleanPlaceName("10.2°N, 78.9°E"))
        assertEquals(
            "Alangudi",
            PlaceDataPolicy.cleanCityOrVillage(listOf("9X5H+3HF, Alangudi"))
        )
        assertEquals(
            "",
            PlaceDataPolicy.cleanSubArea(
                candidates = listOf("9X5H+3HF", "9X6J+VCC"),
                cityOrVillage = "Alangudi"
            )
        )
    }

    @Test
    fun retainsNamedPlacesButIgnoresMultiStopRoadDescriptions() {
        assertEquals(
            "Alangudi Hospital",
            PlaceDataPolicy.cleanSubArea(listOf("Alangudi Hospital"), "Alangudi")
        )
        assertEquals(
            "Alangudi Bus Stand",
            PlaceDataPolicy.cleanSubArea(listOf("Alangudi Bus Stand"), "Alangudi")
        )
        assertEquals(
            "",
            PlaceDataPolicy.cleanSubArea(
                listOf("Pudukkottai - Alangudi - Avanam - Pattukkottai Road"),
                "Kallangudi"
            )
        )
    }

    @Test
    fun treatsPunctuationAndCaseVariantsAsTheSamePlace() {
        assertTrue(PlaceDataPolicy.samePlaceName("Alangudi", "ALANGUDI"))
        assertTrue(PlaceDataPolicy.samePlaceName("Alangudi (9X5H+3HF)", "Alangudi"))
        assertTrue(
            PlaceDataPolicy.samePlaceName(
                "Alangudi Hospital 9X5H+3HF",
                "Alangudi Hospital 9X6J+VCC"
            )
        )
        assertFalse(PlaceDataPolicy.samePlaceName("Alangudi Hospital", "Alangudi Bus Stand"))
    }

    @Test
    fun removesCodesBeforeIdentifyingHighwayLandmarks() {
        assertEquals("NH 44", PlaceDataPolicy.highwayLandmark(listOf("9X5H+3HF, NH 44")))
        assertEquals(null, PlaceDataPolicy.highwayLandmark(listOf("9X5H+3HF")))
    }

    @Test
    fun neverAnnouncesUnnamedRoadPlaceholders() {
        // Owner report (16 October 2026): "பெயரில்லாத சாலை" was announced as an area.
        listOf(
            "Unnamed Road", "Unnamed Rd", "unnamed road", "Unknown", "பெயரில்லாத சாலை",
            "பெயர் இல்லாத சாலை", "பெயரிடப்படாத சாலை", "Road", "சாலை", "Service Road", "சர்வீஸ் சாலை"
        ).forEach { placeholder ->
            assertNull(placeholder, PlaceDataPolicy.cleanPlaceName(placeholder))
        }
        assertEquals(
            "",
            PlaceDataPolicy.cleanSubArea(listOf("பெயரில்லாத சாலை", "Unnamed Road"), "புலியூர்நத்தம்")
        )
        assertEquals(
            "கம்பர் தெரு",
            PlaceDataPolicy.cleanSubArea(listOf("பெயரில்லாத சாலை", "கம்பர் தெரு"), "ஆலங்குடி")
        )
        // Real names that contain a road word stay.
        assertEquals("Main Street", PlaceDataPolicy.cleanPlaceName("Main Street"))
        assertEquals("Trichy Road", PlaceDataPolicy.cleanPlaceName("Trichy Road"))
    }

    @Test
    fun aTamilNameAndItsEnglishSpellingAreTheSamePlace() {
        // Owner report: "ஆலங்குடி", then "Alangudi" 1.5 minutes later, was announced twice.
        listOf(
            "ஆலங்குடி" to "Alangudi",
            "புலியூர்நத்தம்" to "Puliyur Natham",
            "பள்ளப்பட்டி" to "Pallapatti",
            "கரூர்" to "Karur",
            "திருச்சிராப்பள்ளி" to "Tiruchirappalli",
            "தஞ்சாவூர்" to "Thanjavur",
            "கோயம்புத்தூர்" to "Coimbatore",
            "புதுக்கோட்டை" to "Pudukkottai"
        ).forEach { (tamil, english) ->
            assertTrue("$tamil / $english", PlaceDataPolicy.samePlaceName(tamil, english))
            assertTrue("$english / $tamil", PlaceDataPolicy.samePlaceName(english, tamil))
        }
        assertFalse(PlaceDataPolicy.samePlaceName("ஆலங்குடி", "Aranthangi"))
        assertFalse(PlaceDataPolicy.samePlaceName("கரூர்", "Kulithalai"))
        // Within one script names must match exactly, so similar-sounding places stay separate.
        assertFalse(PlaceDataPolicy.samePlaceName("Karur", "Kurur"))
        assertFalse(PlaceDataPolicy.samePlaceName("ஆலங்குடி", "அலங்குடி"))
    }

    @Test
    fun stateAndCountryAreNeverTownsOrAreas() {
        assertEquals("", PlaceDataPolicy.cleanCityOrVillage(listOf(null, "Tamil Nadu")))
        assertEquals("", PlaceDataPolicy.cleanCityOrVillage(listOf("தமிழ்நாடு")))
        assertEquals("Karur", PlaceDataPolicy.cleanCityOrVillage(listOf("Tamil Nadu", "Karur")))
        assertEquals("", PlaceDataPolicy.cleanSubArea(listOf("India", "இந்தியா"), "Karur"))
    }

    @Test
    fun removesDoorNumbersAndPinCodesButKeepsNames() {
        assertEquals("Gandhi Nagar", PlaceDataPolicy.cleanPlaceName("12/3, Gandhi Nagar"))
        assertEquals("MG Road", PlaceDataPolicy.cleanPlaceName("No. 5, MG Road"))
        assertEquals("Karur", PlaceDataPolicy.cleanPlaceName("Karur 639001"))
        assertEquals("4 Road Junction", PlaceDataPolicy.cleanPlaceName("4 Road Junction"))
    }

    @Test
    fun districtWordIsRemovedOnlyOnce() {
        assertEquals("Karur", PlaceDataPolicy.withoutDistrictSuffix("Karur District"))
        assertEquals("Karur", PlaceDataPolicy.withoutDistrictSuffix("Karur dist."))
        assertEquals("கரூர்", PlaceDataPolicy.withoutDistrictSuffix("கரூர் மாவட்டம்"))
        assertEquals("Karur", PlaceDataPolicy.withoutDistrictSuffix("Karur"))
    }

    @Test
    fun aTownAnnouncedMinutesAgoIsNotRepeatedAtATownBorder() {
        val recent = RecentPlaces(windowMillis = 10 * 60_000L)
        recent.remember("Alangudi", nowMillis = 0)
        recent.remember("Vadakadu", nowMillis = 60_000)
        assertTrue(recent.wasAnnouncedRecently("Alangudi", nowMillis = 120_000))
        assertTrue(recent.wasAnnouncedRecently("ஆலங்குடி", nowMillis = 120_000))
        assertFalse(recent.wasAnnouncedRecently("Aranthangi", nowMillis = 120_000))
        // After the window the town is announced again.
        assertFalse(recent.wasAnnouncedRecently("Alangudi", nowMillis = 11 * 60_000L))
        recent.clear()
        assertFalse(recent.wasAnnouncedRecently("Vadakadu", nowMillis = 120_000))
    }
}
