package com.universalrp.pulseeq.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.universalrp.pulseeq.EqTab
import com.universalrp.pulseeq.EqUiState
import com.universalrp.pulseeq.EqViewModel

@Composable
fun SettingsScreen(state: EqUiState, vm: EqViewModel) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Settings, contentDescription = null, tint = Cyan)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Background & coverage",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "How PulseEQ keeps equalizing after you leave the app",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
        }

        item {
            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SettingSwitch(
                        title = "Run in the background",
                        subtitle = "Keeps the DSP and the system effect alive with a small ongoing " +
                            "notification. This is the switch that stops the equalizer from closing.",
                        checked = state.serviceRunning,
                        onCheckedChange = { vm.setServiceWanted(it) },
                    )
                    Spacer(Modifier.height(8.dp))
                    SettingSwitch(
                        title = "Try system-wide effect",
                        subtitle = "Attach one equalizer to the phone's whole output mix. Google deprecated " +
                            "this and many Android 11+ ROMs refuse it for normal apps — PulseEQ reports " +
                            "honestly whether your phone allowed it.",
                        checked = state.systemEqWanted,
                        onCheckedChange = { vm.setSystemEqWanted(it) },
                    )
                    Spacer(Modifier.height(8.dp))
                    SettingSwitch(
                        title = "Equalize cooperating players",
                        subtitle = "Players such as Poweramp, VLC or Musicolet announce their audio session. " +
                            "PulseEQ shapes those sessions directly — no capture, no root.",
                        checked = state.cooperating,
                        onCheckedChange = { vm.setCooperating(it) },
                    )
                }
            }
        }

        item {
            PanelCard(Modifier.fillMaxWidth(), color = SurfaceHigh) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "System effect: ${if (state.systemEqActive) "ACTIVE" else "not active"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.systemEqActive) Good else Warn,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        state.systemStatus.ifBlank { "Not started yet." },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        state.deviceBandInfo,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                    if (state.cooperatingSessions > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Currently equalizing ${state.cooperatingSessions} player session(s).",
                            style = MaterialTheme.typography.labelSmall,
                            color = Cyan,
                        )
                    }
                }
            }
        }

        item {
            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Warning, contentDescription = null, tint = Warn)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Battery optimisation",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                if (state.batteryOptimized)
                                    "PulseEQ is still optimised — Android may stop the equalizer when the " +
                                        "screen is off for a long time."
                                else
                                    "PulseEQ is exempt — the equalizer survives Doze.",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (state.batteryOptimized) Warn else Good,
                            )
                        }
                    }
                    if (state.batteryOptimized) {
                        Spacer(Modifier.height(10.dp))
                        IconChip(
                            Icons.Outlined.Warning,
                            "Allow unrestricted battery use",
                            tint = Warn,
                        ) { vm.requestIgnoreBatteryOptimizations() }
                    }
                }
            }
        }

        item {
            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "LED brightness",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Slider(
                        value = state.ledBrightness,
                        onValueChange = { vm.setLedBrightness(it) },
                        valueRange = 0.35f..1f,
                        colors = SliderDefaults.colors(thumbColor = Warn, activeTrackColor = Warn),
                    )
                    Text(
                        "Idle bars dim down automatically; this sets how bright the lit segments are.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
        }

        item {
            PanelCard(
                Modifier
                    .fillMaxWidth()
                    .clickable { vm.navigate(EqTab.ABOUT) }
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = Cyan)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "About PulseEQ",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "What it can and cannot equalize — the honest list",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                    Text("›", color = TextSecondary, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF04202A),
                checkedTrackColor = Cyan,
            ),
        )
    }
}
