package com.universalrp.cleansweep.ai

import org.junit.Assert.*
import org.junit.Test

class AssistantEvidencePolicyTest {
    @Test fun screenshotQuestionUsesLocalConfigurationNotWikipedia() {
        assertTrue(AssistantEvidencePolicy.asksAboutThisAssistant("what ai api is this"))
        assertTrue(AssistantEvidencePolicy.asksAboutThisAssistant("Which AI model are you using?"))
    }
    @Test fun tamilIdentityQuestionsStayLocalToo() {
        assertTrue(AssistantEvidencePolicy.asksAboutThisAssistant("இது எந்த AI?"))
        assertTrue(AssistantEvidencePolicy.asksAboutThisAssistant("நீங்கள் யார்?"))
    }
    @Test fun generalApiAdviceAndComparisonsAreNotMisroutedToIdentity() {
        for (q in listOf("Which AI API should I use for this app?", "Compare OpenAI and Gemini APIs", "What is an API?", "What is the latest AI model?"))
            assertFalse(q, AssistantEvidencePolicy.asksAboutThisAssistant(q))
    }
    @Test fun identityDescribesConfigurationWithoutPretendingAnApiAnswered() {
        val text = AssistantEvidencePolicy.identity("Google Gemini", "configured-model", true, false, false)
        assertTrue(text.contains("Configured online provider: Google Gemini"))
        assertTrue(text.contains("configured-model")); assertTrue(text.contains("no AI API or web search"))
        assertFalse(text.contains("powered by")); assertTrue(text.contains("does not prove"))
    }
    @Test fun offlineAndFreeModeDisclosuresRemainHonest() {
        val text = AssistantEvidencePolicy.identity("Free AI", "", false, false, true)
        assertTrue(text.contains("local/offline")); assertTrue(text.contains("can switch providers"))
    }
    @Test fun unrelatedOpenAiResultIsNotEvidenceForABatteryQuestion() {
        assertFalse(AssistantEvidencePolicy.relevant("OpenAI is an American company", "Redmi battery temperature"))
    }
    @Test fun titleOverlapHandlesTamilNaduAndOfficeAbbreviations() {
        assertTrue(AssistantEvidencePolicy.relevant("Chief Minister of Tamil Nadu", "current cm of tamilnadu"))
        assertFalse(AssistantEvidencePolicy.relevant("List of megaprojects in India", "current cm of tamilnadu"))
    }
    @Test fun nonLatinAndEmptyQueriesDoNotForceAnUnrelatedResult() {
        assertFalse(AssistantEvidencePolicy.relevant("OpenAI", "தமிழ்நாடு வானிலை"))
        assertFalse(AssistantEvidencePolicy.relevant("Random article", "what is the"))
    }
    @Test fun duplicateArticleUrlsCollapseRegardlessOfSourceLabelOrTracking() {
        assertEquals(AssistantEvidencePolicy.evidenceKey("https://en.wikipedia.org/wiki/OpenAI#History", "lead"),
            AssistantEvidencePolicy.evidenceKey("https://en.wikipedia.org/wiki/OpenAI?utm_source=search", "different excerpt"))
    }
    @Test fun meaningfulQueryIdsAndDistinctEvidenceRemainSeparate() {
        assertNotEquals(AssistantEvidencePolicy.evidenceKey("https://example.org/story?id=1", "text"),
            AssistantEvidencePolicy.evidenceKey("https://example.org/story?id=2", "text"))
        assertTrue(AssistantEvidencePolicy.identity("Gemini", "model", true, true, false).contains("உள்ளூரில்"))
    }
}
