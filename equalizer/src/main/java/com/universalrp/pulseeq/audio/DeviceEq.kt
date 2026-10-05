package com.universalrp.pulseeq.audio

import android.media.audiofx.Equalizer
import kotlin.math.abs
import kotlin.math.ln

/**
 * Talks to the audio effects that are built into Android.
 *
 * Two things live here:
 *
 * 1. **The global output mix** (`session 0`) — one Equalizer for the whole phone.
 *    Google deprecated this and most Android 11+ ROMs refuse it for normal apps,
 *    but many OEM builds (and older Androids) still allow it. We try, and we tell
 *    the user exactly what happened instead of pretending.
 *
 * 2. **Cooperating apps** — players such as Poweramp, VLC or Musicolet announce
 *    their audio session with `OPEN_AUDIO_EFFECT_CONTROL_SESSION`, which lets us
 *    attach an equalizer to *their* audio. No capture, no root, no doubling.
 *
 * In both cases we can only drive the bands the device's own equalizer engine
 * provides (often 5). PulseEQ's 20 faders are interpolated onto them — the
 * built-in player uses PulseEQ's full 20-band DSP instead.
 */
object DeviceEq {

    private var globalEq: Equalizer? = null
    private val sessionEqs = HashMap<Int, Equalizer>()

    @Volatile
    var globalActive: Boolean = false
        private set

    @Volatile
    var status: String = "Not started yet"
        private set

    @Volatile
    var deviceBandInfo: String = "Unknown"
        private set

    /** Sessions offered to us by other players, for the UI to show. */
    @Volatile
    var cooperatingSessions: Int = 0
        private set

    @Volatile
    private var currentGains: List<Float> = List(Bands.COUNT) { 0f }

    @Volatile
    private var currentPreamp: Float = 0f

    @Volatile
    private var currentBypass: Boolean = false

    // ------------------------------------------------------------------ global

    /** Tries to attach one equalizer to the whole output mix. Never throws. */
    fun attachGlobal(): Boolean {
        releaseGlobal()
        return try {
            val eq = Equalizer(0, 0)
            eq.enabled = false
            globalEq = eq
            globalActive = true
            deviceBandInfo = describe(eq)
            status = "Attached to the global output mix — every app is equalized"
            applyTo(eq)
            true
        } catch (t: Throwable) {
            globalActive = false
            status = "This ROM blocks system-wide effects (${t.javaClass.simpleName}" +
                (t.message?.let { ": $it" } ?: "") + ")"
            false
        }
    }

    fun releaseGlobal() {
        try {
            globalEq?.enabled = false
        } catch (t: Throwable) {
            // Ignored — the effect may already be gone.
        }
        try {
            globalEq?.release()
        } catch (t: Throwable) {
            // Ignored.
        }
        globalEq = null
        globalActive = false
    }

    // -------------------------------------------------------------- sessions

    /** Called when a player announces an audio session. */
    fun attachSession(sessionId: Int) {
        if (sessionId <= 0 || sessionEqs.containsKey(sessionId)) return
        try {
            val eq = Equalizer(0, sessionId)
            eq.enabled = false
            sessionEqs[sessionId] = eq
            applyTo(eq)
            cooperatingSessions = sessionEqs.size
            status = "Equalizing ${sessionEqs.size} cooperating player session(s)"
        } catch (t: Throwable) {
            status = "Could not attach to session $sessionId (${t.javaClass.simpleName})"
        }
    }

    fun detachSession(sessionId: Int) {
        val eq = sessionEqs.remove(sessionId) ?: return
        try {
            eq.enabled = false
        } catch (t: Throwable) {
            // Ignored.
        }
        try {
            eq.release()
        } catch (t: Throwable) {
            // Ignored.
        }
        cooperatingSessions = sessionEqs.size
    }

    fun releaseAllSessions() {
        val ids = sessionEqs.keys.toList()
        ids.forEach { detachSession(it) }
    }

    fun releaseEverything() {
        releaseGlobal()
        releaseAllSessions()
        cooperatingSessions = 0
    }

    // ------------------------------------------------------------------ apply

    /** Pushes the current curve into every attached effect. Safe to call often. */
    fun applyCurve(gains: List<Float>, preampDb: Float, bypass: Boolean) {
        currentGains = gains
        currentPreamp = preampDb
        currentBypass = bypass
        globalEq?.let { applyTo(it) }
        sessionEqs.values.forEach { applyTo(it) }
    }

    private fun applyTo(eq: Equalizer) {
        try {
            val bands = eq.numberOfBands.toInt()
            if (bands <= 0) return
            val range = eq.bandLevelRange
            val lo = range[0].toFloat()
            val hi = range[1].toFloat()

            for (b in 0 until bands) {
                val centerHz = eq.getCenterFreq(b.toShort()) / 1000f
                val gainDb = interpolateGain(centerHz, currentGains) + currentPreamp
                val milliBel = (gainDb * 100f).coerceIn(lo, hi)
                eq.setBandLevel(b.toShort(), milliBel.toInt().toShort())
            }
            eq.enabled = !currentBypass
        } catch (t: Throwable) {
            status = "Effect rejected the curve (${t.javaClass.simpleName})"
        }
    }

    /**
     * Our 20-band curve, evaluated at an arbitrary frequency: linear between the
     * two neighbouring band centres, measured on a log frequency axis.
     */
    fun interpolateGain(freqHz: Float, gains: List<Float>): Float {
        if (gains.isEmpty()) return 0f
        val f = Bands.FREQ
        if (freqHz <= f.first()) return gains.first()
        if (freqHz >= f.last()) return gains.last()
        for (i in 0 until f.size - 1) {
            val low = f[i]
            val high = f[i + 1]
            if (freqHz >= low && freqHz <= high) {
                val t = (ln(freqHz / low) / ln(high / low)).coerceIn(0f, 1f)
                return gains.getOrElse(i) { 0f } * (1f - t) + gains.getOrElse(i + 1) { 0f } * t
            }
        }
        return 0f
    }

    /** Human-readable description of what the device engine actually offers. */
    private fun describe(eq: Equalizer): String {
        return try {
            val bands = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val centres = (0 until bands).joinToString(", ") { b ->
                val hz = eq.getCenterFreq(b.toShort()) / 1000f
                if (hz >= 1000f) {
                    val k = hz / 1000f
                    if (abs(k - k.toInt()) < 0.05f) "${k.toInt()}k" else "%.1fk".format(k)
                } else {
                    hz.toInt().toString()
                }
            }
            "Device engine: $bands bands ($centres Hz), range ${range[0] / 100f}..${range[1] / 100f} dB"
        } catch (t: Throwable) {
            "Device engine: not readable (${t.javaClass.simpleName})"
        }
    }
}
