package com.universalrp.cleansweep.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.universalrp.cleansweep.*
import com.universalrp.cleansweep.ai.*
import com.universalrp.cleansweep.data.*
import com.universalrp.cleansweep.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun InsightsShortcut(vm: MainViewModel) {
    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tr("History & tools"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(tr("Phone self-test, security changes, charging history and data budgeting. The status pill stays simple."),
                style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { vm.openInsights() }, modifier = Modifier.weight(1f)) { Text(tr("Open tools")) }
                TextButton(onClick = { vm.openInsights(InsightsTab.ACTION_CHECKLIST) }, modifier = Modifier.weight(1f)) { Text(tr("Action checklist")) }
            }
        }
    }
}

private fun tabTitle(tab: InsightsTab) = tr(when (tab) {
    InsightsTab.COMPATIBILITY -> "Self-test"
    InsightsTab.SECURITY_HISTORY -> "Security timeline"
    InsightsTab.CHARGING_HISTORY -> "Charging history"
    InsightsTab.DATA_BUDGET -> "Data budget"
    InsightsTab.ACTION_CHECKLIST -> "Action checklist"
})

@Composable
fun InsightsScreen(state: UiState, vm: MainViewModel) {
    var clear by remember { mutableStateOf<InsightsTab?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(state.insightsTab, state.chargeHistoryEnabled, lifecycleOwner) {
        if (state.insightsTab == InsightsTab.CHARGING_HISTORY && state.chargeHistoryEnabled) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) { delay(10_000); vm.refreshInsights() }
            }
        }
    }
    clear?.let { kind ->
        AlertDialog(onDismissRequest = { clear = null }, title = { Text(tr("Clear local history?")) },
            text = { Text(tr(if (kind == InsightsTab.SECURITY_HISTORY)
                "This removes the local baseline, timeline and reviewed choices. No app permissions are changed."
                else "This removes this tool's saved history. Recording switches and your data plan stay unchanged.")) },
            confirmButton = { TextButton(onClick = { vm.clearLocalHistory(kind); clear = null }) { Text(tr("Clear")) } },
            dismissButton = { TextButton(onClick = { clear = null }) { Text(tr("Cancel")) } })
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.goBack() }) { Icon(Icons.Outlined.ArrowBack, tr("Back")) }
            Text(tr("History & tools"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                if (state.insightsTab == InsightsTab.SECURITY_HISTORY || state.insightsTab == InsightsTab.ACTION_CHECKLIST) vm.recheckActionChecklist()
                vm.refreshInsights()
            }, enabled = !state.insightsBusy && !state.securityBusy) { Icon(Icons.Outlined.Refresh, tr("Refresh")) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InsightsTab.entries.forEach { tab ->
                FilterChip(selected = tab == state.insightsTab, onClick = { vm.selectInsightsTab(tab) },
                    enabled = !state.insightsBusy, label = { Text(tabTitle(tab)) })
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.insightsBusy || state.securityBusy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.insightsError?.let { error -> item { Notice(error, WarnAmber) } }
            when (state.insightsTab) {
                InsightsTab.COMPATIBILITY -> item { CompatibilityContent(state, vm) }
                InsightsTab.SECURITY_HISTORY -> securityTimeline(state, vm, onClear = { clear = InsightsTab.SECURITY_HISTORY })
                InsightsTab.CHARGING_HISTORY -> chargingHistory(state, vm, onClear = { clear = InsightsTab.CHARGING_HISTORY })
                InsightsTab.DATA_BUDGET -> item { DataBudgetContent(state, vm, onClear = { clear = InsightsTab.DATA_BUDGET }) }
                InsightsTab.ACTION_CHECKLIST -> actionChecklist(state, vm)
            }
        }
    }
}

@Composable
private fun Notice(text: String, tone: Color = TextSecondary) {
    Text(tr(text), style = MaterialTheme.typography.bodySmall, color = tone)
}

