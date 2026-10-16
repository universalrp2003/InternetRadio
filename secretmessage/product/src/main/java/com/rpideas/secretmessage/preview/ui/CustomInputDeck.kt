package com.rpideas.secretmessage.preview.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rpideas.secretmessage.preview.chat.StrokePolicy
import com.rpideas.secretmessage.preview.model.InlineSegment
import com.rpideas.secretmessage.preview.model.PayloadLimits
import com.rpideas.secretmessage.preview.model.PayloadValidator
import com.rpideas.secretmessage.preview.model.VectorStroke

internal enum class InputDeckMode { ENGLISH_KEYBOARD, DRAWING_PAD }

internal enum class DrawingPadSubMode {
    /** Draw a letter or word in any script and insert it inline with typed text. */
    INLINE_LETTER,

    /** Draw a standalone picture that is sent as its own message. */
    FULL_PICTURE,
}

internal enum class DrawnLetterSizePreset(val label: String, val heightDp: Float) {
    SMALL("Small", 26f),
    MEDIUM("Medium", 38f),
    LARGE("Large", 54f),
}

/** Letter pads are square so a glyph keeps its shape inline; pictures use a 3:2 surface. */
internal const val LETTER_PAD_HEIGHT_DP = 140f
internal const val PICTURE_PAD_HEIGHT_DP = 150f
internal const val COMPACT_LETTER_PAD_HEIGHT_DP = 96f
internal const val COMPACT_PICTURE_PAD_HEIGHT_DP = 100f
internal const val PICTURE_ASPECT_RATIO = 1.5f
private const val DEFAULT_LINE_WIDTH_DP = 5f

