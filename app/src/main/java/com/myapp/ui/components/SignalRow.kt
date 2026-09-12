package com.myapp.ui.components

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
import com.myapp.R
import com.myapp.ui.theme.Ink
import com.myapp.ui.theme.Ink2
import com.myapp.ui.theme.Muted
import com.myapp.ui.theme.PlainTickerType

/**
 * One F-Score signal, 44dp: the name 15 Ink 2 left, the answer in mono 14 right.
 *
 * Three answers, not two. The server widened `fscore.signals` from boolean to nullable when the
 * filings behind a check are missing (docs/data-map.md), and a check nobody could evaluate is not
 * a check the company failed, so a null reads "n/a" in Muted and never "no".
 */
@Composable
fun SignalRow(
    name: String,
    ok: Boolean?,
    modifier: Modifier = Modifier,
) {
    val word = stringResource(
        when (ok) {
            true -> R.string.word_yes
            false -> R.string.word_no
            null -> R.string.word_na
        },
    )
    val passed = ok == true
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
            style = PlainTickerType.rowText,
            color = if (passed) Ink2 else Muted,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = word,
            style = PlainTickerType.monoRow,
            color = if (passed) Ink else Muted,
            maxLines = 1,
        )
    }
}

@InstrumentPreviews
@Composable
private fun SignalRowPreview() {
    PreviewCanvas {
        Column {
            SignalRow(name = "Return on assets positive", ok = true)
            SignalRow(name = "Liquidity improving", ok = false)
            SignalRow(name = "Gross margin improving", ok = null)
        }
    }
}
