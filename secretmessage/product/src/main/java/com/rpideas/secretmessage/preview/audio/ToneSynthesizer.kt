package com.rpideas.secretmessage.preview.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Pure Kotlin 16-bit mono PCM synthesis for the six built-in tones (adapted from the WhisperPad
 * SoundSynthesizer). Oscillators accumulate phase so sweeps and vibrato stay at their intended
 * pitch, and every tone/note fades in and out to avoid clicks. No files or permissions are needed.
 */
object ToneSynthesizer {
    const val SAMPLE_RATE = 44_100
    private const val TWO_PI = 2.0 * PI
    private const val ATTACK_SECONDS = 0.004
    private const val RELEASE_SECONDS = 0.012

    fun durationSeconds(sound: BuiltInNotificationSound): Double = when (sound) {
        BuiltInNotificationSound.CRYSTAL_PULSE -> 0.55
        BuiltInNotificationSound.CYBER_PING -> 0.42
        BuiltInNotificationSound.ZEN_DROP -> 0.65
        BuiltInNotificationSound.STEALTH_TAP -> 0.32
        BuiltInNotificationSound.AURORA_CHORD -> 0.60
        BuiltInNotificationSound.QUANTUM_SHIMMER -> 0.55
    }

    fun synthesize(sound: BuiltInNotificationSound): ShortArray {
        val duration = durationSeconds(sound)
        return when (sound) {
            BuiltInNotificationSound.CRYSTAL_PULSE -> {
                val low = Oscillator()
                val high = Oscillator()
                render(duration) { t -> (0.65 * low.next(880.0) + 0.35 * high.next(1318.5)) * exp(-6.5 * t) * 0.8 }
            }
            BuiltInNotificationSound.CYBER_PING -> {
                val sweep = Oscillator()
                render(duration) { t -> sweep.next(1046.5 + 522.0 * (t / duration)) * exp(-5.5 * t) * 0.75 }
            }
            BuiltInNotificationSound.ZEN_DROP -> {
                val base = Oscillator()
                val octave = Oscillator()
                render(duration) { t -> (0.75 * base.next(528.0) + 0.25 * octave.next(1056.0)) * exp(-5.0 * t) * 0.85 }
            }
            BuiltInNotificationSound.STEALTH_TAP -> {
                val pulse = Oscillator()
                render(duration) { t ->
                    val burstStart = when {
                        t < 0.07 -> 0.0
                        t >= 0.14 && t < 0.21 -> 0.14
                        else -> -1.0
                    }
                    if (burstStart < 0.0) {
                        0.0
                    } else {
                        val local = t - burstStart
                        pulse.next(440.0) * exp(-22.0 * local) * edgeRamp(local, 0.07) * 0.8
                    }
                }
            }
            BuiltInNotificationSound.AURORA_CHORD -> {
                val voice = Oscillator()
                render(duration) { t ->
                    val noteStart: Double
                    val noteLength: Double
                    val frequency: Double
                    when {
                        t < 0.18 -> {
                            noteStart = 0.0
                            noteLength = 0.18
                            frequency = 1046.50
                        }
                        t < 0.36 -> {
                            noteStart = 0.18
                            noteLength = 0.18
                            frequency = 1318.51
                        }
                        else -> {
                            noteStart = 0.36
                            noteLength = duration - 0.36
                            frequency = 1567.98
                        }
                    }
                    val local = t - noteStart
                    voice.next(frequency) * exp(-7.0 * local) * edgeRamp(local, noteLength) * 0.75
                }
            }
            BuiltInNotificationSound.QUANTUM_SHIMMER -> {
                val upper = Oscillator()
                val lower = Oscillator()
                render(duration) { t ->
                    val vibrato = 25.0 * sin(TWO_PI * 18.0 * t)
                    (0.6 * upper.next(987.77 + vibrato) + 0.4 * lower.next(1480.0 - vibrato)) * exp(-4.8 * t) * 0.78
                }
            }
        }
    }

    private class Oscillator {
        private var phase = 0.0

        fun next(frequencyHz: Double): Double {
            val value = sin(phase)
            phase += TWO_PI * frequencyHz / SAMPLE_RATE
            if (phase >= TWO_PI) phase -= TWO_PI
            return value
        }
    }

    private inline fun render(durationSeconds: Double, sample: (Double) -> Double): ShortArray {
        val total = (SAMPLE_RATE * durationSeconds).roundToInt()
        val output = ShortArray(total)
        for (index in 0 until total) {
            val t = index.toDouble() / SAMPLE_RATE
            output[index] = toPcm(sample(t) * edgeRamp(t, durationSeconds))
        }
        return output
    }

    /** Linear fade-in over [ATTACK_SECONDS] and fade-out over the last [RELEASE_SECONDS]. */
    private fun edgeRamp(t: Double, length: Double): Double {
        val attack = (t / ATTACK_SECONDS).coerceIn(0.0, 1.0)
        val release = ((length - t) / RELEASE_SECONDS).coerceIn(0.0, 1.0)
        return attack * release
    }

    private fun toPcm(value: Double): Short =
        (value * Short.MAX_VALUE).roundToInt().coerceIn(-Short.MAX_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
}
