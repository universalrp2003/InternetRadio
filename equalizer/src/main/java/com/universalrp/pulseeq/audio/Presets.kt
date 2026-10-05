package com.universalrp.pulseeq.audio

/** One equalizer preset: 20 gain values plus a preamp to stop boosts clipping. */
class EqPreset(
    val name: String,
    val gains: FloatArray,
    val preampDb: Float = 0f,
    val description: String = "",
    val icon: String = "🎚",
)

/**
 * The preset library. Gain values are in dB (0 = untouched, +12 = max boost) and
 * are written low band → high band, matching [Bands.FREQ].
 *
 * These are tasteful curves rather than magic: Music is a gentle smile, Speech
 * cuts the rumble that muddy dialogue, Night tames the extremes for quiet
 * listening. Every one of them can be edited by dragging a fader.
 */
object Presets {

    private fun g(
        a0: Float, a1: Float, a2: Float, a3: Float, a4: Float,
        a5: Float, a6: Float, a7: Float, a8: Float, a9: Float,
        a10: Float, a11: Float, a12: Float, a13: Float, a14: Float,
        a15: Float, a16: Float, a17: Float, a18: Float, a19: Float,
    ): FloatArray = floatArrayOf(
        a0, a1, a2, a3, a4, a5, a6, a7, a8, a9,
        a10, a11, a12, a13, a14, a15, a16, a17, a18, a19,
    )

    val FLAT = EqPreset(
        name = "Flat",
        gains = FloatArray(Bands.COUNT),
        preampDb = 0f,
        description = "No colouring — the reference",
        icon = "▬",
    )

