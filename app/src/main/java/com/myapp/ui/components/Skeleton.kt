package com.myapp.ui.components

import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import com.myapp.ui.theme.Elevated
import com.myapp.ui.theme.Line

/** One Elevated bar standing in for a line of text. */
@Composable
fun SkeletonBar(
    modifier: Modifier = Modifier,
    width: Dp = 120.dp,
    height: Dp = 14.dp,
) {
    Box(modifier.size(width = width, height = height).background(Elevated))
}

/** [count] placeholder list rows at 64dp with dividers; announced once as "Loading". */
@Composable
fun SkeletonRows(
    count: Int,
    modifier: Modifier = Modifier,
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
                SkeletonBar(width = 96.dp, height = 16.dp)
                SkeletonBar(width = 180.dp, height = 12.dp)
            }
            HorizontalDivider(thickness = 1.dp, color = Line)
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
