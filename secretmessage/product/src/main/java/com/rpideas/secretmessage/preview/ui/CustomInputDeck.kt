package com.rpideas.secretmessage.preview.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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

/**
 * Both pads are square, sized by [DrawingPadLayout]: a letter keeps its shape inline, and a picture
 * is shown in a square bubble, so the picture pad can be as large as the letter pad.
 */
internal const val PICTURE_ASPECT_RATIO = 1f

/**
 * Line widths are stored relative to this pad height (the 140 dp letter pad of 0.2.0-preview), not
 * the current pad's, so a drawing keeps the same proportions on the large pad, in the small inline
 * letter tiles and in picture bubbles.
 */
internal const val LINE_WIDTH_REFERENCE_PAD_DP = 140f
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
    padSide: Dp,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    var deckMode by remember { mutableStateOf(InputDeckMode.ENGLISH_KEYBOARD) }
    var isShifted by remember { mutableStateOf(false) }
    var isSymbolLayer by remember { mutableStateOf(false) }
    var drawSubMode by remember { mutableStateOf(DrawingPadSubMode.FULL_PICTURE) }
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
                        text = t("Type with the built-in English keyboard or draw letters in any language"),
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
                Text(t("Send"), fontWeight = FontWeight.Bold)
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

            // Owner request for 1.1.0: the pad stays as large as before, the controls around it are
            // smaller (compact pills instead of full-size buttons and chips).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DeckButton(
                    label = t("English keyboard"),
                    background = KeyBorder,
                    onClick = { deckMode = InputDeckMode.ENGLISH_KEYBOARD },
                )
                DeckToggle(
                    label = t("Picture"),
                    selected = drawSubMode == DrawingPadSubMode.FULL_PICTURE,
                    onClick = {
                        if (drawSubMode != DrawingPadSubMode.FULL_PICTURE) completedStrokes.clear()
                        drawSubMode = DrawingPadSubMode.FULL_PICTURE
                    },
                )
                DeckToggle(
                    label = t("Letter"),
                    selected = drawSubMode == DrawingPadSubMode.INLINE_LETTER,
                    onClick = {
                        if (drawSubMode != DrawingPadSubMode.INLINE_LETTER) completedStrokes.clear()
                        drawSubMode = DrawingPadSubMode.INLINE_LETTER
                    },
                )
                Spacer(Modifier.weight(1f))
                if (drawSubMode == DrawingPadSubMode.INLINE_LETTER) {
                    DrawnLetterSizePreset.entries.forEach { preset ->
                        val sizeDescription = t("{0} letter size", t(preset.label))
                        DeckToggle(
                            label = preset.label.take(1),
                            selected = letterSizePreset == preset,
                            onClick = { letterSizePreset = preset },
                            modifier = Modifier.semantics { contentDescription = sizeDescription },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = t("Line {0}", lineWidthDp.toInt()),
                    color = Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.width(56.dp),
                )
                Slider(
                    value = lineWidthDp,
                    onValueChange = { lineWidthDp = it },
                    valueRange = 2f..18f,
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp),
                )
            }

            // The pad takes the room left between the control rows, up to [padSide]. The rows are
            // measured first, so a taller control row shrinks the pad instead of pushing the tools
            // off-screen.
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .padding(vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                val side = minOf(maxWidth, maxHeight, padSide)
                val isLetterPad = drawSubMode == DrawingPadSubMode.INLINE_LETTER
                Box(modifier = Modifier.size(side)) {
                    DrawingSurface(
                        strokes = completedStrokes,
                        widthFraction = StrokePolicy.widthFraction(lineWidthDp, LINE_WIDTH_REFERENCE_PAD_DP),
                        acceptsStrokes = acceptsStrokes,
                        onStrokeFinished = { stroke ->
                            val fits = completedStrokes.size < strokeLimit &&
                                pointsUsed + stroke.points.size <= pointLimit &&
                                PayloadValidator.isValidStroke(stroke)
                            if (fits) completedStrokes.add(stroke)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CanvasBackground)
                            .border(1.dp, StrokeInk, RoundedCornerShape(12.dp))
                            .semantics {
                                contentDescription = if (isLetterPad) t("Letter drawing pad") else t("Picture drawing pad")
                            },
                    )
                    // The hint sits inside the pad (it does not take touches) until the first stroke.
                    val padHint = when {
                        !acceptsStrokes -> t("Drawing limit reached: insert, send, undo or clear.")
                        completedStrokes.isNotEmpty() -> null
                        isLetterPad -> t("Draw a letter or word in any language, then insert it.")
                        else -> t("Draw a picture; it is sent on its own.")
                    }
                    if (padHint != null) {
                        Text(
                            text = padHint,
                            color = Muted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(if (acceptsStrokes) Alignment.Center else Alignment.BottomCenter)
                                .padding(16.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DeckButton(
                    label = t("Undo"),
                    background = ToolGrey,
                    onClick = { if (completedStrokes.isNotEmpty()) completedStrokes.removeAt(completedStrokes.lastIndex) },
                )
                DeckButton(label = t("Clear"), background = ToolGrey, onClick = { completedStrokes.clear() })
                DeckButton(label = t("⌫ Del"), background = KeyDelete, onClick = onBackspace)
                Spacer(Modifier.weight(1f))
                if (drawSubMode == DrawingPadSubMode.INLINE_LETTER) {
                    DeckButton(
                        label = t("+ Insert letter"),
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
                        label = t("Send picture"),
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
            .semantics { contentDescription = t("Drawn letter") }
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
                label = t("Draw pad"),
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

/** Keys about as tall as a phone's own keyboard (owner request after the 0.4.0 Internet test). */
private val KEY_HEIGHT = 54.dp

@Composable
private fun KeyButton(
    label: String,
    modifier: Modifier = Modifier,
    background: Color = KeyBackground,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(KEY_HEIGHT)
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .border(0.5.dp, KeyBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = if (label.length > 2) 14.sp else 22.sp,
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
    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (enabled) background else background.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/** A compact on/off pill for the pad mode and letter size (smaller than a Material chip). */
@Composable
private fun DeckToggle(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) InsertBlue else Color.Transparent)
            .border(1.dp, if (selected) InsertBlue else KeyBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Ink,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}
