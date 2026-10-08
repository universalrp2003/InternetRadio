package com.universalrp.cleansweep.data

import android.os.Build
import java.util.Calendar

/**
 * Estimates the true release/manufacture age of the phone based on device model,
 * factory first shipping API level (`ro.product.first_api_level`), SoC, and fallback build heuristics.
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

    private fun getSystemProperty(key: String): String? = try {
        val clazz = Class.forName("android.os.SystemProperties")
        val method = clazz.getMethod("get", String::class.java)
        val value = method.invoke(null, key) as? String
        if (value.isNullOrBlank()) null else value.trim()
    } catch (e: Exception) {
        null
    }

    /**
     * Determines device release year using model name lookup, factory first API level,
     * and hardware architecture.
     */
    fun estimateAge(): PhoneAge {
        val now = Calendar.getInstance()
        val currentYear = now.get(Calendar.YEAR)
        val currentMonth = now.get(Calendar.MONTH) + 1

        var deviceYear = 0
        var deviceMonth = 3 // default spring release

        val model = (Build.MODEL + " " + Build.DEVICE + " " + Build.PRODUCT).lowercase()

        // 1. Model keyword database
        when {
            // 2026 releases
            model.contains("s26") || model.contains("x300") || model.contains("find x9") || model.contains("poco x8") -> {
                deviceYear = 2026; deviceMonth = 2
            }
            // 2025 releases
            model.contains("s25") || model.contains("x200") || model.contains("find x8") || model.contains("xiaomi 15") ||
                model.contains("redmi note 14") || model.contains("poco x7") -> {
                deviceYear = 2025; deviceMonth = 1
            }
            // 2024 releases
            model.contains("s24") || model.contains("pixel 9") || model.contains("pixel 8a") || model.contains("xiaomi 14") ||
                model.contains("redmi note 13") || model.contains("poco f6") || model.contains("poco x6") ||
                model.contains("redmi 13") || model.contains("nord 4") || model.contains("nord ce 4") -> {
                deviceYear = 2024; deviceMonth = 3
            }
            // 2023 releases
            model.contains("s23") || model.contains("pixel 8") || model.contains("pixel 7a") || model.contains("xiaomi 13") ||
                model.contains("redmi note 12") || model.contains("redmi 12") || model.contains("poco f5") ||
                model.contains("poco x5") || model.contains("nord 3") || model.contains("nord ce 3") -> {
                deviceYear = 2023; deviceMonth = 3
            }
            // 2022 releases
            model.contains("s22") || model.contains("pixel 7") || model.contains("pixel 6a") || model.contains("xiaomi 12") ||
                model.contains("redmi note 11") || model.contains("redmi 10") || model.contains("poco f4") ||
                model.contains("poco x4") || model.contains("nord 2t") || model.contains("nord ce 2") -> {
                deviceYear = 2022; deviceMonth = 3
            }
            // 2021 releases
            model.contains("s21") || model.contains("pixel 6") || model.contains("pixel 5a") || model.contains("xiaomi 11") ||
                model.contains("redmi note 10") || model.contains("redmi 9") || model.contains("poco f3") ||
                model.contains("poco x3 pro") || model.contains("nord 2") -> {
                deviceYear = 2021; deviceMonth = 3
            }
            // 2020 releases
            model.contains("s20") || model.contains("note 20") || model.contains("pixel 5") || model.contains("pixel 4a") ||
                model.contains("redmi note 9") || model.contains("poco x3") || model.contains("nord") -> {
                deviceYear = 2020; deviceMonth = 4
            }
            // 2019 releases
            model.contains("s10") || model.contains("note 10") || model.contains("pixel 4") || model.contains("pixel 3a") ||
                model.contains("redmi note 8") || model.contains("redmi note 7") || model.contains("oppo a3s") ||
                model.contains("realme 3") || model.contains("realme 5") || model.contains("galaxy m30") -> {
                deviceYear = 2019; deviceMonth = 3
            }
            // 2018 or older
            model.contains("s9") || model.contains("note 9") || model.contains("pixel 3") || model.contains("redmi note 5") ||
                model.contains("redmi note 6") || model.contains("pocophone f1") || model.contains("realme 1") ||
                model.contains("realme 2") -> {
                deviceYear = 2018; deviceMonth = 4
            }
        }

        // 2. Factory First API Level (`ro.product.first_api_level` or `ro.board.first_api_level`)
        // This is set once in factory firmware and NEVER increases when the phone updates Android!
        if (deviceYear == 0) {
            val firstApi = getSystemProperty("ro.product.first_api_level")?.toIntOrNull()
                ?: getSystemProperty("ro.board.first_api_level")?.toIntOrNull()
                ?: getSystemProperty("ro.vendor.build.version.sdk")?.toIntOrNull()

            if (firstApi != null && firstApi > 0) {
                deviceYear = when {
                    firstApi >= 35 -> 2024 // Android 15 launch
                    firstApi == 34 -> 2023 // Android 14 launch
                    firstApi == 33 -> 2022 // Android 13 launch
                    firstApi == 31 || firstApi == 32 -> 2021 // Android 12 / 12L
                    firstApi == 30 -> 2020 // Android 11 launch
                    firstApi == 29 -> 2019 // Android 10 launch
                    firstApi == 28 -> 2018 // Android 9 launch
                    firstApi == 26 || firstApi == 27 -> 2017 // Android 8
                    firstApi <= 25 -> 2016
                    else -> 2022
                }
                deviceMonth = 6
            }
        }

        // 3. Fallback: Security Patch Level or build timestamp
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

        if (deviceYear == 0) {
            deviceYear = 2022
            deviceMonth = 1
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
