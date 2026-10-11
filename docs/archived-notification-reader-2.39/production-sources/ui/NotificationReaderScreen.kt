package com.universalrp.cleansweep.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.universalrp.cleansweep.MainViewModel
import com.universalrp.cleansweep.Screen
import com.universalrp.cleansweep.UiState
import com.universalrp.cleansweep.data.tr
import com.universalrp.cleansweep.notifications.*
import com.universalrp.cleansweep.ui.theme.TextSecondary
import com.universalrp.cleansweep.voice.Announcer
import kotlinx.coroutines.*

private data class ReaderApp(val pkg: String, val label: String)

@Composable
fun NotificationReaderScreen(state: UiState, vm: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(NotificationReaderPrefs.load(context)) }
    var granted by remember { mutableStateOf(MessageReaderService.accessGranted(context)) }
    val connected by MessageReaderService.connected.collectAsStateWithLifecycle()
    val status by MessageReaderService.status.collectAsStateWithLifecycle()
    val recent by MessageReaderService.recentSenders.collectAsStateWithLifecycle()
    var apps by remember { mutableStateOf<List<ReaderApp>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var picker by remember { mutableStateOf(false) }
    var modelReady by remember { mutableStateOf(false) }
    var modelBusy by remember { mutableStateOf(false) }
    var modelConsent by remember { mutableStateOf(false) }
    var wifiOnly by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            granted = MessageReaderService.accessGranted(context); config = NotificationReaderPrefs.load(context)
        } }
        lifecycle.lifecycle.addObserver(observer); onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { runCatching {
            context.packageManager.getInstalledApplications(0).filter { it.packageName != context.packageName &&
                (context.packageManager.getLaunchIntentForPackage(it.packageName) != null || config.rules.any { rule -> rule.packageName == it.packageName }) }
                .map { ReaderApp(it.packageName, it.loadLabel(context.packageManager).toString()) }.sortedBy { it.label.lowercase() }
        }.getOrDefault(emptyList()) }
        modelReady = try { LocalMessageTranslator.ready() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { false }
    }
    fun save(value: NotificationReaderConfig) { config = value; NotificationReaderPrefs.save(context, value) }
    fun replace(rule: NotificationReadingPolicy.Rule) { save(config.copy(rules = config.rules.map { if (it.packageName == rule.packageName) rule else it })) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.goBack() }) { Text(tr("Back")) }
            Text(tr("Selected notification reading"), style = MaterialTheme.typography.titleMedium)
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                PanelCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Off by default. Choose apps and optional sender names. Sender name only is the default; full message reading is a separate choice for each app. No app name, time or notification count is spoken."))
                    Text(tr("Notification access lets Android deliver broad notification content to this service. Live Guard discards unselected apps before reading content. It does not read old notifications, upload or save messages, or request Contacts access. App-supplied sender names are not verified identities."), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text(tr("Turning reading off stops speech but does not revoke Android notification access. Revoke access in Android settings if you no longer want this service connected."), style = MaterialTheme.typography.labelSmall)
                    Text(tr("Notification reading uses only an installed non-network TTS voice in the Live Guard language and on-device Tamil/English translation. Unknown language or missing translation model means sender name only. No private message is sent to an AI or online speech service by Live Guard."), style = MaterialTheme.typography.bodySmall)
                    Text(tr("Your installed TTS engine implements its offline voice. Its own privacy policy still applies. Delivery depends on Android access, volume, quiet hours, the voice master switch and OEM background limits."), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Enable selected notification reading"), Modifier.weight(1f))
                        Switch(config.enabled, onCheckedChange = { save(config.copy(enabled = it)) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Read only while screen is on and phone unlocked"), Modifier.weight(1f))
                        Switch(config.unlockedOnly, onCheckedChange = { save(config.copy(unlockedOnly = it)) })
                    }
                    if (!config.unlockedOnly) Text(tr("Lock-screen reading can expose private sender names and messages to people nearby. Android may hide sensitive content regardless of this choice."), style = MaterialTheme.typography.bodySmall)
                    Text(tr(if (granted) "Android notification access granted" else "Android notification access not granted"))
                    Text(tr(if (connected) "Listener connected; this does not prove speech was heard." else "Listener not connected. Android may restrict or suspend it."), style = MaterialTheme.typography.labelSmall)
                    TextButton(onClick = { runCatching { MessageReaderService.openAccess(context) }.onFailure { message = "Open Android Settings and grant Notification access to Live Guard if you trust it." } }) {
                        Text(tr("Open Android notification access"))
                    }
                    Text(tr("If Android blocks access for a sideloaded app, review App info and Allow restricted settings only if you trust Live Guard. Android, not this app, grants access."), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    if (!state.voiceOn) Text(tr("Voice master switch is off. Reading stays silent until you explicitly enable voice."))
                    TextButton(onClick = { scope.launch {
                        val language = com.universalrp.cleansweep.data.Lang.languageIn(context)
                        val voice = Announcer.installedAnswerVoices(context, language).firstOrNull { !it.online }
                        message = if (voice == null) "No installed non-network voice for this language. Install one in Android TTS settings."
                        else if (Announcer.previewAnswerVoice(context, language, voice.name)) "Offline preview callback completed. Confirm you heard it yourself; reading was not enabled by preview."
                        else "Offline voice preview failed. Reading remains subject to your existing choices."
                    } }) { Text(tr("Preview installed offline voice")) }
                    TextButton(onClick = { vm.navigate(Screen.VOICE) }) { Text(tr("Voice settings and language voices")) }
                    TextButton(onClick = { picker = true }) { Text(tr("Select multiple apps")) }
                    if (config.rules.isEmpty()) Text(tr("No apps selected: nothing will be read."))
                    Text(tr(status), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    TextButton(onClick = { save(NotificationReaderConfig()); NotificationReaderPrefs.clear(context) }) { Text(tr("Turn off and clear sender choices")) }
                } }
            }
            items(config.rules, key = { it.packageName }) { rule ->
                var senderDraft by remember(rule.packageName) { mutableStateOf("") }
                PanelCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(apps.firstOrNull { it.pkg == rule.packageName }?.label ?: rule.packageName, style = MaterialTheme.typography.titleSmall)
                    Text(rule.packageName, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Read full message"), Modifier.weight(1f)); Switch(rule.fullMessage, onCheckedChange = { replace(rule.copy(fullMessage = it)) })
                    }
                    Text(tr(if (rule.fullMessage) "Sender plus translated content; suspected passwords and OTPs are omitted." else "Sender name only"), style = MaterialTheme.typography.labelSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Only selected senders"), Modifier.weight(1f)); Switch(rule.contactsOnly, onCheckedChange = { replace(rule.copy(contactsOnly = it)) })
                    }
                    if (rule.contactsOnly) {
                        Text(tr("Names are app-scoped notification sender names, not verified contacts. Groups may use a group title. Empty selection reads nobody."), style = MaterialTheme.typography.labelSmall)
                        Text(tr("Enable the reader and receive a new message to see sender choices. Existing messages are not replayed. You can also enter a name exactly as it appears."), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        (rule.senders + recent[rule.packageName].orEmpty()).sorted().forEach { sender ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(sender in rule.senders, onCheckedChange = { checked -> replace(rule.copy(senders = if (checked) rule.senders + sender else rule.senders - sender)) })
                                Text(sender, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        OutlinedTextField(senderDraft, onValueChange = { senderDraft = it.take(80) }, label = { Text(tr("Sender name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        TextButton(onClick = { NotificationReadingPolicy.name(senderDraft)?.let { replace(rule.copy(senders = rule.senders + it)); senderDraft = "" } }, enabled = senderDraft.isNotBlank()) { Text(tr("Add sender choice")) }
                    }
                    TextButton(onClick = { save(config.copy(rules = config.rules.filterNot { it.packageName == rule.packageName })) }) { Text(tr("Remove this app")) }
                } }
            }
            item {
                PanelCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("On-device translation models"), style = MaterialTheme.typography.titleSmall)
                    Text(tr(if (modelReady) "Tamil/English translation model is present; actual translation may still fail." else "Translation model is missing or unavailable. Different-language content falls back to sender name only."))
                    Text(tr("Download requires your approval and an internet connection. Model download traffic goes to Google's model service, not your private message text. Space and data costs vary. Models and app choices stay on this phone."), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { modelConsent = true }, enabled = !modelBusy) { Text(tr(if (modelBusy) "Downloading translation model…" else "Review and download local models")) }
                    if (modelBusy) CircularProgressIndicator(Modifier.size(24.dp))
                    message?.let { Text(tr(it), style = MaterialTheme.typography.bodySmall) }
                    SetupGuideButton(SetupGuides.NOTIFICATIONS)
                } }
            }
        }
    }
    if (picker) AlertDialog(onDismissRequest = { picker = false }, title = { Text(tr("Select multiple apps")) }, text = {
        Column {
            OutlinedTextField(query, onValueChange = { query = it }, label = { Text(tr("Search apps")) }, singleLine = true)
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items(apps.filter { it.label.contains(query, true) || it.pkg.contains(query, true) }, key = { it.pkg }) { app ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(config.rules.any { it.packageName == app.pkg }, onCheckedChange = { checked ->
                            save(config.copy(rules = if (checked) config.rules + NotificationReadingPolicy.Rule(app.pkg) else config.rules.filterNot { it.packageName == app.pkg }))
                        }); Column(Modifier.weight(1f)) { Text(app.label); Text(app.pkg, style = MaterialTheme.typography.labelSmall, color = TextSecondary) }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { picker = false }) { Text(tr("Done")) } })
    if (modelConsent) AlertDialog(onDismissRequest = { modelConsent = false }, title = { Text(tr("Download local translation models?")) }, text = {
        Column {
            Text(tr("Only model files are downloaded. No message is sent for translation. Android/ML Kit may finish a consented download after you leave this page."))
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(wifiOnly, onCheckedChange = { wifiOnly = it }); Text(tr("Wi-Fi only (recommended)")) }
        }
    }, confirmButton = { TextButton(onClick = {
        modelConsent = false; modelBusy = true; message = null
        scope.launch {
            try { val success = LocalMessageTranslator.download(wifiOnly); modelReady = LocalMessageTranslator.ready()
                message = if (success && modelReady) "Local translation model downloaded. No message text was uploaded." else "Model download not ready. Keep sender-only reading and retry on Wi-Fi." } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { message = "Model download failed. Private messages were not uploaded." } finally { modelBusy = false }
        }
    }) { Text(tr("Download models")) } }, dismissButton = { TextButton(onClick = { modelConsent = false }) { Text(tr("Cancel")) } })
}
