package com.universalrp.cleansweep.data

import org.junit.Assert.*
import org.junit.Test

/**
 * 2.42: one language source of truth. These are pure policy checks — they cannot prove what a
 * phone renders, which still needs the owner's device at both font scales.
 */
class LanguageSourcePolicyTest {

    @Test fun aSavedChoiceBeatsTheProcessDefault() {
        assertEquals(AppLang.TA, LanguageSourcePolicy.resolve("ta", AppLang.EN))
        assertEquals(AppLang.EN, LanguageSourcePolicy.resolve("en", AppLang.TA))
        // An unknown/garbage id resolves to the Tamil first-run default, never a half-translated screen.
        assertEquals(AppLang.TA, LanguageSourcePolicy.resolve("fr", AppLang.TA))
        assertEquals(AppLang.EN, LanguageSourcePolicy.resolve("", AppLang.EN))
        assertEquals(AppLang.EN, LanguageSourcePolicy.resolve("   ", AppLang.EN))
    }

    @Test fun noSavedChoiceKeepsWhatTheProcessAlreadyResolved() {
        assertEquals(AppLang.TA, LanguageSourcePolicy.resolve(null, AppLang.TA))
        assertEquals(AppLang.EN, LanguageSourcePolicy.resolve(null, AppLang.EN))
        // A fresh install with nothing saved defaults to Tamil, not blank English controls.
        assertEquals(AppLang.TA, LanguageSourcePolicy.resolve(null, null))
    }

    @Test fun theMirrorAndTheAuthorityMustAgreeBeforeAnythingIsComposed() {
        assertTrue(LanguageSourcePolicy.agrees("ta", AppLang.TA, AppLang.TA))
        assertTrue(LanguageSourcePolicy.agrees(null, AppLang.EN, AppLang.EN))
        // The exact 2.41 defect: header (mirror) English while the cards (authority) were Tamil.
        assertFalse(LanguageSourcePolicy.agrees("ta", AppLang.TA, AppLang.EN))
        assertFalse(LanguageSourcePolicy.agrees("en", AppLang.EN, AppLang.TA))
        // A saved choice the process has not applied yet is a disagreement, not a valid state.
        assertFalse(LanguageSourcePolicy.agrees("ta", AppLang.EN, AppLang.EN))
    }

    @Test fun aLanguageChangeAlwaysRebuildsTheScreenTree() {
        assertTrue(LanguageSourcePolicy.requiresRebuild(AppLang.EN, AppLang.TA))
        assertTrue(LanguageSourcePolicy.requiresRebuild(AppLang.TA, AppLang.EN))
        // Re-selecting the same language must not throw away the screen's own state.
        assertFalse(LanguageSourcePolicy.requiresRebuild(AppLang.TA, AppLang.TA))
        assertFalse(LanguageSourcePolicy.requiresRebuild(AppLang.EN, AppLang.EN))
    }

    /**
     * The owner reported "Verified in-app download; Android confirms installation." as a
     * *missing* Tamil pair. It is not missing — it has been in the catalog since the update
     * card shipped. That line stayed English in the Tamil UI because the card observes no
     * state, so Compose skipped it on a toggle and it kept text composed against the old
     * authority. Asserting the pair exists keeps the real diagnosis on the record and stops a
     * duplicate key (which would fail the localization gate) from being "added" again.
     */
    @Test fun theCardsReportedAsEnglishAlreadyHaveTamilPairs() {
        val keys = Lang.translatedKeys()
        for (key in listOf(
            "Verified in-app download; Android confirms installation.",
            "App updates",
            "Reviewed feature setup",
            "Live Guard readings",
            "Estimated phone age",
        )) {
            assertTrue("missing catalog entry: $key", keys.contains(key))
            val tamil = Lang.translate(AppLang.TA, key)
            assertNotEquals("untranslated in Tamil: $key", key, tamil)
            assertTrue("no Tamil script in: $key", tamil.any { it.code in 0x0B80..0x0BFF })
        }
    }

    @Test fun isTamilIsTheOnlySpellingOfTheQuestion() {
        assertTrue(LanguageSourcePolicy.isTamil(AppLang.TA))
        assertFalse(LanguageSourcePolicy.isTamil(AppLang.EN))
        // And it agrees with the authority the whole app reads through tr().
        Lang.set(AppLang.TA)
        try {
            assertEquals(LanguageSourcePolicy.isTamil(Lang.current), Lang.isTamil)
            assertTrue(Lang.isTamil)
        } finally {
            Lang.set(AppLang.EN)
        }
        assertEquals(LanguageSourcePolicy.isTamil(Lang.current), Lang.isTamil)
    }
}
