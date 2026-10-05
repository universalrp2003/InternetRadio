package com.universalrp.tamilnadufm.data

import org.json.JSONObject

/**
 * One playable station. [url] is the stream itself; everything else is for display
 * and filtering. Stations come from three places: the seed list shipped in the app,
 * the live open directory (search), or the user's own "add station" entry.
 */
data class RadioStation(
    val id: String,
    val name: String,
    val url: String,
    val favicon: String = "",
    val homepage: String = "",
    val tags: String = "",
    val country: String = "",
    val countryCode: String = "",
    val state: String = "",
    val language: String = "",
    val codec: String = "",
    val bitrate: Int = 0,
    val votes: Int = 0,
    val category: String = Category.TAMIL,
    val isCustom: Boolean = false,
    /** Directory entries carry a health check; curated ones are probed by CI. */
    val verified: Boolean = true,
) {
    /** "128 kbps MP3" or "MP3" or "" — shown under the station name. */
    val qualityLabel: String
        get() = buildList {
            if (bitrate > 0) add("$bitrate kbps")
            if (codec.isNotBlank()) add(codec.uppercase())
        }.joinToString(" ")

    val place: String
        get() = when {
            state.isNotBlank() && country.isNotBlank() -> "$state, $country"
            country.isNotBlank() -> country
            else -> ""
        }

    /** What the row shows under the name: quality + place, whichever exist. */
    val subtitle: String
        get() = listOf(qualityLabel, place).filter { it.isNotBlank() }.joinToString(" • ")

    val monogram: String
        get() = name.trim().split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifBlank { "FM" }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("url", url)
        put("favicon", favicon)
        put("homepage", homepage)
        put("tags", tags)
        put("country", country)
        put("countryCode", countryCode)
        put("state", state)
        put("language", language)
        put("codec", codec)
        put("bitrate", bitrate)
        put("votes", votes)
        put("category", category)
        put("isCustom", isCustom)
        put("verified", verified)
    }

    companion object {
        fun fromJson(json: JSONObject, custom: Boolean = false): RadioStation = RadioStation(
            id = json.optString("id").ifBlank { json.optString("url") },
            name = json.optString("name", "Unknown station"),
            url = json.optString("url_resolved").ifBlank { json.optString("url") },
            favicon = json.optString("favicon"),
            homepage = json.optString("homepage"),
            tags = json.optString("tags"),
            country = json.optString("country"),
            countryCode = json.optString("countryCode", json.optString("countrycode")),
            state = json.optString("state"),
            language = json.optString("language"),
            codec = json.optString("codec"),
            bitrate = json.optInt("bitrate", 0),
            votes = json.optInt("votes", 0),
            category = json.optString("category", Category.TAMIL),
            isCustom = custom || json.optBoolean("isCustom", false),
            verified = json.optBoolean("verified", true),
        )
    }
}

/** Station groups the app shows as chips. */
object Category {
    const val TAMIL = "tamil"
    const val TAMIL_NEWS = "tamil_news"
    const val TAMIL_FM = "tamil_fm"
    const val TAMIL_DEVOTIONAL = "tamil_devotional"
    const val WORLD_NEWS = "world_news"
    const val INDIA_NEWS = "india_news"
    const val ENGLISH = "english"
    const val CUSTOM = "custom"

    /** UI-only filter: every news bucket at once. */
    const val ALL_NEWS = "all_news"

    /** id used by the UI chips */
    const val ALL = "all"
    const val FAVOURITES = "favourites"
    const val RECENT = "recent"
    const val LOCAL = "local"

    /** UI-only filter: stations that carry a Tamil Nadu town or city in their name. */
    const val TOWNS = "towns"

    /** Town / city names used by the [TOWNS] filter and by search suggestions. */
    val TOWN_WORDS = listOf(
        "kovai", "coimbatore", "madurai", "trichy", "tiruchirappalli", "salem", "erode",
        "vellore", "dharmapuri", "puducherry", "pondicherry", "pondy", "ooty",
        "udhagamandalam", "kodaikanal", "nagercoil", "kanyakumari", "tirunelveli",
        "tuticorin", "thoothukudi", "thanjavur", "dindigul", "karur", "pollachi",
        "tirupur", "tiruppur", "hosur", "cuddalore", "namakkal", "villupuram", "chennai",
        "chengalpattu", "kanchipuram", "ranipet", "ramanathapuram", "theni", "tenkasi",
        "virudhunagar", "krishnagiri", "ariyalur", "perambalur", "nagapattinam",
        "mayiladuthurai", "tiruvannamalai", "tiruvarur", "kallakurichi", "pudukkottai",
        "pudukottai", "sivaganga", "nilgiris", "palani", "tiruvallur", "tambaram",
    )

    /**
     * True when a station is a Tamil station that names a Tamil Nadu town or city, so
     * the towns chip shows local FM (AIR Madurai, Puducherry, Kodaikanal, Pollachi …)
     * instead of foreign stations that happen to share a name.
     */
    fun isTamilTown(station: RadioStation): Boolean {
        val tamil = station.language.lowercase().contains("tamil") ||
            station.category == TAMIL || station.category == TAMIL_FM ||
            station.category == TAMIL_NEWS || station.category == TAMIL_DEVOTIONAL
        if (!tamil) return false
        val text = listOf(station.name, station.tags, station.state, station.homepage)
            .joinToString(" ").lowercase()
        return TOWN_WORDS.any { text.contains(it) }
    }

    fun isNews(id: String): Boolean =
        id == TAMIL_NEWS || id == WORLD_NEWS || id == INDIA_NEWS

    fun label(id: String): String = when (id) {
        ALL -> "All"
        ALL_NEWS -> "All news"
        FAVOURITES -> "Favourites"
        CUSTOM -> "My stations"
        RECENT -> "Recent"
        TOWNS -> "TN towns"
        TAMIL -> "Tamil FM"
        TAMIL_FM -> "Tamil worldwide"
        TAMIL_NEWS -> "Tamil news"
        TAMIL_DEVOTIONAL -> "Bakthi"
        WORLD_NEWS -> "World news"
        INDIA_NEWS -> "India news"
        ENGLISH -> "English"
        else -> id.replaceFirstChar { it.uppercase() }
    }
}
