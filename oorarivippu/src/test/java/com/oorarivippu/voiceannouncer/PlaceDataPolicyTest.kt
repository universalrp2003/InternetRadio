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
}
