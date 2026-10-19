package com.universalrp.vocalremover.processing

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** One durable, single-song job. Chunk files are the resume checkpoints. */
data class SeparationJob(
    val id: String,
    val sourceName: String,
    val sourceUri: String,
    val sourceFilePath: String = "",
    val pcmFilePath: String = "",
    val sampleRate: Int = 0,
    val outputSampleRate: Int = 0,
    val channels: Int = 0,
    val totalFrames: Long = 0L,
    val completedChunks: Int = 0,
    val totalChunks: Int = 0,
    val profile: ProcessingProfile = ProcessingProfile.BALANCED,
    val status: JobStatus = JobStatus.QUEUED,
    val phase: String = "Waiting to start",
    val progress: Float = 0f,
    val message: String = "",
    val pauseReason: PauseReason? = null,
    /** Last fully published instrumental WAV; it is never overwritten by a later pass. */
    val outputUri: String = "",
    /** True only while a WAV/MP3 MediaStore row is incomplete and safe to rewrite or discard. */
    val pendingWavUri: String = "",
    val pendingMp3Uri: String = "",
    val separationPass: SeparationPass = SeparationPass.FIRST,
    val operation: JobOperation = JobOperation.SEPARATION,
    val firstPassOutputUri: String = "",
    val extraPassInputUri: String = "",
    val mp3SourceUri: String = "",
    val mp3OutputUri: String = "",
    val privateOutputPath: String = "",
    val updatedAtMs: Long = System.currentTimeMillis(),
)

enum class JobStatus { QUEUED, RUNNING, PAUSING, PAUSED, COMPLETED, FAILED, CANCELED }
enum class SeparationPass { FIRST, EXTRA_VOCAL_REDUCTION }
enum class JobOperation { SEPARATION, MP3_EXPORT }

object SeparationJobStore {
    private val lock = Any()
    private val mutableJob = MutableStateFlow<SeparationJob?>(null)
    val job: StateFlow<SeparationJob?> = mutableJob.asStateFlow()

    fun initialize(context: Context) {
        synchronized(lock) { mutableJob.value = read(context) }
    }

    fun current(context: Context): SeparationJob? = synchronized(lock) {
        mutableJob.value ?: read(context).also { mutableJob.value = it }
    }

    fun save(context: Context, value: SeparationJob?) = synchronized(lock) {
        val file = AtomicFile(stateFile(context))
        if (value == null) {
            runCatching { file.delete() }
            mutableJob.value = null
            return
        }
        val json = JSONObject().apply {
            put("id", value.id)
            put("sourceName", value.sourceName)
            put("sourceUri", value.sourceUri)
            put("sourceFilePath", value.sourceFilePath)
            put("pcmFilePath", value.pcmFilePath)
            put("sampleRate", value.sampleRate)
            put("outputSampleRate", value.outputSampleRate)
            put("channels", value.channels)
            put("totalFrames", value.totalFrames)
            put("completedChunks", value.completedChunks)
            put("totalChunks", value.totalChunks)
            put("profile", value.profile.name)
            put("status", value.status.name)
            put("phase", value.phase)
            put("progress", value.progress.toDouble())
            put("message", value.message)
            put("pauseReason", value.pauseReason?.name ?: "")
            put("outputUri", value.outputUri)
            put("pendingWavUri", value.pendingWavUri)
            put("pendingMp3Uri", value.pendingMp3Uri)
            put("separationPass", value.separationPass.name)
            put("operation", value.operation.name)
            put("firstPassOutputUri", value.firstPassOutputUri)
            put("extraPassInputUri", value.extraPassInputUri)
            put("mp3SourceUri", value.mp3SourceUri)
            put("mp3OutputUri", value.mp3OutputUri)
            put("privateOutputPath", value.privateOutputPath)
            put("updatedAtMs", value.updatedAtMs)
        }.toString().toByteArray(Charsets.UTF_8)

        var stream: FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(json)
            stream.fd.sync()
            file.finishWrite(stream)
        } catch (t: Throwable) {
            stream?.let { file.failWrite(it) }
            throw t
        }
        mutableJob.value = value
    }

    fun update(context: Context, transform: (SeparationJob?) -> SeparationJob?) {
        synchronized(lock) {
            val latest = mutableJob.value ?: read(context)
            save(context, transform(latest)?.copy(updatedAtMs = System.currentTimeMillis()))
        }
    }

    private fun read(context: Context): SeparationJob? = runCatching {
        val atomic = AtomicFile(stateFile(context))
        val base = atomic.baseFile
        if (!base.exists() && !File(base.path + ".bak").exists()) return null
        val json = atomic.openRead().bufferedReader().use { JSONObject(it.readText()) }
        SeparationJob(
            id = json.getString("id"),
            sourceName = json.optString("sourceName", "song.mp3"),
            sourceUri = json.optString("sourceUri"),
            sourceFilePath = json.optString("sourceFilePath"),
            pcmFilePath = json.optString("pcmFilePath"),
            sampleRate = json.optInt("sampleRate"),
            outputSampleRate = json.optInt("outputSampleRate"),
            channels = json.optInt("channels"),
            totalFrames = json.optLong("totalFrames"),
            completedChunks = json.optInt("completedChunks"),
            totalChunks = json.optInt("totalChunks"),
            profile = runCatching { ProcessingProfile.valueOf(json.optString("profile")) }
                .getOrDefault(ProcessingProfile.BALANCED),
            status = runCatching { JobStatus.valueOf(json.optString("status")) }
                .getOrDefault(JobStatus.FAILED),
            phase = json.optString("phase", "Recovered job"),
            progress = json.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f),
            message = json.optString("message"),
            pauseReason = json.optString("pauseReason").takeIf { it.isNotBlank() }
                ?.let { runCatching { PauseReason.valueOf(it) }.getOrNull() },
            outputUri = json.optString("outputUri"),
            pendingWavUri = json.optString("pendingWavUri"),
            pendingMp3Uri = json.optString("pendingMp3Uri"),
            separationPass = runCatching { SeparationPass.valueOf(json.optString("separationPass")) }
                .getOrDefault(SeparationPass.FIRST),
            operation = runCatching { JobOperation.valueOf(json.optString("operation")) }
                .getOrDefault(JobOperation.SEPARATION),
            firstPassOutputUri = json.optString("firstPassOutputUri"),
            extraPassInputUri = json.optString("extraPassInputUri"),
            mp3SourceUri = json.optString("mp3SourceUri"),
            mp3OutputUri = json.optString("mp3OutputUri"),
            privateOutputPath = json.optString("privateOutputPath"),
            updatedAtMs = json.optLong("updatedAtMs", System.currentTimeMillis()),
        )
    }.getOrNull()

    private fun stateFile(context: Context): File = File(context.filesDir, "separation-job.json")
}
