package com.universalrp.cleansweep.data

import android.os.Build
import java.util.Calendar
import java.util.Locale

/**
 * Local, coarse model/initial-Android-generation age estimate, NOT the manufacture,
 * purchase or first-use date of this particular handset. Firmware updates/uptime
 * cannot establish physical phone age. Unsupported evidence remains unavailable.
 */
object PhoneAgeEstimator {
    enum class Basis { MODEL_FAMILY, FACTORY_ANDROID_GENERATION, UNAVAILABLE }

    data class PhoneAge(
        val years: Int,
        val months: Int,
        val releaseYear: Int, // Approximate reference year, not this unit's manufacture date.
        val isEstimated: Boolean = true,
        val basis: Basis = Basis.MODEL_FAMILY,
    ) {
        val available: Boolean get() = releaseYear > 0 && years >= 0 && months in 0..11 && basis != Basis.UNAVAILABLE

        fun displayString(isTamil: Boolean): String {
            if (!available) return if (isTamil) "மதிப்பிடத் தகவல் இல்லை" else "Not enough information"
            return if (isTamil) {
                when {
                    years > 0 && months > 0 -> "சுமார் $years வருடம் $months மாதம் ($releaseYear)"
                    years > 0 -> "சுமார் $years வருடம் ($releaseYear)"
                    months > 0 -> "சுமார் $months மாதம் ($releaseYear)"
                    else -> "சுமார் 0 மாதம் ($releaseYear)"
                }
            } else {
                when {
                    years > 0 && months > 0 -> "About $years yr $months mo ($releaseYear)"
                    years > 0 -> "About $years yr ($releaseYear)"
                    months > 0 -> "About $months mo ($releaseYear)"
                    else -> "About 0 mo ($releaseYear)"
                }
            }
        }
    }

    fun unavailable() = PhoneAge(0, 0, 0, basis = Basis.UNAVAILABLE)

    private fun getSystemProperty(key: String): String? = try {
        val clazz = Class.forName("android.os.SystemProperties")
        val method = clazz.getMethod("get", String::class.java)
        val value = method.invoke(null, key) as? String
        value?.trim()?.takeIf { it.isNotEmpty() }
    } catch (e: Exception) { null }

    /** Best-effort local evidence only; hidden property access may be unavailable on a ROM. */
    fun estimateAge(): PhoneAge {
        val now = Calendar.getInstance()
        val firstApi = getSystemProperty("ro.product.first_api_level")?.toIntOrNull()?.takeIf { it > 0 }
            ?: getSystemProperty("ro.board.first_api_level")?.toIntOrNull()?.takeIf { it > 0 }
        return estimateFromEvidence(
            "${Build.MODEL} ${Build.DEVICE} ${Build.PRODUCT}", firstApi,
            now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1,
        )
    }

