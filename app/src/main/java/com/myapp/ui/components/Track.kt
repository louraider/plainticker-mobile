package com.myapp.ui.components

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
import com.myapp.ui.theme.Ink
import com.myapp.ui.theme.Ink2
import com.myapp.ui.theme.LineStrong
import com.myapp.ui.theme.Muted
import com.myapp.ui.theme.PlainTickerType

/** Marker settle: 400 ms, cubic-bezier(.2,.8,.2,1). Instant under reduced motion. */
private val MarkerEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/**
 * A position, never a filled bar: label 15 Ink 2 left, value mono 20 and a state word 13 Muted
 * right, a 1dp Line strong track with a 2dp Ink marker at [positionPct] (0 to 100).
 * Speaks as one sentence: "Quality: strong, 8 of 9".
 */
@Composable
fun Track(
    label: String,
    value: String,
    state: String,
    positionPct: Float,
    modifier: Modifier = Modifier,
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
                color = Ink2,
                modifier = Modifier.weight(1f).alignByBaseline(),
            )
            Row(Modifier.alignByBaseline(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = value,
                    style = PlainTickerType.trackValue,
                    color = Ink,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    text = state,
                    style = PlainTickerType.small,
                    color = Muted,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        DrawCanvas(Modifier.fillMaxWidth().height(12.dp)) {
            val two = 2.dp.toPx()
            drawRect(color = LineStrong, topLeft = Offset(0f, 5.dp.toPx()), size = Size(size.width, 1.dp.toPx()))
            drawRect(color = Ink, topLeft = Offset((size.width - two) * position, 0f), size = Size(two, size.height))
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
