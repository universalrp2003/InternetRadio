package com.universalrp.cleansweep.data

import android.content.SharedPreferences
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.universalrp.cleansweep.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZoneId

/** Today's real Tamil headlines only; no synthetic "today" news, app warnings or rotating safety-tip filler. */
object TamilInfoStripRepo {
    enum class Outcome { READY, NO_TODAY_NEWS, OFFLINE, FEEDS_UNAVAILABLE }
    data class Snapshot(val items: List<NewsHeadline>, val shown: Set<String>, val outcome: Outcome, val checkedAtMs: Long?)
    private const val PREFS = "cleansweep_info_strip"
    private const val ITEMS = "dated_headlines_v2"
    private const val LAST_ATTEMPT = "today_fetch_attempt"
    private const val FETCH_DAY = "today_fetch_day"
    private const val SHOWN = "today_shown_titles"
    private const val SHOWN_DAY = "shown_local_day"
    private const val LAST_OUTCOME = "today_outcome"
    private const val CACHE_MS = 15 * 60 * 1000L
    private const val RETRY_MS = 2 * 60 * 1000L

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

    private fun localScope(now: Long, zone: ZoneId) = zone.id + "|" + java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

    @Synchronized fun shownToday(context: Context): Set<String> {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return shownFor(p, localScope(System.currentTimeMillis(), ZoneId.systemDefault()))
    }

    @Synchronized private fun shownFor(p: SharedPreferences, scope: String): Set<String> {
        if (p.getString(SHOWN_DAY, null) != scope) { p.edit().putString(SHOWN_DAY, scope).remove(SHOWN).apply(); return emptySet() }
        return p.getStringSet(SHOWN, emptySet()).orEmpty().toSet()
    }

    suspend fun getItems(context: Context, forceRefresh: Boolean = false): Snapshot = withContext(Dispatchers.IO) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis(); val zone = ZoneId.systemDefault(); val day = localScope(now, zone)
        // Old caches had no machine-readable publication year/time and could contain static/fake headlines.
        prefs.edit().remove("cached_items").remove("shown_title_hashes").remove("last_fetch_ms").apply()
        val allCached = loadCache(prefs.getString(ITEMS, null))
        val cached = NewsPolicy.today(allCached, now, zone)
        val attempted = prefs.getLong(LAST_ATTEMPT, 0L)
        val lastOutcome = runCatching { Outcome.valueOf(prefs.getString(LAST_OUTCOME, "NO_TODAY_NEWS")!!) }.getOrDefault(Outcome.NO_TODAY_NEWS)
        val interval = if (lastOutcome in setOf(Outcome.OFFLINE, Outcome.FEEDS_UNAVAILABLE)) RETRY_MS else CACHE_MS
        if (!forceRefresh && prefs.getString(FETCH_DAY, null) == day && now >= attempted && now - attempted < interval) {
            return@withContext Snapshot(cached, shownFor(prefs, day), if (cached.isNotEmpty()) Outcome.READY else lastOutcome, attempted.takeIf { it > 0 })
        }
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = runCatching { connectivity?.activeNetwork?.let { connectivity.getNetworkCapabilities(it) } }.getOrNull()
        val internet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val fetched = if (internet) fetchFeeds() else emptyList()
        currentCoroutineContext().ensureActive()
        // Re-evaluate date AND timezone after networking, including midnight/time-zone changes during a fetch.
        val finished = System.currentTimeMillis(); val finishedZone = ZoneId.systemDefault(); val finishedDay = localScope(finished, finishedZone)
        val items = NewsPolicy.today(fetched.flatMap { it.items } + allCached, finished, finishedZone)
        val outcome = when {
            items.isNotEmpty() -> Outcome.READY
            !internet -> Outcome.OFFLINE
            fetched.none { it.success } -> Outcome.FEEDS_UNAVAILABLE
            else -> Outcome.NO_TODAY_NEWS
        }
        prefs.edit().putString(ITEMS, encode(items)).putLong(LAST_ATTEMPT, finished).putString(FETCH_DAY, finishedDay)
            .putString(LAST_OUTCOME, outcome.name).apply()
        // Do not overwrite headlines marked while the network refresh was in progress with a stale shown snapshot.
        Snapshot(items, shownFor(prefs, finishedDay), outcome, finished)
    }

    @Synchronized fun markShown(context: Context, key: String) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val day = localScope(System.currentTimeMillis(), ZoneId.systemDefault())
        val before = shownFor(prefs, day)
        val keys = (before.toList() + key.take(500)).distinct().takeLast(NewsPolicy.MAX_ITEMS * 2).toSet()
        prefs.edit().putString(SHOWN_DAY, day).putStringSet(SHOWN, keys).apply()
    }

    private data class FeedResult(val success: Boolean, val items: List<NewsHeadline>)
    private suspend fun fetchFeeds(): List<FeedResult> = coroutineScope {
        val limit = Semaphore(3)
        val deadline = System.nanoTime() + 10_000_000_000L // shared budget, not ten independent long waits.
        RSS_FEEDS.map { (category, publisher, url) -> async(Dispatchers.IO) {
            limit.withPermit {
                currentCoroutineContext().ensureActive()
                fun remainingMs() = ((deadline - System.nanoTime()) / 1_000_000).coerceAtMost(3_000).toInt()
                if (remainingMs() <= 0) return@withPermit FeedResult(false, emptyList())
                var connection: HttpURLConnection? = null
                try {
                    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = remainingMs().coerceAtLeast(1); readTimeout = remainingMs().coerceAtLeast(1)
                        setRequestProperty("User-Agent", "LiveGuard/${BuildConfig.VERSION_NAME} TamilHeadlines")
                        setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
                    }
                    connection = conn
                    if (conn.responseCode != 200 || remainingMs() <= 0) return@withPermit FeedResult(false, emptyList())
                    val raw = conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        val chars = CharArray(NewsPolicy.MAX_FEED_CHARS + 1); var count = 0
                        while (count < chars.size) {
                            currentCoroutineContext().ensureActive()
                            if (remainingMs() <= 0) return@withPermit FeedResult(false, emptyList())
                            conn.readTimeout = remainingMs().coerceAtLeast(1)
                            val n = reader.read(chars, count, chars.size - count)
                            if (n < 0) break
                            count += n
                        }
                        String(chars, 0, count)
                    }
                    currentCoroutineContext().ensureActive()
                    FeedResult(true, NewsPolicy.parseFeed(raw, category, publisher))
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { FeedResult(false, emptyList()) }
                finally { connection?.disconnect() }
            }
        } }.awaitAll()
    }

    private fun loadCache(raw: String?): List<NewsHeadline> = runCatching {
        val array = JSONArray(raw ?: "[]")
        (0 until minOf(array.length(), NewsPolicy.MAX_ITEMS)).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val time = item.optLong("published", 0L)
            if (time <= 0) null else NewsHeadline(item.optString("cat"), item.optString("title"), item.optString("src"), time, item.optString("link"))
        }
    }.getOrDefault(emptyList())

    private fun encode(items: List<NewsHeadline>): String = JSONArray().apply {
        items.take(NewsPolicy.MAX_ITEMS).forEach { item -> put(JSONObject().apply {
            put("cat", item.category); put("title", item.title); put("src", item.source)
            put("published", item.publishedAtMs); put("link", item.link)
        }) }
    }.toString()
}
