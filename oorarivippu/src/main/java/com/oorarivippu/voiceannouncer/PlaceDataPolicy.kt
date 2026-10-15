package com.oorarivippu.voiceannouncer

/** Pure address-field filters so geocoder noise can be tested without a device. */
internal object PlaceDataPolicy {
    private val numberOnlyComponent = Regex("""^[\p{N}\s/.,#\-+]+$""")
    private val highwayOrMajorLandmark = Regex(
        """(?i)\b(?:NH|SH)\s*[-.]?\s*\d+[A-Z]?\b|\b(?:national|state)\s+(?:highway|hwy)\b|\b(?:toll\s+(?:plaza|gate)|interchange|highway\s+junction|bypass)\b|தேசிய\s+நெடுஞ்சாலை|மாநில\s+நெடுஞ்சாலை|சுங்கச்சாவடி|பைபாஸ்"""
    )

    fun cleanSubArea(candidates: Iterable<String?>, cityOrVillage: String): String =
        candidates.firstNotNullOfOrNull { candidate ->
            candidate?.trim()?.takeIf { value ->
                value.isNotBlank() &&
                    !value.equals(cityOrVillage, ignoreCase = true) &&
                    !numberOnlyComponent.matches(value)
            }
        }.orEmpty()

    fun highwayLandmark(candidates: Iterable<String?>): String? =
        candidates.firstNotNullOfOrNull { candidate ->
            candidate?.trim()?.takeIf { value ->
                value.isNotBlank() && highwayOrMajorLandmark.containsMatchIn(value)
            }
        }
}