    /** Pure calculation for repeatable tests; never substitutes the CURRENT Android SDK/patch. */
    fun estimateFromEvidence(modelText: String, firstApi: Int?, currentYear: Int, currentMonth: Int): PhoneAge {
        if (currentYear < 2015 || currentMonth !in 1..12) return unavailable()
        val model = modelText.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), " ").trim()
        var deviceYear = 0
        var deviceMonth = 3 // Coarse representative month, not an official launch/manufacture date.
        var basis = Basis.MODEL_FAMILY

        // Existing model-family heuristics, not a verified official launch-date database.
        when {
            // 2026 approximate family reference group
            matchesFamily(model, "s26") || matchesFamily(model, "x300") || matchesFamily(model, "find x9") || matchesFamily(model, "poco x8") -> {
                deviceYear = 2026; deviceMonth = 2
            }
            // 2025 approximate family reference group
            matchesFamily(model, "s25") || matchesFamily(model, "x200") || matchesFamily(model, "find x8") || matchesFamily(model, "xiaomi 15") ||
                matchesFamily(model, "redmi note 14") || matchesFamily(model, "poco x7") -> {
                deviceYear = 2025; deviceMonth = 1
            }
            // 2024 approximate family reference group
            matchesFamily(model, "s24") || matchesFamily(model, "pixel 9") || matchesFamily(model, "pixel 8a") || matchesFamily(model, "xiaomi 14") ||
                matchesFamily(model, "redmi note 13") || matchesFamily(model, "poco f6") || matchesFamily(model, "poco x6") ||
                matchesFamily(model, "redmi 13") || matchesFamily(model, "nord 4") || matchesFamily(model, "nord ce 4") -> {
                deviceYear = 2024; deviceMonth = 3
            }
            // 2023 approximate family reference group
            matchesFamily(model, "s23") || matchesFamily(model, "pixel 8") || matchesFamily(model, "pixel 7a") || matchesFamily(model, "xiaomi 13") ||
                matchesFamily(model, "redmi note 12") || matchesFamily(model, "redmi 12") || matchesFamily(model, "poco f5") ||
                matchesFamily(model, "poco x5") || matchesFamily(model, "nord 3") || matchesFamily(model, "nord ce 3") -> {
                deviceYear = 2023; deviceMonth = 3
            }
            // 2022 approximate family reference group
            matchesFamily(model, "s22") || matchesFamily(model, "pixel 7") || matchesFamily(model, "pixel 6a") || matchesFamily(model, "xiaomi 12") ||
                matchesFamily(model, "redmi note 11") || matchesFamily(model, "redmi 10") || matchesFamily(model, "poco f4") ||
                matchesFamily(model, "poco x4") || matchesFamily(model, "nord 2t") || matchesFamily(model, "nord ce 2") -> {
                deviceYear = 2022; deviceMonth = 3
            }
            // 2021 approximate family reference group
            matchesFamily(model, "s21") || matchesFamily(model, "pixel 6") || matchesFamily(model, "pixel 5a") || matchesFamily(model, "xiaomi 11") ||
                matchesFamily(model, "redmi note 10") || matchesFamily(model, "redmi 9") || matchesFamily(model, "poco f3") ||
                matchesFamily(model, "poco x3 pro") || matchesFamily(model, "nord 2") -> {
                deviceYear = 2021; deviceMonth = 3
            }
            // 2020 approximate family reference group
            matchesFamily(model, "s20") || matchesFamily(model, "note 20") || matchesFamily(model, "pixel 5") || matchesFamily(model, "pixel 4a") ||
                matchesFamily(model, "redmi note 9") || matchesFamily(model, "poco x3") || matchesFamily(model, "nord") -> {
                deviceYear = 2020; deviceMonth = 4
            }
            // 2019 approximate family reference group
            matchesFamily(model, "s10") || matchesFamily(model, "note 10") || matchesFamily(model, "pixel 4") || matchesFamily(model, "pixel 3a") ||
                matchesFamily(model, "redmi note 8") || matchesFamily(model, "redmi note 7") || matchesFamily(model, "oppo a3s") ||
                matchesFamily(model, "realme 3") || matchesFamily(model, "realme 5") || matchesFamily(model, "galaxy m30") -> {
                deviceYear = 2019; deviceMonth = 3
            }
            // 2018 or older
            matchesFamily(model, "s9") || matchesFamily(model, "note 9") || matchesFamily(model, "pixel 3") || matchesFamily(model, "redmi note 5") ||
                matchesFamily(model, "redmi note 6") || matchesFamily(model, "pocophone f1") || matchesFamily(model, "realme 1") ||
                matchesFamily(model, "realme 2") -> {
                deviceYear = 2018; deviceMonth = 4
            }
        }

        // Factory first API identifies an Android generation, NOT the handset's exact launch date.
        // June is the existing coarse mid-year reference for this heuristic; months are approximate.
        if (deviceYear == 0) {
            deviceYear = when (firstApi) {
                36 -> 2025
                35 -> 2024
                34 -> 2023
                33 -> 2022
                31, 32 -> 2021
                30 -> 2020
                29 -> 2019
                28 -> 2018
                26, 27 -> 2017
                24, 25 -> 2016
                23 -> 2015
                else -> 0
            }
            deviceMonth = 6
            basis = Basis.FACTORY_ANDROID_GENERATION
        }
        // Do not invent a default 2022 phone or derive age from an updated vendor SDK/security patch.
        if (deviceYear == 0) return unavailable()
        val totalMonths = (currentYear - deviceYear) * 12 + currentMonth - deviceMonth
        if (totalMonths < 0) return unavailable()
        return PhoneAge(totalMonths / 12, totalMonths % 12, deviceYear, basis = basis)
    }

    private fun matchesFamily(model: String, family: String): Boolean =
        Regex("(?:^| )" + Regex.escape(family) + "(?:$| )").containsMatchIn(model)
}
