package com.myapp.ui.detail

import com.myapp.R
import com.myapp.data.MultiplierSource
import com.myapp.data.SplitMultiplier
import com.myapp.data.jupiter.PriceEntry
import com.myapp.data.jupiter.StockData
import com.myapp.data.jupiter.TrackingQuality
import com.myapp.data.plainticker.AnalysisPayload
import com.myapp.data.plainticker.Axes
import com.myapp.data.plainticker.Axis
import com.myapp.data.plainticker.FScore
import com.myapp.data.plainticker.Method
import com.myapp.data.rpc.DefaultAccountState
import com.myapp.data.rpc.PausableConfig
import com.myapp.data.rpc.PermanentDelegate
import com.myapp.data.rpc.TransferHookConfig
import com.myapp.data.xstocks.MarketSource
import com.myapp.data.xstocks.MarketState
import com.myapp.data.xstocks.MarketStatus
import com.myapp.data.xstocks.Reserves
import com.myapp.repo.mintFacts
import com.myapp.ui.Copy
import com.myapp.repo.scaled
import com.myapp.repo.xStock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Every state of the Detail screen from plan section 13 Pass 2, asserted on the words and the
 * numerals the screen will draw rather than on pixels.
 *
 * The screen itself is a placement of these answers ([DetailScreen]), so a rule that matters is
 * testable here: the premium is never recomputed, Caution never leaves the two issuer-control
 * values, an absent extension and an unreadable mint never read the same, and a null SEC field is
 * never a zero.
 */
class DetailModelTest {

    private val mint = "TSLAxMint".padEnd(44, '1')
    private val now = Instant.parse("2026-09-12T20:55:00Z").toEpochMilli()

    private val asset = xStock("TSLAx", "TSLA", mint, name = "Tesla xStock")

    private val closed = MarketStatus(
        state = MarketState.CLOSED,
        source = MarketSource.VENUE,
        venueOpen = false,
        nextChangeAtMillis = null,
    )

    private val open = closed.copy(state = MarketState.REGULAR, venueOpen = true)

    private val reserves = Reserves(
        sharesHeld = 26_101.0,
        tokensInCirculation = 25_924.0,
        custodians = listOf("Alpaca"),
        asOf = null,
    )

    private fun payload(
        fscore: FScore? = FScore(
            score = 8,
            scale = "0-9",
            signals = listOf(true, true, true, false, true, true, true, true, true),
        ),
        axes: Axes = Axes(
            quality = Axis(value = 8.0, scale = "0-9", position = 0.8889, state = "strong", labelEn = "Strong"),
            valuation = Axis(value = 51.09, scale = "0-100", position = 0.5109, state = "fair", labelEn = "Moderate"),
            momentum = Axis(value = 0.7926, scale = "0-1", position = 0.7926, state = "high", labelEn = "Near 52-week high"),
        ),
        asOf: String? = "2026-09-12T20:00:00Z",
        statement: String? = "Rule-based classification of fundamentals against the sector.",
        composite: Double? = 71.0,
    ) = AnalysisPayload(
        ticker = "TSLA",
        company = "Tesla, Inc.",
        sector = "Consumer Discretionary",
        asOf = asOf,
        axes = axes,
        fscore = fscore,
        compositePercentile = composite,
        method = Method(statementEn = statement, schemaVersion = "v1.1"),
    )

    /** Everything landed: the shape the video opens on. */
    private fun served(
        quote: Piece<PriceEntry> = Piece.Ready(
            PriceEntry(usdPrice = 366.17, liquidity = 1_300_000.0, stockData = StockData(price = 365.84)),
        ),
        chain: Piece<ChainRead> = Piece.Ready(
            ChainRead(
                facts = mintFacts(
                    permanentDelegate = PermanentDelegate("5aMNNLQJwAEeoemTEMkv5NVjqKwvvefRYCQ5Z67HFvEq"),
                    pausable = PausableConfig(paused = false, authority = null),
                    scaledUiAmount = scaled(),
                    transferHook = TransferHookConfig(programId = null, authority = null),
                    defaultAccountState = DefaultAccountState("initialized"),
                ),
                slot = 446_503_662L,
                readAtMillis = now - 2_000L,
            ),
        ),
        reservesPiece: Piece<Reserves> = Piece.Ready(reserves),
        split: Piece<SplitMultiplier> = Piece.Ready(SplitMultiplier(1.0, MultiplierSource.MINT, null)),
        analysis: AnalysisState = AnalysisState.Served(payload()),
        market: MarketStatus? = closed,
    ) = DetailUiState(
        ticker = "TSLA",
        catalogAsset = Piece.Ready(asset),
        analysisState = analysis,
        quote = quote,
        chain = chain,
        reserves = reservesPiece,
        split = split,
        market = market,
        nowMillis = now,
    )

