package com.universalrp.pulseeq.audio

import android.content.Context

/**
 * The single place where PulseEQ's state lives.
 *
 * The foreground service, the player, the session receiver and the UI all talk to
 * these objects, so there is exactly one equalizer curve and one audio engine no
 * matter how many parts of the app are alive.
 */
object EqCore {

    @Volatile
    private var settings: EqSettings? = null

    @Volatile
    private var engine: EqEngine? = null

    @Volatile
    private var player: PlayerEngine? = null

    val eq: EqEngine
        get() = engine ?: synchronized(this) {
            engine ?: EqEngine().also { engine = it }
        }

    fun settings(context: Context): EqSettings =
        settings ?: synchronized(this) {
            settings ?: EqSettings(context).also {
                settings = it
                // Push whatever was saved last time into the DSP.
                eq.setCurve(it.gains, it.preampDb)
                eq.bypass = it.bypass
            }
        }

    fun player(context: Context): PlayerEngine =
        player ?: synchronized(this) {
            player ?: PlayerEngine(context.applicationContext, eq).also { player = it }
        }

    /** Applies gains/preamp everywhere: DSP, saved settings, device effects. */
    fun applyCurve(context: Context, gains: List<Float>, preampDb: Float) {
        val s = settings(context)
        s.gains = gains
        s.preampDb = preampDb
        s.presetName = Presets.matchName(gains)
        eq.setCurve(gains, preampDb)
        DeviceEq.applyCurve(gains, preampDb, s.bypass)
    }

    fun setBypass(context: Context, bypass: Boolean) {
        val s = settings(context)
        s.bypass = bypass
        eq.bypass = bypass
        DeviceEq.applyCurve(s.gains, s.preampDb, bypass)
    }

    fun applyPreset(context: Context, preset: EqPreset) {
        applyCurve(context, preset.gains.toList(), preset.preampDb)
    }

    /** Called by the service when the user enables the global effect. */
    fun startGlobal(): Boolean {
        val s = settings ?: return false
        val ok = DeviceEq.attachGlobal()
        DeviceEq.applyCurve(s.gains, s.preampDb, s.bypass)
        return ok
    }

    fun stopGlobal() {
        DeviceEq.releaseGlobal()
    }

    fun cooperatingEnabled(): Boolean = settings?.cooperating ?: true

    fun onForeignSessionOpened(sessionId: Int) {
        val s = settings ?: return
        DeviceEq.attachSession(sessionId)
        DeviceEq.applyCurve(s.gains, s.preampDb, s.bypass)
    }

    fun onForeignSessionClosed(sessionId: Int) {
        DeviceEq.detachSession(sessionId)
    }

    /** Fresh curve + coefficients for a new playback session. */
    fun refreshFromSettings(context: Context) {
        val s = settings(context)
        eq.setCurve(s.gains, s.preampDb)
        eq.bypass = s.bypass
        DeviceEq.applyCurve(s.gains, s.preampDb, s.bypass)
    }
}
