package com.plainticker.mobile.ui.components

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberType

/** Which of the three ways a finished attempt can end, as the mark draws it. */
enum class ResultMarkTone {
    /** It landed: an amber disc with a check drawn into it. */
    Landed,

    /** It did not, and nothing more is coming: the closed caution ring with its sign. */
    Failed,

    /** It was handed on and nobody has said yet whether it landed: a broken amber ring, static. */
    Pending,
}

/**
 * The result of a swap or a vote, as its own moment at the top of the sheet (founder's report from
 * the Seeker, 2026-09-29: "vote sent" was small text with no success or failure mark, and the swap
 * result read the same way). One anatomy for both, so a reader learns it once:
 *
 * 1. [eyebrow], what the attempt was ("USDC to TSLAx", "Vote to cover NFLXx"), small, secondary;
 * 2. the mark, 72dp, whose shape alone says which of the three it is ([ResultMarkTone]);
 * 3. [headline], the plain answer in the largest words on the sheet ([AmberType.screenTitle],
 *    26/700), in the caution colour for a failure;
 * 4. [figure], the hero number when there is one (a swap's fill), [AmberType.figureLarge] amber;
 * 5. [sentences], one or two plain sentences: what happened and what happens next.
 *
 * The facts, the explorer link and the actions sit under it, drawn by each sheet in its own way.
 * Centred, because it is the one moment on the sheet, not one more row of it; every width in the
 * block wraps (no `maxLines` anywhere but the figure, which [com.plainticker.mobile.ui.swap.
 * SwapResultFitTest] proves fits one line), so nothing here can clip (DESIGN.md section 4).
 *
 * **Motion** (DESIGN.md section 6): the mark draws itself in on Amber's settle spring (no bounce,
 * medium-low stiffness): a landing's ring closes, fills, and the check strokes in; a failure's
 * ring closes and its sign fades in; a pending ring's four segments close in turn and then stay
 * still, because a moving ring is a spinner. The figure fades in on the quick 150ms token. All of
 * it is gated on [rememberMotionEnabled]: at animator scale 0 the first frame is already the
 * settled one, and nothing needs to move to be read, because the headline says it in words.
 *
 * **Haptic**: one Confirm when a landing appears, keyed on [hapticKey] (the signature), so a
 * recomposition cannot buzz twice. A failure buzzes nothing.
 *
 * **Accessibility**: one merged node, a heading and a polite live region whose description is
 * [announcement], so TalkBack reads the result once when it appears; [modifier] is where the
 * sheet's own focus requester is attached.
 */