    private fun label(copy: Copy?): Int = (copy as Copy.Words).id

    private fun raw(copy: Copy?): String = (copy as Copy.Raw).text

    private fun args(copy: Copy?): List<String> = (copy as Copy.Words).args

    // ---- Header and hero ---------------------------------------------------------------------

    @Test
    fun `the hero is the token symbol and the registrant's own name`() {
        val state = served()
        assertEquals("TSLAx", state.heroTicker)
        assertEquals("Tesla, Inc.", state.heroCompany)
    }

    @Test
    fun `before the catalog answers the hero is the route's ticker and the company is not invented`() {
        val loading = DetailUiState(ticker = "TSLA", nowMillis = now)
        assertEquals("TSLA", loading.heroTicker)
        assertNull(loading.heroCompany)
        // The catalog answered first and PlainTicker has not: the xStock name carries the row.
        val catalogOnly = loading.copy(catalogAsset = Piece.Ready(asset), analysisState = AnalysisState.NotServed)
        assertEquals("Tesla xStock", catalogOnly.heroCompany)
    }

    // ---- The price row -----------------------------------------------------------------------

    @Test
    fun `the price row names the reference by market state`() {
        assertEquals(R.string.detail_nyse_close, label(served(market = closed).priceRow.referenceLabel))
        assertEquals(R.string.detail_nyse_price, label(served(market = open).priceRow.referenceLabel))
        // No venue answer at all reads as shut, the older and more careful of the two claims.
        assertEquals(R.string.detail_nyse_close, label(served(market = null).priceRow.referenceLabel))
    }

    @Test
    fun `the gauge caption is named by the same market state`() {
        assertEquals(R.string.detail_gauge_reference_close, label(served(market = closed).gaugeReference))
        assertEquals(R.string.detail_gauge_reference_live, label(served(market = open).gaugeReference))
    }

    @Test
    fun `both prices are formatted by Fmt`() {
        val row = served().priceRow
        assertEquals("\$366.17", row.tokenPrice)
        assertEquals("\$365.84", row.referencePrice)
        assertNull(row.tokenNote)
        assertNull(row.referenceNote)
    }

    @Test
    fun `reference price unavailable - the row says so and the gauge draws nothing`() {
        val state = served(quote = Piece.Ready(PriceEntry(usdPrice = 366.17, liquidity = 1_300_000.0)))
        val row = state.priceRow
        assertEquals("\$366.17", row.tokenPrice)
        assertNull(row.referencePrice)
        assertEquals(R.string.detail_reference_unavailable, label(row.referenceNote))
        // Tracked, so the pool is fine; there is simply no NYSE close to place a tick against.
        assertTrue(state.gauge is TrackingQuality.Tracked)
        assertNull(state.gauge?.premiumPct)
    }

    @Test
    fun `prices unavailable - a refused quote and an unpriced token are different sentences`() {
        val refused = served(quote = Piece.Failed).priceRow
        assertNull(refused.tokenPrice)
        assertEquals(R.string.list_prices_unavailable, label(refused.tokenNote))

        val unpriced = served(quote = Piece.Absent).priceRow
        assertNull(unpriced.tokenPrice)
        assertEquals(R.string.detail_price_absent, label(unpriced.tokenNote))

        // Still in flight is neither: the row draws a skeleton, so it states nothing at all.
        assertNull(served(quote = Piece.Loading).priceRow.tokenNote)
    }

    // ---- The gauge ---------------------------------------------------------------------------

    @Test
    fun `the gauge is handed the one tracking answer and never a premium of its own`() {
        val tracked = served().gauge
        assertTrue(tracked is TrackingQuality.Tracked)
        assertEquals(0.0902, tracked!!.premiumPct!!, 1e-4)

        // Below the floor: APPx as it read live on 2026-09-12, +89.34 percent off a pool of $34.
        val thin = served(
            quote = Piece.Ready(PriceEntry(usdPrice = 1_158.76, liquidity = 34.0, stockData = StockData(price = 612.0))),
        ).gauge
        assertEquals(TrackingQuality.Thin(34.0), thin)
        assertNull(thin?.premiumPct)

        // Priced with no depth reported: unknown, which is not the same as deep.
        assertEquals(
            TrackingQuality.Untracked,
            served(quote = Piece.Ready(PriceEntry(usdPrice = 366.17, stockData = StockData(price = 365.84)))).gauge,
        )

        // No quote at all: no tracking question, and the price row already says why.
        assertNull(served(quote = Piece.Failed).gauge)
    }

