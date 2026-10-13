package com.universalrp.vocalremover

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.universalrp.vocalremover.audio.WavPlaybackController
import com.universalrp.vocalremover.audio.WavPlaybackState
import com.universalrp.vocalremover.audio.WavPlaybackStatus
import com.universalrp.vocalremover.health.DeviceMonitor
import com.universalrp.vocalremover.health.DeviceSnapshot
import com.universalrp.vocalremover.model.ModelRepository
import com.universalrp.vocalremover.processing.JobStatus
import com.universalrp.vocalremover.processing.AudioQueueItem
import com.universalrp.vocalremover.processing.AudioQueueStatus
import com.universalrp.vocalremover.processing.PauseReason
import com.universalrp.vocalremover.processing.ProcessingProfile
import com.universalrp.vocalremover.processing.ProcessingQueueStore
import com.universalrp.vocalremover.processing.SeparationJob
import com.universalrp.vocalremover.processing.SeparationJobStore
import com.universalrp.vocalremover.processing.SeparationPass
import com.universalrp.vocalremover.processing.SeparationService
import com.universalrp.vocalremover.processing.ServiceTelemetry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

private val Ink = Color(0xFF152E2D)
private val Teal = Color(0xFF1F7770)
private val TealDark = Color(0xFF155851)
private val Mint = Color(0xFFE0F1EB)
private val Paper = Color(0xFFF5F6F1)
private val SoftWhite = Color(0xFFFEFFFC)
private val Muted = Color(0xFF637674)
private val Amber = Color(0xFFE7A955)
private val Coral = Color(0xFFB65046)

private data class ModelDownloadUiState(
    val downloading: Boolean = false,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = ModelRepository.MODEL_BYTES,
    val errorMessage: String? = null,
)

