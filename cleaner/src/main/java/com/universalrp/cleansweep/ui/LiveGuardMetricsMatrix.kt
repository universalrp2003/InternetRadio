package com.universalrp.cleansweep.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
@Composable
fun LiveGuardMetricsMatrix(state: UiState, vm: MainViewModel) {
    val storage = state.storage?.takeIf { it.total > 0 && it.free in 0..it.total }
    val device = state.health?.device
    val ram = ReadingPolicy.ramUsedFraction(device?.totalRamBytes, device?.availableRamBytes)
    val quality = state.liveNetworkQuality
    val report = state.securityReport
    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("Live Guard readings"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                    tr(ReadingPolicy.securityValue(report)), tr(ReadingPolicy.securityNote(report)),
                    if (report == null) tr("Not checked") else if (report.unavailableChecks.isNotEmpty()) tr("Incomplete") else tr("Review findings"),
                    if (report == null || report.unavailableChecks.isNotEmpty()) TextSecondary else if (report.highCount > 0) WarnAmber else AccentCyan,
                    report?.scannedAtMs, onClick = { vm.loadSecurity() })
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
    SimpleDateFormat("dd MMM, HH:mm:ss", Locale.getDefault()).format(Date(atMs))