    // ---- The live bar ------------------------------------------------------------------------

    @Test
    fun `the live bar states the slot and the age of the read, and breathes only while it is live`() {
        val line = served().liveLine!!
        assertEquals(R.string.detail_live_label, label(line.label))
        assertEquals(R.string.detail_live_meta, label(line.meta))
        assertEquals(listOf("446,503,662", "2 s ago"), args(line.meta))
        assertTrue(line.live)
        // Announced by the slot, not by the ticking age, so a reader is not interrupted per second.
        assertEquals(listOf("446,503,662"), args(line.announcement))
    }

    @Test
    fun `a read older than the forwarder's cache window is stale, so the bar stops breathing`() {
        val readAt = now - LIVE_WINDOW_MILLIS - 1
        val stale = served(
            chain = Piece.Ready(ChainRead(mintFacts(), slot = 446_503_662L, readAtMillis = readAt)),
        ).liveLine!!
        assertFalse(stale.live)
        assertEquals(listOf("446,503,662", "1 min ago"), args(stale.meta))
    }

    @Test
    fun `chain unreadable - the live bar keeps its slot and says nothing below it is from the chain`() {
        val line = served(chain = Piece.Failed).liveLine!!
        assertEquals(R.string.detail_live_unread_label, label(line.label))
        assertEquals(R.string.detail_live_unread_meta, label(line.meta))
        assertFalse(line.live)
    }

    @Test
    fun `while the mint read is in flight there is no live bar to draw, only a skeleton`() {
        assertNull(served(chain = Piece.Loading).liveLine)
        assertTrue(served(chain = Piece.Loading).trustLoading)
    }

    // ---- Backing and controls ------------------------------------------------------------------

    @Test
    fun `the grid is five facts in the fixed order, the reserves spanning the first row`() {
        val cells = served().trustFacts
        assertEquals(
            listOf(
                R.string.detail_fact_por,
                R.string.detail_fact_delegate,
                R.string.detail_fact_pausable,
                R.string.detail_fact_split,
                R.string.detail_fact_hook,
            ),
            cells.map { label(it.label) },
        )
        assertEquals(2, cells.first().span)
        assertTrue("the reserves sub line carries numbers", cells.first().subMono)
        assertTrue("the four controls each take half a row", cells.drop(1).all { it.span == 1 })
    }

    @Test
    fun `caution is on the value of the two controls the issuer actually holds, and nowhere else`() {
        val cells = served().trustFacts.associateBy { label(it.label) }
        assertTrue(cells.getValue(R.string.detail_fact_delegate).caution)
        assertTrue(cells.getValue(R.string.detail_fact_pausable).caution)
        assertFalse(cells.getValue(R.string.detail_fact_por).caution)
        assertFalse(cells.getValue(R.string.detail_fact_split).caution)
        assertFalse(cells.getValue(R.string.detail_fact_hook).caution)
    }

    @Test
    fun `an absent extension reads None and takes no caution`() {
        val bare = served(
            chain = Piece.Ready(ChainRead(mintFacts(), slot = 1L, readAtMillis = now)),
        ).trustFacts.associateBy { label(it.label) }

        val delegate = bare.getValue(R.string.detail_fact_delegate)
        assertEquals(R.string.value_none, label(delegate.value))
        assertEquals(R.string.detail_fact_delegate_none_sub, label(delegate.sub))
        assertFalse(delegate.caution)

        val pausable = bare.getValue(R.string.detail_fact_pausable)
        assertEquals(R.string.value_none, label(pausable.value))
        assertEquals(R.string.detail_fact_pausable_none_sub, label(pausable.sub))
        assertFalse(pausable.caution)

        val hook = bare.getValue(R.string.detail_fact_hook)
        assertEquals(R.string.value_none, label(hook.value))
        assertEquals(R.string.detail_fact_hook_sub, label(hook.sub))
    }

