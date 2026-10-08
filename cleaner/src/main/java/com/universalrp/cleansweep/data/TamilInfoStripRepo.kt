package com.universalrp.cleansweep.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Dated, attributed Tamil headlines and cybersecurity / digital safety alerts.
 * Uses trusted Tamil news RSS feeds and cert-in / tech safety feeds.
 * Caches items locally and refreshes periodically, respecting metered data.
 */
object TamilInfoStripRepo {

    data class Item(
        val category: String, // e.g. "செய்திகள்" (News) or "பாதுகாப்பு" (Cybersecurity)
        val title: String,
        val source: String,
        val date: String,
        val link: String,
    )

    private const val PREFS = "cleansweep_info_strip"
    private const val KEY_LAST_FETCH = "last_fetch_ms"
    private const val KEY_ITEMS_JSON = "cached_items"
    private const val CACHE_VALIDITY_MS = 30 * 60 * 1000L // 30 minutes periodic refresh

    // Fallback curated alerts to ensure something is always displayed if offline
    private val DEFAULT_ITEMS = listOf(
        Item(
            category = "செய்திகள்",
            title = "தெரியாத எண்களில் இருந்து வரும் APK கோப்புகளை ஒருபோதும் நிறுவ வேண்டாம் — சைபர் பாதுகாப்பு எச்சரிக்கை.",
            source = "பாதுகாப்பு",
            date = "அக்டோபர் 2026",
            link = "https://www.cert-in.org.in",
        ),
        Item(
            category = "செய்திகள்",
            title = "வங்கி கணக்கு விவரங்கள் அல்லது OTP-ஐ யாரிடமும் தொலைபேசியில் பகிராதீர்கள்.",
            source = "பாதுகாப்பு",
            date = "அக்டோபர் 2026",
            link = "https://cybercrime.gov.in",
        ),
        Item(
            category = "செய்திகள்",
            title = "தமிழ்நாடு, இந்தியா மற்றும் சர்வதேச முக்கிய நிகழ்வுகள் உடனுக்குடன் வழங்கப்படுகின்றன.",
            source = "செய்திகள்",
            date = "அக்டோபர் 2026",
            link = "https://tamil.news18.com",
        ),
    )

    private val RSS_FEEDS = listOf(
        // BBC Tamil RSS
        Triple("செய்திகள்", "BBC Tamil", "https://feeds.bbci.co.uk/tamil/rss.xml"),
        // News18 Tamil
        Triple("செய்திகள்", "News18 Tamil", "https://tamil.news18.com/commonfeeds/v1/tam/rss/national.xml"),
        // Oneindia Tamil
        Triple("செய்திகள்", "Oneindia Tamil", "https://tamil.oneindia.com/rss/feeds/tamil-news-fb.xml"),
        // Google News Tamil RSS
        Triple("செய்திகள்", "Top News", "https://news.google.com/rss?hl=ta&gl=IN&ceid=IN:ta"),
    )

    suspend fun getItems(context: Context, forceRefresh: Boolean = false): List<Item> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastFetch = prefs.getLong(KEY_LAST_FETCH, 0L)
        val now = System.currentTimeMillis()

        if (!forceRefresh && (now - lastFetch) < CACHE_VALIDITY_MS) {
            val cached = loadCached(prefs)
            if (cached.isNotEmpty()) return@withContext cached
        }

        // Fetch fresh items if network allows
        val fresh = fetchFromFeeds(context)
        if (fresh.isNotEmpty()) {
            saveCache(prefs, fresh, now)
            return@withContext fresh
        }

        val cached = loadCached(prefs)
        if (cached.isNotEmpty()) cached else DEFAULT_ITEMS
    }

    private fun fetchFromFeeds(context: Context): List<Item> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val net = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(net)
        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return emptyList()
        }

        val results = mutableListOf<Item>()

        // 1. Fetch from top Tamil and national RSS feeds
        for ((cat, srcLabel, feedUrl) in RSS_FEEDS) {
            if (results.size >= 15) break
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(feedUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    setRequestProperty("User-Agent", "CleanSweep Android NewsReader")
                }
                if (conn.responseCode != 200) continue
                conn.inputStream.use { stream ->
                    val parser = Xml.newPullParser()
                    parser.setInput(stream, "UTF-8")
                    var inItem = false
                    var title = ""; var link = ""; var pubDate = ""
                    while (parser.eventType != XmlPullParser.END_DOCUMENT && results.size < 12) {
                        when (parser.eventType) {
                            XmlPullParser.START_TAG -> {
                                when (parser.name) {
                                    "item" -> { inItem = true; title = ""; link = ""; pubDate = "" }
                                    "title" -> if (inItem) title = parser.nextText()
                                    "link" -> if (inItem) link = parser.nextText()
                                    "pubDate" -> if (inItem) pubDate = parser.nextText()
                                }
                            }
                            XmlPullParser.END_TAG -> {
                                if (parser.name == "item" && inItem) {
                                    val cleanedTitle = cleanTitle(title)
                                    val formattedDate = formatDate(pubDate)
                                    if (cleanedTitle.isNotBlank() && isTamil(cleanedTitle)) {
                                        results.add(
                                            Item(
                                                category = cat,
                                                title = cleanedTitle,
                                                source = srcLabel,
                                                date = formattedDate,
                                                link = link,
                                            )
                                        )
                                    }
                                    inItem = false
                                }
                            }
                        }
                        parser.next()
                    }
                }
            } catch (e: Exception) {
                // Ignore transient network errors on individual feeds
            } finally {
                conn?.disconnect()
            }
        }

        return results.distinctBy { it.title }
    }

    private fun cleanTitle(raw: String): String {
        return raw.replace(Regex("<.*?>"), "")
            .replace(Regex(" - [^-]+$"), "") // Remove trailing " - BBC News தமிழ்"
            .trim()
    }

    private fun formatDate(raw: String): String {
        if (raw.isBlank()) return "இன்று"
        return try {
            val zdt = ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME)
            val d = zdt.dayOfMonth
            val m = zdt.monthValue
            "$d/$m"
        } catch (e: Exception) {
            "சமீபத்தியது"
        }
    }

    private fun isTamil(s: String): Boolean =
        s.any { it.code in 0x0B80..0x0BFF }

    private fun loadCached(prefs: android.content.SharedPreferences): List<Item> {
        val raw = prefs.getString(KEY_ITEMS_JSON, null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            val list = mutableListOf<Item>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    Item(
                        category = o.getString("cat"),
                        title = o.getString("title"),
                        source = o.getString("src"),
                        date = o.getString("date"),
                        link = o.getString("link"),
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveCache(prefs: android.content.SharedPreferences, items: List<Item>, now: Long) {
        try {
            val arr = org.json.JSONArray()
            for (item in items) {
                val o = org.json.JSONObject()
                o.put("cat", item.category)
                o.put("title", item.title)
                o.put("src", item.source)
                o.put("date", item.date)
                o.put("link", item.link)
                arr.put(o)
            }
            prefs.edit()
                .putString(KEY_ITEMS_JSON, arr.toString())
                .putLong(KEY_LAST_FETCH, now)
                .apply()
        } catch (e: Exception) {
            // Ignored
        }
    }
}
