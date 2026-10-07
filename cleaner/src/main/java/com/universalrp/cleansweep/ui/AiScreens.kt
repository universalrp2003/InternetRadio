package com.universalrp.cleansweep.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.CheckCircle
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.universalrp.cleansweep.MainViewModel
import com.universalrp.cleansweep.data.tr
import com.universalrp.cleansweep.Screen
import com.universalrp.cleansweep.UiState
import com.universalrp.cleansweep.ai.AiProvider
import com.universalrp.cleansweep.ai.AiSettings
import com.universalrp.cleansweep.ui.theme.AccentCyan
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
    val context = LocalContext.current
    var keyVisible by remember { mutableStateOf(false) }
    var keyDraft by remember(state.aiConfig) { mutableStateOf(state.aiConfig.apiKey) }
    var modelDraft by remember(state.aiConfig) { mutableStateOf(state.aiConfig.model) }
    var baseDraft by remember(state.aiConfig) { mutableStateOf(state.aiConfig.baseUrl) }
    var showModels by remember { mutableStateOf(false) }

    val provider = state.aiConfig.provider
    val availableModels = (state.aiModels + provider.suggestedModels).distinct()

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
            // ------------------------------- which AI is answering right now (live check)
            item {
                PanelCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (state.aiStatusOk) Icons.Outlined.CheckCircle
                                else Icons.Outlined.Warning,
                                contentDescription = null,
                                tint = if (state.aiStatusOk) GoodGreen else WarnAmber,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (state.aiStatusChecking) "Checking…"
                                    else if (state.aiStatusOk) "Working: ${state.aiEngineLabel}"
                                    else "Not answering yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    state.aiStatusText
                                        ?: if (state.aiConfig.ready) {
                                            "Tested when the app opened and after every change."
                                        } else {
                                            "Pick a provider below — a pasted key selects it for you."
                                        },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = { vm.refreshAiStatus(announce = true) }) {
                                Text("Check now", color = AccentCyan, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = { vm.useAnotherFreeAi() }) {
                                Text(tr("Use a free AI"), color = GoodGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ------------------------------------------------ 1. pick a provider
            item {
                PanelCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        StepHeader(1, "Pick who analyses the report")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Three taps to working AI: pick a provider below, paste its key, tap " +
                                "\"Load models\" and choose one. CleanSweep talks to the provider directly — " +
                                "there is no CleanSweep server, and nothing is sent until you tap Analyse.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                        Spacer(Modifier.height(12.dp))
                        AiProvider.entries.forEach { entry ->
                            ProviderRow(
                                provider = entry,
                                selected = provider == entry,
                                savedKey = AiSettings.hasSavedKey(context, entry),
                                onClick = { vm.selectAiProvider(entry) },
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Already paying for Gemini / ChatGPT Pro? Those subscriptions do not " +
                                "include API access — the key is separate and has its own free tier. " +
                                "Google sign-in inside a sideloaded app would need an OAuth project " +
                                "registered to this APK's signing key, so the key route below is the one " +
                                "that works for everyone.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                    }
                }
            }

            // --------------------------------------------------- 2. key and model
            item {
                // ---- the language the AI answers in (separate from the menu language) ----
                PanelCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            tr("Answer language"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            tr(
                                "Which language the AI writes in. Separate from the menu language — " +
                                    "you can read English menus and still get Tamil answers."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ModeChip2(
                                label = tr("Same as I type"),
                                selected = state.aiAnswerLanguage == "auto",
                                onClick = { vm.setAnswerLanguage("auto") },
                            )
                            ModeChip2(
                                label = "English",
                                selected = state.aiAnswerLanguage == "en",
                                onClick = { vm.setAnswerLanguage("en") },
                            )
                            ModeChip2(
                                label = "தமிழ்",
                                selected = state.aiAnswerLanguage == "ta",
                                onClick = { vm.setAnswerLanguage("ta") },
                            )
                        }
                    }
                }

                PanelCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        StepHeader(2, "Paste the key")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            provider.signupHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                        if (provider.signupUrl.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { openUrl(context, provider.signupUrl) }) {
                                Icon(
                                    Icons.Outlined.OpenInNew,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Open ${provider.signupUrl.substringAfter("://").substringBefore("/")}",
                                    color = AccentCyan)
                            }
                        }

                        if (provider.needsKey) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = keyDraft,
                                onValueChange = { keyDraft = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("API key") },
                                visualTransformation = if (keyVisible) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                trailingIcon = {
                                    TextButton(onClick = { keyVisible = !keyVisible }) {
                                        Text(if (keyVisible) "Hide" else "Show", color = AccentCyan)
                                    }
                                },
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                AiSettings.keyStorageNote(),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                        } else {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "No key needed. The app asks public endpoints in turn (Kilo first, " +
                                    "then Pollinations, then OVHcloud). They are shared with everyone, so " +
                                    "they can be slow or busy at peak times — a free Gemini key is more " +
                                    "reliable and takes about a minute to get.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AccentCyan,
                            )
                        }

                        if (provider == AiProvider.CUSTOM) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = baseDraft,
                                onValueChange = { baseDraft = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Base URL") },
                                placeholder = { Text("http://192.168.1.5:11434/v1") },
                            )
                        }

                        Spacer(Modifier.height(14.dp))
                        StepHeader(3, "Choose the model")
                        Spacer(Modifier.height(6.dp))
                        if (provider == AiProvider.FREE) {
                            Text(
                                "Each free endpoint uses its own model automatically: " +
                                    "kilo-auto/free → openai → gpt-oss-120b.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        } else {
                            Text(
                                "Tap \"Load models\" to ask the provider which models your key can use. " +
                                    "Model names change: a retired one answers with a 410 error, which is " +
                                    "why the list is worth loading instead of typing.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        vm.saveAiConfig(keyDraft, modelDraft, baseDraft)
                                        vm.loadAiModels()
                                        showModels = true
                                    },
                                    enabled = !state.aiModelsBusy,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SurfaceHigh,
                                        contentColor = TextPrimary,
                                    ),
                                ) {
                                    if (state.aiModelsBusy) {
                                        CircularProgressIndicator(
                                            color = AccentCyan,
                                            modifier = Modifier.size(15.dp),
                                            strokeWidth = 2.dp,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    } else {
                                        Icon(
                                            Icons.Outlined.Refresh,
                                            contentDescription = null,
                                            tint = AccentCyan,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(tr("Load models"), fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = {
                                        vm.saveAiConfig(keyDraft, modelDraft, baseDraft)
                                        vm.testAiConnection()
                                    },
                                    enabled = !state.aiTestBusy,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SurfaceHigh,
                                        contentColor = TextPrimary,
                                    ),
                                ) {
                                    Text(tr("Test"), fontWeight = FontWeight.Bold)
                                }
                            }
                            state.aiModelsError?.let { error ->
                                Spacer(Modifier.height(8.dp))
                                Text(error, style = MaterialTheme.typography.labelSmall, color = DangerRed)
                            }

                            Spacer(Modifier.height(10.dp))
                            ModelChips(
                                models = availableModels,
                                selected = modelDraft.ifBlank { state.aiConfig.resolvedModel },
                                onSelect = { chosen ->
                                    modelDraft = chosen
                                    vm.saveAiConfig(keyDraft, chosen, baseDraft)
                                },
                            )

                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = modelDraft,
                                onValueChange = { modelDraft = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Model (or type one)") },
                                placeholder = {
                                    Text(provider.defaultModel.ifBlank { "model name" })
                                },
                            )
                        }

                        state.aiTestResult?.let { result ->
                            Spacer(Modifier.height(10.dp))
                            Text(
                                result,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (result.startsWith("OK")) GoodGreen else DangerRed,
                            )
                        }

                        Spacer(Modifier.height(14.dp))
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
                            onClick = { vm.saveAiConfig(keyDraft, modelDraft, baseDraft) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentCyan,
                                contentColor = Color(0xFF03202B),
                            ),
                        ) {
                            Text(tr("Save settings"), fontWeight = FontWeight.Bold)
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
                                "Never sent: your files, photos, messages, contacts, file paths or the " +
                                    "API key itself. Only the report text goes, and only when you tap Analyse.",
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
private fun StepHeader(number: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(AccentCyan),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$number",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF03202B),
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ProviderRow(
    provider: AiProvider,
    selected: Boolean,
    savedKey: Boolean,
    onClick: () -> Unit,
) {
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
        // Shows which providers already have a key saved, so switching between them is
        // obviously free of any re-typing.
        Text(
            when {
                savedKey -> "key saved ✓"
                provider.needsKey -> "needs key"
                else -> "no signup"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (savedKey) GoodGreen else TextSecondary,
        )
    }
}

@Composable
private fun ModelChips(models: List<String>, selected: String, onSelect: (String) -> Unit) {
    if (models.isEmpty()) return
    Column {
        Text(
            "Suggested for this provider",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            models.forEach { model ->
                Text(
                    model,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (model == selected) AccentCyan.copy(alpha = 0.22f) else SurfaceHigh
                        )
                        .border(
                            1.dp,
                            if (model == selected) AccentCyan else OutlineC,
                            RoundedCornerShape(20.dp),
                        )
                        .clickable { onSelect(model) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (model == selected) AccentCyan else TextPrimary,
                )
            }
        }
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
            .navigationBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.goBack() }) {
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
            // ------------------------------------------- local vitals, side by side
            item {
                Text(
                    "Your phone right now",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "These numbers are read from your phone, not from the AI. Green = healthy, " +
                        "amber = worth watching, red = do something.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }

            item {
                state.health?.let { health ->
                    VitalsGrid(health, state.securityReport?.score, state.storage)
                } ?: PanelCard(Modifier.fillMaxWidth()) {
                    Text(tr("Reading the sensors…"), Modifier.padding(16.dp), color = TextSecondary)
                }
            }

            item {
                PanelCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            when {
                                state.aiBusy -> "The AI is reading your report…"
                                state.aiAnswer != null -> "What the AI says"
                                state.aiError != null -> "Could not analyse"
                                else -> "Ready when you are"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        val who = state.aiUsedProvider.ifBlank { state.aiConfig.engineLabel }
                        val modelText = state.aiUsedModel
                        Text(
                            "Answered by: $who" + (if (modelText.isNotBlank()) " · $modelText" else ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                        val timingText = if (state.aiUsedMs > 0L) {
                            "Took %.1f s • ".format(state.aiUsedMs / 1000.0)
                        } else {
                            ""
                        }
                        Text(
                            timingText +
                                "app names: " +
                                (if (state.aiConfig.includeAppNames) "included" else "not included") +
                                " • network: " +
                                (if (state.aiConfig.includeNetwork) "included" else "not included"),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                        state.aiFallbackNote?.let { note ->
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Your provider did not answer first, so a free AI replied. " +
                                    "Reason: $note",
                                style = MaterialTheme.typography.labelSmall,
                                color = WarnAmber,
                            )
                        }
                        if (state.aiBusy) {
                            Spacer(Modifier.height(12.dp))
                            CircularProgressIndicator(color = AccentCyan)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Big reports take 20–60 seconds on a free service.",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                        }
                        if (!state.aiConfig.ready) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "No AI provider is set up. The free option needs no key at all; Gemini " +
                                    "is the most reliable free key.",
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
                            ) { Text(tr("Set up AI")) }
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
                                Text(tr("What went wrong"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(error, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Spacer(Modifier.height(10.dp))
                            Row {
                                TextButton(onClick = { vm.runAiAnalysis() }) {
                                    Text(tr("Try again"), color = AccentCyan)
                                }
                                TextButton(onClick = { vm.navigate(Screen.AI_SETTINGS) }) {
                                    Text(tr("AI settings"), color = TextSecondary)
                                }
                            }
                        }
                    }
                }
            }

            // ----------------------------------------------- the answer, sectioned
            state.aiAnswer?.let { answer ->
                val sections = splitAnswer(answer)
                sections.forEach { section ->
                    item {
                        AiSectionCard(section)
                    }
                }
                item {
                    PanelCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
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
                    "AI answers can be wrong, and nothing is ever deleted automatically — the AI " +
                        "explains, you decide. The coloured boxes above are measured on your phone and " +
                        "stay true even when the AI is offline.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
        }
    }
}

/** One AI answer split on markdown-style headings so it renders as readable cards. */
private data class AiSection(val heading: String, val lines: List<String>)

private fun splitAnswer(answer: String): List<AiSection> {
    val sections = mutableListOf<AiSection>()
    var heading = "Summary"
    var lines = mutableListOf<String>()
    answer.lines().forEach { raw ->
        val line = raw.trimEnd()
        val trimmed = line.trim()
        val isHeading = trimmed.startsWith("#") ||
            (trimmed.length in 3..60 && trimmed.endsWith(":") && !trimmed.startsWith("*") &&
                !trimmed.startsWith("-") && trimmed.none { it.isDigit() && false })
        when {
            trimmed.startsWith("###") || trimmed.startsWith("##") || trimmed.startsWith("#") -> {
                if (lines.any { it.isNotBlank() }) sections.add(AiSection(heading, lines))
                heading = trimmed.trimStart('#').trim()
                lines = mutableListOf()
            }
            isHeading -> {
                if (lines.any { it.isNotBlank() }) sections.add(AiSection(heading, lines))
                heading = trimmed.trimEnd(':')
                lines = mutableListOf()
            }
            else -> lines.add(line)
        }
    }
    if (lines.any { it.isNotBlank() }) sections.add(AiSection(heading, lines))
    if (sections.isEmpty()) sections.add(AiSection("Summary", answer.lines()))
    return sections
}

@Composable
private fun ModeChip2(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) AccentCyan else SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        color = if (selected) Color(0xFF03202B) else TextPrimary,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun AiSectionCard(section: AiSection) {
    val headingState = headingTone(section.heading)
    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(4.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(headingState)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    section.heading,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = headingState,
                )
            }
            Spacer(Modifier.height(8.dp))
            section.lines.forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    return@forEach
                }
                val bullet = line.startsWith("*") || line.startsWith("-") || line.startsWith("•")
                val cleaned = line.trimStart('*', '-', '•', ' ').replace("**", "")
                val tone = lineTone(cleaned)
                Row(Modifier.padding(vertical = 2.dp)) {
                    Box(
                        Modifier
                            .padding(top = 6.dp)
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(tone)
                    )
                    Spacer(Modifier.width(10.dp))
                    AiText(
                        cleaned,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (tone == TextSecondary) TextPrimary else tone,
                    )
                }
            }
        }
    }
}

/** Headings that promise good news are green, problems are red, the rest cyan. */
private fun headingTone(heading: String): Color {
    val lower = heading.lowercase()
    return when {
        listOf("risk", "problem", "issue", "danger", "warning", "cannot", "clean up", "action").any {
            lower.contains(it)
        } -> DangerRed
        listOf("normal", "leave alone", "good", "healthy", "fine", "safe", "keep").any {
            lower.contains(it)
        } -> GoodGreen
        else -> AccentCyan
    }
}

/** Individual lines get a colour from the words in them, like a traffic light. */
private fun lineTone(text: String): Color {
    val lower = text.lowercase()
    return when {
        listOf(
            "risk", "risky", "malware", "virus", "danger", "critical", "high-risk", "uninstall",
            "revoke", "not needed", "bloatware", "unused", "should be removed", "too high",
            "overheat", "hot", "threat", "suspicious",
        ).any { lower.contains(it) } -> DangerRed
        listOf("watch", "consider", "maybe", "might", "moderate", "amber", "slightly").any {
            lower.contains(it)
        } -> WarnAmber
        listOf("normal", "fine", "healthy", "good", "leave", "safe", "expected", "ok", "no action").any {
            lower.contains(it)
        } -> GoodGreen
        else -> TextSecondary
    }
}

/* --------------------------------------------------------------- vitals grid */

@Composable
private fun VitalsGrid(
    health: com.universalrp.cleansweep.data.HealthSnapshot,
    securityScore: Int?,
    storage: com.universalrp.cleansweep.data.StorageInfo?,
) {
    val battery = health.battery
    val cell = 0.5f

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VitalBox(
                "Battery",
                if (battery.percent >= 0) "${battery.percent}%" else "—",
                battery.statusLabel,
                toneFor(battery.percent, 15f, 35f, invert = true),
                Modifier.weight(cell),
            )
            VitalBox(
                "Battery heat",
                battery.temperatureC?.let { "%.1f °C".format(it) } ?: "Not reported",
                healthGuide("batteryTemp", battery.temperatureC),
                temperatureTone(battery.temperatureC, 38f, 45f),
                Modifier.weight(cell),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VitalBox(
                "CPU heat",
                health.cpuTempC?.let { "%.1f °C".format(it) } ?: "Not reported",
                healthGuide("cpuTemp", health.cpuTempC),
                temperatureTone(health.cpuTempC, 48f, 60f),
                Modifier.weight(cell),
            )
            VitalBox(
                if (battery.charging) "Charging power" else "Power draw",
                battery.powerW?.let { "%.2f W".format(it) } ?: "Not reported",
                healthGuide("power", battery.powerW),
                if (battery.charging) GoodGreen else AccentCyan,
                Modifier.weight(cell),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val ramFraction = if (health.device.totalRamBytes > 0L) {
                (health.device.totalRamBytes - health.device.availableRamBytes).toFloat() /
                    health.device.totalRamBytes.toFloat()
            } else {
                0f
            }
            VitalBox(
                "RAM in use",
                "${(ramFraction * 100).toInt()}%",
                "Android keeps RAM busy on purpose — over 80% is normal on a phone with many apps.",
                if (ramFraction > 0.9f) WarnAmber else GoodGreen,
                Modifier.weight(cell),
            )
            VitalBox(
                "Storage free",
                storage?.let { it.free.formatGb() } ?: "—",
                storage?.let {
                    "%.0f%% of the storage is used.".format(it.usedFraction * 100)
                } ?: "",
                when {
                    storage == null -> TextSecondary
                    storage.usedFraction > 0.92f -> DangerRed
                    storage.usedFraction > 0.8f -> WarnAmber
                    else -> GoodGreen
                },
                Modifier.weight(cell),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VitalBox(
                "Security",
                securityScore?.let { "$it/100" } ?: "Not run yet",
                when {
                    securityScore == null -> "Run the security check for a score."
                    securityScore >= 90 -> "High-permission apps are under control."
                    securityScore >= 75 -> "A couple of settings are worth a look."
                    else -> "Some apps hold permissions they do not need."
                },
                when {
                    securityScore == null -> TextSecondary
                    securityScore >= 90 -> GoodGreen
                    securityScore >= 75 -> WarnAmber
                    else -> DangerRed
                },
                Modifier.weight(cell),
            )
            VitalBox(
                "Time ${if (battery.charging) "to full" else "left"}",
                com.universalrp.cleansweep.data.batteryTimeLabel(battery)?.replace("About ", "")
                    ?: "Not enough data",
                "Computed from the current flowing in or out right now, so it changes as you use " +
                    "or charge the phone.",
                AccentCyan,
                Modifier.weight(cell),
            )
        }
    }
}

@Composable
private fun VitalBox(
    title: String,
    value: String,
    note: String,
    colour: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceHigh)
    ) {
        Column(Modifier.padding(12.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colour)
            )
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colour,
            )
            if (note.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(note, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        }
    }
}

private fun temperatureTone(celsius: Float?, warm: Float, hot: Float): Color = when {
    celsius == null -> TextSecondary
    celsius < warm -> GoodGreen
    celsius < hot -> WarnAmber
    else -> DangerRed
}

private fun toneFor(value: Int, red: Float, amber: Float, invert: Boolean): Color = when {
    invert -> when {
        value <= red -> DangerRed
        value <= amber -> WarnAmber
        else -> GoodGreen
    }
    value >= red -> DangerRed
    value >= amber -> WarnAmber
    else -> GoodGreen
}

private fun healthGuide(kind: String, value: Float?): String = when (kind) {
    "batteryTemp" -> when {
        value == null -> "This phone does not report it."
        value < 40f -> "Normal — phones sit at 30–40 °C."
        value < 45f -> "Warm. Normal while charging or gaming."
        else -> "Hot. Let it cool; avoid heavy games while charging."
    }
    "cpuTemp" -> when {
        value == null -> "Kernel does not expose a CPU sensor."
        value < 48f -> "Normal."
        value < 60f -> "Warm — the phone may start slowing itself down."
        else -> "Hot. Close heavy apps and let it rest."
    }
    "power" -> when {
        value == null -> "This kernel does not report current."
        value < 3f -> "Light use."
        value < 6f -> "Normal use."
        else -> "Heavy use — screen or games at work."
    }
    else -> ""
}

private fun Long.formatGb(): String = "%.1f GB".format(this / (1024.0 * 1024.0 * 1024.0))

@Composable
private fun SmallAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
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
        context.startActivity(
            Intent.createChooser(intent, "Share report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        // Ignored.
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        // No browser installed.
    }
}
