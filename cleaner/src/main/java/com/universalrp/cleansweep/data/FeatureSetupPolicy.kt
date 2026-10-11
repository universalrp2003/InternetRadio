package com.universalrp.cleansweep.data

object FeatureSetupPolicy {
    enum class Status { READY, NEEDS_ACCESS, NOT_CONFIGURED, OFF_BY_CHOICE }
    data class Feature(val id: String, val title: String, val status: Status, val explanation: String)
    fun status(enabled: Boolean, configured: Boolean = true, access: Boolean = true): Status = when {
        !enabled -> Status.OFF_BY_CHOICE
        !configured -> Status.NOT_CONFIGURED
        !access -> Status.NEEDS_ACCESS
        else -> Status.READY
    }
    fun pending(features: List<Feature>): List<Feature> = features.filter { it.status == Status.NEEDS_ACCESS || it.status == Status.NOT_CONFIGURED }
    fun remind(enabled: Boolean, permission: Boolean, pending: Int, lastAt: Long, now: Long): Boolean =
        enabled && permission && pending > 0 && now > 0 && (lastAt == 0L || (now >= lastAt && now - lastAt >= 86_400_000L))
}
