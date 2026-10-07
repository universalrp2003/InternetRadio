package com.universalrp.cleansweep.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.universalrp.cleansweep.MainViewModel
import com.universalrp.cleansweep.Screen
import com.universalrp.cleansweep.UiState
import com.universalrp.cleansweep.data.AppRow
import com.universalrp.cleansweep.data.Finding
import com.universalrp.cleansweep.data.LanDevice
import com.universalrp.cleansweep.data.Severity
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ================================================================= installed apps */

@Composable
fun AppsScreen(state: UiState, vm: MainViewModel) {
    var detail by remember { mutableStateOf<AppRow?>(null) }

    LaunchedEffect(Unit) {
        if (state.appInventory == null) vm.loadAppInventory()
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.goBack() }) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Home", tint = TextSecondary)
            }
            Text(tr("Installed apps"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { vm.loadAppInventory() }) {
                Icon(Icons.Outlined.Sync, contentDescription = "Reload", tint = TextSecondary)
            }
        }

        val report = state.appInventory
        val rows = vm.filteredApps()

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.appQuery,
                    onValueChange = { vm.setAppQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, contentDescription = null, tint = TextSecondary)
                    },
                    trailingIcon = {
                        if (state.appQuery.isNotEmpty()) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "Clear",
                                tint = TextSecondary,
                                modifier = Modifier.clickable { vm.setAppQuery("") },
                            )
                        }
                    },
                    placeholder = { Text(tr("Search an app or package")) },
                )
            }

            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    vm.appFilters().forEach { (id, label) ->
                        Chip(
                            label = label,
                            selected = state.appFilter == id,
                            onClick = { vm.setAppFilter(id) },
                        )
                    }
                }
            }

            report?.let { loaded ->
                item {
                    PanelCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "${loaded.rows.size} apps • ${loaded.totalBytes.formatBytes()}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${loaded.stubCount} known preinstall stubs • ${loaded.unusedCount} unused " +
                                    "30+ days • ${loaded.sideloadedCount} not from a store • " +
                                    "${loaded.riskyCount} with sensitive permissions",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                            if (!loaded.usageAccessGranted) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Grant \"Usage access\" (the App cache screen does it) to see sizes " +
                                        "and last-used dates — without it Android hides them.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = WarnAmber,
                                )
                            }
                        }
                    }
                }
            }

            if (state.appInventoryBusy) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentCyan)
                    }
                }
            }

            if (report != null && rows.isEmpty() && !state.appInventoryBusy) {
                item {
                    Text(tr("Nothing matches that filter."),
                        Modifier.fillMaxWidth().padding(24.dp),
                        color = TextSecondary,
                    )
                }
            }

            items(rows, key = { it.pkg }) { row ->
                AppRowCard(row) { detail = row }
            }

            item {
                Text(
                    "Tags are facts from Android, not opinions: \"Preinstalled\" means the phone shipped " +
                        "with it, \"Unused 30+ days\" comes from Android's own usage records, " +
                        "\"Sideloaded\" means no app store installed it. Use \"Analyse my phone\" on the " +
                        "Phone health screen for advice on what is safe to remove.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
        }
    }

    detail?.let { row ->
        AppDetailDialog(row, vm) { detail = null }
    }
}

@Composable
private fun AppRowCard(row: AppRow, onClick: () -> Unit) {
    PanelCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    row.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    row.pkg,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.tags.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        row.tags.forEach { tag -> TagPill(tag) }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                ByteChip(row.totalBytes)
                Spacer(Modifier.height(6.dp))
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TextSecondary)
            }
        }
    }
}

@Composable
private fun TagPill(text: String) {
    val colour = when {
        text.contains("Bloat") -> DangerRed
        text.contains("Sideloaded") -> WarnAmber
        text.contains("Sensitive") -> WarnAmber
        text.contains("Unused") -> AccentViolet
        else -> AccentCyan
    }
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceHigh)
            .border(1.dp, colour.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall,
        color = colour,
    )
}

