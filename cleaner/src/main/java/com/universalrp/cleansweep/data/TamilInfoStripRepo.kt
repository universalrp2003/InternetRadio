package com.universalrp.cleansweep.data

import android.content.Context
import android.content.pm.PackageManager
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
 * Dated, attributed Tamil headlines, Indian & International news, and on-device cybersecurity / digital safety alerts.
 * Covers international news, India news, Tamil Nadu local news, and app risk warnings.
 * Maintains a large cache (up to 100 items), rotates cyclic headlines, and supports manual refresh.
 */
object TamilInfoStripRepo {

    data class Item(
        val category: String, // e.g. "பாதுகாப்பு எச்சரிக்கை", "தமிழ்நாடு", "இந்தியா", "சர்வதேசம்"
        val title: String,
        val source: String,
        val date: String,
        val link: String,
        val isSecurityAlert: Boolean = false,
    )

    private const val PREFS = "cleansweep_info_strip"
    private const val KEY_LAST_FETCH = "last_fetch_ms"
    private const val KEY_ITEMS_JSON = "cached_items"
    private const val KEY_SHOWN_HASHES = "shown_title_hashes"
    private const val CACHE_VALIDITY_MS = 30 * 60 * 1000L // 20 minutes periodic refresh

    // Curated Tamil Nadu, India, International and Cyber Safety fallback updates
    private val DEFAULT_ITEMS = listOf(
        Item(
            category = "பாதுகாப்பு எச்சரிக்கை",
            title = "தெரியாத எண்களில் இருந்து வரும் APK கோப்புகளை அல்லது செயலிகளை ஒருபோதும் நிறுவ வேண்டாம் — சைபர் பாதுகாப்பு எச்சரிக்கை.",
            source = "சைபர் கிரைம் பிரிவு",
            date = "இன்று",
            link = "https://www.cert-in.org.in",
            isSecurityAlert = true,
        ),
        Item(
            category = "பாதுகாப்பு எச்சரிக்கை",
            title = "வங்கி கணக்கு விவரங்கள், கிரெடிட் கார்டு CVV அல்லது OTP-ஐ யாரிடமும் தொலைபேசியில் பகிராதீர்கள்.",
            source = "இந்திய சைபர் பாதுகாப்பு",
            date = "இன்று",
            link = "https://cybercrime.gov.in",
            isSecurityAlert = true,
        ),
        Item(
            category = "தமிழ்நாடு",
            title = "தமிழ்நாடு அரசு மின்சார வாகன பயன்பாட்டை ஊக்குவிக்க புதிய சார்ஜிங் நிலையங்களை விரிவாக்கம் செய்கிறது.",
            source = "தினத்தந்தி தமிழ்நாடு",
            date = "இன்று",
            link = "https://www.dailythanthi.com",
        ),
        Item(
            category = "இந்தியா",
            title = "இந்திய ரயில்வே புதிய அதிவிரைவு வந்தே பாரத் ரயில் வழித்தடங்களை அறிமுகப்படுத்தியுள்ளது.",
            source = "தினமணி இந்தியா",
            date = "இன்று",
            link = "https://www.dinamani.com",
        ),
        Item(
            category = "சர்வதேசம்",
            title = "சர்வதேச அளவில் விண்வெளி ஆராய்ச்சி மற்றும் பசுமை ஆற்றல் திட்டங்களில் புதிய முன்னேற்றங்கள் அறிவிப்பு.",
            source = "BBC Tamil",
            date = "இன்று",
            link = "https://feeds.bbci.co.uk/tamil/rss.xml",
        ),
        Item(
            category = "தமிழ்நாடு",
            title = "சென்னை மற்றும் முக்கிய மாவட்டங்களில் உள்கட்டமைப்பு மற்றும் குடிநீர் பாதுகாப்பு திட்டங்கள் துரிதம்.",
            source = "News18 Tamil",
            date = "இன்று",
            link = "https://tamil.news18.com",
        ),
    )

