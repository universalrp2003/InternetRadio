package com.rpideas.secretmessage.wifitest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rpideas.secretmessage.preview.ui.Accent
import com.rpideas.secretmessage.preview.ui.Ink
import com.rpideas.secretmessage.preview.ui.Muted
import com.rpideas.secretmessage.preview.ui.Panel
import com.rpideas.secretmessage.preview.ui.Warm

/** What the connect screen asks the activity to do. */
interface ConnectActions {
    fun startHost()
    fun shareInvite(invite: String)
    fun stopWaiting()
    fun pasteAndJoin()
    fun joinPending()
    fun answer(codesMatch: Boolean)
    fun copyReport()
    fun resetKey()
    fun forgetPhones()
}

private val Danger = Color(0xFFFF8A80)

/** Start or join a Wi-Fi chat, compare the safety code, and see this phone's test identity. */
@Composable
internal fun ConnectScreen(
    state: ConnectionState,
    identitySummary: String,
    engineInfo: String,
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
        Section(color = Color(0xFF342B20)) {
            Text(WifiWording.NOTICE_TITLE, color = Warm, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(WifiWording.NOTICE_BODY, color = Color(0xFFFFE6C7), fontSize = 12.sp, lineHeight = 16.sp)
            Text("Engine: $engineInfo", color = Color(0xFFFFE6C7), fontSize = 11.sp, lineHeight = 15.sp)
        }
        if (notice != null) {
            Text(notice, color = Warm, fontSize = 13.sp, lineHeight = 18.sp)
        }
        when (state) {
            is ConnectionState.Idle -> IdleSection(state, pendingInvite, actions)
            is ConnectionState.Hosting -> HostingSection(state, actions)
            is ConnectionState.Joining -> InfoSection(
                "Connecting to the other phone…",
                "This takes a few seconds on the same Wi-Fi. Keep this screen open until the safety code appears.",
            )
            is ConnectionState.Comparing -> CompareSection(state, actions)
            is ConnectionState.Connected -> InfoSection("Connected", "Opening the chat…")
            is ConnectionState.Ending -> InfoSection(state.note, "Messages are removed from both phones.")
        }
        PhoneSection(identitySummary, idle = state is ConnectionState.Idle, actions)
        Spacer(Modifier.padding(bottom = 8.dp))
    }
}

@Composable
private fun Header(onOpenSettings: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("Secret Message", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(WifiWording.TEST_LABEL, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Muted)
        }
        Spacer(Modifier.weight(1f))
        Surface(shape = CircleShape, color = Color(0xFF3A3026)) {
            Text(
                WifiWording.BADGE,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                color = Warm,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
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
private fun IdleSection(state: ConnectionState.Idle, pendingInvite: String?, actions: ConnectActions) {
    if (state.lastResult != null) {
        Section {
            Title("Last chat")
            Note(state.lastResult, color = Ink)
        }
    }
    if (pendingInvite != null) {
        Section {
            Title("Invite received")
            Note("Join only an invite you expected. Both phones must be on the same Wi-Fi.")
            Button(onClick = actions::joinPending, modifier = Modifier.fillMaxWidth()) { Text("Join this chat") }
        }
    }
    Section {
        Title("Start or join a chat")
        Note(
            "Both phones must be on the same Wi-Fi (not a guest network). One phone starts the chat and sends the " +
                "invite; the other phone joins it. Then you both compare a 20-digit safety code.",
        )
        Button(onClick = actions::startHost, modifier = Modifier.fillMaxWidth()) { Text("Start a chat") }
        OutlinedButton(onClick = actions::pasteAndJoin, modifier = Modifier.fillMaxWidth()) {
            Text("Paste invite and join")
        }
    }
}

@Composable
private fun HostingSection(state: ConnectionState.Hosting, actions: ConnectActions) {
    Section {
        Title(if (state.stopping) "Stopping…" else "Waiting for the other phone (up to 3 minutes)")
        Note(
            "Send this invite to the other phone by SMS or any messaging app. You can switch apps to send it; this " +
                "phone keeps waiting. The invite works once, only on this Wi-Fi. Keep it private.",
        )
        SelectionContainer {
            Text(state.invite, color = Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { actions.shareInvite(state.invite) },
                enabled = !state.stopping,
                modifier = Modifier.weight(1f),
            ) { Text("Share invite") }
            OutlinedButton(
                onClick = actions::stopWaiting,
                enabled = !state.stopping,
                modifier = Modifier.weight(1f),
            ) { Text("Stop waiting") }
        }
    }
}

@Composable
private fun CompareSection(state: ConnectionState.Comparing, actions: ConnectActions) {
    val prompt = state.prompt
    val device = PeerPins.formatDeviceId(prompt.peerDevice)
    Section {
        Title("Compare the safety code")
        when (prompt.peerStatus) {
            PeerStatus.CHANGED -> Note(
                "WARNING: the key of device $device has changed since you verified it. Resetting or reinstalling " +
                    "that phone causes this, but so could another device pretending to be it. Compare every digit, " +
                    "and tap Codes differ if you are unsure.",
                color = Danger,
            )
            PeerStatus.KNOWN -> Note(
                "Remembered phone (device $device): it uses the same key you verified before. Still compare the code.",
            )
            PeerStatus.NEW -> Note(
                "New phone (device $device). If the codes match, this phone will remember its key.",
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
        Note(
            "Hold the phones side by side and compare every digit. Each person has two minutes to answer. " +
                "Codes differ on either phone stops both phones.",
        )
        when (state.answer) {
            null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { actions.answer(true) }, modifier = Modifier.weight(1f)) { Text("Codes match") }
                OutlinedButton(onClick = { actions.answer(false) }, modifier = Modifier.weight(1f)) {
                    Text("Codes differ")
                }
            }
            true -> Note("You confirmed the code. Waiting for the other phone's answer…", color = Ink)
            false -> Note("You reported different codes. Stopping on both phones…", color = Ink)
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

@Composable
private fun PhoneSection(identitySummary: String, idle: Boolean, actions: ConnectActions) {
    Section {
        Title("This phone")
        Note("Test identity: $identitySummary.")
        TextButton(onClick = actions::copyReport) { Text("Copy test report") }
        if (idle) {
            TextButton(onClick = actions::resetKey) { Text("Reset this phone's key") }
            TextButton(onClick = actions::forgetPhones) { Text("Forget remembered phones") }
        }
    }
}

/** The four groups on two lines, so a narrow phone can show the code at a large size. */
private fun twoLines(code: String): String {
    val groups = code.trim().split(' ')
    return if (groups.size == 4) "${groups[0]} ${groups[1]}\n${groups[2]} ${groups[3]}" else code
}
