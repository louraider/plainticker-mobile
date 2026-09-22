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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberSurface
import com.plainticker.mobile.ui.theme.AmberType

/**
 * A 2dp bar beside "Live from the mint" that breathes (opacity 1 to 0.45, 2.4 s ease-in-out) only
 * while [live] is true and the system animator scale is not 0. Static when landed or stale. The
 * only continuous motion on a screen, and this restyle keeps it exactly as it was: DESIGN.md
 * section 6 names this the one piece of Amber motion this pass must not touch, and it is already
 * meaning, not decoration, without needing to move at all. Under the smoke script's animator scale
 * of 0 the bar simply stops breathing and [alpha] holds at 1f; nothing here needs the animation to
 * be legible, because [label] itself says "Live from the mint" or "Landed" in words, not only in
 * whether the bar is pulsing.
 *
 * [colors] defaults to the system-following [defaultAmberColors] rather than Instrument's
 * fixed-dark Accent/Muted: this bar draws the swap sheet's and the pass sheet's own phase, and a
 * bar that stayed dark on an Amber-light sheet is the same money-flow fault [Sheet] closes.
 *
 * **Type.** [label] in [AmberType.context] (14/400, [AmberColors.actionText]): a phase sentence
 * ("No wallet is connected, so the wallet is being asked to authorize.", [VotePhase][
 * com.plainticker.mobile.ui.vote.VotePhase]'s own longest, 420.616dp at this size) with no
 * `maxLines` set, so it wraps rather than clips regardless of length, the same as before this
 * restyle. [meta] in [AmberType.meta] (12/400, [AmberColors.textTertiary]), single line.
 *
 * **The one slot this restyle hardens.** [meta] keeps `maxLines = 1` (a timestamp or a short
 * technical readout reads worse split across two lines) but now carries [TextOverflow.Ellipsis],
 * and the label/meta [Column] now takes `weight(1f, fill = false)` in the row: previously that
 * Column had no width modifier at all beside the bar's fixed 2dp, exactly the shape the brief
 * names as a trap this project has hit before, just not yet caught here. Measured against it:
 * `res/font/bricolage_grotesque.ttf` (fontTools, 2026-09-22, instantiated at `meta`'s own
 * `wght`/`wdth`/`opsz`), the real worst-case [meta] this component draws,
 * `detail_live_unread_meta` ("Nothing below this line is read from the chain", 46 characters), is
 * 255.624dp. The column's own budget on a 400dp frame, this row's 20dp side padding, the 2dp bar
 * and the 14dp gap between them, is 400 − 40 − 2 − 14 = **344dp**: a 88.376dp margin at 1x, and
 * still 11.689dp at 1.3x font scale (255.624dp × 1.3 = 332.311dp), so this real content never
 * actually reaches the ellipsis the fix adds as a backstop.
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
                .background(colors.stateLive, RoundedCornerShape(1.dp)),
        )
        // weight(1f, fill = false): bounds this column to whatever the row has left past the bar
        // and the 14dp gap, so label (unbounded lines) and meta (maxLines = 1) are actually
        // constrained rather than free to demand more width than the row has, the shape of the
        // clipping trap this file's own doc comment measures against.
        Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = label, style = AmberType.context, color = colors.actionText)
            Text(
                text = meta,
                style = AmberType.meta,
                color = colors.textTertiary(AmberSurface.GROUND),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@InstrumentPreviews
@Composable
private fun LiveBarPreview() {
    AmberPreviewCanvas {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            LiveBar(label = "Live from the mint", meta = "slot 445,912,118 · 2 s ago", live = true)
            LiveBar(label = "Landed", meta = "confirmed in 3.1 s", live = false)
        }
    }
}
