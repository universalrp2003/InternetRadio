package com.universalrp.cleansweep.data

/**
 * 2.42: **one** source of truth for "which language is this UI written in".
 *
 * The bug this replaces: `tr()` / [Lang.t] read the process-wide [Lang.current], while several
 * composables read `state.lang` from the ViewModel, and nothing kept the two equal. On the
 * owner's 2.41 screenshots the header showed English while the "Reviewed feature setup" and
 * "Live Guard readings" cards showed Tamil — and a card that observes no state at all (the
 * update card) never recomposed on a toggle, so its line stayed English in *both* languages.
 * A cold start made it worse: [Lang.current] begins as English and is only corrected when the
 * settings flow emits, so the first frame could be composed in the wrong language.
 *
 * The rules, in the order they are enforced:
 *  1. [Lang.current] is the authority for every string. It is set from the saved choice
 *     **before** the first composition (MainActivity) and again on every toggle
 *     (MainViewModel.setLanguage), so no frame is ever composed against a stale default.
 *  2. `UiState.lang` is a *mirror* whose only remaining job is to make Compose recompose —
 *     AppRoot wraps the screens in `key(state.lang)`, which rebuilds the subtree so every
 *     `tr()` call re-reads the authority. It is written from the authority, never chosen
 *     independently, and no display site reads it any more.
 *  3. A saved choice always beats a process default: a service or worker waking in a fresh
 *     process reads the saved mirror rather than assuming English.
 */
object LanguageSourcePolicy {

    /** The language to use: a saved choice wins, then whatever the process already resolved. */
    fun resolve(savedId: String?, processCurrent: AppLang?): AppLang =
        savedId?.takeIf { it.isNotBlank() }?.let { AppLang.fromId(it) } ?: processCurrent ?: AppLang.TA

    /**
     * True when the saved choice, the authority and the mirror all name the same language —
     * i.e. nothing on screen can render the opposite language. A saved `null` means "no choice
     * written yet", so the authority itself is the expected value.
     */
    fun agrees(savedId: String?, authority: AppLang, mirror: AppLang): Boolean =
        resolve(savedId, authority) == authority && authority == mirror

    /** The single question every display site asks; kept here so there is one spelling of it. */
    fun isTamil(language: AppLang): Boolean = language == AppLang.TA

    /**
     * Whether a toggle must rebuild the whole screen tree. Always true for a real change: a
     * composable that observes no state would otherwise keep text composed in the old language.
     */
    fun requiresRebuild(previous: AppLang, next: AppLang): Boolean = previous != next
}