@Composable
private fun HistorySwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(tr(title), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CompatibilityContent(state: UiState, vm: MainViewModel) {
    val context = LocalContext.current
    var preview by remember { mutableStateOf(false) }
    val report = state.compatibilityReport
    if (preview && report != null) {
        val text = report.safeText()
        AlertDialog(onDismissRequest = { preview = false }, title = { Text(tr("Preview diagnostic report")) },
            text = { Column(Modifier.heightIn(max = 400.dp).verticalScrollCompat()) {
                Notice("Only this preview is shared. No automatic upload or raw logs.")
                Spacer(Modifier.height(12.dp)); Text(text, style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { TextButton(onClick = { shareDiagnostic(context, text) }) { Text(tr("Share this report")) } },
            dismissButton = { Row { TextButton(onClick = { copyDiagnostic(context, text) }) { Text(tr("Copy")) }
                TextButton(onClick = { preview = false }) { Text(tr("Close")) } } })
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Notice("This checks local permissions, configuration and sensor availability. It does not grant access, start monitoring, contact an AI or test OEM rendering.")
        if (report == null) Text(tr("Run the self-test to see this phone's capabilities.")) else {
            Text("${report.manufacturer} ${report.model} • Android ${report.androidVersion} / API ${report.sdk}", style = MaterialTheme.typography.titleMedium)
            Notice(tr("Last checked %s", readingTime(report.checkedAtMs)))
            Button(onClick = { preview = true }, modifier = Modifier.fillMaxWidth()) { Text(tr("Preview / share safe report")) }
            for (check in report.checks) {
                PanelCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(tr(check.title), fontWeight = FontWeight.SemiBold)
                        val (label, tone) = when (check.status) {
                            CheckStatus.READY -> "Reported / ready" to AccentCyan
                            CheckStatus.NEEDS_PERMISSION -> "Permission not granted" to WarnAmber
                            CheckStatus.DISABLED -> "Off / restricted" to TextSecondary
                            CheckStatus.NOT_REPORTED -> "Not reported / not yet tested" to TextSecondary
                            CheckStatus.DEVICE_TEST_NEEDED -> "Needs a device test" to WarnAmber
                        }
                        Text(tr(label), color = tone, style = MaterialTheme.typography.labelMedium)
                        Notice(check.detail)
                    }
                }
            }
        }
        Button(onClick = { vm.refreshInsights() }, enabled = !state.insightsBusy, modifier = Modifier.fillMaxWidth()) { Text(tr("Run self-test again")) }
        TextButton(onClick = { vm.openOwnAppSettings() }, modifier = Modifier.fillMaxWidth()) { Text(tr("Open Live Guard app settings")) }
        TextButton(onClick = { vm.navigate(Screen.SETTINGS) }, modifier = Modifier.fillMaxWidth()) { Text(tr("Monitoring settings")) }
    }
}

private fun LazyListScope.securityTimeline(state: UiState, vm: MainViewModel, onClear: () -> Unit) {
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HistorySwitch("Record security changes locally", state.securityHistoryEnabled, vm::setSecurityHistoryEnabled)
            Notice("Opt-in, private on this phone; up to 200 events. Turning recording off pauses collection. Clear removes saved history and reviewed choices.")
            Notice("Changes are first observed during comparable checks, not necessarily at the moment access was granted. A permission is not proof of malware.")
            Row { TextButton(onClick = { vm.recheckActionChecklist() }, enabled = !state.securityBusy) { Text(tr("Run a fresh check")) }
                TextButton(onClick = onClear) { Text(tr("Clear history")) } }
            state.securityHistory.latest?.let { latest ->
                if (state.securityReport?.scannedAtMs?.let { it < latest.atMs } == true) Notice("This report predates the stored baseline; it is not compared. Recheck and verify the phone clock.", WarnAmber)
                Notice(tr("Last recorded check %s", readingTime(latest.atMs)))
                if (latest.unavailableChecks.isNotEmpty()) Notice("Some checks were unavailable; their previous observations are retained, not marked resolved.", WarnAmber)
            }
        }
    }
    if (state.securityHistory.events.isEmpty()) item { Notice("No saved timeline yet. Enable recording and run a check to establish a baseline; existing findings are not counted as new.") }
    items(state.securityHistory.events.asReversed(), key = { it.id }) { event ->
        PanelCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                val label = when (event.kind) {
                    SecurityChangeKind.BASELINE -> "Baseline established"
                    SecurityChangeKind.NEW_APP -> "App newly observed"
                    SecurityChangeKind.APP_NO_LONGER_LISTED -> "App no longer listed"
                    SecurityChangeKind.NEW_FINDING -> "Finding newly observed"
                    SecurityChangeKind.CHANGED -> "Evidence changed"
                    SecurityChangeKind.RESOLVED -> "No longer observed"
                    SecurityChangeKind.REVIEWED -> "Reviewed / expected"
                    SecurityChangeKind.REVIEW_CLEARED -> "Review acknowledgement removed"
                }
                Text(tr(label), color = AccentCyan, style = MaterialTheme.typography.labelMedium)
                Text(event.title, fontWeight = FontWeight.SemiBold)
                event.appLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                event.packageName?.let { Notice(it) }
                Notice(readingTime(event.atMs)); Notice(event.detail)
            }
        }
    }
}

