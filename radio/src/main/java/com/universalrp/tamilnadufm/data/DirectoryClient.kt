package com.universalrp.tamilnadufm.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Searches the live open radio directory (radio-browser.info).
 *
 * The app ships a seed list so it works immediately, but a directory is the only
 * honest way to keep hundreds of Tamil/world stations current — stream URLs come
 * and go. The directory is free, needs no key and no account, and the request is
 * made straight from the phone: no server in the middle.
 */
object DirectoryClient {

    private val mirrors = listOf(
        "https://de1.api.radio-browser.info",
        "https://de2.api.radio-browser.info",
        "https://nl1.api.radio-browser.info",
        "https://at1.api.radio-browser.info",
    )

    /** Free-text search across the whole directory. */
    suspend fun search(text: String, limit: Int = 60): List<RadioStation> =
        query(mapOf("name" to text), limit)

    /** Popular Tamil stations (used by "Discover Tamil stations"). */
    suspend fun popularTamil(limit: Int = 80): List<RadioStation> =
        query(
            mapOf("language" to "tamil", "order" to "votes", "reverse" to "true"),
            limit,
        )

    /** News stations in a given language, e.g. English or Tamil. */
    suspend fun news(language: String, limit: Int = 60): List<RadioStation> =
        query(
            mapOf(
                "language" to language,
                "tag" to "news",
                "order" to "votes",
                "reverse" to "true",
            ),
            limit,
        )

    private suspend fun query(params: Map<String, String>, limit: Int): List<RadioStation> =
        withContext(Dispatchers.IO) {
            val full = params + mapOf(
                "limit" to limit.toString(),
                "hidebroken" to "true",
                "order" to (params["order"] ?: "votes"),
                "reverse" to (params["reverse"] ?: "true"),
            )
            val queryString = full.entries.joinToString("&") { (key, value) ->
                "$key=${URLEncoder.encode(value, "UTF-8")}"
            }

            for (mirror in mirrors) {
                val result = try {
                    fetch("$mirror/json/stations/search?$queryString")
                } catch (t: Throwable) {
                    null
                }
                if (!result.isNullOrEmpty()) return@withContext result
            }
            emptyList()
        }

    private fun fetch(url: String): List<RadioStation> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "TamilnaduFMRadio/1.0 (Android)")
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) return emptyList()
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val array = JSONArray(body)
            val stations = ArrayList<RadioStation>(array.length())
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val station = RadioStation.fromJson(item)
                if (station.url.isBlank()) continue
                if (station.url.startsWith("http://") || station.url.startsWith("https://")) {
                    stations.add(
                        station.copy(
                            category = categoryFor(station, Category.TAMIL),
                            verified = item.optInt("lastcheckok", 1) == 1,
                        )
                    )
                }
            }
            return stations
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    /** Maps a directory hit onto one of our categories, for chip filtering. */
    fun categoryFor(station: RadioStation, fallback: String): String {
        val text = (station.name + " " + station.tags).lowercase()
        val tamil = station.language.lowercase().contains("tamil")
        return when {
            text.contains("news") && tamil -> Category.TAMIL_NEWS
            text.contains("news") && station.countryCode == "IN" -> Category.INDIA_NEWS
            text.contains("news") -> Category.WORLD_NEWS
            tamil && (text.contains("bakthi") || text.contains("devotional") ||
                text.contains("bhakti") || text.contains("temple")) -> Category.TAMIL_DEVOTIONAL
            tamil && station.countryCode in listOf("LK", "MY", "SG") -> Category.TAMIL_FM
            tamil -> Category.TAMIL
            else -> fallback
        }
    }
}
