package com.plainticker.mobile.ui.share

import com.plainticker.mobile.data.KnownMints
import com.plainticker.mobile.data.jupiter.PriceEntry
import com.plainticker.mobile.data.jupiter.StockData
import com.plainticker.mobile.data.plainticker.AnalysisPayload
import com.plainticker.mobile.data.plainticker.Axes
import com.plainticker.mobile.data.plainticker.Axis
import com.plainticker.mobile.data.plainticker.Verdict
import com.plainticker.mobile.data.rpc.PermanentDelegate
import com.plainticker.mobile.data.xstocks.Reserves
import com.plainticker.mobile.repo.mintFacts
import com.plainticker.mobile.repo.xStock
import com.plainticker.mobile.ui.ShippedCopy
import com.plainticker.mobile.ui.detail.AnalysisState
import com.plainticker.mobile.ui.detail.ChainRead
import com.plainticker.mobile.ui.detail.DetailUiState
import com.plainticker.mobile.ui.detail.Piece
import com.plainticker.mobile.ui.detail.shareCard
import com.plainticker.mobile.ui.swap.SwapFill
import com.plainticker.mobile.ui.swap.SwapLeg
import com.plainticker.mobile.ui.swap.SwapQuote
import com.plainticker.mobile.ui.swap.SwapState
import com.plainticker.mobile.ui.swap.SwapTiming
import com.plainticker.mobile.ui.swap.SwapToken
import com.plainticker.mobile.ui.vote.VoteState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The share images' content (founder feedback 2026-09-29): which facts each card carries, for a
 * Pro reader and a free one, and what no card may ever carry. The picture itself is drawn by
 * [ShareCardRenderer] on a device; everything it says is decided here and asserted off the
 * shipped strings.xml.
 */
class ShareCardTest {

    private val asset = xStock("AAPLx", "AAPL", "mint-AAPL")
    private val delegate = PermanentDelegate("5aMNNLQJwAEeoemTEMkv5NVjqKwvvefRYCQ5Z67HFvEq")
    private val reserves = Reserves(sharesHeld = 39_102.0, tokensInCirculation = 38_800.68, custodians = listOf("Alpaca"), asOf = null)
    private val deepQuote = PriceEntry(usdPrice = 255.10, liquidity = 1_300_000.0, stockData = StockData(price = 254.57))

    /** 29 Sep 2026, 12:00 UTC. */
    private val now = 1_790_683_200_000L

    private fun payload(verdict: Verdict?, locked: Boolean) = AnalysisPayload(
        ticker = "AAPL",
        company = "Apple Inc.",
        sector = "Information Technology",
        compositePercentile = if (locked) null else 71.0,
        axes = Axes(
            quality = Axis(value = 8.0, scale = "0-9", position = 0.88, state = "strong", labelEn = "Strong"),
            valuation = if (locked) Axis(locked = true) else Axis(value = 51.09, scale = "0-100", position = 0.51, state = "fair", labelEn = "Moderate"),
            momentum = if (locked) Axis(locked = true) else Axis(value = 0.79, scale = "0-1", position = 0.79, state = "high", labelEn = "Near 52-week high"),
        ),
        verdict = verdict,
    )

    private fun state(
        verdict: Verdict? = Verdict(code = "quality_compounder", labelEn = "Quality compounder"),
        locked: Boolean = false,
        chainRead: Boolean = true,
        withReserves: Boolean = true,
        quote: PriceEntry? = deepQuote,
        analysed: Boolean = true,
    ) = DetailUiState(
        ticker = "AAPL",
        catalogAsset = Piece.Ready(asset),
        analysisState = if (analysed) AnalysisState.Served(payload(verdict, locked)) else AnalysisState.NotServed,
        quote = quote?.let { Piece.Ready(it) } ?: Piece.Absent,
        chain = if (chainRead) Piece.Ready(ChainRead(mintFacts(permanentDelegate = delegate), slot = 1L, readAtMillis = 1L)) else Piece.Failed,
        reserves = if (withReserves) Piece.Ready(reserves) else Piece.Failed,
        nowMillis = now,
    )

    private fun ShareCard.labels(): List<String> = facts.map { ShippedCopy.render(it.label) }

    /** Every word the card would draw, resolved the way the renderer resolves it. */
    private fun ShareCard.allText(): String = (
        listOfNotNull(eyebrow, headline, factsLabel, url, footer) + facts.flatMap { listOfNotNull(it.label, it.value, it.sub) }
        ).joinToString("\n") { ShippedCopy.render(it) }

