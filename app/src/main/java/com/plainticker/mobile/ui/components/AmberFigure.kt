package com.plainticker.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberSurface
import com.plainticker.mobile.ui.theme.AmberType

/**
 * The number with its context (docs/design-research-2026-09-21.md section 5.5, "Number with
 * context"): the figure this product's whole argument rests on, 34/700 tnum in
 * [AmberColors.actionText], an optional label above it and a context line below, in a 28dp card.
 * DESIGN.md section 1 is why the label matters as much as the figure: "the token's own figure is
 * named for what it is (\"Pool quote\", not \"Token price\")," so this component never guesses a
 * label, it only draws the one it is given.
 *
 * **Colour is never a second cue for direction.** DESIGN.md section 7 and the foundation rule in
 * docs/design-research-2026-09-21.md section 4 both apply here: [figure] is always
 * [AmberColors.actionText], win or lose, above or below a reference; whether it read as a gain or
 * a state is the sentence in [context], set in [AmberColors.textSecondary], never a colour change
 * on the number itself. [tone] exists only for the one caution figure DESIGN.md section 1.2
 * actually asks for (an explicit issuer-control risk), and even then colours the [context]
 * sentence, not [figure]: "never on a number" (docs/design-research-2026-09-21.md section 5.3).
 *
 * **The clipping trap, resolved the same way as [AmberTickerRow]: no fixed width anywhere.**
 * [card] applies `fillMaxWidth()`, never a literal dp, so nothing here can be squeezed to a
 * narrower slot than the screen actually gives it. [figure] stays single line (short, tabular
 * content: a price, a percentage, a composite) with nothing beside it to clip against; [context]
 * is left free to wrap across as many lines as a full sentence needs (Today's own lede is one,
 * "22 of 160 analyzed can be tracked today"), which is why it carries no `maxLines` here at all.
 */
@Composable
fun AmberFigure(
    figure: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    context: String? = null,
    colors: AmberColors = defaultAmberColors(),
    tone: FactTone = FactTone.Neutral,
    /** False draws the figure bare, no card: Detail's plain composite cell (research 5.5's `nwc.plain`). */
    card: Boolean = true,
) {
    val body = @Composable {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (label != null) {
                Text(
                    text = label,
                    style = AmberType.meta,
                    color = colors.textTertiary(if (card) AmberSurface.RAISED else AmberSurface.GROUND),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = figure,
                style = AmberType.figureLarge,
                color = colors.actionText,
                maxLines = 1,
                softWrap = false,
            )
            if (context != null) {
                Text(
                    text = context,
                    style = AmberType.context,
                    color = if (tone == FactTone.Caution) colors.stateCaution else colors.textSecondary,
                )
            }
        }
    }
    if (card) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(colors.surfaceRaised)
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            body()
        }
    } else {
        Column(modifier = modifier.fillMaxWidth()) {
            body()
        }
    }
}

@InstrumentPreviews
@Composable
private fun AmberFigurePreview() {
    AmberPreviewCanvas {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AmberFigure(figure = "22", context = "22 of 160 analyzed can be tracked today")
            AmberFigure(label = "Token price", figure = "\$334.54", context = "+0.12% vs NYSE close \$334.14")
            AmberFigure(figure = "50", context = "of 100, sector median 55", card = false)
        }
    }
}
