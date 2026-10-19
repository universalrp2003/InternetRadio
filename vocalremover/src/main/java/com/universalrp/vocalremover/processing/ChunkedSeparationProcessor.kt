package com.universalrp.vocalremover.processing

import com.universalrp.vocalremover.audio.DecodedPcmInfo
import com.universalrp.vocalremover.engine.SherpaUvrEngine
import com.universalrp.vocalremover.health.DeviceMonitor
import com.universalrp.vocalremover.health.DeviceSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.min
import kotlin.math.roundToInt

interface ProcessorHost {
    /** Wait at a safe boundary; release the model engine before sleeping through a pause. */
    suspend fun checkpoint(
        reason: PauseReason? = null,
        message: String = "",
        beforePause: () -> Unit = {},
    ): Boolean
    fun isCanceled(): Boolean
    fun update(
        phase: String,
        progress: Float,
        message: String,
        completedChunks: Int? = null,
        totalChunks: Int? = null,
        outputSampleRate: Int? = null,
        privateOutputPath: String? = null,
    )
}

/**
 * Bounded-window offline processing. Completed 12-second cores are fsynced and atomically
 * renamed before the durable completed-chunk count advances. One-second overlap is blended
 * when the WAV is assembled; each chunk also includes one second of inference context on
 * either side to reduce edge artifacts.
 */
