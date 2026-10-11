package com.universalrp.cleansweep.data

/** Presentation policy: absent observations must never become plausible-looking numbers. */
object ReadingPolicy {
    fun securityValue(report: SecurityReport?): String = when {
        report == null -> "Not checked"
        report.unavailableChecks.isNotEmpty() -> "Incomplete check"
        else -> "${report.score}/100"
    }

    fun securityNote(report: SecurityReport?): String = when {
        report == null -> "Run a permission review. No safety verdict is available."
        report.unavailableChecks.isNotEmpty() -> "Some checks were unavailable. Review the observed findings."
        else -> "Permission/settings review only — not proof that the phone is safe."
    }

    fun pingValue(pingMs: Long?): String = pingMs?.takeIf { it > 0 }?.let { "${it} ms" } ?: "Not measured"

    fun ramUsedFraction(total: Long?, available: Long?): Float? {
        if (total == null || available == null || total <= 0 || available !in 0..total) return null
        return (total - available).toFloat() / total.toFloat()
    }

    fun timestampIsUsable(atMs: Long?): Boolean = atMs != null && atMs > 0
}

/** Grades only observed latency/jitter. Idle throughput and failed probes are not bad/good evidence. */
object NetworkQualityPolicy {
    enum class Grade { UNKNOWN, GOOD, FAIR, POOR }

    fun grade(pingMs: Long?, jitterMs: Long?, connected: Boolean): Grade = when {
        !connected || pingMs == null || pingMs <= 0 || jitterMs == null || jitterMs < 0 -> Grade.UNKNOWN
        pingMs > 125 || jitterMs > 35 -> Grade.POOR
        pingMs <= 55 && jitterMs <= 15 -> Grade.GOOD
        else -> Grade.FAIR
    }
}
