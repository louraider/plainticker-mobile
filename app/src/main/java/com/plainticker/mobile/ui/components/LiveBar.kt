package com.plainticker.mobile.ui.components

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberSurface
import com.plainticker.mobile.ui.theme.PlainTickerType

/**
 * A 2dp bar beside "Live from the mint" that breathes (opacity 1 to 0.45, 2.4 s ease-in-out) only
 * while [live] is true and the system animator scale is not 0. Static when landed or stale. The
 * only continuous motion on a screen.
 *
 * [colors] defaults to the system-following [defaultAmberColors] rather than Instrument's
 * fixed-dark Accent/Muted: this bar draws the swap sheet's and the pass sheet's own phase, and a
 * bar that stayed dark on an Amber-light sheet is the same money-flow fault [Sheet] closes.
 *
 * @param announcement what a screen reader hears when the region changes; pass something stable
 *   (not the ticking "2 s ago") so it is announced at most once per update that matters.
 */
@Composable
fun LiveBar(
    label: String,
    meta: String,
    live: Boolean,
    modifier: Modifier = Modifier,
    announcement: String = "$label, $meta",
    colors: AmberColors = defaultAmberColors(),
) {
    val breathing = live && rememberMotionEnabled()
    val alpha: State<Float> = if (breathing) {
        rememberInfiniteTransition(label = "live").animateFloat(
            initialValue = 1f,
            targetValue = 0.45f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = EaseInOut),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "breathe",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(IntrinsicSize.Min)
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = announcement
            },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .width(2.dp)
                .fillMaxHeight()
                .graphicsLayer { this.alpha = alpha.value }
                .background(colors.stateLive),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = label, style = PlainTickerType.textAction, color = colors.actionText)
            Text(
                text = meta,
                style = PlainTickerType.meta,
                color = colors.textTertiary(AmberSurface.GROUND),
                maxLines = 1,
            )
        }
    }
}

@InstrumentPreviews
@Composable
private fun LiveBarPreview() {
    PreviewCanvas {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            LiveBar(label = "Live from the mint", meta = "slot 445,912,118 · 2 s ago", live = true)
            LiveBar(label = "Landed", meta = "confirmed in 3.1 s", live = false)
        }
    }
}
