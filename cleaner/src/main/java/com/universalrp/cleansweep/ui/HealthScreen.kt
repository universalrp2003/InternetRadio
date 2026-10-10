package com.universalrp.cleansweep.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.universalrp.cleansweep.MainViewModel
import com.universalrp.cleansweep.Screen
import com.universalrp.cleansweep.UiState
import com.universalrp.cleansweep.data.BatteryReading
import com.universalrp.cleansweep.data.HealthSnapshot
import com.universalrp.cleansweep.data.batteryTimeLabel
import com.universalrp.cleansweep.data.formatBytes
import com.universalrp.cleansweep.data.formatUptime
import com.universalrp.cleansweep.data.tr
import com.universalrp.cleansweep.ui.theme.AccentCyan
import com.universalrp.cleansweep.ui.theme.AccentViolet
import com.universalrp.cleansweep.ui.theme.DangerRed
import com.universalrp.cleansweep.ui.theme.GoodGreen
import com.universalrp.cleansweep.ui.theme.SurfaceHigh
import com.universalrp.cleansweep.ui.theme.TextPrimary
import com.universalrp.cleansweep.ui.theme.TextSecondary
import com.universalrp.cleansweep.ui.theme.WarnAmber
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Live battery, temperature, CPU, memory and device information — the numbers the
 * phone's own engine reports, refreshed while you watch.
 */
@Composable
fun HealthScreen(state: UiState, vm: MainViewModel) {
    // Re-read the sensors while this screen is open. Two seconds is fast enough to
    // watch the charging current change without waking the phone constantly.
    LaunchedEffect(Unit) {
        while (isActive) {
            vm.refreshHealth()
            delay(2000)
        }
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
            Text(tr("Phone health"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { vm.refreshHealth() }) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh now", tint = TextSecondary)
            }
        }

        val health = state.health
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (health == null) {
                item {
                    PanelCard(Modifier.fillMaxWidth()) {
                        Text(tr("Reading the sensors…"),
                            Modifier.padding(16.dp),
                            color = TextSecondary,
                        )
                    }
                }
            } else {
                item { BatteryCard(health.battery) }
                item { StatusBarPillCard(state, vm) }
                item { ThermalCard(health) }
                item { CpuCard(health) }
                item { MemoryCard(health) }
                item { DeviceCard(health) }
            }
        }
    }
}

/**
 * The user circled the empty part of the status bar and asked for the charging watts to be
 * shown there. Android has no way for an app to add a real system status-bar item, so
 * CleanSweep draws a tiny reading itself — this card is where it is switched on, with an
 * honest explanation of what it can and cannot do.
 */
