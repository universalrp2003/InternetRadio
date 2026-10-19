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
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.universalrp.vocalremover.MainActivity
import com.universalrp.vocalremover.R
import com.universalrp.vocalremover.audio.DecodedPcmInfo
import com.universalrp.vocalremover.audio.Mp3Decoder
import com.universalrp.vocalremover.audio.PcmWavDecoder
import com.universalrp.vocalremover.audio.PublicMp3Exporter
import com.universalrp.vocalremover.audio.PublicWavExporter
import com.universalrp.vocalremover.health.DeviceMonitor
import com.universalrp.vocalremover.health.DeviceSnapshot
import com.universalrp.vocalremover.model.ModelRepository
import com.universalrp.updates.AppLanguageState
import com.universalrp.updates.AppStrings
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

/** Long-running, visible, resumable on-device separation and export worker. */
class SeparationService : Service() {
    private lateinit var scope: CoroutineScope
    private lateinit var monitor: DeviceMonitor
    private val pauseRequested = MutableStateFlow(false)
    private val cancelRequested = MutableStateFlow(false)
    private var worker: Job? = null
    private var queueWaiter: Job? = null
    private var retryWaiter: Job? = null
    @Volatile private var retryRequestedJobId: String? = null
    private var workerStartId: Int = 0
    private var latestStartId: Int = 0
    private var healthJob: Job? = null
    private val lowMemoryAutoResumeGate = LowMemoryAutoResumeGate()
    private var partialWakeLock: PowerManager.WakeLock? = null
    private var foregroundStarted = false
    @Volatile private var pauseMessage: String = "Paused by you. Resume when ready."
    @Volatile private var requestedPauseReason: PauseReason? = null
    @Volatile private var lastProgressWriteMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        AppLanguageState.initialize(this)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        monitor = DeviceMonitor(this)
        SeparationJobStore.initialize(this)
        ProcessingQueueStore.initialize(this)
        createNotificationChannel()
        healthJob = scope.launch { monitorDeviceWhileActive() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        return try {
            when (intent?.action) {
                ACTION_PAUSE -> requestUserPause()
                ACTION_RESUME -> resumeJob()
                ACTION_CANCEL -> cancelJob()
                ACTION_RETRY -> retryJob()
                ACTION_DISCARD -> discardJob()
                ACTION_EXTRA_VOCAL_REDUCTION -> startExtraVocalReduction()
                ACTION_EXPORT_MP3 -> startMp3Export()
                ACTION_QUEUE -> restoreOrStart()
                ACTION_START, null -> restoreOrStart()
                else -> restoreOrStart()
            }
            START_STICKY
        } catch (failure: Exception) {
            recordStartFailure(failure, startId)
            START_NOT_STICKY
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun recordStartFailure(failure: Exception, startId: Int) {
        val detail = failure.message?.take(180)?.ifBlank { null } ?: failure.javaClass.simpleName
        Log.e("VocalRemoverService", "Could not start or control the processing foreground service", failure)
        runCatching { setCpuWakeLock(false) }
        runCatching {
            SeparationJobStore.update(this) { current ->
                if (current == null || current.status in TERMINAL_STATES) current
                else current.copy(
                    status = JobStatus.FAILED,
                    phase = "Could not start processing",
                    message = "Android could not start the background service (${failure.javaClass.simpleName}): $detail",
                    pauseReason = PauseReason.ERROR,
                )
            }
        }.onFailure { Log.e("VocalRemoverService", "Could not save the service-start error", it) }
        runCatching { SeparationJobStore.current(this)?.let(::syncQueueItemWithJob) }
        runCatching { stopForegroundNow() }
        runCatching { stopSelf(startId) }
    }

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
        syncQueueItemWithJob(paused)
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
        val job = SeparationJobStore.current(this)
        if (job != null && job.status in ACTIVE_STATES) {
            ensureForeground(job)
            if (cancelRequested.value && worker?.isActive == true) {
                deferRestoreUntilWorkerStops()
                return
            }
            if (job.status == JobStatus.PAUSED || job.status == JobStatus.PAUSING) {
                pauseRequested.value = true
                requestedPauseReason = job.pauseReason
                pauseMessage = if (job.pauseReason == PauseReason.LOW_MEMORY) {
                    lowMemoryPauseMessage()
                } else {
                    job.message.ifBlank { "Paused. Resume when ready." }
                }
                SeparationJobStore.update(this) { current ->
                    if (current != null && current.id == job.id && current.pauseReason == PauseReason.LOW_MEMORY) {
                        current.copy(message = pauseMessage)
                    } else current
                }
                val restored = SeparationJobStore.current(this) ?: job
                if (job.status == JobStatus.PAUSING) enterPausedState() else updateNotification(restored)
            } else {
                pauseRequested.value = false
                cancelRequested.value = false
                if (worker?.isActive == true) deferRestoreUntilWorkerStops() else launchWorker()
            }
            return
        }

        job?.let { terminalJob ->
            val queueEntry = ProcessingQueueStore.find(this, terminalJob.id)
            if (queueEntry?.status != AudioQueueStatus.WAITING) syncQueueItemWithJob(terminalJob)
        }
        if (worker?.isActive == true) {
            deferRestoreUntilWorkerStops()
            return
        }
        startNextQueuedItemOrStop()
    }

    private fun deferRestoreUntilWorkerStops() {
        val finishingWorker = worker?.takeIf { it.isActive } ?: return restoreOrStart()
        if (queueWaiter?.isActive == true) return
        queueWaiter = scope.launch {
            finishingWorker.join()
            queueWaiter = null
            restoreOrStart()
        }
    }

    private fun startNextQueuedItemOrStop() {
        val next = ProcessingQueueStore.nextWaiting(this)
        if (next == null) {
            val current = SeparationJobStore.current(this)
            if (current != null) updateNotification(current)
            stopForegroundNow()
            stopSelfResult(latestStartId)
            return
        }

        val job = activateQueuedItem(next)
        pauseRequested.value = false
        cancelRequested.value = false
        requestedPauseReason = null
        ensureForeground(job)
        setCpuWakeLock(true)
        launchWorker()
    }

    private fun activateQueuedItem(item: AudioQueueItem): SeparationJob {
        val job = SeparationJob(
            id = item.id,
            sourceName = item.sourceName,
            sourceUri = item.sourceUri,
            profile = item.profile,
            status = JobStatus.QUEUED,
            phase = "Waiting to start",
            message = "Starting the next song in the queue. The selected original will remain unchanged.",
        )
        SeparationJobStore.save(this, job)
        ProcessingQueueStore.update(this, item.id) { current ->
            current.copy(status = AudioQueueStatus.PROCESSING, message = "Processing on this device", outputUri = "")
        }
        return job
    }

    private fun syncQueueItemWithJob(job: SeparationJob) {
        val status = when (job.status) {
            JobStatus.QUEUED, JobStatus.RUNNING, JobStatus.PAUSING -> AudioQueueStatus.PROCESSING
            JobStatus.PAUSED -> AudioQueueStatus.PAUSED
            JobStatus.COMPLETED -> AudioQueueStatus.COMPLETED
            JobStatus.FAILED -> AudioQueueStatus.FAILED
            JobStatus.CANCELED -> AudioQueueStatus.CANCELED
        }
        ProcessingQueueStore.update(this, job.id) { item ->
            item.copy(
                status = status,
                message = job.message.take(220),
                outputUri = job.outputUri.ifBlank { item.outputUri },
                firstPassOutputUri = job.firstPassOutputUri.ifBlank { item.firstPassOutputUri },
                mp3SourceUri = job.mp3SourceUri.ifBlank { item.mp3SourceUri },
                mp3OutputUri = job.mp3OutputUri.ifBlank { item.mp3OutputUri },
                separationPass = job.separationPass,
            )
        }
        if (status == AudioQueueStatus.COMPLETED || status == AudioQueueStatus.CANCELED) {
            releaseQueueSourcePermissionIfUnused(job.sourceUri)
        }
    }

    private fun releaseQueueSourcePermissionIfUnused(sourceUri: String) {
        if (sourceUri.isBlank()) return
        val stillNeeded = ProcessingQueueStore.current(this).any {
            it.sourceUri == sourceUri && it.status in setOf(
                AudioQueueStatus.WAITING,
                AudioQueueStatus.PROCESSING,
                AudioQueueStatus.PAUSED,
            )
        }
        if (!stillNeeded) {
            runCatching {
                contentResolver.releasePersistableUriPermission(
                    Uri.parse(sourceUri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
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
        ProcessingQueueStore.update(this, job.id) {
            it.copy(status = AudioQueueStatus.PROCESSING, message = "Resuming this song")
        }
        ensureForeground(SeparationJobStore.current(this) ?: job)
        setCpuWakeLock(true)
        if (worker?.isActive == true) workerStartId = latestStartId
        launchWorker()
    }

    private fun startExtraVocalReduction() {
        val current = SeparationJobStore.current(this) ?: return stopSelf()
        if (current.status != JobStatus.COMPLETED ||
            current.separationPass != SeparationPass.FIRST ||
            current.outputUri.isBlank()
        ) {
            if (worker?.isActive != true) stopSelf()
            return
        }
        worker?.takeIf { it.isActive }?.let { finishingWorker ->
            scope.launch {
                finishingWorker.join()
                startExtraVocalReduction()
            }
            return
        }

        val firstWav = current.outputUri
        val prepared = current.copy(
            separationPass = SeparationPass.EXTRA_VOCAL_REDUCTION,
            operation = JobOperation.SEPARATION,
            firstPassOutputUri = current.firstPassOutputUri.ifBlank { firstWav },
            extraPassInputUri = firstWav,
            pendingWavUri = "",
            status = JobStatus.QUEUED,
            phase = "Starting optional extra vocal reduction",
            progress = 0f,
            message = "The saved first WAV and selected original will stay unchanged. This second on-device pass may reduce more vocals, but can also alter instruments and takes longer.",
            pauseReason = null,
            sourceFilePath = "",
            pcmFilePath = "",
            sampleRate = 0,
            outputSampleRate = 0,
            channels = 0,
            totalFrames = 0L,
            completedChunks = 0,
            totalChunks = 0,
            privateOutputPath = "",
        )
        SeparationJobStore.save(this, prepared)
        ProcessingQueueStore.update(this, current.id) {
            it.copy(status = AudioQueueStatus.PROCESSING, message = "Running the optional second separation pass")
        }
        pauseRequested.value = false
        cancelRequested.value = false
        requestedPauseReason = null
        ensureForeground(prepared)
        setCpuWakeLock(true)
        launchWorker()
    }

    private fun startMp3Export() {
        val current = SeparationJobStore.current(this) ?: return stopSelf()
        if (current.status != JobStatus.COMPLETED || current.outputUri.isBlank()) {
            if (worker?.isActive != true) stopSelf()
            return
        }
        worker?.takeIf { it.isActive }?.let { finishingWorker ->
            scope.launch {
                finishingWorker.join()
                startMp3Export()
            }
            return
        }
        val sourceWav = current.outputUri
        if (current.mp3SourceUri == sourceWav && current.mp3OutputUri.isNotBlank()) {
            stopSelf()
            return
        }
        val canReusePending = current.mp3SourceUri == sourceWav
        val prepared = current.copy(
            operation = JobOperation.MP3_EXPORT,
            mp3SourceUri = sourceWav,
            mp3OutputUri = if (canReusePending) current.mp3OutputUri else "",
            pendingMp3Uri = if (canReusePending) current.pendingMp3Uri else "",
            status = JobStatus.QUEUED,
            phase = "Starting high-quality MP3 export",
            progress = 0f,
            message = "Encoding a separate 320 kb/s MP3 locally. MP3 is lossy; the saved WAV will remain unchanged.",
            pauseReason = null,
            completedChunks = 0,
            totalChunks = 0,
            privateOutputPath = "",
        )
        SeparationJobStore.save(this, prepared)
        ProcessingQueueStore.update(this, current.id) {
            it.copy(status = AudioQueueStatus.PROCESSING, message = "Creating a separate MP3 export")
        }
        pauseRequested.value = false
        cancelRequested.value = false
        requestedPauseReason = null
        ensureForeground(prepared)
        setCpuWakeLock(true)
        launchWorker()
    }

    private fun cancelJob() {
        val job = SeparationJobStore.current(this) ?: return stopSelf()
        if (job.status in TERMINAL_STATES) return
        cancelRequested.value = true
        pauseRequested.value = false
        requestedPauseReason = PauseReason.USER
        pauseMessage = "Stopping the processing queue at the next safe checkpoint."
        ProcessingQueueStore.cancelWaiting(this)
        if (worker?.isActive == true) workerStartId = latestStartId
        SeparationJobStore.update(this) { current ->
            current?.copy(status = JobStatus.PAUSING, message = pauseMessage)
        }
        if (worker?.isActive != true) {
            finishCanceled(job)
            stopForegroundNow()
            stopSelfResult(latestStartId)
        } else updateNotification(SeparationJobStore.current(this) ?: job)
    }

    private fun retryJob() {
        val job = SeparationJobStore.current(this) ?: return stopSelf()
        if (job.status != JobStatus.FAILED) return restoreOrStart()
        worker?.takeIf { it.isActive }?.let { finishingWorker ->
            retryRequestedJobId = job.id
            if (retryWaiter?.isActive != true) {
                retryWaiter = scope.launch {
                    finishingWorker.join()
                    retryWaiter = null
                    val latest = SeparationJobStore.current(this@SeparationService)
                    if (latest?.id == job.id && latest.status == JobStatus.FAILED) retryJob()
                    else restoreOrStart()
                }
            }
            return
        }
        retryRequestedJobId = null
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
        ProcessingQueueStore.update(this, job.id) {
            it.copy(status = AudioQueueStatus.PROCESSING, message = "Retrying this song on the device")
        }
        ensureForeground(SeparationJobStore.current(this) ?: job)
        launchWorker()
    }

    private fun discardJob() {
        if (worker?.isActive == true) return
        val job = SeparationJobStore.current(this)
        if (job != null) {
            removePrivateWork(job)
            removePendingOutput(job.pendingWavUri)
            removePendingOutput(job.pendingMp3Uri)
        }
        SeparationJobStore.save(this, null)
        stopForegroundNow()
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        stopSelf()
    }

    private fun launchWorker() {
        if (worker?.isActive == true) return
        workerStartId = latestStartId
        worker = scope.launch {
            var activeJob = SeparationJobStore.current(this@SeparationService) ?: return@launch
            setCpuWakeLock(!pauseRequested.value)
            try {
                while (isActive) {
                    activeJob = SeparationJobStore.current(this@SeparationService) ?: activeJob
                    try {
                        runJob(activeJob)
                    } catch (canceled: CancellationException) {
                        if (cancelRequested.value) {
                            finishCanceled(SeparationJobStore.current(this@SeparationService) ?: activeJob)
                            break
                        }
                        throw canceled
                    } catch (failure: Throwable) {
                        if (cancelRequested.value) {
                            finishCanceled(SeparationJobStore.current(this@SeparationService) ?: activeJob)
                            break
                        }
                        recordJobFailure(activeJob, failure)
                    }

                    val finished = SeparationJobStore.current(this@SeparationService) ?: activeJob
                    syncQueueItemWithJob(finished)
                    if (finished.status == JobStatus.FAILED && retryRequestedJobId == finished.id) {
                        retryRequestedJobId = null
                        activeJob = finished.copy(
                            status = JobStatus.RUNNING,
                            phase = "Retrying saved work",
                            message = "Retrying from saved audio chunks.",
                            pauseReason = null,
                        )
                        SeparationJobStore.save(this@SeparationService, activeJob)
                        ProcessingQueueStore.update(this@SeparationService, activeJob.id) {
                            it.copy(status = AudioQueueStatus.PROCESSING, message = "Retrying this song on the device")
                        }
                        setCpuWakeLock(true)
                        continue
                    }
                    if (finished.status !in setOf(JobStatus.COMPLETED, JobStatus.FAILED)) break

                    val next = ProcessingQueueStore.nextWaiting(this@SeparationService) ?: break
                    activeJob = activateQueuedItem(next)
                    pauseRequested.value = false
                    cancelRequested.value = false
                    requestedPauseReason = null
                    ensureForeground(activeJob)
                    setCpuWakeLock(true)
                }
            } finally {
                setCpuWakeLock(false)
                val finalJob = SeparationJobStore.current(this@SeparationService)
                if (finalJob == null || finalJob.status !in ACTIVE_STATES) {
                    if (finalJob != null) updateNotification(finalJob)
                    stopForegroundNow()
                    stopSelfResult(workerStartId)
                } else {
                    updateNotification(finalJob)
                }
            }
        }
    }

    private fun recordJobFailure(initial: SeparationJob, failure: Throwable) {
        val message = failure.message?.take(280)?.ifBlank { null }
            ?: "Processing stopped unexpectedly. The selected original is unchanged."
        SeparationJobStore.update(this) { current ->
            current?.copy(
                status = JobStatus.FAILED,
                message = message,
                phase = "Could not finish",
                pauseReason = PauseReason.ERROR,
            )
        }
        val failedJob = SeparationJobStore.current(this) ?: initial
        syncQueueItemWithJob(failedJob)
        runCatching { NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(failedJob)) }
    }

    private suspend fun runJob(initial: SeparationJob) {
        var job = SeparationJobStore.current(this) ?: initial
        val host = createProcessorHost()

        if (job.operation == JobOperation.MP3_EXPORT) {
            runMp3Export(job, host)
            return
        }

        val isExtraPass = job.separationPass == SeparationPass.EXTRA_VOCAL_REDUCTION
        val workDir = File(File(filesDir, "jobs"), job.id).apply { mkdirs() }
        setJobStatus(
            JobStatus.RUNNING,
            if (isExtraPass) "Preparing extra vocal reduction" else "Preparing on-device processing",
            if (isExtraPass) {
                "Running the optional second pass on the saved first WAV. The original and first-pass WAV remain unchanged."
            } else {
                "Starting safely; the original song will not be changed."
            },
        )

        val source = if (isExtraPass) null else copySourceToPrivateStorage(job, workDir, host)
        job = SeparationJobStore.current(this) ?: job

        val modelRepository = ModelRepository(this)
        val model = modelRepository.installedFile() ?: run {
            awaitModelStorageHeadroom(host)
            updateJobProgress(
                "Downloading separation model",
                0.02f,
                "Downloading the 63.7 MiB vocal-focused model over HTTPS; song audio is not uploaded.",
            )
            modelRepository.download(
                checkpoint = { awaitCheckpoint() },
                onProgress = { bytes, total ->
                    val percent = 0.02f + 0.10f * (bytes.toFloat() / total.coerceAtLeast(1L)).coerceIn(0f, 1f)
                    updateJobProgress(
                        "Downloading separation model",
                        percent,
                        "${DeviceMonitor.formatBytes(bytes)} of ${DeviceMonitor.formatBytes(total)} downloaded",
                    )
                },
            )
        }
        updateJobProgress(
            if (isExtraPass) "Preparing second-pass audio" else "Preparing MP3 decoder",
            0.12f,
            if (isExtraPass) "Reading the saved first-pass WAV locally; no audio is uploaded." else
                "The downloaded model stays on this device and is checksum-verified.",
        )
        if (!awaitCheckpoint()) throw CancellationException("Canceled")

        val pcmFile = File(workDir, "decoded-float.pcm")
        val recoveredInfo = recoverDecodedInfo(job, pcmFile)
        val decoded = recoveredInfo ?: run {
            val info = if (isExtraPass) {
                val inputWavUri = job.extraPassInputUri.ifBlank { job.firstPassOutputUri }
                check(inputWavUri.isNotBlank()) { "The first-pass WAV location is missing" }
                awaitWavDecodeStorageHeadroom(inputWavUri, host)
                val input = contentResolver.openInputStream(Uri.parse(inputWavUri))
                    ?: throw IllegalStateException("Android could not read the saved first-pass WAV")
                PcmWavDecoder().decodeToFloatFile(
                    source = input,
                    destination = pcmFile,
                    checkpoint = { awaitCheckpoint() },
                    onProgress = { value ->
                        updateJobProgress(
                            phase = "Reading first-pass WAV",
                            progress = 0.12f + 0.12f * value,
                            message = "Reading local PCM WAV · ${percent(value)}%",
                        )
                    },
                )
            } else {
                Mp3Decoder().decodeToFloatFile(
                    source = source ?: throw IllegalStateException("The selected MP3 could not be opened"),
                    destination = pcmFile,
                    checkpoint = { awaitCheckpoint() },
                    onProgress = { value ->
                        updateJobProgress(
                            phase = "Decoding MP3",
                            progress = 0.12f + 0.12f * value,
                            message = "Decoding locally · ${percent(value)}%",
                        )
                    },
                )
            }
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
            info
        }

        job = SeparationJobStore.current(this) ?: job
        val processor = ChunkedSeparationProcessor(monitor::read)
        val baseModelKey = ModelRepository.MODEL_SHA256.take(16)
        val modelCacheKey = if (isExtraPass) "$baseModelKey-extra" else baseModelKey
        val privateWav = processor.process(
            job = job,
            workDir = workDir,
            inputPcmFile = pcmFile,
            decoded = decoded,
            modelFile = model,
            host = host,
            modelCacheKey = modelCacheKey,
        )
        if (!awaitCheckpoint()) throw CancellationException("Canceled")

        val outputName = outputDisplayName(job.sourceName, isExtraPass)
        updateJobProgress(
            if (isExtraPass) "Saving extra-reduction WAV" else "Saving WAV",
            0.97f,
            if (isExtraPass) "Saving the optional second-pass WAV separately. The first-pass WAV stays intact." else
                "Saving the instrumental as a stereo PCM WAV in Music/Vocal Remover Studio.",
        )
        val outputUri = PublicWavExporter(this).export(
            source = privateWav,
            displayName = outputName,
            existingUri = (SeparationJobStore.current(this) ?: job).pendingWavUri,
            host = host,
            onUriReady = { uri ->
                SeparationJobStore.update(this) { current -> current?.copy(pendingWavUri = uri) }
            },
            onProgress = { value ->
                updateJobProgress(
                    phase = if (isExtraPass) "Saving extra-reduction WAV" else "Saving WAV",
                    progress = 0.97f + 0.025f * value,
                    message = "Copying WAV to Music · ${percent(value)}%",
                )
            },
        )
        SeparationJobStore.update(this) { current ->
            current?.copy(
                status = JobStatus.COMPLETED,
                phase = if (isExtraPass) "Extra vocal reduction ready" else "Instrumental ready",
                progress = 1f,
                message = if (isExtraPass) {
                    "Saved the optional extra-reduction WAV. The first-pass WAV and source song were left unchanged."
                } else {
                    "Saved to Music/Vocal Remover Studio. The source song was left unchanged."
                },
                operation = JobOperation.SEPARATION,
                outputUri = outputUri,
                pendingWavUri = "",
                firstPassOutputUri = if (isExtraPass) {
                    current.firstPassOutputUri.ifBlank { current.extraPassInputUri }
                } else {
                    outputUri
                },
                extraPassInputUri = if (isExtraPass) current.extraPassInputUri else "",
                sourceFilePath = "",
                pcmFilePath = "",
                privateOutputPath = "",
                pauseReason = null,
                completedChunks = current.totalChunks,
            )
        }
        removePrivateWork(job)
        updateNotification(SeparationJobStore.current(this) ?: job)
    }

    private suspend fun runMp3Export(initial: SeparationJob, host: ProcessorHost) {
        val job = SeparationJobStore.current(this) ?: initial
        val wavUri = job.mp3SourceUri.ifBlank { job.outputUri }
        check(wavUri.isNotBlank()) { "The completed instrumental WAV could not be found" }
        SeparationJobStore.update(this) { current ->
            current?.copy(
                status = JobStatus.RUNNING,
                phase = "Converting WAV to MP3",
                progress = 0f,
                completedChunks = 0,
                totalChunks = 0,
                message = "Encoding a separate 320 kb/s MP3 on this phone. MP3 is lossy; the WAV will remain unchanged.",
            )
        }
        updateNotification(SeparationJobStore.current(this))
        awaitMp3StorageHeadroom(wavUri, host)
        if (!awaitCheckpoint()) throw CancellationException("Canceled before MP3 export")

        val outputName = mp3DisplayName(job.sourceName, job.separationPass == SeparationPass.EXTRA_VOCAL_REDUCTION)
        updateJobProgress("Converting WAV to MP3", 0.03f, "LAME high-quality 320 kb/s CBR encoding · WAV stays untouched.")
        val outputUri = PublicMp3Exporter(this).export(
            sourceWavUri = wavUri,
            displayName = outputName,
            existingPendingUri = (SeparationJobStore.current(this) ?: job).pendingMp3Uri,
            host = host,
            onUriReady = { uri ->
                SeparationJobStore.update(this) { current -> current?.copy(pendingMp3Uri = uri) }
            },
            onProgress = { value ->
                updateJobProgress(
                    phase = "Converting WAV to MP3",
                    progress = 0.03f + 0.94f * value,
                    message = "Encoding high-quality MP3 · ${percent(value)}%",
                )
            },
        )
        SeparationJobStore.update(this) { current ->
            current?.copy(
                status = JobStatus.COMPLETED,
                operation = JobOperation.SEPARATION,
                phase = "MP3 export ready",
                progress = 1f,
                message = "Saved a separate 320 kb/s MP3 in Music/Vocal Remover Studio. MP3 is lossy; the WAV and source remain unchanged.",
                outputUri = current.outputUri,
                mp3OutputUri = outputUri,
                pendingMp3Uri = "",
                pauseReason = null,
            )
        }
        updateNotification(SeparationJobStore.current(this) ?: job)
    }

    private suspend fun awaitWavDecodeStorageHeadroom(sourceUri: String, host: ProcessorHost) {
        val wavBytes = contentLength(sourceUri)
            ?: throw IllegalStateException("The saved WAV size could not be measured safely; check device storage, then retry.")
        val floatBytes = ((wavBytes - 44L).coerceAtLeast(0L) / 4L) * Float.SIZE_BYTES * 2L
        val requiredBytes = floatBytes + ProcessingPolicy.STORAGE_SAFETY_RESERVE_BYTES
        while (true) {
            if (!host.checkpoint()) throw CancellationException("Canceled before reading the first-pass WAV")
            val available = monitor.read().availableStorageBytes
                ?: throw IllegalStateException("Free app storage could not be measured safely; check device storage, then retry.")
            if (available >= requiredBytes) return
            val shortfall = requiredBytes - available
            val message = "The second pass needs about ${DeviceMonitor.formatBytes(requiredBytes)} free to decode the saved WAV and keep a 96 MiB reserve; " +
                "only ${DeviceMonitor.formatBytes(available)} is available. Free about ${DeviceMonitor.formatBytes(shortfall)} more, then resume."
            if (!host.checkpoint(PauseReason.STORAGE, message)) {
                throw CancellationException("Canceled before reading the first-pass WAV")
            }
        }
    }

    private suspend fun awaitMp3StorageHeadroom(sourceUri: String, host: ProcessorHost) {
        val wavBytes = contentLength(sourceUri)
            ?: throw IllegalStateException("The saved WAV size could not be measured safely; check device storage, then retry.")
        // At 320 kb/s, MP3 is about 22.7% of 44.1 kHz stereo PCM16 WAV size; leave margin for headers.
        val estimatedMp3Bytes = (wavBytes / 4L).coerceAtLeast(1L) + 256L * 1024L
        val requiredBytes = estimatedMp3Bytes + ProcessingPolicy.STORAGE_SAFETY_RESERVE_BYTES
        while (true) {
            if (!host.checkpoint()) throw CancellationException("Canceled before MP3 export")
            val available = monitor.read().availableStorageBytes
                ?: throw IllegalStateException("Free storage could not be measured safely; check device storage, then retry.")
            if (available >= requiredBytes) return
            val shortfall = requiredBytes - available
            val message = "The 320 kb/s MP3 export needs about ${DeviceMonitor.formatBytes(requiredBytes)} free including a safety reserve; " +
                "only ${DeviceMonitor.formatBytes(available)} is available. Free about ${DeviceMonitor.formatBytes(shortfall)} more, then resume."
            if (!host.checkpoint(PauseReason.STORAGE, message)) throw CancellationException("Canceled before MP3 export")
        }
    }

    private fun contentLength(uriValue: String): Long? {
        val uri = Uri.parse(uriValue)
        val descriptorLength = runCatching {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length.takeIf { length -> length > 0L } }
        }.getOrNull()
        return descriptorLength ?: runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0).takeIf { it > 0L } else null
            }
        }.getOrNull()
    }