class MainActivity : ComponentActivity() {
    private lateinit var wavPlaybackController: WavPlaybackController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wavPlaybackController = WavPlaybackController(applicationContext)
        SeparationJobStore.initialize(applicationContext)
        ProcessingQueueStore.initialize(applicationContext)
        val recoveredJob = SeparationJobStore.current(applicationContext)
        val hasQueuedFiles = ProcessingQueueStore.nextWaiting(applicationContext) != null
        val shouldRestoreService = recoveredJob?.status in setOf(JobStatus.RUNNING, JobStatus.QUEUED, JobStatus.PAUSING) ||
            (hasQueuedFiles && recoveredJob?.status != JobStatus.PAUSED)
        if (shouldRestoreService) {
            androidx.core.content.ContextCompat.startForegroundService(
                this,
                Intent(this, SeparationService::class.java).setAction(
                    if (recoveredJob?.status in setOf(JobStatus.RUNNING, JobStatus.QUEUED, JobStatus.PAUSING)) {
                        SeparationService.ACTION_START
                    } else {
                        SeparationService.ACTION_QUEUE
                    },
                ),
            )
        }
        window.statusBarColor = android.graphics.Color.rgb(245, 246, 241)
        window.navigationBarColor = android.graphics.Color.rgb(245, 246, 241)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Teal,
                    onPrimary = Color.White,
                    secondary = Amber,
                    background = Paper,
                    surface = SoftWhite,
                    onSurface = Ink,
                    onBackground = Ink,
                    error = Coral,
                ),
            ) {
                VocalRemoverScreen(wavPlaybackController)
            }
        }
    }

    override fun onStop() {
        if (::wavPlaybackController.isInitialized) wavPlaybackController.stop()
        super.onStop()
    }

    override fun onDestroy() {
        if (::wavPlaybackController.isInitialized) wavPlaybackController.release()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VocalRemoverScreen(wavPlaybackController: WavPlaybackController) {
    val context = LocalContext.current
    val activity = context as? Activity
    val job by SeparationJobStore.job.collectAsState()
    val playbackState by wavPlaybackController.state.collectAsState()
    val serviceSnapshot by ServiceTelemetry.device.collectAsState()
    val monitor = remember { DeviceMonitor(context.applicationContext) }
    var localSnapshot by remember { mutableStateOf<DeviceSnapshot?>(null) }
    var keepScreenAwake by remember { mutableStateOf(AppPreferences.keepScreenAwake(context)) }
    var selectedProfile by remember { mutableStateOf(job?.profile ?: ProcessingProfile.BALANCED) }
    var modelReady by remember { mutableStateOf(false) }
    val modelDownloadStateFlow = remember { MutableStateFlow(ModelDownloadUiState()) }
    val modelDownloadState by modelDownloadStateFlow.collectAsState()
    val downloadScope = rememberCoroutineScope()
    var showCredits by remember { mutableStateOf(false) }
    var showExtraPassConfirmation by remember { mutableStateOf(false) }
    var showMp3Confirmation by remember { mutableStateOf(false) }
    var isChoosingAudio by remember { mutableStateOf(false) }
    var chooseMultipleAfterPermission by remember { mutableStateOf(false) }
    val queueItems by ProcessingQueueStore.items.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        isChoosingAudio = false
        if (uri != null) enqueueAudioUris(context, listOf(uri), selectedProfile)
    }
    val multiplePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        isChoosingAudio = false
        if (uris.isNotEmpty()) enqueueAudioUris(context, uris, selectedProfile)
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        isChoosingAudio = false
        if (granted) {
            if (chooseMultipleAfterPermission) multiplePicker.launch(arrayOf("audio/mpeg"))
            else picker.launch(arrayOf("audio/mpeg"))
        } else {
            Toast.makeText(
                context,
                "Allow notifications so the long-running foreground queue stays visible and controllable.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    val launchAudioPicker: (Boolean) -> Unit = { multiple ->
        chooseMultipleAfterPermission = multiple
        val needsNotificationPermission = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermission) {
            isChoosingAudio = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            isChoosingAudio = true
            if (multiple) multiplePicker.launch(arrayOf("audio/mpeg")) else picker.launch(arrayOf("audio/mpeg"))
        }
    }

    val downloadModel: () -> Unit = {
        if (!modelReady && !modelDownloadState.downloading) {
            modelDownloadStateFlow.value = ModelDownloadUiState(downloading = true)
            downloadScope.launch {
                try {
                    val repository = ModelRepository(context.applicationContext)
                    withContext(Dispatchers.IO) {
                        repository.download(
                            checkpoint = { currentCoroutineContext().isActive },
                            onProgress = { bytes, total ->
                                modelDownloadStateFlow.value = ModelDownloadUiState(
                                    downloading = true,
                                    downloadedBytes = bytes,
                                    totalBytes = total,
                                )
                            },
                        )
                    }
                    modelReady = true
                    modelDownloadStateFlow.value = ModelDownloadUiState()
                    Toast.makeText(context, "Model downloaded and checksum verified. Choose an MP3 to begin.", Toast.LENGTH_LONG).show()
                } catch (cancelled: CancellationException) {
                    modelDownloadStateFlow.value = ModelDownloadUiState(
                        errorMessage = "Download was interrupted. Tap Retry to try again.",
                    )
                    throw cancelled
                } catch (failure: Exception) {
                    val detail = failure.message?.take(180)?.ifBlank { null }
                        ?: "Check your internet connection and try again."
                    modelDownloadStateFlow.value = ModelDownloadUiState(errorMessage = detail)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            localSnapshot = withContext(Dispatchers.IO) { monitor.read() }
            delay(2_000)
        }
    }
    LaunchedEffect(job?.profile, job?.status) {
        if (job != null && job?.status !in setOf(JobStatus.RUNNING, JobStatus.PAUSING, JobStatus.PAUSED)) {
            selectedProfile = job!!.profile
        }
    }
    LaunchedEffect(job?.phase, job?.status) {
        modelReady = withContext(Dispatchers.IO) { ModelRepository(context.applicationContext).installedFile() != null }
    }
    DisposableEffect(keepScreenAwake, job?.status, queueItems, modelDownloadState.downloading, activity) {
        val active = job?.status in setOf(JobStatus.QUEUED, JobStatus.RUNNING, JobStatus.PAUSING) ||
            queueItems.any { it.status == AudioQueueStatus.PROCESSING } || modelDownloadState.downloading
        if (keepScreenAwake && active) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val active = job?.status in setOf(JobStatus.QUEUED, JobStatus.RUNNING, JobStatus.PAUSING, JobStatus.PAUSED) ||
        queueItems.any { it.status in setOf(AudioQueueStatus.PROCESSING, AudioQueueStatus.PAUSED) }
    val currentSnapshot = if (active && serviceSnapshot != null) serviceSnapshot else localSnapshot

    Scaffold(containerColor = Paper) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Paper)
                .padding(scaffoldPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(modelReady = modelReady)
            HeroCard()

            if (job != null && job!!.status != JobStatus.CANCELED) {
                JobCard(
                    job = job!!,
                    onPause = { startServiceAction(context, SeparationService.ACTION_PAUSE) },
                    onResume = { startServiceAction(context, SeparationService.ACTION_RESUME) },
                    onCancel = { startServiceAction(context, SeparationService.ACTION_CANCEL) },
                    onRetry = { startServiceAction(context, SeparationService.ACTION_RETRY) },
                    playbackState = playbackState,
                    onPlay = { wavPlaybackController.play(job!!.outputUri) },
                    onPlayFirstPass = { wavPlaybackController.play(job!!.firstPassOutputUri) },
                    onPlayMp3 = { wavPlaybackController.play(job!!.mp3OutputUri) },
                    onExtraVocalReduction = { showExtraPassConfirmation = true },
                    onExportMp3 = { showMp3Confirmation = true },
                    onPausePlayback = { wavPlaybackController.pause() },
                    onResumePlayback = { wavPlaybackController.resume() },
                    onStopPlayback = { wavPlaybackController.stop() },
                    onNewSong = {
                        wavPlaybackController.stop()
                        launchAudioPicker(true)
                    },
                    onDiscard = {
                        job?.let { discardTerminalJob(context, it) }
                        SeparationJobStore.initialize(context)
                    },
                )
            }

            if (queueItems.isNotEmpty()) {
                ProcessingQueueCard(
                    items = queueItems,
                    currentJob = job,
                    onRemoveWaiting = { item ->
                        if (ProcessingQueueStore.removeWaiting(context, item.id)) releaseQueueUriIfUnused(context, item.sourceUri)
                    },
                    onRetry = { item ->
                        if (job?.id == item.id && job?.status == JobStatus.FAILED) {
                            startServiceAction(context, SeparationService.ACTION_RETRY)
                        } else if (ProcessingQueueStore.retry(context, item.id)) {
                            startServiceAction(context, SeparationService.ACTION_QUEUE)
                        }
                    },
                    onPlay = { item -> wavPlaybackController.play(item.outputUri) },
                    onSelect = { item ->
                        if (active || queueItems.any { it.status == AudioQueueStatus.WAITING }) {
                            Toast.makeText(context, "Wait until the queue is idle before opening an earlier result.", Toast.LENGTH_LONG).show()
                        } else {
                            selectCompletedQueueItem(context, item)
                        }
                    },
                    onClearFinished = {
                        ProcessingQueueStore.clearFinished(context).forEach { item ->
                            if (job?.id != item.id || job?.status != JobStatus.FAILED) {
                                releaseQueueUriIfUnused(context, item.sourceUri)
                            }
                        }
                    },
                )
            }

            OutlinedButton(
                onClick = { launchAudioPicker(true) },
                enabled = !isChoosingAudio && !modelDownloadState.downloading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(15.dp),
            ) {
                androidx.compose.material3.Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isChoosingAudio) "Choosing MP3 files…" else "Add multiple MP3s to queue", fontWeight = FontWeight.Bold)
            }

            if (queueItems.any { it.status == AudioQueueStatus.WAITING } &&
                job?.status !in setOf(JobStatus.RUNNING, JobStatus.QUEUED, JobStatus.PAUSING, JobStatus.PAUSED)
            ) {
                Button(
                    onClick = { startServiceAction(context, SeparationService.ACTION_QUEUE) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TealDark),
                ) {
                    androidx.compose.material3.Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Resume queued songs", fontWeight = FontWeight.Bold)
                }
            }

            ProfileCard(
                selected = job?.profile?.takeIf { active } ?: selectedProfile,
                enabled = !active,
                onSelect = { selectedProfile = it },
            )

            ScreenAwakeCard(
                enabled = keepScreenAwake,
                canToggle = !active || job?.status == JobStatus.RUNNING,
                onToggle = {
                    keepScreenAwake = it
                    AppPreferences.setKeepScreenAwake(context, it)
                },
            )

            if (job == null || job?.status == JobStatus.CANCELED) {
                ModelCard(
                    modelReady = modelReady,
                    downloadState = modelDownloadState,
                    onDownload = downloadModel,
                    onCredits = { showCredits = true },
                )
                Button(
                    onClick = { launchAudioPicker(false) },
                    enabled = !isChoosingAudio && !modelDownloadState.downloading,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TealDark),
                ) {
                    if (isChoosingAudio || modelDownloadState.downloading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        androidx.compose.material3.Icon(Icons.Default.FolderOpen, contentDescription = null)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        when {
                            isChoosingAudio -> "Choose an MP3…"
                            modelDownloadState.downloading -> "Model downloading…"
                            else -> "Choose an MP3"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }
                Text(
                    "Download the model here first, or it downloads automatically when you start a song. Your selected audio stays on this phone and is never uploaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            }

            DeviceHealthCard(currentSnapshot)
            PrivacyCard(onCredits = { showCredits = true })
            Text(
                "Vocal Remover Studio · standalone prototype · batch queue, WAV, extra pass & MP3 export",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                textAlign = TextAlign.Center,
            )
        }
    }

    if (showCredits) CreditsDialog(onDismiss = { showCredits = false })
    if (showExtraPassConfirmation) {
        AlertDialog(
            onDismissRequest = { showExtraPassConfirmation = false },
            containerColor = SoftWhite,
            title = { Text("Try extra vocal reduction?", fontWeight = FontWeight.Bold, color = Ink) },
            text = {
                Text(
                    "This runs the saved instrumental through the separator once more, entirely on this phone. It may reduce leftover singing, but can also soften or change instruments and may take about as long as the first pass, using more processing time, storage, and heat. A separate WAV will be saved; your source and first WAV stay unchanged. Listen to the first WAV before choosing.",
                    color = Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            dismissButton = { TextButton(onClick = { showExtraPassConfirmation = false }) { Text("Not now") } },
            confirmButton = {
                TextButton(onClick = {
                    showExtraPassConfirmation = false
                    wavPlaybackController.stop()
                    SeparationJobStore.update(context) { current -> current?.copy(profile = selectedProfile) }
                    startServiceAction(context, SeparationService.ACTION_EXTRA_VOCAL_REDUCTION)
                }) { Text("Start second pass") }
            },
        )
    }
    if (showMp3Confirmation) {
        AlertDialog(
            onDismissRequest = { showMp3Confirmation = false },
            containerColor = SoftWhite,
            title = { Text("Create a high-quality MP3?", fontWeight = FontWeight.Bold, color = Ink) },
            text = {
                Text(
                    "MP3 is a lossy format, so no WAV-to-MP3 conversion can be mathematically lossless. This uses a high-quality 320 kb/s CBR LAME encode to minimize the extra loss. It creates a separate MP3 in Music/Vocal Remover Studio; the WAV remains unchanged.",
                    color = Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            dismissButton = { TextButton(onClick = { showMp3Confirmation = false }) { Text("Keep WAV only") } },
            confirmButton = {
                TextButton(onClick = {
                    showMp3Confirmation = false
                    wavPlaybackController.stop()
                    startServiceAction(context, SeparationService.ACTION_EXPORT_MP3)
                }) { Text("Create 320 kb/s MP3") }
            },
        )
    }
}

@Composable
private fun Header(modelReady: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Mint),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(Icons.Default.Audiotrack, contentDescription = null, tint = TealDark, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("VOCAL REMOVER", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Teal, letterSpacing = 1.5.sp)
            Text("Studio", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink, lineHeight = 26.sp)
        }
        Surface(color = if (modelReady) Mint else Color(0xFFFFF0D8), shape = CircleShape) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp)) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (modelReady) Teal else Amber))
                Spacer(Modifier.width(7.dp))
                Text(if (modelReady) "MODEL READY" else "MODEL NEEDED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Ink, letterSpacing = .5.sp)
            }
        }
    }
}

