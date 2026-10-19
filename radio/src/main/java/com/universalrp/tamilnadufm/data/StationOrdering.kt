package com.universalrp.tamilnadufm.data

/**
 * Keeps streams that passed the shipped/live health check ahead of unchecked entries,
 * then uses Radio Browser community votes to rank popularity. The all-stations view is
 * Tamil-first within each health group so this Tamil-first app surfaces its main audience
 * without allowing popularity to outrank stream health.
 */
object StationOrdering {
    private val tamilCategories = setOf(
        Category.TAMIL,
        Category.TAMIL_FM,
        Category.TAMIL_ILAYARAJA,
        Category.TAMIL_MSV,
        Category.TAMIL_AR_RAHMAN,
        Category.TAMIL_80S_90S,
        Category.TAMIL_DEVOTIONAL,
        Category.TAMIL_NEWS,
        Category.TOWNS,
    )

    private val popularComparator =
        compareByDescending<RadioStation> { it.verified }
            .thenByDescending { it.votes.coerceAtLeast(0) }
            .thenBy { it.name.trim().lowercase() }
            .thenBy { it.url.lowercase() }

    private val tamilFirstComparator =
        compareByDescending<RadioStation> { it.verified }
            .thenByDescending { isTamil(it) }
            .thenByDescending { it.votes.coerceAtLeast(0) }
            .thenBy { it.name.trim().lowercase() }
            .thenBy { it.url.lowercase() }

    /** Use for a category-filtered list, such as Tamil FM or Tamil devotional. */
    fun mostPopular(stations: List<RadioStation>): List<RadioStation> =
        stations.sortedWith(popularComparator)

    /** Keep Tamil entries first in mixed lists, but never ahead of a healthier stream. */
    fun tamilFirstPopular(stations: List<RadioStation>): List<RadioStation> =
        stations.sortedWith(tamilFirstComparator)

    private fun isTamil(station: RadioStation): Boolean =
        station.category in tamilCategories || station.language.contains("tamil", ignoreCase = true)
}
