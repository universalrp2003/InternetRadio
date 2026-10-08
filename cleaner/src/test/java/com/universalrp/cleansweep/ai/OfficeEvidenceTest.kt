package com.universalrp.cleansweep.ai

import org.junit.Assert.*
import org.junit.Test

class OfficeEvidenceTest {
    private val question = "who is the current chief minister of Tamil Nadu"
    @Test fun realLeadWithPartyMustReturnPerson() {
        val lead = "There have been four instances of President's rule in Tamil Nadu, most recently in 1991. C. Joseph Vijay of the Tamilaga Vettri Kazhagam is the incumbent Chief Minister since 10 May 2026."
        assertEquals("C. Joseph Vijay", OfficeEvidence.incumbent(lead))
        assertFalse(OfficeEvidence.answer(question, "a guess", lead).contains("identifies Tamilaga"))
    }
    @Test fun missingEvidenceMustNotRepeatModelGuess() {
        val result = OfficeEvidence.answer(question, "M. K. Stalin is current", "The chief minister heads the government.")
        assertTrue(result.contains("could not verify"))
        assertFalse(result.contains("Stalin"))
    }
    @Test fun initialsAreNotSentenceEnds() {
        assertEquals("C. Joseph Vijay", OfficeEvidence.incumbent("The incumbent is C. Joseph Vijay, since 10 May 2026."))
        assertEquals("M. K. Stalin", OfficeEvidence.incumbent("The incumbent is M. K. Stalin."))
    }
    @Test fun correctNameMentionDoesNotAllowContradictoryProse() {
        val result = OfficeEvidence.answer(question, "Vijay is not current; Stalin is.", "The incumbent is C. Joseph Vijay.")
        assertTrue(result.contains("C. Joseph Vijay"))
        assertFalse(result.contains("Stalin"))
    }
    @Test fun voiceTypoStillRequiresEvidence() {
        assertTrue(OfficeEvidence.applies("who is cm if tamilnadu"))
        assertTrue(OfficeEvidence.answer("who is cm if tamilnadu", "a guess", "").contains("could not verify"))
    }
    @Test fun alternativeLeadWording() {
        assertEquals("Jane Smith", OfficeEvidence.incumbent("Jane Smith is the current mayor."))
        assertEquals("Jane Smith", OfficeEvidence.incumbent("The office is currently held by Jane Smith."))
    }
    @Test fun unrelatedAnswerIsUnchanged() {
        assertEquals("Four", OfficeEvidence.answer("what is two plus two", "Four", ""))
    }
    @Test fun formerHolderDoesNotCount() {
        assertNull(OfficeEvidence.incumbent("M. K. Stalin served from 2021 to 2026."))
    }
}
