package com.universalrp.cleansweep.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.universalrp.cleansweep.data.AppLang
import com.universalrp.cleansweep.data.DisplayTime
import com.universalrp.cleansweep.data.Lang
import com.universalrp.cleansweep.data.formatBytes
import com.universalrp.cleansweep.data.tr
import com.universalrp.cleansweep.updates.LiveGuardUpdates
import com.universalrp.cleansweep.ui.theme.TextSecondary

private fun localizedReleaseNotes(notes: String): String {
    val marker = "## தமிழ்"
    return if (Lang.isTamil && marker in notes) notes.substringAfter(marker).trim()
    else notes.substringBefore(marker).trim()
}

@Composable
fun LiveGuardUpdateCard() {
    val context = LocalContext.current
    val state by LiveGuardUpdates.state.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf(false) }
    var automatic by remember { mutableStateOf(LiveGuardUpdates.automatic(context)) }
    var canInstall by remember { mutableStateOf(LiveGuardUpdates.canInstall(context)) }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) canInstall = LiveGuardUpdates.canInstall(context) }
        lifecycle.lifecycle.addObserver(observer); onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(tr("App updates"), style = MaterialTheme.typography.titleSmall)
                    Text(tr("Verified in-app download; Android confirms installation."), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text(tr("Checks RP-Android-Apps first; InternetRadio remains as a grace-period fallback."), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
                PulsingUpdateButton(available = state.release != null && !state.busy, onClick = { dialog = true })
            }
            if (state.downloading) Text(tr("Downloading %s of %s", state.bytes.formatBytes(), (state.release?.bytes ?: 0).formatBytes()), style = MaterialTheme.typography.bodySmall)
        }
    }
    if (dialog) AlertDialog(
        onDismissRequest = { dialog = false },
        title = { Text(tr("App updates")) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("Installed version: %s", com.universalrp.cleansweep.BuildConfig.VERSION_NAME))
            Text(tr("Downloads only a stable published Live Guard APK from the official GitHub repository. SHA-256, package, higher version code and the installed signing certificate must match. No browser download or silent installation."), style = MaterialTheme.typography.bodySmall)
            Text(tr("Android may require Install unknown apps permission for Live Guard. Enable it only if you trust this update; you can turn it off afterward. Android installer confirmation is required. Play Protect and any scanning decisions belong to Android and cannot be forced or bypassed."), style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth()) {
                Text(tr("Check for updates on app launch"), Modifier.weight(1f))
                Switch(checked = automatic, onCheckedChange = { automatic = it; LiveGuardUpdates.setAutomatic(context, it) })
            }
            state.checkedAt?.let { Text(tr("Last update check: %s", DisplayTime.dated(it, Lang.languageIn(context) == AppLang.TA)), style = MaterialTheme.typography.labelSmall) }
            state.message?.let { Text(tr(it), style = MaterialTheme.typography.bodySmall) }
            state.release?.let { release ->
                Text(tr("Published version: %s", release.version))
                Text(tr("Release source") + ": ${release.repository}", style = MaterialTheme.typography.labelSmall)
                Text(tr("Published: %s", DisplayTime.dated(release.publishedAt, Lang.languageIn(context) == AppLang.TA)), style = MaterialTheme.typography.labelSmall)
                Text(localizedReleaseNotes(release.notes).ifBlank { tr("No release notes were published.") }, style = MaterialTheme.typography.bodySmall)
                if (state.downloading) {
                    LinearProgressIndicator(progress = { (state.bytes.toDouble() / release.bytes).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text(tr(if (state.bytes == release.bytes) "Verifying downloaded APK…" else "Downloading APK inside Live Guard…"))
                } else if (state.verifiedApk != null) {
                    if (!canInstall) Text(tr("Installation access is not granted. Tap below to open Android permission settings, then return and tap Install."), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { if (canInstall) LiveGuardUpdates.install(context) else LiveGuardUpdates.requestInstallPermission(context) }, enabled = !state.busy) {
                        Text(tr(if (canInstall) "Install with Android confirmation" else "Allow installation for Live Guard"))
                    }
                } else Button(onClick = { LiveGuardUpdates.download(context) }, enabled = !state.busy) { Text(tr("Download and verify APK")) }
            }
            if (state.busy) TextButton(onClick = { LiveGuardUpdates.cancel() }) { Text(tr("Cancel")) }
            TextButton(onClick = { LiveGuardUpdates.check(context) }, enabled = !state.busy) { Text(tr("Check published updates")) }
        } },
        confirmButton = { TextButton(onClick = { dialog = false }) { Text(tr("Close")) } },
    )
}

