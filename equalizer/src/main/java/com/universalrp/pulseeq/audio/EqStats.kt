package com.universalrp.pulseeq.audio

/** Live numbers the UI shows in diagnostics. Written by the audio thread. */
object EqStats {
    @Volatile
    var currentSampleRate: Int = 0

    /** Frames processed since the app started — proof the DSP is actually running. */
    @Volatile
    var framesProcessed: Long = 0L
}

/**
 * Very small UI sound effects, built from Android's tone generator so PulseEQ
 * ships no audio assets at all.
 */
object Sfx {

    @Volatile
    var enabled: Boolean = true

    fun tick(context: android.content.Context) {
        if (!enabled) return
        runCatching {
            val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 35)
            tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 45)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                runCatching { tg.release() }
            }, 160)
        }
    }
}
