package com.universalrp.cleansweep.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.universalrp.cleansweep.MainViewModel
import com.universalrp.cleansweep.Screen
import com.universalrp.cleansweep.UiState
import com.universalrp.cleansweep.data.AppCacheInfo
import com.universalrp.cleansweep.data.formatBytes
import com.universalrp.cleansweep.ui.theme.AccentCyan
import com.universalrp.cleansweep.ui.theme.Bg
import com.universalrp.cleansweep.ui.theme.DangerRed
import com.universalrp.cleansweep.ui.theme.SurfaceC
import com.universalrp.cleansweep.ui.theme.SurfaceHigh
import com.universalrp.cleansweep.ui.theme.TextPrimary
import com.universalrp.cleansweep.ui.theme.TextSecondary
import com.universalrp.cleansweep.ui.theme.WarnAmber

@Composable
fun AppCacheScreen(state: UiState, vm: MainViewModel) {
    var showHowItWorks by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.navigate(Screen.HOME) }) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Home", tint = TextSecondary)
            }
            Text(
                "App cache cleaner",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { vm.loadAppCaches() }) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = TextSecondary)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.usageAccess) {
                item { UsageAccessCard(vm) }
            } else {
                item {
                    val totalCache = state.appCaches.sumOf { it.cacheBytes }
                    PanelCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    totalCache.formatBytes(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentCyan,
                                )
                                Text(
                                    "cache across ${state.appCaches.size} apps",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "System apps",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Switch(
                                    checked = state.includeSystemApps,
                                    onCheckedChange = { vm.setIncludeSystemApps(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF03202B),
                                        checkedTrackColor = AccentCyan,
                                    ),
                                )
                            }
                        }
                    }
                }

                item { AutoCleanCard(state, vm) }

                if (state.appsLoading) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = AccentCyan)
                        }
                    }
                } else if (state.appCaches.isEmpty()) {
                    item {
                        Text(
                            "No apps with meaningful cache found. Pull down the list or tap Refresh after using your apps a bit.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                        )
                    }
                }

                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Select apps",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { vm.toggleAllApps(true) }) {
                            Text("All", color = AccentCyan)
                        }
                        TextButton(onClick = { vm.toggleAllApps(false) }) {
                            Text("None", color = TextSecondary)
                        }
                    }
                }

                items(state.appCaches, key = { it.pkg }) { app ->
                    AppCacheRow(app, vm)
                }
            }
        }

        // Bottom action bar
        if (state.usageAccess) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(SurfaceC)
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.pendingManualApps > 0) {
                        Button(
                            onClick = { vm.openNextManualApp() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WarnAmber,
                                contentColor = Color(0xFF2B1D02),
                            ),
                        ) {
                            Text("Next app (${state.pendingManualApps} left)", fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = {
                            if (!state.autoCleanAvailable) showHowItWorks = true
                            else vm.cleanSelectedApps()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DangerRed,
                            contentColor = Color(0xFF2B0606),
                        ),
                    ) {
                        Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Clean cache", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showHowItWorks) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showHowItWorks = false },
            title = { Text("Auto clean needs Accessibility") },
            text = {
                Text(
                    "Android does not let one app clear another app's cache (that would need root). " +
                        "CleanSweep can do it for you instead: with Accessibility turned on, it opens each " +
                        "selected app's page and taps Storage → Clear cache automatically.\n\n" +
                        "You can also continue in manual mode: CleanSweep opens each app page and you tap Clear cache yourself.",
                    color = TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showHowItWorks = false
                    vm.openAccessibilitySettings()
                }) {
                    Text("Enable auto clean", color = AccentCyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showHowItWorks = false
                    vm.cleanSelectedApps()
                }) {
                    Text("Manual mode", color = TextSecondary)
                }
            },
        )
    }
}

@Composable
private fun UsageAccessCard(vm: MainViewModel) {
    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Android, contentDescription = null, tint = WarnAmber)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Usage access needed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "To show how much cache each app is holding, CleanSweep needs the " +
                    "“Usage access” permission. Find CleanSweep on the next screen and switch it ON.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { vm.requestUsageAccess() }) {
                Text("Grant usage access", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AutoCleanCard(state: UiState, vm: MainViewModel) {
    PanelCard(
        Modifier
            .fillMaxWidth()
            .clickable { vm.openAccessibilitySettings() }
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(SurfaceHigh, shape = MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.AutoFixHigh,
                    contentDescription = null,
                    tint = if (state.autoCleanAvailable) AccentCyan else TextSecondary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Auto clean (Accessibility)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (state.autoCleanAvailable)
                        "Enabled — caches will be cleared fully automatically"
                    else
                        "Off — tap to enable hands-free cache cleaning",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.autoCleanAvailable) AccentCyan else TextSecondary,
                )
            }
            StatusDot(state.autoCleanAvailable, if (state.autoCleanAvailable) "ON" else "OFF")
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}

@Composable
private fun AppCacheRow(app: AppCacheInfo, vm: MainViewModel) {
    PanelCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { vm.toggleApp(app.pkg) }
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = app.selected,
                onCheckedChange = { vm.toggleApp(app.pkg) },
                colors = CheckboxDefaults.colors(
                    checkedColor = AccentCyan,
                    checkmarkColor = Color(0xFF03202B),
                    uncheckedColor = TextSecondary,
                ),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    app.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    app.pkg + if (app.isSystem) " • system" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            ByteChip(app.cacheBytes)
        }
    }
}
