package com.rpideas.secretmessage.preview.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rpideas.secretmessage.preview.audio.BuiltInNotificationSound
import com.rpideas.secretmessage.preview.chat.ChatTimerPolicy
import com.rpideas.secretmessage.preview.chat.MAX_VIEW_SECONDS
import com.rpideas.secretmessage.preview.chat.MIN_VIEW_SECONDS
import com.rpideas.secretmessage.preview.chat.VIEW_SECONDS_STEP
import com.rpideas.secretmessage.preview.settings.PreviewSettings
import kotlin.math.roundToInt

@Composable
internal fun SettingsScreen(
    settings: PreviewSettings,
    appLockMessage: String?,
    versionLabel: String,
    onBack: () -> Unit,
    onViewSecondsChange: (Int) -> Unit,
    onSelectTone: (BuiltInNotificationSound) -> Unit,
    onPreviewTone: (BuiltInNotificationSound) -> Unit,
    onAppLockChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    var sliderSeconds by remember(settings.viewSeconds) { mutableFloatStateOf(settings.viewSeconds.toFloat()) }
    val shownSeconds = ChatTimerPolicy.snapViewSeconds(sliderSeconds.roundToInt())
    val sliderSteps = (MAX_VIEW_SECONDS - MIN_VIEW_SECONDS) / VIEW_SECONDS_STEP - 1

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Preview settings", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Back to chat") }
        }

        SettingsSection(title = "Recipient view timer") {
            Text("$shownSeconds seconds", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Slider(
                value = sliderSeconds,
                onValueChange = { sliderSeconds = it },
                valueRange = MIN_VIEW_SECONDS.toFloat()..MAX_VIEW_SECONDS.toFloat(),
                steps = sliderSteps,
                onValueChangeFinished = {
                    onViewSecondsChange(ChatTimerPolicy.snapViewSeconds(sliderSeconds.roundToInt()))
                },
            )
            SettingsNote(
                "Choose 60 to 300 seconds; the timer cannot be turned off. In the planned secure app the countdown " +
                    "starts only when the other phone views an authenticated delivery. This preview cannot deliver " +
                    "messages, so the countdown never starts here.",
            )
        }

        SettingsSection(title = "Notification tone") {
            BuiltInNotificationSound.entries.forEach { sound ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectTone(sound) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = settings.toneId == sound.id, onClick = { onSelectTone(sound) })
                    Column(modifier = Modifier.weight(1f)) {
                        Text(sound.displayName, color = Ink, fontSize = 14.sp)
                        Text(sound.subtitle, color = Muted, fontSize = 11.sp)
                    }
                    IconButton(onClick = { onPreviewTone(sound) }) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = "Play ${sound.displayName}", tint = Accent)
                    }
                }
            }
            SettingsNote(
                "Six original tones generated on this phone. They play at the notification volume. " +
                    "Message notifications are not enabled in this preview and would never show message content.",
            )
        }

        SettingsSection(title = "App lock") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Require unlock to open", color = Ink, fontSize = 14.sp)
                    Text(
                        "Fingerprint, face or this phone's screen-lock PIN, pattern or password.",
                        color = Muted,
                        fontSize = 11.sp,
                    )
                }
                Switch(checked = settings.appLockEnabled, onCheckedChange = onAppLockChange)
            }
            if (appLockMessage != null) {
                Text(appLockMessage, color = Warm, fontSize = 12.sp)
            }
            SettingsNote(
                "The lock is local to this phone and hides the chat when you return to the app. " +
                    "Biometric data stays with Android and is never sent anywhere.",
            )
        }

        SettingsSection(title = "What this preview is") {
            SettingsNote(
                "Secret Message $versionLabel is a local-only debug preview of the WhisperPad product screens: " +
                    "built-in keyboard, drawing pad, timers, tones and app lock. No MLS transport, pairing or network " +
                    "permission is included, and no message is saved. It is not a secure messenger.",
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Panel,
        modifier = Modifier.fillMaxWidth(),
    ) {
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
private fun SettingsNote(text: String) {
    Text(text, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
}
