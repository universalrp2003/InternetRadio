package com.universalrp.cleansweep.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * wrong. This fetches a few fresh, dated snippets first (DuckDuckGo instant answers and
 * Wikipedia search) and the assistant hands them to the model together with today's date,
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

    /** Runs both sources and keeps up to four usable snippets. Never throws. */
    suspend fun lookup(query: String): Lookup = withContext(Dispatchers.IO) {
        val q = query.trim().take(300)
        if (q.isEmpty()) return@withContext Lookup(emptyList())
        val out = ArrayList<Snippet>(4)
        runCatching { duckDuckGo(q) }.getOrNull()?.let { out.addAll(it) }
        if (out.size < 4) {
            runCatching { wikipedia(q) }.getOrNull()?.let { out.addAll(it) }
        }
        Lookup(out.take(4))
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

    /** Wikipedia's keyless search API: titles plus short text extracts. */
    private fun wikipedia(query: String): List<Snippet> {
        val url = "https://en.wikipedia.org/w/api.php?action=query&list=search" +
            "&srsearch=" + URLEncoder.encode(query, "UTF-8") +
            "&srlimit=3&format=json&utf8=1"
        val body = get(url).ifBlank { return emptyList() }
        return try {
            val results = JSONObject(body).optJSONObject("query")?.optJSONArray("search")
                ?: return emptyList()
            val out = ArrayList<Snippet>(3)
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val title = item.optString("title").trim()
                val snippet = stripTags(item.optString("snippet")).trim().take(400)
                if (title.isNotBlank() && snippet.length > 40) {
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
            out
        } catch (e: Exception) {
            emptyList()
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
                setRequestProperty("User-Agent", "CleanSweep/2.7 (Android)")
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
