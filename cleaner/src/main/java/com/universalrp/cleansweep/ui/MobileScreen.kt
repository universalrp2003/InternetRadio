package com.universalrp.cleansweep.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.universalrp.cleansweep.MainViewModel
import com.universalrp.cleansweep.Screen
import com.universalrp.cleansweep.UiState
import com.universalrp.cleansweep.data.CellTower
import com.universalrp.cleansweep.data.DataAppUse
import com.universalrp.cleansweep.data.SpeedMeter
import com.universalrp.cleansweep.data.formatBytes
import com.universalrp.cleansweep.data.tr
import com.universalrp.cleansweep.ui.theme.AccentCyan
import com.universalrp.cleansweep.ui.theme.AccentViolet
import com.universalrp.cleansweep.ui.theme.DangerRed
import com.universalrp.cleansweep.ui.theme.GoodGreen
import com.universalrp.cleansweep.ui.theme.OutlineC
import com.universalrp.cleansweep.ui.theme.SurfaceHigh
import com.universalrp.cleansweep.ui.theme.TextPrimary
import com.universalrp.cleansweep.ui.theme.TextSecondary
import com.universalrp.cleansweep.ui.theme.WarnAmber
import java.util.Locale

/**
 * "Mobile & data" — the mobile twin of the Wi-Fi screen.
 *
 * It answers the questions the user asked for: which operator and SIM is live, is this 5G or
 * 4G, how strong is the signal in real dBm, which towers are in reach, what is the public IP
 * and ISP, how much ping and jitter the link has, how fast it really goes (with a size the
 * user picks, because a speed test spends real data) and how much data has already gone
 * through today — mobile and Wi-Fi separately.
 */
