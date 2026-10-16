package com.rpideas.secretmessage.preview.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rpideas.secretmessage.preview.chat.ChatTimerPolicy
import com.rpideas.secretmessage.preview.chat.UNDELIVERED_TTL_MILLIS
import com.rpideas.secretmessage.preview.model.EphemeralMessage
import com.rpideas.secretmessage.preview.model.InlineSegment
import com.rpideas.secretmessage.preview.model.MessageDeliveryState
import com.rpideas.secretmessage.preview.model.VectorStroke
import com.rpideas.secretmessage.preview.transport.MlsSessionStatus

@Composable
internal fun ChatScreen(
    messages: List<EphemeralMessage>,
    composer: List<InlineSegment>,
    canSend: Boolean,
    nowElapsedMillis: Long,
    sessionStatus: MlsSessionStatus,
    statusDescription: String,
    notice: String?,
    onTypeEnglishChar: (String) -> Unit,
    onBackspace: () -> Unit,
    onInsertInlineGlyph: (List<VectorStroke>, Float) -> Unit,
    onSendInlineMessage: () -> Unit,
    onSendFullPicture: (List<VectorStroke>) -> Unit,
    onOpenSettings: () -> Unit,
    onChatVisibilityChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val currentVisibilityCallback by rememberUpdatedState(onChatVisibilityChanged)

    DisposableEffect(Unit) {
        currentVisibilityCallback(true)
        onDispose { currentVisibilityCallback(false) }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        PreviewHeader(onOpenSettings = onOpenSettings)
        Spacer(Modifier.height(8.dp))
        LocalOnlyNotice()
        Spacer(Modifier.height(6.dp))
        ConnectionStatusCard(sessionStatus = sessionStatus, statusDescription = statusDescription)
        if (notice != null) {
            Text(
                text = notice,
                color = Warm,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    EmptyChatNotice(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                    )
                }
            } else {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message = message, nowElapsedMillis = nowElapsedMillis)
                }
            }
        }

        CustomInputDeck(
            composerSegments = composer,
            canSend = canSend,
            onTypeEnglishChar = onTypeEnglishChar,
            onBackspace = onBackspace,
            onInsertInlineGlyph = onInsertInlineGlyph,
            onSendInlineMessage = onSendInlineMessage,
            onSendFullPicture = onSendFullPicture,
        )
        Text(
            text = "No connection in this preview · unsent messages are removed after " +
                "${UNDELIVERED_TTL_MILLIS / 1_000L} seconds · not encrypted",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            color = Muted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PreviewHeader(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(14.dp),
            color = PanelRaised,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = Accent)
            }
        }
        Column(modifier = Modifier.padding(start = 10.dp)) {
            Text("Secret Message", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("LOCAL UI PREVIEW", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Muted)
        }
        Spacer(Modifier.weight(1f))
        Surface(shape = CircleShape, color = Color(0xFF3A3026)) {
            Text(
                "PREVIEW",
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                color = Warm,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Outlined.Settings, contentDescription = "Preview settings", tint = Accent)
        }
    }
}

@Composable
private fun LocalOnlyNotice() {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF342B20),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text(
                "NOT SECURE · LOCAL PREVIEW ONLY",
                color = Warm,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "This build does not connect to another phone or encrypt messages. Do not enter sensitive content.",
                modifier = Modifier.padding(top = 3.dp),
                color = Color(0xFFFFE6C7),
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun ConnectionStatusCard(sessionStatus: MlsSessionStatus, statusDescription: String) {
    val label = when (sessionStatus) {
        MlsSessionStatus.DISCONNECTED -> "Not connected"
        MlsSessionStatus.AWAITING_MLS_WELCOME_OR_KEYPACKAGE -> "Setting up session"
        MlsSessionStatus.VERIFYING_MLS_EPOCH_AUTHENTICATION -> "Verifying session"
        MlsSessionStatus.CONNECTED_MLS_VERIFIED -> "Connected"
        MlsSessionStatus.ERROR -> "Connection error"
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Panel,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(8.dp), shape = CircleShape, color = Warm) {}
                Text(
                    label,
                    modifier = Modifier.padding(start = 8.dp),
                    color = Ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                Text("Pairing not enabled", color = Muted, fontSize = 11.sp)
            }
            Text(statusDescription, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun EmptyChatNotice(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 300.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Your preview chat", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Type with the built-in English keyboard, or open the drawing pad to draw letters in any " +
                    "language or a picture. Nothing leaves this phone; unsent messages are removed after 30 seconds.",
                modifier = Modifier.padding(top = 6.dp),
                color = Muted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageBubble(message: EphemeralMessage, nowElapsedMillis: Long) {
    val alignment = if (message.isFromLocalUser) Arrangement.End else Arrangement.Start
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = alignment) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (message.isFromLocalUser) PanelRaised else Panel)
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            val content = message.content
            if (content.fullPictureStrokes.isNotEmpty()) {
                VectorStrokeThumbnail(
                    strokes = content.fullPictureStrokes,
                    modifier = Modifier
                        .widthIn(min = 180.dp, max = 276.dp)
                        .aspectRatio(PICTURE_ASPECT_RATIO)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CanvasBackground),
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    content.inlineSegments.forEach { segment ->
                        when (segment) {
                            is InlineSegment.EnglishText -> Text(
                                text = segment.text,
                                color = Ink,
                                fontSize = 15.sp,
                                lineHeight = 21.sp,
                                modifier = Modifier.align(Alignment.CenterVertically),
                            )
                            is InlineSegment.DrawnGlyph -> InlineGlyph(segment)
                        }
                    }
                }
            }
            Text(
                text = statusLine(message, nowElapsedMillis),
                modifier = Modifier.padding(top = 6.dp),
                color = Muted,
                fontSize = 10.sp,
            )
        }
    }
}

private fun statusLine(message: EphemeralMessage, nowElapsedMillis: Long): String {
    val deadline = message.deadlineElapsedMillis
    val remaining = if (deadline == null) 0 else ChatTimerPolicy.remainingSeconds(nowElapsedMillis, deadline)
    return when (message.deliveryState) {
        MessageDeliveryState.SENDING_UNDELIVERED -> "Not delivered · removed in ${remaining}s"
        MessageDeliveryState.DELIVERED_WAITING_VIEW ->
            if (message.isFromLocalUser) "Delivered · waiting to be viewed" else "New · countdown starts when viewed"
        MessageDeliveryState.VIEWED_COUNTING_DOWN -> "Viewed · ${formatClock(remaining)} left"
    }
}

private fun formatClock(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
