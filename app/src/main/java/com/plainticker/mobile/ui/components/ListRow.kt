package com.plainticker.mobile.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.ui.theme.Elevated
import com.plainticker.mobile.ui.theme.Ink
import com.plainticker.mobile.ui.theme.Ink2
import com.plainticker.mobile.ui.theme.Line
import com.plainticker.mobile.ui.theme.Muted
import com.plainticker.mobile.ui.theme.PlainTickerType

/**
 * Instrument's ticker row: a 64dp-minimum row, ticker mono 18 and company 13 left with a mono 12
 * meta line (up to two lines, so a numeral wraps rather than clips), a mono 18 value and a 13 sub
 * word right, an optional trailing text action, one Line divider below. One focusable item for a
 * screen reader (descendants merged) with the click labelled "Open TSLAx"; the trailing action
 * stays its own target. [muted] is the price-only row: everything in Muted.
 *
 * [description] is what the merged item says instead of its parts read end to end. Without it a
 * reader hears the row's cells in order, punctuation and all ("TSLAx Tesla, Inc. 2.01364 TSLAx
 * · +0.09% vs NYSE close"); with it the row is one spoken sentence, which is what plan section 13
 * Pass 6 asks of a list row. The caller composes it, because only the caller knows which cell is
 * which; [com.plainticker.mobile.ui.components.spoken] is what turns a numeral into words.
 *
 * **Not retired, and why that is not the same task as finishing the migration.** Every
 * product-facing caller now draws [AmberTickerRow] instead (Onboarding's backdrop, Watchlist,
 * Vote's leader row — see [AmberTickerRow]'s own doc comment, "The leader row, unblocked", for the
 * case that blocked the last of them). One caller is left, deliberately: `GalleryScreen.kt`
 * (debug builds only), which exists to check the phone against `design/canvas/instrument.py`'s own
 * artboards — its `RowSample` data is that canvas's own numbers, field for field
 * (`GallerySamples.kt`'s own doc comment). Retargeting its rows to [AmberTickerRow] would make it
 * stop matching the artboards it exists to validate against, which is a different task (rebuilding
 * a comparison tool against a new canvas) than migrating a product screen's anatomy. This row stays
 * until that tool is retargeted or retired on its own terms.
 */
@Composable
fun ListRow(
    ticker: String,
    company: String?,
    modifier: Modifier = Modifier,
    meta: String? = null,
    valueRight: String? = null,
    valueSub: String? = null,
    trailingAction: String? = null,
    onTrailingAction: (() -> Unit)? = null,
    muted: Boolean = false,
    /**
     * Whether to keep the state word's column open on a row that has no word.
     *
     * A section where some rows carry a word and some do not is still one column of numerals to
     * a reader scanning it, so the caller says so once for the whole section and the odd row out
     * lines up with its neighbours instead of sitting [ValueSubWidth] to their right.
     */
    reserveValueSub: Boolean = valueSub != null,
    /**
     * The second cell in the numeral face. A receipt's "to 0.01364 TSLAx" is an amount and a
     * ticker, and DESIGN.md section 3 gives both to JetBrains Mono; the words that join them come
     * with it, exactly as they do on the mono meta line below.
     */
    companyMono: Boolean = false,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
    onClickLabel: String = "Open $ticker",
    description: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val tickerColor = if (muted) Muted else Ink
    val companyColor = if (muted) Muted else Ink2
    val interaction = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClickLabel = onClickLabel,
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        Modifier.semantics(mergeDescendants = true) {}
    }
    // On the same node as the merge above, so it replaces what the cells would have said rather
    // than being read after them.
    val spokenAs = if (description != null) {
        Modifier.semantics { contentDescription = description }
    } else {
        Modifier
    }
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .focusOutline(interactionSource)
                .background(if (pressed) Elevated else Color.Transparent)
                .then(interaction)
                .then(spokenAs)
                .defaultMinSize(minHeight = 64.dp)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = ticker,
                        style = PlainTickerType.listTicker,
                        color = tickerColor,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.alignByBaseline(),
                    )
                    if (company != null) {
                        Text(
                            text = company,
                            style = if (companyMono) PlainTickerType.monoSmall else PlainTickerType.small,
                            color = companyColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.alignByBaseline().weight(1f, fill = false),
                        )
                    }
                }
                if (meta != null) {
                    Text(
                        text = meta,
                        style = PlainTickerType.meta,
                        color = Muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (valueRight != null) {
                        Text(
                            text = valueRight,
                            style = if (muted) PlainTickerType.listValueMuted else PlainTickerType.listTicker,
                            color = tickerColor,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                    // The word gets a column of its own, so its width stops deciding where the
                    // number starts. It is set at the start of that column rather than the end,
                    // which puts two aligned left edges and one aligned right edge on the screen:
                    // a table, which is what a column of tabular numerals is for.
                    if (valueSub != null || reserveValueSub) {
                        Text(
                            text = valueSub.orEmpty(),
                            style = PlainTickerType.small,
                            color = Muted,
                            maxLines = 1,
                            modifier = Modifier
                                .alignByBaseline()
                                .width(valueSubWidth(LocalDensity.current.fontScale)),
                        )
                    }
                }
                if (trailingAction != null && onTrailingAction != null) {
                    TextAction(
                        label = trailingAction,
                        onClick = onTrailingAction,
                        contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 0.dp, bottom = 10.dp),
                    )
                }
            }
        }
        if (divider) HorizontalDivider(thickness = 1.dp, color = Line)
    }
}

