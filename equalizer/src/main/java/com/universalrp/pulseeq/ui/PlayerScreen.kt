package com.universalrp.pulseeq.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.universalrp.pulseeq.EqTab
import com.universalrp.pulseeq.EqUiState
import com.universalrp.pulseeq.EqViewModel
import com.universalrp.pulseeq.audio.PlayerEngine
import com.universalrp.pulseeq.audio.EqCore

@Composable
fun PlayerScreen(state: EqUiState, vm: EqViewModel) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) vm.playFile(uri)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = Cyan)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "PulseEQ player",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "The only player where the full 20-band engine is guaranteed: samples pass " +
                            "through PulseEQ's own DSP before they reach the speaker",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
        }

        item {
            LedSpectrum(
                engine = EqCore.eq,
                gains = state.gains,
                bypassed = state.bypass,
                brightness = state.ledBrightness,
                animate = state.ledAnimation,
                live = state.playerState == PlayerEngine.State.PLAYING,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        when (state.playerState) {
                            PlayerEngine.State.PLAYING -> "Playing"
                            PlayerEngine.State.PAUSED -> "Paused"
                            PlayerEngine.State.FINISHED -> "Finished"
                            PlayerEngine.State.ERROR -> "Could not play that file"
                            PlayerEngine.State.IDLE -> "Nothing loaded"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = when (state.playerState) {
                            PlayerEngine.State.PLAYING -> Good
                            PlayerEngine.State.ERROR -> Danger
                            else -> TextSecondary
                        },
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        state.playerTitle.ifBlank { state.lastTitle ?: "Choose an audio file that is saved on your phone" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    state.playerError?.let { err ->
                        Spacer(Modifier.height(4.dp))
                        Text(err, style = MaterialTheme.typography.bodySmall, color = Danger)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        buildString {
                            append("Position ${formatMs(state.playerPositionMs)}")
                            if (state.playerDurationMs > 0L) {
                                append(" / ${formatMs(state.playerDurationMs)}")
                            }
                            if (EqStatsRef.sampleRate > 0) {
                                append("  •  ${EqStatsRef.sampleRate} Hz stereo")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { vm.togglePlayPause() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Cyan,
                                contentColor = Color(0xFF04202A),
                            ),
                        ) {
                            Icon(
                                if (state.playerState == PlayerEngine.State.PLAYING)
                                    Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                contentDescription = null,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Play / pause", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { vm.stopPlayer() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SurfaceHigh,
                                contentColor = TextPrimary,
                            ),
                        ) {
                            Icon(Icons.Outlined.Stop, contentDescription = null)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { picker.launch(arrayOf("audio/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SurfaceHigh,
                            contentColor = TextPrimary,
                        ),
                    ) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Choose audio file")
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "The picker only shows audio files already on your phone. If the last file is " +
                            "still available, “Play / pause” resumes it without asking again.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Start,
                    )
                }
            }
        }

        item {
            PanelCard(Modifier.fillMaxWidth(), color = SurfaceHigh) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "What this player does not do",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "It plays local files — streaming is not supported. Internet access is only for update checks, not " +
                            "online music. For YouTube, Spotify and other apps, use the Background/Settings " +
                            "options: the system-wide effect or the cooperating-player sessions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Want the presets here?",
                        style = MaterialTheme.typography.labelMedium,
                        color = Cyan,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    IconChip(Icons.Outlined.MusicNote, "Open presets") { vm.navigate(EqTab.PRESETS) }
                }
            }
        }
    }
}

/** Read-only view of the engine's sample rate for the transport readout. */
private object EqStatsRef {
    val sampleRate: Int get() = com.universalrp.pulseeq.audio.EqStats.currentSampleRate
}

private fun formatMs(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
