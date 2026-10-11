package com.universalrp.cleansweep.data

import org.junit.Assert.*
import org.junit.Test

class PermissionReviewSummaryTest {
    private fun report(time: Long = 1234, missing: Set<String> = emptySet(), apps: Int = 20) = SecurityReport(emptyList(), 100, apps, time, missing)
    @Test fun retainsActualScanTimestamp() { assertEquals(1234L, PermissionReviewSummary.from(report()).atMs) }
    @Test fun completeReviewHasNoIncompleteFlag() { assertFalse(PermissionReviewSummary.from(report()).incomplete) }
    @Test fun unavailableChecksRemainIncomplete() { assertTrue(PermissionReviewSummary.from(report(missing = setOf("accessibility"))).incomplete) }
    @Test fun emptyInventoryCannotBeCompleteReview() { assertTrue(PermissionReviewSummary.from(report(apps = 0)).incomplete) }
    @Test fun absentTimestampIsNotInvented() { assertFalse(PermissionReviewSummary.from(report(time = 0)).valid()) }
    @Test fun corruptCountsAreRejected() { assertFalse(PermissionReviewSummary(100, -1, 0, 0, true).valid()); assertFalse(PermissionReviewSummary(100, 100_001, 0, 0, true).valid()) }
    @Test fun clockRollbackDoesNotRewriteSavedDate() { val saved = PermissionReviewSummary.from(report()); assertTrue(saved.isFuture(1000)); assertEquals(1234L, saved.atMs) }
    @Test fun aggregateContainsNoAppOrFindingIdentities() {
        val original = report().copy(findings = listOf(Finding("secret", "Private app", "com.private.example", Severity.HIGH)))
        val saved = PermissionReviewSummary.from(original); assertEquals(1, saved.high); assertFalse(saved.toString().contains("com.private")); assertFalse(saved.toString().contains("Private app"))
    }
}
