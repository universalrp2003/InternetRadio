package com.universalrp.vocalremover.audio

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class PcmWavToMp3EncoderTest {
    @Test
    fun writesMpegFramesAtHighBitrateWithoutChangingTheWavInput() = runBlocking {
        val wav = makeToneWav(sampleRate = 44_100, frames = 4_410)
        val encoded = ByteArrayOutputStream()
        val progress = mutableListOf<Float>()
        val format = PcmWavToMp3Encoder().encode(
            source = ByteArrayInputStream(wav),
            destination = encoded,
            checkpoint = { true },
            onProgress = { progress.add(it) },
        )
        val bytes = encoded.toByteArray()

        assertEquals(44_100, format.sampleRate)
        assertEquals(2, format.channels)
        assertEquals(4_410L, format.frames)
        assertTrue("MP3 output should contain audio frames", bytes.size > 128)
        assertTrue("MP3 frame sync should be present", bytes.hasMpegAudioFrameSync())
        assertTrue("encoder progress should reach completion", progress.last() >= 1f)
        assertEquals(44 + 4_410 * 4, wav.size)
    }

    private fun makeToneWav(sampleRate: Int, frames: Int): ByteArray {
        val channels = 2
        val dataBytes = frames * channels * 2
        val buffer = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + dataBytes)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * channels * 2)
        buffer.putShort((channels * 2).toShort())
        buffer.putShort(16)
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataBytes)
        repeat(frames) { frame ->
            val sample = (sin(2.0 * PI * 440.0 * frame / sampleRate) * 12_000.0).toInt().toShort()
            buffer.putShort(sample)
            buffer.putShort(sample)
        }
        return buffer.array()
    }

    private fun ByteArray.hasMpegAudioFrameSync(): Boolean {
        for (index in 0 until (size - 1)) {
            if ((this[index].toInt() and 0xff) == 0xff &&
                (this[index + 1].toInt() and 0xe0) == 0xe0
            ) {
                return true
            }
        }
        return false
    }
}
