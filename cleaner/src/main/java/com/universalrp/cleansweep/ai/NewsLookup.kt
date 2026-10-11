package com.universalrp.cleansweep.ai

import com.universalrp.cleansweep.data.AppLang
import com.universalrp.cleansweep.data.DisplayTime
import com.universalrp.cleansweep.data.Lang
import com.universalrp.cleansweep.data.NewsPolicy
import com.universalrp.cleansweep.net.BoundedHttp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.time.ZoneId

/** Dated RSS only, TODAY in the phone's timezone. A fetch timestamp is never a publication date. */
object NewsLookup {
    fun applies(q: String) = (Regex("(?i)\\b(headlines?|news)\\b").containsMatchIn(q) || q.contains("செய்தி")) &&
        !Regex("(?i)\\b(what is|meaning|define|history)\\b").containsMatchIn(q)

    suspend fun fetch(q: String): List<WebLookup.Snippet> = fetchResult(q).snippets

    suspend fun fetchResult(q: String, tamil: Boolean = q.any { it.code in 0x0B80..0x0BFF }): WebLookup.Lookup = withContext(Dispatchers.IO) {
        val topic = q.replace(Regex("(?i)\\b(tell|me|please|show|give|what|are|the|today|today's|latest|current|headlines?|news)\\b"), " ")
            .replace(Regex("இன்றைய|இன்று|செய்திகள்|செய்தி|சொல்லுங்கள்|சொல்|காட்டு"), " ").replace(Regex("\\s+"), " ").trim(' ', '?', '.', '!')
        val params = if (tamil) "hl=ta&gl=IN&ceid=IN:ta" else "hl=en-IN&gl=IN&ceid=IN:en"
        val url = if (topic.isBlank()) "https://news.google.com/rss?$params"
            else "https://news.google.com/rss/search?q=" + URLEncoder.encode("$topic when:1d", "UTF-8") + "&$params"
        try {
            val response = BoundedHttp.request(url, AiRequestPolicy.WEB_BUDGET_MS,
                headers = mapOf("Accept" to "application/rss+xml,application/xml", "User-Agent" to "LiveGuard/Android"))
            if (response.code !in 200..299) return@withContext WebLookup.Lookup(emptyList(), status = WebLookup.Status.UNAVAILABLE)
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()
            val items = NewsPolicy.parseFeed(response.body, "news", "Google News RSS")
                .filter { NewsPolicy.isToday(it.publishedAtMs, now, zone) }.distinctBy { it.key }.take(6)
                .map { WebLookup.Snippet(it.source + " · " + DisplayTime.dated(it.publishedAtMs, tamil), it.title, it.link) }
            WebLookup.Lookup(items, status = if (items.isEmpty()) WebLookup.Status.NO_RELEVANT else WebLookup.Status.FETCHED)
        } catch (e: CancellationException) { throw e } catch (error: Exception) { WebLookup.Lookup(emptyList(), status = WebLookup.Status.UNAVAILABLE) }
    }

    fun answer(items: List<WebLookup.Snippet>, tamil: Boolean = Lang.isTamil): String {
        val language = if (tamil) AppLang.TA else AppLang.EN
        return if (items.isEmpty()) Lang.translate(language,
            "I could not retrieve today's dated headlines. I will not substitute old news or encyclopedia entries. Please retry later.")
        else Lang.translate(language, "Today's headlines (RSS publication dates; not independently verified):") + "\n\n" +
            items.joinToString("\n\n") { "• ${it.text}\n${it.source}\n${it.url}" }
    }
}