    @Test
    fun `a present extension that is quiet is a Yes with the reason, not a None`() {
        val cells = served().trustFacts.associateBy { label(it.label) }
        val delegate = cells.getValue(R.string.detail_fact_delegate)
        assertEquals(R.string.value_yes, label(delegate.value))
        assertEquals(R.string.detail_fact_delegate_sub, label(delegate.sub))

        val pausable = cells.getValue(R.string.detail_fact_pausable)
        assertEquals(R.string.value_yes, label(pausable.value))
        assertEquals(R.string.detail_fact_pausable_sub, label(pausable.sub))

        // The hook extension is on the mint with an empty slot: no program runs, so the fact the
        // reader needs is the same as an absent extension.
        assertEquals(R.string.value_none, label(cells.getValue(R.string.detail_fact_hook).value))
    }

    @Test
    fun `a permanent delegate that has been revoked is not a risk the issuer holds`() {
        val revoked = served(
            chain = Piece.Ready(
                ChainRead(mintFacts(permanentDelegate = PermanentDelegate(delegate = null)), 1L, now),
            ),
        ).trustFacts.first { label(it.label) == R.string.detail_fact_delegate }
        assertEquals(R.string.value_none, label(revoked.value))
        assertFalse(revoked.caution)
    }

    @Test
    fun `paused transfers say they are paused, and still carry caution`() {
        val paused = served(
            chain = Piece.Ready(
                ChainRead(mintFacts(pausable = PausableConfig(paused = true, authority = null)), 1L, now),
            ),
        ).trustFacts.first { label(it.label) == R.string.detail_fact_pausable }
        assertEquals(R.string.value_yes, label(paused.value))
        assertEquals(R.string.detail_fact_pausable_paused_sub, label(paused.sub))
        assertTrue(paused.caution)
    }

    @Test
    fun `a transfer hook that runs names the program instead of claiming there is none`() {
        val hook = served(
            chain = Piece.Ready(
                ChainRead(
                    mintFacts(transferHook = TransferHookConfig(programId = "JSDL1i4mcAqGYzkWrxCB6sKxHu6HYJmAPMyeCEtjjpVi", authority = null)),
                    1L,
                    now,
                ),
            ),
        ).trustFacts.first { label(it.label) == R.string.detail_fact_hook }
        assertEquals("JSDL…jpVi", raw(hook.value))
        assertEquals(R.string.detail_fact_hook_runs_sub, label(hook.sub))
        assertFalse("a hook is a fact, not an issuer control DESIGN.md colours", hook.caution)
    }

    @Test
    fun `chain unreadable - the three chain cells read Unknown with the reason, never None`() {
        val cells = served(chain = Piece.Failed).trustFacts.associateBy { label(it.label) }
        listOf(R.string.detail_fact_delegate, R.string.detail_fact_pausable, R.string.detail_fact_hook).forEach { id ->
            val cell = cells.getValue(id)
            assertEquals("cell $id", R.string.detail_value_unknown, label(cell.value))
            assertEquals("cell $id", R.string.detail_chain_unread_sub, label(cell.sub))
            assertFalse("an unread mint is never a warning", cell.caution)
        }
        // The reserves come from xStocks, so a dead forwarder leaves them alone.
        assertEquals("100.7%", raw(cells.getValue(R.string.detail_fact_por).value))
    }

    // ---- Proof of reserves ---------------------------------------------------------------------

    @Test
    fun `the reserves state the ratio and the shares behind the tokens, naming the custodian`() {
        val cell = served().trustFacts.first()
        assertEquals("100.7%", raw(cell.value))
        assertEquals(R.string.detail_fact_por_custodian_sub, label(cell.sub))
        assertEquals(listOf("26,101", "Alpaca", "25,924"), args(cell.sub))
    }

    @Test
    fun `several custodians or none leave the sub line unnamed rather than half true`() {
        val many = served(reservesPiece = Piece.Ready(reserves.copy(custodians = listOf("Alpaca", "InCore"))))
        val cell = many.trustFacts.first()
        assertEquals(R.string.detail_fact_por_sub, label(cell.sub))
        assertEquals(listOf("26,101", "25,924"), args(cell.sub))
    }