class ChunkedSeparationProcessor(
    private val readDeviceSnapshot: () -> DeviceSnapshot,
) {
    suspend fun process(
        job: SeparationJob,
        workDir: File,
        inputPcmFile: File,
        decoded: DecodedPcmInfo,
        modelFile: File,
        host: ProcessorHost,
        modelCacheKey: String = modelFile.nameWithoutExtension,
    ): File = withContext(Dispatchers.IO) {
        check(decoded.channels in 1..2 && decoded.sampleRate > 0 && decoded.frames > 0L)
        check(inputPcmFile.isFile && modelFile.isFile) { "Local input or model file is missing" }

        require(modelCacheKey.matches(Regex("[A-Za-z0-9._-]{1,64}"))) { "Invalid model cache key" }
        val chunkDir = File(workDir, "chunks-$modelCacheKey").apply { mkdirs() }
        val wavFile = File(workDir, "instrumental-$modelCacheKey.wav")
        removeStaleModelCaches(workDir, chunkDir, wavFile)
        val coreFrames = CORE_SECONDS.toLong() * decoded.sampleRate
        val overlapFrames = OVERLAP_SECONDS.toLong() * decoded.sampleRate
        val strideFrames = coreFrames - overlapFrames
        val contextFrames = CONTEXT_SECONDS.toLong() * decoded.sampleRate
        val starts = mutableListOf<Long>()
        var next = 0L
        while (next < decoded.frames) {
            starts += next
            if (next + coreFrames >= decoded.frames) break
            next += strideFrames
        }
        val totalChunks = starts.size

        var engine: SherpaUvrEngine? = null
        var engineThreads = -1
        fun releaseEngineForPause() {
            runCatching { engine?.close() }
            engine = null
            engineThreads = -1
        }
        try {
            var completed = 0
            var outputRate = job.outputSampleRate
            for (index in starts.indices) {
                if (!host.checkpoint(beforePause = ::releaseEngineForPause)) {
                    throw CancellationException("Canceled by user")
                }
                ensureNotCanceled(host)
                val coreStart = starts[index]
                val coreEnd = min(decoded.frames, coreStart + coreFrames)
                val segment = File(chunkDir, "chunk_${index.toString().padStart(5, '0')}.f32")
                var expectedOutFrames = 0L

                // A recovered chunk does not need the native model to be loaded again. This is
                // especially important after restart, when every inference chunk may be complete.
                if (outputRate > 0) {
                    ensureStorageHeadroom(
                        starts = starts,
                        decodedFrames = decoded.frames,
                        inputRate = decoded.sampleRate,
                        outputRate = outputRate,
                        coreFrames = coreFrames,
                        overlapFrames = scaleFrame(overlapFrames, decoded.sampleRate, outputRate),
                        chunkDir = chunkDir,
                        wavFile = wavFile,
                        host = host,
                        beforePause = ::releaseEngineForPause,
                    )
                    val startOut = scaleFrame(coreStart, decoded.sampleRate, outputRate)
                    val endOut = scaleFrame(coreEnd, decoded.sampleRate, outputRate)
                    expectedOutFrames = (endOut - startOut).coerceAtLeast(1L)
                    if (segmentIsValid(segment, expectedOutFrames)) {
                        completed++
                        host.update(
                            phase = "Resuming separation",
                            progress = progressForChunks(completed, totalChunks),
                            message = "Recovered saved chunk $completed of $totalChunks",
                            completedChunks = completed,
                            totalChunks = totalChunks,
                            outputSampleRate = outputRate,
                        )
                        continue
                    }
                }

                var decision = awaitSafeToRun(job.profile, host, ::releaseEngineForPause)
                if (engine == null || engineThreads != decision.threads) {
                    engine?.close()
                    val loadedEngine = SherpaUvrEngine(modelFile.absolutePath, decision.threads)
                    engine = loadedEngine
                    engineThreads = decision.threads
                    val actualRate = loadedEngine.outputSampleRateHz
                    if (outputRate != 0 && outputRate != actualRate) {
                        throw IllegalStateException("The model output format changed; saved chunks cannot be mixed")
                    }
                    outputRate = actualRate
                    host.update(
                        phase = "Loading separation model",
                        progress = progressForChunks(completed, totalChunks),
                        message = "On-device UVR model ready at ${actualRate} Hz",
                        completedChunks = completed,
                        totalChunks = totalChunks,
                        outputSampleRate = actualRate,
                    )
                }
                // Recalculate before every chunk so external storage changes during a long job
                // cannot turn a once-safe estimate into an avoidable mid-job disk exhaustion.
                ensureStorageHeadroom(
                    starts = starts,
                    decodedFrames = decoded.frames,
                    inputRate = decoded.sampleRate,
                    outputRate = outputRate,
                    coreFrames = coreFrames,
                    overlapFrames = scaleFrame(overlapFrames, decoded.sampleRate, outputRate),
                    chunkDir = chunkDir,
                    wavFile = wavFile,
                    host = host,
                    beforePause = ::releaseEngineForPause,
                )

                val startOut = scaleFrame(coreStart, decoded.sampleRate, outputRate)
                val endOut = scaleFrame(coreEnd, decoded.sampleRate, outputRate)
                expectedOutFrames = (endOut - startOut).coerceAtLeast(1L)
                if (segmentIsValid(segment, expectedOutFrames)) {
                    completed++
                    host.update(
                        phase = "Resuming separation",
                        progress = progressForChunks(completed, totalChunks),
                        message = "Recovered saved chunk $completed of $totalChunks",
                        completedChunks = completed,
                        totalChunks = totalChunks,
                        outputSampleRate = outputRate,
                    )
                    continue
                }

                decision = awaitSafeToRun(job.profile, host, ::releaseEngineForPause)
                if (decision.threads != engineThreads) {
                    engine?.close()
                    val loadedEngine = SherpaUvrEngine(modelFile.absolutePath, decision.threads)
                    engine = loadedEngine
                    engineThreads = decision.threads
                    val actualRate = loadedEngine.outputSampleRateHz
                    if (outputRate != 0 && outputRate != actualRate) {
                        throw IllegalStateException("The model output format changed; saved chunks cannot be mixed")
                    }
                    outputRate = actualRate
                    host.update(
                        phase = "Loading separation model",
                        progress = progressForChunks(completed, totalChunks),
                        message = "Adjusted to ${decision.threads} inference thread(s)",
                        completedChunks = completed,
                        totalChunks = totalChunks,
                        outputSampleRate = outputRate,
                    )
                }

                val windowStart = (coreStart - contextFrames).coerceAtLeast(0L)
                val windowEnd = min(decoded.frames, coreEnd + contextFrames)
                val windowFrames = windowEnd - windowStart
                val input = readPcmWindow(inputPcmFile, windowStart, windowFrames, decoded.channels)
                host.update(
                    phase = "Separating audio",
                    progress = progressForChunks(completed, totalChunks),
                    message = "Chunk ${index + 1} of $totalChunks — processing stays on this device",
                    completedChunks = completed,
                    totalChunks = totalChunks,
                    outputSampleRate = outputRate,
                )
                val separated = engine!!.separate(input, decoded.sampleRate)
                check(separated.sampleRate == outputRate) { "The model changed output sample rate mid-job" }
                writeInstrumentalSegment(
                    target = segment,
                    separatedChannels = separated.channels,
                    inputStartFrame = windowStart,
                    coreStartFrame = coreStart,
                    coreEndFrame = coreEnd,
                    expectedOutputFrames = expectedOutFrames,
                    inputRate = decoded.sampleRate,
                    outputRate = outputRate,
                )
                completed++
                host.update(
                    phase = "Separating audio",
                    progress = progressForChunks(completed, totalChunks),
                    message = "Saved chunk $completed of $totalChunks; it can be resumed after interruption",
                    completedChunks = completed,
                    totalChunks = totalChunks,
                    outputSampleRate = outputRate,
                )
                if (decision.interChunkDelayMs > 0L) delay(decision.interChunkDelayMs)
            }

            if (completed != totalChunks) throw IllegalStateException("Not all audio chunks were saved")
            if (!host.checkpoint(beforePause = ::releaseEngineForPause)) {
                throw CancellationException("Canceled by user")
            }
            if (engine != null) awaitSafeToRun(job.profile, host, ::releaseEngineForPause)
            val rate = outputRate.takeIf { it > 0 }
                ?: engine?.outputSampleRateHz
                ?: throw IllegalStateException("The output sample rate is unavailable")
            // All inference is complete; release the model's large native allocations before WAV stitching.
            releaseEngineForPause()
            host.update(
                phase = "Writing instrumental WAV",
                progress = 0.91f,
                message = "Blending chunk boundaries and writing a standard stereo WAV",
                completedChunks = totalChunks,
                totalChunks = totalChunks,
                outputSampleRate = rate,
                privateOutputPath = wavFile.absolutePath,
            )
            val segmentFiles = starts.indices.map { File(chunkDir, "chunk_${it.toString().padStart(5, '0')}.f32") }
            val overlapOutFrames = scaleFrame(overlapFrames, decoded.sampleRate, rate)
            val expectedWavFrames = segmentFiles.sumOf { it.length() / STEREO_FLOAT_BYTES_PER_FRAME } -
                overlapOutFrames * (segmentFiles.size.toLong() - 1L)
            check(WavStitcher.isFrameCountSupported(expectedWavFrames)) {
                "This song would exceed the 4 GB WAV file limit. Try a shorter song."
            }
            if (!WavStitcher.isValid(wavFile, rate, expectedWavFrames)) {
                WavStitcher.write(
                    segments = segmentFiles,
                    output = wavFile,
                    sampleRate = rate,
                    overlapFrames = overlapOutFrames,
                    host = host,
                    completedChunks = totalChunks,
                    totalChunks = totalChunks,
                )
            } else {
                host.update(
                    phase = "Saving instrumental WAV",
                    progress = 0.97f,
                    message = "Recovered the completed WAV from the previous run",
                    completedChunks = totalChunks,
                    totalChunks = totalChunks,
                    outputSampleRate = rate,
                    privateOutputPath = wavFile.absolutePath,
                )
            }
            wavFile
        } finally {
            runCatching { engine?.close() }
        }
    }

    private fun removeStaleModelCaches(workDir: File, activeChunkDir: File, activeWav: File) {
        workDir.listFiles()?.forEach { entry ->
            val isOldChunks = entry.isDirectory &&
                (entry.name == "chunks" || entry.name.startsWith("chunks-")) && entry != activeChunkDir
            val isOldInstrumental = entry.isFile &&
                entry.name.startsWith("instrumental") && entry.extension.equals("wav", ignoreCase = true) && entry != activeWav
            if (isOldChunks || isOldInstrumental) runCatching { entry.deleteRecursively() }
        }
    }

    private suspend fun ensureStorageHeadroom(
        starts: List<Long>,
        decodedFrames: Long,
        inputRate: Int,
        outputRate: Int,
        coreFrames: Long,
        overlapFrames: Long,
        chunkDir: File,
        wavFile: File,
        host: ProcessorHost,
        beforePause: () -> Unit,
    ) {
        // A killed process can leave only uncommitted .part files. They are not resumable;
        // discard them before estimating space because completed .f32/WAV files are durable.
        chunkDir.listFiles { _, name -> name.endsWith(".part") }?.forEach { part ->
            if (part.exists() && !part.delete()) {
                throw IllegalStateException("Could not remove an incomplete audio chunk; free app storage, then retry.")
            }
        }
        val wavPart = File(wavFile.parentFile, wavFile.name + ".part")
        if (wavPart.exists() && !wavPart.delete()) {
            throw IllegalStateException("Could not remove an incomplete private WAV; free app storage, then retry.")
        }
        while (true) {
            if (!host.checkpoint(beforePause = beforePause)) throw CancellationException("Canceled by user")
            var missingSegmentBytes = 0L
            var totalSegmentFrames = 0L
            starts.forEachIndexed { index, start ->
                val end = min(decodedFrames, start + coreFrames)
                val frames = (scaleFrame(end, inputRate, outputRate) -
                    scaleFrame(start, inputRate, outputRate)).coerceAtLeast(1L)
                totalSegmentFrames += frames
                val file = File(chunkDir, "chunk_${index.toString().padStart(5, '0')}.f32")
                if (!segmentIsValid(file, frames)) {
                    if (file.exists() && !file.delete()) {
                        throw IllegalStateException("Could not remove a damaged audio chunk; free app storage, then retry.")
                    }
                    missingSegmentBytes += frames * STEREO_FLOAT_BYTES_PER_FRAME
                }
            }
            val expectedOutputFrames = (totalSegmentFrames - overlapFrames * (starts.size.toLong() - 1L)).coerceAtLeast(1L)
            check(WavStitcher.isFrameCountSupported(expectedOutputFrames)) {
                "This song would exceed the 4 GB WAV file limit. Try a shorter song."
            }
            val wavBytes = WAV_HEADER_BYTES.toLong() + expectedOutputFrames * STEREO_PCM16_BYTES_PER_FRAME
            val privateWavReady = WavStitcher.isValid(wavFile, outputRate, expectedOutputFrames)
            if (!privateWavReady && wavFile.exists() && !wavFile.delete()) {
                throw IllegalStateException("Could not remove an incomplete private WAV; free app storage, then retry.")
            }
            val requiredBytes = missingSegmentBytes + (if (privateWavReady) 0L else wavBytes) +
                wavBytes + ProcessingPolicy.STORAGE_SAFETY_RESERVE_BYTES
            val available = readDeviceSnapshot().availableStorageBytes
                ?: throw IllegalStateException("Free app storage could not be measured safely; check device storage, then retry.")
            if (available >= requiredBytes) return

            val shortfall = requiredBytes - available
            val detail = "This song needs about ${DeviceMonitor.formatBytes(requiredBytes)} free for resumable WAV output; " +
                "only ${DeviceMonitor.formatBytes(available)} is available. Free space, then resume " +
                "(about ${DeviceMonitor.formatBytes(shortfall)} more is needed)."
            if (!host.checkpoint(PauseReason.STORAGE, detail, beforePause)) {
                throw CancellationException("Canceled by user")
            }
        }
    }

    private suspend fun awaitSafeToRun(
        profile: ProcessingProfile,
        host: ProcessorHost,
        beforePause: () -> Unit,
    ): WorkDecision {
        while (true) {
            if (!host.checkpoint(beforePause = beforePause)) {
                throw CancellationException("Canceled by user")
            }
            ensureNotCanceled(host)
            val snapshot = readDeviceSnapshot()
            val decision = ProcessingPolicy.decide(
                profile = profile,
                availableCores = Runtime.getRuntime().availableProcessors(),
                snapshot = snapshot.toSafetySnapshot(),
            )
            if (!decision.pause) return decision
            val detail = when (decision.reason) {
                PauseReason.THERMAL -> "Android reports severe thermal status. Let the device cool, then resume."
                PauseReason.LOW_MEMORY -> "Low RAM; saved chunks are safe. Will auto-resume after at least " +
                    "${DeviceMonitor.formatBytes(ProcessingPolicy.LOW_MEMORY_AUTO_RESUME_BYTES)} is free for " +
                    "${ProcessingPolicy.LOW_MEMORY_AUTO_RESUME_STABLE_MS / 1_000L} seconds, once other safety checks are clear."
                PauseReason.STORAGE -> "Free storage is low. Make space, then resume."
                else -> "Processing paused for device safety."
            }
            if (!host.checkpoint(decision.reason, detail, beforePause)) {
                throw CancellationException("Canceled by user")
            }
        }
    }

    private fun DeviceSnapshot.toSafetySnapshot() = SafetySnapshot(
        thermal = thermal,
        batteryTemperatureC = batteryTemperatureC,
        cpuTemperatureC = cpuTemperatureC,
        availableRamBytes = availableRamBytes,
        availableStorageBytes = availableStorageBytes,
    )

    private fun readPcmWindow(file: File, startFrame: Long, frames: Long, channels: Int): Array<FloatArray> {
        require(frames in 1..Int.MAX_VALUE.toLong())
        val count = frames.toInt()
        val result = Array(channels) { FloatArray(count) }
        RandomAccessFile(file, "r").use { input ->
            input.seek(startFrame * channels * Float.SIZE_BYTES)
            repeat(count) { frame ->
                repeat(channels) { channel -> result[channel][frame] = input.readFloat() }
            }
        }
        return result
    }

    private fun writeInstrumentalSegment(
        target: File,
        separatedChannels: Array<FloatArray>,
        inputStartFrame: Long,
        coreStartFrame: Long,
        coreEndFrame: Long,
        expectedOutputFrames: Long,
        inputRate: Int,
        outputRate: Int,
    ) {
        require(separatedChannels.isNotEmpty() && separatedChannels.size <= 2)
        require(separatedChannels.all { it.size == separatedChannels[0].size })
        require(expectedOutputFrames in 1..Int.MAX_VALUE.toLong())
        val cropStart = scaleFrame(coreStartFrame - inputStartFrame, inputRate, outputRate)
        val cropEnd = scaleFrame(coreEndFrame - inputStartFrame, inputRate, outputRate)
        val available = separatedChannels[0].size.toLong()
        val neededEnd = cropStart + expectedOutputFrames
        check(cropStart >= 0L && cropEnd >= cropStart && cropStart < available &&
            neededEnd - available <= ALLOWED_EDGE_ROUNDING &&
            kotlin.math.abs((cropEnd - cropStart) - expectedOutputFrames) <= ALLOWED_EDGE_ROUNDING) {
            "The separator returned an unexpected chunk length"
        }

        target.parentFile?.mkdirs()
        val part = File(target.parentFile, target.name + ".part")
        part.delete()
        try {
            val fileOut = FileOutputStream(part)
            try {
                val output = DataOutputStream(BufferedOutputStream(fileOut, 256 * 1024))
                try {
                    val left = separatedChannels[0]
                    val right = if (separatedChannels.size > 1) separatedChannels[1] else left
                    val start = cropStart.toInt()
                    val endExclusive = min(cropEnd, available).toInt()
                    var frame = 0
                    while (frame < expectedOutputFrames.toInt()) {
                        val sourceIndex = start + frame
                        val index = sourceIndex.coerceAtMost(endExclusive - 1)
                        output.writeFloat(left[index].finiteSample())
                        output.writeFloat(right[index].finiteSample())
                        frame++
                    }
                    output.flush()
                    fileOut.fd.sync()
                } finally {
                    output.close()
                }
            } finally {
                runCatching { fileOut.close() }
            }
            check(part.length() == expectedOutputFrames * STEREO_FLOAT_BYTES_PER_FRAME) {
                "The saved chunk has an invalid size"
            }
            target.delete()
            check(part.renameTo(target)) { "Could not finalize a saved audio chunk" }
        } catch (t: Throwable) {
            part.delete()
            throw t
        }
    }

    private fun segmentIsValid(file: File, expectedFrames: Long): Boolean =
        file.isFile && file.length() == expectedFrames * STEREO_FLOAT_BYTES_PER_FRAME

    private fun progressForChunks(completed: Int, total: Int): Float =
        (0.24f + 0.64f * completed.toFloat() / total.coerceAtLeast(1)).coerceIn(0.24f, 0.88f)

    private fun ensureNotCanceled(host: ProcessorHost) {
        if (host.isCanceled()) throw CancellationException("Canceled by user")
    }

    companion object {
        private const val CORE_SECONDS = 12
        private const val OVERLAP_SECONDS = 1
        private const val CONTEXT_SECONDS = 1
        private const val WAV_HEADER_BYTES = 44
        private const val STEREO_PCM16_BYTES_PER_FRAME = 4L
        private const val STEREO_FLOAT_BYTES_PER_FRAME = 8L
        private const val ALLOWED_EDGE_ROUNDING = 2L

        internal fun scaleFrame(frames: Long, sourceRate: Int, targetRate: Int): Long =
            ((frames * targetRate.toLong() + sourceRate / 2L) / sourceRate).coerceAtLeast(0L)

        private fun Float.finiteSample(): Float = if (isFinite()) coerceIn(-1f, 1f) else 0f
    }
}

