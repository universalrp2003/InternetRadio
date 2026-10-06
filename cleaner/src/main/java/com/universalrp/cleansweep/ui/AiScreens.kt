package com.universalrp.cleansweep.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.universalrp.cleansweep.MainViewModel
import com.universalrp.cleansweep.Screen
import com.universalrp.cleansweep.UiState
import com.universalrp.cleansweep.ai.AiProvider
import com.universalrp.cleansweep.ai.AiSettings
import com.universalrp.cleansweep.ui.theme.AccentCyan
import com.universalrp.cleansweep.ui.theme.AccentViolet
import com.universalrp.cleansweep.ui.theme.DangerRed
import com.universalrp.cleansweep.ui.theme.GoodGreen
import com.universalrp.cleansweep.ui.theme.OutlineC
import com.universalrp.cleansweep.ui.theme.SurfaceHigh
import com.universalrp.cleansweep.ui.theme.TextPrimary
import com.universalrp.cleansweep.ui.theme.TextSecondary
import com.universalrp.cleansweep.ui.theme.WarnAmber

/* ================================================================ AI settings */

@Composable
fun AiSettingsScreen(state: UiState, vm: MainViewModel) {
    var keyVisible by remember { mutableStateOf(false) }
    var keyDraft by remember(state.aiConfig) { mutableStateOf(state.aiConfig.apiKey) }
    var modelDraft by remember(state.aiConfig) { mutableStateOf(state.aiConfig.model) }
    var baseDraft by remember(state.aiConfig) { mutableStateOf(state.aiConfig.baseUrl) }

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
            IconButton(onClick = { vm.navigate(Screen.HEALTH) }) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = TextSecondary)
            }
            Text(
                "AI analysis settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                PanelCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.SmartToy, contentDescription = null, tint = AccentViolet)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Who should analyse the report?",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "CleanSweep builds a report on the phone (hardware, battery, security " +
                                "findings, apps) and sends it straight to the service you pick. There is " +
                                "no CleanSweep server in between and nothing is stored anywhere else.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                        Spacer(Modifier.height(12.dp))
                        AiProvider.entries.forEach { provider ->
                            ProviderRow(
                                provider = provider,
                                selected = state.aiConfig.provider == provider,
                                onClick = { vm.selectAiProvider(provider) },
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }

            item {
                PanelCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.VpnKey, contentDescription = null, tint = AccentCyan)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Key and model",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            state.aiConfig.provider.signupHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )

                        if (state.aiConfig.provider.needsKey) {
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = keyDraft,
                                onValueChange = { keyDraft = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("API key") },
                                visualTransformation = if (keyVisible) {
                                    androidx.compose.ui.text.input.VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                trailingIcon = {
                                    TextButton(onClick = { keyVisible = !keyVisible }) {
                                        Text(
                                            if (keyVisible) "Hide" else "Show",
                                            color = AccentCyan,
                                        )
                                    }
                                },
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                AiSettings.keyStorageNote(),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                        }

                        if (state.aiConfig.provider != AiProvider.FREE) {
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = modelDraft,
                                onValueChange = { modelDraft = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Model") },
                                placeholder = { Text(state.aiConfig.provider.defaultModel.ifBlank { "model name" }) },
                            )
                        }

                        if (state.aiConfig.provider == AiProvider.CUSTOM) {
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = baseDraft,
                                onValueChange = { baseDraft = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Base URL") },
                                placeholder = { Text("http://192.168.1.5:11434/v1") },
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        SwitchRow(
                            label = "Send installed app names",
                            detail = "Gives specific advice (\"this one is bloatware\"). Off = totals only.",
                            checked = state.aiConfig.includeAppNames,
                            onChange = { vm.setAiIncludeAppNames(it) },
                        )
                        SwitchRow(
                            label = "Include network summary",
                            detail = "Wi-Fi name, signal, how many devices answered the scan.",
                            checked = state.aiConfig.includeNetwork,
                            onChange = { vm.setAiIncludeNetwork(it) },
                        )

                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = {
                                vm.saveAiConfig(
                                    apiKey = keyDraft,
                                    model = modelDraft,
                                    baseUrl = baseDraft,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentCyan,
                                contentColor = Color(0xFF03202B),
                            ),
                        ) {
                            Text("Save settings", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                vm.saveAiConfig(
                                    apiKey = keyDraft,
                                    model = modelDraft,
                                    baseUrl = baseDraft,
                                )
                                vm.testAiConnection()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.aiTestBusy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SurfaceHigh,
                                contentColor = TextPrimary,
                            ),
                        ) {
                            if (state.aiTestBusy) {
                                CircularProgressIndicator(
                                    color = AccentCyan,
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Test connection", fontWeight = FontWeight.Bold)
                        }
                        state.aiTestResult?.let { result ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                result,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (result.startsWith("OK")) GoodGreen else DangerRed,
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Never sent: your files, photos, messages, contacts, file paths or " +
                                    "the API key itself. Only the report text goes, and only when you " +
                                    "tap Analyse.",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            }

            item {
                GradientButton(
                    text = "Analyse my phone now",
                    icon = Icons.Outlined.SmartToy,
                    onClick = { vm.runAiAnalysis() },
                    enabled = state.aiConfig.ready,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ProviderRow(provider: AiProvider, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) AccentCyan.copy(alpha = 0.18f) else SurfaceHigh)
            .border(1.dp, if (selected) AccentCyan else OutlineC, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            provider.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) AccentCyan else TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (provider.needsKey) "needs key" else "no signup",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF03202B),
                checkedTrackColor = AccentCyan,
            ),
        )
    }
}

/* ==================================================================== AI report */

@Composable
fun AiReportScreen(state: UiState, vm: MainViewModel) {
    val context = LocalContext.current
    var showPrompt by remember { mutableStateOf(false) }

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
            IconButton(onClick = { vm.navigate(Screen.HEALTH) }) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = TextSecondary)
            }
            Text(
                "AI analysis",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { vm.runAiAnalysis() }, enabled = !state.aiBusy) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Analyse again", tint = TextSecondary)
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                PanelCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            when {
                                state.aiBusy -> "Thinking…"
                                state.aiAnswer != null -> "Analysis ready"
                                state.aiError != null -> "Could not analyse"
                                else -> "Ready when you are"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Provider: ${state.aiUsedProvider.ifBlank { state.aiConfig.provider.label }}" +
                                " • app names: " +
                                if (state.aiConfig.includeAppNames) "included" else "not included",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                        if (state.aiBusy) {
                            Spacer(Modifier.height(12.dp))
                            CircularProgressIndicator(color = AccentCyan)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Large reports can take 20–60 seconds on free services.",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                        }
                        if (!state.aiConfig.ready) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "No AI provider is set up yet (or the free option is the only one ready). " +
                                    "Tap AI settings to add a key.",
                                style = MaterialTheme.typography.bodySmall,
                                color = WarnAmber,
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = { vm.navigate(Screen.AI_SETTINGS) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentCyan,
                                    contentColor = Color(0xFF03202B),
                                ),
                            ) { Text("Open AI settings") }
                        }
                    }
                }
            }

            state.aiError?.let { error ->
                item {
                    PanelCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Warning, contentDescription = null, tint = DangerRed)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "What went wrong",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(error, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Spacer(Modifier.height(10.dp))
                            Row {
                                TextButton(onClick = { vm.runAiAnalysis() }) {
                                    Text("Try again", color = AccentCyan)
                                }
                                TextButton(onClick = { vm.navigate(Screen.AI_SETTINGS) }) {
                                    Text("AI settings", color = TextSecondary)
                                }
                            }
                        }
                    }
                }
            }

            state.aiAnswer?.let { answer ->
                item {
                    PanelCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                answer,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                            )
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SmallAction("Copy", Icons.Outlined.ContentCopy) {
                                    copyToClipboard(context, answer)
                                }
                                SmallAction("Share", Icons.Outlined.Share) {
                                    share(context, answer)
                                }
                            }
                        }
                    }
                }
            }

            item {
                PanelCard(
                    Modifier
                        .fillMaxWidth()
                        .clickable { showPrompt = !showPrompt }
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            if (showPrompt) "Hide what was sent" else "See exactly what was sent",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AccentCyan,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (showPrompt) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                state.aiPromptPreview.ifBlank { "Nothing has been sent yet." },
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "AI answers can be wrong. Nothing is ever deleted automatically — the AI only " +
                        "explains and suggests, and you decide.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun SmallAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
    }
}

private fun copyToClipboard(context: Context, text: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("CleanSweep AI report", text))
    } catch (e: Exception) {
        // Nothing sensible to do if the clipboard is unavailable.
    }
}

private fun share(context: Context, text: String) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, "CleanSweep phone health report")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        // Ignored.
    }
}
