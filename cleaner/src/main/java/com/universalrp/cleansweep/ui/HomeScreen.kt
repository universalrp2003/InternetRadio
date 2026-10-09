package com.universalrp.cleansweep.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.universalrp.cleansweep.MainViewModel
import com.universalrp.cleansweep.Screen
import com.universalrp.cleansweep.UiState
import com.universalrp.cleansweep.data.formatBytes
import com.universalrp.cleansweep.data.formatUptime
import com.universalrp.cleansweep.data.JunkKind
import com.universalrp.cleansweep.data.AppLang
import com.universalrp.cleansweep.data.Lang
import com.universalrp.cleansweep.data.tr
import com.universalrp.cleansweep.ui.theme.AccentCyan
import com.universalrp.cleansweep.ui.theme.DangerRed
import com.universalrp.cleansweep.ui.theme.AccentViolet
import com.universalrp.cleansweep.ui.theme.GoodGreen
import com.universalrp.cleansweep.ui.theme.OutlineC
import com.universalrp.cleansweep.ui.theme.SurfaceHigh
import com.universalrp.cleansweep.ui.theme.TextPrimary
import com.universalrp.cleansweep.ui.theme.TextSecondary
import com.universalrp.cleansweep.ui.theme.WarnAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(state: UiState, vm: MainViewModel, listState: LazyListState) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Top bar - neat and compact header, prevents wrapping on small screens
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(AccentCyan, AccentViolet))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.CleaningServices,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    tr(Lang.appName()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            // One tap between தமிழ் and English, right where the eye already is. The full
            // choice (and the note about the launcher name) stays in Settings → Language.
            TextButton(
                onClick = { vm.setLanguage(if (state.lang == AppLang.TA) AppLang.EN else AppLang.TA) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    if (state.lang == AppLang.TA) "EN" else "த",
                    color = AccentCyan,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            IconButton(
                onClick = { vm.navigate(Screen.VOICE) },
                modifier = Modifier.size(38.dp),
            ) {
                Icon(
                    if (state.voiceOn) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff,
                    contentDescription = "Voice",
                    tint = if (state.voiceOn) AccentCyan else TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(
                onClick = { vm.navigate(Screen.SETTINGS) },
                modifier = Modifier.size(38.dp),
            ) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(
                onClick = { vm.navigate(Screen.ABOUT) },
                modifier = Modifier.size(38.dp),
            ) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = "About",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        LedBar(Modifier.fillMaxWidth().padding(horizontal = 16.dp))

        if (state.tamilInfoVisible && state.tamilInfoItems.isNotEmpty()) {
            TamilInfoStrip(
                items = state.tamilInfoItems,
                isPaused = state.tamilInfoPaused,
                onTogglePause = { vm.toggleTamilInfoPause() },
                onHide = { vm.toggleTamilInfoVisibility() },
                onRefresh = { vm.loadTamilInfoStrip(force = true) },
                onSpeak = { text -> vm.speakTamilText(text) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Every section is keyed, so the restored scroll position still points at the
            // same card when a conditional card (setup prompt, health, last clean) appears
            // or disappears between visits.
            if (!state.hasAllFilesAccess) {
                item(key = "setup") { SetupCard(vm) }
            }

            item(key = "storage") { StorageCard(state) }

            item(key = "mobile_data_window") { HomeMobileDataCard(state, vm) }

            item(key = "scanbutton") {
                GradientButton(
                    text = if (state.report != null) "Scan again" else "Scan & clean junk",
                    icon = Icons.Outlined.AutoFixHigh,
                    onClick = { vm.startScan() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.hasAllFilesAccess && state.legacyStorageOk,
                )
            }

            if (!state.legacyStorageOk) {
                item(key = "legacy") { LegacyStorageCard(vm) }
            }

            state.health?.let { health ->
                item(key = "health") {
                    PanelCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.BatteryFull,
                                    contentDescription = null,
                                    tint = GoodGreen,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Battery ${health.battery.percent}% • " +
                                            (health.battery.temperatureC?.let { "%.1f °C".format(it) }
                                                ?: "temp not reported"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        buildString {
                                            append(health.battery.statusLabel)
                                            health.battery.powerW?.let { append(" • ${"%.1f".format(it)} W") }
                                            health.cpuTempC?.let { append(" • CPU ${"%.0f".format(it)} °C") }
                                            append(" • uptime ${formatUptime(health.device.uptimeMs)}")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                    )
                                }
                                Text(tr("LIVE"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GoodGreen,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "assistant") { AssistantCard(state, vm) }

            item(key = "quick") {
                SectionTitle(tr("Phone health, security & network"))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickCard(
                            icon = Icons.Outlined.BatteryFull,
                            title = tr("Phone health"),
                            subtitle = tr("Battery, heat, CPU, watts"),
                            onClick = { vm.navigate(Screen.HEALTH) },
                            modifier = Modifier.weight(1f),
                        )
                        QuickCard(
                            icon = Icons.Outlined.Security,
                            title = tr("Security check"),
                            subtitle = tr("Permissions, risky apps"),
                            onClick = { vm.loadSecurity() },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickCard(
                            icon = Icons.Outlined.SignalCellularAlt,
                            title = tr("Mobile & data"),
                            subtitle = tr("Signal, speed test, data used"),
                            onClick = { vm.navigate(Screen.MOBILE); vm.loadMobile() },
                            modifier = Modifier.weight(1f),
                        )
                        QuickCard(
                            icon = Icons.Outlined.Wifi,
                            title = tr("Wi-Fi devices"),
                            subtitle = tr("Who is on your network"),
                            onClick = {
                                vm.navigate(Screen.NETWORK)
                                vm.refreshNetworkDetails()
                            },
                            modifier = Modifier.weight(1f),
                        )
                        QuickCard(
                            icon = Icons.Outlined.Apps,
                            title = tr("Installed apps"),
                            subtitle = tr("Bloatware, unused apps"),
                            onClick = { vm.loadAppInventory() },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    QuickCard(
                        icon = Icons.Outlined.SmartToy,
                        title = if (state.aiConfig.ready) "AI phone analysis" else "Set up AI analysis",
                        subtitle = if (state.aiConfig.ready) {
                            "Explain everything in plain words"
                        } else {
                            "The free option needs no signup"
                        },
                        onClick = {
                            if (state.aiConfig.ready) vm.runAiAnalysis() else vm.navigate(Screen.AI_SETTINGS)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item(key = "tiles") {
                SectionTitle(tr("Quick cleaning actions"))
                Text(tr("Each tile scans for its own kind of junk only — then you tick what to clean."),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickCard(
                            icon = Icons.Outlined.DeleteSweep,
                            title = tr("Temp & junk"),
                            subtitle = tr("Only .tmp, .log, leftovers"),
                            onClick = { vm.startScan(setOf(JunkKind.RESIDUAL), JunkKind.RESIDUAL.label) },
                            modifier = Modifier.weight(1f),
                        )
                        QuickCard(
                            icon = Icons.Outlined.Photo,
                            title = tr("Thumbnails"),
                            subtitle = tr("Only image cache files"),
                            onClick = { vm.startScan(setOf(JunkKind.THUMBNAILS), JunkKind.THUMBNAILS.label) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickCard(
                            icon = Icons.Outlined.Android,
                            title = tr("APK files"),
                            subtitle = tr("Only installer packages"),
                            onClick = { vm.startScan(setOf(JunkKind.APK_FILES), JunkKind.APK_FILES.label) },
                            modifier = Modifier.weight(1f),
                        )
                        QuickCard(
                            icon = Icons.Outlined.ContentCopy,
                            title = tr("Duplicates"),
                            subtitle = tr("Only identical files"),
                            onClick = { vm.startScan(setOf(JunkKind.DUPLICATES), JunkKind.DUPLICATES.label) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickCard(
                            icon = Icons.Outlined.Folder,
                            title = tr("Empty folders"),
                            subtitle = tr("Only empty folders"),
                            onClick = { vm.startScan(setOf(JunkKind.EMPTY_FOLDERS), JunkKind.EMPTY_FOLDERS.label) },
                            modifier = Modifier.weight(1f),
                        )
                        QuickCard(
                            icon = Icons.Outlined.FileDownload,
                            title = tr("Old downloads"),
                            subtitle = tr("Only old Download files"),
                            onClick = { vm.startScan(setOf(JunkKind.OLD_DOWNLOADS), JunkKind.OLD_DOWNLOADS.label) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickCard(
                            icon = Icons.Outlined.SdStorage,
                            title = tr("Large files"),
                            subtitle = tr("Only files over the limit"),
                            onClick = { vm.startScan(setOf(JunkKind.LARGE_FILES), JunkKind.LARGE_FILES.label) },
                            modifier = Modifier.weight(1f),
                        )
                        QuickCard(
                            icon = Icons.Outlined.Android,
                            title = tr("App cache"),
                            subtitle = tr("Per-app caches, cached"),
                            onClick = { vm.navigate(Screen.APP_CACHE) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** Entry point for the on-device assistant (v1.3) with quick question input directly on Home. */
@Composable
private fun AssistantCard(state: UiState, vm: MainViewModel) {
    val totalCache = state.appCaches.sumOf { if (it.cacheBytes > 0L) it.cacheBytes else 0L }
    val online = state.assistantOnline && state.aiConfig.ready
    val badge = if (online) "ONLINE AI" else "ON-DEVICE"
    val badgeColor = if (online) GoodGreen else AccentCyan
    var quickQuestion by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }

    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { vm.openAssistant() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(AccentCyan, AccentViolet))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.SmartToy,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            tr("Ask the assistant"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF04202A),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(badgeColor)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Text(
                        if (online) state.aiEngineLabel else tr("Free space, cache help — answers on this phone"),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1,
                    )
                }
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TextSecondary)
            }

            Spacer(Modifier.height(10.dp))

            // Direct question bar on Home screen
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = quickQuestion,
                    onValueChange = { quickQuestion = it },
                    placeholder = { Text(tr("Ask any question…"), style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val q = quickQuestion.trim()
                        if (q.isNotBlank()) {
                            vm.askAssistant(q)
                            vm.openAssistant()
                        } else {
                            vm.openAssistant()
                        }
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentCyan),
                ) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = "Ask",
                        tint = Color(0xFF04202A),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupCard(vm: MainViewModel) {
    PanelCard(
        Modifier
            .fillMaxWidth()
            .border(1.dp, WarnAmber.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Warning, contentDescription = null, tint = WarnAmber)
                Spacer(Modifier.width(10.dp))
                Text(tr("Storage access needed"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "CleanSweep needs “All files access” to scan for junk, duplicates and empty folders. " +
                    "On the next screen, find CleanSweep and switch it ON.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
            GradientButton(
                text = tr("Allow storage access"),
                icon = Icons.Outlined.Settings,
                onClick = { vm.requestAllFilesAccess() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StorageCard(state: UiState) {
    val storage = state.storage
    val freeRam = state.health?.device?.availableRamBytes
    PanelCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                StorageGauge(
                    usedFraction = storage?.usedFraction ?: 0f,
                    modifier = Modifier.size(150.dp),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        storage?.free?.formatBytes() ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "free storage",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                    if (freeRam != null && freeRam > 0L) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "${freeRam.formatBytes()} RAM",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = GoodGreen,
                        )
                    }
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                StorageRow("Total", storage?.total?.formatBytes() ?: "—")
                Spacer(Modifier.height(8.dp))
                StorageRow("Used", storage?.used?.formatBytes() ?: "—")
                Spacer(Modifier.height(8.dp))
                StorageRow("Free", storage?.free?.formatBytes() ?: "—")
                Spacer(Modifier.height(12.dp))
                val pct = ((storage?.usedFraction ?: 0f) * 100).toInt()
                Text(
                    "$pct% of storage used",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (pct > 90) WarnAmber else TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                val isTa = state.lang == AppLang.TA
                val phoneAge = remember { com.universalrp.cleansweep.data.PhoneAgeEstimator.estimateAge() }
                Text(
                    (if (isTa) "போன் வயது: " else "Est. phone age: ") + phoneAge.displayString(isTa),
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentCyan,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun StorageRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.width(48.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun QuickCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SurfaceHighCard(modifier.clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = AccentCyan)
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun SurfaceHighCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = SurfaceHigh,
    ) {
        content()
    }
}

@Composable
fun TamilInfoStrip(
    items: List<com.universalrp.cleansweep.data.TamilInfoStripRepo.Item>,
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    onHide: () -> Unit,
    onRefresh: () -> Unit,
    onSpeak: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    var currentIndex by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }

    androidx.compose.runtime.LaunchedEffect(isPaused, items) {
        while (!isPaused) {
            kotlinx.coroutines.delay(6_000)
            currentIndex = (currentIndex + 1) % items.size
        }
    }

    val item = items[currentIndex.coerceIn(0, items.size - 1)]
    val isAlert = item.isSecurityAlert

    androidx.compose.material3.Surface(
        modifier = modifier
            .clickable { onSpeak(item.title) }
            .semantics { contentDescription = "Updates: ${item.title}" },
        shape = RoundedCornerShape(10.dp),
        color = if (isAlert) DangerRed.copy(alpha = 0.12f) else SurfaceHigh,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isAlert) DangerRed.copy(alpha = 0.6f) else OutlineC
        ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isAlert) {
                Icon(
                    Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = DangerRed,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    color = if (isAlert) DangerRed else TextPrimary,
                    fontWeight = if (isAlert) FontWeight.SemiBold else FontWeight.Normal,
                )
                Text(
                    "${item.source} • ${item.date} • ${item.category} • tap to listen",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isAlert) DangerRed.copy(alpha = 0.8f) else TextSecondary,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(6.dp))
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = "New News",
                    tint = AccentCyan,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Android 8, 9 and 10 need runtime storage permission before an app can delete
 * anything. Without it the scan still finds junk and every delete is refused — the
 * "0 MB cleaned" report users hit on phones like the Oppo A3s.
 */
@Composable
private fun LegacyStorageCard(vm: MainViewModel) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.refresh() }

    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Warning, contentDescription = null, tint = WarnAmber)
                Spacer(Modifier.width(10.dp))
                Text(tr("Allow storage access to delete files"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "This phone runs Android 9 or older, where an app needs the storage permission " +
                    "before it can delete anything. Until then CleanSweep finds your junk and " +
                    "Android refuses every delete — which is why cleaning reported 0 MB.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
            GradientButton(
                text = tr("Allow storage access"),
                icon = Icons.Outlined.Warning,
                onClick = { launcher.launch(vm.legacyStoragePermissions()) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}


@Composable
private fun HomeMobileDataCard(state: UiState, vm: MainViewModel) {
    val pack = state.dataPackInfo
    val isUnlimited = pack?.isUnlimited5g == true
    var showQuotaDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.SignalCellularAlt,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        tr("Mobile & Data Pack"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (isUnlimited) tr("Unlimited 5G Active • Counters paused") else tr("Today & Pack quota tracking"),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
                IconButton(onClick = { vm.loadUsage() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(10.dp))

            // Unlimited 5G Toggle Row
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceHigh)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        tr("Unlimited 5G Pack"),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    Text(
                        tr("Hide daily limit & quota countdown on unlimited plans"),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
                Switch(
                    checked = isUnlimited,
                    onCheckedChange = { vm.setUnlimited5g(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AccentCyan,
                        checkedTrackColor = AccentCyan.copy(alpha = 0.35f),
                    ),
                )
            }

            if (!isUnlimited) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceHigh)
                            .padding(12.dp)
                    ) {
                        Text(tr("Mobile data (Today)"), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            pack?.formattedToday ?: "0 MB",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentViolet,
                        )
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceHigh)
                            .padding(12.dp)
                    ) {
                        Text(tr("Pack balance left"), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            (pack?.formattedRemaining ?: "12 GB") + " left",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = GoodGreen,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                tr("Pack Plan: ") + (pack?.formattedPackTotal ?: "0 MB") + " / " + (pack?.formattedPackLimit ?: "12 GB"),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = AccentCyan,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                tr("Strict Mobile Only"),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                        }

                        // Progress Bar
                        val progress = pack?.progressRatio ?: 0f
                        Spacer(Modifier.height(6.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF1E293B))
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(progress)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (progress >= 0.9f) DangerRed else AccentCyan)
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { showQuotaDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SurfaceHigh.copy(alpha = 0.6f),
                                    contentColor = AccentCyan,
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(tr("Set Pack Size"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { showResetDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentViolet.copy(alpha = 0.25f),
                                    contentColor = AccentViolet,
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(tr("Reset Recharge"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    tr("Enjoy high-speed 5G without quota limits. Daily data tracking and pack warnings are hidden."),
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentCyan,
                )
            }
        }
    }

    if (showResetDialog) {
        var inputGb by remember { mutableStateOf(pack?.packLimitGb?.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() } ?: "12") }
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(tr("Reset Recharge Data Pack"), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        tr("Did you recharge a new data pack? Enter your pack size in GB (e.g. 1.5, 3, 6, 12, 25, 50 GB) and reset the counter to zero."),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = inputGb,
                        onValueChange = { inputGb = it },
                        label = { Text(tr("Pack Size in GB")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = TextSecondary.copy(alpha = 0.4f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val size = inputGb.toFloatOrNull()
                        vm.resetDataPackCount(size)
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentViolet),
                ) {
                    Text(tr("Reset to 0 MB"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(tr("Cancel"), color = TextSecondary)
                }
            },
        )
    }

    if (showQuotaDialog) {
        var inputGb by remember { mutableStateOf(pack?.packLimitGb?.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() } ?: "12") }
        AlertDialog(
            onDismissRequest = { showQuotaDialog = false },
            title = { Text(tr("Change Data Pack Limit"), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        tr("Update your total data quota in GB without resetting current usage counter:"),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = inputGb,
                        onValueChange = { inputGb = it },
                        label = { Text(tr("Pack Quota (GB)")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = TextSecondary.copy(alpha = 0.4f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val size = inputGb.toFloatOrNull() ?: 12f
                        vm.updateDataPackLimit(size)
                        showQuotaDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                ) {
                    Text(tr("Save Limit"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuotaDialog = false }) {
                    Text(tr("Cancel"), color = TextSecondary)
                }
            },
        )
    }
}
