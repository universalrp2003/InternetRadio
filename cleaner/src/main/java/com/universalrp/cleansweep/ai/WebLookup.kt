package com.universalrp.cleansweep.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Keyless live-web snippets for the assistant.
 *
 * Providers other than Google cannot browse by themselves, so a question like \"today's
 * news\" or \"current petrol price\" used to be answered from training memory — confidently
 * wrong. This fetches a few fresh, dated snippets first (DuckDuckGo instant answers,
 * Wikipedia search hits, and the lead paragraph of the top Wikipedia articles) and the
 * assistant hands them to the model together with today's date,
 * so the model quotes the live text instead of guessing. Both sources need no key and no
 * signup; when both come back empty the model is told today's date and asked to say when
 * an answer may be out of date, instead of inventing one.
 *
 * Only the question words are sent (as a search query), never anything about the phone.
 */
object WebLookup {

    data class Snippet(val source: String, val text: String, val url: String = "")

    data class Lookup(val snippets: List<Snippet>)

    private const val TIMEOUT_MS = 12_000

    /**
     * Words that usually mean \"the answer changes over time\". Used to explain in the UI
     * why a lookup ran — the lookup itself runs for every online question while the
     * switch is on, because a stale answer is worse than a slow one.
     */
    private val FRESH_WORDS = listOf(
        "today", "tonight", "tomorrow", "yesterday", "latest", "current", "now", "live",
        "price", "cost", "rate", "fare", "petrol", "diesel", "gold", "silver",
        "score", "result", "winner", "won", "match", "tournament", "ipl",
        "weather", "forecast", "cyclone", "rain",
        "news", "election", "minister", "president", "prime minister", "budget", "scheme",
        "version", "release", "update", "launched", "announced", "upcoming",
        "stock", "share", "nifty", "sensex", "job", "vacancy", "admission",
        // Tamil equivalents, so a Tamil question gets the same treatment.
        "இன்று", "இன்றைய", "நேற்று", "நாளை", "செய்தி", "செய்திகள்", "விலை",
        "முடிவு", "முடிவுகள்", "தேர்தல்", "வானிலை", "மழை", "புதிய", "வெளியீடு",
        "ஸ்கோர்", "வேலை", "தங்கம்", "பெட்ரோல்",
    )

    fun looksTimeSensitive(question: String): Boolean {
        val lower = question.lowercase()
        return FRESH_WORDS.any { lower.contains(it) }
    }

    /**
     * Runs every source and keeps up to six usable snippets. Never throws.
     *
     * The two sources run at the same time; then the lead paragraph of each of the top
     * Wikipedia hits is fetched, also at the same time — about two round-trips in
     * total. The article lead is the most current one-paragraph statement on a topic
     * (for \"who is the current X\" it names the incumbent outright), while search
     * snippets are fragments built around the query words and too often mangle the
     * answer — one such fragment is how \"Tamil Nadu's CM\" once came back wrong.
     */
    suspend fun lookup(query: String): Lookup = coroutineScope {
        val q = query.trim().take(300)
        if (q.isEmpty()) return@coroutineScope Lookup(emptyList())
        val ddg = async(Dispatchers.IO) { runCatching { duckDuckGo(q) }.getOrNull().orEmpty() }
        val wiki = async(Dispatchers.IO) { runCatching { wikipedia(q) }.getOrNull() }
        val ddgSnippets = ddg.await()
        val (wikiSnippets, wikiTitles) =
            wiki.await() ?: (emptyList<Snippet>() to emptyList<String>())
        val summaries = wikiTitles.take(2).map { title ->
            async(Dispatchers.IO) { runCatching { wikiSummary(title) }.getOrNull() }
        }.mapNotNull { it.await() }
        // Summaries first: the model reads top-down, and the lead is the best evidence.
        Lookup((summaries + ddgSnippets + wikiSnippets).take(6))
    }

