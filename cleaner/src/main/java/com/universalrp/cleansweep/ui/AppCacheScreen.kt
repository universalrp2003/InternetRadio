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

                item { GuidedCleanCard(state) }

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
                        onClick = { vm.cleanSelectedApps() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DangerRed,
                            contentColor = Color(0xFF2B0606),
                        ),
                    ) {
                        Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (state.pendingManualApps > 0) "Clean next apps" else "Clean cache",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
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

/**
 * Explains the v1.3 guided flow honestly: Android does not allow one app to wipe
 * another app's cache, and CleanSweep no longer asks for Accessibility to fake it.
 * Instead it walks you through the official buttons, two taps per app.
 */
@Composable
private fun GuidedCleanCard(state: UiState) {
    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(SurfaceHigh, shape = MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.AutoFixHigh,
                        contentDescription = null,
                        tint = if (state.pendingManualApps > 0) WarnAmber else AccentCyan,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Guided two-tap clean",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "No Accessibility permission needed — v1.3 dropped it",
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentCyan,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (state.pendingManualApps > 0)
                    "Cleaning in progress: ${state.pendingManualApps} app${if (state.pendingManualApps == 1) "" else "s"} left. " +
                        "CleanSweep opened the app's storage page — tap “Clear cache” there, come back, then tap “Next app”."
                else
                    "Android does not let any cleaner wipe another app's cache silently (that needs root). " +
                        "So CleanSweep opens each selected app's storage page and you tap “Clear cache” — two taps per app, " +
                        "and you can see exactly what is happening.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "1. Tick apps  →  2. Clean cache  →  3. Tap “Clear cache” in Settings  →  4. Back → Next app",
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
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