internal object WavStitcher {
    fun isFrameCountSupported(frames: Long): Boolean =
        frames > 0L && frames <= MAX_RIFF_DATA_BYTES / PCM16_STEREO_BYTES

    fun isValid(file: File, sampleRate: Int, frames: Long): Boolean = runCatching {
        if (!file.isFile || file.length() != WAV_HEADER_BYTES + frames * PCM16_STEREO_BYTES) return false
        RandomAccessFile(file, "r").use { wav ->
            val header = ByteArray(WAV_HEADER_BYTES)
            wav.readFully(header)
            val dataBytes = frames * PCM16_STEREO_BYTES
            String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                unsignedIntLe(header, 4) == 36L + dataBytes &&
                String(header, 8, 4, Charsets.US_ASCII) == "WAVE" &&
                String(header, 12, 4, Charsets.US_ASCII) == "fmt " &&
                unsignedIntLe(header, 16) == 16L &&
                unsignedShortLe(header, 20) == 1 &&
                unsignedShortLe(header, 22) == 2 &&
                unsignedIntLe(header, 24) == sampleRate.toLong() &&
                unsignedIntLe(header, 28) == sampleRate.toLong() * PCM16_STEREO_BYTES &&
                unsignedShortLe(header, 32) == PCM16_STEREO_BYTES.toInt() &&
                unsignedShortLe(header, 34) == 16 &&
                String(header, 36, 4, Charsets.US_ASCII) == "data" &&
                unsignedIntLe(header, 40) == dataBytes
        }
    }.getOrDefault(false)

