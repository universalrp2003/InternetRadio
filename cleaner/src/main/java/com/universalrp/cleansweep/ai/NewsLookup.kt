package com.universalrp.cleansweep.ai

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Headlines come from dated RSS items, never encyclopedia search results. */
object NewsLookup {
    fun applies(q: String) = Regex("(?i)\\b(headlines?|news)\\b").containsMatchIn(q) &&
        !Regex("(?i)\\b(what is|meaning|define|history)\\b").containsMatchIn(q)

    suspend fun fetch(q: String): List<WebLookup.Snippet> = withContext(Dispatchers.IO) {
        val url = "https://news.google.com/rss/search?q=" + URLEncoder.encode("$q when:1d", "UTF-8") + "&hl=en-IN&gl=IN&ceid=IN:en"
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 12000
            connection.readTimeout = 12000
            connection.setRequestProperty("User-Agent", "CleanSweep Android")
            if (connection.responseCode != 200) return@withContext emptyList()
            connection.inputStream.use { stream ->
                val parser = Xml.newPullParser()
                parser.setInput(stream, "UTF-8")
                val results = mutableListOf<WebLookup.Snippet>()
                var inItem = false
                var title = ""; var link = ""; var date = ""
                while (parser.eventType != XmlPullParser.END_DOCUMENT && results.size < 6) {
                    if (parser.eventType == XmlPullParser.START_TAG) {
                        when (parser.name) {
                            "item" -> { inItem = true; title = ""; link = ""; date = "" }
                            "title" -> if (inItem) title = parser.nextText()
                            "link" -> if (inItem) link = parser.nextText()
                            "pubDate" -> if (inItem) date = parser.nextText()
                        }
                    } else if (parser.eventType == XmlPullParser.END_TAG && parser.name == "item") {
                        val published = runCatching { ZonedDateTime.parse(date, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
                        val age = published?.let { System.currentTimeMillis() - it }
                        if (age != null && age in 0..(36L * 60 * 60 * 1000) && title.isNotBlank() && link.startsWith("https://"))
                            results.add(WebLookup.Snippet("News RSS · $date", title, link))
                        inItem = false
                    }
                    parser.next()
                }
                results.distinctBy { it.text }
            }
        } catch (_: Exception) { emptyList() } finally { connection.disconnect() }
    }
    fun answer(items: List<WebLookup.Snippet>): String = if (items.isEmpty())
        "I could not retrieve recent, dated news headlines. Please try again later; I will not substitute old news or encyclopedia entries."
    else "Recent headlines (Google News RSS; publication dates shown, not independently verified):\n\n" +
        items.joinToString("\n\n") { "• ${it.text}\n${it.source}\n${it.url}" }
}
