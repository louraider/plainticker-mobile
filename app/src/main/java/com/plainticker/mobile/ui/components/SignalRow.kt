package com.plainticker.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.R
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberSurface
import com.plainticker.mobile.ui.theme.AmberType

/**
 * One F-Score signal, 44dp: the name secondary left, the answer primary right.
 *
 * Three answers, not two. The server widened `fscore.signals` from boolean to nullable when the
 * filings behind a check are missing (docs/data-map.md), and a check nobody could evaluate is not
 * a check the company failed, so a null reads "n/a" in tertiary text and never "no".
 *
 * **"Primary right" is a colour statement, not a size one.** [name] and [word] share
 * [AmberType.body] and [AmberType.context] respectively purely for their sizes (15sp and 14sp,
 * the closest pair this restyle's word-only token roster offers); what actually marks the answer
 * as primary is [colors.textPrimary] against [name]'s [colors.textSecondary], unchanged from
 * Instrument's own version of this row. `tnum` is deliberately never turned on here: "yes", "no"
 * and "n/a" are words, not numerals, so [AmberType.figureRow]'s tabular feature (which this face
 * widens the comma and period under, DESIGN.md section 3) has no business on this line, unlike
 * Instrument's old `monoRow`, which put every signal's answer in JetBrains Mono whether or not it
 * carried a digit.
 *
 * **Distinguishable without colour twice over.** The word itself ("yes" / "no" / "n/a") already
 * carries the pass/fail/unknown meaning a colourblind reader needs; the muted state additionally
 * dims both [name] and [word] together rather than recolouring just one, so the row still reads
 * as "this whole line is de-emphasised" in greyscale.
 *
 * **[name] already carried no clipping risk, and stays that way.** `weight(1f)` with no `maxLines`
 * lets it wrap instead of squeeze, so nothing about the new, larger 15sp face reopens the trap;
 * measured anyway (fontTools against `res/font/bricolage_grotesque.ttf`, 2026-09-22, `body` at its
 * own 15sp/400): the F-Score's own longest real name, `signal_cfo_positive`'s English string
 * "Operating cash flow positive" (29 characters), is 200.295dp. Against a 360dp content width
 * (400dp frame less this row's own 20dp side padding, twice), less the 12dp gap and the widest
 * `word` this row draws ("yes", 23.506dp at `context`'s 14sp/400), the budget is 360 − 12 −
 * 23.506 = **324.494dp**, a 124.199dp margin, still 57.058dp at 1.3x font scale: this row never
 * actually needs the wrap its own unbounded `maxLines` already allows for.
 *
 * Part of Detail's gated F-Score list, so [colors] defaults to the system-following
 * [defaultAmberColors] rather than Instrument's fixed-dark Ink/Ink2/Muted.
 */
@Composable
fun SignalRow(
    name: String,
    ok: Boolean?,
    modifier: Modifier = Modifier,
    colors: AmberColors = defaultAmberColors(),
) {
    val word = stringResource(
        when (ok) {
            true -> R.string.word_yes
            false -> R.string.word_no
            null -> R.string.word_na
        },
    )
    val passed = ok == true
    val muted = colors.textTertiary(AmberSurface.GROUND)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .clearAndSetSemantics { contentDescription = "$name: $word" },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = AmberType.body,
            color = if (passed) colors.textSecondary else muted,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = word,
            style = AmberType.context,
            color = if (passed) colors.textPrimary else muted,
            maxLines = 1,
        )
    }
}

@InstrumentPreviews
@Composable
private fun SignalRowPreview() {
    AmberPreviewCanvas {
        Column {
            SignalRow(name = "Return on assets positive", ok = true)
            SignalRow(name = "Liquidity improving", ok = false)
            SignalRow(name = "Gross margin improving", ok = null)
        }
    }
}
