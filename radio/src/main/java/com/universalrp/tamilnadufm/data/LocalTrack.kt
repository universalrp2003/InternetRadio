package com.universalrp.tamilnadufm.data

import android.net.Uri

/** A song from the phone's own storage, played through the same equalizer. */
data class LocalTrack(
    val uri: Uri,
    val title: String,
    val artist: String,
    val durationMs: Long,
) {
    val durationLabel: String
        get() {
            if (durationMs <= 0L) return ""
            val total = durationMs / 1000L
            return "%d:%02d".format(total / 60, total % 60)
        }

    val subtitle: String
        get() = listOf(artist, durationLabel).filter { it.isNotBlank() }.joinToString(" • ")
}
