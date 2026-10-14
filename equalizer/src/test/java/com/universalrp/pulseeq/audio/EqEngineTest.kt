package com.universalrp.pulseeq.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class EqEngineTest {
    @Test fun flatCurvePreservesSamplesBelowTheLimiterKnee() {
        val engine = EqEngine()
        engine.configure(48_000)
        engine.setCurve(List(Bands.COUNT) { 0f }, 0f)
        val input = FloatArray(2048) { index -> 0.6f * sin(index * 0.071).toFloat() }
        val expected = input.copyOf()

        engine.process(input, input.size / 2)

        assertTrue(expected.contentEquals(input))
    }

    @Test fun liveBandChangeIsAppliedAtTheNextBlockAndChangesTheTone() {
        val sampleRate = 48_000
        val flat = EqEngine().apply {
            configure(sampleRate)
            setCurve(List(Bands.COUNT) { 0f }, 0f)
        }
        val boosted = EqEngine().apply {
            configure(sampleRate)
            setCurve(List(Bands.COUNT) { index -> if (index == 10) 12f else 0f }, 0f)
        }
        val flatTone = tone(1_000f, sampleRate, 0.1f, 9_600)
        val boostedTone = flatTone.copyOf()

        flat.process(flatTone, flatTone.size / 2)
        boosted.process(boostedTone, boostedTone.size / 2)

        assertTrue(rms(boostedTone, 2_400 * 2) > rms(flatTone, 2_400 * 2) * 2f)
        assertTrue(boostedTone.all { it.isFinite() })
    }

    @Test fun bypassedCurveLeavesPcmUnchanged() {
        val engine = EqEngine()
        engine.configure(44_100)
        engine.setCurve(List(Bands.COUNT) { if (it == 3) 12f else -8f }, 8f)
        engine.bypass = true
        val input = tone(400f, 44_100, 0.15f, 2_048)
        val expected = input.copyOf()

        engine.process(input, input.size / 2)

        assertTrue(expected.contentEquals(input))
    }

    @Test fun sampleRateReconfigurationClearsStaleAnalyserLevelsAndPeaks() {
        val engine = EqEngine()
        engine.configure(48_000)
        val input = tone(1_000f, 48_000, 0.3f, 2_048)
        engine.process(input, input.size / 2)
        assertTrue(engine.levels.any { it > 0f })
        assertTrue(engine.peaks.any { it > 0f })

        engine.configure(44_100)

        assertTrue(engine.levels.all { it == 0f })
        assertTrue(engine.peaks.all { it == 0f })
    }

    @Test fun racingCurveBypassAndSampleRateUpdatesNeverTearAudioState() {
        val engine = EqEngine()
        engine.configure(48_000)
        val start = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)
        val audioThread = Thread({
            try {
                start.await()
                repeat(1_000) { block ->
                    val buffer = FloatArray(512) { index ->
                        (0.7 * sin((block * 512 + index) * 0.013)).toFloat()
                    }
                    engine.process(buffer, buffer.size / 2)
                    if (buffer.any { !it.isFinite() }) error("DSP produced a non-finite sample")
                }
            } catch (t: Throwable) {
                failure.compareAndSet(null, t)
            }
        }, "eq-test-audio")
        val controlThread = Thread({
            try {
                start.await()
                repeat(600) { revision ->
                    val curve = List(Bands.COUNT) { band ->
                        if (band == revision % Bands.COUNT) (revision % 25 - 12).toFloat() else 0f
                    }
                    engine.setCurve(curve, (revision % 17 - 8).toFloat())
                    engine.bypass = revision % 3 == 0
                    if (revision % 40 == 0) engine.configure(if (revision % 80 == 0) 44_100 else 48_000)
                }
            } catch (t: Throwable) {
                failure.compareAndSet(null, t)
            }
        }, "eq-test-control")

        audioThread.start()
        controlThread.start()
        start.countDown()
        audioThread.join(TimeUnit.SECONDS.toMillis(20))
        controlThread.join(TimeUnit.SECONDS.toMillis(20))

        assertFalse("Audio worker did not finish", audioThread.isAlive)
        assertFalse("Control worker did not finish", controlThread.isAlive)
        assertNull(failure.get())
    }

    private fun tone(frequency: Float, sampleRate: Int, amplitude: Float, frames: Int): FloatArray =
        FloatArray(frames * 2) { sample ->
            val frame = sample / 2
            (amplitude * sin(2.0 * PI * frequency * frame / sampleRate)).toFloat()
        }

    private fun rms(samples: FloatArray, startIndex: Int): Float {
        var sum = 0.0
        for (i in startIndex until samples.size) sum += samples[i] * samples[i]
        return sqrt(sum / (samples.size - startIndex)).toFloat()
    }
}
