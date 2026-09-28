package com.plainticker.mobile.ui.portfolio

import com.plainticker.mobile.R
import com.plainticker.mobile.data.receipts.SwapReceipt
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.ShippedCopy
import com.plainticker.mobile.wallet.testAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every state of the Portfolio from plan section 13 Pass 2, asserted on the words and the numerals
 * the screen will draw (design task DT8). What the screen does with them is placement; what is
 * held here is the rule that picked them.
 */
class PortfolioModelTest {

    private val mint = "TSLAxMint".padEnd(44, '1')

    private fun position(
        symbol: String = "TSLAx",
        ticker: String = "TSLA",
        company: String = "Tesla xStock",
        amountRaw: Long = 201_364_000L,
        decimals: Int? = 8,
        multiplier: Double? = 1.0,
        priceUsd: Double? = 366.17,
        referencePriceUsd: Double? = 365.84,
        poolUsd: Double? = 250_000.0,
    ) = PortfolioPosition(
        symbol = symbol,
        ticker = ticker,
        company = company,
        mint = mint,
        amountRaw = amountRaw,
        decimals = decimals,
        multiplier = multiplier,
        priceUsd = priceUsd,
        referencePriceUsd = referencePriceUsd,
        poolUsd = poolUsd,
    )

    private fun state(vararg positions: PortfolioPosition) = PortfolioUiState(
        phase = WalletPhase.CONNECTED,
        account = testAccount(),
        settled = true,
        positions = positions.toList(),
        totalUsd = positions.mapNotNull { it.valueUsd }.takeIf { it.isNotEmpty() }?.sum(),
    )

    private fun words(copy: Copy?): Copy.Words = copy as Copy.Words

    // ---- The holding row -------------------------------------------------------------------

    @Test
    fun `a holding reads as shares held and the premium against its share's US price`() {
        val row = holdingRow(position())

        assertEquals("TSLAx", row.symbol)
        assertEquals("TSLA", row.ticker)
        assertEquals("Tesla xStock", row.company)
        assertEquals(Copy.Words(R.string.portfolio_row_quantity, listOf("2.01364", "TSLAx")), row.quantity)
        assertEquals(Copy.Words(R.string.list_row_meta_premium, listOf("+0.09%")), row.tracking)
        assertEquals("\$737.33", row.value)
    }

    @Test
    fun `the multiplier is applied before anything is drawn`() {
        // 50_000_000 raw at 8 decimals is 0.5 tokens; a 10x mint makes it 5 shares.
        val row = holdingRow(position(amountRaw = 50_000_000L, multiplier = 10.0, priceUsd = 120.0))
        assertEquals(Copy.Words(R.string.portfolio_row_quantity, listOf("5", "TSLAx")), row.quantity)
        assertEquals("\$600.00", row.value)
    }

    @Test
    fun `a mint that could not be read says so, and says nothing else`() {
        val row = holdingRow(position(decimals = null, multiplier = null))

        assertEquals(Copy.Words(R.string.portfolio_row_mint_unread), row.quantity)
        // No premium beside a quantity this app does not have: the two would invite a product.
        assertNull(row.tracking)
        assertNull(row.value)
    }

    @Test
    fun `a pool under the floor states its size where the premium would be`() {
        // APPx as it read live on 2026-09-12: +89.34 percent quoted off a pool holding $34.
        val row = holdingRow(position(priceUsd = 1_158.76, referencePriceUsd = 612.00, poolUsd = 34.0))

        assertEquals(Copy.Words(R.string.list_row_meta_thin, listOf("\$34")), row.tracking)
        // The holding is still worth what the quote says; only the tracking figure is withheld.
        assertEquals("\$2,333.33", row.value)
    }

    @Test
    fun `a pool Jupiter did not report is unknown, never deep`() {
        val row = holdingRow(position(poolUsd = null))
        assertEquals(Copy.Words(R.string.list_row_meta_pool_unknown), row.tracking)
    }

    @Test
    fun `a position nobody can price keeps its quantity and has no value`() {
        val row = holdingRow(position(priceUsd = null, referencePriceUsd = null))

        assertEquals(Copy.Words(R.string.portfolio_row_quantity, listOf("2.01364", "TSLAx")), row.quantity)
        assertNull(row.tracking)
        assertNull(row.value)
    }

