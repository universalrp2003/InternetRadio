package com.universalrp.pulseeq.audio

import android.content.Context
import android.content.SharedPreferences

/**
 * Everything PulseEQ remembers, in one small preferences file.
 * SharedPreferences (not DataStore) on purpose: the foreground service reads this
 * synchronously while starting up, before any coroutine could be collected.
 */
class EqSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("pulseeq", Context.MODE_PRIVATE)

    var gains: List<Float>
        get() = decodeGains(prefs.getString(KEY_GAINS, null))
        set(value) = prefs.edit().putString(KEY_GAINS, encodeGains(value)).apply()

    var presetName: String
        get() = prefs.getString(KEY_PRESET, Presets.FLAT.name) ?: Presets.FLAT.name
        set(value) = prefs.edit().putString(KEY_PRESET, value).apply()

    var preampDb: Float
        get() = prefs.getFloat(KEY_PREAMP, 0f)
        set(value) = prefs.edit().putFloat(KEY_PREAMP, value).apply()

    var bypass: Boolean
        get() = prefs.getBoolean(KEY_BYPASS, false)
        set(value) = prefs.edit().putBoolean(KEY_BYPASS, value).apply()

    /** User wants the service (and therefore the system effect) to stay alive. */
    var serviceWanted: Boolean
        get() = prefs.getBoolean(KEY_SERVICE, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE, value).apply()

    /** Try to attach to the global output mix on start-up. */
    var systemEqWanted: Boolean
        get() = prefs.getBoolean(KEY_SYSTEM_EQ, false)
        set(value) = prefs.edit().putBoolean(KEY_SYSTEM_EQ, value).apply()

    /** Attach to sessions announced by other players. */
    var cooperating: Boolean
        get() = prefs.getBoolean(KEY_COOPERATING, true)
        set(value) = prefs.edit().putBoolean(KEY_COOPERATING, value).apply()

    var ledAnimation: Boolean
        get() = prefs.getBoolean(KEY_LED, true)
        set(value) = prefs.edit().putBoolean(KEY_LED, value).apply()

    /** 0.35 .. 1.0 multiplier for the LED brightness. */
    var ledBrightness: Float
        get() = prefs.getFloat(KEY_LED_BRIGHTNESS, 1f)
        set(value) = prefs.edit().putFloat(KEY_LED_BRIGHTNESS, value.coerceIn(0.35f, 1f)).apply()

    /** Last played track, so the player can resume the same file. */
    var lastUri: String?
        get() = prefs.getString(KEY_LAST_URI, null)
        set(value) = prefs.edit().putString(KEY_LAST_URI, value).apply()

    var lastTitle: String?
        get() = prefs.getString(KEY_LAST_TITLE, null)
        set(value) = prefs.edit().putString(KEY_LAST_TITLE, value).apply()

    // ------------------------------------------------------------------ codec

    private fun encodeGains(values: List<Float>): String =
        values.joinToString(",") { "%.2f".format(it) }

    private fun decodeGains(raw: String?): List<Float> {
        if (raw.isNullOrBlank()) return List(Bands.COUNT) { 0f }
        val parts = raw.split(',')
            .mapNotNull { it.trim().toFloatOrNull() }
            .map { it.coerceIn(Bands.MIN_DB, Bands.MAX_DB) }
        if (parts.isEmpty()) return List(Bands.COUNT) { 0f }
        return if (parts.size >= Bands.COUNT) parts.take(Bands.COUNT)
        else parts + List(Bands.COUNT - parts.size) { 0f }
    }

    private companion object {
        const val KEY_GAINS = "gains"
        const val KEY_PRESET = "preset"
        const val KEY_PREAMP = "preamp"
        const val KEY_BYPASS = "bypass"
        const val KEY_SERVICE = "service_wanted"
        const val KEY_SYSTEM_EQ = "system_eq"
        const val KEY_COOPERATING = "cooperating"
        const val KEY_LED = "led_animation"
        const val KEY_LED_BRIGHTNESS = "led_brightness"
        const val KEY_LAST_URI = "last_uri"
        const val KEY_LAST_TITLE = "last_title"
    }
}