    private suspend fun awaitModelStorageHeadroom(host: ProcessorHost) {
        val required = ModelRepository.MODEL_BYTES + ProcessingPolicy.STORAGE_SAFETY_RESERVE_BYTES
        while (true) {
            if (!host.checkpoint()) throw CancellationException("Canceled before model download")
            val available = monitor.read().availableStorageBytes
                ?: throw IllegalStateException("Free app storage could not be measured safely; check device storage, then retry.")
            if (available >= required) return
            val shortfall = required - available
            val message = "Downloading the 63.7 MiB model while keeping a 96 MiB safety reserve needs " +
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
        PrivateSourceCopyRecovery.findExisting(job.sourceFilePath, workDir)?.let { return it }

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
                                if (remaining > Long.MAX_VALUE - ProcessingPolicy.STORAGE_SAFETY_RESERVE_BYTES) Long.MAX_VALUE
                                else remaining + ProcessingPolicy.STORAGE_SAFETY_RESERVE_BYTES
                            } ?: ProcessingPolicy.STORAGE_SAFETY_RESERVE_BYTES
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
        val resumed = SeparationJobStore.current(this)
        resumed?.let(::syncQueueItemWithJob)
        updateNotification(resumed)
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
        val pausedJob = SeparationJobStore.current(this)
        pausedJob?.let(::syncQueueItemWithJob)
        updateNotification(pausedJob)
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
            val job = SeparationJobStore.current(this)
            if (job == null || job.status !in ACTIVE_STATES) {
                lowMemoryAutoResumeGate.reset()
                continue
            }
            val snapshot = runCatching { monitor.read() }.getOrNull()
            if (snapshot == null) {
                lowMemoryAutoResumeGate.reset()
                continue
            }
            ServiceTelemetry.publish(snapshot)

            if (job.status == JobStatus.PAUSED &&
                job.pauseReason == PauseReason.LOW_MEMORY &&
                pauseRequested.value && !cancelRequested.value
            ) {
                val resumeSafety = ProcessingPolicy.decide(
                    profile = job.profile,
                    availableCores = Runtime.getRuntime().availableProcessors(),
                    snapshot = snapshot.toSafetySnapshot(),
                )
                val safeExceptForMemory = !resumeSafety.pause || resumeSafety.reason == PauseReason.LOW_MEMORY
                val ready = lowMemoryAutoResumeGate.shouldResume(
                    nowElapsedMs = SystemClock.elapsedRealtime(),
                    reason = if (safeExceptForMemory) job.pauseReason else null,
                    availableRamBytes = snapshot.availableRamBytes,
                )
                if (ready) {
                    val current = SeparationJobStore.current(this)
                    val stillWaitingForRam = current != null &&
                        current.id == job.id &&
                        current.status == JobStatus.PAUSED &&
                        current.pauseReason == PauseReason.LOW_MEMORY &&
                        pauseRequested.value && !cancelRequested.value
                    lowMemoryAutoResumeGate.reset()
                    if (stillWaitingForRam) resumeJob()
                }
                continue
            }
            lowMemoryAutoResumeGate.reset()
            if (job.status != JobStatus.RUNNING || pauseRequested.value) continue

            val decision = ProcessingPolicy.decide(
                profile = job.profile,
                availableCores = Runtime.getRuntime().availableProcessors(),
                snapshot = snapshot.toSafetySnapshot(),
            )
            if (decision.pause) {
                val text = when (decision.reason) {
                    PauseReason.THERMAL -> "Android reports severe thermal status. Let the device cool, then resume."
                    PauseReason.LOW_MEMORY -> lowMemoryPauseMessage()
                    PauseReason.STORAGE -> "Free storage is low. Make space, then resume."
                    else -> "Processing paused for device safety."
                }
                requestSafetyPause(decision.reason ?: PauseReason.ERROR, text)
            }
        }
    }

    private fun lowMemoryPauseMessage(): String =
        "Low RAM; saved chunks are safe. Will auto-resume after Android reports at least " +
            "${DeviceMonitor.formatBytes(ProcessingPolicy.LOW_MEMORY_AUTO_RESUME_BYTES)} free for " +
            "${ProcessingPolicy.LOW_MEMORY_AUTO_RESUME_STABLE_MS / 1_000L} seconds, once other safety checks are clear."

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
        removePendingOutput(job.pendingWavUri)
        removePendingOutput(job.pendingMp3Uri)

        val fallbackWav = if (job.separationPass == SeparationPass.EXTRA_VOCAL_REDUCTION) {
            job.firstPassOutputUri.ifBlank { job.extraPassInputUri }
        } else {
            job.outputUri
        }
        SeparationJobStore.update(this) { current ->
            when {
                current == null -> null
                current.operation == JobOperation.MP3_EXPORT -> current.copy(
                    status = JobStatus.COMPLETED,
                    operation = JobOperation.SEPARATION,
                    phase = "Instrumental ready",
                    progress = 1f,
                    message = "MP3 export canceled. The saved WAV and selected original were left unchanged.",
                    pendingMp3Uri = "",
                    pauseReason = null,
                    sourceFilePath = "",
                    pcmFilePath = "",
                    privateOutputPath = "",
                )
                current.separationPass == SeparationPass.EXTRA_VOCAL_REDUCTION -> current.copy(
                    status = JobStatus.COMPLETED,
                    operation = JobOperation.SEPARATION,
                    separationPass = SeparationPass.FIRST,
                    phase = "Instrumental ready",
                    progress = 1f,
                    message = "The optional second pass was canceled. The first-pass WAV and selected original remain unchanged.",
                    outputUri = fallbackWav,
                    firstPassOutputUri = fallbackWav,
                    extraPassInputUri = "",
                    pendingWavUri = "",
                    pauseReason = null,
                    sourceFilePath = "",
                    pcmFilePath = "",
                    sampleRate = 0,
                    outputSampleRate = 0,
                    channels = 0,
                    totalFrames = 0L,
                    completedChunks = 0,
                    totalChunks = 0,
                    privateOutputPath = "",
                )
                else -> current.copy(
                    status = JobStatus.CANCELED,
                    phase = "Canceled",
                    message = "Canceled safely. Your selected original song was not changed.",
                    sourceFilePath = "",
                    pcmFilePath = "",
                    privateOutputPath = "",
                    outputUri = "",
                    pendingWavUri = "",
                    pendingMp3Uri = "",
                    pauseReason = null,
                )
            }
        }
        pauseRequested.value = false
        cancelRequested.value = false
        val finalJob = SeparationJobStore.current(this) ?: job
        syncQueueItemWithJob(finalJob)
        updateNotification(finalJob)
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
            // AndroidX Core 1.15 masks foreground types it doesn't know to type NONE.
            // MEDIA_PROCESSING is an API 35 type, so call the platform API directly here;
            // target SDK 36 correctly rejects a type-less foreground service.
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING,
            )
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
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
                AppStrings.translate("Resume"),
                servicePendingIntent(ACTION_RESUME, REQUEST_RESUME),
            )
        } else if (job.status == JobStatus.FAILED) {
            builder.addAction(
                android.R.drawable.ic_menu_rotate,
                AppStrings.translate("Retry"),
                servicePendingIntent(ACTION_RETRY, REQUEST_RETRY),
            )
        } else if (job.status == JobStatus.RUNNING || job.status == JobStatus.QUEUED) {
            builder.addAction(
                android.R.drawable.ic_media_pause,
                AppStrings.translate("Pause"),
                servicePendingIntent(ACTION_PAUSE, REQUEST_PAUSE),
            )
        }
        if (job.status !in TERMINAL_STATES) {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                AppStrings.translate("Cancel"),
                servicePendingIntent(ACTION_CANCEL, REQUEST_CANCEL),
            )
        }
        return builder.build()
    }

    private fun notificationText(job: SeparationJob): String {
        val base = when (job.status) {
            JobStatus.PAUSED -> job.message.ifBlank { "Paused at a safe checkpoint" }
            JobStatus.PAUSING -> job.message.ifBlank { "Finishing the current short audio chunk before pausing" }
            JobStatus.FAILED -> "${job.phase}: ${job.message}"
            JobStatus.COMPLETED -> when {
                job.mp3OutputUri.isNotBlank() && job.mp3SourceUri == job.outputUri -> "WAV and 320 kb/s MP3 saved; original unchanged"
                job.separationPass == SeparationPass.EXTRA_VOCAL_REDUCTION -> "Extra-reduction WAV saved; first WAV retained"
                else -> "Instrumental WAV saved; original unchanged"
            }
            JobStatus.CANCELED -> "Canceled; original unchanged"
            else -> "${job.phase} · ${(job.progress * 100).roundToInt()}%"
        }
        val waiting = ProcessingQueueStore.current(this).count { it.status == AudioQueueStatus.WAITING }
        val status = if (waiting > 0) "$base · $waiting waiting".take(180) else base.take(180)
        return AppStrings.translate(status)
    }

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
            val channel = NotificationChannel(CHANNEL_ID, AppStrings.translate("Vocal separation"), NotificationManager.IMPORTANCE_LOW).apply {
                description = AppStrings.translate("Visible progress and pause/resume controls for local song processing")
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

    private fun outputDisplayName(sourceName: String, extraPass: Boolean): String {
        val base = safeOutputBaseName(sourceName)
        return if (extraPass) "$base - Extra vocal reduction.wav" else "$base - Instrumental.wav"
    }

    private fun mp3DisplayName(sourceName: String, extraPass: Boolean): String {
        val base = safeOutputBaseName(sourceName)
        val stem = if (extraPass) "Extra vocal reduction" else "Instrumental"
        return "$base - $stem 320kbps.mp3"
    }

    private fun safeOutputBaseName(sourceName: String): String =
        sourceName.substringBeforeLast('.', sourceName).take(72)
            .replace(Regex("[^A-Za-z0-9 _().-]"), "_")
            .trim().ifBlank { "Song" }

    private fun percent(value: Float): Int = (value.coerceIn(0f, 1f) * 100).roundToInt()

    private fun safetyPauseMessage(reason: PauseReason): String = when (reason) {
        PauseReason.THERMAL -> "Android reports severe thermal status. Let the device cool, then resume."
        PauseReason.LOW_MEMORY -> lowMemoryPauseMessage()
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
        const val ACTION_EXTRA_VOCAL_REDUCTION = "com.universalrp.vocalremover.action.EXTRA_VOCAL_REDUCTION"
        const val ACTION_EXPORT_MP3 = "com.universalrp.vocalremover.action.EXPORT_MP3"
        const val ACTION_QUEUE = "com.universalrp.vocalremover.action.RUN_QUEUE"

        private const val CHANNEL_ID = "vocal_remover_processing"
        const val NOTIFICATION_ID = 4103
        private const val REQUEST_OPEN = 4104
        private const val REQUEST_PAUSE = 4105
        private const val REQUEST_RESUME = 4106
        private const val REQUEST_CANCEL = 4107
        private const val REQUEST_RETRY = 4108
        private val ACTIVE_STATES = setOf(JobStatus.QUEUED, JobStatus.RUNNING, JobStatus.PAUSING, JobStatus.PAUSED)
        private val TERMINAL_STATES = setOf(JobStatus.COMPLETED, JobStatus.CANCELED, JobStatus.FAILED)
    }
}