    @Test
    fun `a priced token with no NYSE reference draws no premium and still draws its value`() {
        val row = holdingRow(position(referencePriceUsd = null))
        assertNull(row.tracking)
        assertEquals("\$737.33", row.value)
    }

    // ---- The total -------------------------------------------------------------------------

    @Test
    fun `the total covers the priced positions and the sentence counts them`() {
        val block = totalBlock(state(position(), position(symbol = "NVDAx", ticker = "NVDA", priceUsd = 182.11)))

        assertEquals(Copy.Counted(R.plurals.portfolio_priced_by, 2, listOf("2")), block.sub)
        assertTrue(block.value!!.startsWith("$"))
    }

    @Test
    fun `a total that covers only some of them says how many`() {
        val block = totalBlock(
            state(
                position(),
                position(symbol = "MCDx", ticker = "MCD", priceUsd = null, referencePriceUsd = null),
                position(symbol = "AAPLx", ticker = "AAPL", decimals = null, multiplier = null),
            ),
        )

        // The count that picks the noun is how many are on screen, not how many were priced.
        assertEquals(Copy.Counted(R.plurals.portfolio_priced_partial, 3, listOf("1", "3")), block.sub)
        assertEquals("\$737.33", block.value)
    }

    @Test
    fun `nothing priced leaves no total at all rather than a zero`() {
        val block = totalBlock(state(position(priceUsd = null, referencePriceUsd = null)))

        assertNull(block.value)
        assertEquals(Copy.Counted(R.plurals.portfolio_priced_none, 1, listOf("1")), block.sub)
    }

    // ---- Recent swaps ----------------------------------------------------------------------

    private fun receipt(
        outputAmountRaw: Long? = 1_364_000L,
        routeCostPct: Double? = 0.09,
    ) = SwapReceipt(
        signature = "5xY1",
        inputMint = "usdc",
        inputSymbol = "USDC",
        inputAmountRaw = 5_000_000L,
        inputDecimals = 6,
        outputMint = mint,
        outputSymbol = "TSLAx",
        outputAmountRaw = outputAmountRaw,
        outputDecimals = 8,
        routeCostPct = routeCostPct,
        route = "Metis",
        landedAtMillis = 1_789_045_020_000L,
    )

    @Test
    fun `a swap row is what was paid, what came back, the cost paid and when`() {
        val row = swapRow(receipt(), java.time.ZoneOffset.UTC)

        assertEquals(Copy.Words(R.string.portfolio_row_quantity, listOf("5", "USDC")), row.paid)
        assertEquals(Copy.Words(R.string.portfolio_swap_row_received, listOf("0.01364", "TSLAx")), row.received)
        // A receipt with no SOL priced in has the route's cost, and says route (judges' review, 2026-09-27).
        assertEquals(Copy.Words(R.string.portfolio_swap_route_cost, listOf("0.09%")), row.cost)
        assertEquals(Copy.Raw("10 Sep 2026 12:57"), row.landed)
    }

    /** Device QA of 1.3.18: the recent swaps were the last rows still printing UTC. */
    @Test
    fun `a swap row and a recorded holding read in the reader's own time`() {
        val kyiv = java.time.ZoneId.of("Europe/Kyiv")
        assertEquals(Copy.Raw("10 Sep 2026 15:57"), swapRow(receipt(), kyiv).landed)
        val holding = recordedHoldings(listOf(receipt())).single()
        val meta = recordedRow(holding, kyiv).meta as Copy.Words
        assertEquals(listOf("10 Sep 2026 15:57"), meta.args)
        assertTrue("no UTC label on a local time", meta.args.none { "UTC" in it })
    }

    @Test
    fun `a receipt that priced its SOL states the all-in cost paid`() {
        // 0.09 percent route, plus 0.30 dollars of SOL (fees and a refundable deposit) over 5 in.
        val priced = receipt().copy(solCostUsd = 0.30, rentUsd = 0.2977, inputUsd = 5.0)
        assertEquals(0.09 + 6.0, priced.allInCostPct!!, 1e-9)
        assertEquals(Copy.Words(R.string.portfolio_swap_cost, listOf("6.09%")), swapRow(priced).cost)
    }

    @Test
    fun `a fill the execute answer never reported stays unreported`() {
        val row = swapRow(receipt(outputAmountRaw = null, routeCostPct = null))

        assertEquals(Copy.Words(R.string.portfolio_swap_row_received_unknown, listOf("TSLAx")), row.received)
        assertEquals(Copy.Words(R.string.portfolio_swap_cost_unknown), row.cost)
    }

