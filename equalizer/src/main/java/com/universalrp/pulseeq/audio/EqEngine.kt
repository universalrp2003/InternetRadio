package com.universalrp.pulseeq.audio

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * PulseEQ's own 20-band equalizer.
 *
 * It runs on the audio thread: 20 peaking biquads per channel, a preamp, a soft
 * limiter so boosted curves never clip, and a second bank of band-pass filters
 * that feeds the LED spectrum with the real energy of each band.
 *
 * Bands whose gain is zero are skipped completely, so "Flat" costs almost nothing.
 */
class EqEngine {

    private var sampleRate = 48000f

    private val filters = Array(Bands.COUNT) { i ->
        PeakingBiquad(Bands.FREQ[i], 0f, Bands.Q, sampleRate)
    }
    private val analysers = Array(Bands.COUNT) { i ->
        BandPassFilter(Bands.FREQ[i], 1.3f, sampleRate)
    }

    private val gains = FloatArray(Bands.COUNT)
    private var activeIndices = IntArray(0)

    @Volatile
    var preampDb: Float = 0f
        private set

    @Volatile
    var bypass: Boolean = false

    private var preampGain = 1f

    /** Smoothed 0..1 level per band, for the LED spectrum. Written on the audio thread. */
    val levels = FloatArray(Bands.COUNT)

    /** Slowly-falling peak markers, also for the LEDs. */
    val peaks = FloatArray(Bands.COUNT)

    private val energy = FloatArray(Bands.COUNT)
    private var energyFrames = 0

    // ------------------------------------------------------------------ config

    fun configure(sampleRateHz: Int) {
        sampleRate = sampleRateHz.toFloat().coerceAtLeast(8000f)
        EqStats.currentSampleRate = sampleRateHz
        for (i in 0 until Bands.COUNT) {
            filters[i].design(sampleRate, Bands.FREQ[i], gains[i], Bands.Q)
            analysers[i].design(Bands.FREQ[i], 1.3f, sampleRate)
            filters[i].reset()
        }
        energyFrames = 0
        java.util.Arrays.fill(energy, 0f)
    }

    /** Sets all 20 gains (dB, low → high) and preamp, then refreshes coefficients. */
    fun setCurve(newGains: List<Float>, preamp: Float) {
        val changed = newGains.size == Bands.COUNT &&
            newGains.indices.any { kotlin.math.abs(newGains[it] - gains[it]) > 0.01f }
        for (i in 0 until Bands.COUNT) {
            gains[i] = newGains.getOrElse(i) { 0f }.coerceIn(Bands.MIN_DB, Bands.MAX_DB)
        }
        preampDb = preamp.coerceIn(-24f, 12f)
        preampGain = 10f.pow(preampDb / 20f)
        if (changed || activeIndices.isEmpty()) rebuildActive()
    }

    private fun rebuildActive() {
        val active = ArrayList<Int>(Bands.COUNT)
        for (i in 0 until Bands.COUNT) {
            if (!filters[i].isTransparent) {
                filters[i].design(sampleRate, Bands.FREQ[i], gains[i], Bands.Q)
                active.add(i)
            }
        }
        activeIndices = active.toIntArray()
    }

    fun gainOf(index: Int): Float = gains.getOrElse(index) { 0f }

    fun resetState() {
        filters.forEach { it.reset() }
    }

    /** Both channels silent and filters cleared — called when playback restarts. */
    fun flush() {
        filters.forEach { it.reset() }
        java.util.Arrays.fill(levels, 0f)
        java.util.Arrays.fill(peaks, 0f)
        java.util.Arrays.fill(energy, 0f)
        energyFrames = 0
    }

    // --------------------------------------------------------------- processing

    /**
     * Processes interleaved stereo float samples in place.
     * [frames] is the number of stereo frames (buffer length / 2).
     */
    fun process(buffer: FloatArray, frames: Int) {
        if (frames <= 0) return
        if (!bypass) {
            val active = activeIndices
            val gain = preampGain
            if (active.isNotEmpty() || gain != 1f) {
                var i = 0
                for (n in 0 until frames) {
                    var l = buffer[i]
                    var r = buffer[i + 1]
                    for (k in active) {
                        val f = filters[k]
                        l = f.processLeft(l)
                        r = f.processRight(r)
                    }
                    buffer[i] = softClip(l * gain)
                    buffer[i + 1] = softClip(r * gain)
                    i += 2
                }
            }
        }
        analyse(buffer, frames)
        EqStats.framesProcessed += frames
    }

    /** Feeds the LED spectrum with the real per-band energy of what we just played. */
    private fun analyse(buffer: FloatArray, frames: Int) {
        var i = 0
        for (n in 0 until frames) {
            val mono = 0.5f * (buffer[i] + buffer[i + 1])
            for (k in 0 until Bands.COUNT) {
                val v = analysers[k].process(mono)
                energy[k] += v * v
            }
            i += 2
        }
        energyFrames += frames
        if (energyFrames < 480) return

        val inv = 1f / energyFrames.toFloat()
        for (k in 0 until Bands.COUNT) {
            val rms = sqrt(energy[k] * inv)
            val db = 20f * log10(rms + 1e-7f)
            // -62 dB .. 0 dB mapped onto the LED height.
            val target = ((db + 62f) / 62f).coerceIn(0f, 1f)
            val current = levels[k]
            levels[k] = if (target > current) target else current * 0.74f + target * 0.26f
            peaks[k] = if (levels[k] > peaks[k]) levels[k] else (peaks[k] - 0.012f).coerceAtLeast(0f)
            energy[k] = 0f
        }
        energyFrames = 0
    }

    /**
     * Identity below 0.8, smoothly saturating to ±1.0 above it. This is why a
     * +12 dB bass boost sounds loud rather than broken.
     */
    private fun softClip(x: Float): Float = when {
        x > 0.8f -> 0.8f + 0.2f * tanh((x - 0.8f) / 0.2f)
        x < -0.8f -> -0.8f + 0.2f * tanh((x + 0.8f) / 0.2f)
        else -> x
    }
}
