package com.universalrp.vocalremover.audio

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.min

/** The bounded PCM layout supported by the app's own WAV exports and later local passes. */
data class PcmWavFormat(
    val sampleRate: Int,
    val channels: Int,
    val dataBytes: Long,
    val bytesPerFrame: Int,
) {
    val frames: Long get() = dataBytes / bytesPerFrame
    val durationUs: Long get() = frames * 1_000_000L / sampleRate
}

/** RIFF/WAVE reader for integer PCM16 mono or stereo audio. */
object PcmWavHeader {
    fun read(input: DataInputStream): PcmWavFormat {
        require(readAscii(input, 4) == "RIFF") { "This is not a RIFF WAV file" }
        readUInt32Le(input) // RIFF size; the data chunk length is the authoritative sample count.
        require(readAscii(input, 4) == "WAVE") { "This is not a WAVE file" }

        var sampleRate: Int? = null
        var channelCount: Int? = null
        var blockAlign: Int? = null
        var audioFormat: Int? = null
        var bitsPerSample: Int? = null

        while (true) {
            val chunkId = readAscii(input, 4)
            val chunkBytes = readUInt32Le(input)
            when (chunkId) {
                "fmt " -> {
                    require(chunkBytes in 16L..MAX_FORMAT_CHUNK_BYTES) { "The WAV format chunk is invalid" }
                    val format = ByteArray(16)
                    input.readFully(format)
                    audioFormat = unsignedShortLe(format, 0)
                    channelCount = unsignedShortLe(format, 2)
                    sampleRate = unsignedIntLe(format, 4).takeIf { it <= Int.MAX_VALUE }?.toInt()
                    blockAlign = unsignedShortLe(format, 12)
                    bitsPerSample = unsignedShortLe(format, 14)
                    skipExactly(input, chunkBytes - format.size)
                    if ((chunkBytes and 1L) != 0L) skipExactly(input, 1L)
                }
                "data" -> {
                    val channels = channelCount
                        ?: throw IllegalArgumentException("The WAV data appears before its format chunk")
                    val rate = sampleRate
                        ?: throw IllegalArgumentException("The WAV sample rate is missing")
                    val frameBytes = blockAlign
                        ?: throw IllegalArgumentException("The WAV frame layout is missing")
                    require(audioFormat == PCM_FORMAT) { "Only uncompressed PCM WAV is supported" }
                    require(channels in 1..2) { "Only mono or stereo WAV is supported" }
                    require(rate in MIN_SAMPLE_RATE..MAX_SAMPLE_RATE) { "The WAV sample rate is unsupported" }
                    require(bitsPerSample == PCM_BITS_PER_SAMPLE) { "Only 16-bit PCM WAV is supported" }
                    require(frameBytes == channels * PCM_BYTES_PER_SAMPLE) { "The WAV channel layout is invalid" }
                    require(chunkBytes > 0L && chunkBytes % frameBytes == 0L) { "The WAV ends with a partial audio frame" }
                    return PcmWavFormat(rate, channels, chunkBytes, frameBytes)
                }
                else -> {
                    skipExactly(input, chunkBytes)
                    if ((chunkBytes and 1L) != 0L) skipExactly(input, 1L)
                }
            }
        }
    }

