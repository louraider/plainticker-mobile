package com.plainticker.mobile.ui.detail

import com.plainticker.mobile.data.rpc.PermanentDelegate
import com.plainticker.mobile.data.xstocks.Reserves
import com.plainticker.mobile.repo.mintFacts
import com.plainticker.mobile.repo.xStock
import com.plainticker.mobile.ui.ShippedCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Detail's Share line (judges' round 2): facts the screen read, the web page, nothing else. */
class DetailShareTest {

    private val asset = xStock("AAPLx", "AAPL", "mint-AAPL")

    private fun state(
        delegate: PermanentDelegate? = null,
        chainRead: Boolean = true,
        reserves: Reserves? = null,
        analysed: Boolean = true,
    ) = DetailUiState(
        ticker = "AAPL",
        catalogAsset = Piece.Ready(asset),
        analysisState = if (analysed) {
            AnalysisState.Served(com.plainticker.mobile.data.plainticker.AnalysisPayload(ticker = "AAPL"))
        } else {
            AnalysisState.NotServed
        },
        chain = if (chainRead) Piece.Ready(ChainRead(mintFacts(permanentDelegate = delegate), slot = 1L, readAtMillis = 1L)) else Piece.Failed,
        reserves = reserves?.let { Piece.Ready(it) } ?: Piece.Failed,
    )

    private fun share(state: DetailUiState) = state.shareText { ShippedCopy.render(it) }

    @Test
    fun `the line names the delegate and the reported reserves, then the stock's web page`() {
        val reserves = Reserves(sharesHeld = 1_008.0, tokensInCirculation = 1_000.0, custodians = listOf("Alpaca"), asOf = null)
        assertEquals(
            "AAPLx: issuer can move tokens (permanent delegate), reserves reported at 100.8%. " +
                "https://www.plainticker.com/en/AAPL",
            share(state(delegate = PermanentDelegate("5aMNNLQJwAEeoemTEMkv5NVjqKwvvefRYCQ5Z67HFvEq"), reserves = reserves)),
        )
    }

    @Test
    fun `a revoked or absent delegate is said as none`() {
        assertEquals(
            "AAPLx: no permanent delegate. https://www.plainticker.com/en/AAPL",
            share(state(delegate = PermanentDelegate(delegate = null))),
        )
    }

    @Test
    fun `facts that did not read add nothing, so a share never says more than the screen`() {
        assertEquals("AAPLx on PlainTicker. https://www.plainticker.com/en/AAPL", share(state(chainRead = false)))
    }

    @Test
    fun `a stock the web does not analyse links the site, never a page that is not there`() {
        assertEquals(
            "AAPLx: no permanent delegate. https://www.plainticker.com/en",
            share(state(delegate = PermanentDelegate(delegate = null), analysed = false)),
        )
    }

    @Test
    fun `no verdict, no score and no price ever ride along`() {
        val text = share(state(delegate = PermanentDelegate("x".padEnd(44, '1'))))
        listOf("Shortlist", "Watch", "Skip", "score", "\$").forEach { word ->
            assertFalse("the share line carries \"$word\"", text.contains(word))
        }
    }
}
