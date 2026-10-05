package com.universalrp.tamilnadufm.audio

import android.content.Context
import androidx.annotation.RequiresApi
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.util.Log
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

/**
 * Everything that makes the sound better, attached to **our own** playback.
 *
 * CleanSound works on the 10-band EQ and then layers the extras:
 *
 *  - **Rumble cut** rolls off the sub-bass that FM compression and phone speakers
 *    turn into mush (and that studio mics pick up from traffic and footsteps).
 *  - **Hiss cut** trims the top octave that low-bitrate streams fill with hiss.
 *  - **Clear sound** adds gentle multiband compression plus a limiter, so quiet
 *    parts stay audible and loud parts (ads, jingles) stop distorting.
 *  - **Volume boost** is a loudness enhancer, kept modest on purpose.
 *
 * The chain is built on `DynamicsProcessing` (API 28+) which gives true 10-band EQ,
 * compression and limiting; on older phones it degrades to the classic
 * `Equalizer` + `BassBoost` + `LoudnessEnhancer` effects. Everything here is a
 * standard Android audio effect on a session we own, so no root and no hacks.
 */
object AudioFx {

    private const val TAG = "TamilnaduFM"
    private const val PREFS = "tamilnadufm_audio"

    data class Settings(
        val enabled: Boolean = true,
        val gains: List<Float> = List(Bands.COUNT) { 0f },
        val presetName: String = EqPresets.FLAT.name,
        val preampDb: Float = 0f,
        val clearSound: Boolean = true,
        val rumbleCut: Boolean = true,
        val hissCut: Boolean = false,
        val boostDb: Float = 0f,
    )

    @Volatile
    var settings: Settings = Settings()
        private set

    @Volatile
    private var context: Context? = null

    @Volatile
    private var dynamic: DynamicsProcessing? = null

    @Volatile
    private var equalizer: Equalizer? = null

    @Volatile
    private var bassBoost: BassBoost? = null

    @Volatile
    private var loudness: LoudnessEnhancer? = null

    @Volatile
    var engineName: String = "not attached"
        private set

    /** The curve actually sent to the effects: user gains + noise trimming. */
    fun effectiveGains(): List<Float> {
        val s = settings
        return s.gains.mapIndexed { index, gain ->
            var value = gain
            if (s.rumbleCut && index <= 1) value -= 6f
            if (s.hissCut && index >= Bands.COUNT - 1) value -= 5f
            if (s.hissCut && index == Bands.COUNT - 2) value -= 2f
            value.coerceIn(Bands.MIN_DB, Bands.MAX_DB)
        }
    }

    // --------------------------------------------------------------- lifecycle

    fun load(context: Context) {
        this.context = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        settings = Settings(
            enabled = prefs.getBoolean("enabled", true),
            gains = decode(prefs.getString("gains", null)),
            presetName = prefs.getString("preset", EqPresets.FLAT.name) ?: EqPresets.FLAT.name,
            preampDb = prefs.getFloat("preamp", 0f),
            clearSound = prefs.getBoolean("clear_sound", true),
            rumbleCut = prefs.getBoolean("rumble_cut", true),
            hissCut = prefs.getBoolean("hiss_cut", false),
            boostDb = prefs.getFloat("boost", 0f),
        )
    }