    internal fun readUInt32Le(input: DataInputStream): Long {
        val b0 = input.readUnsignedByte().toLong()
        val b1 = input.readUnsignedByte().toLong()
        val b2 = input.readUnsignedByte().toLong()
        val b3 = input.readUnsignedByte().toLong()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    internal fun readAscii(input: DataInputStream, count: Int): String {
        val bytes = ByteArray(count)
        input.readFully(bytes)
        return String(bytes, Charsets.US_ASCII)
    }

    private fun unsignedShortLe(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun unsignedIntLe(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xff) or
            ((bytes[offset + 1].toLong() and 0xff) shl 8) or
            ((bytes[offset + 2].toLong() and 0xff) shl 16) or
            ((bytes[offset + 3].toLong() and 0xff) shl 24)

    private fun skipExactly(input: DataInputStream, byteCount: Long) {
        var remaining = byteCount
        val discard = ByteArray(IO_BUFFER_BYTES)
        while (remaining > 0L) {
            val count = input.read(discard, 0, min(discard.size.toLong(), remaining).toInt())
            if (count < 0) throw IllegalArgumentException("The WAV file is truncated")
            remaining -= count
        }
    }

    private const val PCM_FORMAT = 1
    private const val PCM_BITS_PER_SAMPLE = 16
    private const val PCM_BYTES_PER_SAMPLE = 2
    private const val MIN_SAMPLE_RATE = 8_000
    private const val MAX_SAMPLE_RATE = 192_000
    private const val MAX_FORMAT_CHUNK_BYTES = 1L * 1024L * 1024L
    private const val IO_BUFFER_BYTES = 64 * 1024
}

/** Convert the app's public PCM16 WAV to the disk-backed float PCM used by the separator. */
class PcmWavDecoder {
    suspend fun decodeToFloatFile(
        source: InputStream,
        destination: File,
        checkpoint: suspend () -> Boolean,
        onProgress: (Float) -> Unit,
    ): DecodedPcmInfo = withContext(Dispatchers.IO) {
        destination.parentFile?.mkdirs()
        destination.delete()
        val part = File(destination.parentFile, destination.name + ".part").apply { delete() }
        var outputFile: FileOutputStream? = null
        var writer: DataOutputStream? = null
        var decodedFormat: PcmWavFormat? = null
        try {
            DataInputStream(BufferedInputStream(source, IO_BUFFER_BYTES)).use { input ->
                val format = PcmWavHeader.read(input)
                decodedFormat = format
                val pcmFile = FileOutputStream(part)
                outputFile = pcmFile
                val pcmWriter = DataOutputStream(BufferedOutputStream(pcmFile, IO_BUFFER_BYTES))
                writer = pcmWriter

                val bufferBytes = ByteArray(IO_BUFFER_BYTES - (IO_BUFFER_BYTES % format.bytesPerFrame))
                var remaining = format.dataBytes
                var writtenFrames = 0L
                while (remaining > 0L) {
                    if (!checkpoint()) throw CancellationException("Canceled while reading the saved WAV")
                    val count = min(bufferBytes.size.toLong(), remaining).toInt()
                    input.readFully(bufferBytes, 0, count)
                    var offset = 0
                    while (offset < count) {
                        val left = signedShortLe(bufferBytes, offset)
                        pcmWriter.writeFloat(left / 32768f)
                        if (format.channels == 2) {
                            val right = signedShortLe(bufferBytes, offset + 2)
                            pcmWriter.writeFloat(right / 32768f)
                        }
                        offset += format.bytesPerFrame
                    }
                    writtenFrames += count / format.bytesPerFrame
                    remaining -= count
                    onProgress((writtenFrames.toDouble() / format.frames).toFloat().coerceIn(0f, 1f))
                }
                pcmWriter.flush()
                pcmFile.fd.sync()
                pcmWriter.close()
                writer = null
                outputFile = null
            }

            val format = decodedFormat ?: throw IllegalArgumentException("The WAV format could not be read")
            check(part.length() == format.frames * format.channels * Float.SIZE_BYTES) {
                "The decoded WAV PCM could not be verified"
            }
            check(part.renameTo(destination)) { "Could not save the decoded WAV checkpoint" }
            onProgress(1f)
            DecodedPcmInfo(
                sampleRate = format.sampleRate,
                channels = format.channels,
                frames = format.frames,
                durationUs = format.durationUs,
            )
        } catch (failure: Throwable) {
            destination.delete()
            part.delete()
            throw failure
        } finally {
            runCatching { writer?.close() }
            runCatching { outputFile?.close() }
        }
    }

    companion object {
        private const val IO_BUFFER_BYTES = 256 * 1024

        private fun signedShortLe(bytes: ByteArray, offset: Int): Int {
            val value = (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
            return if (value >= 32_768) value - 65_536 else value
        }
    }
}
