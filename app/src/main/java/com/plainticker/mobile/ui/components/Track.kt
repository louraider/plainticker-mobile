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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberSurface
import com.plainticker.mobile.ui.theme.PlainTickerType

/** Marker settle: 400 ms, cubic-bezier(.2,.8,.2,1). Instant under reduced motion. */
private val MarkerEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/**
 * A position, never a filled bar: secondary label left, primary value and a tertiary state word
 * right, a 1dp bordered track with a 2dp primary-text marker at [positionPct] (0 to 100). Speaks
 * as one sentence: "Quality: strong, 8 of 9".
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
                style = PlainTickerType.rowLabel,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f).alignByBaseline(),
            )
            Row(Modifier.alignByBaseline(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = value,
                    style = PlainTickerType.trackValue,
                    color = colors.textPrimary,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    text = state,
                    style = PlainTickerType.small,
                    color = colors.textTertiary(AmberSurface.GROUND),
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        DrawCanvas(Modifier.fillMaxWidth().height(12.dp)) {
            val two = 2.dp.toPx()
            drawRect(color = colors.border, topLeft = Offset(0f, 5.dp.toPx()), size = Size(size.width, 1.dp.toPx()))
            drawRect(color = colors.textPrimary, topLeft = Offset((size.width - two) * position, 0f), size = Size(two, size.height))
        }
    }
}

@InstrumentPreviews
@Composable
private fun TrackPreview() {
    PreviewCanvas {
        Column {
            Track(label = "Quality", value = "8/9", state = "strong", positionPct = 89f)
            Track(label = "Valuation", value = "51", state = "fair", positionPct = 51f)
            Track(label = "Momentum", value = "0.79", state = "high", positionPct = 79f)
        }
    }
}
