package com.universalrp.cleansweep.ai

import com.universalrp.cleansweep.data.*
import org.junit.Assert.*
import org.junit.Test

class SecurityAiPromptTest {
    private val appTargets = (0..74).map {
        FindingApp("PrivateApp$it", "com.private.target$it", "com.private.target$it/.PrivateService")
    }
    private val ids = listOf(
        "accessibility", "notification_listeners", "device_admin", "install_other_apps",
        "usage_access", "overlay", "sideloaded", "sms_readers", "banking_sms_readers",
        "facebook_stubs", "odd_installer", "no_lock", "developer_options", "old_patch",
        "root_tools", "no_hash_lookup",
    )
    private val security = SecurityReport(
        findings = ids.mapIndexed { index, id ->
            Finding(
                id = id,
                title = "Issue $index",
                detail = "Evidence $index for PrivateApp0",
                severity = Severity.entries[index % Severity.entries.size],
                count = appTargets.size,
                samples = appTargets.map { it.label },
                fixHint = "Manual hint $index",
                affectedApps = appTargets,
            )
        },
        score = 45,
        appsChecked = 75,
        scannedAtMs = 123_456L,
    )
    private val storage = StorageInfo(total = 64_000_000_000L, free = 4_000_000_000L)
    private val health = HealthSnapshot(
        battery = BatteryReading(
            percent = 77, temperatureC = 39f, voltageV = 4.1f, currentA = 2f,
            averageCurrentA = 1.9f, powerW = 8.2f, charging = true,
            pluggedLabel = "USB", statusLabel = "Charging", healthLabel = "Good",
            technology = "Li-ion", chargeCounterMah = 3000f, estimatedCapacityMah = 4000f,
            currentSignNote = "Battery-side", powerConnected = true,
        ),
        batteryTempFromKernelC = null, cpuTempC = null, cpuTempZone = null,
        cpuCurrentMhz = 1000L, cpuMaxMhz = 2000L, cpuLoadAvg = null,
        cpuLoadPercent = null, cpuMaxTempC = null,
        device = DeviceDetails(
            manufacturer = "Xiaomi", model = "Example Redmi", device = "example",
            hardware = "example", soc = "Example SoC", androidVersion = "15", sdk = 35,
            securityPatch = "2025-01-01", abi = "arm64-v8a", cores = 8,
            uptimeMs = 10_000L, totalRamBytes = 8_000_000_000L, availableRamBytes = 3_000_000_000L,
        ),
        storage = storage, capturedAtMs = 124_000L,
    )
    private val malware = MalwareReport(
        checked = 65,
        hits = listOf(MalwareHit("com.private.target0", "PrivateApp0", "Example scanner",
            "Known hash for PrivateApp0", "private-hash-not-for-ai")),
        skipped = 10, vtLookups = 1, scannedAtMs = 100_000L,
    )
    private val network = NetworkReport(
        wifi = WifiDetails(
            connected = true, transport = "Wi-Fi", ssid = "PrivateWifiNetwork",
            bssid = null, ip = "192.168.41.9", prefixLength = 24, gateway = "192.168.41.1",
            dns = listOf("192.168.41.1"), linkSpeedMbps = 100, rssiDbm = -50,
            signalPercent = 80, frequencyMhz = 5000, band = "5 GHz", macAddress = null,
            needsPermission = false,
        ),
        devices = emptyList(), scannedAtMs = 80_000L, probedHosts = 10, note = "Last scan",
    )
    private val quality = LiveNetworkQuality.QualitySnapshot(
        isWifi = true, isMobile = false, rxSpeedBytesPerSec = 200L, txSpeedBytesPerSec = 100L,
        formattedRxSpeed = "200 B/s", formattedTxSpeed = "100 B/s", pingMs = 80L,
        jitterMs = 10L, grade = LiveNetworkQuality.QualityGrade.MEDIUM_QUALITY,
        labelTamil = "Example", labelEnglish = "Medium observed quality",
    )
    private val dataUsage = DataUsageTracker.UsageInfo(
        hasUsageAccess = false, todayMobileBytes = 100L, packTotalMobileBytes = 200L,
        packStartDateMs = 50_000L, packLimitGb = 1f, packLimitBytes = 1000L,
        packRemainingBytes = 800L, formattedToday = "123 MB", formattedPackTotal = "456 MB",
        formattedRemaining = "789 MB", formattedPackLimit = "1 GB", progressRatio = 0.2f,
        isUnlimited5g = true,
    )

    private fun prompt(config: AiConfig = AiConfig()): String = SecurityAiPrompt.build(
        config, health, null, security, network, storage, malware,
        networkQuality = quality, dataUsage = dataUsage,
        deviceGuidance = "MIUI / HyperOS menus vary; review before changing.",
    )