    // ---- The stock card -----------------------------------------------------------------------------

    @Test
    fun `a pro reader's card names the stock, the issuer controls, the gap and the classification with its qualifier`() {
        val card = state().shareCard()
        assertEquals(ShareCardKind.Stock, card.kind)
        assertEquals("AAPLx", ShippedCopy.render(card.eyebrow!!))
        assertEquals("Apple Inc.", ShippedCopy.render(card.headline))
        assertEquals("Backing and controls", ShippedCopy.render(card.factsLabel!!))
        assertEquals(
            listOf(
                "Permanent delegate", "Reserves reported by xStocks", "Minted on chain",
                "In circulation per xStocks", "Token vs last US price", "Classification",
            ),
            card.labels(),
        )
        val delegateCell = card.facts[0]
        assertEquals("Yes", ShippedCopy.render(delegateCell.value))
        assertTrue("a delegate the issuer holds is the one caution", delegateCell.caution)
        assertEquals("100.8%", ShippedCopy.render(card.facts[1].value))
        assertEquals("38,800.68", ShippedCopy.render(card.facts[3].value))
        assertEquals("+0.21%", ShippedCopy.render(card.facts[4].value))
        val classification = card.facts[5]
        assertEquals("Quality compounder", ShippedCopy.render(classification.value))
        assertEquals("By the method's fixed rule. Not a price forecast or advice.", ShippedCopy.render(classification.sub!!))
        assertEquals(listOf(false, false, false, false, false), card.facts.drop(1).map { it.caution })
        assertEquals("plainticker.com/en/AAPL", ShippedCopy.render(card.url))
        assertEquals(
            "Read on 29 Sep 2026 from the Solana mint, xStocks and Jupiter. Not a price forecast or investment advice.",
            ShippedCopy.render(card.footer!!),
        )
    }

    @Test
    fun `a free reader's card carries no classification, no lock word and no Pro number`() {
        val free = state(verdict = Verdict(code = "quality_compounder", labelEn = "Quality compounder", locked = true), locked = true).shareCard()
        assertFalse("the classification row is Pro's", "Classification" in free.labels())
        val text = free.allText()
        listOf("Quality compounder", "Pro", "51.09", "Moderate", "of 100", "composite").forEach {
            assertFalse("a free reader's card carries \"$it\"", text.contains(it, ignoreCase = true))
        }
        // The facts that are open to everyone stay.
        assertEquals(
            listOf("Permanent delegate", "Reserves reported by xStocks", "Minted on chain", "In circulation per xStocks", "Token vs last US price"),
            free.labels(),
        )
    }

    @Test
    fun `no card carries the composite or an axis, not even a pro reader's`() {
        val text = state().shareCard().allText()
        listOf("51.09", "Moderate", "Strong", "Near 52-week high", "of 100", "composite").forEach {
            assertFalse("the card carries \"$it\"", text.contains(it))
        }
    }

    @Test
    fun `facts that did not read add no cell, so the card never says more than the screen`() {
        val card = state(chainRead = false, withReserves = false, quote = null, verdict = null).shareCard()
        assertTrue(card.facts.isEmpty())
        assertNull("no facts, no label over nothing", card.factsLabel)
        assertEquals("Apple Inc.", ShippedCopy.render(card.headline))
    }

    @Test
    fun `a gap is drawn only above the liquidity floor`() {
        val thin = PriceEntry(usdPrice = 611.56, liquidity = 34.0, stockData = StockData(price = 323.0))
        assertFalse("Token vs last US price" in state(quote = thin).shareCard().labels())
    }

    @Test
    fun `a delegate that is absent reads none without caution, and a lone last cell takes its row`() {
        val card = state(verdict = null, withReserves = false, quote = null)
            .copy(chain = Piece.Ready(ChainRead(mintFacts(permanentDelegate = null), slot = 1L, readAtMillis = 1L)))
            .shareCard()
        assertEquals(listOf("Permanent delegate", "Minted on chain"), card.labels())
        assertEquals("None", ShippedCopy.render(card.facts[0].value))
        assertFalse(card.facts[0].caution)
        assertEquals("two cells pair up", listOf(1, 1), card.facts.map { it.span })
        val odd = state(verdict = null).shareCard()
        assertEquals(5, odd.facts.size)
        assertEquals("the fifth cell takes its row", listOf(1, 1, 1, 1, 2), odd.facts.map { it.span })
    }

