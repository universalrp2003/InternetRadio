package com.universalrp.cleansweep.ai

import org.junit.Assert.*
import org.junit.Test

/** 2.41 AI output budget + provider model check: bounded asks, exact-id evidence only. */
class AiOutputBudgetPolicyTest {
    @Test fun modelListingIsNotGenerationAndKeepsZeroOutput() {
        assertEquals(0, AiOutputBudgetPolicy.effectiveOutputTokens(AiRequestPolicy.Use.MODELS))
        assertEquals(0, AiOutputBudgetPolicy.effectiveOutputTokens(AiRequestPolicy.Use.MODELS, 45_000L))
    }
    @Test fun everyRealAnswerStaysInsideTheHardBounds() {
        assertEquals(32, AiOutputBudgetPolicy.effectiveOutputTokens(0))
        assertEquals(32, AiOutputBudgetPolicy.effectiveOutputTokens(1))
        assertEquals(4_000, AiOutputBudgetPolicy.effectiveOutputTokens(100_000))
        assertEquals(1_200, AiOutputBudgetPolicy.effectiveOutputTokens(AiRequestPolicy.Use.CHAT))
        assertEquals(3_600, AiOutputBudgetPolicy.effectiveOutputTokens(AiRequestPolicy.Use.REPORT))
    }
    @Test fun aNearlySpentDeadlineShrinksTheAnswerInsteadOfGuaranteeingATimeout() {
        assertEquals(3_600, AiOutputBudgetPolicy.effectiveOutputTokens(AiRequestPolicy.Use.REPORT, 90_000L))
        assertEquals(600, AiOutputBudgetPolicy.effectiveOutputTokens(AiRequestPolicy.Use.REPORT, 12_000L))
        assertEquals(240, AiOutputBudgetPolicy.effectiveOutputTokens(AiRequestPolicy.Use.REPORT, 3_000L))
        assertEquals(32, AiOutputBudgetPolicy.effectiveOutputTokens(AiRequestPolicy.Use.TEST, 3_000L))
    }
    @Test fun modelIdsAreComparedAfterOneDocumentedNormalisation() {
        assertEquals("gemini-2.5-flash", AiOutputBudgetPolicy.normalizeModelId(" models/Gemini-2.5-Flash "))
        assertEquals("gpt-oss-120b", AiOutputBudgetPolicy.normalizeModelId("google/gpt-oss-120b"))
        assertEquals(
            AiOutputBudgetPolicy.ModelCheck.ACCEPTED,
            AiOutputBudgetPolicy.checkModel("gemini-2.5-flash", listOf("models/gemini-2.5-flash")),
        )
    }
    @Test fun aModelTheProviderDoesNotListIsRejectedAndNotSubstituted() {
        assertEquals(
            AiOutputBudgetPolicy.ModelCheck.REJECTED_UNLISTED,
            AiOutputBudgetPolicy.checkModel("gemini-1.0-retired", listOf("models/gemini-2.5-flash", "models/gemini-2.5-pro")),
        )
        assertEquals(
            AiOutputBudgetPolicy.ModelCheck.REJECTED_UNLISTED,
            AiOutputBudgetPolicy.checkModel("llama-3.3-70b-versatile", listOf("llama-3.1-8b-instant")),
        )
        assertEquals("The selected model could not be used. Load available models in AI settings.", AiOutputBudgetPolicy.rejectionMessage())
    }
    @Test fun anUnavailableListOrEmptyModelIsNotEvidenceAgainstTheModel() {
        assertEquals(AiOutputBudgetPolicy.ModelCheck.NO_LIST, AiOutputBudgetPolicy.checkModel("anything", emptyList()))
        assertEquals(AiOutputBudgetPolicy.ModelCheck.NO_LIST, AiOutputBudgetPolicy.checkModel("anything", listOf("  ", "models/")))
        assertEquals(AiOutputBudgetPolicy.ModelCheck.NO_MODEL, AiOutputBudgetPolicy.checkModel("   ", listOf("models/gemini-2.5-flash")))
    }
}
