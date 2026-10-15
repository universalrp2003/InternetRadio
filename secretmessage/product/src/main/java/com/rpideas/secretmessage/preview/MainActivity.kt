package com.rpideas.secretmessage.preview

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.delay

private val Night = Color(0xFF101922)
private val Panel = Color(0xFF192633)
private val PanelRaised = Color(0xFF223342)
private val Accent = Color(0xFFB9D7FF)
private val Warm = Color(0xFFFFC983)
private val Ink = Color(0xFFF3F6FA)
private val Muted = Color(0xFFB2BFCA)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Best-effort screenshot deterrent only; it cannot prevent every capture path.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent,
                    secondary = Warm,
                    background = Night,
                    surface = Panel,
                    onPrimary = Night,
                    onSecondary = Night,
                    onBackground = Ink,
                    onSurface = Ink,
                ),
            ) {
                SecretMessagePreviewScreen()
            }
        }
    }
}

@Composable
private fun SecretMessagePreviewScreen() {
    val messages = remember { mutableStateListOf<EphemeralPreviewMessage>() }
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }
    var nowElapsed by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var nextMessageId by remember { mutableLongStateOf(1L) }
    var drawingMode by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            val now = SystemClock.elapsedRealtime()
            nowElapsed = now
            messages.removeAll { EphemeralMessagePolicy.isExpired(now, it.expiresAtElapsedRealtime) }
            delay(500L)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    fun addLocalMessage(text: String?, strokes: List<List<PreviewPoint>>) {
        val now = SystemClock.elapsedRealtime()
        messages.add(
            EphemeralPreviewMessage(
                id = nextMessageId,
                text = text,
                drawing = strokes,
                expiresAtElapsedRealtime = EphemeralMessagePolicy.expiresAt(now),
            ),
        )
        nextMessageId += 1L
        nowElapsed = now
    }

    Scaffold(containerColor = Night) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            PreviewHeader()
            Spacer(Modifier.height(12.dp))
            LocalOnlyNotice()
            Spacer(Modifier.height(10.dp))
            ConnectionStatusCard()
            Spacer(Modifier.height(10.dp))

            val visibleMessages = EphemeralMessagePolicy.visibleMessages(messages, nowElapsed)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (visibleMessages.isEmpty()) {
                    item {
                        EmptyChatNotice(Modifier.fillMaxWidth().heightIn(min = 150.dp))
                    }
                } else {
                    items(visibleMessages, key = { it.id }) { message ->
                        PreviewMessageBubble(message, nowElapsed)
                    }
                }
            }

            if (drawingMode) {
                DrawingComposer(
                    onCancel = { drawingMode = false },
                    onAddToPreview = { strokes ->
                        addLocalMessage(text = null, strokes = strokes)
                        drawingMode = false
                    },
                )
            } else {
                PreviewComposer(
                    draft = draft,
                    onOpenDrawing = { drawingMode = true },
                )
                Spacer(Modifier.height(6.dp))
                PreviewQwertyKeyboard(
                    draft = draft,
                    onDraftChange = { draft = it },
                    onSend = {
                        val message = draft.trim()
                        if (message.isNotEmpty()) {
                            addLocalMessage(text = message, strokes = emptyList())
                            draft = ""
                        }
                    },
                )
                Text(
                    text = "Local echo only · disappears 60 seconds after Send · not encrypted",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 2.dp),
                    color = Muted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PreviewHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(14.dp),
            color = PanelRaised,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = Accent)
            }
        }
        Column(modifier = Modifier.padding(start = 11.dp)) {
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
    }
}

