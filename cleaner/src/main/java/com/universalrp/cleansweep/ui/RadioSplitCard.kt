package com.universalrp.cleansweep.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.universalrp.cleansweep.data.DataUsageTracker
import com.universalrp.cleansweep.data.NetworkSplit
import com.universalrp.cleansweep.notify.RadioWatchService
import com.universalrp.cleansweep.ui.theme.AccentCyan
import com.universalrp.cleansweep.voice.Announcer

/**
 * v2.47 — the 4G / 5G card.
 *
 * Layout follows the owner's own rule: short lines that open on tap (the "Oor Arivippu"
 * style) instead of a wall of explanation. The counters only appear when the split is on.
 */
@Composable
fun RadioSplitCard() {
    val context = androidx.compose.ui.platform.LocalContext.current

    var separate by remember { mutableStateOf(NetworkSplit.separateEnabled(context)) }
    var announce by remember { mutableStateOf(NetworkSplit.announceEnabled(context)) }
    var policy by remember { mutableStateOf(NetworkSplit.policy(context)) }
    var volume by remember { mutableFloatStateOf(Announcer.voiceVolume(context)) }
    var expanded by remember { mutableStateOf(false) }

    val limitMb = if (DataUsageTracker.hasConfiguredPlan(context)) {
        DataUsageTracker.getPackLimitGb(context) * 1024.0
    } else 0.0
    val status = NetworkSplit.status(context, limitMb)

    fun applyWatch() {
        if (RadioWatchService.needed(context)) RadioWatchService.start(context)
        else RadioWatchService.stop(context)
    }

    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.SignalCellularAlt,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "4G / 5G split",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (separate) "4G ${NetworkSplit.format(status.fourGLeftMb)} left · 5G " +
                            (if (status.fiveGUnlimited) "Unlimited"
                            else NetworkSplit.format(status.fiveGLeftMb) + " left")
                        else "One combined counter",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentCyan,
                    )
                }
                Text(if (expanded) "–" else "+", fontSize = 20.sp, color = AccentCyan)
            }

            if (!expanded) return@Column

            Spacer(Modifier.height(10.dp))

            // ---- counters -------------------------------------------------
            if (separate) {
                SplitLine(
                    label = "4G",
                    used = NetworkSplit.format(status.fourGUsedMb),
                    left = NetworkSplit.format(status.fourGLeftMb),
                    unlimited = false,
                )
                Spacer(Modifier.height(6.dp))
                SplitLine(
                    label = "5G",
                    used = NetworkSplit.format(status.fiveGUsedMb),
                    left = if (status.fiveGUnlimited) "Unlimited" else NetworkSplit.format(status.fiveGLeftMb),
                    unlimited = status.fiveGUnlimited,
                )
                if (status.savedBy5GMb > 0) {
                    Text(
                        "5G saved ${NetworkSplit.format(status.savedBy5GMb)} today",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentCyan,
                    )
                }
                Text(
                    status.unlimitedReason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }

            // ---- switches ------------------------------------------------
            SplitSwitch(
                title = "Count 4G and 5G separately",
                subtitle = "Android keeps one mobile total; this splits each 3-second sample by the radio in use.",
                checked = separate,
                onChange = {
                    separate = it
                    NetworkSplit.setSeparate(context, it)
                    applyWatch()
                },
            )
            SplitSwitch(
                title = "Announce 4G ⇄ 5G changes",
                subtitle = "Speaks once per change, at most every 20 seconds. Quiet hours still apply.",
                checked = announce,
                onChange = {
                    announce = it
                    NetworkSplit.setAnnounce(context, it)
                    applyWatch()
                },
            )

            Text(
                "5G unlimited on this plan",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("AUTO" to "Auto", "ON" to "On", "OFF" to "Off").forEach { (key, label) ->
                    OutlinedButton(
                        onClick = { policy = key; NetworkSplit.setPolicy(context, key) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            label,
                            fontSize = 13.sp,
                            fontWeight = if (policy == key) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
            Text(
                NetworkSplit.reason(context),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ---- in-app volume -------------------------------------------
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Announcement volume",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Text("${(volume * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            }
            Slider(
                value = volume,
                onValueChange = { volume = it },
                onValueChangeFinished = { Announcer.setVoiceVolume(context, volume) },
                valueRange = 0f..1f,
                steps = 19,
            )
            TextButton(onClick = {
                Announcer.setVoiceVolume(context, volume)
                Announcer.speak(context, "This is the alert volume", "இது எச்சரிக்கை ஒலி அளவு",
                    Announcer.Event.TEST)
            }) { Text("Test / சோதனை") }
        }
    }
}

@Composable
private fun SplitLine(label: String, used: String, left: String, unlimited: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.width(8.dp))
        Text(used + " used", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(
            if (unlimited) "Unlimited" else "$left left",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (unlimited) AccentCyan else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SplitSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
