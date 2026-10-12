package com.universalrp.cleansweep.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.universalrp.cleansweep.MainViewModel
import com.universalrp.cleansweep.Screen
import com.universalrp.cleansweep.UiState
import com.universalrp.cleansweep.data.*
import com.universalrp.cleansweep.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** No socket/binder reads during composition; each card displays dated observed values only. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveGuardMetricsMatrix(state: UiState, vm: MainViewModel) {
    val storage = state.storage?.takeIf { it.total > 0 && it.free in 0..it.total }
    val device = state.health?.device
    val ram = ReadingPolicy.ramUsedFraction(device?.totalRamBytes, device?.availableRamBytes)
    val quality = state.liveNetworkQuality
    val report = state.securityReport
    val previous = state.lastPermissionReview?.takeIf { it.valid() }
    val previousFuture = previous?.isFuture(System.currentTimeMillis()) == true
    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Keep the owner's phone-age feature visible; wrap instead of crowding the title on small/large-font screens.
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Live Guard readings"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterVertically))
                PhoneAgeBadge(state.phoneAge, LanguageSourcePolicy.isTamil(Lang.current))
            }
            Text(device?.let { "Android ${it.androidVersion} / API ${it.sdk}" } ?: tr("Device not read yet"),
                style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricPillarCard(Modifier.weight(1f), Icons.Outlined.Storage, tr("Storage free"),
                    storage?.free?.formatBytes() ?: tr("Not reported"),
                    storage?.let { "${it.used.formatBytes()} / ${it.total.formatBytes()}" } ?: tr("Not measured"),
                    storage?.let { "${(it.usedFraction * 100).toInt()}% ${tr("used")}" } ?: tr("Not measured"),
                    if (storage == null) TextSecondary else if (storage.usedFraction > 0.9) WarnAmber else AccentCyan,
                    state.health?.capturedAtMs)
                MetricPillarCard(Modifier.weight(1f), Icons.Outlined.Memory, tr("RAM free"),
                    if (ram != null) device!!.availableRamBytes.formatBytes() else tr("Not reported"),
                    if (ram != null) "${device!!.totalRamBytes.formatBytes()} ${tr("total")}" else tr("Not measured"),
                    tr("Not a speed/safety score"), AccentCyan, state.health?.capturedAtMs)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricPillarCard(Modifier.weight(1f), Icons.Outlined.Security, tr("Permission review"),
                    if (report != null) tr(ReadingPolicy.securityValue(report)) else tr(if (!state.lastPermissionReviewLoaded) "Reading saved review…" else if (previous != null) "Previous review" else "Not checked"),
                    when {
                        report != null -> tr(ReadingPolicy.securityNote(report))
                        previousFuture -> tr("Phone clock changed. Saved review is not a current verification.")
                        previous != null -> tr("Saved review only. Run a fresh check for current findings.")
                        else -> tr(ReadingPolicy.securityNote(null))
                    },
                    when { state.securityBusy -> tr("Checking…"); report != null -> tr(if (report.unavailableChecks.isNotEmpty()) "Incomplete" else "Review findings")
                        previous != null -> tr(if (previous.incomplete) "Previous review incomplete" else "Tap to check again")
                        else -> tr("Tap to run a review") },
                    if (report == null || report.unavailableChecks.isNotEmpty()) TextSecondary else if (report.highCount > 0) WarnAmber else AccentCyan,
                    report?.scannedAtMs ?: previous?.atMs, onClick = { vm.loadSecurity() })
                MetricPillarCard(Modifier.weight(1f), Icons.Outlined.Speed, tr("Network probe"),
                    tr(ReadingPolicy.pingValue(quality?.pingMs)), quality?.let { tr(it.labelEnglish) } ?: tr("Not measured"),
                    when { quality == null -> tr("Not measured"); quality.isWifi -> "Wi-Fi"; quality.isMobile -> tr("Cellular"); else -> tr("Other / offline") },
                    when (quality?.grade) { LiveNetworkQuality.QualityGrade.TOP_QUALITY -> GoodGreen
                        LiveNetworkQuality.QualityGrade.MEDIUM_QUALITY -> WarnAmber
                        LiveNetworkQuality.QualityGrade.BAD_QUALITY -> DangerRed
                        else -> TextSecondary }, quality?.capturedAtMs,
                    onClick = { vm.navigate(Screen.MOBILE); vm.loadMobile() })
            }
            Text(tr("Quality uses a small active latency probe, not a speed test. Missing ping/jitter stays unknown."),
                style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}

/** Clearly separate a coarse age estimate from the measured storage/RAM/network readings. */
@Composable
private fun PhoneAgeBadge(age: PhoneAgeEstimator.PhoneAge?, isTamil: Boolean) {
    var showDetails by remember { mutableStateOf(false) }
    if (showDetails) AlertDialog(
        onDismissRequest = { showDetails = false },
        title = { Text(tr("Estimated phone age")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("This is a coarse model/initial-Android-generation estimate. The reference months are approximate, not verified launch dates."))
                Text(tr("Android does not reveal this handset's exact manufacture, purchase or first-use date. An Android upgrade, security patch or uptime does not prove phone age."))
                val basis = when (age?.basis) {
                    PhoneAgeEstimator.Basis.MODEL_FAMILY -> "Estimate basis: model-family heuristic"
                    PhoneAgeEstimator.Basis.FACTORY_ANDROID_GENERATION -> "Estimate basis: factory Android generation"
                    else -> "Not enough model/factory information to estimate age."
                }
                Text(tr(basis), color = TextSecondary)
            }
        },
        confirmButton = { TextButton(onClick = { showDetails = false }) { Text(tr("Close")) } },
    )
    Surface(
        modifier = Modifier.widthIn(max = 220.dp).clickable { showDetails = true },
        shape = RoundedCornerShape(12.dp), color = AccentCyan.copy(alpha = 0.12f),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(tr("Estimated phone age"), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(age?.displayString(isTamil) ?: tr("Calculating age estimate…"),
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = AccentCyan)
        }
    }
}

@Composable
private fun MetricPillarCard(
    modifier: Modifier, icon: ImageVector, title: String, value: String, note: String,
    tag: String, tone: Color, atMs: Long?, onClick: (() -> Unit)? = null,
) {
    Surface(shape = RoundedCornerShape(14.dp), color = SurfaceHigh,
        modifier = modifier.let { if (onClick != null) it.clickable(onClick = onClick) else it }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, null, tint = tone, modifier = Modifier.size(21.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tone)
            Text(note, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(tag, style = MaterialTheme.typography.labelSmall, color = tone)
            if (ReadingPolicy.timestampIsUsable(atMs)) Text(tr("Last checked %s", readingTime(atMs!!)),
                style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}

internal fun readingTime(atMs: Long): String = if (atMs <= 0) tr("Not checked") else
    DisplayTime.dated(atMs, Lang.isTamil, seconds = true)