@Composable
private fun StatusBarPillCard(state: UiState, vm: MainViewModel) {
    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (state.statusPill && state.statusPillAllowed) Icons.Outlined.CheckCircle
                    else Icons.Outlined.Bolt,
                    contentDescription = null,
                    tint = if (state.statusPill && state.statusPillAllowed) GoodGreen else AccentCyan,
                )
                Spacer(Modifier.width(10.dp))
                Text(tr("Charging watts beside the clock"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    state.statusPill && state.statusPillAllowed ->
                        "On. The reading appears in the empty space at the top (beside the front " +
                            "camera) only while the charger is connected. It cannot be tapped and " +
                            "shows nothing but watts."
                    state.statusPill ->
                        "Switched on, but Android still needs “Display over other apps” before " +
                            "anything can be drawn there."
                    else ->
                        "Android does not let normal apps add items to the system status bar. " +
                            "CleanSweep draws its own tiny reading instead — watts while charging, " +
                            "in the empty space next to the clock."
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (state.statusPill) vm.setStatusPill(false) else vm.setStatusPill(true)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.statusPill) SurfaceHigh else AccentCyan,
                        contentColor = if (state.statusPill) TextSecondary else Color(0xFF03202B),
                    ),
                ) {
                    Text(
                        if (state.statusPill) "Turn it off" else "Show watts in the status bar",
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (state.statusPill && !state.statusPillAllowed) {
                    TextButton(onClick = { vm.openOverlaySettings() }) {
                        Text(tr("Allow overlay"), color = WarnAmber, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (state.statusPill && state.statusPillAllowed) {
                Spacer(Modifier.height(8.dp))
                Text(
                    tr(
                        "Move it anywhere: phones put VoLTE, VPN, the carrier name or the battery " +
                            "percentage in that strip, so the free space is different on every model."
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
                ReadingMovePad(
                    onMove = { dx, dy -> vm.moveStatusPill(dx, dy) },
                    onReset = { vm.resetStatusPill() },
                    note = tr("The position is remembered, and the reading still only appears while charging."),
                    dragging = state.pillDragging,
                    onToggleDrag = { vm.togglePillDrag() },
                )
            }
        }
    }
}

@Composable
private fun BatteryCard(battery: BatteryReading) {
    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.BatteryFull, contentDescription = null, tint = GoodGreen)
                Spacer(Modifier.width(10.dp))
                Text(tr("Battery"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (battery.percent >= 0) "${battery.percent}%" else "—",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (battery.charging) GoodGreen else TextPrimary,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${battery.statusLabel} • ${battery.pluggedLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )

            Bar(value = if (battery.percent >= 0) battery.percent / 100f else 0f,
                color = if (battery.charging) GoodGreen else AccentCyan)
            batteryTimeLabel(battery)?.let { estimate ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        estimate,
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentCyan,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (battery.charging) {
                        Text(
                            battery.powerW?.let { "%.1f W".format(it) }
                                ?: battery.currentA?.let { "%.2f A".format(it) } ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = GoodGreen,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (battery.charging && battery.percent >= 80) {
                    Text(
                        "Above 80% charging slows down on purpose — that is how lithium batteries " +
                            "protect themselves, so the last 20% always takes longer.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox(
                    "Temperature",
                    battery.temperatureC?.let { "%.1f °C".format(it) } ?: "Not reported",
                    battery.temperatureC?.let { temperatureColor(it) } ?: TextSecondary,
                    Modifier.weight(1f),
                )
                StatBox(
                    "Voltage",
                    battery.voltageV?.let { "%.2f V".format(it) } ?: "Not reported",
                    TextPrimary,
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox(
                    "Current",
                    battery.currentA?.let { "%+.2f A".format(it) } ?: "Not reported",
                    if ((battery.currentA ?: 0f) > 0.05f) GoodGreen else WarnAmber,
                    Modifier.weight(1f),
                )
                StatBox(
                    if (battery.charging) "Charging power" else "Power draw",
                    battery.powerW?.let { "%.2f W".format(it) } ?: "Not reported",
                    AccentCyan,
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox("Health", battery.healthLabel, TextPrimary, Modifier.weight(1f))
                StatBox(
                    "Charge counter",
                    battery.chargeCounterMah?.let { "%.0f mAh".format(it) } ?: "Not reported",
                    TextPrimary,
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Technology: ${battery.technology}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
            Text(
                battery.currentSignNote,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Charging speed in watts is voltage × current. A 10 W charger shows about 9 W at the " +
                    "battery because some power turns into heat on the way in.",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun ThermalCard(health: HealthSnapshot) {
    val cpuTemp = health.cpuTempC
    val colour = cpuTemp?.let { temperatureColor(it) } ?: TextSecondary
    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Thermostat, contentDescription = null, tint = colour)
                Spacer(Modifier.width(10.dp))
                Text(tr("Temperature"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    cpuTemp?.let { "%.1f °C".format(it) } ?: "—",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = colour,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (cpuTemp == null)
                    "This kernel does not expose a CPU temperature sensor to apps. Battery " +
                        "temperature and the hottest sensor below are still shown."
                else
                    "${health.thermalState} • sensor: ${health.cpuTempZone ?: "unknown"}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox(
                    "Battery sensor",
                    health.battery.temperatureC?.let { "%.1f °C".format(it) }
                        ?: health.batteryTempFromKernelC?.let { "%.1f °C".format(it) }
                        ?: "Not reported",
                    health.battery.temperatureC?.let { temperatureColor(it) } ?: TextSecondary,
                    Modifier.weight(1f),
                )
                StatBox(
                    "Hottest sensor",
                    health.cpuMaxTempC?.let { "%.1f °C".format(it) } ?: "Not reported",
                    health.cpuMaxTempC?.let { temperatureColor(it) } ?: TextSecondary,
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "The hottest sensor is often a charging or modem sensor, not the processor — it can " +
                    "read higher than the CPU without anything being wrong.",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Normal for a phone in your hand: 30–42 °C. Warm to 48 °C while charging or gaming " +
                    "is normal. Repeated 50 °C+ makes the phone slow itself down; 60 °C+ is a sign to " +
                    "stop and let it cool.",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun CpuCard(health: HealthSnapshot) {
    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Speed, contentDescription = null, tint = AccentCyan)
                Spacer(Modifier.width(10.dp))
                Text(tr("Processor"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${health.device.cores} cores",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox(
                    "Fastest core now",
                    health.cpuCurrentMhz?.let { "$it MHz" } ?: "Not reported",
                    AccentCyan,
                    Modifier.weight(1f),
                )
                StatBox(
                    "Fastest core max",
                    health.cpuMaxMhz?.let { "$it MHz" } ?: "Not reported",
                    TextPrimary,
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            health.cpuLoadPercent?.let { load ->
                Text(
                    "Load: ${load.toInt()}% of all cores" +
                        (health.cpuLoadAvg?.let { " (average ${"%.2f".format(it)})" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Bar(value = load / 100f, color = if (load > 80f) WarnAmber else AccentCyan)
            }
            Text(
                "The two numbers are the fastest big core, not core 0 — a phone mixes big and " +
                    "little cores, so \"now\" can be well below \"max\".",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
            Text(
                "SoC ${health.device.soc} • ${health.device.abi}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun MemoryCard(health: HealthSnapshot) {
    val usedRam = (health.device.totalRamBytes - health.device.availableRamBytes).coerceAtLeast(0L)
    val ramFraction = if (health.device.totalRamBytes > 0L) {
        usedRam.toFloat() / health.device.totalRamBytes.toFloat()
    } else {
        0f
    }
    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Memory, contentDescription = null, tint = AccentViolet)
                Spacer(Modifier.width(10.dp))
                Text(tr("Memory & storage"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "RAM: ${usedRam.formatBytes()} used of ${health.device.totalRamBytes.formatBytes()}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Bar(value = ramFraction, color = if (ramFraction > 0.85f) WarnAmber else AccentViolet)
            Spacer(Modifier.height(12.dp))
            Text(
                "Storage: ${health.storage.used.formatBytes()} used of " +
                    "${health.storage.total.formatBytes()} — ${health.storage.free.formatBytes()} free",
                style = MaterialTheme.typography.bodyMedium,
            )
            Bar(
                value = health.storage.usedFraction,
                color = if (health.storage.usedFraction > 0.9f) DangerRed else AccentCyan,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Android keeps RAM busy on purpose — cached apps are what make switching back " +
                    "instant. High RAM use alone is not a problem.",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun DeviceCard(health: HealthSnapshot) {
    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, tint = AccentCyan)
                Spacer(Modifier.width(10.dp))
                Text(
                    "${health.device.manufacturer} ${health.device.model}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            InfoLine("Android", "${health.device.androidVersion} (API ${health.device.sdk})")
            InfoLine("Security patch", health.device.securityPatch)
            InfoLine("Uptime", formatUptime(health.device.uptimeMs))
            InfoLine("Device / hardware", "${health.device.device} • ${health.device.hardware}")
        }
    }
}

/* ------------------------------------------------------------------ small parts */

@Composable
private fun StatBox(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = valueColor,
            )
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** A plain filled bar — no animation, no flicker, just the number. */
@Composable
private fun Bar(value: Float, color: Color) {
    val safe = value.coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF1E2A45))
    ) {
        if (safe > 0.01f) {
            Box(
                Modifier
                    .fillMaxWidth(safe)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
    Spacer(Modifier.height(4.dp))
}

private fun temperatureColor(celsius: Float): Color = when {
    celsius < 40f -> GoodGreen
    celsius < 48f -> AccentCyan
    celsius < 55f -> WarnAmber
    else -> DangerRed
}
