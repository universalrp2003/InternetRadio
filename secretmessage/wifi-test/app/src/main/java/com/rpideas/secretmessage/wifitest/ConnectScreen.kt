package com.rpideas.secretmessage.wifitest

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rpideas.secretmessage.preview.ui.Accent
import com.rpideas.secretmessage.preview.ui.Ink
import com.rpideas.secretmessage.preview.ui.Muted
import com.rpideas.secretmessage.preview.ui.Panel
import com.rpideas.secretmessage.preview.ui.PanelRaised
import com.rpideas.secretmessage.preview.ui.Warm

/** What the connect screen and the app's settings ask the activity to do. */
interface ConnectActions {
    /** Starts a chat through the Internet relay. */
    fun startHost()

    /** Starts a chat on the same Wi-Fi, without the relay. */
    fun startWifiHost()
    fun shareInvite(invite: String)
    fun stopWaiting()
    fun pasteAndJoin()
    fun joinPending()
    fun answer(codesMatch: Boolean)
    fun copyReport()
    fun resetKey()
    fun forgetPhones()

    /** Calls a remembered phone through its doorbell (1.1.0). */
    fun callContact(deviceId: String)
    fun renameContact(deviceId: String, name: String)
    fun forgetContact(deviceId: String)

    /** Answers or ignores the chat request on screen. */
    fun answerRing()
    fun dismissRing()
    fun setReady(enabled: Boolean)
    fun openBatterySettings()
}

private val Danger = Color(0xFFFF8A80)
private val Online = Color(0xFF00B894)
private val Offline = Color(0xFF8A97A3)

/** Remembered phones, starting or joining a chat, and the safety-code comparison. */
@Composable
internal fun ConnectScreen(
    state: ConnectionState,
    contacts: List<Contact>,
    presence: Map<String, Doorbell.Presence>,
    ring: IncomingRing?,
    pendingInvite: String?,
    notice: String?,
    actions: ConnectActions,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header(onOpenSettings)
        if (notice != null) {
            Text(tr(notice), color = Warm, fontSize = 13.sp, lineHeight = 18.sp)
        }
        if (ring != null && state is ConnectionState.Idle) RingSection(ring, actions)
        when (state) {
            is ConnectionState.Idle -> {
                IdleSection(state, pendingInvite, actions)
                ContactsSection(contacts, presence, ring?.invite?.contact?.deviceId, actions)
                StartSection(actions, hasContacts = contacts.isNotEmpty())
            }
            is ConnectionState.Hosting -> if (state.calling != null) CallingSection(state, actions) else HostingSection(state, actions)
            is ConnectionState.Joining -> {
                val how = if (state.internet) {
                    tr("Through the relay this takes a few seconds.")
                } else {
                    tr("This takes a few seconds on the same Wi-Fi.")
                }
                val text = listOf(tr(state.link), how + " " + tr("Keep this screen open until the chat opens."))
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
                InfoSection(tr("Connecting to the other phone…"), text)
            }
            is ConnectionState.Comparing -> CompareSection(state, actions)
            is ConnectionState.Connected -> InfoSection(tr("Connected"), tr("Opening the chat…"))
            is ConnectionState.Ending -> InfoSection(tr(state.note), tr("Messages are removed from both phones."))
        }
        Spacer(Modifier.padding(bottom = 8.dp))
    }
}

@Composable
private fun Header(onOpenSettings: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Secret Message", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(tr(WifiWording.SUBTITLE), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Muted)
        }
        LanguageToggle()
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = Accent)
        }
    }
}

@Composable
private fun Section(color: Color = Panel, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = color, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun Note(text: String, color: Color = Muted) {
    Text(text, color = color, fontSize = 13.sp, lineHeight = 18.sp)
}

@Composable
private fun RingSection(ring: IncomingRing, actions: ConnectActions) {
    Section(color = PanelRaised) {
        Title(tr("{0} wants to chat", ring.invite.contact.label))
        Note(tr("A remembered phone is calling. Answer within about 3 minutes."), color = Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = actions::answerRing, modifier = Modifier.weight(1f)) { Text(tr("Answer")) }
            OutlinedButton(onClick = actions::dismissRing, modifier = Modifier.weight(1f)) { Text(tr("Ignore")) }
        }
    }
}