@Composable
private fun AppDetailDialog(row: AppRow, vm: MainViewModel, onClose: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(row.label, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                InfoRow("Package", row.pkg)
                InfoRow("Size", row.totalBytes.formatBytes() +
                    " (data ${row.dataBytes.formatBytes()}, cache ${row.cacheBytes.formatBytes()})")
                InfoRow("Installed", dateFormat.format(Date(row.firstInstallMs)))
                InfoRow("Updated", if (row.updatedMs > 0) dateFormat.format(Date(row.updatedMs)) else "never")
                InfoRow(
                    "Last used",
                    row.lastUsedMs?.let { dateFormat.format(Date(it)) } ?: "no record in 30 days",
                )
                InfoRow("Installer", row.installer ?: "not from a store / unknown")
                InfoRow("Type", if (row.isSystem) "System app (preinstalled)" else "Installed by you")
                if (row.riskyPermissions.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(tr("Permissions worth knowing about"),
                        style = MaterialTheme.typography.labelLarge,
                        color = WarnAmber,
                    )
                    row.riskyPermissions.forEach { permission ->
                        Text("• $permission", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
                if (row.isKnownBloatStub) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This is one of the well-known Facebook preinstall stubs that vendors bundle. " +
                            "Disabling it stops it running without breaking your phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DangerRed,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "CleanSweep never force-stops or uninstalls anything by itself — Android asks " +
                        "you to confirm on the next screen.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    vm.openAppInfo(row.pkg)
                    onClose()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentCyan,
                    contentColor = Color(0xFF03202B),
                ),
            ) {
                Text(if (row.isSystem) "App settings" else "Uninstall", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text(tr("Close"), color = TextSecondary) }
        },
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
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

/* ===================================================================== security */

@Composable
fun SecurityScreen(state: UiState, vm: MainViewModel) {
    LaunchedEffect(Unit) {
        if (state.securityReport == null) vm.loadSecurity()
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.goBack() }) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Home", tint = TextSecondary)
            }
            Text(tr("Security check"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { vm.loadSecurity() }) {
                Icon(Icons.Outlined.Sync, contentDescription = "Rescan", tint = TextSecondary)
            }
        }

        val report = state.securityReport
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.securityBusy) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentCyan)
                    }
                }
            }

            report?.let { loaded ->
                item {
                    PanelCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.Security,
                                    contentDescription = null,
                                    tint = scoreColor(loaded.score),
                                    modifier = Modifier.size(30.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${loaded.score}/100",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = scoreColor(loaded.score),
                                    )
                                    Text(
                                        loaded.verdict,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "${loaded.findings.size} findings over ${loaded.appsChecked} apps • " +
                                    "${loaded.highCount} high, ${loaded.mediumCount} medium",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                    }
                }

                items(loaded.findings, key = { it.id }) { finding ->
                    FindingCard(finding)
                }

                item {
                    PanelCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.SmartToy, contentDescription = null, tint = AccentViolet)
                                Spacer(Modifier.width(10.dp))
                                Text(tr("Want a second opinion?"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "The AI analysis reads these findings and explains which one matters " +
                                    "most on your phone.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                            Spacer(Modifier.height(10.dp))
                            GradientButton(
                                text = if (state.aiConfig.ready) "Analyse with AI" else "Set up AI analysis",
                                icon = Icons.Outlined.SmartToy,
                                onClick = {
                                    if (state.aiConfig.ready) vm.runAiAnalysis()
                                    else vm.navigate(Screen.AI_SETTINGS)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FindingCard(finding: Finding) {
    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (finding.severity) {
                        Severity.HIGH -> Icons.Outlined.ErrorOutline
                        Severity.MEDIUM -> Icons.Outlined.Lock
                        Severity.LOW -> Icons.Outlined.BugReport
                        Severity.INFO -> Icons.Outlined.Info
                    },
                    contentDescription = null,
                    tint = severityColor(finding.severity),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    finding.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    finding.severity.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = severityColor(finding.severity),
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                finding.detail,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            if (finding.samples.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                finding.samples.take(8).forEach { sample ->
                    Text("• $sample", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                }
            }
            finding.fixHint?.let { hint ->
                Spacer(Modifier.height(8.dp))
                Text(
                    hint,
                    style = MaterialTheme.typography.labelMedium,
                    color = AccentCyan,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun severityColor(severity: Severity): Color = when (severity) {
    Severity.HIGH -> DangerRed
    Severity.MEDIUM -> WarnAmber
    Severity.LOW -> AccentCyan
    Severity.INFO -> TextSecondary
}

private fun scoreColor(score: Int): Color = when {
    score >= 90 -> GoodGreen
    score >= 75 -> AccentCyan
    score >= 55 -> WarnAmber
    else -> DangerRed
}

/* ====================================================================== network */

@Composable
fun NetworkScreen(state: UiState, vm: MainViewModel) {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // The old build only refreshed the flags here, so the card stayed on screen and the
        // button looked dead. Now the details are read again the moment the dialog closes.
        vm.onWifiPermissionResult()
    }

    LaunchedEffect(Unit) {
        if (state.networkReport == null) vm.refreshNetworkDetails()
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.goBack() }) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Home", tint = TextSecondary)
            }
            Text(tr("Wi-Fi & network"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { vm.refreshNetworkDetails() }) {
                Icon(Icons.Outlined.Sync, contentDescription = "Refresh", tint = TextSecondary)
            }
        }

        val report = state.networkReport
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Only ask while there is really nothing to show: once the phone reports the
            // Wi-Fi name / router address, the card goes away for good.
            val canReadName = report?.wifi?.ssid != null || report?.wifi?.gateway != null
            val connectedToWifi = report?.wifi?.connected == true
            val needsPermissionCard = connectedToWifi && !state.wifiPermission && !canReadName
            if (needsPermissionCard) {
                item {
                    NetworkPermissionCard(
                        granted = false,
                        onRequest = { permissionLauncher.launch(vm.networkPermissions()) },
                        onOpenSettings = { vm.requestNetworkPermission() },
                    )
                }
            }

            report?.let { loaded ->
                item {
                    PanelCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Wifi, contentDescription = null, tint = AccentCyan)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        // v2.6, at the user's request: CleanSweep does not
                                        // print the Wi-Fi network name at all. The rest of the
                                        // reading — signal, band, router, DNS — is unchanged.
                                        if (loaded.wifi.connected) tr("Wi-Fi connected") else loaded.wifi.transport,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        loaded.wifi.transport +
                                            (loaded.wifi.band?.let { " • $it" } ?: "") +
                                            (loaded.wifi.linkSpeedMbps?.let { " • $it Mbps" } ?: ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                    )
                                }
                                loaded.wifi.signalPercent?.let { signal ->
                                    Text(
                                        "$signal%",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (signal > 60) GoodGreen else WarnAmber,
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            InfoRow("This phone", loaded.wifi.subnetLabel ?: "unknown")
                            InfoRow("Gateway (router)", loaded.wifi.gateway ?: "unknown")
                            InfoRow("DNS", loaded.wifi.dns.joinToString(", ").ifBlank { "unknown" })
                            loaded.wifi.rssiDbm?.let { InfoRow("Signal", "$it dBm") }
                            loaded.wifi.bssid?.let { InfoRow("Access point", it) }
                            loaded.wifi.macAddress?.let { InfoRow("Wi-Fi MAC", it) }
                        }
                    }
                }

                item {
                    PanelCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Devices, contentDescription = null, tint = AccentViolet)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${loaded.devices.size} devices answered",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        "Probed ${loaded.probedHosts} addresses on your network",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            GradientButton(
                                text = if (state.networkBusy) "Scanning…" else "Scan my network",
                                icon = Icons.Outlined.Router,
                                onClick = { vm.scanNetwork() },
                                enabled = !state.networkBusy,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            state.networkProgress?.let { (done, total) ->
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Checked $done of $total addresses…",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            if (loaded.devices.size > 1) {
                                GradientButton(
                                    text = when {
                                        state.aiDeviceBusy -> "Identifying…"
                                        state.aiConfig.ready -> "Identify with AI"
                                        else -> "Set up AI to identify"
                                    },
                                    icon = Icons.Outlined.SmartToy,
                                    onClick = {
                                        // With no provider this explains itself instead of
                                        // being a button that does nothing.
                                        if (state.aiConfig.ready) {
                                            vm.identifyDevicesWithAi()
                                        } else {
                                            vm.openAiSettings()
                                        }
                                    },
                                    enabled = !state.aiDeviceBusy,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    if (state.aiConfig.ready) {
                                        "The AI sees the same table (ports, names, MAC vendors) and " +
                                            "says what each device probably is, with its confidence."
                                    } else {
                                        "CleanSweep's own guess is on each card below. Add a free AI " +
                                            "key to get a second opinion with reasons."
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                )
                                state.aiDeviceResult?.let { result ->
                                    Spacer(Modifier.height(8.dp))
                                    AiText(result, style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            Text(
                                loaded.note,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                        }
                    }
                }

                if (loaded.devices.isNotEmpty()) {
                    items(loaded.devices, key = { it.ip }) { device -> DeviceCard(device) }
                }
            }

            if (report == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentCyan)
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkPermissionCard(
    granted: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = WarnAmber)
                Spacer(Modifier.width(10.dp))
                Text(tr("Permission needed for Wi-Fi details"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Android hides your Wi-Fi name, the router address and the access point unless the " +
                    "app has Location or Nearby-devices permission. CleanSweep uses it only while this " +
                    "screen is open — nothing about your network is stored or sent anywhere.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRequest,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentCyan,
                        contentColor = Color(0xFF03202B),
                    ),
                ) {
                    Text(
                        if (granted) "Allowed — tap to rescan" else "Allow and rescan",
                        fontWeight = FontWeight.Bold,
                    )
                }
                TextButton(onClick = onOpenSettings) {
                    Text(tr("App settings"), color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(device: LanDevice) {
    PanelCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                when {
                    device.isSelf -> Icons.Outlined.Devices
                    device.isGateway -> Icons.Outlined.Router
                    device.kind == "Computer" -> Icons.Outlined.Computer
                    else -> Icons.Outlined.Wifi
                },
                contentDescription = null,
                tint = if (device.isSelf || device.isGateway) AccentCyan else TextSecondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    device.ip + (device.hostname?.let { " • $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(device.identity.type)
                        append(" • ").append(device.identity.confidence)
                        append(" (").append(device.identity.evidence).append(")")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
                device.vendor?.let { vendor ->
                    Text(
                        "Maker: $vendor",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
                device.mac?.let {
                    Text("MAC $it", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
            if (device.openPorts.isNotEmpty()) {
                Text(
                    "${device.openPorts.size} port(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentViolet,
                )
            }
        }
    }
}

/* ================================================================ shared chip */

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) AccentCyan.copy(alpha = 0.22f) else SurfaceHigh)
            .border(
                1.dp,
                if (selected) AccentCyan else OutlineC,
                RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) AccentCyan else TextPrimary,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
    )
}

@Composable
fun CheckLine(text: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
            contentDescription = null,
            tint = if (ok) GoodGreen else WarnAmber,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}
