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

    // A leading door number ("12/3, Gandhi Nagar", "No. 5, …") and a six-digit PIN code are not names.
    private val leadingDoorNumber = Regex("""^\s*(?:(?i:no)\.?\s*)?\d+[\p{L}\d/\-]*\s*,\s*""")
    private val pinCode = Regex("""(?<!\d)\d{6}(?!\d)""")

    // Geocoder placeholders for roads and places without a name (owner report, 16 October 2026:
    // Google's "Unnamed Road", in Tamil "பெயரில்லாத சாலை", was announced as an area).
    private val englishPlaceholder = Regex(
        """(?i)^(?:unnamed|un-named|unknown|nameless|no\s*name|null|undefined|n/a)\b.*$"""
    )
    private val tamilPlaceholderPrefixes = listOf(
        "பெயரில்லாத",
        "பெயர் இல்லாத",
        "பெயரிடப்படாத",
        "பெயர் இடப்படாத",
        "பெயரற்ற",
        "தெரியாத",
    )

    // A road word on its own names nothing ("Road", "சாலை"), nor does a highway's "Service Road".
    private val genericRoadOnly = Regex(
        """(?i)^(?:service\s+)?(?:road|rd\.?|street|st\.?|lane|highway)$|^(?:சர்வீஸ்\s+|சேவை\s+)?(?:சாலை|ரோடு|தெரு|வீதி|நெடுஞ்சாலை)$"""
    )

    // The country or a state is never a town or an area to announce.
    private val regionNames = setOf(
        "india", "இந்தியா", "tamil nadu", "tamilnadu", "தமிழ்நாடு", "தமிழ் நாடு", "kerala", "கேரளா",
        "karnataka", "கர்நாடகா", "andhra pradesh", "ஆந்திரப் பிரதேசம்", "ஆந்திரா", "telangana", "தெலங்கானா",
    )
    private val districtSuffix = Regex("""(?i)\s*(?:\bdistrict\b|\bdist\.?)\s*$|\s*மாவட்டம்\s*$""")

    /** Removes Plus Codes, door numbers and PIN codes and returns only a readable name. */
    fun cleanPlaceName(candidate: String?): String? {
        val value = candidate?.let { raw ->
            val withoutCodes = pinCode.replace(plusCode.replace(raw, " "), " ")
            val withoutDoorNumber = leadingDoorNumber.replace(withoutCodes, "")
            whitespace.replace(withoutDoorNumber, " ").trim { character ->
                character.isWhitespace() || character in ",;:|()[]{}"
            }
        }.orEmpty()
        return value.takeIf {
            it.isNotBlank() &&
                !numberOnlyComponent.matches(it) &&
                !coordinatePair.matches(it) &&
                !isPlaceholder(it)
        }
    }

    /** True for geocoder placeholders such as "Unnamed Road" or "பெயரில்லாத சாலை". */
    fun isPlaceholder(value: String): Boolean {
        val trimmed = value.trim()
        return englishPlaceholder.matches(trimmed) ||
            tamilPlaceholderPrefixes.any { trimmed.startsWith(it) } ||
            genericRoadOnly.matches(trimmed)
    }

    fun cleanCityOrVillage(candidates: Iterable<String?>): String =
        candidates.firstNotNullOfOrNull { candidate ->
            cleanPlaceName(candidate)?.takeUnless(::isRegionName)
        }.orEmpty()

    fun cleanSubArea(candidates: Iterable<String?>, cityOrVillage: String): String =
        candidates.firstNotNullOfOrNull { candidate ->
            cleanPlaceName(candidate)?.takeUnless { value ->
                samePlaceName(value, cityOrVillage) ||
                    isRegionName(value) ||
                    highwayOrMajorLandmark.containsMatchIn(value) ||
                    isMultiStopRoadRoute(value)
            }
        }.orEmpty()

    /**
     * True if both name the same place. Case and punctuation are ignored, and a Tamil name
     * matches its English spelling (owner report: "ஆலங்குடி", then "Alangudi" 1.5 minutes later,
     * was announced twice). Names in the same script must match exactly, so two different
     * places are never merged because their spellings sound alike.
     */
    fun samePlaceName(first: String, second: String): Boolean {
        val firstKey = placeKey(first)
        if (firstKey.isBlank()) return false
        if (firstKey == placeKey(second)) return true
        if (hasTamil(first) == hasTamil(second)) return false
        val firstSound = soundKey(first)
        return firstSound.length >= MIN_SOUND_KEY && firstSound == soundKey(second)
    }

    fun highwayLandmark(candidates: Iterable<String?>): String? =
        candidates.firstNotNullOfOrNull { candidate ->
            cleanPlaceName(candidate)?.takeIf { value ->
                highwayOrMajorLandmark.containsMatchIn(value)
            }
        }

    /** "Karur District" or "கரூர் மாவட்டம்" without the word, so announcements add it only once. */
    fun withoutDistrictSuffix(district: String): String =
        districtSuffix.replace(district, "").trim().ifBlank { district.trim() }

    private fun isRegionName(value: String): Boolean = value.trim().lowercase(Locale.ROOT) in regionNames

    private fun placeKey(value: String): String =
        cleanPlaceName(value)
            ?.lowercase(Locale.ROOT)
            ?.filter(Char::isLetterOrDigit)
            .orEmpty()

    private fun hasTamil(value: String): Boolean = value.any { it in '\u0B80'..'\u0BFF' }

    /**
     * The consonant sounds of a name, the same for Tamil script and its usual English spelling:
     * vowels are dropped, letters that English writes differently for one Tamil sound are merged
     * (த/ட/d/th → t, க/g/c → k, ச/ஜ/s/ch/j → s, ல/ள/ழ/zh → l, ன/ண/ந/ங → n, ப/b → p), and
     * doubled letters count once. "ஆலங்குடி" and "Alangudi" both give "lnkt".
     */
    private fun soundKey(value: String): String {
        val name = cleanPlaceName(value)?.lowercase(Locale.ROOT).orEmpty()
        val classes = StringBuilder()
        var latin = StringBuilder()

        fun flushLatin() {
            if (latin.isNotEmpty()) {
                classes.append(latinSounds(latin.toString()))
                latin = StringBuilder()
            }
        }

        for (character in name) {
            when {
                character in '\u0B80'..'\u0BFF' -> {
                    flushLatin()
                    tamilConsonants[character]?.let { classes.append(it) }
                }
                character in 'a'..'z' -> latin.append(character)
                character.isDigit() -> {
                    flushLatin()
                    classes.append(character)
                }
                else -> flushLatin()
            }
        }
        flushLatin()
        val collapsed = StringBuilder()
        for (character in classes) {
            if (collapsed.isEmpty() || collapsed.last() != character) collapsed.append(character)
        }
        return collapsed.toString()
    }

    private fun latinSounds(word: String): String {
        var text = word
        listOf(
            "ng" to "nk", "th" to "t", "dh" to "t", "zh" to "l", "sh" to "s", "ch" to "s",
            "kh" to "k", "gh" to "k", "ph" to "p", "bh" to "p",
        ).forEach { (from, to) -> text = text.replace(from, to) }
        val out = StringBuilder()
        for (character in text) {
            when (character) {
                'a', 'e', 'i', 'o', 'u', 'y', 'h' -> Unit
                'd' -> out.append('t')
                'g', 'c', 'q' -> out.append('k')
                'x' -> out.append("ks")
                'b', 'f' -> out.append('p')
                'w' -> out.append('v')
                'j', 'z' -> out.append('s')
                else -> out.append(character)
            }
        }
        return out.toString()
    }

    private val tamilConsonants: Map<Char, Char> = mapOf(
        'க' to 'k', 'ங' to 'n', 'ச' to 's', 'ஜ' to 's', 'ஞ' to 'n', 'ட' to 't', 'ண' to 'n',
        'த' to 't', 'ந' to 'n', 'ன' to 'n', 'ப' to 'p', 'ம' to 'm', 'ர' to 'r', 'ற' to 'r',
        'ல' to 'l', 'ள' to 'l', 'ழ' to 'l', 'வ' to 'v', 'ஶ' to 's', 'ஷ' to 's', 'ஸ' to 's',
    )

    private const val MIN_SOUND_KEY = 2

    /** Ignore long route/address chains such as “Town A - Town B - Town C Road”. */
    private fun isMultiStopRoadRoute(value: String): Boolean =
        roadOrRoute.containsMatchIn(value) && routeSeparator.findAll(value).count() >= 2
}

/**
 * Towns announced in the last few minutes. Near a town border the geocoder can alternate between
 * two neighbouring towns; each is announced once, not every time the answer flips.
 */
internal class RecentPlaces(private val windowMillis: Long = DEFAULT_WINDOW_MILLIS) {
    private val entries = ArrayDeque<Pair<String, Long>>()

    fun wasAnnouncedRecently(name: String, nowMillis: Long): Boolean {
        prune(nowMillis)
        return entries.any { (announced, _) -> PlaceDataPolicy.samePlaceName(announced, name) }
    }

    fun remember(name: String, nowMillis: Long) {
        if (name.isBlank()) return
        prune(nowMillis)
        entries.removeAll { (announced, _) -> PlaceDataPolicy.samePlaceName(announced, name) }
        entries.addLast(name to nowMillis)
        while (entries.size > MAX_ENTRIES) entries.removeFirst()
    }

    fun clear() = entries.clear()

    private fun prune(nowMillis: Long) {
        entries.removeAll { (_, at) -> nowMillis - at > windowMillis || nowMillis < at }
    }

    companion object {
        const val DEFAULT_WINDOW_MILLIS = 10 * 60_000L
        private const val MAX_ENTRIES = 8
    }
}