/**
 * WhisperPad built-in English keyboard plus switchable drawing pad. It never opens the system
 * keyboard, transmits strokes rather than recognized words, and uses no translation or fonts.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CustomInputDeck(
    composerSegments: List<InlineSegment>,
    canSend: Boolean,
    onTypeEnglishChar: (String) -> Unit,
    onBackspace: () -> Unit,
    onInsertInlineGlyph: (List<VectorStroke>, Float) -> Unit,
    onSendInlineMessage: () -> Unit,
    onSendFullPicture: (List<VectorStroke>) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    var deckMode by remember { mutableStateOf(InputDeckMode.ENGLISH_KEYBOARD) }
    var isShifted by remember { mutableStateOf(false) }
    var isSymbolLayer by remember { mutableStateOf(false) }
    var drawSubMode by remember { mutableStateOf(DrawingPadSubMode.INLINE_LETTER) }
    var lineWidthDp by remember { mutableFloatStateOf(DEFAULT_LINE_WIDTH_DP) }
    var letterSizePreset by remember { mutableStateOf(DrawnLetterSizePreset.MEDIUM) }
    val completedStrokes = remember { mutableStateListOf<VectorStroke>() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DeckBackground, RoundedCornerShape(14.dp))
            .padding(6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ComposerBackground, RoundedCornerShape(10.dp))
                .border(1.dp, ComposerBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(max = if (compact) 44.dp else 72.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (composerSegments.isEmpty()) {
                    Text(
                        text = "Type with the built-in English keyboard or draw letters in any language",
                        color = Muted,
                        fontSize = 13.sp,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        composerSegments.forEach { segment ->
                            when (segment) {
                                is InlineSegment.EnglishText -> Text(
                                    text = segment.text,
                                    color = Ink,
                                    fontSize = 16.sp,
                                    modifier = Modifier.align(Alignment.CenterVertically),
                                )
                                is InlineSegment.DrawnGlyph -> InlineGlyph(segment)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onSendInlineMessage,
                enabled = canSend,
                colors = ButtonDefaults.buttonColors(containerColor = SendGreen, contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text("Send", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(6.dp))

        if (deckMode == InputDeckMode.ENGLISH_KEYBOARD) {
            EnglishKeyboardLayout(
                isShifted = isShifted,
                isSymbolLayer = isSymbolLayer,
                onKeyPress = { key ->
                    onTypeEnglishChar(key)
                    if (isShifted) isShifted = false
                },
                onToggleShift = { isShifted = !isShifted },
                onToggleSymbols = { isSymbolLayer = !isSymbolLayer },
                onBackspace = onBackspace,
                onSwitchToDrawPad = { deckMode = InputDeckMode.DRAWING_PAD },
            )
        } else {
            val padHeightDp = when {
                drawSubMode == DrawingPadSubMode.INLINE_LETTER && compact -> COMPACT_LETTER_PAD_HEIGHT_DP
                drawSubMode == DrawingPadSubMode.INLINE_LETTER -> LETTER_PAD_HEIGHT_DP
                compact -> COMPACT_PICTURE_PAD_HEIGHT_DP
                else -> PICTURE_PAD_HEIGHT_DP
            }
            val strokeLimit = if (drawSubMode == DrawingPadSubMode.INLINE_LETTER) {
                PayloadLimits.MAX_STROKES_PER_GLYPH
            } else {
                PayloadLimits.MAX_STROKES_PER_PICTURE
            }
            val pointLimit = if (drawSubMode == DrawingPadSubMode.INLINE_LETTER) {
                PayloadLimits.MAX_POINTS_PER_GLYPH
            } else {
                PayloadLimits.MAX_POINTS_PER_MESSAGE
            }
            val pointsUsed = PayloadValidator.pointCount(completedStrokes)
            val acceptsStrokes = completedStrokes.size < strokeLimit && pointsUsed < pointLimit

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DeckButton(
                    label = "English keyboard",
                    background = KeyBorder,
                    onClick = { deckMode = InputDeckMode.ENGLISH_KEYBOARD },
                )
                FilterChip(
                    selected = drawSubMode == DrawingPadSubMode.INLINE_LETTER,
                    onClick = {
                        if (drawSubMode != DrawingPadSubMode.INLINE_LETTER) completedStrokes.clear()
                        drawSubMode = DrawingPadSubMode.INLINE_LETTER
                    },
                    label = { Text("Letter", fontSize = 12.sp) },
                )
                FilterChip(
                    selected = drawSubMode == DrawingPadSubMode.FULL_PICTURE,
                    onClick = {
                        if (drawSubMode != DrawingPadSubMode.FULL_PICTURE) completedStrokes.clear()
                        drawSubMode = DrawingPadSubMode.FULL_PICTURE
                    },
                    label = { Text("Picture", fontSize = 12.sp) },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Line ${lineWidthDp.toInt()}",
                    color = Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.width(52.dp),
                )
                Slider(
                    value = lineWidthDp,
                    onValueChange = { lineWidthDp = it },
                    valueRange = 2f..18f,
                    modifier = Modifier.weight(1f),
                )
                if (drawSubMode == DrawingPadSubMode.INLINE_LETTER) {
                    DrawnLetterSizePreset.entries.forEach { preset ->
                        FilterChip(
                            selected = letterSizePreset == preset,
                            onClick = { letterSizePreset = preset },
                            label = { Text(preset.label.take(1), fontSize = 12.sp) },
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .semantics { contentDescription = "${preset.label} letter size" },
                        )
                    }
                }
            }

            val surfaceModifier = if (drawSubMode == DrawingPadSubMode.INLINE_LETTER) {
                Modifier
                    .height(padHeightDp.dp)
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
            } else {
                Modifier
                    .height(padHeightDp.dp)
                    .aspectRatio(PICTURE_ASPECT_RATIO, matchHeightConstraintsFirst = true)
            }
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DrawingSurface(
                    strokes = completedStrokes,
                    widthFraction = StrokePolicy.widthFraction(lineWidthDp, padHeightDp),
                    acceptsStrokes = acceptsStrokes,
                    onStrokeFinished = { stroke ->
                        val fits = completedStrokes.size < strokeLimit &&
                            pointsUsed + stroke.points.size <= pointLimit &&
                            PayloadValidator.isValidStroke(stroke)
                        if (fits) completedStrokes.add(stroke)
                    },
                    modifier = surfaceModifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(CanvasBackground)
                        .border(1.dp, StrokeInk, RoundedCornerShape(12.dp)),
                )
            }
            Text(
                text = if (acceptsStrokes) {
                    if (drawSubMode == DrawingPadSubMode.INLINE_LETTER) {
                        "Draw a letter or word in any language, then insert it."
                    } else {
                        "Draw a picture; it is sent on its own."
                    }
                } else {
                    "Drawing limit reached: insert, send, undo or clear."
                },
                color = Muted,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DeckButton(
                    label = "Undo",
                    background = ToolGrey,
                    onClick = { if (completedStrokes.isNotEmpty()) completedStrokes.removeAt(completedStrokes.lastIndex) },
                )
                DeckButton(label = "Clear", background = ToolGrey, onClick = { completedStrokes.clear() })
                DeckButton(label = "⌫ Del", background = KeyDelete, onClick = onBackspace)
                Spacer(Modifier.weight(1f))
                if (drawSubMode == DrawingPadSubMode.INLINE_LETTER) {
                    DeckButton(
                        label = "+ Insert letter",
                        background = InsertBlue,
                        bold = true,
                        enabled = completedStrokes.isNotEmpty(),
                        onClick = {
                            if (completedStrokes.isNotEmpty()) {
                                onInsertInlineGlyph(completedStrokes.toList(), letterSizePreset.heightDp)
                                completedStrokes.clear()
                            }
                        },
                    )
                } else {
                    DeckButton(
                        label = "Send picture",
                        background = SendGreen,
                        bold = true,
                        enabled = completedStrokes.isNotEmpty(),
                        onClick = {
                            if (completedStrokes.isNotEmpty()) {
                                onSendFullPicture(completedStrokes.toList())
                                completedStrokes.clear()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun InlineGlyph(glyph: InlineSegment.DrawnGlyph, modifier: Modifier = Modifier) {
    VectorStrokeThumbnail(
        strokes = glyph.strokes,
        modifier = modifier
            .size(glyph.displayHeightDp.dp)
            .background(GlyphBackground, RoundedCornerShape(4.dp))
            .border(0.5.dp, StrokeInk, RoundedCornerShape(4.dp))
            .padding(2.dp),
    )
}

@Composable
private fun EnglishKeyboardLayout(
    isShifted: Boolean,
    isSymbolLayer: Boolean,
    onKeyPress: (String) -> Unit,
    onToggleShift: () -> Unit,
    onToggleSymbols: () -> Unit,
    onBackspace: () -> Unit,
    onSwitchToDrawPad: () -> Unit,
) {
    val rows = if (!isSymbolLayer) {
        listOf(
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
            listOf("z", "x", "c", "v", "b", "n", "m"),
        )
    } else {
        listOf(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            listOf("@", "#", "$", "%", "&", "*", "-", "+", "(", ")"),
            listOf("!", "\"", "'", ":", ";", "/", "?", "=", "_"),
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        rows.forEachIndexed { rowIndex, keys ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            ) {
                if (rowIndex == 2 && !isSymbolLayer) {
                    KeyButton(
                        label = "⇧",
                        modifier = Modifier.weight(1.4f),
                        background = if (isShifted) InsertBlue else KeyModifier,
                        onClick = onToggleShift,
                    )
                }
                keys.forEach { key ->
                    val displayKey = if (isShifted && !isSymbolLayer) key.uppercase() else key
                    KeyButton(
                        label = displayKey,
                        modifier = Modifier.weight(1f),
                        onClick = { onKeyPress(displayKey) },
                    )
                }
                if (rowIndex == 2) {
                    KeyButton(
                        label = "⌫",
                        modifier = Modifier.weight(1.4f),
                        background = KeyDelete,
                        onClick = onBackspace,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            KeyButton(
                label = if (isSymbolLayer) "ABC" else "?123",
                modifier = Modifier.weight(1.3f),
                background = KeyModifier,
                onClick = onToggleSymbols,
            )
            KeyButton(
                label = "Draw pad",
                modifier = Modifier.weight(1.9f),
                background = DrawPurple,
                onClick = onSwitchToDrawPad,
            )
            KeyButton(
                label = "space",
                modifier = Modifier.weight(3.0f),
                onClick = { onKeyPress(" ") },
            )
            KeyButton(label = ",", modifier = Modifier.weight(0.8f), onClick = { onKeyPress(",") })
            KeyButton(label = ".", modifier = Modifier.weight(0.8f), onClick = { onKeyPress(".") })
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    modifier: Modifier = Modifier,
    background: Color = KeyBackground,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .border(0.5.dp, KeyBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = if (label.length > 2) 12.sp else 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun DeckButton(
    label: String,
    background: Color,
    onClick: () -> Unit,
    bold: Boolean = false,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = background, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
    }
}