@Composable
fun ResultHero(
    tone: ResultMarkTone,
    headline: String,
    sentences: List<String>,
    announcement: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    figure: String? = null,
    hapticKey: Any? = null,
    colors: AmberColors = defaultAmberColors(),
) {
    val motion = rememberMotionEnabled()
    // Without motion the first frame is the settled one; with it, the settle starts on arrival.
    var settled by remember(tone, headline) { mutableStateOf(!motion) }
    LaunchedEffect(tone, headline) { settled = true }
    val progress by animateFloatAsState(
        targetValue = if (settled) 1f else 0f,
        animationSpec = if (motion) MarkSettle else snap(),
        label = "result-mark",
    )
    val reveal by animateFloatAsState(
        targetValue = if (settled) 1f else 0f,
        animationSpec = if (motion) tween(durationMillis = QUICK_MILLIS, easing = LinearOutSlowInEasing) else snap(),
        label = "result-reveal",
    )
    ConfirmOnLanded(landed = tone == ResultMarkTone.Landed, key = hapticKey)

    val headlineColor = if (tone == ResultMarkTone.Failed) colors.stateCaution else colors.textPrimary
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = HeroSide, end = HeroSide, top = HeroTop)
            .semantics(mergeDescendants = true) {
                heading()
                liveRegion = LiveRegionMode.Polite
                contentDescription = announcement
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        eyebrow?.let {
            Text(text = it, style = AmberType.meta, color = colors.textSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(EyebrowGap))
        }
        ResultMark(tone = tone, progress = progress, colors = colors)
        Spacer(Modifier.height(HeroMarkGap))
        Text(
            text = headline,
            style = AmberType.screenTitle,
            color = headlineColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        figure?.let {
            Spacer(Modifier.height(FigureGap))
            Text(
                text = it,
                style = AmberType.figureLarge,
                color = colors.actionText,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.graphicsLayer { alpha = reveal },
            )
        }
        if (sentences.isNotEmpty()) {
            Spacer(Modifier.height(SentencesGap))
            Column(verticalArrangement = Arrangement.spacedBy(SentenceGap)) {
                sentences.forEach {
                    Text(
                        text = it,
                        style = AmberType.body,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * The mark, drawn, never a glyph. Decorative: the hero's own description already says what it
 * means. [progress] runs 0 to 1 as it draws itself in; [markPhases] splits it into the parts.
 */
@Composable
fun ResultMark(tone: ResultMarkTone, progress: Float, colors: AmberColors, modifier: Modifier = Modifier) {
    val live = colors.stateLive
    val onLive = colors.actionOnFill
    val caution = colors.stateCaution
    Canvas(modifier.size(ResultMarkSize)) {
        val stroke = ResultMarkStroke.toPx()
        val phases = markPhases(tone, progress)
        when (tone) {
            ResultMarkTone.Landed -> drawLanded(phases, live, onLive, stroke)
            // The shared caution mark (CautionMark.kt), the one every refusal carries too.
            ResultMarkTone.Failed -> drawCautionMark(color = caution, stroke = stroke, progress = progress)
            ResultMarkTone.Pending -> drawPending(phases.ring, live, stroke)
        }
    }
}

/**
 * What TalkBack reads for a result, once: the headline and then each sentence, with a full stop
 * after the headline (which carries none on screen) and never a doubled one.
 */
fun resultAnnouncement(headline: String, sentences: List<String>): String =
    sentences.fold(headline.trim()) { spoken, next ->
        val sentence = next.trim()
        when {
            sentence.isEmpty() -> spoken
            spoken.endsWith(".") -> "$spoken $sentence"
            else -> "$spoken. $sentence"
        }
    }

/** How far each part of the mark has drawn, for one [progress]. Pure, so the JVM tests read it. */
internal data class MarkPhases(val ring: Float, val fill: Float, val check: Float)

/**
 * The entrance as three overlapping parts: the ring closes over the first [RING_END] of the
 * progress, the disc fills from [FILL_START] to [FILL_END], the check strokes in from
 * [CHECK_START] to the end. Only a landing fills or checks; the other two tones are the ring alone.
 */
internal fun markPhases(tone: ResultMarkTone, progress: Float): MarkPhases {
    val p = progress.coerceIn(0f, 1f)
    fun part(start: Float, end: Float) = ((p - start) / (end - start)).coerceIn(0f, 1f)
    val ring = part(0f, RING_END)
    return if (tone == ResultMarkTone.Landed) {
        MarkPhases(ring = ring, fill = part(FILL_START, FILL_END), check = part(CHECK_START, 1f))
    } else {
        MarkPhases(ring = ring, fill = 0f, check = 0f)
    }
}

private fun DrawScope.drawLanded(phases: MarkPhases, live: Color, onLive: Color, stroke: Float) {
    val inset = stroke / 2f
    drawArc(
        color = live,
        startAngle = -90f,
        sweepAngle = 360f * phases.ring,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = Size(size.width - stroke, size.height - stroke),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
    if (phases.fill > 0f) drawCircle(color = live, alpha = phases.fill, radius = size.minDimension / 2f)
    if (phases.check <= 0f) return
    val check = Path().apply {
        moveTo(size.width * CHECK_X0, size.height * CHECK_Y0)
        lineTo(size.width * CHECK_X1, size.height * CHECK_Y1)
        lineTo(size.width * CHECK_X2, size.height * CHECK_Y2)
    }
    val measure = PathMeasure().apply { setPath(check, false) }
    val drawn = Path()
    measure.getSegment(0f, measure.length * phases.check, drawn, true)
    drawPath(
        path = drawn,
        color = onLive,
        style = Stroke(width = size.minDimension * CHECK_STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private fun DrawScope.drawPending(ring: Float, live: Color, stroke: Float) {
    val inset = stroke / 2f
    val segment = 360f / PENDING_SEGMENTS
    repeat(PENDING_SEGMENTS) { i ->
        // The segments close one after another as the ring does, then stay exactly where they are.
        val share = (ring * PENDING_SEGMENTS - i).coerceIn(0f, 1f)
        if (share > 0f) {
            drawArc(
                color = live,
                startAngle = -90f + i * segment,
                sweepAngle = (segment - PENDING_GAP_DEGREES) * share,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

/** One Confirm haptic when a landing appears, and exactly one: keyed on [key], the signature. */
@Composable
private fun ConfirmOnLanded(landed: Boolean, key: Any?) {
    val haptics = LocalHapticFeedback.current
    val isLanded by rememberUpdatedState(landed)
    LaunchedEffect(key) {
        if (key != null && isLanded) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    }
}

// ---- Measurements -------------------------------------------------------------------------------

val ResultMarkSize: Dp = 72.dp
val ResultMarkStroke: Dp = 4.dp

private val HeroSide = 20.dp
private val HeroTop = 16.dp
private val EyebrowGap = 16.dp
private val HeroMarkGap = 16.dp
private val FigureGap = 6.dp
private val SentencesGap = 10.dp
private val SentenceGap = 4.dp

/** Amber's settle: no bounce, medium-low stiffness, the spring AmberChip and Today use. */
private val MarkSettle = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

/** Amber's quick token: the 150ms one-shot reveal Portfolio's total uses. */
private const val QUICK_MILLIS = 150

internal const val RING_END = 0.55f
internal const val FILL_START = 0.4f
internal const val FILL_END = 0.7f
internal const val CHECK_START = 0.55f

/** The check, as fractions of the mark: down to the elbow, then up to the right. */
private const val CHECK_X0 = 0.29f
private const val CHECK_Y0 = 0.52f
private const val CHECK_X1 = 0.44f
private const val CHECK_Y1 = 0.67f
private const val CHECK_X2 = 0.72f
private const val CHECK_Y2 = 0.37f
private const val CHECK_STROKE = 0.085f

private const val PENDING_SEGMENTS = 4
private const val PENDING_GAP_DEGREES = 28f
