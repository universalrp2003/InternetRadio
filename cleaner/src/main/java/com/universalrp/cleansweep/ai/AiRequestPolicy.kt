package com.universalrp.cleansweep.ai

/** Whole-request budgets, including fallback; never restart a multi-minute timeout at every endpoint. */
object AiRequestPolicy {
    enum class Use(val budgetMs: Long, val outputTokens: Int) {
        CHAT(45_000, 1_200), REPORT(90_000, 3_600), TEST(15_000, 32), MODELS(15_000, 0)
    }
    const val WEB_BUDGET_MS = 8_000L
    const val MAX_FREE_ENDPOINTS = 2
    const val MAX_RESPONSE_BYTES = 1024 * 1024
    fun remainingMs(startNs: Long, nowNs: Long, budgetMs: Long): Long =
        (budgetMs - ((nowNs - startNs).coerceAtLeast(0) / 1_000_000)).coerceIn(0, budgetMs)
    fun mayFallback(remainingMs: Long): Boolean = remainingMs >= 5_000
    fun automaticRetryCount(): Int = 0 // An explicit Retry/another provider is preferable to hidden minute-long retries.
}

class RequestBudget(val use: AiRequestPolicy.Use, val startedNs: Long = System.nanoTime()) {
    fun remainingMs(): Long = AiRequestPolicy.remainingMs(startedNs, System.nanoTime(), use.budgetMs)
}
