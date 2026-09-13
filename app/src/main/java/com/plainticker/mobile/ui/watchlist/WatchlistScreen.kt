package com.plainticker.mobile.ui.watchlist

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plainticker.mobile.R
import com.plainticker.mobile.ui.components.Banner
import com.plainticker.mobile.ui.components.Heading
import com.plainticker.mobile.ui.components.InstrumentPreviews
// The row component and this screen's row model share a name; the anatomy keeps an alias.
import com.plainticker.mobile.ui.components.ListRow as InstrumentRow
import com.plainticker.mobile.ui.components.Panel
import com.plainticker.mobile.ui.components.PreviewCanvas
import com.plainticker.mobile.ui.components.SkeletonRows
import com.plainticker.mobile.ui.components.TextAction
import com.plainticker.mobile.ui.components.spoken
import com.plainticker.mobile.ui.text
import com.plainticker.mobile.ui.theme.Ink
import com.plainticker.mobile.ui.theme.Ink2
import com.plainticker.mobile.ui.theme.Muted
import com.plainticker.mobile.ui.theme.PlainTickerType
import com.plainticker.mobile.watchlist.DigestRecord
import com.plainticker.mobile.watchlist.WatchedTicker
import java.time.LocalDate

/**
 * The Watchlist (T12, DT8; design/canvas/instrument.py screen_watchlist). One scrolling column:
 * the header the host hands in, the one banner slot, "Watched" with a row per ticker, then
 * "Daily digest" with the last digest the background check produced.
 *
 * The digest is on the screen for a reason that is not decoration. A notification happens once, on
 * a lock screen, and is then gone; if the app cannot show what it said, the app has told a person
 * something it cannot stand behind afterwards. So the Panel draws the stored string, the same one
 * the notification carried, with the time it was produced above it.
 *
 * Nothing here computes anything: [WatchlistModel.kt] picks every sentence and
 * [WatchlistViewModel] every number, so a premium on a row comes from the same rule as the list's
 * and the digest is never re-derived from the rows on screen.
 */
@Composable
fun WatchlistScreen(
    viewModel: WatchlistViewModel,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    onBrowseList: (() -> Unit)? = null,
    onRunCheck: (() -> Unit)? = null,
    header: @Composable () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The one piece of this screen that can change while the app is away: a reader who took the
    // Enable action went to the system settings and came back.
    LifecycleResumeEffect(viewModel) {
        viewModel.notificationsChanged()
        onPauseOrDispose { }
    }

    WatchlistContent(
        state = state,
        onUnwatch = viewModel::unwatch,
        onRetry = viewModel::refresh,
        onOpenDetail = onOpenDetail,
        onBrowseList = onBrowseList,
        onRunCheck = onRunCheck,
        // One way to turn notifications back on, where the line that says they are off is. It
        // opens the system settings and nothing else: the app asks for the permission once, at
        // the first watch, and never asks again (see DetailScreen).
        onEnableNotifications = {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        },
        modifier = modifier,
        header = header,
    )
}

