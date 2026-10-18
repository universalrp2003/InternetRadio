package com.oorarivippu.voiceannouncer

import org.junit.Assert.assertEquals
import org.junit.Test

/** Owner report: பள்ளப்பட்டி exists in several places, so the person must see which one is chosen. */
class DestinationSearchPolicyTest {
    @Test
    fun slashesBecomeCommasSoTheGeocoderReadsTheDistrict() {
        assertEquals("pallapatti, karur", DestinationSearchPolicy.normalizeQuery(" pallapatti/karur "))
        assertEquals("Pallapatti, Karur", DestinationSearchPolicy.normalizeQuery("Pallapatti \\ Karur"))
        assertEquals("Pallapatti", DestinationSearchPolicy.normalizeQuery("Pallapatti/"))
    }

    @Test
    fun detailsShowTheDistrictStateAndPinCode() {
        assertEquals(
            "கரூர் மாவட்டம், தமிழ்நாடு 639205",
            DestinationSearchPolicy.details(
                name = "பள்ளப்பட்டி",
                locality = "பள்ளப்பட்டி",
                district = "கரூர்",
                state = "தமிழ்நாடு",
                postalCode = "639205",
                districtWord = "மாவட்டம்"
            )
        )
        assertEquals(
            "Aravakurichi, Karur district, Tamil Nadu",
            DestinationSearchPolicy.details(
                name = "Pallapatti",
                locality = "Aravakurichi",
                district = "Karur District",
                state = "Tamil Nadu",
                postalCode = null,
                districtWord = "district"
            )
        )
    }

    @Test
    fun repeatsAreDroppedButSameNamedPlacesInOtherDistrictsStay() {
        val karur = DestinationCandidate("Pallapatti", "Karur district, Tamil Nadu", 10.72, 77.87, 40)
        val karurAgain = DestinationCandidate("Pallapatti", "Karur district, Tamil Nadu 639205", 10.721, 77.871, 40)
        val dindigul = DestinationCandidate("Pallapatti", "Dindigul district, Tamil Nadu", 10.36, 77.97, 80)
        val result = DestinationSearchPolicy.distinct(listOf(karur, karurAgain, dindigul))
        assertEquals(listOf(karur, dindigul), result)
    }
}