private fun LazyListScope.chargingHistory(state: UiState, vm: MainViewModel, onClear: () -> Unit) {
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HistorySwitch("Record charging history locally", state.chargeHistoryEnabled, vm::setChargeHistoryEnabled)
            Notice("Opt-in, private, up to 30 sessions / 30 days. Uses existing monitor/app readings; enabling history does not enable persistent monitoring.")
            Notice("All watts are battery-side sensor readings, not adapter output. Gaps, missed plug/end events and reboot interruption are labelled partial.")
            Row { TextButton(onClick = { vm.navigate(Screen.SETTINGS) }) { Text(tr("Monitoring settings")) }
                TextButton(onClick = onClear) { Text(tr("Clear history")) } }
        }
    }
    val comparable = state.chargeHistory.sessions.asReversed().filter { it.twentyToEightyMs != null }.take(2)
    item {
        if (comparable.size == 2) {
            Notice(tr("Last two observed 20% → 80% intervals: %s / %s", duration(comparable[0].twentyToEightyMs!!), duration(comparable[1].twentyToEightyMs!!)))
            Notice("This is a local session comparison, not a charger ranking; temperature, battery level, screen use and OEM reporting affect it.")
        } else Notice("Two fully observed 20% → 80% intervals are needed for a useful session comparison.")
    }
    state.chargeHistory.active?.let { session -> item(key = "active-${session.id}") { ChargeSessionCard(session, active = true) } }
    if (state.chargeHistory.active == null && state.chargeHistory.sessions.isEmpty()) item { Notice("No charging sessions recorded yet. Enable history, then plug in and unplug. A session begun mid-charge is partial.") }
    items(state.chargeHistory.sessions.asReversed(), key = { it.id }) { ChargeSessionCard(it, active = false) }
}

@Composable
private fun ChargeSessionCard(session: ChargeSession, active: Boolean) {
    var expanded by remember(session.id) { mutableStateOf(active) }
    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(tr(if (active) "Recording observed session" else if (session.partial) "Partial observed session" else "Observed charging session"),
                fontWeight = FontWeight.SemiBold, color = AccentCyan)
            Notice(readingTime(session.startedAtMs) + (session.endedAtMs?.let { " → ${readingTime(it)}" } ?: ""))
            Notice(if (active || !session.endObserved) "Percentages/time are observed start and latest sample, not an inferred final unplug reading." else "Start/end percentages and times were observed; the true moment can fall between samples.")
            if (!session.observedFromPlug) Notice("Recording began while connected; the earlier plug time and percentage are unknown.")
            Text("${session.startPercent?.let { "$it%" } ?: "—"} → ${session.endPercent?.let { "$it%" } ?: "—"} • ${duration(session.durationMs)}")
            Text(tr("Sampled average / peak") + ": ${number(session.averageWatts, "W")} / ${number(session.peakWatts, "W")}", style = MaterialTheme.typography.bodySmall)
            Text(tr("Highest observed temperature") + ": ${number(session.maxTemperatureC, "°C")}", style = MaterialTheme.typography.bodySmall)
            Notice(tr("Power-sampled intervals %s of observed %s", duration(session.measuredPowerMs), duration(session.durationMs)))
            Text(tr("Observed 20% → 80%") + ": ${session.twentyToEightyMs?.let(::duration) ?: tr("Not fully observed")}", style = MaterialTheme.typography.bodySmall)
            if (session.partial || active) Notice("An observed/partial session is not a complete charging benchmark. Comparing chargers requires similar battery level, temperature and phone use.")
            TextButton(onClick = { expanded = !expanded }) { Text(tr(if (expanded) "Hide charts" else "Show charts")) }
            if (expanded) {
                val points = (session.samples + listOfNotNull(session.lastSample)).distinctBy { it.elapsedMs }.sortedBy { it.elapsedMs }
                SampleLineChart(tr("Battery-side watts"), points.map { it.elapsedMs to it.watts }, "W", AccentCyan, zeroBase = true)
                SampleLineChart(tr("Battery temperature"), points.map { it.elapsedMs to it.temperatureC }, "°C", WarnAmber, zeroBase = false)
                Notice("Charts show the latest 240 saved points plus the latest sample. Lines join nearby valid samples only; missing values and gaps over two minutes stay open. Summary statistics cover sampled intervals across the observed session.")
            }
        }
    }
}