    @Test
    fun `proof of reserves unavailable - an absence and a failure are different sentences`() {
        val absent = served(reservesPiece = Piece.Absent).trustFacts.first()
        assertEquals(R.string.detail_value_unknown, label(absent.value))
        assertEquals(R.string.detail_fact_por_absent_sub, label(absent.sub))

        val failed = served(reservesPiece = Piece.Failed).trustFacts.first()
        assertEquals(R.string.detail_value_unknown, label(failed.value))
        assertEquals(R.string.detail_fact_por_failed_sub, label(failed.sub))
        // Neither is ever a coverage of zero.
        assertFalse(absent.value is Copy.Raw)
        assertFalse(failed.value is Copy.Raw)
    }

    // ---- The split multiplier --------------------------------------------------------------------

    @Test
    fun `the multiplier from the mint says only that nothing is pending`() {
        val cell = served().trustFacts.first { label(it.label) == R.string.detail_fact_split }
        assertEquals("1.00", raw(cell.value))
        assertEquals(R.string.detail_fact_split_sub, label(cell.sub))
    }

    @Test
    fun `a pending split says what it changes to and when`() {
        val activation = Instant.parse("2026-09-21T00:00:00Z").epochSecond
        val pending = served(
            split = Piece.Ready(
                SplitMultiplier(
                    current = 1.0,
                    source = MultiplierSource.MINT,
                    pending = com.myapp.data.PendingMultiplier(4.0, activation),
                ),
            ),
        ).trustFacts.first { label(it.label) == R.string.detail_fact_split }
        assertEquals(R.string.detail_fact_split_changes_sub, label(pending.sub))
        assertEquals(listOf("4.00", "21 Sep 2026"), args(pending.sub))
    }

    @Test
    fun `a split already activated is no longer pending`() {
        val past = served(
            split = Piece.Ready(
                SplitMultiplier(
                    current = 4.0,
                    source = MultiplierSource.MINT,
                    pending = com.myapp.data.PendingMultiplier(4.0, Instant.parse("2026-09-01T00:00:00Z").epochSecond),
                ),
            ),
        ).trustFacts.first { label(it.label) == R.string.detail_fact_split }
        assertEquals("4.00", raw(past.value))
        assertEquals(R.string.detail_fact_split_sub, label(past.sub))
    }

    @Test
    fun `an xStocks multiplier names its source, so it is never drawn as an on-chain fact`() {
        val fallback = served(
            chain = Piece.Failed,
            split = Piece.Ready(SplitMultiplier(1.0, MultiplierSource.XSTOCKS, null)),
        ).trustFacts.first { label(it.label) == R.string.detail_fact_split }
        assertEquals(R.string.detail_fact_split_from_xstocks, label(fallback.sub))

        val scheduled = served(
            chain = Piece.Failed,
            split = Piece.Ready(
                SplitMultiplier(
                    current = 1.0,
                    source = MultiplierSource.XSTOCKS,
                    pending = com.myapp.data.PendingMultiplier(4.0, Instant.parse("2026-09-21T00:00:00Z").epochSecond),
                ),
            ),
        ).trustFacts.first { label(it.label) == R.string.detail_fact_split }
        assertEquals(R.string.detail_fact_split_changes_xstocks_sub, label(scheduled.sub))
    }

    @Test
    fun `no source answered for the multiplier - the cell says that, never one`() {
        val cell = served(chain = Piece.Failed, split = Piece.Failed)
            .trustFacts.first { label(it.label) == R.string.detail_fact_split }
        assertEquals(R.string.detail_value_unknown, label(cell.value))
        assertEquals(R.string.detail_fact_split_failed_sub, label(cell.sub))
    }

    // ---- Against the sector ----------------------------------------------------------------------

    @Test
    fun `three tracks in order, with the server's own state words lowercased`() {
        val state = served()
        assertEquals(R.string.detail_composite, label(state.compositeMeta))
        assertEquals(listOf("71"), args(state.compositeMeta))
        assertEquals(
            listOf(R.string.detail_track_quality, R.string.detail_track_valuation, R.string.detail_track_momentum),
            state.tracks.map { label(it.label) },
        )
        assertEquals(listOf("8/9", "51", "0.79"), state.tracks.map { it.value })
        assertEquals(listOf("strong", "moderate", "near 52-week high"), state.tracks.map { it.state })
        assertEquals(88.89f, state.tracks[0].positionPct, 0.01f)
    }

    @Test
    fun `an axis with no value is not available for this filer, and draws no marker`() {
        val foreign = served(analysis = AnalysisState.Served(payload(axes = Axes(quality = Axis(scale = "0-9")))))
        val rows = foreign.tracks
        assertEquals(3, rows.size)
        assertTrue("no value means no Track at all", rows.all { it.value == null })
        assertEquals(0f, rows[0].positionPct, 0f)
    }

