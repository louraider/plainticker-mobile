package com.myapp.ui.portfolio

import com.myapp.R
import com.myapp.data.receipts.SwapReceipt
import com.myapp.ui.Copy
import com.myapp.wallet.testAccount
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
    fun `a holding reads as shares held and the premium against the NYSE close`() {
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

        assertEquals(Copy.Words(R.string.portfolio_priced_by, listOf("2")), block.sub)
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

        assertEquals(Copy.Words(R.string.portfolio_priced_partial, listOf("1", "3")), block.sub)
        assertEquals("\$737.33", block.value)
    }

    @Test
    fun `nothing priced leaves no total at all rather than a zero`() {
        val block = totalBlock(state(position(priceUsd = null, referencePriceUsd = null)))

        assertNull(block.value)
        assertEquals(Copy.Words(R.string.portfolio_priced_none, listOf("1")), block.sub)
    }

    // ---- Recent swaps ----------------------------------------------------------------------

    private fun receipt(
        outputAmountRaw: Long? = 1_364_000L,
        allInCostPct: Double? = 0.09,
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
        allInCostPct = allInCostPct,
        route = "Metis",
        landedAtMillis = 1_789_045_020_000L,
    )

    @Test
    fun `a swap row is what was paid, what came back, the cost paid and when`() {
        val row = swapRow(receipt())

        assertEquals(Copy.Words(R.string.portfolio_row_quantity, listOf("5", "USDC")), row.paid)
        assertEquals(Copy.Words(R.string.portfolio_swap_row_received, listOf("0.01364", "TSLAx")), row.received)
        assertEquals(Copy.Words(R.string.portfolio_swap_cost, listOf("0.09%")), row.cost)
        assertEquals(Copy.Raw("10 Sep 2026 12:57 UTC"), row.landed)
    }

    @Test
    fun `a fill the execute answer never reported stays unreported`() {
        val row = swapRow(receipt(outputAmountRaw = null, allInCostPct = null))

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

    @Test
    fun `words are the only sentences, and every one of them is a resource`() {
        val copies = listOf(
            holdingRow(position()).quantity,
            holdingRow(position(decimals = null, multiplier = null)).quantity,
            totalBlock(state(position())).sub,
            swapRow(receipt()).paid,
            swapRow(receipt()).received,
            swapRow(receipt()).cost,
        )
        copies.forEach { assertTrue("$it is not a resource", it is Copy.Words) }
        copies.forEach { assertTrue("resource id is missing", words(it).id != 0) }
    }
}
