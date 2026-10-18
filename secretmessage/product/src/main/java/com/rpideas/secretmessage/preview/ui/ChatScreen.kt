package com.rpideas.secretmessage.preview.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rpideas.secretmessage.preview.chat.ChatTimerPolicy
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
    wording: ChatWording = ChatWording.Preview,
    /** Shows an End button in the header when a build can end a live session. */
    onEndChat: (() -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    val currentVisibilityCallback by rememberUpdatedState(onChatVisibilityChanged)

    DisposableEffect(Unit) {
        currentVisibilityCallback(true)
        onDispose { currentVisibilityCallback(false) }
    }

    // Leading items: notice, status, optional message, then messages (or the empty notice) and a footer.
    // A build may leave out the notice card, the badge and the footer by giving them blank wording.
    val showNoticeCard = wording.noticeTitle.isNotBlank() || wording.noticeBody.isNotBlank()
    val showFooter = wording.footer.isNotBlank()
    val leadingItems = (if (showNoticeCard) 1 else 0) + 1 + (if (notice != null) 1 else 0)
    val totalItems = leadingItems + (if (messages.isEmpty()) 1 else messages.size) + (if (showFooter) 1 else 0)
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(totalItems - 1)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Short screens (for example landscape) keep a shorter composer so the controls stay visible.
        val compact = maxHeight < 600.dp
        // The drawing pad aims for about half of this screen's height (owner request after testing 0.2.0).
        val padSide = DrawingPadLayout.padSideDp(maxWidth.value, maxHeight.value).dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            PreviewHeader(wording = wording, onOpenSettings = onOpenSettings, onEndChat = onEndChat)

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showNoticeCard) {
                    item { LocalOnlyNotice(wording) }
                }
                item {
                    ConnectionStatusCard(
                        sessionStatus = sessionStatus,
                        statusDescription = statusDescription,
                        pairingLabel = wording.pairingLabel,
                    )
                }
                if (notice != null) {
                    item { Text(text = notice, color = Warm, fontSize = 12.sp) }
                }
                if (messages.isEmpty()) {
                    item {
                        EmptyChatNotice(
                            wording,
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 96.dp),
                        )
                    }
                } else {
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(message = message, nowElapsedMillis = nowElapsedMillis)
                    }
                }
                if (showFooter) {
                    item {
                        Text(
                            text = wording.footer,
                            modifier = Modifier.fillMaxWidth(),
                            color = Muted,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                        )
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
                padSide = padSide,
                compact = compact,
            )
        }
    }
}

@Composable
private fun PreviewHeader(wording: ChatWording, onOpenSettings: () -> Unit, onEndChat: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = RoundedCornerShape(12.dp),
            color = PanelRaised,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = Accent)
            }
        }
        Column(modifier = Modifier.padding(start = 10.dp)) {
            Text("Secret Message", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(wording.subtitle, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Muted)
        }
        Spacer(Modifier.weight(1f))
        if (wording.badge.isNotBlank()) {
            Surface(shape = CircleShape, color = Color(0xFF3A3026)) {
                Text(
                    wording.badge,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                    color = Warm,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (onEndChat != null) {
            TextButton(onClick = onEndChat) { Text(t("End"), color = Warm, fontWeight = FontWeight.Bold) }
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Outlined.Settings, contentDescription = wording.settingsButtonDescription, tint = Accent)
        }
    }
}

@Composable
private fun LocalOnlyNotice(wording: ChatWording) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF342B20),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text(
                wording.noticeTitle,
                color = Warm,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                wording.noticeBody,
                modifier = Modifier.padding(top = 3.dp),
                color = Color(0xFFFFE6C7),
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun ConnectionStatusCard(sessionStatus: MlsSessionStatus, statusDescription: String, pairingLabel: String) {
    val label = when (sessionStatus) {
        MlsSessionStatus.DISCONNECTED -> t("Not connected")
        MlsSessionStatus.AWAITING_MLS_WELCOME_OR_KEYPACKAGE -> t("Setting up session")
        MlsSessionStatus.VERIFYING_MLS_EPOCH_AUTHENTICATION -> t("Verifying session")
        MlsSessionStatus.CONNECTED_MLS_VERIFIED -> t("Connected")
        MlsSessionStatus.ERROR -> t("Connection error")
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Panel,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val connected = sessionStatus == MlsSessionStatus.CONNECTED_MLS_VERIFIED
                Surface(modifier = Modifier.size(8.dp), shape = CircleShape, color = if (connected) SendGreen else Warm) {}
                Text(
                    label,
                    modifier = Modifier.padding(start = 8.dp),
                    color = Ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                Text(pairingLabel, color = Muted, fontSize = 11.sp)
            }
            Text(statusDescription, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun EmptyChatNotice(wording: ChatWording, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 300.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(wording.emptyTitle, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                wording.emptyBody,
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
                        .semantics { contentDescription = t("Drawn picture") }
                        .widthIn(min = 160.dp, max = 240.dp)
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
        MessageDeliveryState.SENDING_UNDELIVERED -> t("Not delivered · removed in {0}s", remaining)
        MessageDeliveryState.DELIVERED_WAITING_VIEW ->
            if (message.isFromLocalUser) t("Delivered · waiting to be viewed") else t("New · countdown starts when viewed")
        MessageDeliveryState.VIEWED_COUNTING_DOWN -> t("Viewed · {0} left", formatClock(remaining))
    }
}

private fun formatClock(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
