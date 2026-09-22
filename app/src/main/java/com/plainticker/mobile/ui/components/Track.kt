package com.plainticker.mobile.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas as DrawCanvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberSurface
import com.plainticker.mobile.ui.theme.AmberType

/** Marker settle: 400 ms, cubic-bezier(.2,.8,.2,1). Instant under reduced motion. Unchanged by
 *  this restyle: the research names no motion token for a classification's own settle, and this
 *  one was already correctly gated ([rememberMotionEnabled]) and never load-bearing for legibility
 *  (an un-animated marker still lands on the right [positionPct]; the smoke script's animator
 *  scale of 0 only removes the settle, never the answer). */
private val MarkerEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/**
 * A position, never a filled bar: secondary label left, primary value and a tertiary state word
 * right, over a full-width [AmberColors.surfaceHigh] capsule (the same departures-board track
 * [Gauge] draws, DESIGN.md section 1's signature element) with a 2dp primary-text marker at
 * [positionPct] (0 to 100), standing proud of the capsule the way [Gauge]'s own ticks do. Speaks
 * as one sentence: "Quality: strong, 8 of 9".
 *
 * **Neutral on purpose.** The marker stays [AmberColors.textPrimary], never [AmberColors.actionText]:
 * this draws a classification against the sector, not a live reading, and DESIGN.md section 7's
 * rule against staging a classification as a grade extends to colour the same way it extends to
 * size ("never staged as a grade") — amber stays reserved for the figures it already means
 * something on ([Gauge]'s premium, [AmberFigure]'s price).
 *
 * **Type.** `label` in [AmberType.body] (a classification name is a short phrase, not a numeral);
 * `value` in [AmberType.figureRow] (`tnum`: "8/9", "51", "0.79" are all numerals a reader compares
 * across rows); `state` in [AmberType.meta], whose own doc comment names exactly this content
 * ("state words, timestamps written as words").
 *
 * **Measured, not guessed.** fontTools against `res/font/bricolage_grotesque.ttf`, 2026-09-22, each
 * style instantiated at the exact `wght`/`wdth`/`opsz` [AmberType] builds it with. This row's
 * content width is a 400dp frame less this composable's own 20dp side padding, twice: 360dp. The
 * top line's two groups sit `Arrangement.spacedBy(12.dp)` apart; `value` and `state` sit a further
 * `Arrangement.spacedBy(10.dp)` apart.
 * - **`state`'s own worst case**, the real content [DetailModelTest] and [FreeStaysFreeTest] fix
 *   the server can send (`axis.labelEn`, lowercased): "near 52-week high" (17 characters, momentum's
 *   own state word), 103.812dp at `meta`'s 12sp/400. Paired with the widest realistic `value`
 *   ("0.79" or "1.00", 36.954dp at `figureRow`'s 18sp/600 `tnum`) and the 10dp gap between them,
 *   the value/state group is 150.766dp; `label`'s own budget is 360 − 12 − 150.766 = **197.234dp**,
 *   comfortably past the widest fixed label, "Momentum" (80.955dp), an 116.279dp margin. At 1.3x
 *   font scale the same arithmetic (content width unscaled, every measured width scaled) still
 *   clears with 46.76dp to spare, so `label` never needs the wrap its unbounded `maxLines` already
 *   allows for, and `state`'s `maxLines = 1` (kept, with [TextOverflow.Ellipsis] added as a
 *   backstop this arithmetic says should not fire) is never actually squeezed.
 *
 * Draws Detail's own classification rows ("Against the sector"), so [colors] defaults to the
 * system-following [defaultAmberColors] rather than Instrument's fixed-dark Ink/Ink2/Muted/
 * LineStrong: the gated classification this component is part of must read correctly in light
 * too, not only in the dark set it drew unconditionally before this fix.
 */
@Composable
fun Track(
    label: String,
    value: String,
    state: String,
    positionPct: Float,
    modifier: Modifier = Modifier,
    colors: AmberColors = defaultAmberColors(),
) {
    val target = (positionPct / 100f).coerceIn(0f, 1f)
    val motion = rememberMotionEnabled()
    val position by animateFloatAsState(
        targetValue = target,
        animationSpec = if (motion) tween<Float>(durationMillis = 400, easing = MarkerEasing) else snap<Float>(),
        label = "marker",
    )
    val description = "$label: $state, ${spoken(value)}"
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = label,
                style = AmberType.body,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f).alignByBaseline(),
            )
            Row(Modifier.alignByBaseline(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = value,
                    style = AmberType.figureRow,
                    color = colors.textPrimary,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    text = state,
                    style = AmberType.meta,
                    color = colors.textTertiary(AmberSurface.GROUND),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        DrawCanvas(Modifier.fillMaxWidth().height(TrackCanvasHeight)) {
            val two = 2.dp.toPx()
            // The same capsule Gauge draws (surfaceHigh, rounded to a full pill), replacing
            // Instrument's 1dp border hairline; centred the same way, TrackThickness top and
            // bottom leaving equal margin.
            val trackTop = (TrackCanvasHeight.toPx() - TrackThickness.toPx()) / 2f
            drawRoundRect(
                color = colors.surfaceHigh,
                topLeft = Offset(0f, trackTop),
                size = Size(size.width, TrackThickness.toPx()),
                cornerRadius = CornerRadius(TrackThickness.toPx() / 2f),
            )
            drawRect(color = colors.textPrimary, topLeft = Offset((size.width - two) * position, 0f), size = Size(two, size.height))
        }
    }
}

/** Tall enough for the 2dp marker to stand proud of the capsule on both sides, the way Gauge's ticks do. */
private val TrackCanvasHeight = 12.dp

/** The departures-board capsule's own thickness, matching Gauge's. */
private val TrackThickness = 4.dp

@InstrumentPreviews
@Composable
private fun TrackPreview() {
    AmberPreviewCanvas {
        Column {
            Track(label = "Quality", value = "8/9", state = "strong", positionPct = 89f)
            Track(label = "Valuation", value = "51", state = "fair", positionPct = 51f)
            Track(label = "Momentum", value = "0.79", state = "high", positionPct = 79f)
        }
    }
}