    private fun unsignedShortLe(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun unsignedIntLe(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xff) or
            ((bytes[offset + 1].toLong() and 0xff) shl 8) or
            ((bytes[offset + 2].toLong() and 0xff) shl 16) or
            ((bytes[offset + 3].toLong() and 0xff) shl 24)

    suspend fun write(
        segments: List<File>,
        output: File,
        sampleRate: Int,
        overlapFrames: Long,
        host: ProcessorHost,
        completedChunks: Int,
        totalChunks: Int,
    ) {
        require(segments.isNotEmpty() && sampleRate > 0)
        check(segments.all { it.isFile && it.length() > 0L && it.length() % STEREO_FLOAT_BYTES == 0L }) {
            "A saved audio chunk is missing or damaged"
        }
        var expectedFrames = 0L
        segments.forEachIndexed { index, segment ->
            val frames = segment.length() / STEREO_FLOAT_BYTES
            val overlap = if (index == 0) 0L else min(overlapFrames, min(expectedFrames, frames))
            expectedFrames += frames - overlap
        }
        check(isFrameCountSupported(expectedFrames)) {
            "This song would exceed the 4 GB WAV file limit. Try a shorter song."
        }
        output.parentFile?.mkdirs()
        val part = File(output.parentFile, output.name + ".part")
        part.delete()
        try {
            RandomAccessFile(part, "rw").use { wav ->
                wav.setLength(0L)
                wav.seek(WAV_HEADER_BYTES.toLong())
                var writtenFrames = 0L

                segments.forEachIndexed { index, segment ->
                    if (!host.checkpoint()) throw CancellationException("Canceled by user")
                    check(segment.isFile && segment.length() > 0L && segment.length() % STEREO_FLOAT_BYTES == 0L) {
                        "A saved audio chunk is missing or damaged"
                    }
                    val segmentFrames = segment.length() / STEREO_FLOAT_BYTES
                    val overlap = if (index == 0) 0L else min(overlapFrames, min(writtenFrames, segmentFrames))
                    DataInputStream(BufferedInputStream(FileInputStream(segment), IO_BUFFER_BYTES)).use { input ->
                        if (overlap > 0L) {
                            require(overlap <= Int.MAX_VALUE / PCM16_STEREO_BYTES.toInt())
                            val overlapSamples = overlap.toInt() * 2
                            val current = FloatArray(overlapSamples)
                            repeat(overlapSamples) { current[it] = input.readFloat().finiteSample() }
                            val overlapBytes = overlap.toInt() * PCM16_STEREO_BYTES.toInt()
                            val oldBytes = ByteArray(overlapBytes)
                            val blendBytes = ByteArray(overlapBytes)
                            val blendStart = writtenFrames - overlap
                            val blendPosition = WAV_HEADER_BYTES + blendStart * PCM16_STEREO_BYTES
                            wav.seek(blendPosition)
                            wav.readFully(oldBytes)
                            for (frame in 0 until overlap.toInt()) {
                                val alpha = (frame + 1f) / (overlap.toFloat() + 1f)
                                val sampleOffset = frame * 4
                                val oldLeft = readPcm16(oldBytes, sampleOffset)
                                val oldRight = readPcm16(oldBytes, sampleOffset + 2)
                                val blendedLeft = oldLeft * (1f - alpha) + current[frame * 2] * 32767f * alpha
                                val blendedRight = oldRight * (1f - alpha) + current[frame * 2 + 1] * 32767f * alpha
                                putPcm16(blendBytes, sampleOffset, blendedLeft.roundToInt())
                                putPcm16(blendBytes, sampleOffset + 2, blendedRight.roundToInt())
                            }
                            wav.seek(blendPosition)
                            wav.write(blendBytes)
                            wav.seek(WAV_HEADER_BYTES + writtenFrames * PCM16_STEREO_BYTES)
                        }
                        var remaining = segmentFrames - overlap
                        val frameCapacity = IO_BUFFER_BYTES / PCM16_STEREO_BYTES.toInt()
                        val pcmBuffer = ByteArray(IO_BUFFER_BYTES)
                        while (remaining > 0L) {
                            val framesThisBlock = min(remaining, frameCapacity.toLong()).toInt()
                            var offset = 0
                            repeat(framesThisBlock) {
                                val left = input.readFloat().finiteSample()
                                val right = input.readFloat().finiteSample()
                                putPcm16(pcmBuffer, offset, (left * 32767f).roundToInt())
                                putPcm16(pcmBuffer, offset + 2, (right * 32767f).roundToInt())
                                offset += 4
                            }
                            wav.write(pcmBuffer, 0, offset)
                            writtenFrames += framesThisBlock
                            remaining -= framesThisBlock
                        }
                    }
                    host.update(
                        phase = "Writing instrumental WAV",
                        progress = 0.91f + 0.06f * (index + 1f) / segments.size,
                        message = "Joined audio chunk ${index + 1} of ${segments.size}",
                        completedChunks = completedChunks,
                        totalChunks = totalChunks,
                    )
                }

                check(writtenFrames == expectedFrames) { "The joined WAV has an unexpected duration" }
                val dataBytes = writtenFrames * PCM16_STEREO_BYTES
                check(dataBytes <= MAX_RIFF_DATA_BYTES) {
                    "This WAV would exceed the 4 GB WAV file limit. Try a shorter song."
                }
                wav.seek(0L)
                writeAscii(wav, "RIFF")
                writeIntLe(wav, (36L + dataBytes).toInt())
                writeAscii(wav, "WAVE")
                writeAscii(wav, "fmt ")
                writeIntLe(wav, 16)
                writeShortLe(wav, 1)
                writeShortLe(wav, 2)
                writeIntLe(wav, sampleRate)
                writeIntLe(wav, sampleRate * PCM16_STEREO_BYTES.toInt())
                writeShortLe(wav, PCM16_STEREO_BYTES.toInt())
                writeShortLe(wav, 16)
                writeAscii(wav, "data")
                writeIntLe(wav, dataBytes.toInt())
                wav.fd.sync()
            }
            check(part.length() >= WAV_HEADER_BYTES) { "The WAV file was not created" }
            output.delete()
            check(part.renameTo(output)) { "Could not finalize the instrumental WAV" }
        } catch (t: Throwable) {
            part.delete()
            throw t
        }
    }

    private fun readPcm16(bytes: ByteArray, offset: Int): Int {
        val value = (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
        return if (value >= 32768) value - 65536 else value
    }

    private fun putPcm16(bytes: ByteArray, offset: Int, value: Int) {
        val sample = value.coerceIn(-32768, 32767)
        bytes[offset] = sample.toByte()
        bytes[offset + 1] = (sample shr 8).toByte()
    }

    private fun writeShortLe(file: RandomAccessFile, value: Int) {
        file.write(value and 0xff)
        file.write((value ushr 8) and 0xff)
    }

    private fun writeIntLe(file: RandomAccessFile, value: Int) {
        file.write(value and 0xff)
        file.write((value ushr 8) and 0xff)
        file.write((value ushr 16) and 0xff)
        file.write((value ushr 24) and 0xff)
    }

    private fun writeAscii(file: RandomAccessFile, value: String) {
        file.write(value.toByteArray(Charsets.US_ASCII))
    }

    private fun Float.finiteSample(): Float = if (isFinite()) coerceIn(-1f, 1f) else 0f

    private const val WAV_HEADER_BYTES = 44
    private const val PCM16_STEREO_BYTES = 4L
    private const val STEREO_FLOAT_BYTES = 8L
    private const val MAX_RIFF_DATA_BYTES = 0xffff_ffffL - 36L
    private const val IO_BUFFER_BYTES = 256 * 1024
}