    @Test
    fun `a payload with no composite leaves the heading meta off rather than printing a zero`() {
        assertNull(served(analysis = AnalysisState.Served(payload(composite = null))).compositeMeta)
    }

    // ---- F-Score ----------------------------------------------------------------------------------

    @Test
    fun `the nine signals keep the fixed order of the data map`() {
        val fscore = served().fScore!!
        assertEquals("8", fscore.score)
        assertEquals("9", fscore.outOf)
        assertEquals(
            listOf(
                R.string.signal_roa_positive,
                R.string.signal_cfo_positive,
                R.string.signal_roa_improving,
                R.string.signal_accruals,
                R.string.signal_leverage_falling,
                R.string.signal_liquidity_improving,
                R.string.signal_no_new_shares,
                R.string.signal_gross_margin_improving,
                R.string.signal_asset_turnover_improving,
            ),
            fscore.signals.map { it.name },
        )
        assertEquals(
            listOf(true, true, true, false, true, true, true, true, true),
            fscore.signals.map { it.ok },
        )
        assertFalse(fscore.unavailable)
    }

    @Test
    fun `a signal the filings cannot answer is null, which the row reads as n slash a and never as no`() {
        val withNull = served(
            analysis = AnalysisState.Served(
                payload(
                    fscore = FScore(
                        score = 7,
                        scale = "0-9",
                        signals = listOf(true, true, true, null, true, true, true, true, false),
                    ),
                ),
            ),
        ).fScore!!
        assertNull(withNull.signals[3].ok)
        assertEquals(false, withNull.signals[8].ok)
        assertFalse(withNull.unavailable)
    }

    @Test
    fun `a numeral the payload withholds is not the SEC-null case while a signal was evaluated`() {
        val partial = served(
            analysis = AnalysisState.Served(
                payload(
                    fscore = FScore(
                        score = null,
                        scale = "0-9",
                        signals = listOf(true, null, null, null, null, null, null, null, null),
                    ),
                ),
            ),
        ).fScore!!
        assertNull(partial.score)
        assertFalse("one signal was answered, so the nine rows still stand", partial.unavailable)
    }

    @Test
    fun `a filer with no F-Score keeps the heading and says it is not available, never a zero`() {
        val none = served(analysis = AnalysisState.Served(payload(fscore = null))).fScore!!
        assertNull(none.score)
        assertEquals(9, none.signals.size)
        assertTrue(none.signals.all { it.ok == null })
        assertTrue(none.unavailable)
    }

    // ---- Method -------------------------------------------------------------------------------------

    @Test
    fun `method is the payload's own statement, then the static sources line`() {
        val method = served().method!!
        assertEquals("Rule-based classification of fundamentals against the sector.", raw(method.statement))
        assertEquals(R.string.detail_method_sources, label(method.sources))
    }

    @Test
    fun `a payload with no statement still carries the bundled disclaimer`() {
        val method = served(analysis = AnalysisState.Served(payload(statement = null))).method!!
        assertEquals(R.string.detail_method_body, label(method.statement))
    }

    @Test
    fun `the analysis age is stated only past a day`() {
        assertNull("under a day, the age is noise", served().method!!.age)

        val old = served(
            analysis = AnalysisState.Served(payload(asOf = "2026-09-10T13:25:30Z")),
        ).method!!
        assertEquals(R.string.detail_analysis_age, label(old.age))
        assertEquals(listOf("2 d ago"), args(old.age))
    }

    // ---- The states the screen has to survive ---------------------------------------------------------

    @Test
    fun `not served - the trust layer stands and one line replaces the fundamentals`() {
        val state = served(analysis = AnalysisState.NotServed)
        assertEquals(5, state.trustFacts.size)
        assertEquals("100.7%", raw(state.trustFacts.first().value))
        assertTrue(state.tracks.isEmpty())
        assertNull(state.fScore)
        assertNull(state.method)
        val notice = state.fundamentalsNotice!!
        assertEquals(R.string.detail_analysis_pending, label(notice.text))
        assertEquals(R.string.detail_analysis_pending_action, label(notice.hint))
    }