@Composable
private fun SampleLineChart(title: String, points: List<Pair<Long, Double?>>, unit: String, tone: Color, zeroBase: Boolean) {
    val valid = points.mapNotNull { it.second?.takeIf(Double::isFinite) }
    Text(title, style = MaterialTheme.typography.labelLarge)
    if (valid.size < 2 || points.last().first <= points.first().first) { Notice("Not enough valid samples for a chart."); return }
    val low = if (zeroBase) 0.0 else valid.minOrNull()!! - 1
    val high = maxOf(valid.maxOrNull()!! + if (zeroBase) 0.5 else 1.0, low + 1)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Notice(number(low, unit)); Notice(number(high, unit)) }
    Canvas(Modifier.fillMaxWidth().height(130.dp).semantics { contentDescription = "$title: ${valid.size} ${tr("observed samples")}" }) {
        drawLine(TextSecondary.copy(alpha = 0.3f), Offset(0f, size.height), Offset(size.width, size.height))
        var previous: Pair<Long, Offset>? = null
        for ((time, value) in points) {
            if (value == null || !value.isFinite()) { previous = null; continue }
            val x = ((time - points.first().first).toDouble() / (points.last().first - points.first().first) * size.width).toFloat()
            val y = ((1 - (value - low) / (high - low)) * size.height).toFloat()
            val point = Offset(x, y)
            previous?.let { (before, position) -> if (time - before in 1..ChargeHistoryPolicy.MAX_GAP_MS) drawLine(tone, position, point, strokeWidth = 2.dp.toPx()) }
            drawCircle(tone, 2.dp.toPx(), point); previous = time to point
        }
    }
}

