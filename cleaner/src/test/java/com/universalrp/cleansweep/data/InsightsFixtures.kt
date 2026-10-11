package com.universalrp.cleansweep.data

fun insightReport(at: Long = 1_000, findings: List<Finding> = emptyList(), unavailable: Set<String> = emptySet()) =
    SecurityReport(findings, 100, 2, at, unavailable)

fun insightFinding(id: String = "accessibility", pkg: String = "org.example.reader", label: String = "Same name", severity: Severity = Severity.HIGH, detail: String = "Access is enabled", component: String? = null) =
    Finding(id, "Review $id", detail, severity, count = 1, affectedApps = listOf(FindingApp(label, pkg, component)))

fun insightSnapshot(at: Long = 1_000, findings: List<Finding> = emptyList(), packages: List<ObservedPackage> = emptyList(), unavailable: Set<String> = emptySet()) =
    SecurityHistoryPolicy.snapshot(insightReport(at, findings, unavailable), packages)

fun insightHealth(batteryTemp: Float? = null, cpuTemp: Float? = null): HealthSnapshot = HealthSnapshot(
    battery = BatteryReading(50, batteryTemp, 4f, 2f, null, 8f, true, "USB", "Charging", "Good", "Li-ion", null, null, "", true),
    batteryTempFromKernelC = null, cpuTempC = cpuTemp, cpuTempZone = null, cpuCurrentMhz = null, cpuMaxMhz = null,
    cpuLoadAvg = null, cpuLoadPercent = null, cpuMaxTempC = null,
    device = DeviceDetails("test", "phone", "test", "test", "test", "8", 26, "2026-10-01", "arm", 4, 1000, 1024, 512),
    storage = StorageInfo(10_000_000_000, 5_000_000_000), capturedAtMs = 1000,
)
