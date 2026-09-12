package com.myapp.ui.components

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
import com.myapp.R
import com.myapp.data.jupiter.TrackingQuality
import com.myapp.ui.Fmt
import com.myapp.ui.theme.Accent
import com.myapp.ui.theme.Ink2
import com.myapp.ui.theme.LineStrong
import com.myapp.ui.theme.Muted
import com.myapp.ui.theme.PlainTickerType

/**
 * The tracking gauge, the signature element of Detail (DESIGN.md section 1): a 1dp Line strong
 * track, a 1dp Muted tick at 50 percent for the reference (the NYSE close) and a 2dp Accent tick
 * for the token, placed on a stated scale and clamped to the track. Caption left in Outfit 13,
 * the signed premium right in mono 13 Accent.
 *
 * The gauge is drawn only where the quote behind it means something. [TrackingQuality] is the one
 * rule, shared with the list row, and it is asked here rather than by the caller so no screen can
 * draw a tracking gauge for a token nothing tracks: below the liquidity floor the track is not
 * drawn at all and one sentence states the pool instead. The sentence is a fact about the token,
 * so it is Ink 2 like any other caption and never Caution, which DESIGN.md section 2 keeps for
 * explicit issuer control.
 *
 * A [TrackingQuality.Tracked] token whose reference price Jupiter did not send draws nothing at
 * all: there is no premium to place, and the price row above already says the reference is
 * unavailable (docs/data-map.md, Detail).
 *
 * @param referenceLabel what the token is being measured against, e.g. "Token vs NYSE close".
 * @param tracking the answer from the one rule, built from the Price v3 entry.
 * @param scalePct the premium that fills half the track on each side; 0.5 percent by default.
 */
@Composable
fun Gauge(
    referenceLabel: String,
    tracking: TrackingQuality,
    modifier: Modifier = Modifier,
    scalePct: Double = 0.5,
) {
    when (tracking) {
        is TrackingQuality.Tracked ->
            tracking.premiumPct?.let { GaugeTrack(referenceLabel, it, scalePct, modifier) }

        is TrackingQuality.Thin ->
            PoolLine(stringResource(R.string.detail_gauge_thin, Fmt.compactMoney(tracking.poolUsd, roundDown = true)), modifier)

        TrackingQuality.Untracked ->
            PoolLine(stringResource(R.string.detail_gauge_pool_unknown), modifier)
    }
}

@Composable
private fun GaugeTrack(
    referenceLabel: String,
    tokenPremiumPct: Double,
    scalePct: Double,
    modifier: Modifier,
) {
    val fraction = gaugePosition(tokenPremiumPct, scalePct)
    val premiumText = Fmt.percent(tokenPremiumPct)
    val caption = stringResource(R.string.detail_gauge_caption, referenceLabel, Fmt.plain(scalePct) + "%")
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
            drawRect(color = LineStrong, topLeft = Offset(0f, 6.dp.toPx()), size = Size(size.width, one))
            drawRect(color = Muted, topLeft = Offset((size.width - one) / 2f, 2.dp.toPx()), size = Size(one, 10.dp.toPx()))
            drawRect(color = Accent, topLeft = Offset((size.width - two) * fraction, 0f), size = Size(two, size.height))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = caption, style = PlainTickerType.small, color = Ink2, modifier = Modifier.weight(1f))
            Text(text = premiumText, style = PlainTickerType.monoSmall, color = Accent, maxLines = 1, softWrap = false)
        }
    }
}

/** What stands where the gauge would be: one sentence about the pool, in the gauge's own slot. */
@Composable
private fun PoolLine(text: String, modifier: Modifier) {
    Text(
        text = text,
        style = PlainTickerType.small,
        color = Ink2,
        modifier = modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp),
    )
}

/**
 * Where the token tick sits along the track, 0 to 1: the reference is at 0.5 and a premium of
 * plus or minus [scalePct] reaches the end. 0.09 on a 0.5 scale is 0.59. Clamped.
 */
fun gaugePosition(tokenPremiumPct: Double, scalePct: Double): Float {
    if (scalePct <= 0.0 || tokenPremiumPct.isNaN()) return 0.5f
    return (0.5 + tokenPremiumPct / scalePct * 0.5).coerceIn(0.0, 1.0).toFloat()
}

@InstrumentPreviews
@Composable
private fun GaugePreview() {
    PreviewCanvas {
        Column {
            Gauge("Token vs NYSE close", TrackingQuality.Tracked(0.09, poolUsd = 1_300_000.0))
            Gauge("Token vs NYSE close", TrackingQuality.Tracked(-0.61, poolUsd = 184_000.0))
            Gauge("Token vs NYSE close", TrackingQuality.Tracked(2.4, poolUsd = 12_500.0))
            // Below the floor: the track is gone and the pool is stated. APPx and UBERx as read
            // live on 2026-09-12, and a token Jupiter priced without reporting any depth.
            Gauge("Token vs NYSE close", TrackingQuality.Thin(poolUsd = 34.0))
            Gauge("Token vs NYSE close", TrackingQuality.Thin(poolUsd = 9_840.0))
            Gauge("Token vs NYSE close", TrackingQuality.Untracked)
        }
    }
}
