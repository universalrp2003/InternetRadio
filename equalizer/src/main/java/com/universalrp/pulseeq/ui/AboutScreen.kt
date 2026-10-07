package com.universalrp.pulseeq.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.universalrp.pulseeq.EqViewModel

@Composable
fun AboutScreen(vm: EqViewModel) {
    val sourceContext = androidx.compose.ui.platform.LocalContext.current
    val installedVersion = androidx.compose.runtime.remember {
        runCatching { sourceContext.packageManager.getPackageInfo(sourceContext.packageName, 0).versionName }.getOrNull() ?: "unknown"
    }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = Cyan)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    "About PulseEQ",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "PulseEQ $installedVersion • 20-band equalizer",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            androidx.compose.material3.TextButton(onClick = {
                runCatching { sourceContext.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://github.com/universalrp2003/InternetRadio"))) }
            }) { Text("Source code · GNU GPL v3") }
            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "What PulseEQ is",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Cyan,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "A 20-band equalizer with real DSP of its own — 20 peaking filters per channel, a " +
                            "preamp and a soft limiter — plus an LED spectrum that lights up to the real " +
                            "audio energy of each band, ready-made presets for music, movies, speech and " +
                            "more, and a foreground service that keeps it all running in the background.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }

            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Where the sound is equalized — honestly",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "✅ PulseEQ player — guaranteed. Your file is decoded, filtered by our 20-band " +
                            "engine and played back. The LEDs are live here.\n\n" +
                            "✅ Cooperating players — Poweramp, VLC, Musicolet and others announce their " +
                            "audio session, and PulseEQ shapes it through Android's effect API. No capture, " +
                            "no root.\n\n" +
                            "⚠️ System-wide effect — PulseEQ tries to attach one equalizer to the whole " +
                            "output mix. Google deprecated this and many Android 11+ ROMs refuse it for " +
                            "normal apps; the app tells you whether yours allowed it. When it works, every " +
                            "app is equalized.\n\n" +
                            "❌ Apps that neither use our player nor announce a session — e.g. YouTube or " +
                            "Spotify — cannot be equalized without root. Android simply does not hand out " +
                            "that access, and PulseEQ will not pretend otherwise. (Apps that do this use " +
                            "internal audio capture plus privileged tricks, and Spotify blocks capture " +
                            "outright.)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }

            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Security, contentDescription = null, tint = Violet)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Privacy",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "• No INTERNET permission — PulseEQ cannot send anything anywhere, even by accident.\n" +
                            "• No microphone permission: the LED spectrum reads the audio you are already " +
                            "playing, not the room.\n" +
                            "• No accounts, no ads, no analytics.\n" +
                            "• Your settings live in the app's own private storage.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }

            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Diagnostics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        vm.state.value.engineInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconChip(Icons.Outlined.Info, "Copy info") { vm.copyDiagnostics() }
                        IconChip(Icons.Outlined.Info, "Refresh", tint = Violet) { vm.refresh() }
                    }
                }
            }

            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Listening safety",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Boosting bands adds real energy — that is why the preamp drops automatically and " +
                            "a soft limiter sits on the output. Still, keep phone volume at a sane level, " +
                            "especially with headphones, and give your ears breaks.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
