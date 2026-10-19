package com.rpideas.secretmessage.wifitest

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rpideas.secretmessage.preview.ui.Accent
import com.rpideas.secretmessage.preview.ui.Ink
import com.rpideas.secretmessage.preview.ui.Muted
import com.rpideas.secretmessage.preview.ui.Panel
import com.rpideas.secretmessage.wifitest.updates.AppUpdates

private val Danger = Color(0xFFFF8A80)

/** What the app's own settings sections show. */
internal data class AppSettingsInfo(
    val versionLabel: String,
    val identitySummary: String,
    val engineInfo: String,
    val readyEnabled: Boolean,
    val doorbell: Doorbell.Link,
    val hasDoorbellContacts: Boolean,
    val idle: Boolean,
    val message: String?,
    /** Changes when the app returns to the screen, so the battery status is read again. */
    val resumeCount: Int = 0,
)

/**
 * The app's sections below the product settings (owner requests for 1.1.0): language, ready for
 * chats, this phone (report, reset, forget), the guide, and About with the verified updater and
 * the other RP Ideas apps, as in Oor Arivippu.
 */
@Composable
internal fun AppSettingsSections(info: AppSettingsInfo, actions: ConnectActions, onOpenGuide: () -> Unit) {
    val context = LocalContext.current
    var confirm by remember { mutableStateOf<String?>(null) }

    if (info.message != null) {
        Text(tr(info.message), color = Accent, fontSize = 13.sp, lineHeight = 18.sp)
    }

    AppSection(tr("Language")) {
        LanguageToggle()
        AppNote(tr("The chat keyboard stays English. Drawn letters and pictures work in any language."))
    }

    AppSection(tr("Ready for chats")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(tr("Remembered phones can call this phone"), color = Ink, fontSize = 14.sp)
                Text(readyStatus(info), color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
            }
            Switch(checked = info.readyEnabled, onCheckedChange = actions::setReady)
        }
        val unrestricted = batteryUnrestricted(context)
        AppNote(
            if (unrestricted) {
                tr("Battery: not restricted. Android lets Secret Message stay ready.")
            } else {
                tr("Battery optimization is on, so Android may delay or miss chat requests. Allow Secret Message to run in the background.")
            },
        )
        if (!unrestricted) {
            OutlinedButton(onClick = actions::openBatterySettings, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Allow background use"))
            }
        }
        AppNote(
            tr(
                "While ready, a notification says only \"Secret Message\" and \"Ready for chats\". On Xiaomi, Redmi " +
                    "and POCO phones also turn on Autostart for Secret Message (see the guide).",
            ),
        )
    }

    AppSection(tr("This phone")) {
        AppNote(tr("Identity: {0}.", info.identitySummary))
        Text(tr("Engine: {0}", info.engineInfo), color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
        TextButton(onClick = actions::copyReport) { Text(tr("Copy connection report")) }
        if (info.idle) {
            TextButton(onClick = { confirm = "reset" }) { Text(tr("Reset this phone's key")) }
            TextButton(onClick = { confirm = "forget" }) { Text(tr("Forget remembered phones"), color = Danger) }
        } else {
            AppNote(tr("Reset and Forget are available when no chat is open."))
        }
    }

    AppSection(tr("Guide")) {
        AppNote(tr("How to start a chat, call a remembered phone, and keep Secret Message ready."))
        OutlinedButton(onClick = onOpenGuide, modifier = Modifier.fillMaxWidth()) { Text(tr("Open the guide")) }
    }

    AboutSection(info.versionLabel, context)

    when (confirm) {
        "reset" -> ConfirmDialog(
            title = tr("Reset this phone's key?"),
            body = tr(
                "This phone gets a new key and a new doorbell. Remembered phones will show a key-change warning " +
                    "and need the safety-code comparison in the next chat.",
            ),
            confirmLabel = tr("Reset key"),
            onConfirm = {
                confirm = null
                actions.resetKey()
            },
            onDismiss = { confirm = null },
        )
        "forget" -> ConfirmDialog(
            title = tr("Forget all remembered phones?"),
            body = tr(
                "Their names, doorbells and keys are removed from this phone. The next chat with each phone needs " +
                    "an invite and the safety-code comparison.",
            ),
            confirmLabel = tr("Forget"),
            onConfirm = {
                confirm = null
                actions.forgetPhones()
            },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun AboutSection(versionLabel: String, context: Context) {
    AppSection(tr("About")) {
        Text(tr("Version: {0}", versionLabel), color = Ink, fontSize = 13.sp)
        HorizontalDivider(color = Muted.copy(alpha = 0.3f))
        Text(tr("Contributors"), color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        AppNote(tr("Project development: Ramesh prathap .R · universalrp2003@gmail.com"))
        AppNote(tr("AI software assistance: Arena.ai Agent Mode"))
        HorizontalDivider(color = Muted.copy(alpha = 0.3f))
        LinkButton(tr("Source code — InternetRadio"), "https://github.com/universalrp2003/InternetRadio", context)
        LinkButton(tr("Official downloads — RP-Android-Apps"), "https://github.com/universalrp2003/RP-Android-Apps", context)
        HorizontalDivider(color = Muted.copy(alpha = 0.3f))
        Text(tr("RP Ideas apps"), color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        OTHER_APPS.forEach { (label, url) -> LinkButton(tr(label), url, context) }
        HorizontalDivider(color = Muted.copy(alpha = 0.3f))
        AppUpdates.Control("Secret-Message")
    }
}

/** The other RP Ideas apps' official releases, as listed in Oor Arivippu's About. */
private val OTHER_APPS = listOf(
    "Live Guard (CleanSweep)" to "https://github.com/universalrp2003/RP-Android-Apps/releases/tag/live-guard-v2.46",
    "Ramesh Radio" to "https://github.com/universalrp2003/RP-Android-Apps/releases/tag/ramesh-radio-v1.11",
    "PulseEQ" to "https://github.com/universalrp2003/RP-Android-Apps/releases/tag/pulseeq-v1.5",
    "RP Vocal Remover Studio" to "https://github.com/universalrp2003/RP-Android-Apps/releases/tag/rp-vocal-remover-studio-v0.1.11",
    "Oor Arivippu" to "https://github.com/universalrp2003/RP-Android-Apps/releases/tag/oor-arivippu-v1.1.0",
)

@Composable
private fun LinkButton(label: String, url: String, context: Context) {
    TextButton(
        onClick = {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(label) }
}

@Composable
private fun ConfirmDialog(title: String, body: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = Danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

@Composable
internal fun AppSection(title: String, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = Panel, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
internal fun AppNote(text: String) {
    Text(text, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
}

private fun readyStatus(info: AppSettingsInfo): String = when {
    !info.readyEnabled -> tr("Off: remembered phones see this phone as not reachable.")
    !info.hasDoorbellContacts -> tr("Starts after your first chat with a phone that has Secret Message 1.1.0 or newer.")
    info.doorbell == Doorbell.Link.ONLINE -> tr("Ready: connected to the relay.")
    info.doorbell == Doorbell.Link.CONNECTING -> tr("Connecting to the relay…")
    info.doorbell == Doorbell.Link.RETRYING -> tr("No connection right now; trying again.")
    else -> tr("Starting…")
}

private fun batteryUnrestricted(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