    /** DuckDuckGo's keyless instant-answer API: an abstract plus related topics. */
    private fun duckDuckGo(query: String): List<Snippet> {
        val url = "https://api.duckduckgo.com/?q=" + URLEncoder.encode(query, "UTF-8") +
            "&format=json&no_html=1&skip_disambig=1"
        val body = get(url).ifBlank { return emptyList() }
        val out = ArrayList<Snippet>(3)
        return try {
            val json = JSONObject(body)
            val abstract = json.optString("AbstractText").trim()
            if (abstract.length > 40) {
                out.add(
                    Snippet(
                        source = json.optString("AbstractSource").ifBlank { "DuckDuckGo" },
                        text = abstract.take(600),
                        url = json.optString("AbstractURL"),
                    )
                )
            }
            val related = json.optJSONArray("RelatedTopics")
            if (related != null) {
                var i = 0
                while (out.size < 3 && i < related.length()) {
                    val item = related.optJSONObject(i)
                    val text = item?.optString("Text").orEmpty().trim()
                    if (text.length > 40) {
                        out.add(
                            Snippet(
                                source = "DuckDuckGo",
                                text = text.take(400),
                                url = item?.optString("FirstURL").orEmpty(),
                            )
                        )
                    }
                    i++
                }
            }
            out
        } catch (e: Exception) {
            out
        }
    }

    /**
     * Wikipedia's keyless search API: titles plus short text extracts. Returns the
     * snippets together with the hit titles, so their article leads can be fetched next.
     */
    private fun wikipedia(query: String): Pair<List<Snippet>, List<String>> {
        val url = "https://en.wikipedia.org/w/api.php?action=query&list=search" +
            "&srsearch=" + URLEncoder.encode(query, "UTF-8") +
            "&srlimit=3&format=json&utf8=1"
        val none = emptyList<Snippet>() to emptyList<String>()
        val body = get(url).ifBlank { return none }
        return try {
            val results = JSONObject(body).optJSONObject("query")?.optJSONArray("search")
                ?: return none
            val out = ArrayList<Snippet>(3)
            val titles = ArrayList<String>(3)
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val title = item.optString("title").trim()
                val snippet = stripTags(item.optString("snippet")).trim().take(400)
                if (title.isNotBlank() && snippet.length > 40) {
                    if (title !in titles) titles.add(title)
                    out.add(
                        Snippet(
                            source = "Wikipedia",
                            text = "$title: $snippet",
                            url = "https://en.wikipedia.org/wiki/" +
                                URLEncoder.encode(title.replace(' ', '_'), "UTF-8"),
                        )
                    )
                }
            }
            out to titles
        } catch (e: Exception) {
            none
        }
    }

    /**
     * The lead section of one Wikipedia article, via the keyless REST summary endpoint.
     * Skips disambiguation pages. Null when the article has no usable lead.
     */
    private fun wikiSummary(title: String): Snippet? {
        val slug = URLEncoder.encode(title.replace(' ', '_'), "UTF-8")
        val body = get("https://en.wikipedia.org/api/rest_v1/page/summary/$slug")
        if (body.isBlank()) return null
        return try {
            val json = JSONObject(body)
            if (json.optString("type") == "disambiguation") return null
            val extract = json.optString("extract").trim()
            if (extract.length < 60) return null
            val pageUrl = json.optJSONObject("content_urls")
                ?.optJSONObject("desktop")?.optString("page")
                .orEmpty().ifBlank { "https://en.wikipedia.org/wiki/$slug" }
            Snippet(source = "Wikipedia: $title", text = extract.take(700), url = pageUrl)
        } catch (e: Exception) {
            null
        }
    }

    private fun stripTags(html: String): String =
        html.replace(Regex("<[^>]*>"), "").replace("&quot;", "\"").replace("&amp;", "&")

    private fun get(url: String): String {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "CleanSweep/2.8 (Android)")
            }
            if (connection.responseCode !in 200..299) return ""
            connection.inputStream?.let { readAll(it) }.orEmpty()
        } catch (e: Exception) {
            ""
        } finally {
            connection?.disconnect()
        }
    }

    private fun readAll(stream: java.io.InputStream): String {
        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            val builder = StringBuilder()
            var line = reader.readLine()
            while (line != null) {
                builder.append(line)
                line = reader.readLine()
            }
            return builder.toString()
        }
    }
}
