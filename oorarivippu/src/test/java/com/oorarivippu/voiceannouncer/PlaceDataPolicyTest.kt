package com.oorarivippu.voiceannouncer

import org.junit.Assert.assertEquals
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
}