    @Test
    fun `no row of this screen claims the chain confirmed anything`() {
        val row = swapRow(receipt())
        // The slot and the signature are on the receipt and are deliberately not on the row: this
        // screen makes no chain read for these rows, so it vouches for nothing beyond its own record.
        val spoken = listOf(row.paid, row.received, row.cost, row.landed)
        assertTrue(spoken.none { it is Copy.Words && it.id == R.string.receipt_confirmed_in })
        assertEquals("5xY1", row.signature)
    }

    // ---- The banner ------------------------------------------------------------------------

    @Test
    fun `the banner tiers are what could not be read at all, then what is partly missing`() {
        val chain = PortfolioUiState(chainUnavailable = true, pricesPartial = true, note = WalletNote.CANCELLED)
        assertEquals(PortfolioBanner.ChainUnavailable, chain.banner)
        assertEquals(Copy.Words(R.string.portfolio_error_unavailable), bannerText(chain.banner!!))
        assertTrue(bannerRetries(chain.banner!!))

        val catalog = PortfolioUiState(catalogUnavailable = true, pricesUnavailable = true)
        assertEquals(PortfolioBanner.CatalogUnavailable, catalog.banner)

        val partial = PortfolioUiState(pricesPartial = true)
        assertEquals(Copy.Words(R.string.list_prices_partial), bannerText(partial.banner!!))

        val gone = PortfolioUiState(pricesUnavailable = true)
        assertEquals(Copy.Words(R.string.list_prices_unavailable), bannerText(gone.banner!!))
    }

    @Test
    fun `a wallet note carries no retry, because the connect action is the answer to it`() {
        val state = PortfolioUiState(note = WalletNote.CANCELLED)
        val banner = state.banner!!

        assertEquals(PortfolioBanner.Wallet(WalletNote.CANCELLED), banner)
        assertEquals(Copy.Words(R.string.portfolio_wallet_cancelled), bannerText(banner))
        assertFalse(bannerRetries(banner))
    }

    @Test
    fun `a settled screen with nothing wrong has no banner`() {
        assertNull(state(position()).banner)
    }

    // ---- The states the screen switches on ---------------------------------------------------

    @Test
    fun `an empty wallet is empty only once a load has settled`() {
        val asking = PortfolioUiState(phase = WalletPhase.CONNECTED, account = testAccount(), isLoading = true)
        assertFalse("nothing has been read yet", asking.isEmpty)
        assertTrue(asking.isCold)

        val settled = asking.copy(isLoading = false, settled = true)
        assertTrue(settled.isEmpty)
        assertFalse(settled.isCold)
    }

    @Test
    fun `a chain that could not be read is never an empty wallet`() {
        val state = PortfolioUiState(
            phase = WalletPhase.CONNECTED,
            account = testAccount(),
            settled = true,
            chainUnavailable = true,
        )
        assertFalse(state.isEmpty)
    }

    @Test
    fun `a refresh over drawn rows shows no skeletons`() {
        val state = state(position()).copy(isLoading = true)
        assertFalse(state.isCold)
    }

    // ---- QA 2026-09-26, D2: the recorded row's unscaled meta line, measured not guessed --------

