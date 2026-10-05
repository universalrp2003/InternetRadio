package com.universalrp.pulseeq.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import java.nio.ByteOrder
import java.nio.ByteBuffer

/**
 * PulseEQ's built-in player — the guaranteed way to hear the 20-band engine.
 *
 * It decodes your own audio files with MediaCodec, runs every sample through
 * [EqEngine], and writes the result to an AudioTrack. Because the samples pass
 * through our code, the equalizer here is the real 20-band one, not a 5-band
 * device approximation — and the LED spectrum shows what is actually playing.
 *
 * Playback is driven by a single worker thread; the engine is shared with the
 * rest of the app.
 */
class PlayerEngine(private val context: Context, private val eq: EqEngine) {

    enum class State { IDLE, PLAYING, PAUSED, FINISHED, ERROR }

    @Volatile
    var state: State = State.IDLE
        private set

    @Volatile
    var title: String = ""
        private set

    @Volatile
    var durationMs: Long = 0L
        private set

    @Volatile
    var positionMs: Long = 0L
        private set

    @Volatile
    var errorMessage: String? = null
        private set

    /** Called from the audio thread; the UI copies what it needs. */
    @Volatile
    var onStateChanged: ((State) -> Unit)? = null

    private var worker: Thread? = null
    private val pauseLock = Object()

    @Volatile
    private var paused = false

    @Volatile
    private var stopRequested = false

    private var pendingUri: Uri? = null

    // -------------------------------------------------------------- public API

    fun play(uri: Uri) {
        stop(notify = false)
        pendingUri = uri
        stopRequested = false
        paused = false
        errorMessage = null
        title = readTitle(uri)
        eq.flush()
        worker = Thread({ runPipeline(uri) }, "pulseeq-player").also { it.start() }
    }

    fun pause() {
        if (state != State.PLAYING) return
        paused = true
        state = State.PAUSED
        onStateChanged?.invoke(state)
    }

    fun resume() {
        if (state != State.PAUSED) return
        paused = false
        synchronized(pauseLock) { pauseLock.notifyAll() }
        state = State.PLAYING
        onStateChanged?.invoke(state)
    }

    fun toggle() {
        when (state) {
            State.PLAYING -> pause()
            State.PAUSED -> resume()
            else -> pendingUri?.let { play(it) }
        }
    }

    fun stop(notify: Boolean = true) {
        stopRequested = true
        paused = false
        synchronized(pauseLock) { pauseLock.notifyAll() }
        worker?.let { w ->
            runCatching { w.join(1500) }
        }
        worker = null
        pendingUri = null
        eq.flush()
        state = State.IDLE
        positionMs = 0L
        durationMs = 0L
        title = ""
        if (notify) onStateChanged?.invoke(state)
    }

    // ------------------------------------------------------------- pipeline

