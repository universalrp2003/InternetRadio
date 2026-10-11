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
                Text(tr("Daily update check on app launch"), Modifier.weight(1f))
                Switch(checked = automatic, onCheckedChange = { automatic = it; LiveGuardUpdates.setAutomatic(context, it) })
            }
            state.checkedAt?.let { Text(tr("Last update check: %s", DisplayTime.dated(it, Lang.languageIn(context) == AppLang.TA)), style = MaterialTheme.typography.labelSmall) }
            state.message?.let { Text(tr(it), style = MaterialTheme.typography.bodySmall) }
            state.release?.let { release ->
                Text(tr("Published version: %s", release.version))
                Text(tr("Published: %s", DisplayTime.dated(release.publishedAt, Lang.languageIn(context) == AppLang.TA)), style = MaterialTheme.typography.labelSmall)
                Text(tr("Release notes are publisher text; not automatically translated."), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(release.notes.ifBlank { tr("No release notes were published.") }, style = MaterialTheme.typography.bodySmall)
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
