package com.plainticker.mobile.ui.components

import androidx.compose.foundation.Canvas as DrawCanvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.R
import com.plainticker.mobile.data.jupiter.TrackingQuality
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberSurface
import com.plainticker.mobile.ui.theme.PlainTickerType

/**
 * The tracking gauge, the signature element of Detail (DESIGN.md section 1): a 1dp bordered track
 * between two end stops, a 1dp tertiary tick at 50 percent for the reference (the NYSE close) and
 * a 2dp action-coloured tick for the token, placed on a stated scale. Caption left, the signed
 * premium right in the same action colour.
 *
 * [colors] defaults to the system-following [defaultAmberColors] rather than Instrument's
 * fixed-dark Accent/Ink2/LineStrong/Muted: this is the liquidity floor's own signature element
 * (DESIGN.md section 1.1), and it must read on Amber's light ground as correctly as on its dark
 * one.
 *
 * The gauge is drawn only where the quote behind it means something. [TrackingQuality] is the one
 * rule, shared with the list row, and it is asked here rather than by the caller so no screen can
 * draw a tracking gauge for a token nothing tracks: below the liquidity floor nothing is drawn
 * here at all. The sentence that states the pool instead belongs above the two figures it
 * disqualifies rather than under them, so the surface owns it (DESIGN.md section 1.1): a sentence
 * in this slot was read after both operands and lost the argument to them.
 *
 * A [TrackingQuality.Tracked] token whose reference price Jupiter did not send draws nothing at
 * all: there is no premium to place, and the price row above already says the reference is
 * unavailable (docs/data-map.md, Detail).
 *
 * **The scale is stated and its ends are drawn.** A premium past [scalePct] is drawn as a cap
 * standing off the track, never as a tick resting on the end: the tracked set runs out to about
 * 2.3 percent and any fixed scale can be exceeded, so a saturated tick had to be made
 * unmistakable rather than made impossible. See [gaugeTick].
 *
 * @param referenceLabel what the token is being measured against, e.g. "Token vs NYSE close".
 * @param tracking the answer from the one rule, built from the Price v3 entry.
 * @param scalePct the premium that reaches the end of the track on each side. The default is the
 *   spread the tracked catalogue actually produced, [TrackingQuality.TRACKED_SPREAD_PCT].
 */
@Composable
fun Gauge(
    referenceLabel: String,
    tracking: TrackingQuality,
    modifier: Modifier = Modifier,
    scalePct: Double = TrackingQuality.TRACKED_SPREAD_PCT,
    colors: AmberColors = defaultAmberColors(),
) {
    val premiumPct = (tracking as? TrackingQuality.Tracked)?.premiumPct ?: return
    GaugeTrack(referenceLabel, premiumPct, scalePct, modifier, colors)
}

