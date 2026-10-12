package com.universalrp.cleansweep.ai

/**
 * The 2.41 AI budgets.
 *
 * **Output budget** — every generation ask carries a bounded output-token count. The model
 * list keeps a zero budget (it is not generation), a real answer is never smaller than
 * [MIN_ANSWER_TOKENS], and no ask may exceed [MAX_ANSWER_TOKENS]: an endpoint handed "as
 * long as you like" is the one that turns a single question into a minute-long wait. A
 * request whose own deadline is nearly spent gets a smaller answer budget instead of a
 * guaranteed timeout.
 *
 * **Provider model check** — before a keyed provider is asked to generate, the model name
 * is compared with that provider's own /models list. A model the provider does not list
 * fails fast with the existing translatable "selected model could not be used" message
 * instead of a doomed request. A provider that exposes no list (the keyless Free
 * endpoints, or a list that could not be fetched) is never blocked by this check — an
 * unavailable list is not evidence that a model is wrong.
 */
object AiOutputBudgetPolicy {

    const val MIN_ANSWER_TOKENS = 32
    const val MAX_ANSWER_TOKENS = 4_000

    private const val SHORT_REMAINING_MS = 15_000L
    private const val VERY_SHORT_REMAINING_MS = 8_000L
    private const val SHORT_ANSWER_TOKENS = 600
    private const val VERY_SHORT_ANSWER_TOKENS = 240

    /** Model listing is not generation: it keeps a zero output budget. */
    fun effectiveOutputTokens(use: AiRequestPolicy.Use): Int =
        if (use == AiRequestPolicy.Use.MODELS) 0 else effectiveOutputTokens(use.outputTokens)

    /** Any real answer stays inside [MIN_ANSWER_TOKENS]..[MAX_ANSWER_TOKENS]. */
    fun effectiveOutputTokens(requested: Int): Int = requested.coerceIn(MIN_ANSWER_TOKENS, MAX_ANSWER_TOKENS)

    /** Shrinks the answer budget when the request's own deadline is nearly over. */
    fun effectiveOutputTokens(use: AiRequestPolicy.Use, remainingMs: Long): Int {
        if (use == AiRequestPolicy.Use.MODELS) return 0
        val full = effectiveOutputTokens(use)
        return when {
            remainingMs <= VERY_SHORT_REMAINING_MS -> minOf(full, VERY_SHORT_ANSWER_TOKENS)
            remainingMs <= SHORT_REMAINING_MS -> minOf(full, SHORT_ANSWER_TOKENS)
            else -> full
        }
    }

    enum class ModelCheck { ACCEPTED, REJECTED_UNLISTED, NO_LIST, NO_MODEL }

    /** Google answers `models/gemini-2.5-flash` where the user typed the bare id. */
    fun normalizeModelId(raw: String): String =
        raw.trim().removePrefix("models/").removePrefix("google/").lowercase()

    /**
     * The only accepted evidence is an exact id in the provider's own list (after that
     * one normalisation step). No fuzzy "closest model" matching: a substituted model
     * would answer as something the user never chose.
     */
    fun checkModel(configured: String, listed: List<String>): ModelCheck {
        val wanted = normalizeModelId(configured)
        if (wanted.isBlank()) return ModelCheck.NO_MODEL
        val ids = listed.map { normalizeModelId(it) }.filter { it.isNotBlank() }
        if (ids.isEmpty()) return ModelCheck.NO_LIST
        return if (ids.any { it == wanted }) ModelCheck.ACCEPTED else ModelCheck.REJECTED_UNLISTED
    }

    /**
     * The one honest failure for a model the provider itself did not list. This key already
     * exists in the translatable failure catalog, so the message stays bilingual.
     */
    fun rejectionMessage(): String = "The selected model could not be used. Load available models in AI settings."
}