@Composable
private fun HeroCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Ink),
        shape = RoundedCornerShape(26.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF183B39), Color(0xFF235C55))))
                .padding(22.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(color = Color.White.copy(alpha = .12f), shape = CircleShape) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        androidx.compose.material3.Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFFBFE7D7), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("PRIVATE BY DESIGN", color = Color(0xFFBFE7D7), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
                Text("Keep the music.\nLose the lead vocals.", fontSize = 27.sp, lineHeight = 32.sp, color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    "AI separation runs offline on your phone. Your source stays untouched; the first export is an instrumental WAV.",
                    color = Color(0xFFD3E4DD),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(Icons.Default.Download, contentDescription = null, tint = Color(0xFFBFE7D7), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("One-time model download · no song uploads", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(selected: ProcessingProfile, enabled: Boolean, onSelect: (ProcessingProfile) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = SoftWhite), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(Icons.Default.Timer, null, tint = Teal, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Processing speed", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.weight(1f))
                Text(if (enabled) "CHOOSE ONE" else "JOB PROFILE", fontSize = 9.sp, color = Muted, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ProcessingProfile.entries.forEach { profile ->
                    val chosen = selected == profile
                    Surface(
                        color = if (chosen) Teal else Paper,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).clickable(enabled = enabled) { onSelect(profile) },
                    ) {
                        Column(Modifier.padding(vertical = 12.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(profile.title, color = if (chosen) Color.White else Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                when (profile) {
                                    ProcessingProfile.SLOW -> "coolest"
                                    ProcessingProfile.BALANCED -> "recommended"
                                    ProcessingProfile.FAST -> "more cores"
                                },
                                color = if (chosen) Color.White.copy(alpha = .82f) else Muted,
                                fontSize = 9.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            Text(
                when (selected) {
                    ProcessingProfile.SLOW -> "One inference thread with longer cool-downs. Best when battery and heat matter most."
                    ProcessingProfile.BALANCED -> "A moderate thread count with device-safety back-off. Recommended for most phones."
                    ProcessingProfile.FAST -> "Uses up to four inference threads when Android reports normal thermal status."
                },
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun ScreenAwakeCard(enabled: Boolean, canToggle: Boolean, onToggle: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = SoftWhite), shape = RoundedCornerShape(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Mint), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(Icons.Default.Memory, null, tint = Teal, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Keep screen awake", color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "While this app is open during a job or model download · uses more battery",
                    color = Muted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle, enabled = canToggle)
        }
    }
}

@Composable
private fun ModelCard(
    modelReady: Boolean,
    downloadState: ModelDownloadUiState,
    onDownload: () -> Unit,
    onCredits: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F1ED)), shape = RoundedCornerShape(20.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(44.dp)
                        .clip(CircleShape)
                        .background(SoftWhite)
                        .clickable(enabled = !modelReady && !downloadState.downloading, onClick = onDownload),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        if (modelReady) Icons.Default.CheckCircle else Icons.Default.Download,
                        contentDescription = if (modelReady) "Model ready" else "Download model",
                        tint = Teal,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (modelReady) "On-device model is ready" else "Download vocal-focused model", color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        if (modelReady) "UVR MDX-Net Voc FT · stored privately on this phone · 63.7 MiB"
                        else "UVR MDX-Net Voc FT · 63.7 MiB over HTTPS · checksum verified",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 17.sp,
                    )
                    TextButtonCompact("Model credits & terms", onCredits)
                }
            }
            Text(
                "Vocal separation can leave remnants or introduce musical artifacts; results depend on the mix.",
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 16.sp,
            )
            if (!modelReady) {
                if (downloadState.downloading) {
                    if (downloadState.downloadedBytes > 0L) {
                        LinearProgressIndicator(
                            progress = {
                                (downloadState.downloadedBytes.toFloat() / downloadState.totalBytes.coerceAtLeast(1L))
                                    .coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = Teal,
                            trackColor = Paper,
                        )
                        Text(
                            if (downloadState.downloadedBytes >= downloadState.totalBytes) {
                                "Download complete · verifying checksum…"
                            } else {
                                "${DeviceMonitor.formatBytes(downloadState.downloadedBytes)} of ${DeviceMonitor.formatBytes(downloadState.totalBytes)} downloaded"
                            },
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = Teal,
                            trackColor = Paper,
                        )
                        Text("Connecting securely to the model host…", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TealDark),
                    ) {
                        androidx.compose.material3.Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (downloadState.errorMessage != null) "Retry model download" else "Download model · 63.7 MiB",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                downloadState.errorMessage?.let { error ->
                    Text(
                        "Download failed: $error. Check your internet connection, then retry.",
                        color = Coral,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 17.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProcessingQueueCard(
    items: List<AudioQueueItem>,
    currentJob: SeparationJob?,
    onRemoveWaiting: (AudioQueueItem) -> Unit,
    onRetry: (AudioQueueItem) -> Unit,
    onPlay: (AudioQueueItem) -> Unit,
    onSelect: (AudioQueueItem) -> Unit,
    onClearFinished: () -> Unit,
) {
    val waiting = items.count { it.status == AudioQueueStatus.WAITING }
    val processing = items.count { it.status == AudioQueueStatus.PROCESSING || it.status == AudioQueueStatus.PAUSED }
    val completed = items.count { it.status == AudioQueueStatus.COMPLETED }
    val failed = items.count { it.status == AudioQueueStatus.FAILED }
    val hasFinishedItems = items.any {
        it.status in setOf(AudioQueueStatus.COMPLETED, AudioQueueStatus.FAILED, AudioQueueStatus.CANCELED)
    }
    val liveItems = items.filter {
        it.status in setOf(AudioQueueStatus.WAITING, AudioQueueStatus.PROCESSING, AudioQueueStatus.PAUSED, AudioQueueStatus.FAILED)
    }
    val recentHistory = items.filter {
        it.status == AudioQueueStatus.COMPLETED || it.status == AudioQueueStatus.CANCELED
    }.takeLast(4)
    val visibleItems = (liveItems + recentHistory).distinctBy { it.id }.sortedBy { it.addedAtMs }

    Card(colors = CardDefaults.cardColors(containerColor = SoftWhite), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(Icons.Default.Audiotrack, null, tint = Teal, modifier = Modifier.size(21.dp))
                Spacer(Modifier.width(8.dp))
                Text("Background queue", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.weight(1f))
                if (hasFinishedItems) {
                    TextButton(onClick = onClearFinished) { Text("Clear finished", color = Muted) }
                }
            }
            Text(
                "$processing processing or paused · $waiting waiting · $completed done · $failed failed",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
            visibleItems.forEach { item ->
                val tint = when (item.status) {
                    AudioQueueStatus.COMPLETED -> Teal
                    AudioQueueStatus.FAILED -> Coral
                    AudioQueueStatus.PAUSED -> Amber
                    else -> Muted
                }
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Paper).padding(11.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(
                            when (item.status) {
                                AudioQueueStatus.COMPLETED -> Icons.Default.CheckCircle
                                AudioQueueStatus.FAILED -> Icons.Default.Info
                                AudioQueueStatus.PROCESSING, AudioQueueStatus.PAUSED -> Icons.Default.Audiotrack
                                else -> Icons.Default.Timer
                            },
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.sourceName, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                when (item.status) {
                                    AudioQueueStatus.WAITING -> "Waiting · ${item.profile.title} profile"
                                    AudioQueueStatus.PROCESSING -> if (currentJob?.id == item.id) currentJob.phase else "Processing on device"
                                    AudioQueueStatus.PAUSED -> "Paused · resume from the current job card"
                                    AudioQueueStatus.COMPLETED -> "Instrumental WAV saved"
                                    AudioQueueStatus.FAILED -> item.message.ifBlank { "Failed · retry available" }
                                    AudioQueueStatus.CANCELED -> "Stopped"
                                },
                                color = tint,
                                fontSize = 10.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        when {
                            item.status == AudioQueueStatus.WAITING -> TextButton(onClick = { onRemoveWaiting(item) }) { Text("Remove") }
                            item.status == AudioQueueStatus.FAILED -> TextButton(onClick = { onRetry(item) }) { Text("Retry") }
                            item.status == AudioQueueStatus.COMPLETED && item.outputUri.isNotBlank() -> Row {
                                TextButton(onClick = { onPlay(item) }) { Text("Play") }
                                TextButton(onClick = { onSelect(item) }) { Text("Use") }
                            }
                        }
                    }
                    if (currentJob?.id == item.id && item.status == AudioQueueStatus.PROCESSING) {
                        LinearProgressIndicator(
                            progress = { currentJob.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                            color = Teal,
                            trackColor = SoftWhite,
                        )
                    }
                }
            }
            Text(
                "Songs run one at a time, in order. The queue is saved across app restarts; Android or device safety limits may pause very long runs, and paused work can be resumed.",
                color = Muted,
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )
        }
    }
}

@Composable
private fun JobCard(
    job: SeparationJob,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    playbackState: WavPlaybackState,
    onPlay: () -> Unit,
    onPlayFirstPass: () -> Unit,
    onPlayMp3: () -> Unit,
    onExtraVocalReduction: () -> Unit,
    onExportMp3: () -> Unit,
    onPausePlayback: () -> Unit,
    onResumePlayback: () -> Unit,
    onStopPlayback: () -> Unit,
    onNewSong: () -> Unit,
    onDiscard: () -> Unit,
) {
    val isActive = job.status in setOf(JobStatus.RUNNING, JobStatus.QUEUED, JobStatus.PAUSING)
    val isPaused = job.status == JobStatus.PAUSED
    val isFailed = job.status == JobStatus.FAILED
    val isComplete = job.status == JobStatus.COMPLETED
    val isCanceled = job.status == JobStatus.CANCELED
    val hasCurrentMp3 = job.mp3OutputUri.isNotBlank() && job.mp3SourceUri == job.outputUri
    val tint = when {
        isComplete -> Teal
        isFailed -> Coral
        isPaused -> Amber
        else -> Teal
    }
    Card(colors = CardDefaults.cardColors(containerColor = SoftWhite), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(
                        when {
                            isComplete -> Icons.Default.CheckCircle
                            isPaused -> Icons.Default.Pause
                            isFailed -> Icons.Default.Info
                            else -> Icons.Default.Audiotrack
                        },
                        null,
                        tint = tint,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            isComplete && hasCurrentMp3 -> "WAV and MP3 ready"
                            isComplete && job.separationPass == SeparationPass.EXTRA_VOCAL_REDUCTION -> "Extra reduction ready"
                            isComplete -> "Instrumental ready"
                            isFailed -> "Processing needs attention"
                            isCanceled -> "Job canceled"
                            isPaused -> "Paused safely"
                            job.status == JobStatus.PAUSING -> "Pausing at checkpoint"
                            else -> "Working on your song"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink,
                    )
                    Text(job.sourceName, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("${(job.progress * 100).roundToInt()}%", fontWeight = FontWeight.Bold, color = tint, fontSize = 14.sp)
            }
            LinearProgressIndicator(
                progress = { job.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                color = tint,
                trackColor = Paper,
            )
            Text(job.phase, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(job.message, style = MaterialTheme.typography.bodySmall, color = Muted, lineHeight = 17.sp)
            if (job.totalChunks > 0 && !isComplete) {
                Text("Saved chunks ${job.completedChunks} / ${job.totalChunks} · ${job.profile.title} profile", color = Muted, fontSize = 11.sp)
            }
            when {
                isActive -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onPause,
                        enabled = job.status == JobStatus.RUNNING,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(13.dp),
                    ) {
                        androidx.compose.material3.Icon(Icons.Default.Pause, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (job.status == JobStatus.PAUSING) "Pausing…" else "Pause")
                    }
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) {
                        androidx.compose.material3.Icon(Icons.Default.Close, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Stop queue")
                    }
                }
                isPaused -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onResume, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) {
                        androidx.compose.material3.Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Resume")
                    }
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) { Text("Stop queue") }
                }
                isFailed -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onRetry, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) {
                        androidx.compose.material3.Icon(Icons.Default.Refresh, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Retry saved work")
                    }
                    OutlinedButton(onClick = onDiscard, shape = RoundedCornerShape(13.dp)) { Text("Discard") }
                }
                isComplete -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (playbackState.status) {
                        WavPlaybackStatus.STOPPED -> Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Button(onClick = onPlay, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) {
                                androidx.compose.material3.Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Play instrumental")
                            }
                            OutlinedButton(onClick = onNewSong, shape = RoundedCornerShape(13.dp)) { Text("Add songs") }
                        }
                        WavPlaybackStatus.PREPARING -> Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedButton(onClick = onStopPlayback, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) {
                                androidx.compose.material3.Icon(Icons.Default.Close, null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Cancel playback")
                            }
                            OutlinedButton(onClick = onNewSong, shape = RoundedCornerShape(13.dp)) { Text("Add songs") }
                        }
                        WavPlaybackStatus.PLAYING -> Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedButton(onClick = onPausePlayback, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) {
                                androidx.compose.material3.Icon(Icons.Default.Pause, null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Pause")
                            }
                            Button(onClick = onStopPlayback, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) {
                                androidx.compose.material3.Icon(Icons.Default.Close, null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Stop")
                            }
                        }
                        WavPlaybackStatus.PAUSED -> Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Button(onClick = onResumePlayback, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) {
                                androidx.compose.material3.Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Resume")
                            }
                            OutlinedButton(onClick = onStopPlayback, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) {
                                androidx.compose.material3.Icon(Icons.Default.Close, null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Stop")
                            }
                        }
                    }
                    playbackState.errorMessage?.let { message ->
                        Text(message, color = Coral, style = MaterialTheme.typography.bodySmall)
                    }
                    if (playbackState.status != WavPlaybackStatus.STOPPED) {
                        Text(
                            "Playback stays in this app and stops when you leave it.",
                            color = Muted,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                        )
                    }
                    if (job.separationPass == SeparationPass.FIRST) {
                        Text(
                            "Listen to this WAV first. If singing remains, you can choose one extra on-device reduction pass; it may also change instruments.",
                            color = Muted,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                        )
                        OutlinedButton(
                            onClick = onExtraVocalReduction,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(13.dp),
                        ) {
                            androidx.compose.material3.Icon(Icons.Default.Refresh, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Optional: reduce vocals again")
                        }
                    } else if (job.firstPassOutputUri.isNotBlank()) {
                        OutlinedButton(
                            onClick = onPlayFirstPass,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(13.dp),
                        ) {
                            androidx.compose.material3.Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Play first-pass WAV for comparison")
                        }
                    }
                    if (hasCurrentMp3) {
                        OutlinedButton(
                            onClick = onPlayMp3,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(13.dp),
                        ) {
                            androidx.compose.material3.Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Play saved MP3 · 320 kb/s")
                        }
                    } else {
                        OutlinedButton(
                            onClick = onExportMp3,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(13.dp),
                        ) {
                            androidx.compose.material3.Icon(Icons.Default.Download, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Export MP3 · 320 kb/s")
                        }
                        Text(
                            "MP3 is lossy; this high-bitrate export minimizes extra loss but is not lossless. Your WAV stays unchanged.",
                            color = Muted,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                        )
                    }
                }
                isCanceled -> OutlinedButton(onClick = onNewSong, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) { Text("Add more songs") }
            }
            if (job.status in setOf(JobStatus.RUNNING, JobStatus.PAUSING, JobStatus.PAUSED)) {
                Text("Low-RAM pauses auto-resume when memory recovers and other safety checks are clear; other pauses need Resume. Saved chunks survive interruptions, but Android/OEMs can still stop apps or shut down a device.", color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
            }
        }
    }
}

@Composable
private fun DeviceHealthCard(snapshot: DeviceSnapshot?) {
    Card(colors = CardDefaults.cardColors(containerColor = SoftWhite), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(Icons.Default.Thermostat, null, tint = Teal, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Device check", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.weight(1f))
                Text("BEST EFFORT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Muted, letterSpacing = .8.sp)
            }
            if (snapshot == null) {
                Text("Reading system memory and available thermal sensors…", color = Muted, style = MaterialTheme.typography.bodySmall)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricPill("Android thermal", snapshot.thermalLabel, Modifier.weight(1f))
                    MetricPill("Free RAM", DeviceMonitor.formatBytes(snapshot.availableRamBytes), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricPill("Battery temp", snapshot.batteryTemperatureC?.let { "%.1f °C".format(it) } ?: "Not reported", Modifier.weight(1f))
                    MetricPill(
                        snapshot.cpuSensorLabel?.let { "CPU · $it" } ?: "CPU temperature",
                        snapshot.cpuTemperatureC?.let { "%.1f °C".format(it) } ?: "Not reported",
                        Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricPill("App RAM (PSS)", DeviceMonitor.formatBytes(snapshot.appPssBytes), Modifier.weight(1f))
                    MetricPill("App CPU load", snapshot.appCpuPercent?.let { "%.0f%%".format(it) } ?: "Measuring…", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricPill("Native heap", DeviceMonitor.formatBytes(snapshot.appNativeHeapBytes), Modifier.weight(1f))
                    MetricPill("App storage free", DeviceMonitor.formatBytes(snapshot.availableStorageBytes), Modifier.weight(1f))
                }
                Text(
                    "CPU temperature depends on OEM-exposed thermal zones; ‘Not reported’ is normal. RAM and app CPU are system estimates. Battery/CPU degrees are informational—automatic thermal pause follows Android’s severe thermal status, not a fixed Celsius threshold.",
                    color = Muted,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(color = Paper, shape = RoundedCornerShape(13.dp), modifier = modifier) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 10.dp)) {
            Text(label.uppercase(), fontSize = 8.sp, color = Muted, fontWeight = FontWeight.Bold, letterSpacing = .55.sp, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text(value, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PrivacyCard(onCredits: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF2E1)), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
            androidx.compose.material3.Icon(Icons.Default.Security, null, tint = Color(0xFF9C6421), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("No audio upload. No account.", color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "Internet is used only for the checksum-verified model download. Selected audio is copied to private app storage and separated on-device.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 17.sp,
                )
                TextButtonCompact("Privacy, model credits & open-source notices", onCredits)
            }
        }
    }
}

@Composable
private fun TextButtonCompact(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp, vertical = 3.dp)) {
        Text(text, color = TealDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CreditsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var showNotices by remember { mutableStateOf(false) }
    val notices = remember {
        runCatching { context.assets.open("THIRD_PARTY_NOTICES.md").bufferedReader().use { it.readText() } }
            .getOrDefault("Third-party notices are unavailable in this build.")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SoftWhite,
        title = { Text("Model credits & privacy", fontWeight = FontWeight.Bold, color = Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("This standalone prototype uses the larger, vocal-focused UVR MDX-Net Voc FT model, downloaded on request from the sherpa-onnx source-separation-models release. Its SHA-256 is checked before installation.", color = Ink, style = MaterialTheme.typography.bodyMedium)
                Text("Attribution: UVR (Anjok07 and aufr33), UVR MDX-Net Voc FT. The UVR project asks third-party apps using its models to credit UVR and its developers. The sherpa-onnx runtime is Apache-2.0; model weights are a separate asset.", color = Muted, style = MaterialTheme.typography.bodySmall)
                Text("The app sends no selected audio to a server. Model delivery is the only network feature. The original file is not modified; the WAV is saved separately in Music/Vocal Remover Studio.", color = Muted, style = MaterialTheme.typography.bodySmall)
                Text("Degree sensors are best-effort and OEM-dependent. Safety pausing uses Android thermal status, free RAM and storage; it does not use a hard-coded temperature threshold.", color = Muted, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { openExternal(context, UVR_LICENSE_URL) }) { Text("Official UVR notice", fontSize = 11.sp) }
                    TextButton(onClick = { showNotices = true }) { Text("Bundled notices", fontSize = 11.sp) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
    )
    if (showNotices) {
        AlertDialog(
            onDismissRequest = { showNotices = false },
            containerColor = SoftWhite,
            title = { Text("Third-party notices", fontWeight = FontWeight.Bold, color = Ink) },
            text = {
                Text(
                    notices,
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = { TextButton(onClick = { showNotices = false }) { Text("Close") } },
        )
    }
}

private const val UVR_LICENSE_URL = "https://github.com/Anjok07/ultimatevocalremovergui#license"

private fun openExternal(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        .onFailure { Toast.makeText(context, "Could not open the link.", Toast.LENGTH_SHORT).show() }
}

private fun releaseQueueUriIfUnused(context: Context, sourceUri: String) {
    val stillNeeded = ProcessingQueueStore.current(context).any {
        it.sourceUri == sourceUri && it.status in setOf(
            AudioQueueStatus.WAITING,
            AudioQueueStatus.PROCESSING,
            AudioQueueStatus.PAUSED,
        )
    }
    if (!stillNeeded) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(sourceUri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }
}

private fun selectCompletedQueueItem(context: Context, item: AudioQueueItem) {
    if (item.status != AudioQueueStatus.COMPLETED || item.outputUri.isBlank()) return
    val current = SeparationJobStore.current(context)
    if (current?.status in setOf(JobStatus.RUNNING, JobStatus.QUEUED, JobStatus.PAUSING, JobStatus.PAUSED)) {
        Toast.makeText(context, "Wait for the active queue to finish before opening an earlier result.", Toast.LENGTH_LONG).show()
        return
    }
    SeparationJobStore.save(
        context,
        SeparationJob(
            id = item.id,
            sourceName = item.sourceName,
            sourceUri = item.sourceUri,
            profile = item.profile,
            status = JobStatus.COMPLETED,
            phase = if (item.separationPass == SeparationPass.EXTRA_VOCAL_REDUCTION) "Extra vocal reduction ready" else "Instrumental ready",
            progress = 1f,
            message = if (item.mp3OutputUri.isNotBlank() && item.mp3SourceUri == item.outputUri) {
                "WAV and MP3 are saved. The WAV and selected original remain unchanged."
            } else {
                "Saved in Music/Vocal Remover Studio. The selected original remains unchanged."
            },
            outputUri = item.outputUri,
            firstPassOutputUri = item.firstPassOutputUri.ifBlank {
                if (item.separationPass == SeparationPass.FIRST) item.outputUri else ""
            },
            separationPass = item.separationPass,
            mp3SourceUri = item.mp3SourceUri,
            mp3OutputUri = item.mp3OutputUri,
        ),
    )
}

private fun enqueueAudioUris(context: Context, uris: List<Uri>, profile: ProcessingProfile) {
    var permissionFailures = 0
    val candidates = uris.distinctBy(Uri::toString).mapNotNull { uri ->
        val persisted = runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.isSuccess
        if (!persisted) {
            permissionFailures++
            return@mapNotNull null
        }
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "selected-song.mp3"
        AudioQueueItem(
            id = UUID.randomUUID().toString(),
            sourceName = name,
            sourceUri = uri.toString(),
            profile = profile,
        )
    }
    val result = ProcessingQueueStore.enqueue(context, candidates)
    val stillQueuedUris = ProcessingQueueStore.current(context)
        .filter { it.status in setOf(AudioQueueStatus.WAITING, AudioQueueStatus.PROCESSING, AudioQueueStatus.PAUSED) }
        .mapTo(mutableSetOf()) { it.sourceUri }
    candidates.filter { it.sourceUri !in stillQueuedUris }
        .forEach { releaseQueueUriIfUnused(context, it.sourceUri) }
    if (result.added > 0) startServiceAction(context, SeparationService.ACTION_QUEUE)

    val message = buildString {
        append("${result.added} MP3${if (result.added == 1) "" else "s"} added to the background queue.")
        if (result.duplicates > 0) append(" ${result.duplicates} duplicate file${if (result.duplicates == 1) " was" else "s were"} already queued.")
        if (result.queueFull > 0) append(" ${result.queueFull} skipped: the queue supports up to 100 pending songs.")
        if (permissionFailures > 0) append(" $permissionFailures file${if (permissionFailures == 1) " could" else "s could"} not be kept accessible after restart.")
    }
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}

private fun discardTerminalJob(context: Context, job: SeparationJob) {
    if (job.status in setOf(JobStatus.RUNNING, JobStatus.PAUSING, JobStatus.PAUSED, JobStatus.QUEUED)) return
    runCatching { File(File(context.filesDir, "jobs"), job.id).deleteRecursively() }
    if (job.pendingWavUri.isNotBlank()) {
        runCatching { context.contentResolver.delete(Uri.parse(job.pendingWavUri), null, null) }
    }
    if (job.pendingMp3Uri.isNotBlank()) {
        runCatching { context.contentResolver.delete(Uri.parse(job.pendingMp3Uri), null, null) }
    }
    SeparationJobStore.save(context, null)
    androidx.core.app.NotificationManagerCompat.from(context).cancel(SeparationService.NOTIFICATION_ID)
}

private fun startServiceAction(context: Context, action: String) {
    val intent = Intent(context, SeparationService::class.java).setAction(action)
    runCatching { androidx.core.content.ContextCompat.startForegroundService(context, intent) }
        .onFailure { failure ->
            val detail = failure.message?.take(180)?.ifBlank { null } ?: failure.javaClass.simpleName
            Toast.makeText(context, "Could not control the background queue: $detail", Toast.LENGTH_LONG).show()
        }
}
