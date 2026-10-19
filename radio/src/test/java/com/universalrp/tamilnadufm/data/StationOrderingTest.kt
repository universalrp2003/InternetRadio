package com.universalrp.tamilnadufm.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StationOrderingTest {
    @Test fun verifiedStationsStayAheadAndVotesOrderEachHealthGroup() {
        val stations = listOf(
            station("unchecked-popular", votes = 9_000, verified = false),
            station("verified-less-popular", votes = 100, verified = true),
            station("verified-popular", votes = 8_000, verified = true),
            station("unchecked-zero", votes = 0, verified = false),
        )

        assertEquals(
            listOf("verified-popular", "verified-less-popular", "unchecked-popular", "unchecked-zero"),
            StationOrdering.mostPopular(stations).map { it.name },
        )
    }

    @Test fun mixedAllStationsAreTamilFirstWithinEachHealthGroup() {
        val stations = listOf(
            station("popular-english", votes = 9_000, verified = true, category = Category.ENGLISH),
            station("unchecked-tamil", votes = 20_000, verified = false, category = Category.TAMIL),
            station("popular-tamil", votes = 800, verified = true, category = Category.TAMIL_FM),
            station("unchecked-english", votes = 30_000, verified = false, category = Category.ENGLISH),
            station("less-popular-tamil", votes = 100, verified = true, category = Category.TAMIL),
        )

        assertEquals(
            listOf("popular-tamil", "less-popular-tamil", "popular-english", "unchecked-tamil", "unchecked-english"),
            StationOrdering.tamilFirstPopular(stations).map { it.name },
        )
    }

    @Test fun negativeVoteCountsCannotOutrankPositiveVotes() {
        val stations = listOf(
            station("negative", votes = -2, verified = true),
            station("zero", votes = 0, verified = true),
            station("positive", votes = 1, verified = true),
        )

        assertEquals(listOf("positive", "negative", "zero"), StationOrdering.mostPopular(stations).map { it.name })
    }

    private fun station(
        name: String,
        votes: Int,
        verified: Boolean,
        category: String = Category.TAMIL,
    ) = RadioStation(
        id = name,
        name = name,
        url = "https://example.com/$name",
        votes = votes,
        category = category,
        verified = verified,
    )
}
