package com.universalrp.vocalremover.audio

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmWavDecoderTest {
    @Test
    fun decodesPcm16StereoToInterleavedFloatCheckpoint() = runBlocking {
        val wav = pcm16Wav(
            sampleRate = 44_100,
            channels = 2,
            samples = shortArrayOf(-32_768, 32_767, -16_384, 16_384),
        )
        val destination = File.createTempFile("vocal-remover-wav", ".pcm")
        try {
            val info = PcmWavDecoder().decodeToFloatFile(
                source = ByteArrayInputStream(wav),
                destination = destination,
                checkpoint = { true },
                onProgress = {},
            )

            assertEquals(44_100, info.sampleRate)
            assertEquals(2, info.channels)
            assertEquals(2L, info.frames)
            assertEquals(2L * 2L * Float.SIZE_BYTES, destination.length())
            DataInputStream(destination.inputStream().buffered()).use { input ->
                assertEquals(-1f, input.readFloat(), 0.0001f)
                assertEquals(32_767 / 32_768f, input.readFloat(), 0.0001f)
                assertEquals(-0.5f, input.readFloat(), 0.0001f)
                assertEquals(0.5f, input.readFloat(), 0.0001f)
            }
        } finally {
            destination.delete()
            File(destination.parentFile, destination.name + ".part").delete()
        }
    }

    @Test
    fun rejectsCompressedWavInsteadOfTreatingItAsPcm() {
        val wav = pcm16Wav(
            sampleRate = 44_100,
            channels = 2,
            samples = shortArrayOf(0, 0),
            formatCode = 3,
        )
        val failure = runCatching {
            PcmWavHeader.read(DataInputStream(ByteArrayInputStream(wav)))
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    private fun pcm16Wav(
        sampleRate: Int,
        channels: Int,
        samples: ShortArray,
        formatCode: Int = 1,
    ): ByteArray {
        val dataBytes = samples.size * 2
        val bytesPerFrame = channels * 2
        val buffer = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + dataBytes)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(formatCode.toShort())
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * bytesPerFrame)
        buffer.putShort(bytesPerFrame.toShort())
        buffer.putShort(16)
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataBytes)
        samples.forEach { sample -> buffer.putShort(sample) }
        return buffer.array()
    }
}
