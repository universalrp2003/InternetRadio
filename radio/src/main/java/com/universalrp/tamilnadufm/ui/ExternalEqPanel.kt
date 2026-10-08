package com.universalrp.tamilnadufm.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.universalrp.tamilnadufm.audio.ExternalEqService

@Composable
fun ExternalEqPanel() {
    val context = LocalContext.current
    val running by ExternalEqService.running.collectAsState()
    val status by ExternalEqService.status.collectAsState()
    var error by remember { mutableStateOf("") }
    fun start(action: String) {
        error = ""
        runCatching { ContextCompat.startForegroundService(context, Intent(context, ExternalEqService::class.java).setAction(action)) }
            .onFailure { error = "Could not start background EQ. Check app battery/background restrictions." }
    }
    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("Other apps / system-wide EQ", color = TextPrimary)
            Text("Built-in PulseEQ-style session control. Turn off the separate PulseEQ app to avoid effects competing. Start this before playback in the other app. No recording, no root; not every player is supported.", color = TextSecondary)
            Text(status, color = Teal)
            if (error.isNotEmpty()) Text(error, color = TextSecondary)
            Row {
                TextButton(onClick = { if (running) context.stopService(Intent(context, ExternalEqService::class.java)) else start("sessions") }) {
                    Text(if (running) "Stop other-app EQ" else "Enable other-app EQ")
                }
            }
            TextButton(onClick = { start("global") }) { Text("Try global mix (device dependent)") }
        }
    }
}