/**
 * The width the state word's column is given, at a font scale of one.
 *
 * Measured on the Seeker from a uiautomator dump on 2026-09-13: "strong" is 112 physical pixels
 * at 480dpi, which is 37.3dp, and it is the widest of the three words the list uses. Before this
 * the word and the number were right-aligned as one group, so the word's width decided where the
 * number began: "84" started at x940, "72" at x960, "79" at x989, a 49px jog down the one column
 * a reader scans, in the app whose type system was chosen for tabular numerals.
 */
val ValueSubWidth: Dp = 40.dp

/**
 * That column at the reader's font scale.
 *
 * Android 14 scales 13sp text non-linearly, so a scale of 1.3 grows a glyph by about 1.19 (the
 * device smoke walk measured 119 percent). Scaling the column by the raw factor therefore
 * over-allocates slightly, which spends a little whitespace and can never clip the word. Held at
 * one from below, because a reader who shrinks the type does not need the column to shrink with
 * it, and at two from above, so the largest scale cannot eat the ticker beside it.
 */
internal fun valueSubWidth(fontScale: Float): Dp = ValueSubWidth * fontScale.coerceIn(1f, 2f)

@InstrumentPreviews
@Composable
private fun ListRowPreview() {
    PreviewCanvas {
        Column {
            // The three state words in the order the device drew them, so the preview shows the
            // one thing the finding was about: three numerals that end in the same place.
            ListRow(
                ticker = "NEMx",
                company = "Newmont Corp.",
                meta = "Analysis 7 d old",
                valueRight = "84",
                valueSub = "strong",
                onClick = {},
            )
            ListRow(
                ticker = "ABNBx",
                company = "Airbnb, Inc.",
                meta = "Analysis 1 d old",
                valueRight = "72",
                valueSub = "weak",
                onClick = {},
            )
            ListRow(
                ticker = "NVDAx",
                company = "NVIDIA Corporation",
                meta = "-0.95% vs NYSE close · 7 d old",
                valueRight = "79",
                valueSub = "fair",
                onClick = {},
            )
            // A classified row with no state word keeps the column open, so it lines up too.
            ListRow(
                ticker = "PGRx",
                company = "Progressive Corp.",
                meta = "Analysis 7 d old",
                valueRight = "100",
                reserveValueSub = true,
                onClick = {},
            )
            ListRow(
                ticker = "TSLAx",
                company = "Tesla, Inc.",
                meta = "+0.09% vs NYSE close · 2 d old",
                valueRight = "71",
                valueSub = "strong",
                onClick = {},
            )
            ListRow(
                ticker = "AAPLx",
                company = "Apple Inc.",
                meta = "Reports Oct 30 · +0.01% vs NYSE close",
                trailingAction = "Unwatch",
                onTrailingAction = {},
                onClick = {},
            )
            ListRow(
                ticker = "ASMLx",
                company = "ASML Holding",
                meta = "-0.01% vs NYSE close",
                valueRight = "\$812.40",
                muted = true,
                divider = false,
                onClick = {},
            )
        }
    }
}
