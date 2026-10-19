package com.oorarivippu.voiceannouncer

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

/**
 * One place that matches the destination the person typed. Many villages share a name (owner
 * report: பள்ளப்பட்டி exists in several districts), so the person chooses from these, with the
 * district, state, PIN code and distance shown, instead of trusting the geocoder's first match.
 */
internal data class DestinationCandidate(
    /** The place name, shown and spoken in the destination alerts. */
    val name: String,
    /** Where it is: district, state and PIN code from the geocoder. */
    val details: String,
    val latitude: Double,
    val longitude: Double,
    /** Straight-line distance from this phone's last known position, if one is known. */
    val distanceKm: Int?
)

/** Pure parts of the destination search, so they can be tested without a device. */
internal object DestinationSearchPolicy {
    const val MAX_RESULTS = 10
    private const val SAME_PLACE_METERS = 1_000f
    private val separators = Regex("""\s*[/\\|]+\s*""")
    private val whitespace = Regex("""\s+""")

    /** "pallapatti/karur" becomes "pallapatti, karur", which geocoders understand. */
    fun normalizeQuery(query: String): String =
        whitespace.replace(separators.replace(query.trim(), ", "), " ")
            .trim { it.isWhitespace() || it == ',' }

    /** "கரூர் மாவட்டம், தமிழ்நாடு 639205": district and state that differ from the name, then the PIN code. */
    fun details(
        name: String,
        locality: String?,
        district: String?,
        state: String?,
        postalCode: String?,
        districtWord: String
    ): String {
        val parts = mutableListOf<String>()
        PlaceDataPolicy.cleanPlaceName(locality)
            ?.takeUnless { PlaceDataPolicy.samePlaceName(it, name) }
            ?.let { parts.add(it) }
        PlaceDataPolicy.cleanPlaceName(district)
            ?.let { PlaceDataPolicy.withoutDistrictSuffix(it) }
            ?.takeUnless { value -> parts.any { PlaceDataPolicy.samePlaceName(it, value) } }
            ?.let { parts += "$it $districtWord" }
        PlaceDataPolicy.cleanPlaceName(state)?.let { parts.add(it) }
        val pin = postalCode?.trim()?.takeIf { it.matches(Regex("""\d{6}""")) }
        val place = parts.joinToString(", ")
        return listOfNotNull(place.takeIf { it.isNotBlank() }, pin).joinToString(" ")
    }

    /** Drops repeats: the same name and details, or the same name within about a kilometre. */
    fun distinct(candidates: List<DestinationCandidate>): List<DestinationCandidate> {
        val kept = mutableListOf<DestinationCandidate>()
        for (candidate in candidates) {
            val repeat = kept.any { earlier ->
                PlaceDataPolicy.samePlaceName(earlier.name, candidate.name) &&
                    (earlier.details == candidate.details ||
                        distanceMeters(earlier, candidate) < SAME_PLACE_METERS)
            }
            if (!repeat) kept += candidate
        }
        return kept
    }

    /** Great-circle distance (pure Kotlin, so it also runs in unit tests). */
    private fun distanceMeters(first: DestinationCandidate, second: DestinationCandidate): Float {
        val lat1 = Math.toRadians(first.latitude)
        val lat2 = Math.toRadians(second.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(second.longitude - first.longitude)
        val a = Math.sin(dLat / 2).let { it * it } +
            Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2).let { it * it }
        return (2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(a.coerceIn(0.0, 1.0)))).toFloat()
    }

    private const val EARTH_RADIUS_METERS = 6_371_000.0
}

/** Finds the places that match [query], off the UI thread. No offline map database is bundled. */
@Suppress("DEPRECATION")
internal suspend fun findDestinationCandidates(
    context: Context,
    query: String,
    voiceLanguage: String,
    origin: Location?
): List<DestinationCandidate> = withContext(Dispatchers.IO) {
    val cleanQuery = DestinationSearchPolicy.normalizeQuery(query)
    if (cleanQuery.isBlank() || !Geocoder.isPresent()) return@withContext emptyList()
    try {
        val english = voiceLanguage == "en"
        val locale = if (english) Locale("en", "IN") else Locale("ta", "IN")
        val geocoder = Geocoder(context.applicationContext, locale)
        // Places in India first; anywhere else only if nothing in India matches.
        val addresses = geocoder.getFromLocationName(
            cleanQuery,
            DestinationSearchPolicy.MAX_RESULTS,
            INDIA_SOUTH,
            INDIA_WEST,
            INDIA_NORTH,
            INDIA_EAST
        )?.takeIf { it.isNotEmpty() }
            ?: geocoder.getFromLocationName(cleanQuery, DestinationSearchPolicy.MAX_RESULTS)
            ?: emptyList()
        val districtWord = if (english) "district" else "மாவட்டம்"
        DestinationSearchPolicy.distinct(
            addresses.mapNotNull { address -> candidate(address, cleanQuery, origin, districtWord) }
        )
    } catch (_: Exception) {
        // Includes unavailable geocoder backends and network lookup failures.
        emptyList()
    }
}

private fun candidate(
    address: Address,
    query: String,
    origin: Location?,
    districtWord: String
): DestinationCandidate? {
    if (!address.hasLatitude() || !address.hasLongitude()) return null
    if (!address.latitude.isFinite() || !address.longitude.isFinite()) return null
    val name = PlaceDataPolicy.cleanCityOrVillage(
        listOf(address.featureName, address.locality, address.subLocality, address.subAdminArea)
    ).ifBlank { query.substringBefore(',').trim() }
    val distanceKm = origin?.let { here ->
        val result = FloatArray(1)
        Location.distanceBetween(here.latitude, here.longitude, address.latitude, address.longitude, result)
        (result[0] / 1_000f).roundToInt()
    }
    return DestinationCandidate(
        name = name,
        details = DestinationSearchPolicy.details(
            name = name,
            locality = address.locality,
            district = address.subAdminArea,
            state = address.adminArea,
            postalCode = address.postalCode,
            districtWord = districtWord
        ),
        latitude = address.latitude,
        longitude = address.longitude,
        distanceKm = distanceKm
    )
}

/** This phone's most recent known position, if location access is granted; never waits for GPS. */
@SuppressLint("MissingPermission")
internal fun lastKnownLocation(context: Context): Location? {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    if (!fine && !coarse) return null
    val manager = context.getSystemService(LocationManager::class.java) ?: return null
    return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.time }
}

// India's bounding box (with a margin), used to prefer Indian places with the same name.
private const val INDIA_SOUTH = 6.0
private const val INDIA_WEST = 68.0
private const val INDIA_NORTH = 37.5
private const val INDIA_EAST = 97.5
