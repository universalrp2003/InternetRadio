package com.universalrp.vocalremover.processing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.StatFs
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.universalrp.vocalremover.MainActivity
import com.universalrp.vocalremover.R
import com.universalrp.vocalremover.audio.DecodedPcmInfo
import com.universalrp.vocalremover.audio.Mp3Decoder
import com.universalrp.vocalremover.audio.PublicWavExporter
import com.universalrp.vocalremover.health.DeviceMonitor
import com.universalrp.vocalremover.health.DeviceSnapshot
import com.universalrp.vocalremover.model.ModelRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/** In-process health values are deliberately labels/estimates, never presented as guaranteed sensors. */
object ServiceTelemetry {
    private val mutableDevice = MutableStateFlow<DeviceSnapshot?>(null)
    val device: StateFlow<DeviceSnapshot?> = mutableDevice.asStateFlow()
    internal fun publish(snapshot: DeviceSnapshot?) { mutableDevice.value = snapshot }
}

/** Long-running, visible, resumable on-device separation worker. */
class SeparationService : Service() {
    private lateinit var scope: CoroutineScope
    private lateinit var monitor: DeviceMonitor
    private val pauseRequested = MutableStateFlow(false)
    private val cancelRequested = MutableStateFlow(false)
    private var worker: Job? = null
    private var healthJob: Job? = null
    private var partialWakeLock: PowerManager.WakeLock? = null
    private var foregroundStarted = false
    @Volatile private var pauseMessage: String = "Paused by you. Resume when ready."
    @Volatile private var requestedPauseReason: PauseReason? = null
    @Volatile private var lastProgressWriteMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        monitor = DeviceMonitor(this)
        SeparationJobStore.initialize(this)
        createNotificationChannel()
        healthJob = scope.launch { monitorDeviceWhileActive() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> requestUserPause()
            ACTION_RESUME -> resumeJob()
            ACTION_CANCEL -> cancelJob()
            ACTION_RETRY -> retryJob()
            ACTION_DISCARD -> discardJob()
            ACTION_START, null -> restoreOrStart()
            else -> restoreOrStart()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Android 14 data-sync foreground-service time limit callback. */
    override fun onTimeout(startId: Int) {
        pauseForForegroundTimeLimit()
    }

    /** Android 15+ media-processing foreground-service time limit callback. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        pauseForForegroundTimeLimit()
    }

    private fun pauseForForegroundTimeLimit() {
        val current = SeparationJobStore.current(this)
        if (current == null || current.status in TERMINAL_STATES) {
            setCpuWakeLock(false)
            stopForegroundNow()
            stopSelf()
            return
        }

        pauseMessage = "Android's foreground-processing time allowance ended. Your work is saved; resume when Android allows."
        requestedPauseReason = PauseReason.SERVICE_LIMIT
        pauseRequested.value = true
        runCatching {
            SeparationJobStore.update(this) { job ->
                job?.copy(status = JobStatus.PAUSED, message = pauseMessage, pauseReason = PauseReason.SERVICE_LIMIT)
            }
        }
        setCpuWakeLock(false)
        // Coroutine cancellation cannot interrupt a native call mid-inference. Any chunk that
        // finishes is committed atomically; incomplete temporary files are discarded on recovery.
        worker?.cancel()
        val paused = SeparationJobStore.current(this) ?: current.copy(
            status = JobStatus.PAUSED,
            message = pauseMessage,
            pauseReason = PauseReason.SERVICE_LIMIT,
        )
        if (foregroundStarted) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
            foregroundStarted = false
        }
        runCatching { NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(paused)) }
        stopSelf()
    }

    override fun onDestroy() {
        setCpuWakeLock(false)
        healthJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun restoreOrStart() {
        val job = SeparationJobStore.current(this) ?: run {
            stopSelf()
            return
        }
        if (job.status in TERMINAL_STATES) {
            stopSelf()
            return
        }
        ensureForeground(job)
        when (job.status) {
            JobStatus.PAUSED, JobStatus.PAUSING -> {
                pauseRequested.value = true
                requestedPauseReason = job.pauseReason
                pauseMessage = job.message.ifBlank { "Paused. Resume when ready." }
                if (job.status == JobStatus.PAUSING) enterPausedState() else updateNotification(job)
            }
            else -> {
                pauseRequested.value = false
                cancelRequested.value = false
                launchWorker()
            }
        }
    }

    private fun requestUserPause() {
        val job = SeparationJobStore.current(this) ?: return stopSelf()
        if (job.status !in ACTIVE_STATES) return
        requestedPauseReason = PauseReason.USER
        pauseMessage = "Paused by you. The current short audio chunk will finish first."
        pauseRequested.value = true
        SeparationJobStore.update(this) { current ->
            current?.copy(
                status = JobStatus.PAUSING,
                phase = current.phase,
                message = pauseMessage,
                pauseReason = PauseReason.USER,
            )
        }
        ensureForeground(SeparationJobStore.current(this) ?: job)
        updateNotification(SeparationJobStore.current(this) ?: job)
        if (worker?.isActive != true) enterPausedState()
    }

    private fun resumeJob() {
        val job = SeparationJobStore.current(this) ?: return stopSelf()
        if (job.status !in ACTIVE_STATES && job.status != JobStatus.FAILED) return
        if (job.status == JobStatus.FAILED) return retryJob()
        cancelRequested.value = false
        pauseRequested.value = false
        requestedPauseReason = null
        pauseMessage = ""
        SeparationJobStore.update(this) { current ->
            current?.copy(
                status = JobStatus.RUNNING,
                message = "Resuming from the last completed checkpoint.",
                pauseReason = null,
            )
        }
        ensureForeground(SeparationJobStore.current(this) ?: job)
        setCpuWakeLock(true)
        launchWorker()
    }

    private fun cancelJob() {
        val job = SeparationJobStore.current(this) ?: return stopSelf()
        if (job.status in TERMINAL_STATES) return
        cancelRequested.value = true
        pauseRequested.value = false
        requestedPauseReason = PauseReason.USER
        pauseMessage = "Stopping at the next safe checkpoint."
        SeparationJobStore.update(this) { current ->
            current?.copy(status = JobStatus.PAUSING, message = pauseMessage)
        }
        if (worker?.isActive != true) finishCanceled(job)
        else updateNotification(SeparationJobStore.current(this) ?: job)
    }

    private fun retryJob() {
        val job = SeparationJobStore.current(this) ?: return stopSelf()
        if (job.status != JobStatus.FAILED) return restoreOrStart()
        cancelRequested.value = false
        pauseRequested.value = false
        requestedPauseReason = null
        SeparationJobStore.update(this) { current ->
            current?.copy(
                status = JobStatus.RUNNING,
                message = "Retrying from saved audio chunks.",
                pauseReason = null,
            )
        }
        ensureForeground(SeparationJobStore.current(this) ?: job)
        launchWorker()
    }

    private fun discardJob() {
        if (worker?.isActive == true) return
        val job = SeparationJobStore.current(this)
        if (job != null) {
            removePrivateWork(job)
            if (job.status != JobStatus.COMPLETED) removePendingOutput(job.outputUri)
        }
        SeparationJobStore.save(this, null)
        stopForegroundNow()
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        stopSelf()
    }

    private fun launchWorker() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            val initial = SeparationJobStore.current(this@SeparationService) ?: return@launch
            setCpuWakeLock(!pauseRequested.value)
            try {
                runJob(initial)
            } catch (canceled: CancellationException) {
                if (cancelRequested.value) finishCanceled(SeparationJobStore.current(this@SeparationService) ?: initial)
                else throw canceled
            } catch (failure: Throwable) {
                if (cancelRequested.value) {
                    finishCanceled(SeparationJobStore.current(this@SeparationService) ?: initial)
                } else {
                    setCpuWakeLock(false)
                    val message = failure.message?.take(280)?.ifBlank { null }
                        ?: "Processing stopped unexpectedly. The original song is unchanged."
                    SeparationJobStore.update(this@SeparationService) { current ->
                        current?.copy(
                            status = JobStatus.FAILED,
                            message = message,
                            phase = "Could not finish",
                            pauseReason = PauseReason.ERROR,
                        )
                    }
                    val failedJob = SeparationJobStore.current(this@SeparationService) ?: initial
                    if (foregroundStarted) {
                        ServiceCompat.stopForeground(this@SeparationService, ServiceCompat.STOP_FOREGROUND_DETACH)
                        foregroundStarted = false
                    }
                    runCatching {
                        NotificationManagerCompat.from(this@SeparationService)
                            .notify(NOTIFICATION_ID, buildNotification(failedJob))
                    }
                    stopSelf()
                }
            } finally {
                setCpuWakeLock(false)
            }
        }
    }

    private suspend fun runJob(initial: SeparationJob) {
        var job = SeparationJobStore.current(this) ?: initial
        val workDir = File(File(filesDir, "jobs"), job.id).apply { mkdirs() }
        val host = createProcessorHost()
        setJobStatus(JobStatus.RUNNING, "Preparing on-device processing", "Starting safely; the original song will not be changed.")
        val source = copySourceToPrivateStorage(job, workDir, host)
        job = SeparationJobStore.current(this) ?: job

        val modelRepository = ModelRepository(this)
        val model = modelRepository.installedFile() ?: run {
            awaitModelStorageHeadroom(host)
            updateJobProgress("Downloading separation model", 0.02f, "Downloading the 28.3 MiB model over HTTPS; song audio is not uploaded.")
            modelRepository.download(
                checkpoint = { awaitCheckpoint() },
                onProgress = { bytes, total ->
                    val percent = 0.02f + 0.10f * (bytes.toFloat() / total.coerceAtLeast(1L)).coerceIn(0f, 1f)
                    updateJobProgress("Downloading separation model", percent,
                        "${DeviceMonitor.formatBytes(bytes)} of ${DeviceMonitor.formatBytes(total)} downloaded")
                },
            )
        }
        updateJobProgress("Preparing MP3 decoder", 0.12f, "The downloaded model stays on this device and is checksum-verified.")
        if (!awaitCheckpoint()) throw CancellationException("Canceled")

        val pcmFile = File(workDir, "decoded-float.pcm")
        val recoveredInfo = recoverDecodedInfo(job, pcmFile)
        val decoded = recoveredInfo ?: Mp3Decoder().decodeToFloatFile(
            source = source,
            destination = pcmFile,
            checkpoint = { awaitCheckpoint() },
            onProgress = { value ->
                updateJobProgress(
                    phase = "Decoding MP3",
                    progress = 0.12f + 0.12f * value,
                    message = "Decoding locally · ${percent(value)}%",
                )
            },
        ).also { info ->
            SeparationJobStore.update(this) { current ->
                current?.copy(
                    pcmFilePath = pcmFile.absolutePath,
                    sampleRate = info.sampleRate,
                    outputSampleRate = 0,
                    channels = info.channels,
                    totalFrames = info.frames,
                    completedChunks = 0,
                    totalChunks = 0,
                )
            }
        }
        job = SeparationJobStore.current(this) ?: job
        val processor = ChunkedSeparationProcessor(monitor)
        val privateWav = processor.process(
            job = job,
            workDir = workDir,
            inputPcmFile = pcmFile,
            decoded = decoded,
            modelFile = model,
            host = host,
        )
        if (!awaitCheckpoint()) throw CancellationException("Canceled")

        val outputName = outputDisplayName(job.sourceName)
        updateJobProgress("Saving WAV", 0.97f, "Saving the instrumental as a stereo PCM WAV in Music/Vocal Remover Studio.")
        val outputUri = PublicWavExporter(this).export(
            source = privateWav,
            displayName = outputName,
            existingUri = (SeparationJobStore.current(this) ?: job).outputUri,
            host = host,
            onUriReady = { uri ->
                SeparationJobStore.update(this) { current -> current?.copy(outputUri = uri) }
            },
            onProgress = { value ->
                updateJobProgress(
                    phase = "Saving WAV",
                    progress = 0.97f + 0.025f * value,
                    message = "Copying instrumental WAV to Music · ${percent(value)}%",
                )
            },
        )
        SeparationJobStore.update(this) { current ->
            current?.copy(
                status = JobStatus.COMPLETED,
                phase = "Instrumental ready",
                progress = 1f,
                message = "Saved to Music/Vocal Remover Studio. The source song was left unchanged.",
                outputUri = outputUri,
                sourceFilePath = "",
                pcmFilePath = "",
                privateOutputPath = "",
                pauseReason = null,
                completedChunks = current.totalChunks,
            )
        }
        removePrivateWork(job)
        setCpuWakeLock(false)
        updateNotification(SeparationJobStore.current(this) ?: job)
        stopForegroundNow()
        stopSelf()
    }

    private suspend fun awaitModelStorageHeadroom(host: ProcessorHost) {
        val required = ModelRepository.MODEL_BYTES + STORAGE_SAFETY_RESERVE_BYTES
        while (true) {
            if (!host.checkpoint()) throw CancellationException("Canceled before model download")
            val available = monitor.read().availableStorageBytes
                ?: throw IllegalStateException("Free app storage could not be measured safely; check device storage, then retry.")
            if (available >= required) return
            val shortfall = required - available
            val message = "Downloading the 28.3 MiB model while keeping a 96 MiB safety reserve needs " +
                "about ${DeviceMonitor.formatBytes(required)} free; only ${DeviceMonitor.formatBytes(available)} is available. " +
                "Free about ${DeviceMonitor.formatBytes(shortfall)} more, then resume."
            if (!host.checkpoint(PauseReason.STORAGE, message)) {
                throw CancellationException("Canceled before model download")
            }
        }
    }

    private suspend fun copySourceToPrivateStorage(
        job: SeparationJob,
        workDir: File,
        host: ProcessorHost,
    ): File {
        val destination = File(workDir, "source.mp3")
        val currentPath = job.sourceFilePath.takeIf { it.isNotBlank() }?.let(::File)
        currentPath?.takeIf { it.isFile && it.length() > 0L }?.let { return it }
        if (destination.isFile && destination.length() > 0L) return destination

        val uri = runCatching { Uri.parse(job.sourceUri) }.getOrElse {
            throw IllegalStateException("The selected song cannot be opened")
        }
        val input = contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Android could not read the selected MP3")
        val part = File(workDir, "source.mp3.part").apply { delete() }
        updateJobProgress("Copying song to app storage", 0.005f, "Copying a private working copy; the selected original is not modified.")
        var expectedBytes = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0).takeIf { it > 0L } else null
            }
        }.getOrNull()
        if (expectedBytes == null) expectedBytes = runCatching {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length.takeIf { length -> length > 0L } }
        }.getOrNull()

        try {
            input.buffered().use { source ->
                val fileOut = FileOutputStream(part)
                fileOut.use { raw ->
                    BufferedOutputStream(raw, 512 * 1024).use { target ->
                        val buffer = ByteArray(256 * 1024)
                        var copied = 0L
                        while (true) {
                            if (!awaitCheckpoint()) throw CancellationException("Canceled while copying song")
                            val free = runCatching { StatFs(filesDir.absolutePath).availableBytes }
                                .getOrNull()?.takeIf { it >= 0L }
                                ?: throw IllegalStateException("Free app storage could not be measured safely; check device storage, then retry.")
                            val remainingInputBytes = expectedBytes?.let { (it - copied).coerceAtLeast(0L) }
                            val requiredBytes = remainingInputBytes?.let { remaining ->
                                if (remaining > Long.MAX_VALUE - STORAGE_SAFETY_RESERVE_BYTES) Long.MAX_VALUE
                                else remaining + STORAGE_SAFETY_RESERVE_BYTES
                            } ?: STORAGE_SAFETY_RESERVE_BYTES
                            if (free < requiredBytes) {
                                val shortfall = requiredBytes - free
                                val message = "Copying the selected song while keeping a 96 MiB safety reserve needs about " +
                                    "${DeviceMonitor.formatBytes(requiredBytes)} free; only ${DeviceMonitor.formatBytes(free)} is available. " +
                                    "Free about ${DeviceMonitor.formatBytes(shortfall)} more, then resume."
                                if (!host.checkpoint(PauseReason.STORAGE, message)) {
                                    throw CancellationException("Canceled while copying song")
                                }
                            }
                            val count = source.read(buffer)
                            if (count < 0) break
                            target.write(buffer, 0, count)
                            copied += count
                            val fraction = expectedBytes?.let { copied.toFloat() / it.coerceAtLeast(1L) }
                                ?.coerceIn(0f, 1f) ?: 0f
                            updateJobProgress("Copying song to app storage", 0.005f + fraction * 0.015f,
                                "${DeviceMonitor.formatBytes(copied)} copied to private app storage")
                        }
                        target.flush()
                        raw.fd.sync()
                    }
                }
            }
            check(part.length() > 0L) { "The selected MP3 is empty" }
            destination.delete()
            check(part.renameTo(destination)) { "Could not save the private song copy" }
            SeparationJobStore.update(this) { current -> current?.copy(sourceFilePath = destination.absolutePath) }
            return destination
        } catch (t: Throwable) {
            part.delete()
            throw t
        }
    }

    private fun recoverDecodedInfo(job: SeparationJob, pcmFile: File): DecodedPcmInfo? {
        val savedFile = job.pcmFilePath.takeIf { it.isNotBlank() }?.let(::File) ?: pcmFile
        if (savedFile != pcmFile && savedFile.absolutePath != pcmFile.absolutePath) return null
        if (!savedFile.isFile || job.sampleRate <= 0 || job.channels !in 1..2 || job.totalFrames <= 0L) return null
        val expectedBytes = job.totalFrames * job.channels * Float.SIZE_BYTES
        if (savedFile.length() != expectedBytes) return null
        return DecodedPcmInfo(
            sampleRate = job.sampleRate,
            channels = job.channels,
            frames = job.totalFrames,
            durationUs = job.totalFrames * 1_000_000L / job.sampleRate,
        )
    }

    private fun createProcessorHost() = object : ProcessorHost {
        override suspend fun checkpoint(
            reason: PauseReason?,
            message: String,
            beforePause: () -> Unit,
        ): Boolean = awaitCheckpoint(reason, message, beforePause)

        override fun isCanceled(): Boolean = cancelRequested.value

        override fun update(
            phase: String,
            progress: Float,
            message: String,
            completedChunks: Int?,
            totalChunks: Int?,
            outputSampleRate: Int?,
            privateOutputPath: String?,
        ) {
            updateJobProgress(
                phase = phase,
                progress = progress,
                message = message,
                completedChunks = completedChunks,
                totalChunks = totalChunks,
                outputSampleRate = outputSampleRate,
                privateOutputPath = privateOutputPath,
            )
        }
    }

    private suspend fun awaitCheckpoint(
        reason: PauseReason? = null,
        message: String = "",
        beforePause: () -> Unit = {},
    ): Boolean {
        if (cancelRequested.value) return false
        if (reason != null) {
            requestedPauseReason = reason
            pauseMessage = message.ifBlank { safetyPauseMessage(reason) }
            pauseRequested.value = true
            SeparationJobStore.update(this) { current ->
                current?.copy(status = JobStatus.PAUSING, message = pauseMessage, pauseReason = reason)
            }
        }
        if (cancelRequested.value) return false
        if (!pauseRequested.value) return true

        // Native model sessions hold substantial RAM; release them before waiting while paused.
        beforePause()
        enterPausedState()
        while (pauseRequested.value && !cancelRequested.value) {
            pauseRequested.first { !it || cancelRequested.value }
        }
        if (cancelRequested.value) return false
        requestedPauseReason = null
        setCpuWakeLock(true)
        SeparationJobStore.update(this) { current ->
            current?.copy(status = JobStatus.RUNNING, message = "Resumed from the last safe checkpoint.", pauseReason = null)
        }
        updateNotification(SeparationJobStore.current(this))
        return true
    }

    private fun enterPausedState() {
        setCpuWakeLock(false)
        val current = SeparationJobStore.current(this) ?: return
        if (current.status != JobStatus.PAUSED) {
            SeparationJobStore.update(this) { job ->
                job?.copy(
                    status = JobStatus.PAUSED,
                    message = pauseMessage.ifBlank { "Paused at a safe checkpoint." },
                    pauseReason = requestedPauseReason ?: job.pauseReason,
                )
            }
        }
        updateNotification(SeparationJobStore.current(this))
    }

    private fun requestSafetyPause(reason: PauseReason, message: String) {
        if (pauseRequested.value || cancelRequested.value) return
        requestedPauseReason = reason
        pauseMessage = message
        pauseRequested.value = true
        SeparationJobStore.update(this) { current ->
            current?.copy(status = JobStatus.PAUSING, message = message, pauseReason = reason)
        }
        updateNotification(SeparationJobStore.current(this))
    }

    private suspend fun monitorDeviceWhileActive() {
        while (scope.isActive) {
            delay(2_000)
            val job = SeparationJobStore.current(this) ?: continue
            if (job.status !in ACTIVE_STATES) continue
            val snapshot = runCatching { monitor.read() }.getOrNull() ?: continue
            ServiceTelemetry.publish(snapshot)
            if (job.status != JobStatus.RUNNING || pauseRequested.value) continue
            val decision = ProcessingPolicy.decide(
                profile = job.profile,
                availableCores = Runtime.getRuntime().availableProcessors(),
                snapshot = snapshot.toSafetySnapshot(),
            )
            if (decision.pause) {
                val text = when (decision.reason) {
                    PauseReason.THERMAL -> "Android reports severe thermal status. Let the device cool, then resume."
                    PauseReason.LOW_MEMORY -> "Available system RAM is low. Close other apps, then resume."
                    PauseReason.STORAGE -> "Free storage is low. Make space, then resume."
                    else -> "Processing paused for device safety."
                }
                requestSafetyPause(decision.reason ?: PauseReason.ERROR, text)
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

    private fun updateJobProgress(
        phase: String,
        progress: Float,
        message: String,
        completedChunks: Int? = null,
        totalChunks: Int? = null,
        outputSampleRate: Int? = null,
        privateOutputPath: String? = null,
    ) {
        val now = System.currentTimeMillis()
        val current = SeparationJobStore.current(this) ?: return
        if (current.status == JobStatus.PAUSED) return
        val requestedProgress = progress.coerceIn(0f, 1f)
        val monotonicProgress = maxOf(current.progress.coerceIn(0f, 1f), requestedProgress)
        val completionChanged = completedChunks != null && completedChunks != current.completedChunks
        val phaseChanged = phase != current.phase
        val percentChanged = kotlin.math.abs(monotonicProgress - current.progress) >= 0.005f
        if (!completionChanged && !phaseChanged && !percentChanged && now - lastProgressWriteMs < 900L) return
        lastProgressWriteMs = now
        SeparationJobStore.update(this) { job ->
            job?.copy(
                phase = phase,
                progress = maxOf(job.progress.coerceIn(0f, 1f), requestedProgress),
                message = message,
                completedChunks = completedChunks ?: job.completedChunks,
                totalChunks = totalChunks ?: job.totalChunks,
                outputSampleRate = outputSampleRate ?: job.outputSampleRate,
                privateOutputPath = privateOutputPath ?: job.privateOutputPath,
                status = if (job.status == JobStatus.PAUSED || job.status == JobStatus.PAUSING) job.status else JobStatus.RUNNING,
            )
        }
        updateNotification(SeparationJobStore.current(this))
    }

    private fun setJobStatus(status: JobStatus, phase: String, message: String) {
        SeparationJobStore.update(this) { current -> current?.copy(status = status, phase = phase, message = message) }
        updateNotification(SeparationJobStore.current(this))
    }

    private fun finishCanceled(job: SeparationJob) {
        setCpuWakeLock(false)
        removePrivateWork(job)
        removePendingOutput(job.outputUri)
        SeparationJobStore.update(this) { current ->
            current?.copy(
                status = JobStatus.CANCELED,
                phase = "Canceled",
                message = "Canceled safely. Your selected original song was not changed.",
                sourceFilePath = "",
                pcmFilePath = "",
                privateOutputPath = "",
                outputUri = "",
                pauseReason = null,
            )
        }
        pauseRequested.value = false
        cancelRequested.value = false
        updateNotification(SeparationJobStore.current(this) ?: job)
        stopForegroundNow()
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        stopSelf()
    }

    private fun removePrivateWork(job: SeparationJob) {
        runCatching { File(File(filesDir, "jobs"), job.id).deleteRecursively() }
    }

    private fun removePendingOutput(uriValue: String) {
        if (uriValue.isBlank()) return
        runCatching { contentResolver.delete(Uri.parse(uriValue), null, null) }
    }

    private fun setCpuWakeLock(held: Boolean) {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        if (partialWakeLock == null) {
            partialWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VocalRemover:separation")
                .apply { setReferenceCounted(false) }
        }
        runCatching {
            if (held && partialWakeLock?.isHeld != true) partialWakeLock?.acquire()
            if (!held && partialWakeLock?.isHeld == true) partialWakeLock?.release()
        }
    }

    private fun ensureForeground(job: SeparationJob) {
        val notification = buildNotification(job)
        if (Build.VERSION.SDK_INT >= 35) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING,
            )
        } else if (Build.VERSION.SDK_INT >= 29) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        foregroundStarted = true
    }

    private fun updateNotification(job: SeparationJob?) {
        if (job == null) return
        if (!foregroundStarted && job.status !in TERMINAL_STATES) {
            ensureForeground(job)
            return
        }
        runCatching { NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(job)) }
    }

    private fun buildNotification(job: SeparationJob): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val content = PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(notificationText(job))
            .setContentIntent(content)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(job.status in ACTIVE_STATES)
            .setProgress(100, (job.progress * 100).roundToInt().coerceIn(0, 100), job.status == JobStatus.PAUSING)

        if (job.status == JobStatus.PAUSED) {
            builder.addAction(
                android.R.drawable.ic_media_play,
                "Resume",
                servicePendingIntent(ACTION_RESUME, REQUEST_RESUME),
            )
        } else if (job.status == JobStatus.FAILED) {
            builder.addAction(
                android.R.drawable.ic_menu_rotate,
                "Retry",
                servicePendingIntent(ACTION_RETRY, REQUEST_RETRY),
            )
        } else if (job.status == JobStatus.RUNNING || job.status == JobStatus.QUEUED) {
            builder.addAction(
                android.R.drawable.ic_media_pause,
                "Pause",
                servicePendingIntent(ACTION_PAUSE, REQUEST_PAUSE),
            )
        }
        if (job.status !in TERMINAL_STATES) {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                servicePendingIntent(ACTION_CANCEL, REQUEST_CANCEL),
            )
        }
        return builder.build()
    }

    private fun notificationText(job: SeparationJob): String = when (job.status) {
        JobStatus.PAUSED -> job.message.ifBlank { "Paused at a safe checkpoint" }
        JobStatus.PAUSING -> job.message.ifBlank { "Finishing the current short audio chunk before pausing" }
        JobStatus.FAILED -> "${job.phase}: ${job.message}"
        JobStatus.COMPLETED -> "Instrumental WAV saved; original unchanged"
        JobStatus.CANCELED -> "Canceled; original unchanged"
        else -> "${job.phase} · ${(job.progress * 100).roundToInt()}%"
    }.take(180)

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, SeparationService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Vocal separation", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Visible progress and pause/resume controls for local song processing"
                setShowBadge(false)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun stopForegroundNow() {
        if (foregroundStarted) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }
    }

    private fun outputDisplayName(sourceName: String): String {
        val base = sourceName.substringBeforeLast('.', sourceName).take(72)
            .replace(Regex("[^A-Za-z0-9 _().-]"), "_")
            .trim().ifBlank { "Song" }
        return "$base - Instrumental.wav"
    }

    private fun percent(value: Float): Int = (value.coerceIn(0f, 1f) * 100).roundToInt()

    private fun safetyPauseMessage(reason: PauseReason): String = when (reason) {
        PauseReason.THERMAL -> "Android reports severe thermal status. Let the device cool, then resume."
        PauseReason.LOW_MEMORY -> "Available system RAM is low. Close other apps, then resume."
        PauseReason.STORAGE -> "Free storage is low. Make space, then resume."
        PauseReason.SERVICE_LIMIT -> "Android's foreground-processing time allowance ended. Your work is saved; resume when Android allows."
        PauseReason.USER -> "Paused by you. Resume when ready."
        PauseReason.ERROR -> "Processing paused after an error."
    }

    companion object {
        const val ACTION_START = "com.universalrp.vocalremover.action.START"
        const val ACTION_PAUSE = "com.universalrp.vocalremover.action.PAUSE"
        const val ACTION_RESUME = "com.universalrp.vocalremover.action.RESUME"
        const val ACTION_CANCEL = "com.universalrp.vocalremover.action.CANCEL"
        const val ACTION_RETRY = "com.universalrp.vocalremover.action.RETRY"
        const val ACTION_DISCARD = "com.universalrp.vocalremover.action.DISCARD"

        private const val CHANNEL_ID = "vocal_remover_processing"
        const val NOTIFICATION_ID = 4103
        private const val REQUEST_OPEN = 4104
        private const val REQUEST_PAUSE = 4105
        private const val REQUEST_RESUME = 4106
        private const val REQUEST_CANCEL = 4107
        private const val REQUEST_RETRY = 4108
        private const val STORAGE_SAFETY_RESERVE_BYTES = 96L * 1024L * 1024L
        private val ACTIVE_STATES = setOf(JobStatus.QUEUED, JobStatus.RUNNING, JobStatus.PAUSING, JobStatus.PAUSED)
        private val TERMINAL_STATES = setOf(JobStatus.COMPLETED, JobStatus.CANCELED, JobStatus.FAILED)
    }
}
