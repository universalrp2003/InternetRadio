package com.universalrp.cleansweep.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.universalrp.cleansweep.MainViewModel
import com.universalrp.cleansweep.data.tr
import com.universalrp.cleansweep.Screen
import com.universalrp.cleansweep.UiState
import com.universalrp.cleansweep.ai.AssistantAction
import com.universalrp.cleansweep.ai.AssistantMessage
import com.universalrp.cleansweep.ui.theme.AccentCyan
import com.universalrp.cleansweep.ui.theme.GoodGreen
import com.universalrp.cleansweep.ui.theme.AccentViolet
import com.universalrp.cleansweep.ui.theme.OutlineC
import com.universalrp.cleansweep.ui.theme.SurfaceC
import com.universalrp.cleansweep.ui.theme.SurfaceHigh
import com.universalrp.cleansweep.ui.theme.TextPrimary
import com.universalrp.cleansweep.ui.theme.WarnAmber
import com.universalrp.cleansweep.ui.theme.TextSecondary
import com.universalrp.cleansweep.voice.Announcer

/**
 * The assistant chat.
 *
 * The on-device rule engine in [com.universalrp.cleansweep.ai.Assistant] answers from the
 * numbers already on screen (no internet needed); with Online AI on, the user's own
 * provider answers instead, optionally with live web snippets (see WebLookup).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(state: UiState, vm: MainViewModel) {
    var input by remember { mutableStateOf("") }
    val context = LocalContext.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val listState = rememberLazyListState()
    val showChips = state.assistantMessages.size < 4
    val suggestions = remember(
        state.assistantMessages.size,
        state.report != null,
        state.usageAccess,
        state.totalAppCacheBytes(),
    ) { vm.assistantSuggestions() }

    val itemCount = 1 +
        state.assistantMessages.size +
        (if (state.assistantTyping) 1 else 0) +
        (if (showChips) 1 else 0)

    LaunchedEffect(itemCount) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    fun send(text: String) {
        val q = text.trim()
        if (q.isEmpty()) return
        keyboard?.hide()
        focus.clearFocus(force = true)
        vm.askAssistant(q)
        input = ""
    }

    // The phone's own speech recogniser turns speech into words. CleanSweep asks it for a
    // transcript and nothing more — no RECORD_AUDIO permission, no audio kept anywhere.
    // `listening` is only a label: the app has no way to hear whether the mic is live.
    var listening by remember { mutableStateOf(false) }
    val recognizer = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        listening = false
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val said = result.data
                ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (said.isNotBlank()) {
                input = said
                send(said)
            }
        }
    }
    val voiceAvailable = remember { Announcer.speechAvailable(context) }
    fun listen() {
        if (!voiceAvailable) {
            vm.notifyMessage(
                tr("No speech recogniser is installed on this phone — the keyboard still works.")
            )
            return
        }
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, tr("Ask Live Guard…"))
            putExtra(
                android.speech.RecognizerIntent.EXTRA_LANGUAGE,
                if (com.universalrp.cleansweep.data.Lang.isTamil) "ta-IN" else "en-IN",
            )
            putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        listening = true
        try {
            recognizer.launch(intent)
        } catch (e: Exception) {
            listening = false
            vm.notifyMessage(tr("The speech recogniser could not be opened."))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
    ) {
        // ------------------------------------------------------------ top bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.goBack() }) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = tr("Home"), tint = TextSecondary)
            }
            Box(
                Modifier
                    .size(38.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(AccentCyan, AccentViolet)
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.SmartToy,
                    contentDescription = null,
                    tint = Color(0xFF04202A),
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("Live Guard assistant"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    when {
                        state.assistantOnline && state.aiConfig.ready ->
                            tr("Online AI · %s", state.aiConfig.engineLabel)
                        state.assistantOnline ->
                            tr("Online mode selected · provider setup needed")
                        else ->
                            tr("Local assistant mode · no AI/search request")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentCyan,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            // v2.7: the mute for voice replies, right in the chat — the setting also exists
            // under Voice & daily watch, but nobody should have to dig for it. Switching it
            // off stops whatever is being read out at once (see setVoiceEvent).
            val voiceReplies = state.voiceOn && state.voiceAnswers
            IconButton(onClick = { vm.setVoiceEvent(Announcer.Event.ANSWER, !state.voiceAnswers) }) {
                Icon(
                    if (voiceReplies) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff,
                    contentDescription = if (voiceReplies) tr("Mute voice replies") else tr("Voice replies on"),
                    tint = if (voiceReplies) AccentCyan else TextSecondary,
                )
            }
            IconButton(onClick = { vm.openAiSettings() }) {
                Icon(Icons.Outlined.Settings, contentDescription = tr("Which AI answers"), tint = TextSecondary)
            }
            IconButton(onClick = { vm.clearAssistant() }) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = tr("Clear chat"), tint = TextSecondary)
            }
        }

        // ------------------------------------------------------- verbosity switch
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(tr("Detailed answers"),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = state.assistantVerbose,
                onCheckedChange = { vm.setAssistantVerbose(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF03202B),
                    checkedTrackColor = AccentCyan,
                ),
            )
        }

        // ------------------------------------------------------------- messages
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                val ready = state.aiConfig.ready
                Text(
                    when {
                        state.aiStatusChecking -> tr("Checking the provider connection…")
                        state.assistantOnline && ready -> tr("Online provider selected: %s. A connection check is not proof a model answered. Replies show the responding service, requested model and elapsed time; a requested ID does not prove the underlying execution model.", state.aiEngineLabel)
                        state.assistantOnline -> tr("Online mode needs provider setup. Local help is still available.")
                        else -> tr("The local assistant uses built-in rules and available phone readings. Online AI and web lookup require your choice.")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.assistantOnline && ready && !state.aiStatusOk) WarnAmber
                    else TextSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                )
                if (!ready || (state.assistantOnline && !state.aiStatusOk)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TextButton(
                            onClick = { vm.openAiSettings() },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        ) {
                            Text(tr("Which AI answers?"), color = AccentCyan, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = { vm.useAnotherFreeAi() },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        ) {
                            Text(tr("Use a free AI"), color = GoodGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            items(state.assistantMessages) { msg ->
                AssistantBubble(msg, vm)
            }

            if (state.assistantTyping) {
                item { TypingBubble(state, vm) }
            }

            if (showChips) {
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        suggestions.forEach { chip ->
                            SuggestionChip(chip) { send(chip) }
                        }
                    }
                }
            }
        }

        // --------------------------------------------------------------- input
        Row(
            Modifier
                .fillMaxWidth()
                .background(SurfaceC)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(tr("Ask about your storage…"), color = TextSecondary) },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send(input) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = OutlineC,
                    cursorColor = AccentCyan,
                ),
            )
            Spacer(Modifier.width(6.dp))
            IconButton(
                onClick = { listen() },
                enabled = !state.assistantTyping && !listening,
                modifier = Modifier
                    .size(48.dp)
                    .padding(bottom = 2.dp),
            ) {
                Icon(
                    Icons.Outlined.Mic,
                    contentDescription = tr("Speak"),
                    tint = if (listening) AccentViolet else AccentCyan,
                )
            }
            val canSend = input.isNotBlank() && !state.assistantTyping
            IconButton(
                onClick = { send(input) },
                enabled = canSend,
                modifier = Modifier
                    .size(52.dp)
                    .padding(bottom = 2.dp),
            ) {
                Icon(
                    Icons.Outlined.Send,
                    contentDescription = tr("Send"),
                    tint = if (canSend) AccentCyan else TextSecondary.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun AssistantBubble(msg: AssistantMessage, vm: MainViewModel) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            Modifier
                .fillMaxWidth(if (msg.fromUser) 0.86f else 0.94f)
                .background(
                    color = if (msg.fromUser) SurfaceHigh else SurfaceC,
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (msg.fromUser) 18.dp else 4.dp,
                        bottomEnd = if (msg.fromUser) 4.dp else 18.dp,
                    ),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                if (!msg.fromUser) {
                    Text(tr("CleanSweep AI"),
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentCyan,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                AiText(msg.text)
                // v2.8: the same live evidence the model saw, so the answer can be
                // checked instead of trusted.
                msg.sources.distinct().takeIf { it.isNotEmpty() }?.let { sources ->
                    var expanded by remember(msg.atMs) { mutableStateOf(false) }
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(tr("Web excerpts (%s)", sources.size), style = MaterialTheme.typography.labelSmall, color = AccentCyan)
                    }
                    if (expanded) {
                        Text(tr("Retrieved excerpts, not independent verification."), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        sources.take(3).forEach { line ->
                            Text(line, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Spacer(Modifier.height(2.dp))
                        }
                    }
                }
                msg.meta?.let { meta ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
                if (msg.actionLabel != null && msg.action != AssistantAction.NONE) {
                    Spacer(Modifier.height(2.dp))
                    TextButton(
                        onClick = { vm.runAssistantAction(msg.action) },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                    ) {
                        Text(msg.actionLabel, color = AccentCyan, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Outlined.ArrowForward,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                if (!msg.fromUser && msg.text.isNotBlank()) {
                    // Ask for this particular answer to be read out — allowed at any hour,
                    // because the user asked for it just now, and even when automatic voice
                    // replies are muted.
                    TextButton(
                        onClick = { vm.speakAnswer(msg, forced = true) },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                    ) {
                        Icon(
                            Icons.Outlined.VolumeUp,
                            contentDescription = tr("Read aloud"),
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            tr("Read aloud"),
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingBubble(state: UiState, vm: MainViewModel) {
    var elapsed by remember(state.assistantRequestStartedMs) { mutableStateOf(0L) }
    LaunchedEffect(state.assistantRequestStartedMs) {
        while (state.assistantRequestStartedMs > 0) {
            elapsed = ((android.os.SystemClock.elapsedRealtime() - state.assistantRequestStartedMs).coerceAtLeast(0) / 1_000)
            kotlinx.coroutines.delay(1_000)
        }
    }
    Row(Modifier.fillMaxWidth().background(SurfaceC, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(16.dp), color = AccentCyan, strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(state.assistantPhase ?: tr("Preparing answer…"), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text(tr("Elapsed: %s s · maximum: %s s", elapsed, com.universalrp.cleansweep.ai.AiRequestPolicy.Use.CHAT.budgetMs / 1_000),
                style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
        TextButton(onClick = { vm.cancelAssistant() }) { Text(tr("Cancel")) }
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    Text(
        tr(text),
        style = MaterialTheme.typography.labelMedium,
        color = AccentCyan,
        modifier = Modifier
            .background(SurfaceHigh, shape = RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** Total cache across the apps already listed (0 when the list is not loaded). */
private fun UiState.totalAppCacheBytes(): Long =
    appCaches.sumOf { if (it.cacheBytes > 0L) it.cacheBytes else 0L }
