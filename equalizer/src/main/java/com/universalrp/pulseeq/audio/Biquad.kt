package com.universalrp.pulseeq.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * A peaking EQ biquad (RBJ audio cookbook), with independent state for the left
 * and right channel so one object filters a stereo pair.
 *
 * Every one of PulseEQ's 20 bands is one of these; the audio thread runs them in
 * a tight loop. Gain is in dB — positive boosts, negative cuts, 0 is transparent.
 */
class PeakingBiquad(
    var freqHz: Float,
    var gainDb: Float = 0f,
    var q: Float = Bands.Q,
    sampleRate: Float = 48000f,
) {
    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f

    private var x1l = 0f
    private var x2l = 0f
    private var y1l = 0f
    private var y2l = 0f

    private var x1r = 0f
    private var x2r = 0f
    private var y1r = 0f
    private var y2r = 0f

    init {
        design(sampleRate)
    }

    /** Recomputes the coefficients. Cheap enough to call while a fader drags. */
    fun design(sampleRate: Float = 48000f, freq: Float = freqHz, gain: Float = gainDb, sharpness: Float = q) {
        freqHz = freq
        gainDb = gain
        q = sharpness
        // Never let a band sit above Nyquist; that would blow the filter up.
        val f = freq.coerceIn(15f, sampleRate * 0.45f)
        val a = 10f.pow(gain / 40f)
        val w0 = (2.0 * PI * (f / sampleRate)).toFloat()
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2f * sharpness.coerceAtLeast(0.05f))
        val a0 = 1f + alpha / a

        b0 = (1f + alpha * a) / a0
        b1 = (-2f * cosW0) / a0
        b2 = (1f - alpha * a) / a0
        a1 = (-2f * cosW0) / a0
        a2 = (1f - alpha / a) / a0
    }

    fun processLeft(x: Float): Float {
        val y = b0 * x + b1 * x1l + b2 * x2l - a1 * y1l - a2 * y2l
        x2l = x1l
        x1l = x
        y2l = y1l
        y1l = y
        return y
    }

    fun processRight(x: Float): Float {
        val y = b0 * x + b1 * x1r + b2 * x2r - a1 * y1r - a2 * y2r
        x2r = x1r
        x1r = x
        y2r = y1r
        y1r = y
        return y
    }

    fun reset() {
        x1l = 0f; x2l = 0f; y1l = 0f; y2l = 0f
        x1r = 0f; x2r = 0f; y1r = 0f; y2r = 0f
    }

    /** True when this band is doing nothing (used to skip work entirely). */
    val isTransparent: Boolean get() = gainDb > -0.05f && gainDb < 0.05f
}

/**
 * A simple band-pass filter, used only for the LED spectrum: one per band, fed
 * the mono down-mix, so the lights show the real energy in each frequency range.
 */
class BandPassFilter(freqHz: Float, q: Float = 1.3f, sampleRate: Float = 48000f) {

    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f

    private var x1 = 0f
    private var x2 = 0f
    private var y1 = 0f
    private var y2 = 0f

    fun design(freq: Float, sharpness: Float, rate: Float) {
        val f = freq.coerceIn(15f, rate * 0.45f)
        val w0 = (2.0 * PI * (f / rate)).toFloat()
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2f * sharpness.coerceAtLeast(0.05f))
        val a0 = 1f + alpha

        b0 = alpha / a0
        b1 = 0f
        b2 = (-alpha) / a0
        a1 = (-2f * cosW0) / a0
        a2 = (1f - alpha) / a0
    }

    init {
        design(freqHz, q, sampleRate)
    }

    fun process(x: Float): Float {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = x
        y2 = y1
        y1 = y
        return y
    }
}
