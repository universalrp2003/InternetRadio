package com.universalrp.cleansweep.widget

object CompactWidgetPolicy {
    enum class Profile { BATTERY, DATA, PHONE;
        fun next(): Profile = entries[(ordinal + 1) % entries.size]
    }
    fun defaultFor(component: String): Profile = when (component.substringAfterLast('.')) {
        "CleanSweepDataWidget" -> Profile.DATA
        "CleanSweepPhoneWidget" -> Profile.PHONE
        else -> Profile.BATTERY
    }
    fun parse(value: String?, default: Profile = Profile.BATTERY): Profile = Profile.entries.firstOrNull { it.name == value } ?: default
    fun meteredData(wifi: Boolean, userSetUnlimited5g: Boolean): Boolean = !wifi && !userSetUnlimited5g
    fun wholePercent(value: Int?): String = value?.takeIf { it in 0..100 }?.let { "$it%" } ?: "— %"
}