    @Test fun includesEveryFindingAndEveryAffectedAppNotOnlyFirstSixOrSixty() {
        val text = prompt(AiConfig(includeAppNames = true, includeNetwork = true))
        ids.forEachIndexed { index, id ->
            assertTrue("Missing finding $id", text.contains("[$id]"))
            assertTrue(text.contains("Evidence $index"))
            assertTrue(text.contains("Manual hint $index"))
        }
        appTargets.forEach { app ->
            assertTrue(text.contains(app.label))
            assertTrue(text.contains(app.packageName))
            assertTrue(text.contains(app.componentName!!))
        }
        assertTrue(text.contains("45/100"))
        assertTrue(text.contains("Example Redmi"))
        assertTrue(text.contains("== Battery =="))
        assertTrue(text.contains("== Storage =="))
        assertTrue(text.contains("not charger adapter rating"))
    }

    @Test fun privacySwitchScrubsAppIdentifiersEvenInsideDescriptions() {
        val text = prompt(AiConfig(includeAppNames = false, includeNetwork = true))
        appTargets.forEach { app ->
            assertFalse(text.contains(app.label))
            assertFalse(text.contains(app.packageName))
            assertFalse(text.contains(app.componentName!!))
        }
        assertTrue(text.contains("names, packages and service components withheld"))
        ids.forEach { assertTrue(text.contains("[$it]")) }
        assertTrue(text.contains("known-hash hits"))
    }

    @Test fun genericReportHonoursTheSameAppNamePrivacySwitch() {
        val text = AiReport.build(AiConfig(includeAppNames = false), health, null, security, null, storage)
        assertFalse(text.contains("PrivateApp0"))
        assertFalse(text.contains("com.private.target"))
        assertTrue(text.contains("[sms_readers]"))
    }

    @Test fun networkOptOutOmitLanAddressesDataAndQualityDespiteAvailableSnapshots() {
        val text = prompt(AiConfig(includeAppNames = true, includeNetwork = false))
        assertFalse(text.contains("PrivateWifiNetwork"))
        assertFalse(text.contains("192.168.41"))
        assertFalse(text.contains("Medium observed quality"))
        assertFalse(text.contains("123 MB"))
        assertFalse(text.contains("456 MB"))
        assertTrue(text.contains("[old_patch]"))
    }

    @Test fun sharedNetworkAndOlderScansAreLabelledWithLimits() {
        val text = prompt(AiConfig(includeAppNames = true, includeNetwork = true))
        assertTrue(text.contains("PrivateWifiNetwork"))
        assertTrue(text.contains("may be older than this request"))
        assertTrue(text.contains("not a full speed test"))
        assertTrue(text.contains("not the carrier's bill"))
        assertTrue(text.contains("user supplied"))
        assertTrue(text.contains("last completed result, not a new scan"))
        assertFalse(text.contains("private-hash-not-for-ai"))
    }

    @Test fun unavailableReadingsAndUnrunMalwareAreUnknownNotClean() {
        val text = SecurityAiPrompt.build(AiConfig(), null, null, null, null, null)
        assertTrue(text.contains("fresh security scan was unavailable"))
        assertTrue(text.contains("Unknown is NOT clean"))
        assertTrue(text.contains("Current battery/hardware readings are unavailable"))
        assertFalse(text.contains("100/100 (Looking good)"))
    }

    @Test fun junkSharesTotalsNotPrivatePathsNotesOrFiles() {
        val category = CategoryResult(JunkKind.LARGE_FILES)
        category.files += JunkFile("/storage/emulated/0/Download/private-secret.txt", 4096L, 1L,
            note = "Private file note")
        val junk = ScanReport(listOf(category), 1L, 1L, 90_000L)
        val text = SecurityAiPrompt.build(AiConfig(), health, null, security, null, storage,
            junk = junk, appCacheBytes = 2048L, hasUsageAccess = true, hasStorageAccess = true)
        assertTrue(text.contains("1 items"))
        assertTrue(text.contains("manual review: true"))
        assertTrue(text.contains("Usage access: true"))
        assertFalse(text.contains("private-secret.txt"))
        assertFalse(text.contains("Private file note"))
        assertFalse(text.contains("/storage/"))
    }

    @Test fun inProgressHashScanIsNotPresentedAsFinal() {
        val text = SecurityAiPrompt.build(AiConfig(), null, null, security, null, null,
            malware = malware, malwareScanInProgress = true)
        assertTrue(text.contains("results are not final"))
    }

    @Test fun systemPromptRequestsAllFindingsAndForbidsFalseSafetyPromises() {
        val text = SecurityAiPrompt.systemPrompt()
        assertTrue(text.contains("EVERY"))
        assertTrue(text.contains("does not prove security"))
        assertTrue(text.contains("untrusted data"))
        assertTrue(text.contains("banking"))
        assertFalse(text.contains("under 250 words"))
    }
}
