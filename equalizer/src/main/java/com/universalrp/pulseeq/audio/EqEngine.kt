package com.universalrp.pulseeq.audio

import java.util.concurrent.atomic.AtomicReference
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * PulseEQ's own 20-band equalizer.
 *
 * Curve edits are published as immutable snapshots. The audio thread installs one complete
 * snapshot at the start of a PCM block, so it never sees half-updated coefficients, a mixed
 * left/right curve, or an active-band list from a different fader edit. UI curve edits do not
 * mutate filter state and do not wait for the audio thread.
 *
 * Filters, analysers and their delay/energy state are owned by the processing thread. Methods
 * that reset or reconfigure that state synchronize with [process]; ordinary curve edits only
 * touch the atomic configuration reference.
 */
class EqEngine {

    private class CurveConfig(
        val gains: FloatArray,
        val preampDb: Float,
        val preampGain: Float,
        val bypass: Boolean,
        val sampleRate: Float,
        val activeIndices: IntArray,
    )

    private val filters = Array(Bands.COUNT) { i ->
        PeakingBiquad(Bands.FREQ[i], 0f, Bands.Q, 48000f)
    }
    private val analysers = Array(Bands.COUNT) { i ->
        BandPassFilter(Bands.FREQ[i], 1.3f, 48000f)
    }

    private val requestedConfig = AtomicReference(
        makeConfig(FloatArray(Bands.COUNT), 0f, false, 48000f),
    )

    /** Only the processing/configuration thread reads or writes these applied fields. */
    private var appliedConfig: CurveConfig? = null
    private var activeIndices = IntArray(0)
    private var preampGain = 1f
    private var activeBypass = false

    /** Smoothed 0..1 level per band, for the LED spectrum. Written on the audio thread. */
    val levels = FloatArray(Bands.COUNT)

    /** Slowly-falling peak markers, also for the LEDs. */
    val peaks = FloatArray(Bands.COUNT)

    private val energy = FloatArray(Bands.COUNT)
    private var energyFrames = 0

    val preampDb: Float get() = requestedConfig.get().preampDb

    /** New bypass choice becomes visible to the UI immediately and is applied next PCM block. */
    var bypass: Boolean
        get() = requestedConfig.get().bypass
        set(value) {
            while (true) {
                val current = requestedConfig.get()
                if (current.bypass == value) return
                val next = makeConfig(current.gains, current.preampDb, value, current.sampleRate)
                if (requestedConfig.compareAndSet(current, next)) return
            }
        }

    /**
     * Reconfigures sample-rate-dependent analysis safely against the processing block. The
     * configuration CAS carries the latest curve/bypass forward if a fader edit races this call.
     */
    @Synchronized
    fun configure(sampleRateHz: Int) {
        val rate = sampleRateHz.toFloat().takeIf { it.isFinite() && it >= 8000f } ?: 8000f
        while (true) {
            val current = requestedConfig.get()
            val next = makeConfig(current.gains, current.preampDb, current.bypass, rate)
            if (requestedConfig.compareAndSet(current, next)) break
        }
        EqStats.currentSampleRate = rate.toInt()
        for (i in 0 until Bands.COUNT) {
            analysers[i].design(Bands.FREQ[i], 1.3f, rate)
            analysers[i].reset()
            filters[i].reset()
        }
        appliedConfig = null
        energyFrames = 0
        java.util.Arrays.fill(energy, 0f)
        java.util.Arrays.fill(levels, 0f)
        java.util.Arrays.fill(peaks, 0f)
    }

    /**
     * Publishes all 20 gains and the preamp as one immutable update. Filter coefficients are
     * installed at the next audio-block boundary, never while [process] is using them.
     */
    fun setCurve(newGains: List<Float>, preamp: Float) {
        val safeGains = FloatArray(Bands.COUNT) { index ->
            newGains.getOrNull(index)?.takeIf { it.isFinite() }?.coerceIn(Bands.MIN_DB, Bands.MAX_DB) ?: 0f
        }
        val safePreamp = preamp.takeIf { it.isFinite() }?.coerceIn(-24f, 12f) ?: 0f
        while (true) {
            val current = requestedConfig.get()
            val next = makeConfig(safeGains, safePreamp, current.bypass, current.sampleRate)
            if (requestedConfig.compareAndSet(current, next)) return
        }
    }

    private fun makeConfig(gains: FloatArray, preampDb: Float, bypass: Boolean, sampleRate: Float): CurveConfig {
        val safeGains = FloatArray(Bands.COUNT) { index ->
            gains.getOrNull(index)?.takeIf { it.isFinite() }?.coerceIn(Bands.MIN_DB, Bands.MAX_DB) ?: 0f
        }
        val safePreamp = preampDb.takeIf { it.isFinite() }?.coerceIn(-24f, 12f) ?: 0f
        val active = IntArray(Bands.COUNT)
        var count = 0
        for (i in safeGains.indices) {
            val gain = safeGains[i]
            if (!(gain > -0.05f && gain < 0.05f)) active[count++] = i
        }
        return CurveConfig(
            gains = safeGains,
            preampDb = safePreamp,
            preampGain = 10f.pow(safePreamp / 20f),
            bypass = bypass,
            sampleRate = sampleRate,
            activeIndices = active.copyOf(count),
        )
    }

    /** Called only at a synchronized audio-block boundary. */
    private fun applyPendingConfig() {
        val config = requestedConfig.get()
        if (config === appliedConfig) return
        for (i in 0 until Bands.COUNT) {
            filters[i].design(config.sampleRate, Bands.FREQ[i], config.gains[i], Bands.Q)
        }
        activeIndices = config.activeIndices
        preampGain = config.preampGain
        activeBypass = config.bypass
        appliedConfig = config
    }

    fun gainOf(index: Int): Float = requestedConfig.get().gains.getOrElse(index) { 0f }

    @Synchronized
    fun resetState() {
        filters.forEach { it.reset() }
    }

    /** Both channels silent and filters cleared — called when playback restarts. */
    @Synchronized
    fun flush() {
        filters.forEach { it.reset() }
        analysers.forEach { it.reset() }
        java.util.Arrays.fill(levels, 0f)
        java.util.Arrays.fill(peaks, 0f)
        java.util.Arrays.fill(energy, 0f)
        energyFrames = 0
    }

    /**
     * Processes interleaved stereo float samples in place.
     * [frames] is the number of stereo frames (buffer length / 2).
     */
    @Synchronized
    fun process(buffer: FloatArray, frames: Int) {
        if (frames <= 0) return
        require(frames <= buffer.size / 2) { "PCM buffer is shorter than the requested frame count" }
        applyPendingConfig()
        if (!activeBypass) {
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