/** App-wide launch prompt; it appears only for a newer verified stable release. */
@Composable
fun LiveGuardUpdatePrompt() {
    val context = LocalContext.current
    val state by LiveGuardUpdates.state.collectAsStateWithLifecycle()
    var showingVersion by remember { mutableStateOf<String?>(null) }
    var canInstall by remember { mutableStateOf(LiveGuardUpdates.canInstall(context)) }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) canInstall = LiveGuardUpdates.canInstall(context)
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }

    val release = state.release
    LaunchedEffect(release?.version, state.busy) {
        if (!state.busy && LiveGuardUpdates.automatic(context) && release != null &&
            !LiveGuardUpdates.isDismissed(context, release.version)
        ) showingVersion = release.version
    }
    val prompted = release?.takeIf { it.version == showingVersion }
    if (prompted != null) AlertDialog(
        onDismissRequest = {
            LiveGuardUpdates.dismiss(context, prompted.version)
            showingVersion = null
        },
        title = { Text(tr("Live Guard update available")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(tr("Version %s is available (installed: %s).", prompted.version, com.universalrp.cleansweep.BuildConfig.VERSION_NAME))
                Text(tr("Release source") + ": ${prompted.repository}", style = MaterialTheme.typography.labelSmall)
                Text(tr("Checks RP-Android-Apps first; InternetRadio remains as a grace-period fallback."), style = MaterialTheme.typography.labelSmall)
                Text(tr("This stable APK is downloaded inside Live Guard and verified against its SHA-256, package, newer version and installed signing certificate."), style = MaterialTheme.typography.bodySmall)
                Text(tr("Android requires per-app Install unknown apps access and shows its own confirmation. Play Protect remains enabled and is not bypassed."), style = MaterialTheme.typography.bodySmall)
                Text(tr("What’s new"), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text(prompted.notes.ifBlank { tr("No release notes were published.") }, style = MaterialTheme.typography.bodySmall)
                state.message?.let { Text(tr(it), style = MaterialTheme.typography.bodySmall, color = TextSecondary) }
                if (state.downloading) {
                    LinearProgressIndicator(
                        progress = { (state.bytes.toDouble() / prompted.bytes.coerceAtLeast(1L)).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(tr("Downloading APK inside Live Guard…"), style = MaterialTheme.typography.labelSmall)
                }
                if (state.verifiedApk != null && !canInstall) {
                    Text(tr("Installation access is not granted. Android Settings will open; allow Live Guard only if you trust this update."), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            when {
                state.verifiedApk != null -> TextButton(enabled = !state.busy, onClick = {
                    if (LiveGuardUpdates.canInstall(context)) {
                        canInstall = true
                        LiveGuardUpdates.install(context)
                    } else {
                        canInstall = false
                        LiveGuardUpdates.requestInstallPermission(context)
                    }
                }) { Text(tr(if (canInstall) "Install with Android confirmation" else "Allow installation for Live Guard")) }
                state.downloading -> TextButton(enabled = !state.busy, onClick = { LiveGuardUpdates.cancel() }) { Text(tr("Cancel")) }
                else -> TextButton(enabled = !state.busy, onClick = { LiveGuardUpdates.download(context) }) { Text(tr("Download and verify APK")) }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                LiveGuardUpdates.dismiss(context, prompted.version)
                showingVersion = null
            }) { Text(tr("Later")) }
        },
    )
}

@Composable
private fun PulsingUpdateButton(available: Boolean, onClick: () -> Unit) {
    if (available) {
        // Animate only actual eligible release metadata, never failures/unpublished test builds.
        val transition = rememberInfiniteTransition(label = "Published update")
        val alpha by transition.animateFloat(initialValue = 0.55f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "Update availability")
        Button(onClick = onClick, modifier = Modifier.graphicsLayer { this.alpha = alpha }) { Text(tr("Update")) }
    } else TextButton(onClick = onClick) { Text(tr("Update")) }
}
