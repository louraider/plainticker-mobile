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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.myapp.ui.theme.Accent
import com.myapp.ui.theme.Ink2
import com.myapp.ui.theme.LineStrong
import com.myapp.ui.theme.Muted
import com.myapp.ui.theme.PlainTickerType

/**
 * The tracking gauge: a 1dp Line strong track, a 1dp Muted tick at 50 percent for the reference
 * (the NYSE close) and a 2dp Accent tick for the token, placed on a stated scale and clamped to the
 * track. Caption left in Outfit 13, the signed premium right in mono 13 Accent.
 *
 * @param tokenPremiumPct the token's premium over the reference, in percent (0.09 for +0.09%).
 * @param premiumText the formatted premium, already signed ("+0.09%").
 * @param scalePct the premium that fills half the track on each side; 0.5 percent by default.
 */
@Composable
fun Gauge(
    referenceLabel: String,
    tokenPremiumPct: Double,
    premiumText: String,
    modifier: Modifier = Modifier,
    scalePct: Double = 0.5,
) {
    val fraction = gaugePosition(tokenPremiumPct, scalePct)
    val caption = "$referenceLabel, scale ${plainNumber(scalePct)}%"
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
            Gauge(referenceLabel = "Token vs NYSE close", tokenPremiumPct = 0.09, premiumText = "+0.09%")
            Gauge(referenceLabel = "Token vs NYSE close", tokenPremiumPct = -0.61, premiumText = "-0.61%")
            Gauge(referenceLabel = "Token vs NYSE close", tokenPremiumPct = 2.4, premiumText = "+2.40%")
        }
    }
}
