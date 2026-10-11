package com.universalrp.cleansweep.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.universalrp.cleansweep.MainActivity
import com.universalrp.cleansweep.MainViewModel
import com.universalrp.cleansweep.Screen
import com.universalrp.cleansweep.UiState
import com.universalrp.cleansweep.InsightsTab
import com.universalrp.cleansweep.data.*
import com.universalrp.cleansweep.ui.theme.TextSecondary

private fun Context.ownerActivity(): MainActivity? = when (this) {
    is MainActivity -> this
    is ContextWrapper -> if (baseContext !== this) baseContext.ownerActivity() else null
    else -> null
}

@Composable
fun FeatureSetupHomeCard(state: UiState, vm: MainViewModel) {
    val context = LocalContext.current
    val features = FeatureDirectory.list(context, state)
    val pending = FeatureSetupPolicy.pending(features).size
    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(tr("Reviewed feature setup"), style = MaterialTheme.typography.titleSmall)
            Text(tr(if (!FeatureSetupPrefs.reviewed(context)) "Review optional functions before enabling them. Saved OFF choices are respected."
                else "Your reviewed choices are saved. Optional OFF functions are not failures."), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            if (pending > 0) Text(tr("%d features need setup or Android access", pending), style = MaterialTheme.typography.labelSmall)
            Row {
                TextButton(onClick = { vm.navigate(Screen.FEATURE_SETUP) }) { Text(tr("Review feature setup")) }
            }
        }
    }
}