@Composable
private fun IdleSection(state: ConnectionState.Idle, pendingInvite: String?, actions: ConnectActions) {
    if (state.lastResult != null) {
        Section {
            Title(tr("Last chat"))
            Note(tr(state.lastResult), color = Ink)
        }
    }
    if (pendingInvite != null) {
        Section {
            Title(tr("Invite received"))
            Note(tr("Join only an invite you expected."))
            Button(onClick = actions::joinPending, modifier = Modifier.fillMaxWidth()) { Text(tr("Join this chat")) }
        }
    }
}

/** A remembered phone is calling: Answer, Ignore, or decide later (the banner and the list stay). */
@Composable
internal fun IncomingRingDialog(ring: IncomingRing, onAnswer: () -> Unit, onIgnore: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(tr("{0} wants to chat", ring.invite.contact.label)) },
        text = { Text(tr("A remembered phone is calling. Answer within about 3 minutes.")) },
        confirmButton = { Button(onClick = onAnswer) { Text(tr("Answer")) } },
        dismissButton = { TextButton(onClick = onIgnore) { Text(tr("Ignore")) } },
    )
}

@Composable
private fun ContactsSection(
    contacts: List<Contact>,
    presence: Map<String, Doorbell.Presence>,
    ringingDevice: String?,
    actions: ConnectActions,
) {
    if (contacts.isEmpty()) return
    var editing by remember { mutableStateOf<Contact?>(null) }
    Section {
        Title(tr("Remembered phones"))
        contacts.forEach { contact ->
            ContactRow(
                contact = contact,
                presence = presence[contact.deviceId],
                callingYou = contact.deviceId == ringingDevice,
                actions = actions,
                onEdit = { editing = contact },
            )
        }
        Note(tr("Tap a name to rename it. Names stay on this phone only."))
    }
    editing?.let { contact ->
        ContactDialog(
            contact = contact,
            onSave = { name ->
                actions.renameContact(contact.deviceId, name)
                editing = null
            },
            onForget = {
                actions.forgetContact(contact.deviceId)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun ContactRow(
    contact: Contact,
    presence: Doorbell.Presence?,
    callingYou: Boolean,
    actions: ConnectActions,
    onEdit: () -> Unit,
) {
    val (dot, status) = when {
        callingYou -> Online to tr("Calling you now")
        contact.card == null -> Offline to tr("Chat once with an invite to call this phone")
        presence == Doorbell.Presence.ONLINE -> Online to tr("Online · ready for chats")
        presence == Doorbell.Presence.OFFLINE -> Offline to tr("Not reachable now")
        else -> Offline to tr("Checking…")
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(dot, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onEdit)
                .padding(vertical = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(contact.label, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = tr("Rename"),
                    tint = Muted,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(status, color = Muted, fontSize = 11.sp, lineHeight = 14.sp)
        }
        // While this phone calls you, the same button answers (the activity routes it to Answer).
        Button(
            onClick = { actions.callContact(contact.deviceId) },
            enabled = contact.card != null || callingYou,
            modifier = Modifier.semantics { contentDescription = "Chat with ${contact.label}" },
        ) { Text(if (callingYou) tr("Answer") else tr("Chat")) }
    }
}

@Composable
private fun ContactDialog(contact: Contact, onSave: (String) -> Unit, onForget: () -> Unit, onDismiss: () -> Unit) {
    var name by remember(contact.deviceId) { mutableStateOf(contact.name) }
    var confirmForget by remember(contact.deviceId) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (confirmForget) tr("Forget this phone?") else tr("Name this phone")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (confirmForget) {
                    Text(
                        tr(
                            "Its name, doorbell and key are removed from this phone. The next chat with it needs an " +
                                "invite and the safety-code comparison.",
                        ),
                    )
                } else {
                    Text(tr("Device {0}", PeerPins.formatDeviceId(contact.deviceId)), fontSize = 12.sp, color = Muted)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(ContactBook.MAX_NAME_CHARS) },
                        singleLine = true,
                        label = { Text(tr("Name")) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    )
                    TextButton(onClick = { confirmForget = true }) { Text(tr("Forget this phone"), color = Danger) }
                }
            }
        },
        confirmButton = {
            if (confirmForget) {
                TextButton(onClick = onForget) { Text(tr("Forget"), color = Danger) }
            } else {
                TextButton(onClick = { onSave(name) }) { Text(tr("Save")) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

@Composable
private fun StartSection(actions: ConnectActions, hasContacts: Boolean) {
    Section {
        Title(tr("Start or join a chat"))
        Note(
            if (hasContacts) {
                tr("For a new phone, start a chat and send the invite; the other phone joins.")
            } else {
                tr("Start a chat and send the invite; the other phone joins. Then you both compare a 20-digit safety code.")
            },
        )
        Button(onClick = actions::startHost, modifier = Modifier.fillMaxWidth()) { Text(tr("Start a chat")) }
        OutlinedButton(onClick = actions::pasteAndJoin, modifier = Modifier.fillMaxWidth()) {
            Text(tr("Paste invite and join"))
        }
        TextButton(onClick = actions::startWifiHost) { Text(tr("Start on the same Wi-Fi, without the relay")) }
        Text(tr(WifiWording.NOT_REVIEWED), color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun CallingSection(state: ConnectionState.Hosting, actions: ConnectActions) {
    Section {
        Title(if (state.stopping) tr("Stopping…") else tr("Calling {0}…", state.calling.orEmpty()))
        if (state.link.isNotEmpty()) Note(tr(state.link), color = Ink)
        Note(tr("The other phone gets a notification. The chat opens when it answers (up to 3 minutes)."))
        OutlinedButton(
            onClick = actions::stopWaiting,
            enabled = !state.stopping,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(tr("Stop calling")) }
    }
}

@Composable
private fun HostingSection(state: ConnectionState.Hosting, actions: ConnectActions) {
    Section {
        Title(if (state.stopping) tr("Stopping…") else tr("Waiting for the other phone (up to 3 minutes)"))
        if (state.link.isNotEmpty()) Note(tr(state.link), color = Ink)
        Note(
            if (state.internet) {
                tr("Send the invite by SMS or any app; this phone keeps waiting. It works once. Keep it private.")
            } else {
                tr("Send the invite by SMS or any app; this phone keeps waiting. It works once, only on this Wi-Fi. Keep it private.")
            },
        )
        SelectionContainer {
            Text(state.invite, color = Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { actions.shareInvite(state.invite) },
                enabled = !state.stopping,
                modifier = Modifier.weight(1f),
            ) { Text(tr("Share invite")) }
            OutlinedButton(
                onClick = actions::stopWaiting,
                enabled = !state.stopping,
                modifier = Modifier.weight(1f),
            ) { Text(tr("Stop waiting")) }
        }
    }
}

@Composable
private fun CompareSection(state: ConnectionState.Comparing, actions: ConnectActions) {
    val prompt = state.prompt
    val device = PeerPins.formatDeviceId(prompt.peerDevice)
    Section {
        Title(if (prompt.peerStatus == PeerStatus.KNOWN) tr("Remembered phone") else tr("Compare the safety code"))
        when (prompt.peerStatus) {
            PeerStatus.CHANGED -> Note(
                tr(
                    "WARNING: the key of device {0} has changed since you verified it. Resetting or reinstalling " +
                        "that phone causes this, but so could another device pretending to be it. Compare every digit, " +
                        "and tap Codes differ if you are unsure.",
                    device,
                ),
                color = Danger,
            )
            PeerStatus.KNOWN -> Note(
                tr(
                    "Device {0} uses the same key you verified before, so this phone confirmed it automatically. " +
                        "If the other phone asks, compare this code with it.",
                    device,
                ),
            )
            PeerStatus.NEW -> Note(
                tr("New phone (device {0}). If the codes match, this phone will remember its key.", device),
            )
        }
        Text(
            twoLines(prompt.code),
            modifier = Modifier.fillMaxWidth(),
            color = Ink,
            fontSize = 30.sp,
            lineHeight = 40.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        if (prompt.peerStatus != PeerStatus.KNOWN) {
            Note(
                tr(
                    "Hold the phones side by side and compare every digit. Each person has two minutes to answer. " +
                        "Codes differ on either phone stops both phones.",
                ),
            )
        }
        when (state.answer) {
            null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { actions.answer(true) }, modifier = Modifier.weight(1f)) { Text(tr("Codes match")) }
                OutlinedButton(onClick = { actions.answer(false) }, modifier = Modifier.weight(1f)) {
                    Text(tr("Codes differ"))
                }
            }
            true -> Note(tr("You confirmed the code. Waiting for the other phone's answer…"), color = Ink)
            false -> Note(tr("You reported different codes. Stopping on both phones…"), color = Ink)
        }
    }
}

@Composable
private fun InfoSection(title: String, text: String) {
    Section {
        Title(title)
        Note(text)
    }
}

/** The four groups on two lines, so a narrow phone can show the code at a large size. */
private fun twoLines(code: String): String {
    val groups = code.trim().split(' ')
    return if (groups.size == 4) "${groups[0]} ${groups[1]}\n${groups[2]} ${groups[3]}" else code
}