@Composable
fun MobileScreen(state: UiState, vm: MainViewModel) {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.onPhonePermissionResult() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.goBack() }) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Home", tint = TextSecondary)
                }
                Text(
                    tr("Mobile & data"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.loadMobile() }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = TextSecondary)
                }
            }
        }

        // ------------------------------------------------------------ signal card
        val mobile = state.mobile
        if (mobile == null) {
            item {
                PanelCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (state.mobileBusy) tr("Reading the sensors…") else tr("Mobile network"),
                            color = TextSecondary,
                        )
                    }
                }
            }
        } else {
            item { MobileStatusCard(state, vm) }
            if (mobile.needsPhonePermission) {
                item {
                    PhonePermissionCard(
                        hint = mobile.permissionHint.orEmpty(),
                        onAllow = { permissionLauncher.launch(vm.phonePermissions()) },
                        onSettings = { vm.requestNetworkPermission() },
                    )
                }
            }
            if (mobile.towers.isNotEmpty()) {
                item { TowerCard(mobile.towers) }
            }
            if (mobile.notes.isNotEmpty()) {
                item {
                    PanelCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            mobile.notes.forEach { note ->
                                Text(
                                    "• $note",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(bottom = 3.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // ----------------------------------------------------------- ping & jitter
        item { PingCard(state, vm) }

        // -------------------------------------------------------------- speed test
        item { SpeedTestCard(state, vm) }

        // -------------------------------------------------------------- app network tracker (v2.17)
        item { AppTrackerCard(state, vm) }

        // -------------------------------------------------------------- data usage
        item { DataUsageCard(state, vm) }

        item {
            Text(
                tr(
                    "Everything on this screen is measured on your phone right now. Nothing is " +
                        "uploaded; the ping test sends a few kilobytes and the speed test only runs " +
                        "when you tap it."
                ),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun MobileStatusCard(state: UiState, vm: MainViewModel) {
    val mobile = state.mobile ?: return
    val tone = when (mobile.qualityTone) {
        0 -> GoodGreen
        2 -> DangerRed
        else -> WarnAmber
    }
    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.SignalCellularAlt, contentDescription = null, tint = tone)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        mobile.operatorName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        mobile.networkType +
                            (if (mobile.is5g) " • 5G SA/NSA" else "") +
                            (if (mobile.roaming) " • roaming" else "") +
                            (if (mobile.isVpn) " • VPN on" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                Text(
                    mobile.signalDbm?.let { "$it dBm" } ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = tone,
                )
            }
            Spacer(Modifier.height(8.dp))
            SignalBars(mobile.signalLevel ?: 0, tone)
            Spacer(Modifier.height(6.dp))
            Text(
                tr("Signal quality") + ": " + mobile.quality,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                mobile.dataEnabledString(),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(10.dp))
            MobileInfoRow(tr("SIM & operator"), mobile.operatorName)
            mobile.sims.forEach { sim ->
                MobileInfoRow(
                    "SIM ${sim.slot}" + (if (sim.isDefaultData) " (data)" else ""),
                    sim.carrier + (sim.label.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""),
                )
            }
            if (mobile.sims.any { it.signalDbm != null }) {
                val best = mobile.sims.mapNotNull { it.signalDbm }.maxOrNull()
                if (best != null) MobileInfoRow("Signal now", "$best dBm")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                tr(
                    "dBm is the honest number: −60 is excellent, −100 is barely usable, and a " +
                        "stronger bar count can lie. Values are read from the system, not estimated."
                ),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }
    }
}

private fun com.universalrp.cleansweep.data.MobileSnapshot.dataEnabledString(): String = when {
    dataEnabled && roaming -> "Mobile data is ON and roaming is allowed — watch your bill."
    dataEnabled && activeOnMobile -> "Mobile data is ON and carrying your traffic."
    dataEnabled && activeOnWifi -> "Mobile data is ON — Wi-Fi is carrying your traffic right now."
    dataEnabled -> "Mobile data is ON."
    activeOnWifi -> "Mobile data is switched off. You are on Wi-Fi."
    else -> "Mobile data is switched off."
}

/** Four little bars, filled to the reported level. */
@Composable
private fun SignalBars(level: Int, tone: Color) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (1..4).forEach { index ->
            val filled = index <= level
            Box(
                Modifier
                    .width(18.dp)
                    .height((6 + index * 5).dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (filled) tone else SurfaceHigh)
                    .border(1.dp, OutlineC, RoundedCornerShape(3.dp))
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            "$level / 4",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
    }
}

@Composable
private fun MobileInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Spacer(Modifier.width(10.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PhonePermissionCard(hint: String, onAllow: () -> Unit, onSettings: () -> Unit) {
    PanelCard(
        Modifier
            .fillMaxWidth()
            .border(1.dp, WarnAmber.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Warning, contentDescription = null, tint = WarnAmber)
                Spacer(Modifier.width(10.dp))
                Text(
                    tr("Phone permission gives the real numbers"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(hint, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAllow,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentCyan,
                        contentColor = Color(0xFF03202B),
                    ),
                ) {
                    Text(tr("Allow and rescan"), fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onSettings) {
                    Text(tr("App settings"), color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun TowerCard(towers: List<CellTower>) {
    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                tr("Cell towers in reach") + " (${towers.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            towers.take(8).forEach { tower ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(
                        tower.technology,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = AccentViolet,
                        modifier = Modifier.width(34.dp),
                    )
                    Text(
                        buildString {
                            tower.id?.let { append("cell $it") }
                            tower.pci?.let { append(if (isNotEmpty()) " • " else "").append("PCI $it") }
                            tower.tac?.let { append(if (isNotEmpty()) " • " else "").append("TAC $it") }
                            if (isEmpty()) append("details hidden by Android")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        tower.dbm?.let { "$it dBm" } ?: "—",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                    )
                    if (tower.registered) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            tr("in use"),
                            style = MaterialTheme.typography.labelSmall,
                            color = GoodGreen,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                tr(
                    "Android only reveals tower identity to apps that have Location allowed, and " +
                        "some carriers hide it completely. A dual-SIM phone shows the towers of the " +
                        "SIM that is carrying data."
                ),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun PingCard(state: UiState, vm: MainViewModel) {
    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Speed, contentDescription = null, tint = AccentCyan)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        tr("Ping & jitter"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        tr(
                            "Latency (ping) is how long a round trip takes; jitter is how much it " +
                                "wobbles. High jitter makes video calls stutter even when the ping " +
                                "looks fine."
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            GradientButton(
                text = if (state.pingBusy) {
                    tr("Measuring…") + " " + (state.pingProgress?.let { "${it.first}/${it.second}" } ?: "")
                } else {
                    tr("Ping now")
                },
                icon = Icons.Outlined.Speed,
                onClick = { vm.runPing() },
                enabled = !state.pingBusy,
                modifier = Modifier.fillMaxWidth(),
            )
            state.pingStats.forEach { stats ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stats.label,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stats.host,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                    }
                    // v2.6: the user asked where the jitter is. It was there, but as an
                    // unlabelled "±4 ms" next to the ping — now both are named, so nobody
                    // has to guess which number is which.
                    Column(horizontalAlignment = Alignment.End) {
                        if (stats.received == 0) {
                            Text(
                                tr("no answer"),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = DangerRed,
                            )
                        } else {
                            val tone = when {
                                stats.avgMs < 60 && stats.jitterMs < 20 -> GoodGreen
                                stats.avgMs < 150 -> WarnAmber
                                else -> DangerRed
                            }
                            Text(
                                tr("Ping") + " " + "%.0f ms".format(stats.avgMs),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = tone,
                            )
                            Text(
                                tr("Jitter") + " ±" + "%.0f ms".format(stats.jitterMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                        }
                    }
                }
                Text(
                    "min %.0f • avg %.0f • max %.0f ms • loss %d%%".format(
                        stats.minMs, stats.avgMs, stats.maxMs, stats.lossPercent,
                    ) + " • " + stats.quality,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
            Spacer(Modifier.height(8.dp))
            PublicIpRow(state, vm)
        }
    }
}

@Composable
private fun PublicIpRow(state: UiState, vm: MainViewModel) {
    val info = state.ipInfo
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                tr("Public IP & ISP"),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                when {
                    state.ipBusy -> tr("Asking the network…")
                    info == null -> tr("Tap to see the IP and operator the internet sees (one small request).")
                    info.ip == null -> info.error ?: tr("No answer")
                    else -> buildString {
                        append(info.ip)
                        info.isp?.let { append(" • $it") }
                        info.city?.let { append(" • $it") }
                        info.asn?.let { append(" • AS$it") }
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }
        TextButton(onClick = { vm.loadPublicIp() }, enabled = !state.ipBusy) {
            Text(tr("Check"), color = AccentCyan, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SpeedTestCard(state: UiState, vm: MainViewModel) {
    var showSizes by remember { mutableStateOf(true) }
    val bytes = state.speedSizeMb.toLong() * 1024L * 1024L
    val onMobile = SpeedMeter.onMobileData(androidx.compose.ui.platform.LocalContext.current)
    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                tr("Speed test"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                tr(
                    "This downloads real data and measures how fast it arrived. Small sizes are " +
                        "kind to a metered plan; on an unlimited 5G plan pick the biggest, because " +
                        "the link needs a few seconds before it reaches full speed."
                ),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(10.dp))

            if (showSizes && !state.speedBusy) {
                Text(
                    if (state.speedSizeMb > 0) tr("Test size") + " — " + formatBytesSafe(bytes)
                    else tr("Test size — tap the size you want"),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SpeedMeter.SIZES_MB.take(4).forEach { mb ->
                        Chip(
                            label = "${mb}MB",
                            selected = state.speedSizeMb == mb,
                            onClick = { vm.setSpeedSize(mb) },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SpeedMeter.SIZES_MB.drop(4).forEach { mb ->
                        Chip(
                            label = "${mb}MB",
                            selected = state.speedSizeMb == mb,
                            onClick = { vm.setSpeedSize(mb) },
                        )
                    }
                    Chip(
                        label = tr("Unlimited 5G"),
                        selected = state.speedSizeMb == 100,
                        onClick = { vm.setSpeedSize(100) },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            tr("Also test upload"),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            tr("Sends the same amount again — off by default to save data."),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                    }
                    Switch(
                        checked = state.speedIncludeUpload,
                        onCheckedChange = { vm.setSpeedIncludeUpload(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF03202B),
                            checkedTrackColor = AccentCyan,
                        ),
                    )
                }
                Spacer(Modifier.height(6.dp))
                if (onMobile) {
                    Text(
                        "⚠ " + tr("You are on mobile data — this test downloads about") + " " +
                            formatBytesSafe(bytes) +
                            (if (state.speedIncludeUpload) " " + tr("twice (download + upload)") else ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = WarnAmber,
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }

            if (state.speedBusy) {
                val progress = state.speedProgress
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (speedPhaseLabel(progress?.phase) ?: tr("Testing…")) +
                            " " + (progress?.percent?.let { "$it%" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.cancelSpeedTest() }) {
                        Text(tr("Stop"), color = DangerRed, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(6.dp))
                SpeedBar(
                    value = (progress?.percent ?: 0) / 100f,
                    color = AccentCyan,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    (progress?.mbps?.let { "%.1f Mbps".format(it) } ?: "—") +
                        " • " + formatBytesSafe(progress?.doneBytes ?: 0L) +
                        " / " + formatBytesSafe(progress?.totalBytes ?: bytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                )
            } else {
                val chosen = state.speedSizeMb > 0
                GradientButton(
                    text = if (chosen) {
                        tr("Start test") + " • " + formatBytesSafe(bytes)
                    } else {
                        tr("Pick a test size first")
                    },
                    icon = Icons.Outlined.Speed,
                    onClick = { vm.startSpeedTest() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = chosen,
                )
                if (!chosen) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tr("Nothing runs until you pick a size — the test costs that much data."),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
            }

            state.speedResult?.let { result ->
                Spacer(Modifier.height(10.dp))
                Text(
                    if (result.ok) {
                        tr("Download") + ": %.1f Mbps".format(result.mbps)
                    } else {
                        tr("The test failed") + ": " + (result.error ?: "")
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (result.ok) GoodGreen else DangerRed,
                )
                if (result.ok) {
                    Text(
                        formatBytesSafe(result.bytes) + " in " + "%.1f s".format(result.seconds) +
                            " • " + "%.2f MB/s".format(result.mbps / 8.0),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
            }
            state.speedUploadResult?.let { result ->
                Spacer(Modifier.height(6.dp))
                Text(
                    if (result.ok) {
                        tr("Upload") + ": %.1f Mbps".format(result.mbps)
                    } else {
                        tr("Upload test failed") + ": " + (result.error ?: "")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (result.ok) GoodGreen else DangerRed,
                )
            }
            state.speedError?.takeIf { state.speedResult == null }?.let { error ->
                Spacer(Modifier.height(6.dp))
                Text(error, style = MaterialTheme.typography.labelSmall, color = DangerRed)
            }
        }
    }
}

@Composable
private fun AppTrackerCard(state: UiState, vm: MainViewModel) {
    val report = state.appTrackerReport
    val context = androidx.compose.ui.platform.LocalContext.current
    val vpnLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            vm.startTrackerVpn()
        } else {
            vm.setTrackerVpnMode(false)
        }
    }

    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.SignalCellularAlt, contentDescription = null, tint = AccentCyan)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        tr("Live App Connections & Trackers"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        tr("Inspect active destinations & track telemetry"),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
                IconButton(onClick = { vm.scanAppTrackers() }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Inspect", tint = TextSecondary)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                tr(
                    "Inspects on-device socket connections and attributes them to apps. " +
                        "Choose between standard passive inspection or local on-device VPN loopback for deep packet inspection."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(10.dp))

            // Dual Mode Switches: Passive vs Local VPN
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(SurfaceHigh, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        tr("Local On-Device VPN Mode"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (state.appTrackerVpnActive) tr("VPN Active (zero external data)")
                        else tr("Intercepts live outbound sockets & DNS queries"),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.appTrackerVpnActive) GoodGreen else TextSecondary,
                    )
                }
                Switch(
                    checked = state.appTrackerVpnMode,
                    onCheckedChange = { enable ->
                        if (enable) {
                            val vpnIntent = android.net.VpnService.prepare(context)
                            if (vpnIntent != null) {
                                vpnLauncher.launch(vpnIntent)
                            } else {
                                vm.setTrackerVpnMode(true)
                            }
                        } else {
                            vm.setTrackerVpnMode(false)
                        }
                    },
                )
            }

            Spacer(Modifier.height(10.dp))
            GradientButton(
                text = if (state.appTrackerBusy) tr("Inspecting connections…") else tr("Inspect App Trackers"),
                icon = Icons.Outlined.Refresh,
                onClick = { vm.scanAppTrackers() },
                enabled = !state.appTrackerBusy,
                modifier = Modifier.fillMaxWidth(),
            )

            if (report != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${report.connections.size} connection(s)",
                            style = MaterialTheme.typography.labelMedium,
                            color = AccentCyan,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (report.trackerCount > 0) {
                            Text(
                                "• ${report.trackerCount} tracker(s)",
                                style = MaterialTheme.typography.labelMedium,
                                color = DangerRed,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Text(
                        report.mode,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (report.connections.isEmpty()) {
                    Text(
                        tr("No active outward connections at this exact moment."),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        report.connections.take(16).forEach { conn ->
                            TrackerItemCard(conn)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackerItemCard(conn: com.universalrp.cleansweep.data.AppNetworkTracker.ActiveConnection) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = SurfaceHigh,
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conn.appName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    conn.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (conn.isTracker) DangerRed else AccentCyan,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "Company / Network: ${conn.orgOrCompany}",
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
            )
            Text(
                "Destination: ${conn.destinationHost ?: conn.remoteIp}:${conn.remotePort}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun DataUsageCard(state: UiState, vm: MainViewModel) {
    val usage = state.usage
    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        tr("Data usage"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        tr("Today") + " (since midnight)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
                IconButton(onClick = { vm.loadUsage() }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = TextSecondary)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (usage == null) {
                Text(if (state.usageBusy) tr("Reading…") else tr("Tap refresh"), color = TextSecondary)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    UsageTile(
                        label = tr("Mobile data"),
                        value = usage.todayMobileBytes?.let { formatBytesSafe(it) } ?: "—",
                        color = AccentViolet,
                        modifier = Modifier.weight(1f),
                    )
                    UsageTile(
                        label = tr("Wi-Fi"),
                        value = usage.todayWifiBytes?.let { formatBytesSafe(it) } ?: "—",
                        color = AccentCyan,
                        modifier = Modifier.weight(1f),
                    )
                }
                usage.todayTotalBytes?.let { total ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        tr("Total today") + ": " + formatBytesSafe(total),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    useSinceBootLine(usage) ,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
                if (!usage.hasUsageAccess) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Info, contentDescription = null, tint = WarnAmber)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            tr("Usage access gives today's exact figures"),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = { vm.openUsageAccess() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WarnAmber,
                            contentColor = Color(0xFF2B1D02),
                        ),
                    ) {
                        Text(tr("Grant usage access"), fontWeight = FontWeight.Bold)
                    }
                }
                if (usage.todayAppMobile.isNotEmpty() || usage.todayAppWifi.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        tr("Apps using the most data today"),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val apps = (usage.todayAppMobile + usage.todayAppWifi)
                        .groupBy { it.pkg }
                        .map { (_, list) -> DataAppUse(list.first().label, list.first().pkg, list.sumOf { it.bytes }) }
                        .sortedByDescending { it.bytes }
                        .take(6)
                    apps.forEach { app ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(
                                app.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                formatBytesSafe(app.bytes),
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentCyan,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    usage.note,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
        }
    }
}

private fun useSinceBootLine(usage: com.universalrp.cleansweep.data.DataUsageReport): String =
    tr("Since the phone was switched on") + ": " +
        tr("received") + " " + formatBytesSafe(usage.sinceBootRxBytes) + ", " +
        tr("sent") + " " + formatBytesSafe(usage.sinceBootTxBytes) + " (mobile " +
        formatBytesSafe(usage.sinceBootMobileBytes) + ")"

@Composable
private fun UsageTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceHigh)
            .padding(12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

/** Byte sizes in this file never crash on a null or a huge number. */
private fun formatBytesSafe(bytes: Long): String =
    if (bytes <= 0L) "0 MB" else formatBytesCompat(bytes)

private fun formatBytesCompat(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> String.format(Locale.getDefault(), "%.2f GB", gb)
        mb >= 1 -> String.format(Locale.getDefault(), "%.1f MB", mb)
        else -> String.format(Locale.getDefault(), "%.0f KB", kb)
    }
}

/** Small progress bar used by the speed test. */
@Composable
private fun SpeedBar(value: Float, color: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(SurfaceHigh)
    ) {
        Box(
            Modifier
                .fillMaxWidth(value.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
    }
}

/** "Downloading" / "Uploading" / "Starting" in the chosen language. */
private fun speedPhaseLabel(phase: String?): String? = when (phase) {
    null -> null
    "Downloading" -> tr("Downloading")
    "Uploading" -> tr("Uploading")
    "Starting" -> tr("Starting the test")
    "Starting upload" -> tr("Starting the upload")
    else -> phase
}
