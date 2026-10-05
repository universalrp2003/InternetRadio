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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.universalrp.cleansweep.MainViewModel
import com.universalrp.cleansweep.Screen
import com.universalrp.cleansweep.UiState
import com.universalrp.cleansweep.data.formatBytes
import com.universalrp.cleansweep.ui.theme.AccentCyan
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
                    "CleanSweep",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Junk cleaner for your Redmi",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
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

            item {
                GradientButton(
                    text = if (state.report != null) "Scan again" else "Scan & clean junk",
                    icon = Icons.Outlined.AutoFixHigh,
                    onClick = { vm.startScan() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.hasAllFilesAccess,
                )
            }

            item { AssistantCard(state, vm) }

            item {
                SectionTitle("Quick actions")
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickCard(
                            icon = Icons.Outlined.Android,
                            title = "App cache",
                            subtitle = "Clear caches of installed apps",
                            onClick = { vm.navigate(Screen.APP_CACHE) },
                            modifier = Modifier.weight(1f),
                        )
                        QuickCard(
                            icon = Icons.Outlined.ContentCopy,
                            title = "Duplicates",
                            subtitle = "Find identical files",
                            onClick = { vm.startScan() },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickCard(
                            icon = Icons.Outlined.Folder,
                            title = "Empty folders",
                            subtitle = "Remove useless folders",
                            onClick = { vm.startScan() },
                            modifier = Modifier.weight(1f),
                        )
                        QuickCard(
                            icon = Icons.Outlined.SdStorage,
                            title = "Large files",
                            subtitle = "Review very big files",
                            onClick = { vm.startScan() },
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

            item { RedmiTipCard() }
        }
    }
}

/** Entry point for the on-device assistant (v1.3). */
@Composable
private fun AssistantCard(state: UiState, vm: MainViewModel) {
    val totalCache = state.appCaches.sumOf { if (it.cacheBytes > 0L) it.cacheBytes else 0L }
    val subtitle = when {
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
                    Text(
                        "Ask the assistant",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "ON-DEVICE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF04202A),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentCyan)
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
                Text(
                    "Storage access needed",
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
                text = "Allow storage access",
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
private fun RedmiTipCard() {
    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Tip for Redmi / HyperOS",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = AccentViolet,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "CleanSweep works fully offline and never touches your personal files unless you select them. " +
                    "For the deepest clean, also use the pre-installed Security app → Cleaner once in a while, " +
                    "and remember WhatsApp media (Settings → Storage) often holds the most space.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}
