package com.plainticker.mobile.ui.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.R
import com.plainticker.mobile.ui.components.AmberSheet
import com.plainticker.mobile.ui.components.TextAction
import com.plainticker.mobile.ui.components.defaultAmberColors
import com.plainticker.mobile.ui.theme.AmberSurface
import com.plainticker.mobile.ui.theme.AmberType

/**
 * One row of "Backing and controls", explained (founder feedback 2026-09-29: the section was
 * unclear, and it exists only in the app). [title] is the row's own label wherever the grid has
 * one, so the reader can match the sheet to the screen; [what] says what the row is and [why] why
 * it matters to the person holding the token.
 */
data class BackingTopic(@StringRes val title: Int, @StringRes val what: Int, @StringRes val why: Int)

/**
 * Every row the grid can show, in the grid's own order ([trustFacts]): the reserves, the supply the
 * mint states against the issuer's count, the permanent delegate, pausable transfers, the transfer
 * hook, the split multiplier, and last how the supply is read. The same words are the web page
 * [R.string.backing_explain_url] opens.
 */
val BackingTopics: List<BackingTopic> = listOf(
    BackingTopic(R.string.detail_fact_por, R.string.backing_explain_reserves_what, R.string.backing_explain_reserves_why),
    BackingTopic(R.string.backing_explain_supply_title, R.string.backing_explain_supply_what, R.string.backing_explain_supply_why),
    BackingTopic(R.string.detail_fact_delegate, R.string.backing_explain_delegate_what, R.string.backing_explain_delegate_why),
    BackingTopic(R.string.detail_fact_pausable, R.string.backing_explain_pausable_what, R.string.backing_explain_pausable_why),
    BackingTopic(R.string.detail_fact_hook, R.string.backing_explain_hook_what, R.string.backing_explain_hook_why),
    BackingTopic(R.string.detail_fact_split, R.string.backing_explain_split_what, R.string.backing_explain_split_why),
    BackingTopic(R.string.backing_explain_chain_title, R.string.backing_explain_chain_what, R.string.backing_explain_chain_why),
)

/**
 * "What backing and controls mean": the [AmberSheet] the section head's Explain opens. One
 * scrolling column of plain prose, each topic its row name, then what it is and why it matters,
 * and at the foot the link to the same page on plainticker.com. Nothing here reads the stock: it
 * explains the rows, it does not repeat their values.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackingExplainerSheet(onDismiss: () -> Unit) {
    val colors = defaultAmberColors()
    val openUri = LocalUriHandler.current
    val url = stringResource(R.string.backing_explain_url)
    AmberSheet(onDismissRequest = onDismiss, colors = colors) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.backing_explain_title),
                style = AmberType.sectionHead,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = stringResource(R.string.backing_explain_lede), style = AmberType.body, color = colors.textSecondary)
            BackingTopics.forEach { topic ->
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(topic.title),
                        style = AmberType.rowTicker,
                        color = colors.textPrimary,
                        modifier = Modifier.semantics { heading() },
                    )
                    // On surfaceHigh the tertiary colour promotes to secondary (DESIGN.md section 2).
                    Text(
                        text = stringResource(R.string.backing_explain_what),
                        style = AmberType.meta,
                        color = colors.textTertiary(AmberSurface.HIGH),
                    )
                    Text(text = stringResource(topic.what, *NoArgs), style = AmberType.body, color = colors.textPrimary)
                    Text(
                        text = stringResource(R.string.backing_explain_why),
                        style = AmberType.meta,
                        color = colors.textTertiary(AmberSurface.HIGH),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(text = stringResource(topic.why, *NoArgs), style = AmberType.body, color = colors.textPrimary)
                }
            }
            TextAction(
                label = stringResource(R.string.backing_explain_read_more),
                onClick = { runCatching { openUri.openUri(url) } },
                color = colors.actionText,
                contentPadding = PaddingValues(top = 14.dp, bottom = 14.dp, end = 16.dp),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * Read through the formatting `getString`, so "100%%" in strings.xml draws as "100%". The plain
 * one-argument read would print both percent signs.
 */
private val NoArgs: Array<Any> = emptyArray()
