package com.universalrp.cleansweep.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.universalrp.cleansweep.MainViewModel
import com.universalrp.cleansweep.data.AppLang
import com.universalrp.cleansweep.data.tr
import com.universalrp.cleansweep.ui.theme.TextSecondary
import com.universalrp.cleansweep.voice.Announcer
import com.universalrp.cleansweep.voice.AnswerVoicePair
import com.universalrp.cleansweep.voice.AnswerVoicePreferences
import com.universalrp.cleansweep.voice.InstalledAnswerVoice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Listen to the phone's actual voices; Android voice names/locales are not gender labels. */
@Composable
fun AnswerVoicesCard(appLanguage: AppLang, vm: MainViewModel) {
    val context = LocalContext.current
    var language by remember { mutableStateOf(appLanguage) }
    var inventory by remember(language) { mutableStateOf(emptyList<InstalledAnswerVoice>()) }
    var pair by remember(language) { mutableStateOf(AnswerVoicePreferences.load(context, language)) }
    var loading by remember(language) { mutableStateOf(true) }
    var previewBusy by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }
    LaunchedEffect(language, refresh) {
        loading = true
        try { inventory = Announcer.installedAnswerVoices(context, language) } catch (e: CancellationException) { throw e } catch (error: Exception) { inventory = emptyList() } finally { loading = false }
        pair = AnswerVoicePreferences.load(context, language)
    }
    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tr("Alternating assistant voices"), style = MaterialTheme.typography.titleSmall)
            Text(tr("Voice A and Voice B alternate between answers. Each answer is read once, not twice. Manual Read aloud uses that answer's assigned voice."),
                style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text(tr("Long answers may be shortened to fit speech limits."), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(tr("Android does not reliably label voice gender. Listen to installed-voice previews and choose the two voices you prefer. Choices are saved separately for Tamil and English."),
                style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Row {
                TextButton(onClick = { language = AppLang.TA }, enabled = !previewBusy) { Text(tr("Tamil") + if (language == AppLang.TA) " ✓" else "") }
                TextButton(onClick = { language = AppLang.EN }, enabled = !previewBusy) { Text(tr("English") + if (language == AppLang.EN) " ✓" else "") }
            }
            when {
                loading -> Text(tr("Loading installed voices…"), style = MaterialTheme.typography.bodySmall)
                inventory.isEmpty() -> Text(tr("The engine reported no matching voice for this language, so answers cannot be spoken in it yet. Install a voice in Android TTS settings."),
                    style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                else -> {
                    // 2.42: A/B are filled in automatically, so the card always shows the two
                    // slots that will actually be used; the owner can still preview and swap.
                    Text(tr("Voice A and Voice B are chosen automatically from your installed voices. Change or preview them here any time."),
                        style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    if (inventory.size < 2) Text(tr("Only one matching voice was reported, so both slots use it and the two voices cannot alternate yet."),
                        style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    AnswerVoiceChoice(language, 0, pair, inventory, previewBusy, { previewBusy = it }) { pair = AnswerVoicePreferences.load(context, language) }
                    AnswerVoiceChoice(language, 1, pair, inventory, previewBusy, { previewBusy = it }) { pair = AnswerVoicePreferences.load(context, language) }
                }
            }
            Text(tr("Online voices may send spoken text to the TTS provider. Preview completion is an engine callback, not proof you heard it. Save only after listening."),
                style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Row {
                TextButton(onClick = { refresh++ }, enabled = !loading && !previewBusy) { Text(tr("Reload voices")) }
                TextButton(onClick = { vm.openTtsSettings() }, enabled = !previewBusy) { Text(tr("Android voice settings")) }
            }
        }
    }
}

@Composable
private fun AnswerVoiceChoice(language: AppLang, slot: Int, pair: AnswerVoicePair, inventory: List<InstalledAnswerVoice>,
    busy: Boolean, onBusy: (Boolean) -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved = if (slot == 0) pair.first else pair.second
    val other = if (slot == 0) pair.second else pair.first
    var candidate by remember(language, saved) { mutableStateOf(saved) }
    var expanded by remember { mutableStateOf(false) }
    var previewed by remember(language, candidate) { mutableStateOf(false) }
    var note by remember(language, candidate) { mutableStateOf<String?>(null) }
    val choice = inventory.firstOrNull { it.name == candidate }
    Column(Modifier.fillMaxWidth()) {
        Text(tr(if (slot == 0) "Voice A" else "Voice B"), style = MaterialTheme.typography.labelLarge)
        Box {
            TextButton(onClick = { expanded = true }, enabled = !busy) {
                Text(choice?.let { it.name + " · " + it.language + " · " + tr(if (it.online) "Online" else "Offline") } ?: tr("Choose installed voice"))
            }
            DropdownMenu(expanded = expanded && !busy, onDismissRequest = { expanded = false }) {
                inventory.forEach { voice -> DropdownMenuItem(
                    text = { Text(voice.name + "\n" + voice.language + " · " + tr(if (voice.online) "Online" else "Offline")) },
                    onClick = { candidate = voice.name; expanded = false }, enabled = voice.name != other)
                }
            }
        }
        Row {
            TextButton(enabled = choice != null && !busy, onClick = {
                val selected = choice ?: return@TextButton
                scope.launch {
                    onBusy(true); previewed = false; note = null
                    try {
                        previewed = Announcer.previewAnswerVoice(context, language, selected.name)
                        note = tr(if (previewed) "Preview finished. Save this voice only if you heard it and prefer it."
                            else "Preview failed or was blocked. Check master voice, installed data, media volume and connection.")
                    } catch (e: CancellationException) { throw e } finally { onBusy(false) }
                }
            }) { Text(tr("Preview")) }
            TextButton(enabled = previewed && candidate != null && candidate != other && !busy, onClick = {
                val name = candidate ?: return@TextButton
                scope.launch {
                    if (Announcer.saveAnswerVoice(context, language, slot, name)) { onSaved(); note = tr("Voice choice saved") }
                    else note = tr("Voice choice could not be saved. Preview it again and choose a distinct voice.")
                }
            }) { Text(tr(if (slot == 0) "Use as Voice A" else "Use as Voice B")) }
        }
        note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = TextSecondary) }
    }
}
