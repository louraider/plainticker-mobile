package com.plainticker.mobile.data.xstocks

import com.plainticker.mobile.data.Fixtures
import com.plainticker.mobile.data.net.HttpClientFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Proof of reserves, and the one thing the mapper exists to prevent: xStocks answers 200 with a
 * JSON `null` for a symbol it publishes nothing for, and a screen that drew that as a coverage of
 * zero would make the strongest possible claim out of no data.
 */
class ReservesTest {

    private fun parse(path: String): ProofOfReserves =
        HttpClientFactory.json.decodeFromString(ProofOfReserves.serializer(), Fixtures.read(path))

    @Test
    fun `the live TSLAx answer becomes shares held against tokens in circulation, with its custodian`() {
        val reserves = checkNotNull(parse("xstocks/por-tslax.json").toReserves())

        assertEquals(196_869.0, reserves.sharesHeld, 0.0)
        assertEquals(196_340.26085951860836, reserves.tokensInCirculation, 1e-9)
        assertEquals("Alpaca", reserves.custodian)
        assertEquals(listOf("Alpaca"), reserves.custodians)
        assertEquals("2026-09-10T20:51:24.651Z", reserves.asOf)
        assertEquals(196_869.0 / 196_340.26085951860836, reserves.coverage!!, 1e-12)
    }

    @Test
    fun `a JSON null answer is nothing to state, and never a zero`() {
        val absent: ProofOfReserves? = null
        assertNull(absent.toReserves())
    }

    @Test
    fun `a missing or unusable figure is nothing to state either`() {
        assertNull(ProofOfReserves(symbol = "TSLAx", circulatingSupply = "10").toReserves())
        assertNull(ProofOfReserves(symbol = "TSLAx", sharesHeld = "10").toReserves())
        assertNull(ProofOfReserves(symbol = "TSLAx", sharesHeld = "many", circulatingSupply = "10").toReserves())
        assertNull(ProofOfReserves(symbol = "TSLAx", sharesHeld = "-1", circulatingSupply = "10").toReserves())
    }

    @Test
    fun `no token outstanding is no coverage figure rather than a division`() {
        val reserves = checkNotNull(
            ProofOfReserves(symbol = "TSLAx", sharesHeld = "10", circulatingSupply = "0").toReserves(),
        )
        assertEquals(10.0, reserves.sharesHeld, 0.0)
        assertNull(reserves.coverage)
    }

    @Test
    fun `custodians are named in order, blanks dropped, and two of them name none on their own`() {
        val two = checkNotNull(
            ProofOfReserves(
                symbol = "TSLAx",
                sharesHeld = "10",
                circulatingSupply = "10",
                holdings = listOf(
                    Holding(provider = "Alpaca", quantity = "6"),
                    Holding(provider = "  ", quantity = "0"),
                    Holding(provider = "InteractiveBrokers", quantity = "4"),
                    Holding(provider = "Alpaca", quantity = "0"),
                ),
            ).toReserves(),
        )
        assertEquals(listOf("Alpaca", "InteractiveBrokers"), two.custodians)
        assertNull("two custodians, so no single one to name", two.custodian)
    }
}
