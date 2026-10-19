package com.universalrp.cleansweep.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppNetworkTrackerReportTest {
    @Test fun appCountUsesOnlyVisibleEstablishedSocketsNotHistoricalNetworkStats() {
        fun item(pkg: String, source: AppNetworkTracker.EvidenceSource, localPort: Int, ip: String) =
            AppNetworkTracker.ActiveConnection(
                appName = pkg,
                packageName = pkg,
                uid = 10_001,
                localPort = localPort,
                remoteIp = ip,
                remotePort = if (source == AppNetworkTracker.EvidenceSource.LIVE_ESTABLISHED_TCP) 443 else 0,
                destinationHost = null,
                orgOrCompany = "Example",
                isTracker = false,
                category = "Test",
                evidenceSource = source,
            )

        val report = AppNetworkTracker.TrackerReport(
            timestamp = 1_000L,
            connections = listOf(
                item("app.one", AppNetworkTracker.EvidenceSource.LIVE_ESTABLISHED_TCP, 40001, "192.0.2.1"),
                item("app.one", AppNetworkTracker.EvidenceSource.LIVE_ESTABLISHED_TCP, 40002, "192.0.2.2"),
                item("app.two", AppNetworkTracker.EvidenceSource.LIVE_ESTABLISHED_TCP, 40003, "192.0.2.3"),
                item("app.one", AppNetworkTracker.EvidenceSource.NETWORK_STATS_LAST_24_HOURS, 0, "Example"),
                item("app.three", AppNetworkTracker.EvidenceSource.NETWORK_STATS_LAST_24_HOURS, 0, "Example"),
            ),
            trackerCount = 0,
            liveSocketVisibilityAvailable = true,
            networkStatsHistoryAvailable = true,
            mode = "test",
        )

        assertEquals(3, report.visibleEstablishedConnectionCount)
        assertEquals(2, report.visibleEstablishedAppCount)
        assertEquals(2, report.historicalTrafficAppCount)
    }

    @Test fun unknownUidSocketsDoNotInventAnAppNameOrInflateTheAppCount() {
        val connection = AppNetworkTracker.ActiveConnection(
            appName = "App 42", packageName = "uid:42", uid = 42, localPort = 3_000,
            remoteIp = "192.0.2.8", remotePort = 443, destinationHost = null,
            orgOrCompany = "Example", isTracker = false, category = "Test",
            evidenceSource = AppNetworkTracker.EvidenceSource.LIVE_ESTABLISHED_TCP,
        )
        val report = AppNetworkTracker.TrackerReport(
            timestamp = 1_000L, connections = listOf(connection), trackerCount = 0,
            liveSocketVisibilityAvailable = true, networkStatsHistoryAvailable = false, mode = "test",
        )
        assertEquals(1, report.visibleEstablishedConnectionCount)
        assertEquals(0, report.visibleEstablishedAppCount)
    }
}
