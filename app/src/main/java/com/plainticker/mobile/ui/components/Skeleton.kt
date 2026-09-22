package com.plainticker.mobile.ui.components

import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberLightColors

/**
 * One bar standing in for a line of text, on [colors]' own [AmberColors.surfaceRaised]: a skeleton
 * built for a dark ground (Instrument's fixed [com.plainticker.mobile.ui.theme.Elevated]) read as a
 * row of near-black blocks on Amber's light ground, exactly the "built for a dark ground" fault
 * this pass looks for, so the fill now follows [defaultAmberColors] like every other shared piece.
 *
 * **The light-only ring.** Light's `surfaceRaised` (`#FFFFFF`) sits about 1.03:1 over
 * `surfaceGround` (`#FFFBF2`), so this fill was reading as a blank gap on white even after the
 * fix above, first-paint's own placeholder and (`DetailScreen.kt`'s `VerdictBlock.Locked`) a
 * gated classification's locked state alike. A 1dp [AmberColors.border] ring, gated on
 * `colors === AmberLightColors`, gives it an edge exactly where the tone alone does not; dark's
 * 1.12:1 step already reads and keeps its plain fill, unringed.
 */
@Composable
fun SkeletonBar(
    modifier: Modifier = Modifier,
    width: Dp = 120.dp,
    height: Dp = 14.dp,
    colors: AmberColors = defaultAmberColors(),
) {
    Box(
        modifier
            .size(width = width, height = height)
            .background(colors.surfaceRaised)
            .then(if (colors === AmberLightColors) Modifier.border(1.dp, colors.border) else Modifier),
    )
}

/** [count] placeholder list rows at 64dp with dividers; announced once as "Loading". */
@Composable
fun SkeletonRows(
    count: Int,
    modifier: Modifier = Modifier,
    colors: AmberColors = defaultAmberColors(),
) {
    Column(modifier.fillMaxWidth().semantics { contentDescription = "Loading" }) {
        repeat(count) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SkeletonBar(width = 96.dp, height = 16.dp, colors = colors)
                SkeletonBar(width = 180.dp, height = 12.dp, colors = colors)
            }
            HorizontalDivider(thickness = 1.dp, color = colors.border)
        }
    }
}

/**
 * Skeleton while [loading], then the content fading in over 200 ms ease-out (instant under
 * reduced motion). Never a spinner.
 */
@Composable
fun SkeletonSwitch(
    loading: Boolean,
    modifier: Modifier = Modifier,
    skeleton: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val motion = rememberMotionEnabled()
    val alpha by animateFloatAsState(
        targetValue = if (loading) 0f else 1f,
        animationSpec = if (motion) tween<Float>(durationMillis = 200, easing = EaseOut) else snap<Float>(),
        label = "skeleton",
    )
    Box(modifier) {
        if (loading) {
            skeleton()
        } else {
            Box(Modifier.graphicsLayer { this.alpha = alpha }) { content() }
        }
    }
}

@InstrumentPreviews
@Composable
private fun SkeletonPreview() {
    PreviewCanvas {
        SkeletonRows(count = 3)
    }
}
