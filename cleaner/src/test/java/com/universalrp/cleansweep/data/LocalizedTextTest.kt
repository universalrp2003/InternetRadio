package com.universalrp.cleansweep.data

import com.universalrp.cleansweep.ai.AiFailurePolicy
import org.junit.Assert.*
import org.junit.Test

class LocalizedTextTest {
    @Test fun backgroundLanguageCanBeSpecifiedWithoutGlobalDefault() { Lang.set(AppLang.EN); assertEquals("நிறுத்து", Lang.translate(AppLang.TA, "Stop")); assertEquals("Stop", Lang.translate(AppLang.EN, "Stop")) }
    @Test fun notificationPlaceholderOrderIsPreserved() { assertEquals("இன்றைய மொபைல் தரவு: 1 GB · தொகுப்பில் மீதி: 2 GB", Lang.translate(AppLang.TA, "Mobile data today: %s · Pack left: %s", "1 GB", "2 GB")) }
    @Test fun allStableAiFailureKeysHaveTamilWording() { listOf("timeout", "HTTP 401", "HTTP 403", "HTTP 429", "HTTP 404", "HTTP 500", "no key", "no final answer", "network tasks are busy", "other").forEach { error -> val key = AiFailurePolicy.message(error); assertTrue(Lang.translatedKeys().contains(key)); assertTrue(Lang.translate(AppLang.TA, key).any { it.code in 0x0B80..0x0BFF }) } }
    @Test fun previewAndReviewUiKeysHaveTamilWording() { listOf("Voice A", "Voice B", "Preview", "Previous review", "Reading saved review…", "Preparing answer…", "Web lookup unavailable").forEach { assertTrue(Lang.translatedKeys().contains(it)) } }
}
