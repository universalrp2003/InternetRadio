package com.universalrp.cleansweep.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Battery3Bar
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.ElectricalServices
import androidx.compose.material.icons.outlined.PowerOff
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.universalrp.cleansweep.MainViewModel
import com.universalrp.cleansweep.UiState
import com.universalrp.cleansweep.data.tr
import com.universalrp.cleansweep.ui.theme.AccentCyan
import com.universalrp.cleansweep.ui.theme.AccentViolet
import com.universalrp.cleansweep.ui.theme.DangerRed
import com.universalrp.cleansweep.ui.theme.GoodGreen
import com.universalrp.cleansweep.ui.theme.SurfaceC
import com.universalrp.cleansweep.ui.theme.TextPrimary
import com.universalrp.cleansweep.ui.theme.TextSecondary
import com.universalrp.cleansweep.ui.theme.WarnAmber
import com.universalrp.cleansweep.voice.Announcer

/**
 * Voice & daily watch.
 *
 * Everything CleanSweep can say out loud lives behind switches here, and every one of them can
 * be turned off — the user asked for warnings "at day time, not disturb at user sleeping
 * time", so the quiet window is enforced in the speaker itself, not only in this screen.
 */
@Composable
fun VoiceScreen(state: UiState, vm: MainViewModel) {
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
            IconButton(onClick = { vm.goBack() }) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = tr("Settings"), tint = TextSecondary)
            }
            Text(
                tr("Voice & daily watch"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ------------------------------------------------------------ master switch
            PanelCard(Modifier.fillMaxWidth()) {
              Column(Modifier.fillMaxWidth()) {
                VoiceSwitch(
                    title = tr("Speak to me"),
                    subtitle = if (state.voiceOn) {
                        tr("CleanSweep talks: warnings about the battery, heat and the daily brief.")
                    } else {
                        tr("Voice is off. Nothing will be spoken, at any hour.")
                    },
                    checked = state.voiceOn,
                    onCheckedChange = { vm.setVoiceOn(it) },
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VoiceChip(
                        label = tr("Hear the voice"),
                        icon = Icons.Outlined.PlayArrow,
                        onClick = { vm.testVoice() },
                    )
                    VoiceChip(
                        label = tr("Run the daily check now"),
                        icon = Icons.Outlined.RecordVoiceOver,
                        onClick = { vm.runDailyBriefNow() },
                    )
                }
                Text(
                    tr(
                        "Speaking uses Android's own text-to-speech engine. If your phone has a Tamil " +
                            "voice installed, Tamil text is spoken in Tamil; otherwise the English " +
                            "wording is spoken instead of reading Tamil in an English accent."
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
              }
            }

            // ------------------------------------------------------------ voice quality (v2.6)
            // The phone's own engine is the fallback and always works offline; the online voice
            // is opt-in because it sends the sentence (never files, never the key) to the
            // configured AI provider and speaks the returned audio.
            PanelCard(Modifier.fillMaxWidth()) {
              Column(Modifier.fillMaxWidth()) {
                VoiceSwitch(
                    title = tr("Natural voice (online)"),
                    subtitle = tr(
                        "Sounds far more human than the phone's built-in robot voice: Android's own " +
                            "cloud voice reads the sentence instead of the offline one. Only the words " +
                            "being spoken leave the phone — never a file, never a message."
                    ),
                    checked = state.onlineVoice,
                    onCheckedChange = { vm.setOnlineVoice(it) },
                )
                VoiceSwitch(
                    title = tr("Louder voice alerts"),
                    subtitle = tr(
                        "Temporarily maximizes speech volume during voice warnings so charging and " +
                            "critical alerts are clearly audible, without permanently changing phone media volume."
                    ),
                    checked = state.voiceLouder,
                    onCheckedChange = { vm.setLouderVoice(it) },
                )
                Text(
                    tr(
                        "Works in Tamil and English. If the phone has no cloud voice installed, or the " +
                            "network is out, the offline voice reads the same line instead — " +
                            "announcements never go silent. No API key is used for this."
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
              }
            }

            // ------------------------------------------------------------ quiet hours
            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.NightsStay,
                            contentDescription = null,
                            tint = AccentViolet,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            tr("Quiet hours"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        )
                    }
                    Text(
                        tr(
                            "Nothing is spoken between these hours — not even important warnings. " +
                                "Anything you ask for yourself is still spoken when you ask."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HourPicker(
                            value = state.voiceQuietStart,
                            onPick = { vm.setQuietHours(it, state.voiceQuietEnd) },
                        )
                        Text(
                            "  " + tr("to") + "  ",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        HourPicker(
                            value = state.voiceQuietEnd,
                            onPick = { vm.setQuietHours(state.voiceQuietStart, it) },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            20 to 7,
                            21 to 6,
                            22 to 7,
                            0 to 23,
                        ).forEach { (start, end) ->
                            val startH = if (start == 0 || start == 12) 12 else start % 12
                            val startAmPm = if (start < 12) "AM" else "PM"
                            val endH = if (end == 0 || end == 12) 12 else end % 12
                            val endAmPm = if (end < 12) "AM" else "PM"
                            ModeChipExtra(
                                label = if (start == 0) tr("All day") else "$startH $startAmPm – $endH $endAmPm",
                                selected = state.voiceQuietStart == start && state.voiceQuietEnd == end,
                                onClick = { vm.setQuietHours(start, end) },
                            )
                        }
                    }
                    if (Announcer.inQuietHours(
                            androidx.compose.ui.platform.LocalContext.current,
                        )
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "🌙 " + tr("It is quiet time right now, so warnings are paused."),
                            style = MaterialTheme.typography.labelSmall,
                            color = WarnAmber,
                        )
                    }
                }
            }

            // ------------------------------------------------------------ what gets said
            PanelCard(Modifier.fillMaxWidth()) {
              Column(Modifier.fillMaxWidth()) {
                Text(
                    tr("What CleanSweep may say"),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
                )
                VoiceSwitch(
                    title = tr("Battery low"),
                    subtitle = tr("Under %s%%, while the phone is running on the battery.")
                        .format(Announcer.LOW_PERCENT),
                    icon = Icons.Outlined.BatteryAlert,
                    tint = DangerRed,
                    checked = state.voiceBatteryLow,
                    onCheckedChange = { vm.setVoiceEvent(Announcer.Event.BATTERY_LOW, it) },
                )
                VoiceSwitch(
                    title = tr("Battery full"),
                    subtitle = tr("When the charge reaches 100% — a reminder to unplug."),
                    icon = Icons.Outlined.BatteryFull,
                    tint = GoodGreen,
                    checked = state.voiceBatteryFull,
                    onCheckedChange = { vm.setVoiceEvent(Announcer.Event.BATTERY_FULL, it) },
                )
                VoiceSwitch(
                    title = tr("Running hot"),
                    subtitle = tr("Battery above %s°C or the processor above %s°C.")
                        .format(Announcer.HOT_BATTERY_C.toInt(), Announcer.HOT_CPU_C.toInt()),
                    icon = Icons.Outlined.Thermostat,
                    tint = WarnAmber,
                    checked = state.voiceOverheat,
                    onCheckedChange = { vm.setVoiceEvent(Announcer.Event.OVERHEAT, it) },
                )
                VoiceSwitch(
                    title = tr("Charging started"),
                    subtitle = tr("A short word when the charger goes in — off keeps plug-ins silent."),
                    icon = Icons.Outlined.Bolt,
                    tint = AccentCyan,
                    checked = state.voiceCharging,
                    onCheckedChange = { vm.setVoiceEvent(Announcer.Event.CHARGING, it) },
                )
                VoiceSwitch(
                    title = tr("Daily brief"),
                    subtitle = tr("One short spoken summary a day, with the details in a notification."),
                    icon = Icons.Outlined.RecordVoiceOver,
                    tint = AccentCyan,
                    checked = state.voiceDaily,
                    onCheckedChange = { vm.setVoiceEvent(Announcer.Event.DAILY, it) },
                )
                VoiceSwitch(
                    title = tr("Storage nearly full"),
                    subtitle = tr("Under 1 GB free — the warning that gets worse the longer it waits."),
                    icon = Icons.Outlined.SdStorage,
                    tint = WarnAmber,
                    checked = state.voiceStorageLow,
                    onCheckedChange = { vm.setVoiceEvent(Announcer.Event.STORAGE_LOW, it) },
                )
                VoiceSwitch(
                    title = tr("Charger removed early"),
                    subtitle = tr("Unplugged below %s%%, and only after the hour you set below.")
                        .format(Announcer.UNPLUG_BEFORE_PERCENT),
                    icon = Icons.Outlined.PowerOff,
                    tint = WarnAmber,
                    checked = state.voiceUnplugged,
                    onCheckedChange = { vm.setVoiceEvent(Announcer.Event.UNPLUGGED_EARLY, it) },
                )
                VoiceSwitch(
                    title = tr("Charger connected but not charging"),
                    subtitle = tr("A worn cable or weak charger shows as plugged in and delivers nothing."),
                    icon = Icons.Outlined.ElectricalServices,
                    tint = WarnAmber,
                    checked = state.voiceChargerIdle,
                    onCheckedChange = { vm.setVoiceEvent(Announcer.Event.CHARGER_IDLE, it) },
                )
                VoiceSwitch(
                    title = tr("Battery health looks worn"),
                    subtitle = tr("Said at most once a week, and only when the kernel reports the real capacity."),
                    icon = Icons.Outlined.Battery3Bar,
                    tint = WarnAmber,
                    checked = state.voiceBatteryHealth,
                    onCheckedChange = { vm.setVoiceEvent(Announcer.Event.BATTERY_HEALTH, it) },
                )
                VoiceSwitch(
                    title = tr("Wi-Fi ⇄ mobile data switches"),
                    subtitle = tr("Off by default: a phone that hops networks would otherwise talk all day."),
                    icon = Icons.Outlined.SwapHoriz,
                    tint = AccentCyan,
                    checked = state.voiceNetworkChange,
                    onCheckedChange = { vm.setVoiceEvent(Announcer.Event.NETWORK_CHANGE, it) },
                )
                VoiceSwitch(
                    title = tr("New device joined my Wi-Fi"),
                    subtitle = tr("Uses the network scan; spoken only when it is on and you run a scan."),
                    icon = Icons.Outlined.Devices,
                    tint = AccentCyan,
                    checked = state.voiceNewDevice,
                    onCheckedChange = { vm.setVoiceEvent(Announcer.Event.NEW_DEVICE, it) },
                )
                VoiceSwitch(
                    title = tr("Read AI answers aloud"),
                    subtitle = tr("Speak the answer when you ask the assistant something."),
                    icon = Icons.Outlined.RecordVoiceOver,
                    tint = AccentViolet,
                    checked = state.voiceAnswers,
                    onCheckedChange = { vm.setVoiceEvent(Announcer.Event.ANSWER, it) },
                )
              }
            }

            // ------------------------------------------------------------ the daily check
            PanelCard(Modifier.fillMaxWidth()) {
              Column(Modifier.fillMaxWidth()) {
                VoiceSwitch(
                    title = tr("Daily full check"),
                    subtitle = tr(
                        "Once a day CleanSweep reads the battery, temperature, storage, junk and " +
                            "security, asks the AI for a two-sentence summary when a key is saved, " +
                            "speaks it and posts the full report."
                    ),
                    checked = state.dailyScanOn,
                    onCheckedChange = { vm.setDailyScanOn(it) },
                )
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        tr("Early-unplug reminders only after"),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 6, 7, 8, 9).forEach { hour ->
                            ModeChipExtra(
                                label = "%d:00 AM".format(hour),
                                selected = state.voiceUnplugStart == hour,
                                onClick = { vm.setUnplugStartHour(hour) },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        tr("Run it at"),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(9 to "9:00 AM", 13 to "1:00 PM", 18 to "6:00 PM", 20 to "8:00 PM", 21 to "9:00 PM").forEach { (hour, label) ->
                            ModeChipExtra(
                                label = label,
                                selected = state.dailyHour == hour,
                                onClick = { vm.setDailyHour(hour) },
                            )
                        }
                    }
                }
                if (state.lastBrief.isNotBlank()) {
                    Text(
                        state.lastBrief,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
              }
            }

            Text(
                tr(
                    "Privacy: the microphone is used only when you tap the mic button in the " +
                        "assistant, the phone's own recogniser turns your words into text, and " +
                        "CleanSweep never records or keeps audio. Voice announcements are made on " +
                        "the phone and can be switched off here at any time."
                ),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun VoiceSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    tint: Color = AccentCyan,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF03202B),
                checkedTrackColor = AccentCyan,
            ),
        )
    }
}

@Composable
private fun VoiceChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Icon(icon, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text(label, color = AccentCyan, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    }
}

/** Hour stepper: −1 / +1 with the hour in 12-hour AM/PM format. */
@Composable
private fun HourPicker(value: Int, onPick: (Int) -> Unit) {
    val h = if (value == 0 || value == 12) 12 else value % 12
    val ampm = if (value < 12) "AM" else "PM"
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onPick((value + 23) % 24) }) {
            Text("−", style = MaterialTheme.typography.titleMedium, color = AccentCyan)
        }
        Text(
            "$h:00 $ampm",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = TextPrimary,
        )
        TextButton(onClick = { onPick((value + 1) % 24) }) {
            Text("+", style = MaterialTheme.typography.titleMedium, color = AccentCyan)
        }
    }
}

@Composable
private fun ModeChipExtra(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) AccentCyan else SurfaceC)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        color = if (selected) Color(0xFF03202B) else TextPrimary,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
    )
}
