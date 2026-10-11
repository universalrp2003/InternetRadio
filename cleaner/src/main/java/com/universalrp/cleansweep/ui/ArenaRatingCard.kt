package com.universalrp.cleansweep.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.universalrp.cleansweep.ai.ArenaRatingPolicy
import com.universalrp.cleansweep.ai.ArenaRatings
import com.universalrp.cleansweep.data.AppLang
import com.universalrp.cleansweep.data.DisplayTime
import com.universalrp.cleansweep.data.Lang
import com.universalrp.cleansweep.data.tr
import com.universalrp.cleansweep.ui.theme.TextSecondary
import kotlinx.coroutines.*
import java.util.Locale

@Composable
fun ArenaRatingCard(modelId: String, ambiguous: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<ArenaRatings.Snapshot?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var loadingJob by remember { mutableStateOf<Job?>(null) }
    val selected = snapshot?.let { ArenaRatingPolicy.exact(modelId, it.rows) }.takeUnless { ambiguous }
    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("LM Arena model rating"), style = MaterialTheme.typography.titleSmall)
            Text(tr("Requested model: %s", modelId.ifBlank { tr("Unknown routed model") }), style = MaterialTheme.typography.bodySmall)
            Text(tr("Category: overall text (full), crowd preference. Not an IQ, universal intelligence score, Tamil guarantee or proof that this model ran."), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            if (ambiguous || modelId.isBlank()) Text(tr("Routed or ambiguous model: no exact rating can be assigned."), style = MaterialTheme.typography.bodySmall)
            else if (selected != null) {
                val data = snapshot!!
                val current = ArenaRatingPolicy.current(data.changedAt, System.currentTimeMillis())
                Text(tr(if (current) "Current-window published snapshot: %s" else "Dated snapshot only; current rating unavailable: %s", String.format(Locale.US, "%.1f", selected.rating)))
                if (selected.lower != null && selected.upper != null) Text(tr("Published 95%% interval: %s to %s", String.format(Locale.US, "%.1f", selected.lower), String.format(Locale.US, "%.1f", selected.upper)), style = MaterialTheme.typography.bodySmall)
                Text(tr("Exact source model ID: %s", selected.modelId), style = MaterialTheme.typography.bodySmall)
            } else Text(tr(if (snapshot == null) "No rating loaded. Refresh checks the official public source." else "No exact model match in this source snapshot. No nearby-model score is substituted."), style = MaterialTheme.typography.bodySmall)
            snapshot?.let { data ->
                Text(tr("Data file last changed: %s", DisplayTime.dated(data.changedAt, Lang.languageIn(context) == AppLang.TA)), style = MaterialTheme.typography.labelSmall)
                Text(tr("Source fetched: %s", DisplayTime.dated(data.fetchedAt, Lang.languageIn(context) == AppLang.TA)), style = MaterialTheme.typography.labelSmall)
                Text(tr("Snapshots older than seven days, or ahead of the phone clock, are not labelled current. Official public data may lag the live website."), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
            message?.let { Text(tr(it), style = MaterialTheme.typography.bodySmall) }
            Text(tr("Refreshing may download about 2 MB of public leaderboard data. Your selected model and API key are not sent to the source."), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            if (busy) TextButton(onClick = { loadingJob?.cancel(); busy = false }) { Text(tr("Cancel")) }
            else TextButton(onClick = {
                busy = true; message = null
                loadingJob = scope.launch {
                    try { val result = ArenaRatings.load(refresh = true); if (result != null) snapshot = result
                        else message = "Rating lookup timed out. Any displayed snapshot retains its original date." } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { message = "Official rating source unavailable or its format changed. No score is invented." } finally { busy = false }
                }
            }) { Text(tr("Refresh official rating")) }
            Row {
                TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ArenaRatings.SOURCE))) } }) { Text(tr("Source data")) }
                TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ArenaRatings.LEADERBOARD))) } }) { Text(tr("Live leaderboard")) }
            }
        }
    }
}