@Composable
private fun DataBudgetContent(state: UiState, vm: MainViewModel, onClear: () -> Unit) {
    val context = LocalContext.current
    var expiry by remember(state.dataBudgetConfig.expiresOn) { mutableStateOf(state.dataBudgetConfig.expiresOn.orEmpty()) }
    var confirmStart by remember { mutableStateOf(false) }
    val startDate = state.dataPackInfo?.let { Instant.ofEpochMilli(it.packStartDateMs).atZone(ZoneId.systemDefault()).toLocalDate().toString() }.orEmpty()
    var periodStart by remember(startDate) { mutableStateOf(startDate) }
    if (confirmStart) AlertDialog(onDismissRequest = { confirmStart = false }, title = { Text(tr("Change local period start?")) },
        text = { Text(tr("Android history will use this date's midnight. Partial counter tracking restarts from now; earlier missing traffic is not guessed. Carrier billing and expiry are unchanged.")) },
        confirmButton = { TextButton(onClick = { vm.saveDataPeriodStart(periodStart); confirmStart = false }) { Text(tr("Apply start date")) } },
        dismissButton = { TextButton(onClick = { confirmStart = false }) { Text(tr("Cancel")) } })
    var confirmReset by remember { mutableStateOf(false) }
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false }, title = { Text(tr("Reset local pack counter?")) },
        text = { Text(tr("Confirm only when your pack/recharge has started. This changes local tracking, not your carrier plan. Set the new expiry separately.")) },
        confirmButton = { TextButton(onClick = { vm.resetDataPackCount(); confirmReset = false; vm.refreshInsights() }) { Text(tr("Reset counter")) } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(tr("Cancel")) } })
    val usage = state.dataPackInfo
    val start = usage?.let { Instant.ofEpochMilli(it.packStartDateMs).atZone(ZoneId.systemDefault()).toLocalDate() }
    val result = DataBudgetPolicy.calculate(LocalDate.now(), state.dataBudgetConfig, usage?.packRemainingBytes?.takeIf { usage.readingAvailable },
        usage?.let { it.readingAvailable && !it.isPartial } == true, usage?.isUnlimited5g == true, state.dailyDataHistory, start)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Notice("Android mobile totals cover this device's SIM traffic, not Wi-Fi or the carrier's official bill. Carrier rounding, zero-rating and unlimited-5G eligibility can differ.")
        usage?.let {
            Text(tr("Configured quota") + ": ${it.formattedPackLimit}", fontWeight = FontWeight.SemiBold)
            Text(tr("Remaining estimate") + ": ${it.formattedRemaining}")
            Text(tr("Today so far") + ": ${it.formattedToday}")
            Notice(tr(it.source)); Notice(tr("Last checked %s", readingTime(it.capturedAtMs)))
        }
        OutlinedTextField(value = periodStart, onValueChange = { periodStart = it.take(10) }, label = { Text(tr("Recharge / period start date")) },
            placeholder = { Text("YYYY-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = { confirmStart = true }, enabled = !state.insightsBusy) { Text(tr("Apply start date")) }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !state.dataBudgetConfig.resetAtStartOfDay, onClick = { vm.setDataDateMode(false) }, label = { Text(tr("Expiry day (inclusive)")) })
            FilterChip(selected = state.dataBudgetConfig.resetAtStartOfDay, onClick = { vm.setDataDateMode(true) }, label = { Text(tr("Next reset day (exclusive)")) })
        }
        OutlinedTextField(value = expiry, onValueChange = { expiry = it.take(10) }, label = { Text(tr("Pack expiry / next reset date")) },
            placeholder = { Text("YYYY-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            TextButton(onClick = {
                val date = DataBudgetPolicy.dateOrNull(expiry) ?: LocalDate.now()
                android.app.DatePickerDialog(context, { _, y, m, d -> expiry = LocalDate.of(y, m + 1, d).toString() },
                    date.year, date.monthValue - 1, date.dayOfMonth).show()
            }) { Text(tr("Choose date")) }
            TextButton(onClick = { vm.saveDataExpiry(expiry) }) { Text(tr("Save date")) }
            TextButton(onClick = { expiry = ""; vm.saveDataExpiry("") }) { Text(tr("Remove date")) }
        }
        Notice("Expiry includes the selected day; a next-reset date excludes that day. Dates do not automatically reset counters or assume a recharge. Start-date history begins at local midnight, not the exact carrier recharge hour.")
        PanelCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (result.status) {
                    BudgetStatus.NEED_EXPIRY -> Notice("Set your actual pack expiry to calculate a daily allowance.")
                    BudgetStatus.EXPIRED -> Notice("The configured period has ended. Verify your recharge, reset manually if appropriate and update the date.", WarnAmber)
                    BudgetStatus.UNAVAILABLE -> Notice("Mobile usage is unavailable. No remaining-quota or run-out forecast is guessed.")
                    BudgetStatus.PARTIAL -> Notice("Only a partial counter is available. Precise daily budgeting and run-out forecasts are withheld. Full history may need Usage access or may be restricted by this ROM.", WarnAmber)
                    BudgetStatus.UNLIMITED -> Notice("Unlimited 5G is your configured preference, not carrier verification. Quota forecasts are disabled; measured mobile history can still be shown.")
                    BudgetStatus.READY, BudgetStatus.EXHAUSTED -> {
                        Text(tr("Days including today") + ": ${result.daysRemaining}")
                        Text(tr("Aim for at most today") + ": ${DataUsageTracker.formatBytesSafe(result.dailyBudgetBytes ?: 0)}", fontWeight = FontWeight.SemiBold, color = AccentCyan)
                        if (result.status == BudgetStatus.EXHAUSTED) Notice("The measured usage has reached the configured quota. Check your carrier balance.", WarnAmber)
                        Text(tr("Average of complete recorded days") + ": ${result.averageDailyBytes?.let(DataUsageTracker::formatBytesSafe) ?: tr("Not enough history")}")
                        Text(tr("Estimated remaining days at that pace") + ": ${result.forecastDays?.toString() ?: tr("Not enough history")}")
                        Notice(tr("Based on %s complete past days (up to seven); forecasts need at least three. Unknown/partial days are not counted as zero.", result.completeDays))
                    }
                }
            }
        }
        TextButton(onClick = { confirmReset = true }, enabled = !state.insightsBusy) { Text(tr("Recharge: reset local counter")) }
        if (usage?.hasUsageAccess != true) TextButton(onClick = { vm.openUsageAccess() }) { Text(tr("Open Usage access settings")) }
        HistorySwitch("Keep daily mobile history locally", state.dataHistoryEnabled, vm::setDataHistoryEnabled)
        Notice("Saving is opt-in, up to 90 daily records. The chart can read seven recent Android-reported days without saving them. Today is in progress; missing days remain unknown.")
        DailyDataChart(state.dailyDataHistory)
        state.dailyDataHistory.sortedByDescending { it.day }.take(14).forEach { point ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(point.day, style = MaterialTheme.typography.bodySmall)
                Text((point.bytes?.let(DataUsageTracker::formatBytesSafe) ?: tr("Not measured")) + " • " + tr(if (point.complete) "Reported full day" else "Partial / in progress"),
                    style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        TextButton(onClick = onClear) { Text(tr("Clear saved daily history")) }
    }
}