@Composable
internal fun WatchlistContent(
    state: WatchlistUiState,
    onUnwatch: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onBrowseList: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onRunCheck: (() -> Unit)? = null,
    onEnableNotifications: (() -> Unit)? = null,
    header: @Composable () -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        // The tab content ends above the navigation bar; the padding is part of the scroll.
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
    ) {
        item(key = "header") { header() }
        item(key = "chrome") {
            Column(Modifier.fillMaxWidth()) {
                state.banner?.let {
                    Banner(
                        text = bannerText(it).text(),
                        action = stringResource(R.string.action_retry),
                        onAction = onRetry,
                    )
                }
            }
        }
        item(key = "watched") {
            Heading(text = stringResource(R.string.watchlist_heading_watched), topPadding = HeadingTopGap)
        }

        when {
            // The common first state. It says what watching is for and offers the one place to do
            // it from, rather than leaving a heading over an empty column.
            state.isEmpty -> item(key = "empty") {
                EmptyLine(
                    text = stringResource(R.string.watchlist_empty),
                    action = onBrowseList?.let { stringResource(R.string.action_browse_analyzed) },
                    onAction = onBrowseList,
                )
            }

            state.isCold -> item(key = "skeleton") { SkeletonRows(count = SkeletonRowCount) }

            else -> itemsIndexed(state.rows, key = { _, row -> "w:" + row.ticker }) { index, row ->
                Watched(
                    row = row,
                    last = index == state.rows.lastIndex,
                    onUnwatch = onUnwatch,
                    onOpenDetail = onOpenDetail,
                )
            }
        }

        item(key = "digest") {
            Heading(text = stringResource(R.string.watchlist_heading_digest), topPadding = SectionTopGap)
        }
        item(key = "digest-panel") { Digest(state.digest) }
        item(key = "digest-footer") {
            Footer(
                record = state.digest,
                notificationsOn = state.notificationsOn,
                nowMillis = state.nowMillis,
                onEnableNotifications = onEnableNotifications,
            )
        }
        // Debug builds only, behind the same gate as the component gallery and the wallet spike.
        onRunCheck?.let { run -> item(key = "debug-run") { DebugRunCheck(run) } }
    }
}

/**
 * The fire-now entry point: the daily check, by hand, so the digest and the notification are
 * testable without waiting until tomorrow. It goes through WorkManager, so what it exercises is
 * the whole path the daily run takes and not a shortcut around it.
 */
@Composable
private fun DebugRunCheck(onRunCheck: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Side),
        horizontalArrangement = Arrangement.End,
    ) {
        TextAction(label = stringResource(R.string.debug_run_watchlist_check), onClick = onRunCheck)
    }
}

/**
 * One watched ticker: the token left, the next report and the premium on the meta line, "Unwatch"
 * trailing. The row opens Detail and the text action does not, so the two targets are separate and
 * both are 48dp; there is no swipe, which Pass 2 rejected for discoverability.
 */
@Composable
private fun Watched(
    row: WatchedTicker,
    last: Boolean,
    onUnwatch: (String) -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    val watch = watchRow(row)
    val report = watch.report.text()
    val tracking = watch.tracking?.text()
    val meta = tracking?.let { stringResource(R.string.list_row_meta_join, report, it) } ?: report
    InstrumentRow(
        ticker = watch.symbol,
        company = watch.company,
        meta = meta,
        trailingAction = stringResource(R.string.action_unwatch),
        onTrailingAction = { onUnwatch(watch.ticker) },
        divider = !last,
        onClick = { onOpenDetail(watch.ticker) },
        onClickLabel = stringResource(R.string.action_open_ticker, watch.symbol),
        description = sentence(watch.symbol, watch.company, report, tracking),
    )
}

/** The digest Panel: the time it was produced in mono meta, the digest itself under it. */
@Composable
private fun Digest(record: DigestRecord) {
    val panel = digestPanel(record)
    Panel {
        panel.producedAt?.let { Text(text = it, style = PlainTickerType.meta, color = Muted) }
        Text(text = panel.body.text(), style = PlainTickerType.panelBody, color = Ink)
    }
}

/**
 * Where the next digest goes, and when one was last looked for.
 *
 * A device that will not show notifications says so once, here, with the way to change it beside
 * the sentence. That is the whole of the app's response to a refused permission: the digest is on
 * the screen either way, and nothing asks again.
 */
@Composable
private fun Footer(
    record: DigestRecord,
    notificationsOn: Boolean,
    nowMillis: Long,
    onEnableNotifications: (() -> Unit)?,
) {
    val footer = digestFooter(record = record, notificationsOn = notificationsOn, nowMillis = nowMillis)
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = Side, end = Side, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = footer.delivery.text(),
                style = PlainTickerType.small,
                color = Ink2,
                modifier = Modifier.weight(1f),
            )
            if (!notificationsOn && onEnableNotifications != null) {
                TextAction(label = stringResource(R.string.action_enable), onClick = onEnableNotifications)
            }
        }
        footer.checked?.let { Text(text = it.text(), style = PlainTickerType.small, color = Muted) }
    }
}

