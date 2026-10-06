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
import androidx.compose.material.icons.outlined.Send
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.universalrp.cleansweep.MainViewModel
import com.universalrp.cleansweep.Screen
import com.universalrp.cleansweep.UiState
import com.universalrp.cleansweep.ai.AssistantAction
import com.universalrp.cleansweep.ai.AssistantMessage
import com.universalrp.cleansweep.ui.theme.AccentCyan
import com.universalrp.cleansweep.ui.theme.AccentViolet
import com.universalrp.cleansweep.ui.theme.OutlineC
import com.universalrp.cleansweep.ui.theme.SurfaceC
import com.universalrp.cleansweep.ui.theme.SurfaceHigh
import com.universalrp.cleansweep.ui.theme.TextPrimary
import com.universalrp.cleansweep.ui.theme.TextSecondary

/**
 * The on-device assistant chat.
 *
 * Everything here is local: questions are answered by the rule engine in
 * [com.universalrp.cleansweep.ai.Assistant] using the numbers already on screen.
 * CleanSweep has no INTERNET permission, so there is nothing to send anywhere.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(state: UiState, vm: MainViewModel) {
    var input by remember { mutableStateOf("") }
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
        vm.askAssistant(q)
        input = ""
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
            IconButton(onClick = { vm.navigate(Screen.HOME) }) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Home", tint = TextSecondary)
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
                Text(
                    "CleanSweep Assistant",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Offline AI • answers stay on this phone",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentCyan,
                )
            }
            IconButton(onClick = { vm.clearAssistant() }) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear chat", tint = TextSecondary)
            }
        }

        // ------------------------------------------------------- verbosity switch
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Detailed answers",
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
                Text(
                    "No internet needed — this assistant is a small rule engine that runs on your phone and reads only the numbers shown in this app.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                )
            }

            items(state.assistantMessages) { msg ->
                AssistantBubble(msg, vm)
            }

            if (state.assistantTyping) {
                item { TypingBubble() }
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
                placeholder = { Text("Ask about your storage…", color = TextSecondary) },
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
            Spacer(Modifier.width(8.dp))
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
                    contentDescription = "Send",
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
                    Text(
                        "CleanSweep AI",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentCyan,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    msg.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                )
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
            }
        }
    }
}

@Composable
private fun TypingBubble() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(SurfaceC, shape = RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            color = AccentCyan,
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            "Thinking on this device…",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    Text(
        text,
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
