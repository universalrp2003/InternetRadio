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

    /**
     * Everything the lookup found. [office] is the office article's lead when the
     * question named an office — it doubles as the verification source, so the
     * answer can be checked against it without trusting the model.
     */
    data class Lookup(val snippets: List<Snippet>, val office: Snippet? = null)

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

    private val GREETING_WORDS = setOf(
        "hi", "hello", "hey", "hola", "howdy", "sup", "yo",
        "good morning", "good afternoon", "good evening", "good night",
        "வணக்கம்", "காலை வணக்கம்", "மாலை வணக்கம்", "இரவு வணக்கம்",
        "how are you", "who are you", "what can you do", "help",
        "நலமா", "எப்படி இருக்கீங்க", "நீ யார்", "உன்னால் என்ன செய்ய முடியும்",
    )

    fun isGreetingOrSmallTalk(question: String): Boolean {
        val cleaned = question.trim().lowercase().trimEnd('?', '.', '!', ';', ',').trim()
        if (cleaned in GREETING_WORDS) return true
        if (cleaned.length <= 4 && (cleaned == "hi" || cleaned == "hey" || cleaned == "வணக்கம்")) return true
        return false
    }

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
        if (q.isEmpty() || isGreetingOrSmallTalk(q)) return@coroutineScope Lookup(emptyList())
        if (NewsLookup.applies(q)) return@coroutineScope Lookup(NewsLookup.fetch(q))
        // v2.11: chit-chat ("hi you know who is...") makes a terrible search query — it
        // once surfaced "List of megaprojects in India" for a CM question. Search the
        // shaped query; the office parser and the model still get the raw question.
        val sq = searchQuery(q)
        val ddg = async(Dispatchers.IO) { runCatching { duckDuckGo(sq) }.getOrNull().orEmpty() }
        val wiki = async(Dispatchers.IO) { runCatching { wikipedia(sq) }.getOrNull() }
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
        val officeSnippet = office.await()
        val all = (listOfNotNull(officeSnippet) + summaries + ddgSnippets + wikiSnippets)
            .distinctBy { it.source to it.text.take(60) }
        Lookup(all.take(8), officeSnippet)
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
            // v2.11: drop hits with zero lexical overlap with the question ("List of
            // megaprojects in India" for a CM question) — with a one-hit fallback so
            // a strict filter can never silence the evidence entirely.
            val pairs = out.zip(titles).filter { titleOverlaps(it.second, query) }
            val kept = if (pairs.isNotEmpty()) pairs else out.zip(titles).take(1)
            kept.map { it.first } to kept.map { it.second }
        } catch (e: Exception) {
            none
        }
    }

    /**
     * The lead section of one Wikipedia article, via the keyless REST summary endpoint.
     * Skips disambiguation pages. Null when the article has no usable lead.
     */
    private fun wikiSummary(title: String, maxLen: Int = 700): Snippet? {
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
            Snippet(source = "Wikipedia: $title", text = extract.take(maxLen), url = pageUrl)
        } catch (e: Exception) {
            null
        }
    }

    /** Offices whose holder is the classic "who is the current X" question. */
    private val OFFICE_OF_PATTERN =
        Regex("""(?i)\b(chief minister|prime minister|vice president|president|governor|chief justice|mayor|cm|pm)\s+of\s+([a-z][a-z .'\-]{1,40})""")

    /** "... tamilnadu cm" / "... india pm" — the same question without "of". */
    private val OFFICE_TRAILING_PATTERN =
        Regex("""(?i)\b([a-z][a-z .'\-]{2,40}?)\s+(chief minister|prime minister|vice president|president|governor|chief justice|mayor|cm|pm)\b""")

    /** Question scaffolding ("who is the current ...") stripped off the place name. */
    private val OFFICE_SCAFFOLD =
        Regex("""(?i)^.*\b(who is|who's|who|what is|what's|what|tell me|name the|is the|is|the|current|present|now|new)\s+""")

    /** The office fetch only runs when the question is really asking who holds it now. */
    private val OFFICE_HINT = Regex("""(?i)\b(who|current|incumbent|now|latest|new|elected)\b""")

    /** Voice-typo: "cm if tamilnadu" means "cm of tamilnadu". */
    private val OFFICE_IF_PATTERN =
        Regex("""(?i)\b(chief minister|prime minister|vice president|president|governor|chief justice|mayor|cm|pm)\s+if\s+""")

    /** Chit-chat at the start of a question that only pollutes a search query. */
    private val SEARCH_SCAFFOLD =
        Regex("""(?i)^(?:\s*(?:hi|hello|hey|please|kindly|can you|could you|would you|do you know|you know|tell me|i want to know|want to know|let me know)(?:\b[\s,]+|$))+""")

    /** A question opener is not a search term either ("who is 2026 current cm ..."). */
    private val QUESTION_OPENER =
        Regex("""(?i)^(who is|who's|who|what is|what's|what|when is|when|where is|where|which is|which|is|are|do|does)\s+""")

    /** Words too generic to prove a search hit is about the question. */
    private val TITLE_STOPWORDS = setOf(
        "the", "of", "in", "on", "and", "for", "a", "an", "to", "list", "lists",
        "with", "from", "by", "who", "what", "when", "where", "which", "is",
        "are", "was", "were", "be", "been", "india", "indian", "state", "states",
    )

    /**
     * Shapes "hi you know who is 2026 current cm of tamilnadu" into "2026 current
     * cm of tamilnadu" for the search engines. Falls back to the raw question when
     * nothing sensible is left.
     */
    private fun searchQuery(question: String): String {
        var q = question.trim().trimEnd('?', '.', '!', ';').trim()
        q = SEARCH_SCAFFOLD.replace(q, "").trim()
        q = QUESTION_OPENER.replace(q, "").trim()
        return if (q.length >= 3) q.take(300) else question.trim().take(300)
    }

    /**
     * Whether a search-hit title shares at least one significant word (either way
     * round, so "tamilnadu" still matches "Tamil Nadu") with the question. Empty
     * after stopword removal means a non-Latin query — kept, never filtered.
     */
    private fun titleOverlaps(title: String, query: String): Boolean {
        fun words(s: String) = s.lowercase().split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 2 && it !in TITLE_STOPWORDS }
        // "pm of india" must still match "Prime Minister of India".
        val qw = words(query).flatMap {
            when (it) {
                "cm" -> listOf("cm", "chief", "minister")
                "pm" -> listOf("pm", "prime", "minister")
                else -> listOf(it)
            }
        }.toSet()
        if (qw.isEmpty()) return true
        return words(title).any { t ->
            t.length >= 3 && qw.any { q -> t.contains(q) || q.contains(t) }
        }
    }

    /**
     * Moves the sentence that settles a "who holds it now" question ("The incumbent
     * is ...") to the front of an office lead, so it survives every truncation —
     * the model's prompt cut and the bubble's 220-char evidence line alike.
     */
    private fun prioritizeDecisive(text: String): String {
        // A year-ending sentence is also a boundary; single-letter initials are not.
        val sentences = text.split(Regex("(?<=[!?])\\s+|(?<=[a-z0-9]\\.)\\s+(?=[A-Z])")).filter { it.isNotBlank() }
        if (sentences.size < 2) return text
        val (key, rest) = sentences.partition { s ->
            s.contains(Regex("(?i)\\bincumbent\\b|currently (held|the)|is the current"))
        }
        return if (key.isEmpty()) text else (key + rest).joinToString(" ")
    }

    /**
     * The lead paragraph of the office article itself ("Chief Minister of Tamil
     * Nadu"), resolved through Wikipedia's opensearch so spelling variants like
     * "tamilnadu" still land on the right page. Null unless an office-shaped
     * question yields an office-shaped article — never a guess.
     */
    private fun officeSummary(question: String): Snippet? {
        if (!OFFICE_HINT.containsMatchIn(question)) return null
        // v2.11: voice typing hears "of" as "if" ("cm if tamilnadu") — hear it as "of"
        // when it sits between an office word and whatever follows.
        val heard = OFFICE_IF_PATTERN.replace(question) { "${it.groupValues[1]} of " }
        val ofMatch = OFFICE_OF_PATTERN.find(heard)
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
            if (title.startsWith("List of", ignoreCase = true) ||
                title.contains("deputy", ignoreCase = true) ||
                title.contains("former", ignoreCase = true)) continue
            // A longer extract: the incumbent sentence sits past the first paragraph,
            // and the plain 700-char cut once hid it. The decisive sentence is moved
            // to the front so neither the model nor the 220-char bubble line can miss it.
            val summary = runCatching { officeLead(title) }.getOrNull()
            if (summary != null) return summary.copy(text = prioritizeDecisive(summary.text))
        }
        return null
    }

    /** Read the entire lead before selecting evidence; REST summaries can omit the holder. */
    private fun officeLead(title: String): Snippet? {
        val encoded = URLEncoder.encode(title, "UTF-8")
        val body = get("https://en.wikipedia.org/w/api.php?action=query&prop=extracts&exintro=1&explaintext=1&redirects=1&format=json&titles=$encoded")
        val pages = runCatching { JSONObject(body).getJSONObject("query").getJSONObject("pages") }.getOrNull()
        if (pages != null) {
            val keys = pages.keys()
            while (keys.hasNext()) {
                val page = pages.optJSONObject(keys.next()) ?: continue
                val text = page.optString("extract").trim()
                if (text.isNotEmpty()) return Snippet("Wikipedia: $title", prioritizeDecisive(text),
                    "https://en.wikipedia.org/wiki/" + encoded.replace("+", "_"))
            }
        }
        return wikiSummary(title, Int.MAX_VALUE)
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
                setRequestProperty("User-Agent", "CleanSweep/2.12 (Android)")
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
