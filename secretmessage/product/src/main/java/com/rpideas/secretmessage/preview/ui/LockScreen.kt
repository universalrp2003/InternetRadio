package com.rpideas.secretmessage.preview.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shown instead of the chat while the optional app lock is engaged; no message content is composed. */
@Composable
internal fun LockScreen(
    message: String?,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = Accent, modifier = Modifier.size(44.dp))
            Text(t("Secret Message is locked"), color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                t(
                    "Unlock with your fingerprint, face or screen-lock PIN. Messages stay hidden while locked " +
                        "and their timers keep running.",
                ),
                color = Muted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
            )
            if (message != null) {
                Text(message, color = Warm, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
            Button(onClick = onUnlock) { Text(t("Unlock")) }
        }
    }
}
