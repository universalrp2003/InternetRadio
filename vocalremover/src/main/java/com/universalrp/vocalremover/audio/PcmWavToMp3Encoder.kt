package com.universalrp.vocalremover.audio

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sourceforge.lame.mp3.Lame
import net.sourceforge.lame.mp3.MPEGMode
import net.sourceforge.lame.mp3.VbrMode
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.min

/**
 * Streams the app's PCM16 WAV to a high-bitrate MP3 using the bundled Java LAME encoder.
 * MP3 is inherently lossy; 320 kb/s CBR is chosen to minimize additional audible loss,
 * not to claim bit-perfect or mathematically lossless conversion.
 */
class PcmWavToMp3Encoder {
    suspend fun encode(
        source: InputStream,
        destination: OutputStream,
        checkpoint: suspend () -> Boolean,
        onProgress: (Float) -> Unit,
    ): PcmWavFormat = withContext(Dispatchers.IO) {
        var lame: Lame? = null
        try {
            DataInputStream(BufferedInputStream(source, IO_BUFFER_BYTES)).use { input ->
                val format = PcmWavHeader.read(input)
                val encoder = Lame()
                lame = encoder
                val flags = encoder.flags
                flags.setInNumChannels(format.channels)
                flags.setInSampleRate(format.sampleRate)
                flags.setMode(if (format.channels == 1) MPEGMode.MONO else MPEGMode.JOINT_STEREO)
                flags.setBitRate(TARGET_BITRATE_KBPS)
                flags.setQuality(ENCODER_QUALITY)
                flags.setVBR(VbrMode.vbr_off)
                flags.setWriteId3tagAutomatic(false)
                flags.setFindReplayGain(false)
                val result = encoder.initParams()
                check(result >= 0) { "The MP3 encoder could not initialize this WAV format ($result)" }

                val left = FloatArray(FRAMES_PER_BLOCK)
                val right = FloatArray(FRAMES_PER_BLOCK)
                val pcmBytes = ByteArray(FRAMES_PER_BLOCK * format.bytesPerFrame)
                val encoded = ByteArray(MP3_BUFFER_BYTES)
                var remainingBytes = format.dataBytes
                var processedBytes = 0L
                while (remainingBytes > 0L) {
                    if (!checkpoint()) throw CancellationException("Canceled while converting WAV to MP3")
                    val count = min(pcmBytes.size.toLong(), remainingBytes).toInt()
                    input.readFully(pcmBytes, 0, count)
                    val frames = count / format.bytesPerFrame
                    var byteOffset = 0
                    for (frame in 0 until frames) {
                        left[frame] = signedShortLe(pcmBytes, byteOffset) * SAMPLE_SCALE
                        byteOffset += 2
                        if (format.channels == 2) {
                            right[frame] = signedShortLe(pcmBytes, byteOffset) * SAMPLE_SCALE
                            byteOffset += 2
                        } else {
                            right[frame] = left[frame]
                        }
                    }

                    val encodedBytes = encoder.encodeBuffer(left, right, frames, encoded)
                    check(encodedBytes >= 0) { "The MP3 encoder ran out of output space ($encodedBytes)" }
                    if (encodedBytes > 0) destination.write(encoded, 0, encodedBytes)
                    processedBytes += count
                    remainingBytes -= count
                    onProgress((processedBytes.toDouble() / format.dataBytes).toFloat().coerceIn(0f, 1f))
                }

                if (!checkpoint()) throw CancellationException("Canceled while finishing MP3 encoding")
                val flushedBytes = encoder.encodeFlush(encoded)
                check(flushedBytes >= 0) { "The MP3 encoder could not finish the output ($flushedBytes)" }
                if (flushedBytes > 0) destination.write(encoded, 0, flushedBytes)
                destination.flush()
                onProgress(1f)
                format
            }
        } finally {
            runCatching { lame?.close() }
        }
    }

    private fun signedShortLe(bytes: ByteArray, offset: Int): Int {
        val value = (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
        return if (value >= 32_768) value - 65_536 else value
    }

    companion object {
        const val TARGET_BITRATE_KBPS = 320
        const val ENCODER_QUALITY = 2
        private const val FRAMES_PER_BLOCK = 4_096
        private const val MP3_BUFFER_BYTES = 64 * 1024
        private const val IO_BUFFER_BYTES = 256 * 1024
        private const val SAMPLE_SCALE = 65_536f
    }
}
