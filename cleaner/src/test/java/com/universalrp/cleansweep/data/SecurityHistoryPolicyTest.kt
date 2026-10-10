package com.universalrp.cleansweep.data

import org.junit.Assert.*
import org.junit.Test

class SecurityHistoryPolicyTest {
    private fun baseline(snapshot: SecuritySnapshot) = SecurityHistoryPolicy.observe(SecurityHistoryState(), snapshot)
    @Test fun firstExistingFindingsEstablishABaselineNotNewAccess() {
        val state = baseline(insightSnapshot(findings = listOf(insightFinding()), packages = listOf(ObservedPackage("org.example.reader", "Reader"))))
        assertEquals(listOf(SecurityChangeKind.BASELINE), state.events.map { it.kind })
        assertEquals(1, state.latest!!.observations.size)
    }
    @Test fun newConfirmedPermissionUsesStablePackageAndComponentIdentity() {
        val state = SecurityHistoryPolicy.observe(baseline(insightSnapshot()), insightSnapshot(2000, listOf(insightFinding(component = "org.example.reader/ReaderService"))))
        val event = state.events.last(); assertEquals(SecurityChangeKind.NEW_FINDING, event.kind)
        assertTrue(event.key.contains("org.example.reader/ReaderService")); assertTrue(event.detail.contains("not the exact grant time"))
    }
    @Test fun disappearanceRequiresAFreshAvailableCheckAndIsNotSafetyProof() {
        val state = SecurityHistoryPolicy.observe(baseline(insightSnapshot(findings = listOf(insightFinding()))), insightSnapshot(2000))
        assertEquals(SecurityChangeKind.RESOLVED, state.events.last().kind)
        assertTrue(state.events.last().detail.contains("not a malware-free"))
    }
    @Test fun failedCategoryRetainsTheOldObservationAndReview() {
        val initial = baseline(insightSnapshot(findings = listOf(insightFinding())))
        val reviewed = SecurityHistoryPolicy.review(initial, initial.latest!!.observations.single(), true, 1100)
        val state = SecurityHistoryPolicy.observe(reviewed, insightSnapshot(2000, unavailable = setOf("accessibility")))
        assertEquals(initial.latest!!.observations, state.latest!!.observations)
        assertEquals(reviewed.reviewed, state.reviewed); assertFalse(state.events.any { it.kind == SecurityChangeKind.RESOLVED })
    }
    @Test fun firstSuccessfulReadOfAnInitiallyUnavailableCategoryIsItsBaseline() {
        val initial = baseline(insightSnapshot(unavailable = setOf("accessibility")))
        val state = SecurityHistoryPolicy.observe(initial, insightSnapshot(2000, listOf(insightFinding())))
        assertFalse(state.events.any { it.kind == SecurityChangeKind.NEW_FINDING })
        assertTrue("accessibility" in state.baselinedChecks)
    }
    @Test fun recoveryComparesAgainstTheLastValidBaselineNotTheFailedRead() {
        val initial = baseline(insightSnapshot())
        val failed = SecurityHistoryPolicy.observe(initial, insightSnapshot(2000, unavailable = setOf("accessibility", "installed_apps")))
        val recovered = SecurityHistoryPolicy.observe(failed, insightSnapshot(3000, listOf(insightFinding()), listOf(ObservedPackage("org.example.reader", "Reader"))))
        assertTrue(recovered.events.any { it.kind == SecurityChangeKind.NEW_FINDING })
        assertTrue(recovered.events.any { it.kind == SecurityChangeKind.NEW_APP })
    }
    @Test fun identicalLabelsDoNotMergeDifferentAppsOrServices() {
        val items = listOf(insightFinding(pkg = "org.first", label = "Same", component = "org.first/One"), insightFinding(pkg = "org.second", label = "Same", component = "org.second/Two"))
        assertEquals(2, insightSnapshot(findings = items).observations.size)
        assertEquals(2, insightSnapshot(findings = items).observations.map { it.key }.toSet().size)
    }
    @Test fun appUpdateInvalidatesExpectedAcknowledgement() {
        val findings = listOf(insightFinding())
        val initial = baseline(insightSnapshot(findings = findings, packages = listOf(ObservedPackage("org.example.reader", "Reader", updatedAtMs = 10))))
        val reviewed = SecurityHistoryPolicy.review(initial, initial.latest!!.observations.single(), true, 1100)
        val state = SecurityHistoryPolicy.observe(reviewed, insightSnapshot(2000, findings, listOf(ObservedPackage("org.example.reader", "Reader", updatedAtMs = 20))))
        assertTrue(state.reviewed.isEmpty()); assertEquals(SecurityChangeKind.CHANGED, state.events.last().kind)
    }
    @Test fun reviewingDoesNotRevokeResolveOrRemoveAnObservation() {
        val initial = baseline(insightSnapshot(findings = listOf(insightFinding())))
        val item = initial.latest!!.observations.single()
        val state = SecurityHistoryPolicy.review(initial, item, true, 1200)
        assertTrue(SecurityHistoryPolicy.isReviewed(state, item)); assertEquals(initial.latest, state.latest)
        assertEquals(SecurityChangeKind.REVIEWED, state.events.last().kind)
        assertTrue(state.events.last().detail.contains("Access was not revoked"))
    }
    @Test fun appVisibilityChangesAreNotClaimedToBeRecentInstallOrProvenUninstall() {
        val initial = baseline(insightSnapshot(packages = listOf(ObservedPackage("org.old", "Old"))))
        val state = SecurityHistoryPolicy.observe(initial, insightSnapshot(2000, packages = listOf(ObservedPackage("org.new", "New"))))
        assertTrue(state.events.any { it.kind == SecurityChangeKind.NEW_APP && it.detail.contains("not necessarily newly installed") })
        assertTrue(state.events.any { it.kind == SecurityChangeKind.APP_NO_LONGER_LISTED && it.detail.contains("become unavailable") })
    }
    @Test fun outOfScopeSystemCheckCannotResolveAnAllAppFinding() {
        val initial = baseline(insightSnapshot(findings = listOf(insightFinding(id = "root_tools"))))
        val state = SecurityHistoryPolicy.observe(initial, insightSnapshot(2000, unavailable = setOf("root_tools", "facebook_stubs")))
        assertEquals(initial.latest!!.observations, state.latest!!.observations)
        assertFalse(state.events.any { it.kind == SecurityChangeKind.RESOLVED })
    }
    @Test fun staleResultsAreIgnoredAndEventsAreBounded() {
        var state = baseline(insightSnapshot(1, listOf(insightFinding(detail = "version 1"))))
        for (i in 2..260) state = SecurityHistoryPolicy.observe(state, insightSnapshot(i.toLong(), listOf(insightFinding(detail = "version $i"))))
        assertEquals(SecurityHistoryPolicy.MAX_EVENTS, state.events.size)
        assertEquals(state, SecurityHistoryPolicy.observe(state, insightSnapshot(50)))
    }
}
