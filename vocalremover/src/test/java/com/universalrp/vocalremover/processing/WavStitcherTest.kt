package com.universalrp.vocalremover.processing

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import kotlin.math.abs

class WavStitcherTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun writesStereoPcmWaveAndCrossfadesTheOverlap() = runBlocking {
        val first = segment("first.f32", floatArrayOf(0.1f, 0.1f, 0.2f, 0.2f, 0.3f, 0.3f, 0.4f, 0.4f))
        val second = segment("second.f32", floatArrayOf(0.5f, 0.5f, 0.6f, 0.6f, 0.7f, 0.7f, 0.8f, 0.8f))
        val output = File(temp.root, "instrumental.wav")
        val host = TestHost()

        WavStitcher.write(listOf(first, second), output, 44_100, 2, host, 2, 2)

        assertEquals(44L + 6L * 4L, output.length())
        assertTrue(WavStitcher.isValid(output, 44_100, 6))
        assertEquals("RIFF", String(output.readBytes().copyOfRange(0, 4), Charsets.US_ASCII))
        assertEquals(2, readU16(output, 22))
        assertEquals(44_100, readU32(output, 24))
        assertEquals(16, readU16(output, 34))
        assertEquals(24, readU32(output, 40))

        val samples = readStereoPcm16(output)
        assertEquals(6, samples.size)
        assertNear(0.1f, samples[0][0])
        assertNear(0.2f, samples[1][0])
        assertNear(0.3667f, samples[2][0], 0.002f)
        assertNear(0.5333f, samples[3][0], 0.002f)
        assertNear(0.7f, samples[4][0])
        assertNear(0.8f, samples[5][0])
    }

    @Test fun validationRejectsTruncatedAndMismatchedWaves() = runBlocking {
        val segment = segment("single.f32", floatArrayOf(0f, 0f, .25f, .25f))
        val output = File(temp.root, "single.wav")
        WavStitcher.write(listOf(segment), output, 44_100, 0, TestHost(), 1, 1)
        assertTrue(WavStitcher.isValid(output, 44_100, 2))
        assertFalse(WavStitcher.isValid(output, 48_000, 2))
        File(output.parentFile, output.name + ".part").writeBytes(byteArrayOf(1, 2, 3))
        output.writeBytes(output.readBytes().copyOf(46))
        assertFalse(WavStitcher.isValid(output, 44_100, 2))
    }

    @Test fun canceledWavExportRemovesPartialFileAndPreservesExistingOutput() = runBlocking {
        val segment = segment("cancelled.f32", floatArrayOf(0.1f, -0.1f, 0.2f, -0.2f))
        val output = File(temp.root, "published.wav").apply { writeBytes(byteArrayOf(9, 8, 7, 6)) }
        val originalOutput = output.readBytes()
        val part = File(temp.root, "published.wav.part").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        val failure = runCatching {
            WavStitcher.write(listOf(segment), output, 44_100, 0, TestHost(continueOnCheckpoint = false), 1, 1)
        }.exceptionOrNull()

        assertTrue(failure is kotlinx.coroutines.CancellationException)
        assertTrue(originalOutput.contentEquals(output.readBytes()))
        assertFalse("Canceled export must discard its temporary file", part.exists())
    }

    @Test fun rejectsMalformedPcmHeadersEvenWhenTheFileLengthMatches() = runBlocking {
        val segment = segment("header-check.f32", floatArrayOf(0.1f, -0.1f))
        val output = File(temp.root, "header-check.wav")
        WavStitcher.write(listOf(segment), output, 44_100, 0, TestHost(), 1, 1)
        val validBytes = output.readBytes()
        assertTrue(WavStitcher.isValid(output, 44_100, 1))

        output.writeBytes(validBytes.copyOf().apply { this[20] = 3 })
        assertFalse(WavStitcher.isValid(output, 44_100, 1))

        output.writeBytes(validBytes.copyOf().apply { this[28] = (this[28].toInt() + 1).toByte() })
        assertFalse(WavStitcher.isValid(output, 44_100, 1))
    }

    @Test fun rejectsUnsupportedRiffSizesBeforeWritingLargeFiles() {
        val largestSupportedFrameCount = (0xffff_ffffL - 36L) / 4L
        assertTrue(WavStitcher.isFrameCountSupported(largestSupportedFrameCount))
        assertFalse(WavStitcher.isFrameCountSupported(largestSupportedFrameCount + 1L))
        assertFalse(WavStitcher.isFrameCountSupported(0L))
    }

    @Test fun resampleFrameMappingKeepsChunkBoundariesDeterministic() {
        assertEquals(44_100L, ChunkedSeparationProcessor.scaleFrame(48_000L, 48_000, 44_100))
        assertEquals(22_050L, ChunkedSeparationProcessor.scaleFrame(24_000L, 48_000, 44_100))
    }

    private fun segment(name: String, interleaved: FloatArray): File {
        val file = File(temp.root, name)
        DataOutputStream(file.outputStream()).use { output -> interleaved.forEach(output::writeFloat) }
        return file
    }

    private fun readStereoPcm16(file: File): List<FloatArray> {
        val bytes = file.readBytes()
        return (44 until bytes.size step 4).map { offset ->
            floatArrayOf(readI16(bytes, offset) / 32767f, readI16(bytes, offset + 2) / 32767f)
        }
    }

    private fun readU16(file: File, offset: Int): Int = readU16(file.readBytes(), offset)
    private fun readU32(file: File, offset: Int): Int = readU32(file.readBytes(), offset)
    private fun readU16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
    private fun readU32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)
    private fun readI16(bytes: ByteArray, offset: Int): Int {
        val unsigned = readU16(bytes, offset)
        return if (unsigned >= 32768) unsigned - 65536 else unsigned
    }

    private fun assertNear(expected: Float, actual: Float, tolerance: Float = 0.002f) {
        assertTrue("Expected $expected but got $actual", abs(expected - actual) <= tolerance)
    }

    private class TestHost(private val continueOnCheckpoint: Boolean = true) : ProcessorHost {
        override suspend fun checkpoint(
            reason: PauseReason?,
            message: String,
            beforePause: () -> Unit,
        ) = continueOnCheckpoint
        override fun isCanceled() = false
        override fun update(
            phase: String,
            progress: Float,
            message: String,
            completedChunks: Int?,
            totalChunks: Int?,
            outputSampleRate: Int?,
            privateOutputPath: String?,
        ) = Unit
    }
}