    val ALL: List<EqPreset> = listOf(
        FLAT,
        EqPreset(
            "Music",
            g(
                4f, 3.5f, 3f, 2f, 1f, 0f, -0.5f, -1f, -0.5f, 0f,
                0.5f, 1f, 1.5f, 2f, 2.5f, 3f, 3.5f, 4f, 4f, 3.5f,
            ),
            preampDb = -4f,
            description = "Warm low end, open top — good for most music",
            icon = "🎵",
        ),
        EqPreset(
            "Movies",
            g(
                5f, 5f, 4.5f, 4f, 2.5f, 1f, -1f, -2.5f, -2.5f, -1.5f,
                0f, 0.5f, 1.5f, 2.5f, 3f, 3.5f, 4f, 4f, 3.5f, 3f,
            ),
            preampDb = -5f,
            description = "Cinema rumble and clear dialogue in one curve",
            icon = "🎬",
        ),
        EqPreset(
            "Speech",
            g(
                -8f, -8f, -7f, -6f, -5f, -4f, -3f, -2f, -1f, 0f,
                1.5f, 3f, 4f, 4.5f, 4.5f, 4f, 3f, 2f, 1f, 0f,
            ),
            preampDb = 0f,
            description = "Strips rumble, lifts consonants — lectures, calls, audiobooks",
            icon = "🗣",
        ),
        EqPreset(
            "Podcast",
            g(
                -7f, -7f, -6f, -5f, -4f, -3f, -2.5f, -2f, -1f, 0f,
                1f, 2.5f, 3.5f, 4f, 4f, 3.5f, 2.5f, 1.5f, 0.5f, 0f,
            ),
            preampDb = 0f,
            description = "Speech-forward but easier on the ears than Speech",
            icon = "🎙",
        ),
        EqPreset(
            "Vocal",
            g(
                -6f, -6f, -5f, -4f, -3f, -2f, -1.5f, -1f, 0f, 1f,
                2.5f, 4f, 4.5f, 4.5f, 4f, 3f, 2f, 1f, 0.5f, 0f,
            ),
            preampDb = -1f,
            description = "Pulls a voice out in front of the instruments",
            icon = "🎤",
        ),
        EqPreset(
            "Bass boost",
            g(
                8f, 8f, 7.5f, 7f, 6f, 5f, 3.5f, 2f, 1f, 0f,
                0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            ),
            preampDb = -6f,
            description = "Deep low end. Preamp pulls back so it can't clip",
            icon = "🔊",
        ),
        EqPreset(
            "Treble boost",
            g(
                0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0.5f,
                0.5f, 1f, 1.5f, 2.5f, 3f, 3.5f, 4f, 4.5f, 5f, 5f,
            ),
            preampDb = -3f,
            description = "Detail and air without touching the bass",
            icon = "✨",
        ),
        EqPreset(
            "Rock",
            g(
                4f, 4f, 3.5f, 3f, 1.5f, 0f, -1f, -1.5f, -1f, 0f,
                1f, 2f, 2.5f, 3f, 3.5f, 4f, 4f, 4.5f, 4.5f, 4f,
            ),
            preampDb = -4f,
            description = "Guitars and kick pushed, mid-range cleaned up",
            icon = "🎸",
        ),
        EqPreset(
            "Pop",
            g(
                -1f, -1f, -0.5f, 0f, 0.5f, 1f, 1.5f, 1.5f, 1f, 0.5f,
                0f, 0.5f, 1f, 1.5f, 2f, 2.5f, 3f, 3f, 3f, 3f,
            ),
            preampDb = -2f,
            description = "Bright, vocal-friendly, radio-like balance",
            icon = "🎧",
        ),
        EqPreset(
            "Jazz",
            g(
                3f, 3f, 2.5f, 2f, 1f, 0.5f, 0f, 0f, 0.5f, 1f,
                1f, 0.5f, 0f, 0.5f, 1f, 1.5f, 2f, 2.5f, 3f, 3f,
            ),
            preampDb = -2f,
            description = "Natural body with a warm, unhurried top",
            icon = "🎷",
        ),
        EqPreset(
            "Classical",
            g(
                3f, 3f, 2.5f, 2f, 1.5f, 1f, 0f, 0f, 0f, 0.5f,
                1f, 1f, 0.5f, 0f, 0.5f, 1f, 1.5f, 2f, 2.5f, 3f,
            ),
            preampDb = -2f,
            description = "Nearly flat with a touch of hall and sparkle",
            icon = "🎻",
        ),
        EqPreset(
            "Dance / EDM",
            g(
                6f, 6f, 5f, 4f, 2f, 0f, -1f, -2f, -2f, -1f,
                0.5f, 2f, 3f, 3.5f, 3.5f, 3f, 2.5f, 2f, 2f, 2f,
            ),
            preampDb = -5f,
            description = "Club curve: sub, kick, and a crisp drop",
            icon = "🕺",
        ),
        EqPreset(
            "Hip-hop",
            g(
                7f, 7f, 6f, 5f, 3f, 1f, -0.5f, -1.5f, -1f, 0f,
                0.5f, 1f, 1.5f, 1.5f, 1f, 0.5f, 0f, 0f, 1f, 1.5f,
            ),
            preampDb = -5f,
            description = "Heavy 808s with vocals kept intelligible",
            icon = "🎤",
        ),
        EqPreset(
            "Gaming",
            g(
                3f, 3f, 2f, 1f, -1f, -2f, -2f, -1f, 0f, 1f,
                2f, 2.5f, 3f, 3.5f, 4f, 4f, 3.5f, 3f, 2f, 1f,
            ),
            preampDb = -3f,
            description = "Footsteps and cues up, explosions tamed",
            icon = "🎮",
        ),
        EqPreset(
            "Night / quiet",
            g(
                5f, 4f, 3f, 2f, 1f, 0f, 0f, 0f, 0f, 0f,
                -0.5f, -1f, -1.5f, -1.5f, -1f, -1f, -1.5f, -2f, -2.5f, -3f,
            ),
            preampDb = -2f,
            description = "Even loudness at low volume without harsh treble",
            icon = "🌙",
        ),
        EqPreset(
            "Bollywood",
            g(
                5f, 5f, 4f, 3f, 2f, 1f, 0f, -0.5f, 0f, 0.5f,
                1f, 2f, 2.5f, 3f, 3.5f, 4f, 4f, 4.5f, 4.5f, 4f,
            ),
            preampDb = -4f,
            description = "Dhol punch with clean vocals and strings",
            icon = "🪘",
        ),
    )

    val CUSTOM_NAME = "Custom"

    /** Finds the preset matching these exact gains, or the custom slot. */
    fun matchName(gains: List<Float>): String {
        val p = ALL.firstOrNull { preset ->
            preset.gains.size == gains.size &&
                preset.gains.indices.all { kotlin.math.abs(preset.gains[it] - gains[it]) < 0.05f }
        }
        return p?.name ?: CUSTOM_NAME
    }

    fun find(name: String): EqPreset? = ALL.firstOrNull { it.name == name }
}