@Composable
private fun DailyDataChart(history: List<DailyDataPoint>) {
    val days = history.sortedBy { it.day }.takeLast(7)
    if (days.isEmpty()) { Notice("No daily history available yet."); return }
    val max = maxOf(1L, days.mapNotNull { it.bytes }.maxOrNull() ?: 1).toDouble()
    Text(tr("Recent mobile usage"), style = MaterialTheme.typography.labelLarge)
    Canvas(Modifier.fillMaxWidth().height(100.dp).semantics { contentDescription = tr("Daily mobile usage; missing days are not zero") }) {
        val slot = size.width / days.size
        days.forEachIndexed { index, point ->
            val bytes = point.bytes
            if (bytes == null) drawLine(TextSecondary, Offset(slot * index + slot * 0.2f, size.height - 4.dp.toPx()), Offset(slot * index + slot * 0.8f, size.height - 4.dp.toPx()))
            else {
                val height = maxOf(1.dp.toPx(), (bytes / max * size.height).toFloat())
                drawRect(if (point.complete) AccentCyan else WarnAmber, Offset(slot * index + slot * 0.2f, size.height - height),
                    androidx.compose.ui.geometry.Size(slot * 0.6f, height))
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEach { point -> Text(point.day.takeLast(5), style = MaterialTheme.typography.labelSmall) }
    }
    Notice("Cyan: reported full day. Amber: partial/in progress. Dash: unknown. Zero is shown only when actually reported.")
}

private fun LazyListScope.actionChecklist(state: UiState, vm: MainViewModel) {
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Notice("Priorities come from observed facts and your reviews. AI may add explanations; it cannot create executable routes, revoke permissions, uninstall apps, delete files or mark issues resolved.")
            state.securityReport?.let { Notice(tr("Security last checked %s", readingTime(it.scannedAtMs))) }
            if (state.securityReport?.unavailableChecks?.isNotEmpty() == true) Notice("Some security checks are incomplete. Missing results are not safe/resolved.", WarnAmber)
            state.securityReadError?.let { Notice(it, WarnAmber) }
            Row {
                TextButton(onClick = { vm.recheckActionChecklist() }, enabled = !state.securityBusy) { Text(tr("Recheck current facts")) }
                TextButton(onClick = { if (state.aiConfig.ready) vm.runSecurityAiAnalysis() else vm.openAiSettings() }, enabled = !state.aiBusy && !state.securityBusy) { Text(tr("Ask AI")) }
            }
            Notice("The local checklist works without AI or a network connection. Reviewed/expected is not resolved and does not change access. New evidence/app updates require a new review.")
            if (state.aiActionAdvice.isEmpty()) Notice("No usable AI checklist explanations yet. Safe local review steps remain available.")
        }
    }
    if (state.actionChecklist.isEmpty()) item { Notice(if (state.securityReport == null) "Run a fresh check to build the checklist. No safety verdict is assumed." else "No actionable observations in the available checks. This is not proof that the phone is safe.") }
    ActionGroup.entries.forEach { group ->
        val actions = state.actionChecklist.filter { it.group == group }
        if (actions.isNotEmpty()) {
            item(key = "group-${group.name}") { Text(tr(when (group) { ActionGroup.DO_FIRST -> "Do first"; ActionGroup.OPTIONAL -> "Optional review"; ActionGroup.LEAVE_ALONE -> "Leave alone / expected" }) + " (${actions.size})", fontWeight = FontWeight.Bold, color = AccentCyan) }
            items(actions, key = { it.observation.key }) { ChecklistActionCard(it, state, vm) }
        }
    }
}

