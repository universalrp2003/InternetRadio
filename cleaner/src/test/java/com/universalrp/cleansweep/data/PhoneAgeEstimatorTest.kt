package com.universalrp.cleansweep.data

import org.junit.Assert.*
import org.junit.Test

class PhoneAgeEstimatorTest {
    @Test fun factoryGenerationPreservesTheFormerExampleWithoutHardCodingIt() {
        val age = PhoneAgeEstimator.estimateFromEvidence("unlisted model", 34, 2026, 10)
        assertTrue(age.available)
        assertTrue(age.isEstimated)
        assertEquals(PhoneAgeEstimator.Basis.FACTORY_ANDROID_GENERATION, age.basis)
        assertEquals(3, age.years)
        assertEquals(4, age.months)
        assertEquals(2023, age.releaseYear)
        assertEquals("About 3 yr 4 mo (2023)", age.displayString(false))
    }

    @Test fun knownFamilyUsesItsApproximateReferenceRatherThanAnOsGeneration() {
        val age = PhoneAgeEstimator.estimateFromEvidence("Samsung Galaxy S23 Ultra", 33, 2026, 10)
        assertEquals(PhoneAgeEstimator.Basis.MODEL_FAMILY, age.basis)
        assertEquals(2023, age.releaseYear)
        assertEquals(3, age.years)
        assertEquals(7, age.months)
    }

    @Test fun redmi13FamilyRemainsSupportedWithNormalizedCaseAndSeparators() {
        val age = PhoneAgeEstimator.estimateFromEvidence("REDMI_13 5G", 34, 2026, 10)
        assertTrue(age.available)
        assertEquals(PhoneAgeEstimator.Basis.MODEL_FAMILY, age.basis)
        assertEquals(2024, age.releaseYear)
        assertEquals(2, age.years)
        assertEquals(7, age.months)
    }

    @Test fun hardwareModelCodeIsNotMistakenForTheS9Family() {
        val age = PhoneAgeEstimator.estimateFromEvidence("SM-S918B", 33, 2026, 10)
        assertEquals(PhoneAgeEstimator.Basis.FACTORY_ANDROID_GENERATION, age.basis)
        assertEquals(2022, age.releaseYear)
    }

    @Test fun unsupportedEvidenceStaysUnavailableInsteadOfInventing2022() {
        val age = PhoneAgeEstimator.estimateFromEvidence("unlisted handset", null, 2026, 10)
        assertFalse(age.available)
        assertEquals(PhoneAgeEstimator.Basis.UNAVAILABLE, age.basis)
        assertEquals(0, age.releaseYear)
        assertEquals("Not enough information", age.displayString(false))
    }

    @Test fun unknownAndInvalidFactoryLevelsDoNotUseTheLatestKnownYear() {
        for (api in listOf(-1, 0, 22, 37, 99)) {
            assertFalse("Unsupported first API $api", PhoneAgeEstimator.estimateFromEvidence("unknown", api, 2026, 10).available)
        }
    }

    @Test fun updateLikeTokensCannotSupplyMissingFactoryOrModelEvidence() {
        val age = PhoneAgeEstimator.estimateFromEvidence("Android 16 API 36 security patch 2026-10-01 uptime 400", null, 2026, 10)
        assertFalse(age.available)
    }

    @Test fun monthBorrowProducesNonNegativeYearsAndZeroToElevenMonths() {
        val age = PhoneAgeEstimator.estimateFromEvidence("Galaxy S23", null, 2026, 2)
        assertEquals(2, age.years)
        assertEquals(11, age.months)
        assertEquals("About 2 yr 11 mo (2023)", age.displayString(false))
    }

    @Test fun exactYearAndMonthOnlyFormattingRemainEstimated() {
        val year = PhoneAgeEstimator.estimateFromEvidence("unknown", 34, 2026, 6)
        assertEquals("About 3 yr (2023)", year.displayString(false))
        val months = PhoneAgeEstimator.estimateFromEvidence("unknown", 36, 2025, 10)
        assertEquals("About 4 mo (2025)", months.displayString(false))
        val zero = PhoneAgeEstimator.estimateFromEvidence("unknown", 36, 2025, 6)
        assertEquals("About 0 mo (2025)", zero.displayString(false))
    }

    @Test fun futureReferenceDoesNotClaimANewDeviceOrNegativeAge() {
        val age = PhoneAgeEstimator.estimateFromEvidence("Galaxy S26", null, 2025, 12)
        assertFalse(age.available)
        assertEquals("Not enough information", age.displayString(false))
    }

    @Test fun tamilKeepsApproximationAndUnavailableDisclosures() {
        val age = PhoneAgeEstimator.estimateFromEvidence("unknown", 34, 2026, 10)
        assertEquals("சுமார் 3 வருடம் 4 மாதம் (2023)", age.displayString(true))
        assertEquals("மதிப்பிடத் தகவல் இல்லை", PhoneAgeEstimator.unavailable().displayString(true))
    }

    @Test fun supportedAndroidGenerationsAndInvalidCalendarInputsAreBounded() {
        val references = mapOf(23 to 2015, 24 to 2016, 25 to 2016, 26 to 2017, 27 to 2017,
            28 to 2018, 29 to 2019, 30 to 2020, 31 to 2021, 32 to 2021, 33 to 2022,
            34 to 2023, 35 to 2024, 36 to 2025)
        for ((api, year) in references) {
            val age = PhoneAgeEstimator.estimateFromEvidence("unknown", api, 2026, 10)
            assertTrue(age.available)
            assertEquals(year, age.releaseYear)
            assertEquals(4, age.months)
        }
        for (month in listOf(-1, 0, 13)) {
            assertFalse(PhoneAgeEstimator.estimateFromEvidence("Galaxy S23", 33, 2026, month).available)
        }
        assertFalse(PhoneAgeEstimator.estimateFromEvidence("unknown", 34, 0, 10).available)
    }
}