/** One sentence where the rows would be, so no state of this screen is a blank column. */
@Composable
private fun EmptyLine(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Side),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = PlainTickerType.body,
            color = Ink2,
            modifier = Modifier.weight(1f).padding(bottom = HeadingTopGap),
        )
        if (action != null && onAction != null) TextAction(label = action, onClick = onAction)
    }
}

/**
 * The parts of a row as one spoken phrase, in the order they are drawn, each numeral turned into
 * words ("+0.09%" reads "plus 0.09 percent"). A part that is not on the row is not spoken.
 */
private fun sentence(vararg parts: String?): String =
    parts.filterNot { it.isNullOrBlank() }.joinToString(", ") { spoken(it.orEmpty()) }

private val Side = 20.dp
private val HeadingTopGap = 30.dp
private val SectionTopGap = 30.dp
private const val SkeletonRowCount = 3

// ---- Previews ------------------------------------------------------------------------------

private fun sampleRow(
    ticker: String,
    company: String,
    reportsOn: LocalDate? = null,
    price: Double? = null,
    reference: Double? = null,
    poolUsd: Double? = 250_000.0,
    analyzed: Boolean = true,
) = WatchedTicker(
    ticker = ticker,
    symbol = ticker + "x",
    company = company,
    mint = ticker,
    analyzed = analyzed,
    nextReport = reportsOn,
    priceUsd = price,
    referencePriceUsd = reference,
    poolUsd = poolUsd,
)

private val PreviewState = WatchlistUiState(
    watched = 4,
    rows = listOf(
        sampleRow("TSLA", "Tesla, Inc.", LocalDate.of(2026, 10, 22), 366.50, 366.17),
        sampleRow("AAPL", "Apple Inc.", LocalDate.of(2026, 10, 28), 232.54, 232.52),
        // No report date from the provider, and a pool of $34: both halves say so in words.
        sampleRow("APP", "AppLovin Corp.", null, 1_158.76, 612.00, poolUsd = 34.0),
        // Watched, and no longer on the leaderboard.
        sampleRow("MCD", "McDonald Corp.", null, analyzed = false),
    ),
    digest = DigestRecord(
        text = "4 watched. NVDAx moved from -0.04% to -0.61% against the NYSE close. TSLAx reports in 39 days.",
        producedAtMillis = 1_789_257_600_000L,
        lastCheckedAtMillis = 1_789_268_400_000L,
    ),
    notificationsOn = true,
    nowMillis = 1_789_279_200_000L,
)

@InstrumentPreviews
@Composable
private fun WatchlistPreview() {
    PreviewCanvas {
        WatchlistContent(
            state = PreviewState,
            onUnwatch = {},
            onRetry = {},
            onOpenDetail = {},
            onBrowseList = {},
        )
    }
}

@InstrumentPreviews
@Composable
private fun WatchlistEmptyPreview() {
    PreviewCanvas {
        WatchlistContent(
            state = WatchlistUiState(nowMillis = PreviewState.nowMillis),
            onUnwatch = {},
            onRetry = {},
            onOpenDetail = {},
            onBrowseList = {},
        )
    }
}

@InstrumentPreviews
@Composable
private fun WatchlistNotificationsOffPreview() {
    PreviewCanvas {
        WatchlistContent(
            state = PreviewState.copy(notificationsOn = false, pricesUnavailable = true),
            onUnwatch = {},
            onRetry = {},
            onOpenDetail = {},
            onBrowseList = {},
            onRunCheck = {},
            onEnableNotifications = {},
        )
    }
}
