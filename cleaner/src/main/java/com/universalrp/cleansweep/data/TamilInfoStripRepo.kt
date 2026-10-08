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
 * Covers international news, India news, Tamil Nadu local news, and cybersecurity alerts.
 * Caches items locally, tracks shown items so news doesn't repeat, and allows manual refresh.
 */
object TamilInfoStripRepo {

    data class Item(
        val category: String, // e.g. "செய்திகள்", "தமிழ்நாடு", "இந்தியா", "பாதுகாப்பு"
        val title: String,
        val source: String,
        val date: String,
        val link: String,
    )

    private const val PREFS = "cleansweep_info_strip"
    private const val KEY_LAST_FETCH = "last_fetch_ms"
    private const val KEY_ITEMS_JSON = "cached_items"
    private const val KEY_SHOWN_HASHES = "shown_title_hashes"
    private const val CACHE_VALIDITY_MS = 30 * 60 * 1000L // 30 minutes periodic refresh

    // Curated Tamil Nadu, India, International and Cyber Safety fallback updates
    private val DEFAULT_ITEMS = listOf(
        Item(
            category = "பாதுகாப்பு",
            title = "தெரியாத எண்களில் இருந்து வரும் APK கோப்புகளை அல்லது செயலிகளை ஒருபோதும் நிறுவ வேண்டாம் — சைபர் பாதுகாப்பு எச்சரிக்கை.",
            source = "சைபர் கிரைம் பிரிவு",
            date = "அக்டோபர் 2026",
            link = "https://www.cert-in.org.in",
        ),
        Item(
            category = "பாதுகாப்பு",
            title = "வங்கி கணக்கு விவரங்கள், கிரெடிட் கார்டு CVV அல்லது OTP-ஐ யாரிடமும் தொலைபேசியில் பகிராதீர்கள்.",
            source = "இந்திய சைபர் பாதுகாப்பு",
            date = "அக்டோபர் 2026",
            link = "https://cybercrime.gov.in",
        ),
        Item(
            category = "தமிழ்நாடு",
            title = "தமிழ்நாடு அரசு மின்சார வாகன பயன்பாட்டை ஊக்குவிக்க புதிய சார்ஜிங் நிலையங்களை விரிவாக்கம் செய்கிறது.",
            source = "தினத்தந்தி தமிழ்நாடு",
            date = "அக்டோபர் 2026",
            link = "https://www.dailythanthi.com",
        ),
        Item(
            category = "இந்தியா",
            title = "இந்திய ரயில்வே புதிய அதிவிரைவு வந்தே பாரத் ரயில் வழித்தடங்களை அறிமுகப்படுத்தியுள்ளது.",
            source = "தினமணி இந்தியா",
            date = "அக்டோபர் 2026",
            link = "https://www.dinamani.com",
        ),
        Item(
            category = "சர்வதேசம்",
            title = "சர்வதேச அளவில் விண்வெளி ஆராய்ச்சி மற்றும் பசுமை ஆற்றல் திட்டங்களில் புதிய முன்னேற்றங்கள் அறிவிப்பு.",
            source = "BBC Tamil",
            date = "அக்டோபர் 2026",
            link = "https://feeds.bbci.co.uk/tamil/rss.xml",
        ),
        Item(
            category = "தமிழ்நாடு",
            title = "சென்னை மற்றும் முக்கிய மாவட்டங்களில் உள்கட்டமைப்பு மற்றும் குடிநீர் பாதுகாப்பு திட்டங்கள் துரிதம்.",
            source = "News18 Tamil",
            date = "அக்டோபர் 2026",
            link = "https://tamil.news18.com",
        ),
    )