    /**
     * [R.string.portfolio_recorded_meta_unscaled] used to read "Swapped %1$s · as recorded, current
     * split not read," which clipped mid-word ("…as recorded, c…") at font scale 1.3 in
     * [com.plainticker.mobile.ui.components.AmberTickerRow]'s own `context` slot
     * (`maxLines = 2`) beside this row's `figure`. The section's own lede already says once, above
     * every row, that these are the app's own record rather than a chain read, so "as recorded" was
     * repeating that; the shortened string keeps only the one new fact this clause adds.
     *
     * fontTools 4.63 against `res/font/bricolage_grotesque.ttf`, 2026-09-26, `context`'s 14sp/400
     * instance (advance widths, no kerning, the same conservative direction `AmberTickerRowTest`
     * measures in) for the full rendered sentence with `Fmt.utc`'s own worst case (21 characters,
     * every stamp is this long); `figureRow`'s 18sp/600 tnum instance for
     * [com.plainticker.mobile.ui.components.AmberTickerRowTest]'s own "the widest realistic figure
     * this row draws across every screen," "38,406.2," which this row's own `figure` slot shares
     * the meta line's 336dp content width with the same way every other caller of that row does.
     */
    /*
     * Re-measured 2026-09-26 (audit, item 4): "quantity not rescaled" was jargon, now "count at swap
     * time". fontTools 4.63, the same method as AmberTickerRowTest (hmtx advances, context 14/400
     * opsz 14, figureRow 18/600 opsz 18 with tnum): the new sentence is 345.814dp; the figure
     * "38,406.2" with tnum is 73.296dp. The older constants here (340.830dp, 65.970dp) did not
     * reproduce under that method, so both are replaced with the measured ones, and the new line
     * still clears two lines at 1.3x with 15.87dp to spare.
     */
    @Test
    fun `the unscaled meta line clears AmberTickerRow's two-line budget beside the widest realistic figure, at 1_3x`() {
        // The template itself, unfilled: this is what a future edit could quietly lengthen back
        // toward the old defect, so it is pinned as its own literal rather than only through one
        // rendered example.
        assertEquals(
            "Swapped %1\$s · count at swap time",
            ShippedCopy.strings.getValue("portfolio_recorded_meta_unscaled"),
        )

        // Fmt.utc's own shape is fixed-length whenever the day is two digits (dayOfMonth is never
        // zero-padded, so a single-digit day is one character shorter than this, never longer):
        // "D Sep YYYY HH:MM UTC", 21 characters. This exact stamp, "10 Sep 2026 14:55 UTC," is the
        // one fontTools measured below; any other two-digit-day stamp sums to the same order of
        // width, since every character in it is a digit, a space or one of a fixed set of letters.
        val rendered = ShippedCopy.string("portfolio_recorded_meta_unscaled", "10 Sep 2026 14:55 UTC")
        assertEquals("Swapped 10 Sep 2026 14:55 UTC · count at swap time", rendered)
        assertEquals(21, "10 Sep 2026 14:55 UTC".length)

        val contentWidthDp = 336.0
        val gapDp = 8.0
        val figureWidthDp = 73.296 // "38,406.2" at figureRow's 18sp/600 tnum, 1.0x.
        val contextWidthDp = 345.814 // the rendered sentence above, at context's 14sp/400, 1.0x.

        val budgetDp = contentWidthDp - figureWidthDp - gapDp
        assertEquals(254.704, budgetDp, 0.01)
        // Two lines, not one: this sentence carries a full timestamp and does not need to clear a
        // single line, only the row's real two-line ceiling, the same backstop
        // `AmberTickerRowTest`'s own watchlist test already accepts for a join this long.
        assertTrue(
            "the unscaled meta must clear one full line at 1.0x, or nothing below needs measuring",
            contextWidthDp <= budgetDp * 2,
        )

        val figureWidthAt13xDp = figureWidthDp * 1.3
        val contextWidthAt13xDp = contextWidthDp * 1.3
        val budgetAt13xDp = contentWidthDp - figureWidthAt13xDp - gapDp
        assertEquals(232.715, budgetAt13xDp, 0.01)
        assertEquals(449.558, contextWidthAt13xDp, 0.01)
        assertTrue(
            "the unscaled meta (\"$rendered\", $contextWidthAt13xDp dp at 1.3x) must clear the " +
                "row's own two-line ceiling ($budgetAt13xDp dp per line) beside the widest " +
                "realistic figure, or it clips again",
            contextWidthAt13xDp <= budgetAt13xDp * 2,
        )
    }

    @Test
    fun `words are the only sentences, and every one of them is a resource`() {
        val copies = listOf(
            holdingRow(position()).quantity,
            holdingRow(position(decimals = null, multiplier = null)).quantity,
            swapRow(receipt()).paid,
            swapRow(receipt()).received,
            swapRow(receipt()).cost,
        )
        copies.forEach { assertTrue("$it is not a resource", it is Copy.Words) }
        copies.forEach { assertTrue("resource id is missing", words(it).id != 0) }
        // The one sentence on this screen that says a number out loud is counted copy instead,
        // so it still comes out of the file and still carries the count that chose its form.
        val sub = totalBlock(state(position())).sub
        assertTrue("$sub is not counted copy", sub is Copy.Counted)
        assertTrue("resource id is missing", (sub as Copy.Counted).id != 0)
        assertEquals(1, sub.quantity)
    }
}