@Composable
private fun LocalOnlyNotice() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF342B20),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Text(
                "NOT SECURE · LOCAL PREVIEW ONLY",
                color = Warm,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "This build does not connect to another phone or encrypt messages. Do not enter sensitive content.",
                modifier = Modifier.padding(top = 4.dp),
                color = Color(0xFFFFE6C7),
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun ConnectionStatusCard() {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Panel,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(modifier = Modifier.size(8.dp), shape = CircleShape, color = Warm) {}
            Text(
                "Not connected",
                modifier = Modifier.padding(start = 9.dp),
                color = Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            Text("Pairing not enabled", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun EmptyChatNotice(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 290.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Your preview chat", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Try the in-app QWERTY keyboard or freehand canvas. Messages stay on this screen only and are removed after one minute.",
                modifier = Modifier.padding(top = 8.dp),
                color = Muted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PreviewMessageBubble(message: EphemeralPreviewMessage, nowElapsed: Long) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 5.dp))
                .background(PanelRaised)
                .padding(horizontal = 13.dp, vertical = 10.dp),
        ) {
            message.text?.let { Text(it, color = Ink, fontSize = 15.sp, lineHeight = 21.sp) }
            if (message.drawing.isNotEmpty()) {
                DrawingPreview(
                    strokes = message.drawing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .height(118.dp),
                )
            }
            Text(
                text = "Local preview · ${EphemeralMessagePolicy.remainingSeconds(nowElapsed, message.expiresAtElapsedRealtime)}s",
                modifier = Modifier.padding(top = 7.dp),
                color = Muted,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun PreviewComposer(
    draft: String,
    onOpenDrawing: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            shape = RoundedCornerShape(16.dp),
            color = Panel,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = draft.ifBlank { "Use the preview keyboard" },
                    modifier = Modifier.weight(1f),
                    color = if (draft.isBlank()) Muted else Ink,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onOpenDrawing, modifier = Modifier.padding(start = 3.dp)) {
            Icon(Icons.Outlined.Create, contentDescription = "Open freehand canvas", tint = Accent)
        }
    }
}

@Composable
private fun PreviewQwertyKeyboard(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    var uppercase by remember { mutableStateOf(false) }
    var numberMode by remember { mutableStateOf(false) }
    val rows = if (numberMode) {
        listOf("1234567890", "@#$%&*()-", "'\"/:;!?+=")
    } else {
        listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    }

    fun insert(value: String) {
        if (draft.length < 2_000) onDraftChange(draft + value)
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        KeyboardKeyRow(
            keys = rows[0].map { if (uppercase && !numberMode) it.uppercase() else it.toString() },
            onKey = { insert(if (uppercase && !numberMode) it.uppercase() else it) },
        )
        KeyboardKeyRow(
            keys = rows[1].map { if (uppercase && !numberMode) it.uppercase() else it.toString() },
            modifier = Modifier.fillMaxWidth(0.94f).align(Alignment.CenterHorizontally),
            onKey = { insert(if (uppercase && !numberMode) it.uppercase() else it) },
        )
        Row(
            modifier = Modifier.fillMaxWidth(0.98f).align(Alignment.CenterHorizontally),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            KeyboardKey(
                label = "⇧",
                modifier = Modifier.weight(1.25f),
                onClick = { uppercase = !uppercase },
            )
            rows[2].forEach { character ->
                KeyboardKey(
                    label = if (uppercase && !numberMode) character.uppercase() else character.toString(),
                    modifier = Modifier.weight(1f),
                    onClick = { insert(if (uppercase && !numberMode) character.uppercase() else character.toString()) },
                )
            }
            KeyboardKey(
                label = "⌫",
                modifier = Modifier.weight(1.25f),
                onClick = { if (draft.isNotEmpty()) onDraftChange(draft.dropLast(1)) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            KeyboardKey(
                label = if (numberMode) "ABC" else "123",
                modifier = Modifier.weight(1.3f),
                onClick = { numberMode = !numberMode },
            )
            KeyboardKey(label = "space", modifier = Modifier.weight(2.3f), onClick = { insert(" ") })
            KeyboardKey(label = ",", modifier = Modifier.weight(0.8f), onClick = { insert(",") })
            KeyboardKey(label = ".", modifier = Modifier.weight(0.8f), onClick = { insert(".") })
        }
        FilledTonalButton(
            onClick = onSend,
            enabled = draft.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(39.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = Accent,
                contentColor = Night,
                disabledContainerColor = PanelRaised,
                disabledContentColor = Muted,
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Icon(Icons.Outlined.Send, contentDescription = null, modifier = Modifier.size(16.dp))
            Text("Send local preview", modifier = Modifier.padding(start = 7.dp), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun KeyboardKeyRow(
    keys: List<String>,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onKey: (String) -> Unit,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        keys.forEach { key ->
            KeyboardKey(
                label = key,
                modifier = Modifier.weight(1f),
                onClick = { onKey(key) },
            )
        }
    }
}

@Composable
private fun KeyboardKey(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(9.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = PanelRaised,
            contentColor = Ink,
        ),
        contentPadding = PaddingValues(horizontal = 1.dp, vertical = 0.dp),
    ) {
        Text(label, fontSize = if (label.length > 1) 10.sp else 14.sp, maxLines = 1)
    }
}

@Composable
private fun DrawingComposer(
    onCancel: () -> Unit,
    onAddToPreview: (List<List<PreviewPoint>>) -> Unit,
) {
    var strokes by remember { mutableStateOf(emptyList<List<Offset>>()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Freehand canvas", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("Strokes only · no text recognition", color = Muted, fontSize = 10.sp)
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFF8F7F3))
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { start -> strokes = strokes + listOf(listOf(start)) },
                        onDrag = { change, _ ->
                            change.consume()
                            if (strokes.isNotEmpty()) {
                                strokes = strokes.dropLast(1) + listOf(strokes.last() + change.position)
                            }
                        },
                    )
                },
        ) {
            strokes.forEach { points ->
                if (points.size == 1) {
                    drawCircle(
                        color = Color(0xFF23394D),
                        radius = 3.dp.toPx(),
                        center = points.first(),
                    )
                } else if (points.size > 1) {
                    val path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(
                        path = path,
                        color = Color(0xFF23394D),
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Back") }
            FilledTonalButton(
                onClick = { strokes = emptyList() },
                modifier = Modifier.weight(1f),
            ) { Text("Clear") }
            FilledTonalButton(
                onClick = {
                    val width = canvasSize.width.coerceAtLeast(1).toFloat()
                    val height = canvasSize.height.coerceAtLeast(1).toFloat()
                    val normalized = strokes.map { stroke ->
                        stroke.map { point -> PreviewPoint(point.x / width, point.y / height) }
                    }
                    onAddToPreview(normalized)
                },
                enabled = strokes.isNotEmpty(),
                modifier = Modifier.weight(1.35f),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Accent, contentColor = Night),
            ) { Text("Add local") }
        }
    }
}

@Composable
private fun DrawingPreview(
    strokes: List<List<PreviewPoint>>,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF8F7F3)),
    ) {
        strokes.forEach { points ->
            if (points.size == 1) {
                drawCircle(
                    color = Color(0xFF23394D),
                    radius = 3.dp.toPx(),
                    center = Offset(points.first().x * size.width, points.first().y * size.height),
                )
            } else if (points.size > 1) {
                val path = Path().apply {
                    moveTo(points.first().x * size.width, points.first().y * size.height)
                    points.drop(1).forEach { lineTo(it.x * size.width, it.y * size.height) }
                }
                drawPath(
                    path = path,
                    color = Color(0xFF23394D),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}
