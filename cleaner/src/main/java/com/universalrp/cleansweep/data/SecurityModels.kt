package com.universalrp.cleansweep.data

enum class Severity { HIGH, MEDIUM, LOW, INFO }

/** Display names are not identifiers: keep the exact package and, for services, component. */
data class FindingApp(
    val label: String,
    val packageName: String,
    val componentName: String? = null,
)

data class Finding(
    val id: String,
    val title: String,
    val detail: String,
    val severity: Severity,
    val count: Int = 0,
    val samples: List<String> = emptyList(),
    val fixHint: String? = null,
    val affectedApps: List<FindingApp> = emptyList(),
)

data class SecurityReport(
    val findings: List<Finding>,
    val score: Int,
    val appsChecked: Int,
    val scannedAtMs: Long,
    /** Failed or out-of-scope reads cannot support a safety/resolution claim. */
    val unavailableChecks: Set<String> = emptySet(),
) {
    val highCount: Int get() = findings.count { it.severity == Severity.HIGH }
    val mediumCount: Int get() = findings.count { it.severity == Severity.MEDIUM }
    val verdict: String
        get() = when {
            unavailableChecks.isNotEmpty() -> "Incomplete permission review"
            score >= 90 -> "Few observed permission warnings"
            score >= 75 -> "A few things to check"
            score >= 55 -> "Needs attention"
            else -> "Act on the red items"
        }
}