    private fun runPipeline(uri: Uri) {
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        var track: AudioTrack? = null
        try {
            extractor = MediaExtractor()
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
            } ?: run {
                fail("Cannot open that file")
                return
            }

            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = f
                    break
                }
            }
            val audioFormat = format
            if (trackIndex < 0 || audioFormat == null) {
                fail("No audio track in that file")
                return
            }
            extractor.selectTrack(trackIndex)

            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: run {
                fail("Unknown audio format")
                return
            }
            durationMs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                audioFormat.getLong(MediaFormat.KEY_DURATION) / 1000L
            } else 0L

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var outSampleRate = 48000
            var outChannels = 2
            var pcmFloat = false
            var pcm8 = false
            var framesWritten = 0L
            var stereo = FloatArray(16384)

            while (!stopRequested && !outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex)
                        val size = if (inBuf == null) -1 else extractor.readSampleData(inBuf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val of = codec.outputFormat
                        if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            outSampleRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            outChannels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        if (of.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            when (of.getInteger(MediaFormat.KEY_PCM_ENCODING)) {
                                AudioFormat.ENCODING_PCM_FLOAT -> pcmFloat = true
                                AudioFormat.ENCODING_PCM_8BIT -> pcm8 = true
                            }
                        }
                        eq.configure(outSampleRate)
                        track = buildTrack(outSampleRate, 2)
                        track.play()
                        state = State.PLAYING
                        onStateChanged?.invoke(state)
                    }

                    outIndex >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIndex)
                        val audioTrack = track
                        if (outBuf != null && info.size > 0 && audioTrack != null) {
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            val channels = outChannels.coerceAtLeast(1)
                            val bytesPerSample = if (pcmFloat) 4 else if (pcm8) 1 else 2
                            val frames = info.size / (bytesPerSample * channels)
                            if (frames > 0) {
                                if (stereo.size < frames * 2) stereo = FloatArray(frames * 2)
                                toStereoFloat(outBuf, channels, frames, stereo, pcmFloat, pcm8)
                                eq.process(stereo, frames)
                                writeToTrack(audioTrack, stereo, frames)
                                framesWritten += frames
                                positionMs = framesWritten * 1000L / outSampleRate
                                if (paused) {
                                    audioTrack.pause()
                                    synchronized(pauseLock) {
                                        while (paused && !stopRequested) {
                                            pauseLock.wait(120)
                                        }
                                    }
                                    if (!stopRequested) audioTrack.play()
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }

            if (!stopRequested) {
                state = State.FINISHED
                onStateChanged?.invoke(state)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Playback failed", t)
            if (!stopRequested) fail(t.message ?: t.javaClass.simpleName)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor?.release() }
            runCatching { track?.stop() }
            runCatching { track?.release() }
            eq.flush()
        }
    }

    private fun buildTrack(sampleRate: Int, channels: Int): AudioTrack {
        val channelMask = if (channels == 1) {
            AudioFormat.CHANNEL_OUT_MONO
        } else {
            AudioFormat.CHANNEL_OUT_STEREO
        }
        val minBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            channelMask,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(sampleRate / 4)

        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(minBytes * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    /** Converts whatever the decoder produced into interleaved stereo floats. */
    private fun toStereoFloat(
        buffer: ByteBuffer,
        channels: Int,
        frames: Int,
        out: FloatArray,
        floatPcm: Boolean,
        eightBit: Boolean,
    ) {
        buffer.order(ByteOrder.nativeOrder())
        when {
            floatPcm -> {
                val fb = buffer.asFloatBuffer()
                var i = 0
                for (n in 0 until frames) {
                    when (channels) {
                        1 -> {
                            val v = fb.get()
                            out[i] = v
                            out[i + 1] = v
                        }
                        else -> {
                            var l = fb.get()
                            var r = fb.get()
                            var extra = 2
                            while (extra < channels && fb.hasRemaining()) {
                                val m = fb.get()
                                l = (l + m) * 0.5f
                                r = (r + m) * 0.5f
                                extra++
                            }
                            out[i] = l
                            out[i + 1] = r
                        }
                    }
                    i += 2
                }
            }

            eightBit -> {
                val bb = buffer
                var i = 0
                for (n in 0 until frames) {
                    when (channels) {
                        1 -> {
                            val v = ((bb.get().toInt() and 0xFF) - 128) / 128f
                            out[i] = v
                            out[i + 1] = v
                        }
                        else -> {
                            out[i] = ((bb.get().toInt() and 0xFF) - 128) / 128f
                            out[i + 1] = ((bb.get().toInt() and 0xFF) - 128) / 128f
                            var extra = 2
                            while (extra < channels && bb.hasRemaining()) {
                                bb.get()
                                extra++
                            }
                        }
                    }
                    i += 2
                }
            }

            else -> {
                val sb = buffer.asShortBuffer()
                var i = 0
                for (n in 0 until frames) {
                    when (channels) {
                        1 -> {
                            val v = sb.get() / 32768f
                            out[i] = v
                            out[i + 1] = v
                        }
                        else -> {
                            var l = sb.get() / 32768f
                            var r = sb.get() / 32768f
                            var extra = 2
                            while (extra < channels && sb.hasRemaining()) {
                                val m = sb.get() / 32768f
                                l = (l + m) * 0.5f
                                r = (r + m) * 0.5f
                                extra++
                            }
                            out[i] = l
                            out[i + 1] = r
                        }
                    }
                    i += 2
                }
            }
        }
    }

    private fun writeToTrack(track: AudioTrack, stereo: FloatArray, frames: Int) {
        val shorts = ShortArray(frames * 2)
        for (i in 0 until frames * 2) {
            val v = stereo[i].coerceIn(-1f, 1f)
            shorts[i] = (v * 32767f).toInt().toShort()
        }
        var written = 0
        val total = shorts.size
        while (written < total && !stopRequested) {
            val n = track.write(shorts, written, total - written, AudioTrack.WRITE_BLOCKING)
            if (n <= 0) break
            written += n
        }
    }

    private fun fail(message: String) {
        errorMessage = message
        state = State.ERROR
        onStateChanged?.invoke(state)
    }

    private fun readTitle(uri: Uri): String {
        val retriever = MediaMetadataRetriever()
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
            } ?: return displayName(uri)
            val t = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val a = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            when {
                t.isNullOrBlank() -> displayName(uri)
                a.isNullOrBlank() -> t
                else -> "$a — $t"
            }
        } catch (t: Throwable) {
            displayName(uri)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun displayName(uri: Uri): String =
        uri.lastPathSegment?.substringAfterLast('/') ?: "Audio file"

    private companion object {
        const val TAG = "PulseEQ"
        const val TIMEOUT_US = 10_000L
    }
}