@Composable
private fun GaugeTrack(
    referenceLabel: String,
    tokenPremiumPct: Double,
    scalePct: Double,
    modifier: Modifier,
    colors: AmberColors,
) {
    val tick = gaugeTick(tokenPremiumPct, scalePct)
    val premiumText = Fmt.percent(tokenPremiumPct)
    val caption = stringResource(
        if (tick.offScale) R.string.detail_gauge_caption_off else R.string.detail_gauge_caption,
        referenceLabel,
        Fmt.plain(scalePct) + "%",
    )
    val description = "$referenceLabel: ${spoken(premiumText)}"
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 18.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DrawCanvas(Modifier.fillMaxWidth().height(14.dp)) {
            val one = 1.dp.toPx()
            val two = 2.dp.toPx()
            // The track stops short of the padding: a gutter of OffScaleGap plus the tick's own
            // width is left at each end, so a tick past the scale has somewhere to stand that is
            // visibly not on the track.
            val gutter = OffScaleGap.toPx() + two
            val trackWidth = size.width - gutter * 2
            drawRect(color = colors.border, topLeft = Offset(gutter, 6.dp.toPx()), size = Size(trackWidth, one))
            // End stops, so the scale has a span whose ends can be seen. Without them a tick at
            // the end and a tick past the end are the same picture.
            drawRect(color = colors.border, topLeft = Offset(gutter, 4.dp.toPx()), size = Size(one, 6.dp.toPx()))
            drawRect(color = colors.border, topLeft = Offset(gutter + trackWidth - one, 4.dp.toPx()), size = Size(one, 6.dp.toPx()))
            drawRect(
                color = colors.textTertiary(AmberSurface.GROUND),
                topLeft = Offset(gutter + (trackWidth - one) / 2f, 2.dp.toPx()),
                size = Size(one, 10.dp.toPx()),
            )
            val x = when {
                !tick.offScale -> gutter + (trackWidth - two) * tick.fraction
                tick.fraction > 0.5f -> size.width - two
                else -> 0f
            }
            drawRect(color = colors.actionText, topLeft = Offset(x, 0f), size = Size(two, size.height))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = caption,
                style = PlainTickerType.small,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = premiumText,
                style = PlainTickerType.monoSmall,
                color = colors.actionText,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** The gap between the end of the track and a tick standing off it. */
private val OffScaleGap = 6.dp

/**
 * Where the token tick goes, and whether it is a position or a cap.
 *
 * @param fraction 0 to 1 along the track, the reference at 0.5. It is a position on the stated
 *   scale only while [offScale] is false; past the end it is clamped, and a clamped tick is
 *   exactly the thing a reader must never be handed as a value.
 * @param offScale true when the premium runs past the stated scale. The gauge then stands the
 *   tick off the track and the caption says the reading is past the scale, so a saturated tick
 *   cannot be read as the end of the scale. The exact premium is printed beside it either way.
 */
data class GaugeTick(val fraction: Float, val offScale: Boolean)

/**
 * The one answer behind both the drawing and the caption, so the two cannot drift: 0.09 on a 2.5
 * scale is 0.518, minus 0.95 is 0.31, minus 2.34 is 0.032, and minus 3.0 is past the scale.
 *
 * A scale of zero, or a premium that is not a finite number, puts the tick on the reference and
 * claims nothing.
 */
fun gaugeTick(tokenPremiumPct: Double, scalePct: Double): GaugeTick {
    if (scalePct <= 0.0 || !tokenPremiumPct.isFinite()) return GaugeTick(0.5f, offScale = false)
    val raw = 0.5 + tokenPremiumPct / scalePct * 0.5
    return GaugeTick(raw.coerceIn(0.0, 1.0).toFloat(), offScale = raw < 0.0 || raw > 1.0)
}

@InstrumentPreviews
@Composable
private fun GaugePreview() {
    PreviewCanvas {
        Column {
            // Premiums the live catalogue actually produced. The preview drawing only +0.09
            // percent is why a pinned tick reached a signed release without being seen.
            Gauge("Token vs NYSE close", TrackingQuality.Tracked(0.09, poolUsd = 1_300_000.0))
            Gauge("Token vs NYSE close", TrackingQuality.Tracked(-0.95, poolUsd = 1_900_000.0))
            Gauge("Token vs NYSE close", TrackingQuality.Tracked(-2.34, poolUsd = 12_500.0))
            // Past the scale on each side: the tick stands off the track and the caption says so.
            Gauge("Token vs NYSE close", TrackingQuality.Tracked(-4.10, poolUsd = 11_200.0))
            Gauge("Token vs NYSE close", TrackingQuality.Tracked(6.80, poolUsd = 10_400.0))
            // Below the floor, and priced with no depth: the gauge draws nothing at all, because
            // the price block above it has already stated the pool.
            Gauge("Token vs NYSE close", TrackingQuality.Thin(poolUsd = 34.0))
            Gauge("Token vs NYSE close", TrackingQuality.Untracked)
        }
    }
}
