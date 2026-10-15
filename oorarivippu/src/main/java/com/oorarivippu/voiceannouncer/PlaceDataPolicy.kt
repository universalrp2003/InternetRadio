package com.oorarivippu.voiceannouncer

import java.util.Locale

/** Pure address-field filters so geocoder noise can be tested without a device. */
internal object PlaceDataPolicy {
    private val numberOnlyComponent = Regex("""^[\p{N}\s/.,#\-+]+$""")
    // Open Location Codes (Plus Codes) are identifiers, not names to show or speak.
    // Accept the broader alphanumeric alphabet as geocoders sometimes return malformed codes.
    private val plusCode = Regex("""(?i)(?<![A-Z0-9])[A-Z0-9]{4,8}\+[A-Z0-9]{2,3}(?![A-Z0-9])""")
    private val coordinatePair = Regex(
        """(?i)^\s*[-+]?\d{1,3}(?:\.\d+)?\s*°?\s*[NS]?\s*[,/]\s*[-+]?\d{1,3}(?:\.\d+)?\s*°?\s*[EW]?\s*$"""
    )
    private val whitespace = Regex("""\s+""")
    private val routeSeparator = Regex("""\s*[-–—]\s*""")
    private val roadOrRoute = Regex(
        """(?i)\b(?:road|rd\.?|street|st\.?|highway|hwy\.?)\b|சாலை|நெடுஞ்சாலை|தெரு"""
    )
    private val highwayOrMajorLandmark = Regex(
        """(?i)\b(?:NH|SH)\s*[-.]?\s*\d+[A-Z]?\b|\b(?:national|state)\s+(?:highway|hwy)\b|\b(?:toll\s+(?:plaza|gate)|interchange|highway\s+junction|bypass)\b|தேசிய\s+நெடுஞ்சாலை|மாநில\s+நெடுஞ்சாலை|சுங்கச்சாவடி|பைபாஸ்"""
    )

    /** Removes Plus Codes embedded in geocoder text and returns only a readable name. */
    fun cleanPlaceName(candidate: String?): String? {
        val value = candidate?.let { raw ->
            val withoutCodes = plusCode.replace(raw, " ")
            whitespace.replace(withoutCodes, " ").trim { character ->
                character.isWhitespace() || character in ",;:|()[]{}"
            }
        }.orEmpty()
        return value.takeIf {
            it.isNotBlank() &&
                !numberOnlyComponent.matches(it) &&
                !coordinatePair.matches(it)
        }
    }

    fun cleanCityOrVillage(candidates: Iterable<String?>): String =
        candidates.firstNotNullOfOrNull(::cleanPlaceName).orEmpty()

    fun cleanSubArea(candidates: Iterable<String?>, cityOrVillage: String): String =
        candidates.firstNotNullOfOrNull { candidate ->
            cleanPlaceName(candidate)?.takeUnless { value ->
                samePlaceName(value, cityOrVillage) ||
                    highwayOrMajorLandmark.containsMatchIn(value) ||
                    isMultiStopRoadRoute(value)
            }
        }.orEmpty()

    /** Compares names after Plus Code removal and ignores punctuation/case-only differences. */
    fun samePlaceName(first: String, second: String): Boolean =
        placeKey(first).isNotBlank() && placeKey(first) == placeKey(second)

    fun highwayLandmark(candidates: Iterable<String?>): String? =
        candidates.firstNotNullOfOrNull { candidate ->
            cleanPlaceName(candidate)?.takeIf { value ->
                highwayOrMajorLandmark.containsMatchIn(value)
            }
        }

    private fun placeKey(value: String): String =
        cleanPlaceName(value)
            ?.lowercase(Locale.ROOT)
            ?.filter(Char::isLetterOrDigit)
            .orEmpty()

    /** Ignore long route/address chains such as “Town A - Town B - Town C Road”. */
    private fun isMultiStopRoadRoute(value: String): Boolean =
        roadOrRoute.containsMatchIn(value) && routeSeparator.findAll(value).count() >= 2
}