@Composable
fun ChecklistActionCard(action: ChecklistAction, state: UiState, vm: MainViewModel) {
    var expanded by remember(action.observation.key) { mutableStateOf(action.group == ActionGroup.DO_FIRST) }
    val item = action.observation
    val advice = ActionChecklistPolicy.currentAdvice(action, state.aiActionAdvice)
    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded })
            item.app?.let { Text(it.label); Notice(it.componentName ?: it.packageName) }
            Text(tr(if (action.reviewed) "Reviewed / expected — access unchanged" else "Not yet reviewed"), color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            if (expanded) {
                Notice(item.detail)
                Text(tr("Local review steps"), style = MaterialTheme.typography.labelLarge)
                Notice(action.instructions)
                if (advice != null) {
                    Text(tr("AI explanation — verify it"), style = MaterialTheme.typography.labelLarge, color = WarnAmber)
                    Text(advice.explanation, style = MaterialTheme.typography.bodySmall)
                    if (advice.suggestedReview.isNotBlank()) Text(advice.suggestedReview, style = MaterialTheme.typography.bodySmall)
                    Notice(tr("AI advice from %s", readingTime(advice.atMs)))
                } else if (state.aiActionAdvice.any { it.key == item.key }) Notice("Previous AI advice is not used because the evidence changed. Re-analyse if needed.", WarnAmber)
                if (action.route != null) TextButton(onClick = { vm.openChecklistAction(item.key) }, enabled = !state.securityBusy && !state.aiBusy) { Text(tr(if (action.route == ChecklistRoute.SETTINGS) "Review setting" else "Open relevant tool")) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = action.reviewed, onCheckedChange = { vm.markActionReviewed(item.key, it) }, enabled = !state.securityBusy && !state.aiBusy && state.securityReadError == null)
                    Text(tr("I reviewed this / expected access"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                }
            }
            TextButton(onClick = { expanded = !expanded }) { Text(tr(if (expanded) "Less detail" else "Review details")) }
        }
    }
}

/** Small entry point in the AI report; the full checklist remains accessible even when the provider fails. */
@Composable
fun AiChecklistEntry(state: UiState, vm: MainViewModel) {
    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tr("Turn advice into a review checklist"), fontWeight = FontWeight.SemiBold)
            Notice("Do first, optional review and leave alone/expected. Only verified local routes can open; completion requires fresh evidence.")
            TextButton(onClick = { vm.openInsights(InsightsTab.ACTION_CHECKLIST) }, enabled = !state.aiBusy) {
                Text(tr("Open action checklist") + " (${state.actionChecklist.size})")
            }
        }
    }
}

@Composable
private fun Modifier.verticalScrollCompat(): Modifier = this.then(Modifier.verticalScroll(rememberScrollState()))

private fun duration(ms: Long): String = if (ms < 60_000) "${ms.coerceAtLeast(0) / 1000} s" else formatUptime(ms)
private fun number(value: Double?, unit: String) = value?.takeIf(Double::isFinite)?.let { String.format(Locale.US, "%.1f %s", it, unit) } ?: tr("Not reported")
private fun copyDiagnostic(context: Context, text: String) {
    val ok = runCatching { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Live Guard diagnostic report", text)) }.isSuccess
    android.widget.Toast.makeText(context, tr(if (ok) "Diagnostic report copied" else "Could not copy; the preview remains available."), android.widget.Toast.LENGTH_SHORT).show()
}
private fun shareDiagnostic(context: Context, text: String) { runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text); putExtra(Intent.EXTRA_SUBJECT, "Live Guard compatibility report")
}, "Share diagnostic report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure {
    android.widget.Toast.makeText(context, tr("No share activity available; use Copy or read the preview."), android.widget.Toast.LENGTH_LONG).show()
} }
