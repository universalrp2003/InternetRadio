package com.universalrp.cleansweep.ui

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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
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
import com.universalrp.cleansweep.Screen
import com.universalrp.cleansweep.UiState
import com.universalrp.cleansweep.data.formatBytes
import com.universalrp.cleansweep.ui.theme.AccentCyan
import com.universalrp.cleansweep.ui.theme.SurfaceHigh
import com.universalrp.cleansweep.ui.theme.TextPrimary
import com.universalrp.cleansweep.ui.theme.TextSecondary
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
            IconButton(onClick = { vm.navigate(Screen.HOME) }) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Home", tint = TextSecondary)
            }
            Text(
                "Settings",
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
            PanelCard(Modifier.fillMaxWidth()) {
                SettingSwitch(
                    title = "Scan hidden folders",
                    subtitle = "Look inside folders starting with “.” (more junk, slightly slower)",
                    checked = settings.includeHidden,
                    onCheckedChange = { vm.setIncludeHidden(it) },
                )
            }

            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "Duplicate detection minimum size",
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
                    Text(
                        "Large file threshold",
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
                    Text(
                        "Old downloads age",
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

            PanelCard(Modifier.fillMaxWidth()) {
                SettingSwitch(
                    title = "Only flag APKs of installed apps",
                    subtitle = "Keeps installers for apps you haven't installed yet",
                    checked = settings.apkOnlyInstalled,
                    onCheckedChange = { vm.setApkInstalledOnly(it) },
                )
            }

            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Protected folders",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "These folders are never scanned. You can type a folder like “WhatsApp” or “DCIM/Camera”.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newExclusion,
                            onValueChange = { newExclusion = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("e.g. WhatsApp", color = TextSecondary) },
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
                            Text("Add")
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
