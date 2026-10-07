package com.universalrp.cleansweep.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
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
 * Wikipedia search hits, the lead paragraph of the top Wikipedia articles, and —
 * for "who is the current X" questions — the lead of the office article itself)
 * and the assistant hands them to the model together with today's date,
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
        // v2.9: a second, targeted retrieval for "who is the current X" questions.
        // The office article ("Chief Minister of Tamil Nadu") names the incumbent
        // outright, but plain search kept surfacing the person article instead —
        // whose lead ("served from 2021 to 2026") a small model still misread as
        // current. This fetch goes straight for the office article.
        val office = async(Dispatchers.IO) { runCatching { officeSummary(q) }.getOrNull() }
        val ddgSnippets = ddg.await()
        val (wikiSnippets, wikiTitles) =
            wiki.await() ?: (emptyList<Snippet>() to emptyList<String>())
        val summaries = wikiTitles.take(3).map { title ->
            async(Dispatchers.IO) { runCatching { wikiSummary(title) }.getOrNull() }
        }.mapNotNull { it.await() }
        // Office lead first, then article leads, then the rest: the model reads
        // top-down, and the office lead is the best evidence. Deduped because the
        // office fetch can return an article the plain search also found.
        val all = (listOfNotNull(office.await()) + summaries + ddgSnippets + wikiSnippets)
            .distinctBy { it.source to it.text.take(60) }
        Lookup(all.take(8))
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
            "&srlimit=5&format=json&utf8=1"
        val none = emptyList<Snippet>() to emptyList<String>()
        val body = get(url).ifBlank { return none }
        return try {
            val results = JSONObject(body).optJSONObject("query")?.optJSONArray("search")
                ?: return none
            val out = ArrayList<Snippet>(5)
            val titles = ArrayList<String>(5)
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

    /** Offices whose holder is the classic "who is the current X" question. */
    private val OFFICE_OF_PATTERN =
        Regex("""(?i)\b(chief minister|prime minister|vice president|president|governor|chief justice|mayor|cm|pm)\s+of\s+([a-z][a-z .'\-]{1,40})""")

    /** "... tamilnadu cm" / "... india pm" — the same question without "of". */
    private val OFFICE_TRAILING_PATTERN =
        Regex("""(?i)\b([a-z][a-z .'\-]{2,40}?)\s+(cm|pm)\b""")

    /** Question scaffolding ("who is the current ...") stripped off the place name. */
    private val OFFICE_SCAFFOLD =
        Regex("""(?i)^.*\b(who is|who's|who|what is|what's|what|tell me|name the|is the|is|the|current|present|now|new)\s+""")

    /** The office fetch only runs when the question is really asking who holds it now. */
    private val OFFICE_HINT = Regex("""(?i)\b(who|current|incumbent|now|latest|new|elected)\b""")

    /**
     * The lead paragraph of the office article itself ("Chief Minister of Tamil
     * Nadu"), resolved through Wikipedia's opensearch so spelling variants like
     * "tamilnadu" still land on the right page. Null unless an office-shaped
     * question yields an office-shaped article — never a guess.
     */
    private fun officeSummary(question: String): Snippet? {
        if (!OFFICE_HINT.containsMatchIn(question)) return null
        val ofMatch = OFFICE_OF_PATTERN.find(question)
        val trailingMatch = if (ofMatch == null) OFFICE_TRAILING_PATTERN.find(question) else null
        val officeRaw = (ofMatch?.groupValues?.get(1) ?: trailingMatch?.groupValues?.get(2))
            ?.lowercase().orEmpty()
        var place = (ofMatch?.groupValues?.get(2) ?: trailingMatch?.groupValues?.get(1))
            ?.trim()?.trimEnd('?', '.', ',', '!', ';', ':')?.trim().orEmpty()
        // The trailing pattern swallows the whole question ("who is the current
        // tamilnadu") — cut it back to the place name ("tamilnadu").
        if (trailingMatch != null) {
            val stripped = OFFICE_SCAFFOLD.replace(place, "").trim()
            place = stripped.ifBlank { place.split(' ').lastOrNull().orEmpty() }
        }
        if (officeRaw.isBlank() || place.length < 3 || place.any { it.isDigit() }) return null
        val office = when (officeRaw) {
            "cm" -> "chief minister"
            "pm" -> "prime minister"
            else -> officeRaw
        }
        val phrase = titleWords(office) + " of " + titleWords(place)
        // Only an article that is actually about the office is accepted — the
        // first suggestion for a garbled phrase is usually a person or a place.
        for (title in wikiOpensearch(phrase)) {
            if (!title.lowercase().contains(office)) continue
            val summary = runCatching { wikiSummary(title) }.getOrNull()
            if (summary != null) return summary
        }
        return null
    }

    private fun titleWords(text: String): String =
        text.split(' ').filter { it.isNotBlank() }.joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercase() }
        }

    /** Wikipedia's keyless opensearch: fuzzy title suggestions for a phrase. */
    private fun wikiOpensearch(phrase: String): List<String> {
        val url = "https://en.wikipedia.org/w/api.php?action=opensearch&search=" +
            URLEncoder.encode(phrase, "UTF-8") + "&limit=5&namespace=0&format=json"
        val body = get(url).ifBlank { return emptyList() }
        return try {
            val titles = JSONArray(body).optJSONArray(1) ?: return emptyList()
            (0 until titles.length()).mapNotNull { titles.optString(it).trim().ifBlank { null } }
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
                setRequestProperty("User-Agent", "CleanSweep/2.10 (Android)")
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
