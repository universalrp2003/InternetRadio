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
import androidx.compose.material.icons.filled.OpenInNew
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
import com.universalrp.vocalremover.health.DeviceMonitor
import com.universalrp.vocalremover.health.DeviceSnapshot
import com.universalrp.vocalremover.model.ModelRepository
import com.universalrp.vocalremover.processing.JobStatus
import com.universalrp.vocalremover.processing.ProcessingProfile
import com.universalrp.vocalremover.processing.SeparationJob
import com.universalrp.vocalremover.processing.SeparationJobStore
import com.universalrp.vocalremover.processing.SeparationService
import com.universalrp.vocalremover.processing.ServiceTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SeparationJobStore.initialize(applicationContext)
        val recoveredJob = SeparationJobStore.current(applicationContext)
        if (recoveredJob?.status in setOf(JobStatus.RUNNING, JobStatus.QUEUED, JobStatus.PAUSING)) {
            androidx.core.content.ContextCompat.startForegroundService(
                this,
                Intent(this, SeparationService::class.java).setAction(SeparationService.ACTION_START),
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
                VocalRemoverScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VocalRemoverScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val job by SeparationJobStore.job.collectAsState()
    val serviceSnapshot by ServiceTelemetry.device.collectAsState()
    val monitor = remember { DeviceMonitor(context.applicationContext) }
    var localSnapshot by remember { mutableStateOf<DeviceSnapshot?>(null) }
    var keepScreenAwake by remember { mutableStateOf(AppPreferences.keepScreenAwake(context)) }
    var selectedProfile by remember { mutableStateOf(job?.profile ?: ProcessingProfile.BALANCED) }
    var modelReady by remember { mutableStateOf(false) }
    var showCredits by remember { mutableStateOf(false) }
    var isChoosingAudio by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        isChoosingAudio = false
        if (uri != null) createJobFromUri(context, uri, selectedProfile)
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        isChoosingAudio = false
        if (granted) {
            picker.launch(arrayOf("audio/mpeg"))
        } else {
            Toast.makeText(
                context,
                "Allow notifications so the long-running foreground job stays visible and controllable.",
                Toast.LENGTH_LONG,
            ).show()
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
    DisposableEffect(keepScreenAwake, job?.status, activity) {
        val active = job?.status in setOf(JobStatus.QUEUED, JobStatus.RUNNING, JobStatus.PAUSING)
        if (keepScreenAwake && active) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val active = job?.status in setOf(JobStatus.QUEUED, JobStatus.RUNNING, JobStatus.PAUSING, JobStatus.PAUSED)
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
                    onOpen = { openWav(context, job!!.outputUri) },
                    onNewSong = {
                        discardTerminalJob(context, job!!)
                        SeparationJobStore.initialize(context)
                    },
                )
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
                ModelCard(modelReady = modelReady, onCredits = { showCredits = true })
                Button(
                    onClick = {
                        isChoosingAudio = true
                        if (Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            picker.launch(arrayOf("audio/mpeg"))
                        }
                    },
                    enabled = !isChoosingAudio,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TealDark),
                ) {
                    if (isChoosingAudio) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else androidx.compose.material3.Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(if (isChoosingAudio) "Choose an MP3…" else "Choose an MP3", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Text(
                    "First use downloads a 28.3 MiB model. Only the model is downloaded; your selected song is processed locally and never uploaded by this app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            }

            DeviceHealthCard(currentSnapshot)
            PrivacyCard(onCredits = { showCredits = true })
            Text(
                "Vocal Remover Studio · standalone prototype · WAV instrumental export",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                textAlign = TextAlign.Center,
            )
        }
    }

    if (showCredits) CreditsDialog(onDismiss = { showCredits = false })
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
                    "While this app is open during a job · uses more battery",
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
private fun ModelCard(modelReady: Boolean, onCredits: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F1ED)), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(SoftWhite), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(
                    if (modelReady) Icons.Default.CheckCircle else Icons.Default.Download,
                    contentDescription = null,
                    tint = Teal,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (modelReady) "On-device model is ready" else "One-time model download", color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    if (modelReady) "UVR MDX-Net · stored privately on this phone · 28.3 MiB"
                    else "UVR MDX-Net 9482 · 28.3 MiB over HTTPS · checksum verified",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 17.sp,
                )
                TextButtonCompact("Model credits & terms", onCredits)
            }
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
    onOpen: () -> Unit,
    onNewSong: () -> Unit,
) {
    val isActive = job.status in setOf(JobStatus.RUNNING, JobStatus.QUEUED, JobStatus.PAUSING)
    val isPaused = job.status == JobStatus.PAUSED
    val isFailed = job.status == JobStatus.FAILED
    val isComplete = job.status == JobStatus.COMPLETED
    val isCanceled = job.status == JobStatus.CANCELED
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
                        Text("Cancel")
                    }
                }
                isPaused -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onResume, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) {
                        androidx.compose.material3.Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Resume")
                    }
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) { Text("Cancel job") }
                }
                isFailed -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onRetry, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) {
                        androidx.compose.material3.Icon(Icons.Default.Refresh, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Retry saved work")
                    }
                    OutlinedButton(onClick = onNewSong, shape = RoundedCornerShape(13.dp)) { Text("Discard") }
                }
                isComplete -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onOpen, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) {
                        androidx.compose.material3.Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open / play WAV")
                    }
                    OutlinedButton(onClick = onNewSong, shape = RoundedCornerShape(13.dp)) { Text("New song") }
                }
                isCanceled -> OutlinedButton(onClick = onNewSong, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) { Text("Choose another song") }
            }
            if (job.status in setOf(JobStatus.RUNNING, JobStatus.PAUSING, JobStatus.PAUSED)) {
                Text("A foreground notification keeps controls available. Android/OEMs can still stop apps or shut down a device; saved chunks are used when you resume.", color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
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
                Text("This standalone prototype uses the UVR MDX-Net 9482 model, downloaded on request from the sherpa-onnx source-separation-models release. Its SHA-256 is checked before installation.", color = Ink, style = MaterialTheme.typography.bodyMedium)
                Text("Attribution: UVR (Anjok07 and aufr33), UVR_MDXNET_9482. The UVR project asks third-party apps using its models to credit UVR and its developers. The sherpa-onnx runtime is Apache-2.0; model weights are a separate asset.", color = Muted, style = MaterialTheme.typography.bodySmall)
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

private fun createJobFromUri(context: Context, uri: Uri, profile: ProcessingProfile) {
    val current = SeparationJobStore.current(context)
    if (current != null && current.status in setOf(JobStatus.RUNNING, JobStatus.PAUSING, JobStatus.PAUSED, JobStatus.QUEUED)) {
        Toast.makeText(context, "A job is already active. Finish or cancel it first.", Toast.LENGTH_LONG).show()
        return
    }
    runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    val name = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "selected-song.mp3"

    current?.let { discardTerminalJob(context, it) }
    val job = SeparationJob(
        id = UUID.randomUUID().toString(),
        sourceName = name,
        sourceUri = uri.toString(),
        profile = profile,
        status = JobStatus.QUEUED,
        phase = "Starting",
        message = "Preparing an on-device copy. The original will remain unchanged.",
    )
    SeparationJobStore.save(context, job)
    val intent = Intent(context, SeparationService::class.java).setAction(SeparationService.ACTION_START)
    androidx.core.content.ContextCompat.startForegroundService(context, intent)
}

private fun discardTerminalJob(context: Context, job: SeparationJob) {
    if (job.status in setOf(JobStatus.RUNNING, JobStatus.PAUSING, JobStatus.PAUSED, JobStatus.QUEUED)) return
    runCatching { File(File(context.filesDir, "jobs"), job.id).deleteRecursively() }
    if (job.status != JobStatus.COMPLETED && job.outputUri.isNotBlank()) {
        runCatching { context.contentResolver.delete(Uri.parse(job.outputUri), null, null) }
    }
    SeparationJobStore.save(context, null)
    androidx.core.app.NotificationManagerCompat.from(context).cancel(SeparationService.NOTIFICATION_ID)
}

private fun startServiceAction(context: Context, action: String) {
    val intent = Intent(context, SeparationService::class.java).setAction(action)
    androidx.core.content.ContextCompat.startForegroundService(context, intent)
}

private fun openWav(context: Context, value: String) {
    if (value.isBlank()) return
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(value), "audio/wav")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "No audio player is installed. The WAV is in Music/Vocal Remover Studio.", Toast.LENGTH_LONG).show()
    }
}
