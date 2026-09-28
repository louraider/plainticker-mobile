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
import androidx.compose.ui.geometry.CornerRadius
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
import com.plainticker.mobile.ui.theme.AmberType

/**
 * The tracking gauge, the signature element of Detail (DESIGN.md section 1) and the one piece of
 * this restyle the founder's own approved mockup draws (`scratchpad/design/mockups/gen.py`,
 * `detail_body`'s Amber fragment and `gauge(full=True)`): a full-width, [AmberColors.surfaceHigh]
 * track drawn as a 4dp capsule rather than Instrument's 1dp hairline between two perpendicular end
 * stops, a 1dp tertiary tick at 50 percent for the reference (the NYSE close) standing taller than
 * the track, and a 2dp action-coloured tick for the token, full canvas height, placed on a stated
 * scale. Caption left, the signed premium right in the same action colour, both in Amber's own
 * type (`AmberType.context` and `AmberType.figureInline`, DESIGN.md section 3).
 *
 * **The departures-board read, and why the end stops are gone.** The mockup's own Amber CSS
 * (`gen.py`: `.d-amber .gauge .tr{height:4px;...border-radius:2px;background:var(--h)}` and
 * `.d-amber .gauge .e{display:none}`) turns the hairline into a rounded capsule and hides
 * Instrument's separate end-stop ticks outright: a capsule's own rounded ends already read as the
 * scale's boundary, so a second pair of marks drawn just to say "this is the end" is exactly the
 * "broadsheet hairlines with nothing else to organise a screen" DESIGN.md section 8 names as a
 * generated-look marker once the track itself can say it.
 *
 * **Meaning survives without colour.** The token's tick is the only amber mark here, but it is
 * never colour alone that says so: it is 2dp wide and spans the full canvas height, standing
 * proud on both sides of the 4dp track under it, where the reference tick is 1dp wide and a
 * shorter, fixed length, and the track itself is a flat tonal fill with no mark at all. A reader
 * who cannot see amber against [AmberColors.surfaceGround] still reads three different shapes: a
 * capsule, a short hairline, and a tall bar that crosses it.
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
 * in this slot was read after both operands and lost the argument to them. This restyle changes
 * none of that logic, only the drawing beneath it: the liquidity floor's own early return below is
 * untouched, and the legally-committed disclosure sentence still never routes through this file
 * (`R.string.detail_gauge_thin` is disclosed above the gauge, in `PriceBlock`, not here).
 *
 * A [TrackingQuality.Tracked] token whose reference price Jupiter did not send draws nothing at
 * all: there is no premium to place, and the price row above already says the reference is
 * unavailable (docs/data-map.md, Detail).
 *
 * **The scale is stated and its ends are drawn.** A premium past [scalePct] is drawn as a cap
 * standing off the track, never as a tick resting on the end: the tracked set runs out to about
 * 2.3 percent and any fixed scale can be exceeded, so a saturated tick had to be made
 * unmistakable rather than made impossible. See [gaugeTick]. That cap keeps the same, purely
 * geometric tell this restyle inherits rather than invents: [OffScaleGap] stands it off the
 * capsule's own rounded end, in the open canvas beyond the track, never resting against it, so a
 * saturated reading is visibly not a position on the stated scale before a reader ever reaches the
 * caption that says so in words.
 *
 * **Motion.** None: this canvas answers to `positionPct`-style continuous change nowhere the
 * research draws (section 5.3 names one orchestrated cold-start moment plus the bar pill and the
 * sheet, none of them this component), and the smoke script's animator scale of 0 changes nothing
 * about what this composable draws, because it never animates in the first place.
 *
 * **Measured, not guessed.** `caption` has no `maxLines` set (unchanged by this restyle) so it
 * always wraps rather than clips; `premiumText` is a short, bounded numeral
 * (`maxLines = 1, softWrap = false`). fontTools against `res/font/bricolage_grotesque.ttf`,
 * 2026-09-22, `context` and `figureInline` each instantiated at their own `wght`/`wdth`/`opsz`. On
 * a 400dp frame this composable's own 20dp side padding leaves 360dp, split by a 12dp gap. Even a
 * synthetic worst-case premium past anything the tracked catalogue has produced ("-999.99%",
 * 62.314dp at `figureInline`'s 14sp/400 `tnum`, well past the real worst measured, INTCx at
 * -4.13%, 45.458dp) leaves `caption` a 360 − 62.314 − 12 = **285.686dp** budget; the longest real
 * caption, the off-scale sentence ("Token vs last US price, past the 4.5% scale", 41 characters), is
 * 268.660dp at `context`'s 14sp/400, a 17.026dp margin on one line. At 1.3x font scale that same
 * caption (349.258dp) no longer fits the 1.3x-scaled budget (267dp), so it wraps to two lines,
 * exactly the graceful behaviour its unbounded `maxLines` exists for rather than a defect.
 *
 * @param referenceLabel what the token is being measured against, e.g. "Token vs last US price".
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
        DrawCanvas(Modifier.fillMaxWidth().height(GaugeCanvasHeight)) {
            val one = 1.dp.toPx()
            val two = 2.dp.toPx()
            // The track stops short of the padding: a gutter of OffScaleGap plus the tick's own
            // width is left at each end, so a tick past the scale has somewhere to stand that is
            // visibly not on the track.
            val gutter = OffScaleGap.toPx() + two
            val trackWidth = size.width - gutter * 2
            // The departures-board capsule (gen.py's Amber `.gauge .tr`): 4dp thick, rounded to a
            // full pill (2dp corner radius, half its own height), surfaceHigh rather than a 1dp
            // border hairline. Centred in the canvas: TrackThickness above and below leave equal
            // margin top and bottom of GaugeCanvasHeight.
            val trackTop = (GaugeCanvasHeight.toPx() - TrackThickness.toPx()) / 2f
            drawRoundRect(
                color = colors.surfaceHigh,
                topLeft = Offset(gutter, trackTop),
                size = Size(trackWidth, TrackThickness.toPx()),
                cornerRadius = CornerRadius(TrackThickness.toPx() / 2f),
            )
            // The reference tick at the midpoint: unchanged from the hairline design, a short
            // tertiary mark standing taller than the capsule on both sides so it reads as a
            // crossing peg rather than part of the track's own fill.
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
            // The token's own tick: full canvas height, so it stands proud of the capsule on both
            // sides and is never confused with the reference mark by shape alone, colour aside.
            drawRect(color = colors.actionText, topLeft = Offset(x, 0f), size = Size(two, size.height))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = caption,
                style = AmberType.context,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = premiumText,
                style = AmberType.figureInline,
                color = colors.actionText,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** The whole canvas: tall enough for the reference tick and the token tick to stand above and below the capsule. */