    // Multi-source providers covering Tamil Nadu, India, International & Cyber safety
    private val RSS_FEEDS = listOf(
        // Tamil Nadu News
        Triple("தமிழ்நாடு", "தினத்தந்தி தமிழ்நாடு", "https://www.dailythanthi.com/rss/tamilnadu"),
        Triple("தமிழ்நாடு", "தினமணி தமிழ்நாடு", "https://www.dinamani.com/%E0%AE%A4%E0%AE%AE%E0%AE%BF%E0%AE%B4%E0%AF%8D%E0%AE%A8%E0%AE%BE%E0%AE%9F%E0%AF%81/rssxml"),
        Triple("தமிழ்நாடு", "News18 தமிழ்நாடு", "https://tamil.news18.com/commonfeeds/v1/tam/rss/tamil-nadu.xml"),
        // National / India News
        Triple("இந்தியா", "News18 இந்தியா", "https://tamil.news18.com/commonfeeds/v1/tam/rss/national.xml"),
        Triple("இந்தியா", "தினமணி இந்தியா", "https://www.dinamani.com/%E0%AE%87%E0%AE%A8%E0%AF%8D%E0%AE%A4%E0%AE%BF%E0%AE%AF%E0%AE%BE/rssxml"),
        Triple("இந்தியா", "புதிய தலைமுறை", "https://www.puthiyathalaimurai.com/rss.xml"),
        // International News
        Triple("சர்வதேசம்", "BBC Tamil", "https://feeds.bbci.co.uk/tamil/rss.xml"),
        Triple("சர்வதேசம்", "Oneindia சர்வதேசம்", "https://tamil.oneindia.com/rss/feeds/tamil-international-fb.xml"),
        // General & Trending
        Triple("செய்திகள்", "Oneindia Tamil", "https://tamil.oneindia.com/rss/feeds/tamil-news-fb.xml"),
        Triple("செய்திகள்", "Google News Tamil", "https://news.google.com/rss?hl=ta&gl=IN&ceid=IN:ta"),
    )

    suspend fun getItems(context: Context, forceRefresh: Boolean = false): List<Item> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastFetch = prefs.getLong(KEY_LAST_FETCH, 0L)
        val now = System.currentTimeMillis()

        // Also inject on-device app security risk alerts into the feed!
        val appSecurityAlerts = checkAppSecurityAlerts(context)

        if (forceRefresh) {
            // Completely flush old news and shown hashes on manual refresh!
            prefs.edit().remove(KEY_ITEMS_JSON).remove(KEY_SHOWN_HASHES).apply()
        } else if ((now - lastFetch) < CACHE_VALIDITY_MS) {
            val cached = loadCached(prefs)
            if (cached.isNotEmpty()) return@withContext rotateAndPrioritize(prefs, appSecurityAlerts + cached)
        }

        // Fetch fresh items from multiple providers if network allows
        val fresh = fetchFromFeeds(context)
        if (fresh.isNotEmpty()) {
            saveCache(prefs, fresh, now)
            return@withContext rotateAndPrioritize(prefs, appSecurityAlerts + fresh)
        }

