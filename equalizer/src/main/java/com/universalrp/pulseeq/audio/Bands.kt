package com.universalrp.pulseeq.audio

/**
 * The 20 bands PulseEQ shapes, spaced a half-octave apart from 31 Hz to 20 kHz.
 *
 * These are handled by PulseEQ's own biquad DSP, so the count does not depend on
 * what a particular phone's built-in equalizer offers (many have only 5 bands).
 */
object Bands {

    const val COUNT = 20

    /** Centre frequency of every band, lowest first. */
    val FREQ = floatArrayOf(
        31f, 45f, 63f, 90f, 125f, 180f, 250f, 355f, 500f, 710f,
        1000f, 1400f, 2000f, 2800f, 4000f, 5700f, 8000f, 11400f, 16100f, 20000f,
    )

    /** Short labels for the faders. */
    val LABELS = arrayOf(
        "31", "45", "63", "90", "125", "180", "250", "355", "500", "710",
        "1k", "1.4k", "2k", "2.8k", "4k", "5.7k", "8k", "11k", "16k", "20k",
    )

    /** Filter sharpness. ~1.1 keeps neighbouring bands from fighting each other. */
    const val Q = 1.1f

    const val MIN_DB = -12f
    const val MAX_DB = 12f

    fun label(index: Int): String = LABELS.getOrElse(index) { "" }

    /** "125 Hz" / "8 kHz" — used in the band info screen. */
    fun longLabel(index: Int): String {
        val f = FREQ.getOrElse(index) { 0f }
        return if (f >= 1000f) {
            val k = f / 1000f
            if (k == k.toInt().toFloat()) "${k.toInt()} kHz" else "%.1f kHz".format(k)
        } else {
            "${f.toInt()} Hz"
        }
    }

    fun freqOf(index: Int): Float = FREQ.getOrElse(index) { 1000f }
}
