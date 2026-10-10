package com.universalrp.cleansweep.ui

import androidx.compose.ui.draw.clip

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.universalrp.cleansweep.MainViewModel
import com.universalrp.cleansweep.data.AppLang
import com.universalrp.cleansweep.data.JunkKind
import com.universalrp.cleansweep.data.Lang
import com.universalrp.cleansweep.data.tr
import com.universalrp.cleansweep.Screen
import com.universalrp.cleansweep.UiState
import com.universalrp.cleansweep.data.formatBytes
import com.universalrp.cleansweep.ui.theme.AccentCyan
import com.universalrp.cleansweep.ui.theme.SurfaceHigh
import com.universalrp.cleansweep.ui.theme.TextPrimary
import com.universalrp.cleansweep.ui.theme.TextSecondary
import com.universalrp.cleansweep.ui.theme.GoodGreen
import com.universalrp.cleansweep.ui.theme.WarnAmber
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(state: UiState, vm: MainViewModel) {
    val settings = state.settings
    var newExclusion by remember { mutableStateOf("") }

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
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Home", tint = TextSecondary)
            }
            Text(tr("Settings"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ------------------------------------------------------- voice (v2.4)
            PanelCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.navigate(Screen.VOICE) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (state.voiceOn) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff,
                        contentDescription = null,
                        tint = if (state.voiceOn) AccentCyan else TextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            tr("Voice & daily watch"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (state.voiceOn) {
                                tr("Warnings spoken out loud, quiet hours, and the daily brief.")
                            } else {
                                tr("Voice is off. Nothing will be spoken, at any hour.")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                    }
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TextSecondary)
                }
            }

            // ------------------------------------------------------- language
            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        tr("Language"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        Lang.languageNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeChip(
                            label = "English",
                            selected = state.lang == AppLang.EN,
                            onClick = { vm.setLanguage(AppLang.EN) },
                        )
                        ModeChip(
                            label = "தமிழ்",
                            selected = state.lang == AppLang.TA,
                            onClick = { vm.setLanguage(AppLang.TA) },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        tr("App name") + ": " + Lang.appName(),
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentCyan,
                    )
                }
            }

            PanelCard(Modifier.fillMaxWidth()) {
                SettingSwitch(
                    title = tr("Sound effects"),
                    subtitle = tr("Play a chime when scanning and cleaning"),
                    checked = state.soundsEnabled,
                    onCheckedChange = { vm.setSoundsEnabled(it) },
                )
            }

            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(tr("AI analysis (optional)"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        when {
                            !state.aiConfig.ready -> "No provider set up yet — tap below"
                            state.aiConfig.provider.needsKey -> "Key saved • ${state.aiConfig.engineLabel}"
                            else -> state.aiConfig.engineLabel
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    if (state.aiStatusChecking || state.aiStatusText != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (state.aiStatusChecking) "Checking ${state.aiConfig.engineLabel}…"
                            else state.aiStatusText ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.aiStatusOk || state.aiStatusChecking) GoodGreen else WarnAmber,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { vm.navigate(Screen.AI_SETTINGS) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentCyan,
                            contentColor = Color(0xFF03202B),
                        ),
                    ) {
                        Text(tr("Open AI settings"), fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Replaces the old duplicate "Detailed assistant answers" row: the user asked
            // for an online/offline choice there instead.
            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(tr("Assistant answers"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "On-device: private, instant, works offline. Online: your AI provider " +
                            "explains in more depth (needs the key in AI settings).",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeChip(
                            label = tr("On-device"),
                            selected = !state.assistantOnline,
                            onClick = { vm.setAssistantOnline(false) },
                        )
                        ModeChip(
                            label = tr("Online AI"),
                            selected = state.assistantOnline,
                            enabled = state.aiConfig.ready,
                            onClick = { vm.setAssistantOnline(true) },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            !state.assistantOnline ->
                                "Answers come from the on-device engine — no internet, nothing sent."
                            state.aiConfig.ready ->
                                "Answers will come from ${state.aiConfig.engineLabel}."
                            else ->
                                "No AI provider is set up, so answers fall back to the on-device engine."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.assistantOnline && state.aiConfig.ready) GoodGreen else TextSecondary,
                    )
                }
            }

            // Autostart permission helper for Xiaomi, Oppo, Vivo, Samsung phones
            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                tr("Auto-start after phone restart"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                tr("Xiaomi / Oppo / Vivo require enabling Autostart in Security settings so background monitoring and widgets restore after reboot."),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { vm.openAutoStartSettings() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentCyan.copy(alpha = 0.2f),
                            contentColor = AccentCyan,
                        ),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(tr("Open Autostart Settings"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            PanelCard(Modifier.fillMaxWidth()) {
                Column {
                    SettingSwitch(
                        title = tr("Charging status in the status bar"),
                        subtitle = tr("Ongoing notification with charging watts, battery % and time to full"),
                        checked = state.chargeMonitor,
                        onCheckedChange = { vm.setChargeMonitor(it) },
                    )
                    SettingSwitch(
                        title = tr("Background battery & health monitor"),
                        subtitle = tr(
                            "Keeps low-priority background monitoring active with a persistent notification " +
                                "and an instant Stop button. Note: OEM power savers may restrict background execution, " +
                                "and no app survives an Android force-stop."
                        ),
                        checked = state.persistentMonitor,
                        onCheckedChange = { vm.setPersistentMonitor(it) },
                    )
                }
            }

            // The little watt reading the user asked to see next to the clock. Android has
            // no API for a third-party status-bar item, so CleanSweep draws this itself and
            // says so plainly.
            PanelCard(Modifier.fillMaxWidth()) {
                Column {
                    SettingSwitch(
                        title = tr("Status-bar data and watts"),
                        subtitle = tr("Watts only while connected; data and D/U lights return with background monitoring"),
                        checked = state.statusPill,
                        onCheckedChange = { vm.setStatusPill(it) },
                    )
                    if (state.statusPill && !state.statusPillAllowed) {
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                            Text(
                                "Android needs “Display over other apps” before anything can be " +
                                    "drawn there. Allow it, come back, and the reading appears " +
                                    "beside the front camera.",
                                style = MaterialTheme.typography.labelSmall,
                                color = WarnAmber,
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { vm.openOverlaySettings() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = WarnAmber,
                                    contentColor = Color(0xFF2B1D02),
                                ),
                            ) {
                                Text(tr("Allow display over other apps"), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (state.statusPill && state.statusPillAllowed) {
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                            Text(
                                tr("Ready. Watts only while connected; normal data and D/U lights return after unplugging when background monitoring is enabled. Touch-through except while positioning it."),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                tr("Where it sits") + " — " + tr(
                                    "your phone writes VoLTE, VPN, the carrier name and the battery " +
                                        "percentage up there, so move the reading wherever it is free."
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = WarnAmber,
                            )
                            ReadingMovePad(
                                onMove = { dx, dy -> vm.moveStatusPill(dx, dy) },
                                onReset = { vm.resetStatusPill() },
                                note = tr("Each tap moves it a little; “Auto” puts it back beside the camera."),
                                dragging = state.pillDragging,
                                onToggleDrag = { vm.togglePillDrag() },
                            )
                        }
                    }
                }
            }

            PanelCard(Modifier.fillMaxWidth()) {
                SettingSwitch(
                    title = tr("Detailed assistant answers"),
                    subtitle = tr("Off = the on-device assistant replies in one short line"),
                    checked = state.assistantVerbose,
                    onCheckedChange = { vm.setAssistantVerbose(it) },
                )
            }

            // "In quick cleaning do exactly what I select… after scan don't select anything,
            // user must select and clean — and give an option to choose defaults."
            // CleanSweep now ticks nothing by itself; these switches are the optional shortcut.
            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(tr("Preselect after a scan"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Everything is unticked after a scan, so you always choose what goes. " +
                            "Switch on the categories you are happy to have ticked for you.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(6.dp))
                    JunkKind.entries.forEach { kind ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    kind.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    if (kind.reviewOnly) {
                                        "Review list — may hold your own files"
                                    } else {
                                        "Safe to sweep: ${kind.description.lowercase().take(58)}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (kind.reviewOnly) WarnAmber else TextSecondary,
                                )
                            }
                            Switch(
                                checked = kind in settings.defaultSelected,
                                onCheckedChange = { vm.setDefaultSelected(kind, it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF03202B),
                                    checkedTrackColor = AccentCyan,
                                ),
                            )
                        }
                    }
                }
            }

            PanelCard(Modifier.fillMaxWidth()) {
                SettingSwitch(
                    title = tr("Scan hidden folders"),
                    subtitle = tr("Look inside folders starting with “.” (more junk, slightly slower)"),
                    checked = settings.includeHidden,
                    onCheckedChange = { vm.setIncludeHidden(it) },
                )
            }

            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(tr("Duplicate detection minimum size"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Files smaller than ${settings.dupMinBytes.formatBytes()} are not checked for duplicates",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Slider(
                        value = (settings.dupMinBytes / 1024f),
                        onValueChange = { vm.setDupMinKb(it.roundToInt()) },
                        valueRange = 50f..2048f,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentCyan,
                            activeTrackColor = AccentCyan,
                        ),
                    )
                }
            }

            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(tr("Large file threshold"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Files bigger than ${settings.largeThresholdBytes.formatBytes()} are listed for review",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Slider(
                        value = settings.largeThresholdBytes / (1024f * 1024f),
                        onValueChange = { vm.setLargeMb(it.roundToInt()) },
                        valueRange = 50f..1024f,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentCyan,
                            activeTrackColor = AccentCyan,
                        ),
                    )
                }
            }

            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(tr("Old downloads age"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Download folder files older than ${settings.oldDownloadDays} days are suggested for removal",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Slider(
                        value = settings.oldDownloadDays.toFloat(),
                        onValueChange = { vm.setOldDays(it.roundToInt()) },
                        valueRange = 7f..180f,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentCyan,
                            activeTrackColor = AccentCyan,
                        ),
                    )
                }
            }

            PermissionHubCard(vm)

            PanelCard(Modifier.fillMaxWidth()) {
                SettingSwitch(
                    title = tr("Only flag APKs of installed apps"),
                    subtitle = tr("Keeps installers for apps you haven't installed yet"),
                    checked = settings.apkOnlyInstalled,
                    onCheckedChange = { vm.setApkInstalledOnly(it) },
                )
            }

            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(tr("Protected folders"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(tr("These folders are never scanned. You can type a folder like “WhatsApp” or “DCIM/Camera”."),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newExclusion,
                            onValueChange = { newExclusion = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(tr("e.g. WhatsApp"), color = TextSecondary) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = TextSecondary.copy(alpha = 0.4f),
                                cursorColor = AccentCyan,
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                vm.addExclusion(newExclusion)
                                newExclusion = ""
                            },
                            enabled = newExclusion.isNotBlank(),
                        ) {
                            Text(tr("Add"))
                        }
                    }
                    if (settings.excludedPrefixes.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            settings.excludedPrefixes.sorted().forEach { path ->
                                Row(
                                    Modifier
                                        .background(SurfaceHigh, shape = MaterialTheme.shapes.small)
                                        .clickable { vm.removeExclusion(path) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        path.substringAfterLast('/'),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "Remove",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
private fun ModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Text(
        label,
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) AccentCyan else SurfaceHigh)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        style = MaterialTheme.typography.labelLarge,
        color = when {
            selected -> Color(0xFF03202B)
            enabled -> TextPrimary
            else -> TextSecondary
        },
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
    )
}
