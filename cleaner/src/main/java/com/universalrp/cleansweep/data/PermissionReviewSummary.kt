package com.universalrp.cleansweep.data

/** One aggregate last review, not a history or current safety verdict. No package/finding/app identity. */
data class PermissionReviewSummary(val atMs: Long, val appsChecked: Int, val high: Int, val medium: Int, val incomplete: Boolean) {
    fun valid(): Boolean = atMs > 0 && appsChecked in 0..100_000 && high in 0..100_000 && medium in 0..100_000
    fun isFuture(nowMs: Long): Boolean = atMs > nowMs
    companion object {
        fun from(report: SecurityReport): PermissionReviewSummary = PermissionReviewSummary(report.scannedAtMs,
            report.appsChecked, report.highCount, report.mediumCount, report.unavailableChecks.isNotEmpty() || report.appsChecked == 0)
    }
}
