package com.universalrp.cleansweep.ai

import org.junit.Assert.*
import org.junit.Test

class AiRequestPolicyTest {
    @Test fun chatHasOneFortyFiveSecondBudget() { assertEquals(45_000L, AiRequestPolicy.Use.CHAT.budgetMs) }
    @Test fun checksAreShorterThanChat() { assertEquals(15_000L, AiRequestPolicy.Use.TEST.budgetMs); assertEquals(15_000L, AiRequestPolicy.Use.MODELS.budgetMs) }
    @Test fun webFitsInsideChat() { assertEquals(8_000L, AiRequestPolicy.WEB_BUDGET_MS); assertTrue(AiRequestPolicy.WEB_BUDGET_MS < AiRequestPolicy.Use.CHAT.budgetMs) }
    @Test fun timeSpentDoesNotRestartForFallback() { assertEquals(12_000L, AiRequestPolicy.remainingMs(1_000_000, 33_001_000_000, 45_000)) }
    @Test fun expiredBudgetIsZero() { assertEquals(0L, AiRequestPolicy.remainingMs(0, 50_000_000_000, 45_000)) }
    @Test fun backwardsClockCannotIncreaseBudget() { assertEquals(45_000L, AiRequestPolicy.remainingMs(100, 0, 45_000)) }
    @Test fun freeAttemptsAndRetriesAreCapped() { assertEquals(2, AiRequestPolicy.MAX_FREE_ENDPOINTS); assertEquals(0, AiRequestPolicy.automaticRetryCount()); assertFalse(AiRequestPolicy.mayFallback(4_999)); assertTrue(AiRequestPolicy.mayFallback(5_000)) }
    @Test fun tokenAndResponseBudgetsAreBounded() { assertEquals(1_200, AiRequestPolicy.Use.CHAT.outputTokens); assertEquals(32, AiRequestPolicy.Use.TEST.outputTokens); assertEquals(1024 * 1024, AiRequestPolicy.MAX_RESPONSE_BYTES) }
}
