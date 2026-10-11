package com.universalrp.cleansweep.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import com.universalrp.cleansweep.net.BoundedHttp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
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
    enum class Status { FETCHED, NO_RELEVANT, UNAVAILABLE, TIMED_OUT, SKIPPED }
    data class Lookup(val snippets: List<Snippet>, val office: Snippet? = null, val status: Status = Status.FETCHED,
        val fetchedAtMs: Long = System.currentTimeMillis())
    private class Trace : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Trace>
        val successfulGets = AtomicInteger()
    }

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
        val words = cleaned.split(Regex("\\s+"))
        if (words.size in 1..8 && words.all { it in setOf("hi", "hello", "hey", "வணக்கம்", "vanakkam", "there", "friend", "sir", "நண்பா", "சார்") }) return true
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
    suspend fun lookup(query: String, budget: RequestBudget = RequestBudget(AiRequestPolicy.Use.CHAT), newsTamil: Boolean? = null): Lookup {
        val q = query.trim().take(300)
        if (q.isEmpty() || isGreetingOrSmallTalk(q) || AssistantEvidencePolicy.asksAboutThisAssistant(q))
            return Lookup(emptyList(), status = Status.SKIPPED)
        val trace = Trace()
        return withTimeoutOrNull(minOf(AiRequestPolicy.WEB_BUDGET_MS, budget.remainingMs()).coerceAtLeast(1)) {
            withContext(Dispatchers.IO + trace) { coroutineScope {
                if (NewsLookup.applies(q)) {
                    return@coroutineScope NewsLookup.fetchResult(q, newsTamil ?: q.any { it.code in 0x0B80..0x0BFF })
                }
                val sq = searchQuery(q)
                // Seller/search excerpts, not an encyclopedia, are needed for an exact current price.
                if (ProductEvidencePolicy.priceQuestion(q)) {
                    val prices = readSource { webSearch(sq) }.orEmpty()
                        .filter { ProductEvidencePolicy.sellerPriceEvidence(it.source, it.text, q) }
                        .distinctBy { AssistantEvidencePolicy.evidenceKey(it.url, it.text) }.take(4)
                    return@coroutineScope Lookup(prices, status = status(prices, trace))
                }
                val ddg = async { readSource { duckDuckGo(sq) }.orEmpty() }
                val wiki = async { readSource { wikipedia(sq) } }
                val office = async { readSource { officeSummary(q) } }
                val ddgSnippets = ddg.await()
                val (wikiSnippets, wikiTitles) = wiki.await() ?: (emptyList<Snippet>() to emptyList<String>())
                val summaries = wikiTitles.take(2).map { title -> async { readSource { wikiSummary(title) } } }.mapNotNull { it.await() }
                val officeSnippet = office.await()
                val all = (listOfNotNull(officeSnippet) + summaries + ddgSnippets + wikiSnippets)
                    .filter { ProductEvidencePolicy.exactModel(it.source + " " + it.text, q) &&
                        (it == officeSnippet || AssistantEvidencePolicy.relevant(it.source + " " + it.text.take(400), sq)) }
                    .distinctBy { AssistantEvidencePolicy.evidenceKey(it.url, it.text) }.take(6)
                Lookup(all, officeSnippet?.takeIf { it in all }, status(all, trace))
            } }
        } ?: Lookup(emptyList(), status = Status.TIMED_OUT)
    }

    private fun status(items: List<Snippet>, trace: Trace): Status = when {
        items.isNotEmpty() -> Status.FETCHED
        trace.successfulGets.get() > 0 -> Status.NO_RELEVANT
        else -> Status.UNAVAILABLE
    }
    private suspend fun <T> readSource(block: suspend () -> T): T? = try { block() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    /** Real search result excerpts; no substitution of instant-answer encyclopedia abstracts for prices. */
    private suspend fun webSearch(query: String): List<Snippet> {
        val body = get("https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, "UTF-8"), "text/html")
        if (body.isBlank()) return emptyList()
        val anchors = Regex("""<a\b(?=[^>]*class=["'][^"']*result__a\b)[^>]*>.*?</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(body).take(8).toList()
        return anchors.mapIndexedNotNull { index, hit ->
            val href = Regex("""href=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(hit.value)?.groupValues?.get(1)?.replace("&amp;", "&") ?: return@mapIndexedNotNull null
            val url = searchTarget(href) ?: return@mapIndexedNotNull null
            val end = minOf(anchors.getOrNull(index + 1)?.range?.first ?: body.length, hit.range.last + 6_000)
            val tail = body.substring(hit.range.last + 1, end)
            val detail = Regex("""<(?:a|div)[^>]*class=["'][^"']*result__snippet[^"']*["'][^>]*>(.*?)</(?:a|div)>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(tail)?.groupValues?.get(1).orEmpty()
            val text = (stripTags(hit.value) + ": " + stripTags(detail)).replace(Regex("\\s+"), " ").trim().take(800)
            if (text.length < 35) return@mapIndexedNotNull null
            Snippet("Search excerpt · " + runCatching { URI(url).host }.getOrNull().orEmpty(), text, url)
        }
    }

    private fun searchTarget(raw: String): String? = runCatching {
        val uri = URI(if (raw.startsWith("//")) "https:$raw" else raw)
        val redirect = if (uri.host?.endsWith("duckduckgo.com") == true) uri.rawQuery?.split('&')?.firstOrNull { it.startsWith("uddg=") }
            ?.substringAfter('=')?.let { URLDecoder.decode(it, "UTF-8") } else null
        com.universalrp.cleansweep.data.NewsPolicy.canonicalLink(redirect ?: uri.toString())
    }.getOrNull()

    /** DuckDuckGo's keyless instant-answer API: an abstract plus related topics. */
    private suspend fun duckDuckGo(query: String): List<Snippet> {
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
    private suspend fun wikipedia(query: String): Pair<List<Snippet>, List<String>> {
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
            // megaprojects in India" for a CM question) — no fallback to an unrelated first hit.
            val pairs = out.zip(titles).filter { titleOverlaps(it.second, query) }
            val kept = pairs // No unrelated one-result fallback.
            kept.map { it.first } to kept.map { it.second }
        } catch (e: Exception) {
            none
        }
    }

    /**
     * The lead section of one Wikipedia article, via the keyless REST summary endpoint.
     * Skips disambiguation pages. Null when the article has no usable lead.
     */
    private suspend fun wikiSummary(title: String, maxLen: Int = 700): Snippet? {
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
     * after stopword removal has no evidence of relevance and is not filled with arbitrary hits.
     */
    private fun titleOverlaps(title: String, query: String): Boolean = AssistantEvidencePolicy.relevant(title, query)

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
    private suspend fun officeSummary(question: String): Snippet? {
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
    private suspend fun officeLead(title: String): Snippet? {
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
    private suspend fun wikiOpensearch(phrase: String): List<String> {
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

    private suspend fun get(url: String, accept: String = "application/json"): String {
        val response = BoundedHttp.request(url, AiRequestPolicy.WEB_BUDGET_MS, headers = mapOf("Accept" to accept, "User-Agent" to "LiveGuard/Android"))
        if (response.code !in 200..299) return ""
        coroutineContext[Trace]?.successfulGets?.incrementAndGet()
        return response.body
    }
}