        val cached = loadCached(prefs)
        val itemsToUse = if (cached.isNotEmpty()) cached else DEFAULT_ITEMS
        rotateAndPrioritize(prefs, appSecurityAlerts + itemsToUse)
    }

    /**
     * Checks installed apps on device for High / Medium security risk permissions
     * and turns them into high-priority security alert items in the news feed!
     */
    private fun checkAppSecurityAlerts(context: Context): List<Item> {
        val list = mutableListOf<Item>()
        try {
            val pm = context.packageManager
            val defaultSms = try {
                android.provider.Telephony.Sms.getDefaultSmsPackage(context)
            } catch (e: Exception) {
                null
            }
            val installed = pm.getInstalledApplications(0)
            for (app in installed) {
                val pkg = app.packageName
                val isSystemFlag = (app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                // Strictly exclude Android OS, system vendors, OEMs, Google services, default SMS app, and our own apps
                if (isSystemFlag ||
                    SecurityScanner.isSystemPackage(pkg) ||
                    pkg.startsWith("com.qualcomm.") ||
                    pkg.startsWith("com.qti.") ||
                    pkg.startsWith("com.xiaomi.") ||
                    pkg.startsWith("com.miui.") ||
                    pkg.startsWith("com.google.android.") ||
                    pkg.startsWith("com.android.") ||
                    pkg.contains("cleaner") ||
                    pkg.contains("cleansweep") ||
                    pkg.contains("radio") ||
                    pkg.contains("launcher") ||
                    pkg == defaultSms
                ) {
                    continue
                }

                val label = try { pm.getApplicationLabel(app).toString() } catch (e: Exception) { pkg }
                if (label.isBlank() || label.startsWith("com.")) continue

                // Check SMS permission (High risk for unknown 3rd-party non-banking apps)
                val hasSms = pm.checkPermission(android.Manifest.permission.READ_SMS, pkg) == PackageManager.PERMISSION_GRANTED
                if (hasSms && !SecurityScanner.isBankingOrPaymentApp(pkg, label)) {
                    list.add(
                        Item(
                            category = "பாதுகாப்பு எச்சரிக்கை (High Risk)",
                            title = "எச்சரிக்கை: '$label' செயலி தனிப்பட்ட SMS செய்திகளைப் படிக்கும் அனுமதி பெற்றுள்ளது. தேவை இல்லையெனில் அனுமதியை நீக்கவும்.",
                            source = "பாதுகாப்பு ஆய்வு",
                            date = "உடனடி நடவடிக்கை",
                            link = "",
                            isSecurityAlert = true,
                        )
                    )
                }

                // Check overlay permission (draw over other apps)
                val hasOverlay = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager
                    appOps?.checkOpNoThrow(android.app.AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, app.uid, pkg) == android.app.AppOpsManager.MODE_ALLOWED
                } else false

                if (hasOverlay) {
                    list.add(
                        Item(
                            category = "பாதுகாப்பு எச்சரிக்கை (Medium Risk)",
                            title = "கவனம்: '$label' செயலி மற்ற திரைகளின் மேல் தோன்றும் அனுமதி (Overlay) பெற்றுள்ளது.",
                            source = "பாதுகாப்பு ஆய்வு",
                            date = "சரிபார்க்கவும்",
                            link = "",
                            isSecurityAlert = true,
                        )
                    )
                }
                if (list.size >= 4) break
            }
        } catch (e: Exception) {
            // Ignore
        }
        return list
    }

    /**
     * Reorders news items so users see fresh, unseen headlines first instead of repeating BBC headlines.
     */
    private fun rotateAndPrioritize(
        prefs: android.content.SharedPreferences,
        items: List<Item>,
    ): List<Item> {
        val shownSet = prefs.getStringSet(KEY_SHOWN_HASHES, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (shownSet.size >= items.size && items.isNotEmpty()) {
            shownSet.clear()
        }

        // Security alerts are always prioritized
        val securityAlerts = items.filter { it.isSecurityAlert }
        val normalNews = items.filter { !it.isSecurityAlert }

        val unseen = normalNews.filter { it.title.hashCode().toString() !in shownSet }
        val seen = normalNews.filter { it.title.hashCode().toString() in shownSet }

        unseen.take(5).forEach {
            shownSet.add(it.title.hashCode().toString())
        }
        prefs.edit().putStringSet(KEY_SHOWN_HASHES, shownSet).apply()

        // Combine: Interleave Security alerts at the top and throughout the stream
        val combined = mutableListOf<Item>()
        combined.addAll(securityAlerts)
        combined.addAll(unseen)
        combined.addAll(seen)

        return combined.distinctBy { it.title }
    }

    private fun fetchFromFeeds(context: Context): List<Item> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val net = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(net)
        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return emptyList()
        }

        val results = mutableListOf<Item>()

        // Fetch up to 12 items from each provider to build an extensive pool of 50-100 items
        for ((cat, srcLabel, feedUrl) in RSS_FEEDS) {
            if (results.size >= 100) break
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(feedUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 7_000
                    readTimeout = 7_000
                    setRequestProperty("User-Agent", "CleanSweep Android NewsReader/2.18")
                }
                if (conn.responseCode != 200) continue
                conn.inputStream.use { stream ->
                    val parser = Xml.newPullParser()
                    parser.setInput(stream, "UTF-8")
                    var inItem = false
                    var title = ""; var link = ""; var pubDate = ""
                    var itemsFromThisFeed = 0
                    while (parser.eventType != XmlPullParser.END_DOCUMENT && itemsFromThisFeed < 12) {
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
        results.addAll(DEFAULT_ITEMS.filter { it.category.startsWith("பாதுகாப்பு") })

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
        val json = prefs.getString(KEY_ITEMS_JSON, null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
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
                        isSecurityAlert = o.optBoolean("is_sec", false),
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
                o.put("is_sec", item.isSecurityAlert)
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