    // Multi-source providers covering Tamil Nadu, India, International & Cyber safety
    private val RSS_FEEDS = listOf(
        // Tamil Nadu News
        Triple("தமிழ்நாடு", "தினத்தந்தி தமிழ்நாடு", "https://www.dailythanthi.com/rss/tamilnadu"),
        // National / India News
        Triple("இந்தியா", "News18 Tamil", "https://tamil.news18.com/commonfeeds/v1/tam/rss/national.xml"),
        // International News
        Triple("சர்வதேசம்", "BBC Tamil", "https://feeds.bbci.co.uk/tamil/rss.xml"),
        // Top Multi-Category Tamil News
        Triple("செய்திகள்", "Oneindia Tamil", "https://tamil.oneindia.com/rss/feeds/tamil-news-fb.xml"),
        Triple("செய்திகள்", "Top News", "https://news.google.com/rss?hl=ta&gl=IN&ceid=IN:ta"),
        // Additional reliable sources
        Triple("தமிழ்நாடு", "தினமணி", "https://www.dinamani.com/rss.xml"),
        Triple("இந்தியா", "புதிய தலைமுறை", "https://www.puthiyathalaimurai.com/rss.xml"),
    )

    suspend fun getItems(context: Context, forceRefresh: Boolean = false): List<Item> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastFetch = prefs.getLong(KEY_LAST_FETCH, 0L)
        val now = System.currentTimeMillis()

        if (!forceRefresh && (now - lastFetch) < CACHE_VALIDITY_MS) {
            val cached = loadCached(prefs)
            if (cached.isNotEmpty()) return@withContext rotateAndPrioritize(prefs, cached)
        }

        // Fetch fresh items from multiple providers if network allows
        val fresh = fetchFromFeeds(context)
        if (fresh.isNotEmpty()) {
            saveCache(prefs, fresh, now)
            return@withContext rotateAndPrioritize(prefs, fresh)
        }

        val cached = loadCached(prefs)
        val itemsToUse = if (cached.isNotEmpty()) cached else DEFAULT_ITEMS
        rotateAndPrioritize(prefs, itemsToUse)
    }

    /**
     * Reorders news items so users see fresh, unseen headlines first instead of repeating BBC headlines.
     */
    private fun rotateAndPrioritize(
        prefs: android.content.SharedPreferences,
        items: List<Item>,
    ): List<Item> {
        val shownSet = prefs.getStringSet(KEY_SHOWN_HASHES, emptySet())?.toMutableSet() ?: mutableSetOf()
        // If almost all items have been shown, reset history so news continues cycling
        if (shownSet.size >= items.size && items.isNotEmpty()) {
            shownSet.clear()
        }

        val unseen = items.filter { it.title.hashCode().toString() !in shownSet }
        val seen = items.filter { it.title.hashCode().toString() in shownSet }

        // Mark up to 3 of current unseen items as shown
        unseen.take(3).forEach {
            shownSet.add(it.title.hashCode().toString())
        }
        prefs.edit().putStringSet(KEY_SHOWN_HASHES, shownSet).apply()

        // Balance providers: alternate sources (News18, தினத்தந்தி, தினமணி, BBC, Oneindia, CERT-In)
        return (unseen + seen).distinctBy { it.title }
    }

    private fun fetchFromFeeds(context: Context): List<Item> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val net = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(net)
        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return emptyList()
        }

        val results = mutableListOf<Item>()

        // Fetch up to 4 items from each category to provide a rich variety
        for ((cat, srcLabel, feedUrl) in RSS_FEEDS) {
            if (results.size >= 25) break
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(feedUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6_000
                    readTimeout = 6_000
                    setRequestProperty("User-Agent", "CleanSweep Android NewsReader")
                }
                if (conn.responseCode != 200) continue
                conn.inputStream.use { stream ->
                    val parser = Xml.newPullParser()
                    parser.setInput(stream, "UTF-8")
                    var inItem = false
                    var title = ""; var link = ""; var pubDate = ""
                    var itemsFromThisFeed = 0
                    while (parser.eventType != XmlPullParser.END_DOCUMENT && itemsFromThisFeed < 4) {
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
                                        itemsFromThisFeed++
                                    }
                                    inItem = false
                                }
                            }
                        }
                        parser.next()
                    }
                }
            } catch (e: Exception) {
                // Individual feed failure shouldn't block others
            } finally {
                conn?.disconnect()
            }
        }

        // Add curated security / cyber warnings to the stream
        results.addAll(DEFAULT_ITEMS.filter { it.category == "பாதுகாப்பு" })

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