    @Test
    fun `a stock the web does not analyse links the site`() {
        assertEquals("plainticker.com", ShippedCopy.render(state(analysed = false).shareCard().url))
    }

    // ---- The vote card ------------------------------------------------------------------------------

    private val signature = "4xQm7gZ1LdPqR8vWnJb3sT6yUeK2cHaX9fNmD5oVtHe"

    @Test
    fun `a landed vote says what was voted for, the round, the staked SKR and the short transaction`() {
        val card = VoteState.Landed("NVDA", "NVDAx", 38_406_200_000L, signature, round = 3).shareCard()
        assertEquals(ShareCardKind.Vote, card.kind)
        assertEquals("I voted for NVDA to be analyzed next", ShippedCopy.render(card.headline))
        assertEquals(listOf("Round", "Staked SKR behind the vote", "Transaction on Solscan"), card.labels())
        assertEquals("3", ShippedCopy.render(card.facts[0].value))
        assertEquals("38,406.2 SKR", ShippedCopy.render(card.facts[1].value))
        val tx = card.facts[2]
        assertEquals("4xQm7g…5oVtHe", ShippedCopy.render(tx.value))
        assertTrue("a transaction is an on-chain identifier", tx.mono)
        assertEquals(2, tx.span)
        assertEquals("plainticker.com/en/vote", ShippedCopy.render(card.url))
    }

    @Test
    fun `a vote whose round is unknown claims none`() {
        val card = VoteState.Landed("NVDA", "NVDAx", 38_406_200_000L, signature).shareCard()
        assertEquals(listOf("Staked SKR behind the vote", "Transaction on Solscan"), card.labels())
        assertEquals("the weight takes the row it no longer shares", 2, card.facts[0].span)
    }

    // ---- The swap card ------------------------------------------------------------------------------

    private val tslax = SwapToken(KnownMints.TSLAX, "TSLAx", 8)
    private val quote = SwapQuote(
        requestId = "r",
        inAmountRaw = 5_000_000L,
        outAmountRaw = 1_360_437L,
        worstCaseOutRaw = 1_346_933L,
        routeCostPct = 0.586,
        slippageBps = 100,
        route = "Metis",
        swapType = "aggregator",
        gasless = false,
        solCost = com.plainticker.mobile.ui.swap.SolCost(signatureFeeLamports = 5_000L, rentFeeLamports = 0L, prioritizationFeeLamports = 0L),
        transaction = "tx",
        expireAtEpochSec = null,
    )
    private val fill = SwapFill(signature = signature, inAmountRaw = 5_000_000L, outAmountRaw = 1_360_940L, slot = 1L)
    private val timing = SwapTiming(startedAtMillis = 0L, phaseStartedAtMillis = 0L)

    @Test
    fun `a swap into a token says so with no amount, no price and no cost`() {
        val card = requireNotNull(SwapState.Landed(SwapLeg.into(tslax), quote, fill, requoted = false, timing = timing).shareCard())
        assertEquals("Swapped into TSLAx", ShippedCopy.render(card.headline))
        assertEquals(listOf("Route", "Transaction on Solscan"), card.labels())
        val text = card.allText()
        listOf("0.0136", "1,360", "5 USDC", "USDC", "$", "%").forEach {
            assertFalse("the swap card carries \"$it\"", text.contains(it))
        }
    }

    @Test
    fun `a swap back to USDC makes no card`() {
        assertNull(SwapState.Landed(SwapLeg.into(tslax).flipped(), quote, fill, requoted = false, timing = timing).shareCard())
    }

    // ---- Every card ---------------------------------------------------------------------------------

    @Test
    fun `no card says a verdict verb, and none shouts`() {
        val cards = listOf(
            state().shareCard(),
            VoteState.Landed("NVDA", "NVDAx", 1_000_000L, signature, round = 1).shareCard(),
            requireNotNull(SwapState.Landed(SwapLeg.into(tslax), quote, fill, requoted = false, timing = timing).shareCard()),
        )
        val verbs = Regex("""\b(b${"uy"}|s${"ell"}|h${"old"}|inv${"est"})\b""", RegexOption.IGNORE_CASE)
        cards.forEach { card ->
            val text = card.allText()
            assertFalse(text, verbs.containsMatchIn(text))
            assertFalse(text, '!' in text)
        }
    }
}
