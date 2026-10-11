package com.universalrp.cleansweep.data

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class NewsPolicyTest {
    private val now = Instant.parse("2026-10-11T12:00:00Z").toEpochMilli()
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun item(title: String = "தமிழ்நாட்டில் புதிய செய்தி அறிவிப்பு", time: Long = now - 60_000, link: String = "https://example.org/news/1") =
        NewsHeadline("செய்தி", title, "Publisher", time, link)

    @Test fun rfcPublicationTimeHasAnActualYearAndOffset() {
        assertEquals(Instant.parse("2026-10-11T07:30:00Z").toEpochMilli(), NewsPolicy.publicationTime("Sun, 11 Oct 2026 13:00:00 +0530")!!)
    }
    @Test fun atomPublicationTimesSupportIsoOffsets() {
        assertEquals(Instant.parse("2026-10-11T07:30:00Z").toEpochMilli(), NewsPolicy.publicationTime("2026-10-11T13:00:00+05:30")!!)
        assertEquals(now, NewsPolicy.publicationTime("2026-10-11T12:00:00Z")!!)
    }
    @Test fun missingInvalidAndYearlessDatesNeverBecomeToday() {
        for (date in listOf("", "இன்று", "recent", "9/10", "bad date")) assertNull(NewsPolicy.publicationTime(date))
    }
    @Test fun yesterdayAndTwoDaysOldAreExcludedEvenIfFetchedNow() {
        assertEquals(1, NewsPolicy.today(listOf(item(), item("நேற்றைய பழைய செய்தி தகவல்", now - 86_400_000), item("இரண்டு நாள் பழைய செய்தி", now - 172_800_000)), now, zone).size)
    }
    @Test fun todayMeansThePhonesLocalTimezoneNotTheFeedOrUtcDay() {
        val published = Instant.parse("2026-10-10T20:00:00Z").toEpochMilli()
        assertTrue(NewsPolicy.isToday(published, now, zone))
        assertFalse(NewsPolicy.isToday(published, now, ZoneId.of("UTC")))
    }
    @Test fun futureTimestampsAndZeroAreNotFreshEvidence() {
        assertFalse(NewsPolicy.isToday(now + 1, now, zone)); assertFalse(NewsPolicy.isToday(0, now, zone))
    }
    @Test fun trackingAndFragmentVariantsDoNotRepeatAnArticle() {
        val items = listOf(item(link = "https://example.org/news/1?utm_source=feed#top"), item("மாறிய தலைப்புடன் அதே செய்தி", link = "https://example.org/news/1?fbclid=123"))
        assertEquals(1, NewsPolicy.today(items, now, zone).size)
    }
    @Test fun normalizedTamilTitlesDeduplicateAcrossPublishers() {
        val items = listOf(item(), item("தமிழ்நாட்டில் புதிய செய்தி அறிவிப்பு!", link = "https://second.org/news"))
        assertEquals(1, NewsPolicy.today(items, now, zone).size)
    }
    @Test fun newestDuplicateWinsAndNoCallerOrderAssumptionIsNeeded() {
        val old = item(time = now - 120_000); val newer = item(time = now - 1_000, link = "https://new.org/news")
        assertEquals(newer, NewsPolicy.today(listOf(old, newer), now, zone).single())
    }
    @Test fun emptyShortOrNonTamilHeadlinesCannotFillTheTamilNewsBanner() {
        assertTrue(NewsPolicy.today(listOf(item(""), item("செய்தி"), item("English placeholder")), now, zone).isEmpty())
    }
    @Test fun rssParsesPublishedHeadlinesAndOnlyKnownPublisherSuffixes() {
        val xml = """<rss><channel><item><title>தமிழ்நாடு புதிய செய்தி - Publisher</title><link>https://example.org/a</link><pubDate>Sun, 11 Oct 2026 10:00:00 GMT</pubDate></item></channel></rss>"""
        val result = NewsPolicy.parseFeed(xml, "செய்தி", "Publisher").single()
        assertEquals("தமிழ்நாடு புதிய செய்தி", result.title)
        assertEquals("தமிழ்நாடு - முக்கிய புதிய அறிவிப்பு", NewsPolicy.cleanTitle("தமிழ்நாடு - முக்கிய புதிய அறிவிப்பு", "Publisher"))
    }
    @Test fun atomUpdatedAndFeedBuildDatesDoNotPretendToBePublicationDates() {
        val xml = """<feed xmlns="http://www.w3.org/2005/Atom"><updated>2026-10-11T10:00:00Z</updated><entry><title>புதிய தமிழ்நாடு செய்தி அறிவிப்பு</title><link href="https://example.org/a"/><published>2026-10-11T09:00:00Z</published></entry><entry><title>பழைய செய்தி புதுப்பிக்கப்பட்டது</title><link href="https://example.org/b"/><updated>2026-10-11T10:00:00Z</updated></entry></feed>"""
        assertEquals(1, NewsPolicy.parseFeed(xml, "செய்தி", "Source").size)
    }
    @Test fun unsafeEntitiesUrlsOversizeAndMalformedXmlFailClosed() {
        assertTrue(NewsPolicy.parseFeed("<!DOCTYPE rss [<!ENTITY x SYSTEM 'file:///secret'>]><rss/>", "", "").isEmpty())
        assertTrue(NewsPolicy.parseFeed("x".repeat(NewsPolicy.MAX_FEED_CHARS + 1), "", "").isEmpty())
        assertTrue(NewsPolicy.parseFeed("<rss><broken>", "", "").isEmpty())
        assertNull(NewsPolicy.canonicalLink("javascript:alert(1)")); assertNull(NewsPolicy.canonicalLink("https://user:password@example.org/a"))
    }
    @Test fun boundedPoolAndShownQueueDoNotAutomaticallyCycleOldHeadlines() {
        val list = (1..120).map { item("தமிழ்நாடு புதிய செய்தி அறிவிப்பு எண் $it", now - it * 1_000, "https://example.org/$it") }
        val today = NewsPolicy.today(list, now, zone)
        assertEquals(NewsPolicy.MAX_ITEMS, today.size)
        assertEquals(today.first(), NewsPolicy.nextUnseen(today, emptySet()))
        assertNull(NewsPolicy.nextUnseen(today, today.map { it.key }.toSet()))
    }
}