    /** Called by the playback service whenever the player's audio session changes. */
    fun attach(sessionId: Int) {
        if (context == null) return
        if (sessionId <= 0) return
        releaseEffects()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && attachDynamics(sessionId)) {
            engineName = "10-band EQ + limiter (device engine)"
            applyToEffects()
            return
        }
        if (attachLegacy(sessionId)) {
            engineName = "5-band EQ + bass + loudness (older device engine)"
        } else {
            engineName = "no effect engine available on this phone"
        }
        applyToEffects()
    }

    fun release() {
        releaseEffects()
        engineName = "not attached"
    }

    private fun releaseEffects() {
        runCatching { dynamic?.enabled = false; dynamic?.release() }
        runCatching { equalizer?.enabled = false; equalizer?.release() }
        runCatching { bassBoost?.enabled = false; bassBoost?.release() }
        runCatching { loudness?.enabled = false; loudness?.release() }
        dynamic = null
        equalizer = null
        bassBoost = null
        loudness = null
    }

    // ------------------------------------------------------- modern chain (API 28+)

    @RequiresApi(Build.VERSION_CODES.P)
    private fun attachDynamics(sessionId: Int): Boolean = try {
        // channelCount, sessionId, preEq(+10 bands), mbc(+3 bands), postEq, limiter
        val dp = DynamicsProcessing(
            2,
            sessionId,
            true,
            Bands.COUNT,
            true,
            3,
            false,
            true,
        )
        dynamic = dp
        dp.enabled = false
        true
    } catch (t: Throwable) {
        Log.w(TAG, "DynamicsProcessing unavailable: ${t.javaClass.simpleName}")
        dynamic = null
        false
    }

    // ------------------------------------------------------ legacy chain (API < 28)

    private fun attachLegacy(sessionId: Int): Boolean {
        var ok = false
        try {
            equalizer = Equalizer(0, sessionId).also { it.enabled = false }
            ok = true
        } catch (t: Throwable) {
            Log.w(TAG, "Equalizer unavailable: ${t.javaClass.simpleName}")
        }
        try {
            bassBoost = BassBoost(0, sessionId).also { it.enabled = false }
        } catch (t: Throwable) {
            // Optional.
        }
        try {
            loudness = LoudnessEnhancer(sessionId).also { it.enabled = false }
        } catch (t: Throwable) {
            // Optional.
        }
        return ok
    }

    // -------------------------------------------------------------- application

    private fun applyToEffects() {
        val s = settings
        val gains = effectiveGains()
        val preamp = s.preampDb

        runCatching { applyDynamics(gains, preamp, s) }
        runCatching { applyLegacy(gains, preamp, s) }

        runCatching {
            if (s.boostDb > 0.05f && s.enabled) {
                loudness?.setTargetGain((s.boostDb * 100f).toInt())
                loudness?.enabled = true
            } else {
                loudness?.enabled = false
            }
        }
    }

    private fun applyDynamics(gains: List<Float>, preamp: Float, s: Settings): Boolean {
        val dp = dynamic ?: return false
        val config = dp.config ?: return false

        // ---- limiter: the thing that stops loud ads from shredding the sound
        config.isLimiterInUse = true
        config.limiter?.let { limiter ->
            limiter.isEnabled = true
            limiter.attackTime = 5f
            limiter.releaseTime = 80f
            limiter.ratio = 4f
            limiter.threshold = -3f
            limiter.postGain = 0f
        }

        // ---- multiband compressor: gentle, only when Clear sound is on
        for (channelIndex in 0 until config.channelCount) {
            val channel = config.getChannelByChannelIndex(channelIndex)

            channel.eq?.let { eq ->
                eq.isEnabled = s.enabled
                val bands = eq.bandCount
                for (bandIndex in 0 until bands) {
                    val band = eq.getBandByBandIndex(bandIndex) ?: continue
                    val centreHz = band.cutoffFrequency
                    band.isEnabled = true
                    band.gain = (interpolate(centreHz, gains) + preamp).coerceIn(-12f, 12f)
                }
            }

            channel.mbc?.let { mbc ->
                mbc.isEnabled = s.clearSound
                for (bandIndex in 0 until mbc.bandCount) {
                    val band = mbc.getBandByBandIndex(bandIndex) ?: continue
                    band.isEnabled = s.clearSound
                    band.attackTime = 15f
                    band.releaseTime = 250f
                    band.ratio = 2.5f
                    // Low bands get more headroom than the vocal band.
                    band.threshold = if (bandIndex == 0) -26f else -20f
                }
            }
        }

        dp.setConfig(config)
        dp.enabled = s.enabled || s.clearSound
        return true
    }

    private fun applyLegacy(gains: List<Float>, preamp: Float, s: Settings) {
        val eq = equalizer
        if (eq != null) {
            val range = eq.bandLevelRange
            val low = range[0].toFloat()
            val high = range[1].toFloat()
            for (index in 0 until eq.numberOfBands.toInt()) {
                val band = index.toShort()
                val centreHz = eq.getCenterFreq(band) / 1000f // milliHz → Hz
                val gainDb = interpolate(centreHz, gains) + preamp
                val milliBel = (gainDb * 100f).coerceIn(low, high)
                eq.setBandLevel(band, milliBel.toInt().toShort())
            }
            eq.enabled = s.enabled
        }
        bassBoost?.let { bb ->
            val bass = gains.getOrElse(0) { 0f } + gains.getOrElse(1) { 0f }
            val strength = ((bass.coerceAtLeast(0f) / 12f) * 1000f).toInt().coerceIn(0, 1000)
            bb.setStrength(strength.toShort())
            bb.enabled = s.enabled && strength > 0
        }
    }

    /** Our 10-band curve, evaluated at any frequency (log-linear between bands). */
    fun interpolate(freqHz: Float, gains: List<Float>): Float {
        val freqs = Bands.FREQ
        if (gains.isEmpty()) return 0f
        if (freqHz <= freqs.first()) return gains.first()
        if (freqHz >= freqs.last()) return gains.last()
        for (i in 0 until freqs.size - 1) {
            if (freqHz >= freqs[i] && freqHz <= freqs[i + 1]) {
                val low = freqs[i]
                val high = freqs[i + 1]
                val t = (ln(freqHz / low) / ln(high / low)).coerceIn(0f, 1f)
                return gains.getOrElse(i) { 0f } * (1f - t) + gains.getOrElse(i + 1) { 0f } * t
            }
        }
        return 0f
    }

    // ------------------------------------------------------------------ setters

    private fun persist() {
        val ctx = context ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", settings.enabled)
            .putString("gains", encode(settings.gains))
            .putString("preset", settings.presetName)
            .putFloat("preamp", settings.preampDb)
            .putBoolean("clear_sound", settings.clearSound)
            .putBoolean("rumble_cut", settings.rumbleCut)
            .putBoolean("hiss_cut", settings.hissCut)
            .putFloat("boost", settings.boostDb)
            .apply()
    }

    private fun update(block: (Settings) -> Settings) {
        settings = block(settings)
        persist()
        applyToEffects()
    }

    fun setEnabled(on: Boolean) = update { it.copy(enabled = on) }

    fun setBand(index: Int, gainDb: Float) = update { s ->
        val gains = s.gains.toMutableList()
        if (index in gains.indices) {
            gains[index] = gainDb.coerceIn(Bands.MIN_DB, Bands.MAX_DB)
        }
        val list = gains.toList()
        s.copy(gains = list, presetName = EqPresets.matchName(list), preampDb = autoPreamp(list))
    }

    fun applyPreset(preset: EqPreset) = update {
        it.copy(
            gains = preset.gains.toList(),
            presetName = preset.name,
            preampDb = preset.preampDb,
        )
    }

    fun resetFlat() = update {
        it.copy(
            gains = List(Bands.COUNT) { 0f },
            presetName = EqPresets.FLAT.name,
            preampDb = 0f,
        )
    }

    fun setPreamp(db: Float) = update { it.copy(preampDb = db.coerceIn(-12f, 0f)) }

    fun setClearSound(on: Boolean) = update { it.copy(clearSound = on) }

    fun setRumbleCut(on: Boolean) = update { it.copy(rumbleCut = on) }

    fun setHissCut(on: Boolean) = update { it.copy(hissCut = on) }

    fun setBoost(db: Float) = update { it.copy(boostDb = db.coerceIn(0f, 10f)) }

    /** Keeps the loudest band at 0 dB — the reason boosts never clip. */
    private fun autoPreamp(gains: List<Float>): Float {
        val max = gains.maxOrNull() ?: 0f
        return (-max).coerceIn(-12f, 0f)
    }

    // -------------------------------------------------------------------- codec

    private fun encode(values: List<Float>): String = values.joinToString(",") { "%.2f".format(it) }

    private fun decode(raw: String?): List<Float> {
        if (raw.isNullOrBlank()) return List(Bands.COUNT) { 0f }
        val parsed = raw.split(',').mapNotNull { it.trim().toFloatOrNull() }
        if (parsed.isEmpty()) return List(Bands.COUNT) { 0f }
        val padded = if (parsed.size >= Bands.COUNT) parsed.take(Bands.COUNT)
        else parsed + List(Bands.COUNT - parsed.size) { 0f }
        return padded.map { it.coerceIn(Bands.MIN_DB, Bands.MAX_DB) }
    }

    /** True when the curve is doing nothing at all — used for the UI badge. */
    fun isTransparent(): Boolean =
        !settings.enabled && !settings.clearSound && abs(settings.boostDb) < 0.05f
}
