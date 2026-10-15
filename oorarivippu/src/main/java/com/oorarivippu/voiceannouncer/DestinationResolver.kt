package com.oorarivippu.voiceannouncer

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

internal data class ResolvedDestination(
    val displayName: String,
    val latitude: Double,
    val longitude: Double
)

/** Geocodes a user-entered destination off the UI thread. No offline map database is bundled. */
@Suppress("DEPRECATION")
internal suspend fun resolveDestination(
    context: Context,
    query: String,
    voiceLanguage: String
): ResolvedDestination? = withContext(Dispatchers.IO) {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank() || !Geocoder.isPresent()) return@withContext null
    try {
        val locale = if (voiceLanguage == "en") Locale("en", "IN") else Locale("ta", "IN")
        val result = Geocoder(context.applicationContext, locale)
            .getFromLocationName(cleanQuery, 1)
            ?.firstOrNull()
            ?: return@withContext null
        if (!result.latitude.isFinite() || !result.longitude.isFinite()) return@withContext null
        ResolvedDestination(
            displayName = cleanQuery,
            latitude = result.latitude,
            longitude = result.longitude
        )
    } catch (_: Exception) {
        // Includes unavailable geocoder backends and network lookup failures.
        null
    }
}
