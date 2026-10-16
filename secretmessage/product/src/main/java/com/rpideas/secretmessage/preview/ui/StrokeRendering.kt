package com.rpideas.secretmessage.preview.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.rpideas.secretmessage.preview.chat.StrokePolicy
import com.rpideas.secretmessage.preview.model.NormalizedPoint
import com.rpideas.secretmessage.preview.model.VectorStroke

/** Draw a normalized stroke scaled to this canvas; the width scales with the canvas height. */
internal fun DrawScope.drawNormalizedStroke(stroke: VectorStroke, color: Color) {
    val points = stroke.points
    if (points.isEmpty()) return
    val widthPx = (stroke.widthFraction * size.height).coerceAtLeast(1.5f)
    if (points.size == 1) {
        drawCircle(
            color = color,
            radius = widthPx / 2f,
            center = Offset(points[0].x * size.width, points[0].y * size.height),
        )
        return
    }
    val path = Path().apply {
        moveTo(points[0].x * size.width, points[0].y * size.height)
        for (index in 1 until points.size) {
            lineTo(points[index].x * size.width, points[index].y * size.height)
        }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

@Composable
internal fun VectorStrokeThumbnail(
    strokes: List<VectorStroke>,
    modifier: Modifier = Modifier,
    color: Color = StrokeInk,
) {
    Canvas(modifier = modifier) {
        strokes.forEach { drawNormalizedStroke(it, color) }
    }
}

/**
 * Freehand drawing surface. It records raw pointer positions (not recognized text), keeps a single
 * tap as a dot, and reports each finished stroke. [acceptsStrokes] stops input at the payload limits.
 */
@Composable
internal fun DrawingSurface(
    strokes: List<VectorStroke>,
    widthFraction: Float,
    acceptsStrokes: Boolean,
    onStrokeFinished: (VectorStroke) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activePoints = remember { mutableStateListOf<NormalizedPoint>() }
    val currentWidth by rememberUpdatedState(widthFraction)
    val currentAccepts by rememberUpdatedState(acceptsStrokes)
    val currentOnFinished by rememberUpdatedState(onStrokeFinished)

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                if (!currentAccepts) return@awaitEachGesture
                activePoints.clear()
                activePoints.add(StrokePolicy.normalize(down.position.x, down.position.y, size.width, size.height))
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        change.consume()
                        break
                    }
                    val point = StrokePolicy.normalize(change.position.x, change.position.y, size.width, size.height)
                    if (StrokePolicy.shouldAppend(activePoints, point)) activePoints.add(point)
                    change.consume()
                }
                if (activePoints.isNotEmpty()) {
                    currentOnFinished(VectorStroke(activePoints.toList(), currentWidth))
                    activePoints.clear()
                }
            }
        },
    ) {
        strokes.forEach { drawNormalizedStroke(it, StrokeInk) }
        if (activePoints.isNotEmpty()) {
            drawNormalizedStroke(VectorStroke(activePoints.toList(), widthFraction), StrokeInk)
        }
    }
}
