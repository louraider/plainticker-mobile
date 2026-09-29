package com.plainticker.mobile.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The mark a sheet puts beside a failure, drawn, never a glyph: a closed ring in the caution
 * colour with a bar and a dot inside. One component for every sheet that says an attempt ended
 * without landing (QA of 1.3.21: the swap sheet's "No wallet connected" carried it and the vote
 * sheet's no-wallet refusal did not). Decorative: the sentence beside it already says what it
 * means, so it carries no description of its own.
 *
 * The ring is closed on purpose (QA of 1.3.20): a static open ring is what a spinner looks like,
 * and beside "No wallet connected" it read as a load that had frozen.
 */
@Composable
fun CautionMark(modifier: Modifier = Modifier, color: Color = defaultAmberColors().stateCaution) {
    Canvas(modifier.size(CautionMarkSize)) { drawCautionMark(color, CautionMarkStroke.toPx()) }
}

/**
 * The caution mark into any canvas, so a mark that also draws other tones shares this exact shape.
 *
 * [progress] is how far the mark has drawn itself in, 0 to 1 (the result hero's entrance,
 * [ResultMark]): the ring closes over the first [CAUTION_RING_SHARE] of it and the sign inside
 * fades in over the rest. At 1, the default and the only value every static caller passes, it is
 * the closed ring with its sign, exactly as before.
 */
fun DrawScope.drawCautionMark(color: Color, stroke: Float, progress: Float = 1f) {
    val inset = stroke / 2f
    val ring = (progress / CAUTION_RING_SHARE).coerceIn(0f, 1f)
    val sign = ((progress - CAUTION_RING_SHARE) / (1f - CAUTION_RING_SHARE)).coerceIn(0f, 1f)
    drawArc(
        color = color,
        startAngle = -90f,
        sweepAngle = 360f * ring,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = Size(size.width - stroke, size.height - stroke),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
    // The caution sign: a bar above the centre and a dot below it.
    if (sign <= 0f) return
    val cx = size.width / 2f
    drawLine(
        color = color,
        alpha = sign,
        start = Offset(cx, size.height * CAUTION_BAR_TOP),
        end = Offset(cx, size.height * CAUTION_BAR_BOTTOM),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
    drawCircle(color = color, alpha = sign, radius = stroke * 0.75f, center = Offset(cx, size.height * CAUTION_DOT))
}

/** The mark's size and stroke, the swap receipt's own. */
val CautionMarkSize: Dp = 40.dp
val CautionMarkStroke: Dp = 3.dp

/** Where the caution bar and dot sit, as fractions of the mark's height. */
private const val CAUTION_BAR_TOP = 0.28f
private const val CAUTION_BAR_BOTTOM = 0.56f
private const val CAUTION_DOT = 0.72f

/** The share of [drawCautionMark]'s progress the ring takes to close before the sign appears. */
private const val CAUTION_RING_SHARE = 0.6f
