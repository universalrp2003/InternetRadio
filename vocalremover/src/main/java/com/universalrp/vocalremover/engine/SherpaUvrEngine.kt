package com.universalrp.vocalremover.engine

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.Structure

/** Result contains the estimated non-vocal/accompaniment stem, de-interleaved by channel. */
data class SeparatedPcm(val channels: Array<FloatArray>, val sampleRate: Int)

/**
 * Thin JNA binding for sherpa-onnx's public C source-separation API. This runs entirely in
 * process; only the model file path and PCM buffers are passed to native code.
 */
class SherpaUvrEngine(modelPath: String, threads: Int) : AutoCloseable {
    private val api: NativeLibrary
    private val handle: Pointer
    private val destroyEngine: Function
    private val process: Function
    private val destroyOutput: Function
    private val getSampleRate: Function
    val outputSampleRateHz: Int
    private var closed = false

    init {
        require(threads in 1..Runtime.getRuntime().availableProcessors().coerceAtLeast(1))
        // The standard Android AAR is JNI-only and does not export source separation's C API.
        // The verified Android FFI packages supply this library and its native dependencies.
        api = NativeLibrary.getInstance("sherpa-onnx-c-api")
        val create = api.getFunction("SherpaOnnxCreateOfflineSourceSeparation")
        destroyEngine = api.getFunction("SherpaOnnxDestroyOfflineSourceSeparation")
        process = api.getFunction("SherpaOnnxOfflineSourceSeparationProcess")
        destroyOutput = api.getFunction("SherpaOnnxDestroySourceSeparationOutput")
        getSampleRate = api.getFunction("SherpaOnnxOfflineSourceSeparationGetOutputSampleRate")

        val modelString = Memory(modelPath.toByteArray(Charsets.UTF_8).size + 1L)
        modelString.setString(0, modelPath, "UTF-8")
        val provider = Memory(4)
        provider.setString(0, "cpu", "UTF-8")
        val config = SeparationConfig()
        config.model.uvr.model = modelString
        config.model.num_threads = threads
        config.model.debug = 0
        config.model.provider = provider
        config.model.spleeter.write()
        config.model.uvr.write()
        config.model.write()
        config.write()

        handle = create.invokePointer(arrayOf(config.pointer))
            ?: throw IllegalStateException("The on-device separation model could not be opened")
        outputSampleRateHz = getSampleRate.invokeInt(arrayOf(handle))
        check(outputSampleRateHz > 0) { "The model returned an invalid output sample rate" }
    }

    @Synchronized
    fun separate(channels: Array<FloatArray>, inputSampleRate: Int): SeparatedPcm {
        check(!closed) { "Separator is closed" }
        require(channels.isNotEmpty() && channels.size <= 2) { "Only mono and stereo MP3 input is supported" }
        require(inputSampleRate in 8_000..192_000)
        val frameCount = channels.first().size
        require(frameCount > 0 && channels.all { it.size == frameCount })
        val channelMemory = channels.map { samples ->
            Memory(samples.size.toLong() * Float.SIZE_BYTES).apply {
                write(0, samples, 0, samples.size)
            }
        }
        val pointerArray = Memory(channels.size.toLong() * Native.POINTER_SIZE)
        channelMemory.forEachIndexed { index, memory ->
            pointerArray.setPointer(index.toLong() * Native.POINTER_SIZE, memory)
        }

        val separated = process.invokePointer(
            arrayOf(handle, pointerArray, channels.size, frameCount, inputSampleRate),
        ) ?: throw IllegalStateException("Source separation failed for this chunk")

        try {
            val nativeOutput = SourceOutput(separated).apply { read() }
            check(nativeOutput.num_stems >= 2) { "The model did not return an instrumental stem" }
            check(nativeOutput.sample_rate == outputSampleRateHz && nativeOutput.sample_rate > 0) {
                "The model returned an unexpected output sample rate"
            }
            val stemsPointer = nativeOutput.stems
                ?: throw IllegalStateException("The model returned no stems")
            val stemSize = Stem().size().toLong()
            val accompaniment = Stem(stemsPointer.share(stemSize)).apply { read() }
            val outputChannels = accompaniment.num_channels
            val outputFrames = accompaniment.n
            check(outputChannels in 1..2 && outputFrames > 0) {
                "The model returned an unsupported audio layout"
            }
            val samplePointers = accompaniment.samples
                ?: throw IllegalStateException("The model returned no channel samples")
            val result = Array(outputChannels) { channel ->
                val samplePointer = samplePointers.getPointer(channel.toLong() * Native.POINTER_SIZE)
                    ?: throw IllegalStateException("Missing output channel")
                samplePointer.getFloatArray(0, outputFrames)
            }
            return SeparatedPcm(result, nativeOutput.sample_rate)
        } finally {
            destroyOutput.invokeVoid(arrayOf(separated))
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        try {
            destroyEngine.invokeVoid(arrayOf(handle))
        } finally {
            api.close()
        }
    }

    // JNA reflects over nested Structure field types to calculate native layout. Keep these
    // classes public so it can call their public no-argument constructors on device.
    @Structure.FieldOrder("vocals", "accompaniment")
    class SpleeterConfig : Structure() {
        @JvmField var vocals: Pointer? = null
        @JvmField var accompaniment: Pointer? = null
    }

    @Structure.FieldOrder("model")
    class UvrConfig : Structure() {
        @JvmField var model: Pointer? = null
    }

    @Structure.FieldOrder("spleeter", "uvr", "num_threads", "debug", "provider")
    class ModelConfig : Structure() {
        @JvmField var spleeter: SpleeterConfig = SpleeterConfig()
        @JvmField var uvr: UvrConfig = UvrConfig()
        @JvmField var num_threads: Int = 1
        @JvmField var debug: Int = 0
        @JvmField var provider: Pointer? = null
    }

    @Structure.FieldOrder("model")
    class SeparationConfig : Structure() {
        @JvmField var model: ModelConfig = ModelConfig()
    }

    @Structure.FieldOrder("stems", "num_stems", "sample_rate")
    class SourceOutput : Structure {
        constructor() : super()
        constructor(pointer: Pointer) : super(pointer)

        @JvmField var stems: Pointer? = null
        @JvmField var num_stems: Int = 0
        @JvmField var sample_rate: Int = 0
    }

    @Structure.FieldOrder("samples", "num_channels", "n")
    class Stem : Structure {
        constructor() : super()
        constructor(pointer: Pointer) : super(pointer)

        @JvmField var samples: Pointer? = null
        @JvmField var num_channels: Int = 0
        @JvmField var n: Int = 0
    }
}