@Composable
fun FeatureSetupScreen(state: UiState, vm: MainViewModel) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    val features = remember(state, revision) { FeatureDirectory.list(context, state) }
    var reminders by remember { mutableStateOf(FeatureSetupPrefs.reminders(context)) }
    var backgroundChoice by remember { mutableStateOf(false) }
    var backgroundEdited by remember { mutableStateOf(false) }
    var voiceChoice by remember { mutableStateOf(false) }
    var voiceEdited by remember { mutableStateOf(false) }
    var chargingChoice by remember { mutableStateOf(false) }
    var chargingEdited by remember { mutableStateOf(false) }
    var dailyChoice by remember { mutableStateOf(false) }
    var dailyEdited by remember { mutableStateOf(false) }
    var dailyAi by remember { mutableStateOf(FeatureSetupPrefs.prefs(context).getBoolean("daily_ai", false)) }
    var message by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.goBack() }) { Text(tr("Back")) }
            Text(tr("Reviewed feature setup"), style = MaterialTheme.typography.titleMedium)
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                PanelCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Setup reviews only Live Guard's own functions: safe local readings and refresh defaults do not grant access or delete data. Core Live Guard does not read other apps' notifications or messages, so there is no private-message switch here. Existing OFF choices remain OFF unless you change them here."))
                    Text(tr("This checklist describes configuration, not proof of background delivery, audible speech or a successful AI response."), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    SetupChoice("Persistent background monitoring", if (backgroundEdited) backgroundChoice else state.persistentMonitor) { backgroundChoice = it; backgroundEdited = true }
                    SetupChoice("Voice alerts and answers", if (voiceEdited) voiceChoice else state.voiceOn) { voiceChoice = it; voiceEdited = true }
                    SetupChoice("Charging notification", if (chargingEdited) chargingChoice else state.chargeMonitor) { chargingChoice = it; chargingEdited = true }
                    SetupChoice("Daily local check", if (dailyEdited) dailyChoice else state.dailyScanOn) { dailyChoice = it; dailyEdited = true }
                    SetupChoice("Optional setup reminder notifications", reminders) { reminders = it }
                    SetupChoice("Optional automatic online daily summaries", dailyAi) { dailyAi = it }
                    Text(tr("Automatic online daily summaries send allowed phone-report facts to the selected provider and may use API quota. Off by default. This is separate from local checks and does not include notification messages."), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    if (!FeatureDirectory.notificationPermission(context)) {
                        Text(tr("Background and charging notification choices need Android notification permission. Grant it first, then apply the choices you want."), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { context.ownerActivity()?.requestNotificationsIfNeeded() }) { Text(tr("Review Android notification permission")) }
                    }
                    Button(onClick = {
                        val notificationAccess = FeatureDirectory.notificationPermission(context)
                        // Write only switches explicitly changed in this review, never a stale initial UI default.
                        if (voiceEdited) vm.setVoiceOn(voiceChoice)
                        if (dailyEdited) vm.setDailyScanOn(dailyChoice)
                        if (backgroundEdited && (!backgroundChoice || notificationAccess)) vm.setPersistentMonitor(backgroundChoice)
                        if (chargingEdited && (!chargingChoice || notificationAccess)) vm.setChargeMonitor(chargingChoice)
                        FeatureSetupPrefs.save(context, reminders)
                        FeatureSetupPrefs.prefs(context).edit().putBoolean("daily_ai", dailyAi).apply()
                        revision++
                        message = if (!notificationAccess && ((backgroundEdited && backgroundChoice) || (chargingEdited && chargingChoice)))
                            "Choices saved where allowed. Background notification choices remain unchanged until you grant access and apply again."
                        else "Reviewed choices saved. No access was granted or data deleted automatically."
                        vm.checkSetupReminders()
                    }) { Text(tr("Apply reviewed choices")) }
                    message?.let { Text(tr(it), style = MaterialTheme.typography.bodySmall) }
                    SetupGuideButton(SetupGuides.SETUP)
                } }
            }
            items(features, key = { it.id }) { feature ->
                PanelCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(tr(feature.title), style = MaterialTheme.typography.titleSmall)
                    Text(tr(when (feature.status) {
                        FeatureSetupPolicy.Status.READY -> "Configured; operation still depends on Android and actual readings"
                        FeatureSetupPolicy.Status.NEEDS_ACCESS -> "Needs Android access"
                        FeatureSetupPolicy.Status.NOT_CONFIGURED -> "Not configured"
                        FeatureSetupPolicy.Status.OFF_BY_CHOICE -> "Optional OFF or setup reminder skipped"
                    }), style = MaterialTheme.typography.labelSmall)
                    Text(tr(feature.explanation), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    TextButton(onClick = {
                        when (feature.id) {
                            "health" -> vm.navigate(Screen.HEALTH)
                            "security" -> vm.navigate(Screen.SECURITY)
                            "files" -> if (android.os.Build.VERSION.SDK_INT < 30) context.ownerActivity()?.requestLegacyStorageIfNeeded() else vm.requestAllFilesAccess()
                            "cache" -> vm.navigate(Screen.APP_CACHE)
                            "daily", "voice" -> vm.navigate(Screen.VOICE)
                            "background", "pill" -> vm.navigate(Screen.SETTINGS)
                            "ai" -> vm.navigate(Screen.AI_SETTINGS)
                            "data" -> vm.openInsights(InsightsTab.DATA_BUDGET)
                            "update" -> vm.navigate(Screen.ABOUT)
                        }
                    }) { Text(tr("Open setup / details")) }
                    if (feature.status != FeatureSetupPolicy.Status.READY) {
                        val skipped = feature.id in FeatureSetupPrefs.skipped(context)
                        TextButton(onClick = { FeatureSetupPrefs.skip(context, feature.id, !skipped); revision++ }) {
                            Text(tr(if (skipped) "Restore this setup reminder" else "Skip this setup reminder"))
                        }
                    }
                } }
            }
            item {
                PanelCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr("How to add compact widgets"), style = MaterialTheme.typography.titleSmall)
                    Text(tr("Long-press the launcher, choose Widgets, then Live Guard Battery, Data or Phone. Each has a View selector. Launcher cell sizes vary. Last unplug is saved only when an actual power-disconnect event is observed; missed events stay unknown, not invented."), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { LastUnplugRepo.clear(context); com.universalrp.cleansweep.widget.CleanSweepWidget.refresh(context); message = "Observed last-unplug record cleared." }) { Text(tr("Clear observed last unplug")) }
                } }
            }
        }
    }
}

@Composable
private fun SetupChoice(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(tr(title), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Checkbox(checked, onCheckedChange = onChange)
    }
}