private val GaugeCanvasHeight = 14.dp

/** The departures-board capsule's own thickness (gen.py's Amber `.gauge .tr`, `height:4px`). */
private val TrackThickness = 4.dp

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
    AmberPreviewCanvas {
        Column {
            // Premiums the live catalogue actually produced. The preview drawing only +0.09
            // percent is why a pinned tick reached a signed release without being seen.
            Gauge("Token vs last US price", TrackingQuality.Tracked(0.09, poolUsd = 1_300_000.0))
            Gauge("Token vs last US price", TrackingQuality.Tracked(-0.95, poolUsd = 1_900_000.0))
            Gauge("Token vs last US price", TrackingQuality.Tracked(-2.34, poolUsd = 12_500.0))
            // Past the scale on each side: the tick stands off the track and the caption says so.
            Gauge("Token vs last US price", TrackingQuality.Tracked(-4.10, poolUsd = 11_200.0))
            Gauge("Token vs last US price", TrackingQuality.Tracked(6.80, poolUsd = 10_400.0))
            // Below the floor, and priced with no depth: the gauge draws nothing at all, because
            // the price block above it has already stated the pool.
            Gauge("Token vs last US price", TrackingQuality.Thin(poolUsd = 34.0))
            Gauge("Token vs last US price", TrackingQuality.Untracked)
        }
    }
}
