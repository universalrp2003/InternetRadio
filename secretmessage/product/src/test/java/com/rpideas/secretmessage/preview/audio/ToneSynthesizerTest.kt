package com.rpideas.secretmessage.preview.audio

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToneSynthesizerTest {
    @Test
    fun providesSixDistinctOriginalTones() {
        val sounds = BuiltInNotificationSound.entries
        assertEquals(6, sounds.size)
        assertEquals((1..6).toList(), sounds.map { it.id })
        val waveforms = sounds.map { ToneSynthesizer.synthesize(it) }
        for (first in waveforms.indices) {
            for (second in first + 1 until waveforms.size) {
                assertFalse(
                    "${sounds[first]} and ${sounds[second]} must differ",
                    waveforms[first].contentEquals(waveforms[second]),
                )
            }
        }
    }

    @Test
    fun tonesAreShortAudibleBoundedAndFadeInAndOut() {
        BuiltInNotificationSound.entries.forEach { sound ->
            val samples = ToneSynthesizer.synthesize(sound)
            val seconds = samples.size.toDouble() / ToneSynthesizer.SAMPLE_RATE
            assertTrue("$sound duration $seconds", seconds in 0.3..0.7)
            val peak = samples.maxOf { abs(it.toInt()) }
            assertTrue("$sound peak $peak", peak in 8_000..32_767)
            assertEquals("$sound must start silent", 0, samples.first().toInt())
            assertTrue("$sound must fade out", abs(samples.last().toInt()) < 200)
        }
    }

    @Test
    fun synthesisIsDeterministic() {
        BuiltInNotificationSound.entries.forEach { sound ->
            assertTrue(ToneSynthesizer.synthesize(sound).contentEquals(ToneSynthesizer.synthesize(sound)))
        }
    }

    @Test
    fun unknownToneIdFallsBackToDefault() {
        assertEquals(BuiltInNotificationSound.CRYSTAL_PULSE, BuiltInNotificationSound.fromId(0))
        assertEquals(BuiltInNotificationSound.CRYSTAL_PULSE, BuiltInNotificationSound.fromId(99))
        assertEquals(BuiltInNotificationSound.QUANTUM_SHIMMER, BuiltInNotificationSound.fromId(6))
    }
}
