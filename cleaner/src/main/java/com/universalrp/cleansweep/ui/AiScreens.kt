package com.universalrp.cleansweep.ui

import kotlinx.coroutines.isActive

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
import androidx.compose.runtime.LaunchedEffect
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
    val provider = state.aiConfig.provider
    var keyDraft by remember(provider) { mutableStateOf("") }
    var modelDraft by remember(state.aiConfig) { mutableStateOf(state.aiConfig.model) }
    var baseDraft by remember(state.aiConfig) { mutableStateOf(state.aiConfig.baseUrl) }
    var showModels by remember { mutableStateOf(false) }

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
                Icon(Icons.Outlined.ArrowBack, contentDescription = tr("Back"), tint = TextSecondary)
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
                                    if (state.aiStatusChecking) tr("Checking…")
                                    else if (state.aiStatusOk) tr("Provider responded: %s", state.aiEngineLabel)
                                    else tr("Not checked or unavailable"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    state.aiStatusText
                                        ?: if (state.aiConfig.ready) {
                                            tr("Connection checks do not prove generation. Test connection sends a small generation request.")
                                        } else {
                                            tr("Choose a provider and save its key if required.")
                                        },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = { vm.refreshAiStatus(announce = true) }) {
                                Text(tr("Check now"), color = AccentCyan, fontWeight = FontWeight.Bold)
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
                            tr("Choose a provider, save its key if required, then load available models. Requests go directly to that provider, not a developer server. Connection checks may run on app opening or settings changes; online questions and manual reports send their selected text and allowed facts."),
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
                            tr("Chat subscriptions and API access are separate. Availability, models, quotas and charges depend on the provider and account; check the provider dashboard. A configured model name is not proof it is usable."),
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
                                label = tr("English"),
                                selected = state.aiAnswerLanguage == "en",
                                onClick = { vm.setAnswerLanguage("en") },
                            )
                            ModeChip2(
                                label = tr("Tamil"),
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
                        ProviderKeyHelp(provider)
                        if (provider.needsKey) {
                            Spacer(Modifier.height(8.dp))
                            val keyConfigured = AiSettings.hasSavedKey(context, provider) ||
                                (state.aiConfig.provider == provider && state.aiConfig.apiKey.isNotBlank())
                            Text(
                                tr(if (keyConfigured) "API key configured for %s." else "No API key configured for %s.", provider.shortLabel),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (keyConfigured) GoodGreen else TextSecondary,
                            )
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = keyDraft,
                                onValueChange = { keyDraft = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text(tr("API key")) },
                                placeholder = {
                                    if (keyConfigured) Text(tr("Saved key is hidden. Enter a new key only to replace it."))
                                    else Text(tr("Paste API key"))
                                },
                                visualTransformation = PasswordVisualTransformation(),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                tr(AiSettings.keyStorageNote(state.aiConfig.keyStorageProtected)),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.aiConfig.keyStorageProtected) TextSecondary else DangerRed,
                            )
                            if (keyConfigured) {
                                TextButton(
                                    onClick = {
                                        vm.forgetAiKey(provider)
                                        keyDraft = ""
                                    },
                                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                                ) {
                                    Text(tr("Forget saved key"), color = DangerRed, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                tr("Free AI needs no key and tries at most two endpoints within the same deadline. Shared endpoints may be busy or unavailable. Another provider is an explicit choice, not a silent cross-provider fallback."),
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
                                label = { Text(tr("Base URL")) },
                                placeholder = { Text("http://192.168.1.5:11434/v1") },
                            )
                        }

                        Spacer(Modifier.height(14.dp))
                        StepHeader(3, "Choose the model")
                        Spacer(Modifier.height(6.dp))
                        if (provider == AiProvider.FREE) {
                            Text(
                                tr("Free endpoints use configured model IDs. Replies name the endpoint that returned an answer and its requested model; the underlying routed model may not be reported."),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        } else {
                            Text(
                                tr("Load models asks the provider for available model IDs. A listed model still needs a successful generation test; names, access and quotas can change."),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                            Spacer(Modifier.height(6.dp))
                            // 2.41: the output budget is bounded, and a request that is nearly
                            // out of time asks for a shorter answer instead of timing out.
                            Text(
                                tr("Each reply uses a bounded output budget. When a request's own time limit is nearly spent the answer budget shrinks instead of letting one question run into a minute-long wait."),
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
                                label = { Text(tr("Model (or type one)")) },
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
                                color = TextSecondary,
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
                        SwitchRow(
                            label = tr("Live web lookup"),
                            detail = tr("Fetch relevant web excerpts for assistant questions, with an 8-second lookup limit inside the 45-second chat deadline. Prices require exact-product seller evidence; news requires today's publication date. Excerpts and provider search citations are not independently verified."),
                            checked = state.aiWebLookup,
                            onChange = { vm.setAiWebLookup(it) },
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
                                tr("The app does not collect file contents, photos, messages or contacts for AI requests. Your typed question/report and allowed facts are sent to the selected provider. Its API key is sent only for authentication. Web lookup sends the question as a search query. Do not type private information you do not want to share."),
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
                Icon(Icons.Outlined.ArrowBack, contentDescription = tr("Back"), tint = TextSecondary)
            }
            Text(
                if (state.aiSecurityFocus) "Security AI review" else "AI analysis",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { vm.runAiAnalysis(state.aiSecurityFocus) }, enabled = !state.aiBusy) {
                Icon(Icons.Outlined.Refresh, contentDescription = tr("Analyse again"), tint = TextSecondary)
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ------------------------------------------- local vitals, side by side
            item {
                Text(
                    tr("Last observed phone readings"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "These are dated phone observations, not AI measurements. Colours are review hints, not a safety or battery-health certification.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }

            item {
                state.health?.let { health ->
                    Column {
                    Text(tr("Last checked %s", readingTime(health.capturedAtMs)), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    VitalsGrid(health, state.securityReport?.takeIf { it.unavailableChecks.isEmpty() }?.score, state.storage)
                    }
                } ?: PanelCard(Modifier.fillMaxWidth()) {
                    Text(tr("Reading the sensors…"), Modifier.padding(16.dp), color = TextSecondary)
                }
            }

            item {
                PanelCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            when {
                                state.aiBusy && state.aiSecurityFocus && state.aiPromptPreview.isBlank() -> "Refreshing current phone issues…"
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
                            if (state.aiAnswer != null) tr("Responding service: %s", who) + (if (modelText.isNotBlank()) " · " + tr("Requested model: %s", modelText) else "")
                                else tr("Selected service; not a completed response: %s", state.aiConfig.engineLabel),
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
                        if (state.aiBusy) {
                            Spacer(Modifier.height(12.dp))
                            CircularProgressIndicator(color = AccentCyan)
                            Spacer(Modifier.height(8.dp))
                            AiOperationProgress(state.aiStartedMs, 90, state.aiPhase, onCancel = { vm.cancelAiReport() })
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
                                TextButton(onClick = { vm.runAiAnalysis(state.aiSecurityFocus) }) {
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

            item(key = "action_checklist_entry") { AiChecklistEntry(state, vm) }

            // ----------------------------------------------- the answer, sectioned
            state.aiAnswer?.let { answer ->
                val sections = splitAnswer(com.universalrp.cleansweep.ai.ActionChecklistCodec.narrative(answer).ifBlank { "Use the local action checklist; no human-readable AI explanation was returned." })
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
                com.universalrp.cleansweep.data.ChargingHonestyPolicy.wattsText(battery.powerW) ?: "Not reported",
                healthGuide("power", battery.powerW),
                if (battery.charging) GoodGreen else AccentCyan,
                Modifier.weight(cell),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val ramFraction = com.universalrp.cleansweep.data.ReadingPolicy.ramUsedFraction(health.device.totalRamBytes, health.device.availableRamBytes)
            VitalBox(
                "RAM in use",
                ramFraction?.let { "${(it * 100).toInt()}%" } ?: "Not reported",
                "Android keeps RAM busy on purpose — over 80% is normal on a phone with many apps.",
                if (ramFraction == null) TextSecondary else if (ramFraction > 0.9f) WarnAmber else AccentCyan,
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
                    securityScore >= 90 -> "Few permission warnings observed; this does not prove safety."
                    securityScore >= 75 -> "A couple of settings are worth a look."
                    else -> "Review observed permissions and whether you need them. No access is automatically revoked."
                },
                when {
                    securityScore == null -> TextSecondary
                    securityScore >= 90 -> GoodGreen
                    securityScore >= 75 -> WarnAmber
                    else -> DangerRed
                },
                Modifier.weight(cell),
            )
            val chargingMinutes = com.universalrp.cleansweep.data.batteryTimeMinutes(battery)
            val etaValue = when {
                battery.charging && battery.percent >= 100 -> tr("Full")
                battery.charging -> chargingMinutes?.let { com.universalrp.cleansweep.data.formatMinutes(it) }
                    ?: tr("Estimating")
                battery.powerConnected -> tr("Connected · not charging")
                else -> tr("Unavailable")
            }
            VitalBox(
                tr(if (battery.charging && battery.percent < 100) "Estimated to 100%" else "Battery timing"),
                etaValue,
                tr(if (battery.charging) {
                    "Estimate uses recent in-memory battery-level observations while charging; it is unavailable until enough readings exist. It is not a measured watt reading."
                } else {
                    "A time-to-empty estimate is not inferred from one current reading or a guessed battery capacity."
                }),
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
            Text(tr(title), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
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


/** Elapsed budget is not model-completion percentage. Leaving this composition stops the timer. */
@Composable
fun AiOperationProgress(startedMs: Long, limitSeconds: Int, phase: String?, onCancel: () -> Unit) {
    var elapsed by remember(startedMs) { mutableStateOf(0L) }
    LaunchedEffect(startedMs) {
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            elapsed = ((android.os.SystemClock.elapsedRealtime() - startedMs).coerceAtLeast(0) / 1_000).coerceAtMost(limitSeconds.toLong())
            kotlinx.coroutines.delay(1_000)
        }
    }
    Text(tr(phase ?: "Waiting for the selected provider…"), style = MaterialTheme.typography.bodySmall)
    Text(tr("%d s elapsed; whole-operation limit %d s", elapsed, limitSeconds), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    TextButton(onClick = onCancel) { Text(tr("Cancel")) }
}
