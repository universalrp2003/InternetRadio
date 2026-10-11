package com.universalrp.cleansweep.data

data class PermissionObservation(val granted: List<String>, val notGranted: List<String>, val unavailableChecks: Set<String>)

/** Special access is controlled by AppOps, independently of manifest permission flags. */
object PermissionObservationPolicy {
    val SPECIAL = mapOf(
        "android.permission.REQUEST_INSTALL_PACKAGES" to "install_other_apps",
        "android.permission.PACKAGE_USAGE_STATS" to "usage_access",
        "android.permission.SYSTEM_ALERT_WINDOW" to "overlay",
        "android.permission.MANAGE_EXTERNAL_STORAGE" to "all_files_access",
    )
    private val ORDINARY_CHECKS = setOf("sms_readers", "banking_sms_readers")
    fun observe(declared: List<String>, manifestGranted: Set<String>?, specialStates: Map<String, Boolean?>): PermissionObservation {
        val held = mutableListOf<String>(); val absent = mutableListOf<String>(); val unavailable = mutableSetOf<String>()
        for (permission in declared.distinct()) {
            if (permission in SPECIAL) {
                when (specialStates[permission]) {
                    true -> held += permission
                    false -> absent += permission
                    null -> unavailable += SPECIAL.getValue(permission)
                }
            } else if (manifestGranted == null) unavailable += ORDINARY_CHECKS
            else if (permission in manifestGranted) held += permission else absent += permission
        }
        return PermissionObservation(held, absent, unavailable)
    }
}
