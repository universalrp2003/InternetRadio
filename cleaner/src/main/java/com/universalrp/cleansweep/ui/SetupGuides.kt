package com.universalrp.cleansweep.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.universalrp.cleansweep.ai.AiProvider
import com.universalrp.cleansweep.data.AppLang
import com.universalrp.cleansweep.data.Lang
import com.universalrp.cleansweep.data.tr
import com.universalrp.cleansweep.voice.Announcer

object SetupGuides {
    const val API = "Open the official provider link and sign in. If your account allows API access, create a key, paste it here, save, and run the generation test. Never share the key. Chat subscriptions and API billing are separate; limits or charges may apply."
    const val FREE = "This shared endpoint mode does not need your key. Availability and quotas can change; free access is not guaranteed. You must explicitly choose another provider if this one is unavailable."
    const val CUSTOM = "Use only a trusted OpenAI-compatible endpoint and its own API key instructions. There is no universal signup link for custom servers. Check who operates it before sending questions or reports. Never share your key."
    const val VIRUSTOTAL = "Open the official VirusTotal site, sign in, and find API key in your account menu. Paste it in Live Guard only if you choose the second-opinion hash check. Quotas and terms vary. Live Guard sends a file hash, not the APK file. Never share the key."
    const val CORE = "Core Live Guard does not read other apps' notifications or messages. There is no notification-listener service, no SMS, Contacts or call-log access, no Accessibility service and no device-admin capability in this app. Ordinary Live Guard alerts about your own phone (battery, charging, storage and the daily brief) remain and follow your switches."
    const val SETUP = "Review each optional function before enabling it. Background monitoring, voice and sensitive Android access are your choices. Grant only the permissions you need. The app keeps your saved off choices and cannot bypass Android confirmation or power restrictions."
}

@Composable
fun SetupGuideButton(script: String) {
    val context = LocalContext.current
    var show by remember { mutableStateOf(false) }
    TextButton(onClick = { show = true }) { Text(tr("Help and voice guide")) }
    if (show) AlertDialog(onDismissRequest = { show = false }, title = { Text(tr("Setup guide")) }, text = { Column { Text(tr(script)) } },
        confirmButton = { TextButton(onClick = {
            val language = Lang.languageIn(context)
            Announcer.speakNow(context, Lang.translate(language, script), language)
        }) { Text(tr("Read guide aloud")) } },
        dismissButton = { Column {
            TextButton(onClick = { Announcer.stop() }) { Text(tr("Stop voice")) }
            TextButton(onClick = { show = false }) { Text(tr("Close")) }
        } })
}

@Composable
fun ProviderKeyHelp(provider: AiProvider) {
    val context = LocalContext.current
    val script = when (provider) { AiProvider.FREE -> SetupGuides.FREE; AiProvider.CUSTOM -> SetupGuides.CUSTOM; else -> SetupGuides.API }
    Text(tr(script), style = MaterialTheme.typography.bodySmall)
    if (provider.signupUrl.isNotBlank()) TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(provider.signupUrl))) } }) {
        Text(tr(if (provider.needsKey) "Get API key — official provider" else "Open official endpoint help"))
    }
    SetupGuideButton(script)
}

@Composable
fun VirusTotalKeyHelp() {
    val context = LocalContext.current
    Text(tr("VirusTotal API access is optional. Availability, quotas and terms depend on your account; no permanent free quota is promised."), style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.virustotal.com/gui/my-apikey"))) } }) {
        Text(tr("Get VirusTotal API key — official site"))
    }
    TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://docs.virustotal.com/docs/please-give-me-an-api-key"))) } }) {
        Text(tr("VirusTotal key help"))
    }
    SetupGuideButton(SetupGuides.VIRUSTOTAL)
}
