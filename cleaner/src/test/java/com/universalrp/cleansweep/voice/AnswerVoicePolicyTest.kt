package com.universalrp.cleansweep.voice

import org.junit.Assert.*
import org.junit.Test

class AnswerVoicePolicyTest {
    @Test fun twoVoicesAlternateBetweenAnswers() { assertEquals(listOf(0, 1, 0, 1), (0L..3L).map { AnswerVoicePolicy.slot(it, AnswerVoicePolicy.Mode.ALTERNATE) }) }
    @Test fun aSingleAnswerHasOnlyOneAssignedSlot() { val index = 8L; repeat(5) { assertEquals(0, AnswerVoicePolicy.slot(index, AnswerVoicePolicy.Mode.ALTERNATE)) } }
    @Test fun confirmedChoiceIsDefault() { assertEquals(AnswerVoicePolicy.Mode.ALTERNATE, AnswerVoicePolicy.Mode.parse("alternate_answers")); assertEquals(AnswerVoicePolicy.Mode.ALTERNATE, AnswerVoicePolicy.Mode.parse(null)) }
    @Test fun invalidIndexCannotBecomeInvalidVoice() { assertEquals(0, AnswerVoicePolicy.slot(-10, AnswerVoicePolicy.Mode.ALTERNATE)) }
    @Test fun answersNeverRetryIntoAnotherVoice() { assertFalse(AnswerVoicePolicy.retryInAnotherVoice(false, true)); assertFalse(AnswerVoicePolicy.retryInAnotherVoice(true, true)) }
    @Test fun startedAlertCannotBeReplayedEither() { assertFalse(AnswerVoicePolicy.retryInAnotherVoice(true, false)); assertTrue(AnswerVoicePolicy.retryInAnotherVoice(false, false)) }
    @Test fun preferencesAreValidated() { assertNull(AnswerVoicePolicy.validPreference(" ")); assertNull(AnswerVoicePolicy.validPreference("a".repeat(201))); assertEquals("ta-in-voice-a", AnswerVoicePolicy.validPreference(" ta-in-voice-a ")) }
    @Test fun descriptorContainsCapabilitiesNotGuessedGender() { val voice = InstalledAnswerVoice("engine-provided-id", "ta-IN", false); assertEquals("ta-IN", voice.language); assertFalse(voice.toString().contains("female")); assertFalse(voice.toString().contains("male")) }

    // ------------------------------------------- 2.42 automatic Voice A / Voice B selection

    @Test fun theFirstTwoUsableInstalledVoicesAreChosenAutomatically() {
        assertEquals("a" to "b", AnswerVoicePolicy.automaticPair(listOf("a", "b", "c")))
        // The engine's own order is respected: offline voices arrive first, then by name.
        assertEquals("offline-1" to "offline-2",
            AnswerVoicePolicy.automaticPair(listOf("offline-1", "offline-2", "online-1")))
        // One usable voice serves both slots instead of leaving the answer unspoken.
        assertEquals("only" to "only", AnswerVoicePolicy.automaticPair(listOf("only")))
        assertEquals(null to null, AnswerVoicePolicy.automaticPair(emptyList()))
    }

    @Test fun unusableAndDuplicateVoiceNamesCannotBecomeAChoice() {
        assertEquals("b" to "c", AnswerVoicePolicy.automaticPair(listOf("  ", "a".repeat(201), "b", "b", "c")))
        assertEquals(null to null, AnswerVoicePolicy.automaticPair(listOf("", " ", "a".repeat(201))))
        // Nothing is invented: a choice never contains a name the inventory did not report.
        val pair = AnswerVoicePolicy.automaticPair(listOf("real-voice"))
        assertTrue(pair.first == "real-voice" && pair.second == "real-voice")
    }

    @Test fun aStoredChoiceWinsAndASavedVoiceTheEngineDroppedIsReplaced() {
        assertEquals("a" to "b", AnswerVoicePolicy.effectivePair("a", "b", listOf("a", "b", "c")))
        assertEquals(null to null, AnswerVoicePolicy.effectivePair(null, null, emptyList()))
        // "gone" is no longer reported by the engine, so the other slot is filled from the
        // inventory — preferring a different voice so the two slots can still alternate.
        assertEquals("a" to "b", AnswerVoicePolicy.effectivePair("gone", "b", listOf("a", "b")))
        assertEquals("a" to "b", AnswerVoicePolicy.effectivePair("a", "gone", listOf("a", "b")))
        // Only one voice installed: the surviving choice is kept and the other slot reuses it.
        assertEquals("a" to "a", AnswerVoicePolicy.effectivePair("a", null, listOf("a")))
    }

    @Test fun anEngineThatReportsNothingKeepsTheSavedChoiceInsteadOfErasingIt() {
        // An empty inventory is an uninitialised engine, not evidence against the saved voices.
        assertEquals("a" to "b", AnswerVoicePolicy.effectivePair("a", "b", emptyList()))
        assertEquals("ta-voice" to "ta-voice", AnswerVoicePolicy.effectivePair("ta-voice", "ta-voice", emptyList()))
        // Automatic selection never inspects a name for gender, only for validity.
        assertEquals("female-sounding" to "male-sounding",
            AnswerVoicePolicy.automaticPair(listOf("female-sounding", "male-sounding")))
    }
}
