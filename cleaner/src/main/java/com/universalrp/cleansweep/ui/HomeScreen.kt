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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Folder
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
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
fun HomeScreen(state: UiState, vm: MainViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Top bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(AccentCyan, AccentViolet))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.CleaningServices,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    tr(Lang.appName()),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    Lang.tagline(),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            // One tap between தமிழ் and English, right where the eye already is. The full
            // choice (and the note about the launcher name) stays in Settings → Language.
            TextButton(onClick = { vm.setLanguage(if (state.lang == AppLang.TA) AppLang.EN else AppLang.TA) }) {
                Text(
                    if (state.lang == AppLang.TA) tr("EN") else tr("த"),
                    color = AccentCyan,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            IconButton(onClick = { vm.navigate(Screen.VOICE) }) {
                Icon(
                    if (state.voiceOn) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff,
                    contentDescription = "Voice",
                    tint = if (state.voiceOn) AccentCyan else TextSecondary,
                )
            }
            IconButton(onClick = { vm.navigate(Screen.SETTINGS) }) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = TextSecondary)
            }
            IconButton(onClick = { vm.navigate(Screen.ABOUT) }) {
                Icon(Icons.Outlined.Info, contentDescription = "About", tint = TextSecondary)
            }
        }

        LedBar(Modifier.fillMaxWidth().padding(horizontal = 16.dp))

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!state.hasAllFilesAccess) {
                item { SetupCard(vm) }
            }

            item { StorageCard(state) }

            // The daily brief card: what CleanSweep found for you today, and the switch that
            // turns the whole daily watch on or off without digging into Settings.
            item {
                PanelCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.RecordVoiceOver,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                tr("Daily brief"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            if (state.voiceOn) {
                                Icon(
                                    Icons.Outlined.VolumeUp,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (state.lastBrief.isNotBlank()) state.lastBrief
                            else tr(
                                "Once a day CleanSweep checks the battery, temperature, storage, " +
                                    "junk and security, says a short summary out loud and keeps " +
                                    "the full report here."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { vm.runDailyBriefNow() }) {
                                Text(
                                    tr("Run the daily check now"),
                                    color = AccentCyan,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            TextButton(onClick = { vm.navigate(Screen.VOICE) }) {
                                Text(
                                    if (state.dailyScanOn) tr("Daily brief: on") else tr("Daily brief: off"),
                                    color = if (state.dailyScanOn) GoodGreen else TextSecondary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }

            if (!state.legacyStorageOk) {
                item { LegacyStorageCard(vm) }
            }

            state.health?.let { health ->
                item {
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

            item { AiStatusCard(state, vm) }

            item {
                GradientButton(
                    text = if (state.report != null) "Scan again" else "Scan & clean junk",
                    icon = Icons.Outlined.AutoFixHigh,
                    onClick = { vm.startScan() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.hasAllFilesAccess && state.legacyStorageOk,
                )
            }

            item { AssistantCard(state, vm) }

            item {
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

            item {
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

            state.lastClean?.let { stats ->
                item {
                    PanelCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.CleaningServices,
                                contentDescription = null,
                                tint = GoodGreen,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Last clean: ${stats.freedBytes.formatBytes()} freed",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "${stats.items} items • ${
                                        SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
                                            .format(Date(stats.atMs))
                                    }",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                )
                            }
                        }
                    }
                }
            }

            item { PhoneTipCard() }
        }
    }
}

/** Entry point for the on-device assistant (v1.3). */
@Composable
private fun AssistantCard(state: UiState, vm: MainViewModel) {
    val totalCache = state.appCaches.sumOf { if (it.cacheBytes > 0L) it.cacheBytes else 0L }
    // Which engine is answering *now*. The old build hard-coded "ON-DEVICE" even after
    // the user switched to Online AI, which is exactly what the screenshot complained about.
    val online = state.assistantOnline && state.aiConfig.ready
    val badge = if (online) "ONLINE AI" else "ON-DEVICE"
    val badgeColor = if (online) GoodGreen else AccentCyan
    val subtitle = when {
        online -> "Answered by ${state.aiEngineLabel} — tap to ask or change"
        state.report != null -> "Ask me what the scan found — I work offline"
        totalCache > 0L -> "${totalCache.formatBytes()} of app cache found — ask me what to do"
        else -> "Free space, safe deletes, cache help — answers stay on this phone"
    }
    PanelCard(
        Modifier
            .fillMaxWidth()
            .clickable { vm.openAssistant() }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(AccentCyan, AccentViolet))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.SmartToy,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Ask the assistant"),
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
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}

/**
 * "Is my AI actually able to answer?" — checked on every app start. Green when the chosen
 * provider answered a moment ago, red with the real reason when it did not, and always
 * with a one-tap way out: fix the settings, check again, or switch to a free AI.
 */
@Composable
private fun AiStatusCard(state: UiState, vm: MainViewModel) {
    val configured = state.aiConfig.ready
    val ok = configured && state.aiStatusOk
    val accent = when {
        !configured -> WarnAmber
        ok -> GoodGreen
        else -> DangerRed
    }
    PanelCard(
        Modifier
            .fillMaxWidth()
            .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = accent,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            state.aiStatusChecking -> "Checking your AI…"
                            ok -> "AI ready"
                            configured -> "AI is not answering"
                            else -> "AI is not set up"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        when {
                            state.aiStatusChecking -> "Asking ${state.aiEngineLabel} for a quick hello."
                            ok -> state.aiEngineLabel + " answered. Every answer will show its model."
                            configured -> state.aiStatusText
                                ?: "The provider did not answer. You can try a free AI instead."
                            else -> state.aiStatusText
                                ?: "Add a free Gemini key, or use the free no-key option."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = { vm.openAiSettings() }) {
                    Text(tr("AI settings"), color = AccentCyan, fontWeight = FontWeight.Bold)
                }
                if (configured) {
                    TextButton(onClick = { vm.refreshAiStatus(announce = true) }) {
                        Text(tr("Check again"), color = AccentCyan, fontWeight = FontWeight.Bold)
                    }
                }
                if (!ok) {
                    TextButton(onClick = { vm.useAnotherFreeAi() }) {
                        Text(tr("Use a free AI"), color = GoodGreen, fontWeight = FontWeight.Bold)
                    }
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
                        "free",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                    )
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
private fun PhoneTipCard() {
    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(tr("Works on every Android phone"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = AccentViolet,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Redmi and other Xiaomi phones (HyperOS / MIUI), Samsung, Oppo, Vivo, Realme, " +
                    "OnePlus, Motorola, Nokia, Tecno and stock Android — one app, the same features. " +
                    "CleanSweep never touches your personal files unless you select them, and the only " +
                    "thing that ever uses the internet is the AI analysis you start yourself. " +
                    "WhatsApp media (Settings → Storage) is usually the biggest single space saver.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
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