    @Test
    fun `an incomplete filer and a transport failure each get their own line and no hint`() {
        val incomplete = served(analysis = AnalysisState.Incomplete).fundamentalsNotice!!
        assertEquals(R.string.detail_analysis_incomplete, label(incomplete.text))
        assertNull(incomplete.hint)

        val down = served(analysis = AnalysisState.Unavailable).fundamentalsNotice!!
        assertEquals(R.string.detail_analysis_unavailable, label(down.text))
        assertNull(down.hint)
    }

    @Test
    fun `loading - nothing is claimed and nothing is denied`() {
        val loading = DetailUiState(ticker = "TSLA", nowMillis = now)
        assertNull(loading.fundamentalsNotice)
        assertNull(loading.analysis)
        assertNull(loading.liveLine)
        assertTrue(loading.trustLoading)
        assertNull(loading.tokenNotice)
        assertNull(loading.swapLabel)
        assertNull(loading.priceRow.tokenPrice)
        assertNull(loading.priceRow.tokenNote)
    }

    @Test
    fun `a ticker with no xStock, and a catalog that did not answer, are different lines`() {
        val none = DetailUiState(ticker = "BKNG", catalogAsset = Piece.Absent, nowMillis = now)
        assertEquals(R.string.detail_no_xstock, label(none.tokenNotice))
        assertFalse(none.hasToken)

        val down = DetailUiState(ticker = "TSLA", catalogAsset = Piece.Failed, nowMillis = now)
        assertEquals(R.string.list_catalog_unavailable, label(down.tokenNotice))

        assertNull("a known mint has no notice to draw", served().tokenNotice)
    }

    // ---- The banner slot -------------------------------------------------------------------------------

    @Test
    fun `the one banner slot carries the venue's hours`() {
        assertNull("nothing to say while the exchange is trading", served(market = open).banner)
        assertEquals(DetailBanner.CLOSED, served(market = closed).banner)
        assertEquals(
            DetailBanner.CLOSED_LOCAL,
            served(market = closed.copy(source = MarketSource.LOCAL_SCHEDULE)).banner,
        )
        assertEquals(DetailBanner.HALTED, served(market = closed.copy(state = MarketState.HALTED)).banner)
        assertNull("no venue answer yet is not a banner", served(market = null).banner)
        // The weekday schedule knows no holidays, so an open only it claims is named as its claim
        // rather than passed off as the venue's own answer.
        assertEquals(
            DetailBanner.OPEN_LOCAL,
            served(market = open.copy(source = MarketSource.LOCAL_SCHEDULE)).banner,
        )
    }

    @Test
    fun `extended and overnight are not the exchange session, so the reference is still the close`() {
        listOf(MarketState.EXTENDED, MarketState.OVERNIGHT).forEach { period ->
            val state = served(market = closed.copy(state = period, venueOpen = true))
            assertEquals(period.name, DetailBanner.CLOSED, state.banner)
            assertEquals(period.name, R.string.detail_nyse_close, label(state.priceRow.referenceLabel))
        }
    }

    // ---- The swap footer -------------------------------------------------------------------------------

    @Test
    fun `the button names the pair and the line states the pool until a quote exists`() {
        val state = served()
        assertEquals(R.string.detail_swap_button, label(state.swapLabel))
        assertEquals(listOf("USDC", "TSLAx"), args(state.swapLabel))

        assertEquals(R.string.detail_liquidity_line, label(state.costLine(null)))
        assertEquals(listOf("\$1.3M"), args(state.costLine(null)))

        val quoted = state.costLine(0.09)
        assertEquals(R.string.detail_cost_line, label(quoted))
        assertEquals(listOf("0.09%", "\$1.3M"), args(quoted))
    }

    @Test
    fun `no pool means no cost line rather than an empty one`() {
        assertNull(served(quote = Piece.Failed).costLine(null))
        assertNull(served(quote = Piece.Ready(PriceEntry(usdPrice = 366.17))).costLine(0.09))
    }

    @Test
    fun `the axis value takes the shape its own scale asks for`() {
        assertEquals("8/9", axisValue(8.0, "0-9"))
        assertEquals("51", axisValue(51.09, "0-100"))
        assertEquals("0.79", axisValue(0.7926, "0-1"))
        assertEquals("0.79", axisValue(0.7926, null))
    }

    @Test
    fun `the watch action reads from the shared store rather than from this screen`() {
        assertFalse(served().watched)
        assertTrue(served().copy(watched = true).watched)
        assertNotNull(served().swapLabel)
    }
}
