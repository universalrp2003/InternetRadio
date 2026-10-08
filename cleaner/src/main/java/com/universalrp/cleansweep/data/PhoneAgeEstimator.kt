package com.universalrp.cleansweep.data

import android.os.Build
import java.util.Calendar

/**
 * Estimates the age of the phone based on OS release, security patch level,
 * build timestamp, and API level.
 */
object PhoneAgeEstimator {

    data class PhoneAge(
        val years: Int,
        val months: Int,
        val releaseYear: Int,
        val isEstimated: Boolean = true,
    ) {
        fun displayString(isTamil: Boolean): String {
            return if (isTamil) {
                when {
                    years > 0 && months > 0 -> "சுமார் $years வருடம் $months மாதம் ($releaseYear)"
                    years > 0 -> "சுமார் $years வருடம் ($releaseYear)"
                    months > 0 -> "சுமார் $months மாதம் ($releaseYear)"
                    else -> "புதிய சாதனம் ($releaseYear)"
                }
            } else {
                when {
                    years > 0 && months > 0 -> "About $years yr $months mo ($releaseYear)"
                    years > 0 -> "About $years yr ($releaseYear)"
                    months > 0 -> "About $months mo ($releaseYear)"
                    else -> "New device ($releaseYear)"
                }
            }
        }
    }

    /**
     * Estimates device age and approximate release / manufacturing year.
     */
    fun estimateAge(): PhoneAge {
        val now = Calendar.getInstance()
        val currentYear = now.get(Calendar.YEAR)
        val currentMonth = now.get(Calendar.MONTH) + 1

        var deviceYear = 0
        var deviceMonth = 6

        // 1. Build TIME (hardware firmware build timestamp)
        val buildTime = Build.TIME
        if (buildTime > 1420070400000L) { // Post 2015
            val cal = Calendar.getInstance().apply { timeInMillis = buildTime }
            val y = cal.get(Calendar.YEAR)
            if (y in 2015..currentYear) {
                deviceYear = y
                deviceMonth = cal.get(Calendar.MONTH) + 1
            }
        }

        // 2. Security patch date (often close to firmware release / OS update)
        if (deviceYear == 0) {
            val patch = try { Build.VERSION.SECURITY_PATCH } catch (e: Exception) { null }
            if (!patch.isNullOrBlank()) {
                val parts = patch.split("-")
                if (parts.size >= 2) {
                    val y = parts[0].toIntOrNull()
                    val m = parts[1].toIntOrNull()
                    if (y != null && y in 2015..currentYear) {
                        deviceYear = y
                        deviceMonth = m ?: 6
                    }
                }
            }
        }

        // 3. Fallback based on Android SDK level introduction year
        if (deviceYear == 0) {
            val sdk = Build.VERSION.SDK_INT
            deviceYear = when {
                sdk >= 35 -> 2024 // Android 15
                sdk == 34 -> 2023 // Android 14
                sdk == 33 -> 2022 // Android 13
                sdk == 31 || sdk == 32 -> 2021 // Android 12 / 12L
                sdk == 30 -> 2020 // Android 11
                sdk == 29 -> 2019 // Android 10
                sdk == 28 -> 2018 // Android 9 (Pie)
                sdk == 26 || sdk == 27 -> 2017 // Android 8.0 / 8.1 (Oreo)
                sdk == 24 || sdk == 25 -> 2016 // Android 7.0 / 7.1
                else -> 2015
            }
            deviceMonth = 8
        }

        val totalMonths = ((currentYear - deviceYear) * 12 + (currentMonth - deviceMonth)).coerceAtLeast(0)
        val years = totalMonths / 12
        val months = totalMonths % 12

        return PhoneAge(
            years = years,
            months = months,
            releaseYear = deviceYear,
        )
    }
}
