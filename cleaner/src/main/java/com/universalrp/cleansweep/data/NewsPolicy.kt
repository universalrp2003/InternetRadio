package com.universalrp.cleansweep.data

import java.io.StringReader
import java.net.URI
import java.text.Normalizer
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

/** Real published headlines only. Fetch time, static tips and missing dates are NOT publication dates. */
data class NewsHeadline(val category: String, val title: String, val source: String, val publishedAtMs: Long, val link: String) {
    val key: String get() = NewsPolicy.titleKey(title)
}

object NewsPolicy {
    const val MAX_ITEMS = 80
    const val MAX_FEED_CHARS = 512 * 1024

    fun isToday(publishedAtMs: Long, nowMs: Long, zone: ZoneId): Boolean =
        publishedAtMs > 0 && publishedAtMs <= nowMs &&
            Instant.ofEpochMilli(publishedAtMs).atZone(zone).toLocalDate() == Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()

    fun today(items: List<NewsHeadline>, nowMs: Long, zone: ZoneId): List<NewsHeadline> {
        val titles = hashSetOf<String>(); val links = hashSetOf<String>()
        return items.sortedByDescending { it.publishedAtMs }.filter { item ->
            val key = item.key
            val url = canonicalLink(item.link)
            isToday(item.publishedAtMs, nowMs, zone) && item.title.length in 10..500 &&
                item.title.any { it.code in 0x0B80..0x0BFF } && key.isNotBlank() && url != null &&
                key !in titles && url !in links && titles.add(key) && links.add(url)
        }.take(MAX_ITEMS)
    }

    fun nextUnseen(items: List<NewsHeadline>, shown: Set<String>): NewsHeadline? = items.firstOrNull { it.key !in shown }

    fun titleKey(title: String): String = Normalizer.normalize(title, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{M}\\p{N}]+"), "").take(500)

    fun cleanTitle(raw: String, publisher: String): String {
        val plain = raw.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
        // Remove ONLY an identified publisher suffix, never arbitrary words after a dash.
        val suffix = Regex("\\s+[-–—|]\\s+" + Regex.escape(publisher.trim()) + "$", RegexOption.IGNORE_CASE)
        return (if (publisher.isNotBlank()) plain.replace(suffix, "") else plain).take(500).trim()
    }

    fun canonicalLink(raw: String): String? = runCatching {
        val uri = URI(raw.trim())
        if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null) return null
        val query = uri.rawQuery?.split('&')?.filterNot {
            val k = it.substringBefore('=').lowercase(Locale.ROOT)
            k.startsWith("utm_") || k in setOf("fbclid", "gclid", "oc", "ref")
        }?.joinToString("&")?.takeIf { it.isNotEmpty() }
        buildString {
            append(uri.scheme.lowercase(Locale.ROOT)); append("://"); append(uri.host.lowercase(Locale.ROOT))
            if (uri.port != -1) append(":${uri.port}")
            append(uri.rawPath.orEmpty().ifEmpty { "/" }.trimEnd('/'))
            if (query != null) append("?$query")
        }
    }.getOrNull()

    fun publicationTime(raw: String): Long? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()?.let { return it }
        runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()?.let { return it }
        val formats = listOf(DateTimeFormatter.RFC_1123_DATE_TIME,
            DateTimeFormatter.ofPattern("EEE, d MMM uuuu HH:mm:ss Z", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM uuuu HH:mm:ss Z", Locale.ENGLISH))
        for (format in formats) runCatching { ZonedDateTime.parse(value, format).toInstant().toEpochMilli() }.getOrNull()?.let { return it }
        return null // No "today" substitution, and no yearless/local-time guess.
    }

    /** Bounded RSS/Atom parser; reject DTDs and resolve no external entities. No article body is fetched. */
    fun parseFeed(raw: String, category: String, publisher: String): List<NewsHeadline> {
        if (raw.length > MAX_FEED_CHARS || raw.contains("<!DOCTYPE", ignoreCase = true) || raw.contains("<!ENTITY", ignoreCase = true)) return emptyList()
        return runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true; isValidating = false }
            val builder = factory.newDocumentBuilder().apply {
                setEntityResolver { _, _ -> InputSource(StringReader("")) }
                setErrorHandler(object : org.xml.sax.helpers.DefaultHandler() {
                    override fun error(e: org.xml.sax.SAXParseException) { throw e }
                    override fun fatalError(e: org.xml.sax.SAXParseException) { throw e }
                })
            }
            val document = builder.parse(InputSource(StringReader(raw)))
            val nodes = document.getElementsByTagNameNS("*", "item").let {
                if (it.length > 0) it else document.getElementsByTagNameNS("*", "entry")
            }
            val result = ArrayList<NewsHeadline>()
            for (index in 0 until minOf(nodes.length, 60)) {
                val item = nodes.item(index) as? Element ?: continue
                fun children(name: String): List<Element> = (0 until item.childNodes.length).mapNotNull {
                    item.childNodes.item(it) as? Element
                }.filter { (it.localName ?: it.nodeName.substringAfter(':')) == name }
                fun text(name: String) = children(name).firstOrNull()?.textContent.orEmpty().trim()
                val date = publicationTime(text("pubDate").ifBlank { text("published").ifBlank { text("date") } }) ?: continue
                val source = text("source").ifBlank { publisher }.take(100)
                val title = cleanTitle(text("title"), source)
                val linkNode = children("link").firstOrNull { it.getAttribute("rel") in setOf("", "alternate") }
                val link = linkNode?.getAttribute("href")?.takeIf { it.isNotBlank() } ?: linkNode?.textContent.orEmpty().trim()
                val safeLink = canonicalLink(link) ?: continue
                if (title.isNotBlank()) result.add(NewsHeadline(category, title, source, date, safeLink))
            }
            result
        }.getOrDefault(emptyList())
    }
}
