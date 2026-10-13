package com.universalrp.vocalremover.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.AudioFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream

data class DecodedPcmInfo(
    val sampleRate: Int,
    val channels: Int,
    val frames: Long,
    val durationUs: Long,
)

/** Decode a local MP3 with Android's platform decoder into a disk-backed interleaved float file. */
class Mp3Decoder {
    suspend fun decodeToFloatFile(
        source: File,
        destination: File,
        checkpoint: suspend () -> Boolean,
        onProgress: (Float) -> Unit,
    ): DecodedPcmInfo = withContext(Dispatchers.IO) {
        require(source.isFile && source.length() > 0L) { "The selected MP3 is empty or missing" }
        destination.parentFile?.mkdirs()
        destination.delete()
        val part = File(destination.parentFile, destination.name + ".part").apply { delete() }

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var data: DataOutputStream? = null
        var pcmFileOutput: FileOutputStream? = null
        try {
            extractor.setDataSource(source.absolutePath)
            var audioTrack = -1
            var inputFormat: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.equals("audio/mpeg", ignoreCase = true)) {
                    audioTrack = index
                    inputFormat = format
                    break
                }
            }
            check(audioTrack >= 0 && inputFormat != null) { "This prototype accepts MP3 audio only" }
            extractor.selectTrack(audioTrack)
            val format = inputFormat!!
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val durationUs = runCatching { format.getLong(MediaFormat.KEY_DURATION) }
                .getOrDefault(0L).coerceAtLeast(0L)
            val configuredRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val configuredChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            check(configuredChannels in 1..2) { "Only mono or stereo MP3 is supported" }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            val outputFile = FileOutputStream(part)
            pcmFileOutput = outputFile
            val pcmWriter = DataOutputStream(BufferedOutputStream(outputFile, 512 * 1024))
            data = pcmWriter

            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var sampleRate = configuredRate
            var channels = configuredChannels
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            var frames = 0L

            while (!outputEnded) {
                if (!checkpoint()) throw CancellationException("Canceled while decoding MP3")
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                            ?: throw IllegalStateException("MP3 decoder returned no input buffer")
                        inputBuffer.clear()
                        val size = extractor.readSampleData(inputBuffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            val presentationTime = extractor.sampleTime.coerceAtLeast(0L)
                            codec.queueInputBuffer(inputIndex, 0, size, presentationTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val decodedFormat = codec.outputFormat
                        sampleRate = decodedFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = decodedFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        check(channels in 1..2) { "The MP3 decoder produced more than two channels" }
                        pcmEncoding = runCatching {
                            decodedFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }.getOrDefault(AudioFormat.ENCODING_PCM_16BIT)
                        check(pcmEncoding == AudioFormat.ENCODING_PCM_16BIT || pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                            "The Android MP3 decoder returned an unsupported PCM format"
                        }
                        check(sampleRate > 0) { "The MP3 decoder returned an invalid sample rate" }
                    }
                    else -> if (outputIndex >= 0) {
                        if (info.size > 0) {
                            val output = codec.getOutputBuffer(outputIndex)
                                ?: throw IllegalStateException("MP3 decoder returned no output buffer")
                            val bytes = output.duplicate().apply {
                                position(info.offset)
                                limit(info.offset + info.size)
                            }
                            val bytesPerSample = if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                            val frameBytes = bytesPerSample * channels
                            check(frameBytes > 0 && bytes.remaining() % frameBytes == 0) {
                                "The MP3 decoder returned a partial audio frame"
                            }
                            val frameCount = bytes.remaining() / frameBytes
                            if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                                bytes.order(java.nio.ByteOrder.LITTLE_ENDIAN)
                                repeat(frameCount * channels) {
                                    val sample = bytes.float
                                    pcmWriter.writeFloat(if (sample.isFinite()) sample.coerceIn(-1f, 1f) else 0f)
                                }
                            } else {
                                bytes.order(java.nio.ByteOrder.LITTLE_ENDIAN)
                                repeat(frameCount * channels) {
                                    pcmWriter.writeFloat(bytes.short / 32768f)
                                }
                            }
                            frames += frameCount
                            val estimateFrames = if (durationUs > 0L) durationUs * sampleRate / 1_000_000L else 0L
                            if (estimateFrames > 0L) onProgress((frames.toFloat() / estimateFrames).coerceIn(0f, 1f))
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputEnded = true
                    }
                }
            }
            pcmWriter.flush()
            pcmFileOutput?.fd?.sync()
            check(frames > 0L && part.length() > 0L) { "No decoded samples were produced" }
            pcmWriter.close()
            data = null
            pcmFileOutput = null
            check(part.renameTo(destination)) { "Could not save the decoded audio checkpoint" }
            onProgress(1f)
            DecodedPcmInfo(sampleRate, channels, frames, durationUs)
        } catch (t: Throwable) {
            destination.delete()
            part.delete()
            throw t
        } finally {
            runCatching { data?.close() }
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    companion object { private const val TIMEOUT_US = 10_000L }
}
