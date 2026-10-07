package com.universalrp.tamilnadufm.audio

/**
 * The 10 bands this app's equalizer shapes — the classic octave layout, matching
 * the faders on hardware equalizers (and the ones in the screenshots).
 */
object Bands {
    const val COUNT = 10
    val FREQ = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    val LABELS = arrayOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
    const val MIN_DB = -12f
    const val MAX_DB = 12f
}

/** One preset: 10 gains (low → high) plus its own preamp trim. */
class EqPreset(
    val name: String,
    val gains: FloatArray,
    val preampDb: Float = 0f,
    val note: String = "",
)

object EqPresets {

    val FLAT = EqPreset("Flat", FloatArray(Bands.COUNT))

    val ALL: List<EqPreset> = listOf(
        FLAT,
        EqPreset(
            "Tamil FM",
            floatArrayOf(3f, 3f, 2.5f, 2f, 1f, 0f, 0.5f, 1.5f, 2f, 1.5f),
            preampDb = -3f,
            note = "Warm low end with clear voices — a good default for Tamil FM",
        ),
        EqPreset(
            "Old songs",
            floatArrayOf(2.5f, 2.5f, 2f, 1.5f, 1f, 0.5f, 0.5f, 1f, 1.5f, 1f),
            preampDb = -2f,
            note = "Kind to older recordings — soft top end, no harshness",
        ),
        EqPreset(
            "Music",
            floatArrayOf(4f, 3.5f, 3f, 2f, 1f, 0f, 0.5f, 1.5f, 2.5f, 3f),
            preampDb = -4f,
            note = "Smile curve: full bass, open highs",
        ),
        EqPreset(
            "Movies",
            floatArrayOf(5f, 5f, 4f, 2.5f, 0f, -1.5f, -1f, 1f, 3f, 2.5f),
            preampDb = -5f,
            note = "Cinema punch with dialogue kept forward",
        ),
        EqPreset(
            "Speech / News",
            floatArrayOf(-7f, -6f, -5f, -3f, 0f, 2f, 3.5f, 4f, 2.5f, 1f),
            preampDb = 0f,
            note = "Removes rumble, lifts voices — best for news and talk",
        ),
        EqPreset(
            "Podcast",
            floatArrayOf(-6f, -5f, -4f, -2f, 0f, 1.5f, 3f, 3.5f, 2f, 0.5f),
            preampDb = 0f,
            note = "Speech-forward but easier on the ears than News",
        ),
        EqPreset(
            "Vocal",
            floatArrayOf(-5f, -4f, -3f, -1f, 1f, 3f, 4f, 3.5f, 1.5f, 0f),
            preampDb = -1f,
            note = "Pulls a singer in front of the band",
        ),
        EqPreset(
            "Bass boost",
            floatArrayOf(7f, 6.5f, 5.5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f),
            preampDb = -6f,
            note = "Deep low end; the limiter keeps it from breaking up",
        ),
        EqPreset(
            "Treble boost",
            floatArrayOf(0f, 0f, 0f, 0f, 0f, 0.5f, 1.5f, 3f, 4f, 5f),
            preampDb = -3f,
            note = "Detail and air without touching the bass",
        ),
        EqPreset(
            "Devotional",
            floatArrayOf(2f, 2f, 1.5f, 1f, 0.5f, 0.5f, 1f, 1.5f, 2f, 1.5f),
            preampDb = -2f,
            note = "Gentle and even for bhajans and temple music",
        ),
        EqPreset(
            "Rock",
            floatArrayOf(4f, 3.5f, 2.5f, 1f, -1f, -1.5f, 0.5f, 3f, 4f, 4f),
            preampDb = -4f,
            note = "Guitars and kick up, mid-range cleaned",
        ),
        EqPreset(
            "Pop",
            floatArrayOf(-1f, -0.5f, 0f, 1f, 1.5f, 1f, 0.5f, 1.5f, 2.5f, 3f),
            preampDb = -2f,
            note = "Bright and radio-like",
        ),
        EqPreset(
            "Jazz / Classical",
            floatArrayOf(3f, 2.5f, 2f, 1f, 0f, 0f, 0.5f, 1f, 2f, 2.5f),
            preampDb = -2f,
            note = "Natural, close to flat with a little sparkle",
        ),
        EqPreset(
            "Dance",
            floatArrayOf(6f, 5f, 3.5f, 1f, -1f, -2f, 0f, 2.5f, 3.5f, 3f),
            preampDb = -5f,
            note = "Club curve: sub, kick and a crisp top",
        ),
        EqPreset(
            "Night / low volume",
            floatArrayOf(4f, 3f, 1.5f, 0f, 0f, 0f, -1f, -2f, -3f, -4f),
            preampDb = -2f,
            note = "Even loudness at low volume, no harsh treble",
        ),
    )

    val CUSTOM = "Custom"

    fun matchName(gains: List<Float>): String {
        val match = ALL.firstOrNull { preset ->
            preset.gains.size == gains.size &&
                preset.gains.indices.all { kotlin.math.abs(preset.gains[it] - gains[it]) < 0.06f }
        }
        return match?.name ?: CUSTOM
    }

    fun find(name: String): EqPreset? = ALL.firstOrNull { it.name == name }
}
