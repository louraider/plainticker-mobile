package com.plainticker.mobile.ui.today

import com.plainticker.mobile.data.plainticker.VoteRound
import com.plainticker.mobile.data.xstocks.MarketHours
import com.plainticker.mobile.data.xstocks.MarketSource
import com.plainticker.mobile.data.xstocks.MarketState
import com.plainticker.mobile.data.xstocks.MarketStatus
import com.plainticker.mobile.data.xstocks.Trading
import com.plainticker.mobile.data.xstocks.TradingPeriod
import com.plainticker.mobile.ui.ShippedCopy
import com.plainticker.mobile.watchlist.DigestRecord
import com.plainticker.mobile.watchlist.WatchedTicker
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What Today says in direction A, off the shipped strings.xml the way [ShippedCopy] reads it for
 * every other screen's model test. The reader's zone is a parameter, so the status line is pinned
 * for a reader in Kyiv (the founder's phone), in New York and in Tokyo alike.
 */
class TodayModelTest {

    private val kyiv = ZoneId.of("Europe/Kyiv")
    private val newYork = ZoneId.of("America/New_York")
    private val tokyo = ZoneId.of("Asia/Tokyo")

    private fun utc(text: String): Long = Instant.parse(text).toEpochMilli()

    private fun at(now: String, snapshot: Trading? = null): Pair<MarketStatus, Long> {
        val millis = utc(now)
        return MarketHours.sessionAt(millis, snapshot) to millis
    }

    private fun line(now: String, zone: ZoneId, snapshot: Trading? = null): String {
        val (status, millis) = at(now, snapshot)
        return ShippedCopy.render(statusLine(status, millis, zone)!!)
    }

    // ---- The status line ----------------------------------------------------------------------------

    @Test
    fun `the status line is undrawn while no catalog has answered`() {
        assertNull(statusLine(null, utc("2026-09-24T19:22:00Z"), kyiv))
    }

    @Test
    fun `open, from the venue, closes at the venue's own next change in the reader's time`() {
        val venue = Trading(currentPeriod = TradingPeriod.MARKET, openNow = true, nextChangeAt = "2026-09-24T20:00:00Z")
        assertEquals("NYSE open. Closes at 23:00 your time", line("2026-09-24T19:22:00Z", kyiv, venue))
        assertEquals("NYSE open. Closes at 16:00 your time", line("2026-09-24T19:22:00Z", newYork, venue))
    }

    @Test
    fun `open by the calendar says so, the seeker's own moment with the stale snapshot`() {
        val stale = Trading(currentPeriod = TradingPeriod.CLOSED, openNow = false, nextChangeAt = "2026-09-24T13:30:00Z")
        assertEquals(
            "NYSE open by the exchange calendar. Closes at 23:00 your time",
            line("2026-09-24T19:22:00Z", kyiv, stale),
        )
    }

    @Test
    fun `the early close says it is a short day, at its own 13 00 close`() {
        val venue = Trading(currentPeriod = TradingPeriod.MARKET, openNow = true, nextChangeAt = "2026-11-27T18:00:00Z")
        // Kyiv is UTC+2 in November, so 13:00 in New York (18:00 UTC) is 20:00.
        assertEquals("NYSE open, short day. Closes at 20:00 your time", line("2026-11-27T16:00:00Z", kyiv, venue))
    }

    @Test
    fun `pre-market has its own sentence, and says tokens trade on thinner pools`() {
        assertEquals(
            "NYSE opens at 16:30 your time. Tokens still trade, on thinner pools",
            line("2026-09-24T12:00:00Z", kyiv),
        )
    }

    @Test
    fun `after hours has its own sentence, and its day word is the reader's own calendar day`() {
        // 17:31 in New York on 24 Sep is 00:31 on 25 Sep in Kyiv: the open is later that same Kyiv day.
        assertEquals(
            "NYSE closed for the day. Opens today at 16:30 your time. Tokens still trade, on thinner pools",
            line("2026-09-24T21:31:00Z", kyiv),
        )
        assertEquals(
            "NYSE closed for the day. Opens tomorrow at 09:30 your time. Tokens still trade, on thinner pools",
            line("2026-09-24T21:31:00Z", newYork),
        )
    }

    @Test
    fun `closed overnight opens today or tomorrow depending on where the reader is`() {
        // 20:30 in New York on 23 Sep is 03:30 on 24 Sep in Kyiv and 09:30 on 24 Sep in Tokyo.
        assertEquals("NYSE closed. Opens tomorrow at 09:30 your time", line("2026-09-24T00:30:00Z", newYork))
        assertEquals("NYSE closed. Opens today at 16:30 your time", line("2026-09-24T00:30:00Z", kyiv))
        assertEquals("NYSE closed. Opens today at 22:30 your time", line("2026-09-24T00:30:00Z", tokyo))
    }

    @Test
    fun `a weekend names the weekday it opens`() {
        assertEquals("NYSE closed. Opens Monday at 16:30 your time", line("2026-09-26T12:00:00Z", kyiv))
    }

    @Test
    fun `a holiday says it is a holiday`() {
        // Thanksgiving, 26 Nov 2026; Kyiv is UTC+2 and New York UTC-5, so the open is 16:30.
        assertEquals("NYSE closed for a holiday. Opens tomorrow at 16:30 your time", line("2026-11-26T16:00:00Z", kyiv))
    }

    @Test
    fun `the bar is lit only in the exchange's own session, and the calendar note only when the calendar answers`() {
        val open = at("2026-09-24T19:22:00Z", Trading(currentPeriod = TradingPeriod.MARKET, openNow = true, nextChangeAt = "2026-09-24T20:00:00Z")).first
        val calendar = at("2026-09-24T19:22:00Z").first
        val closed = at("2026-09-26T12:00:00Z").first
        assertTrue(statusLive(open))
        assertTrue(statusLive(calendar))
        assertFalse(statusLive(closed))
        assertFalse(statusLive(null))
        assertFalse("the venue answered", statusFromCalendar(open))
        assertTrue(statusFromCalendar(calendar))
    }

    @Test
    fun `no status sentence carries more than one middle dot, an exclamation mark or a verdict`() {
        val samples = listOf(
            line("2026-09-24T19:22:00Z", kyiv),
            line("2026-09-24T12:00:00Z", kyiv),
            line("2026-09-24T21:31:00Z", kyiv),
            line("2026-09-26T12:00:00Z", kyiv),
            line("2026-11-26T16:00:00Z", kyiv),
        )
        samples.forEach {
            assertFalse(it, it.contains('!'))
            assertTrue(it, it.count { c -> c == '·' } <= 1)
            assertTrue("every status keeps its state in the first sentence: $it", it.startsWith("NYSE "))
        }
    }

    // ---- Freshness, now in the market hours sheet -----------------------------------------------------

    @Test
    fun `freshness waits for both ages, true only once both are known`() {
        assertNull("the analysis age alone is not the sentence", freshnessSentence(1_000L, null, 2_000L))
        assertNull("the price age alone is not the sentence", freshnessSentence(null, 1_000L, 2_000L))
        assertNull(freshnessSentence(null, null, 2_000L))
    }

    @Test
    fun `freshness reads both ages once they are both known`() {
        val now = utc("2026-09-13T12:00:00.000Z")
        val analysisAt = utc("2026-09-12T12:00:00.000Z")
        assertEquals("Analysis 1 d ago, prices 2 s ago.", ShippedCopy.render(freshnessSentence(analysisAt, now - 2_000L, now)!!))
    }

    // ---- Watched ----------------------------------------------------------------------------------------

    @Test
    fun `the figure's meaning is said once, against the share while trading and the close otherwise`() {
        val open = MarketStatus(MarketState.REGULAR, MarketSource.VENUE, venueOpen = true, nextChangeAtMillis = null)
        val shut = open.copy(state = MarketState.CLOSED, venueOpen = false)
        assertEquals("The figure is how far each token sits from the share price now.", ShippedCopy.render(figureMeaning(open)))
        assertEquals("The figure is how far each token sits from the last NYSE close.", ShippedCopy.render(figureMeaning(shut)))
        assertEquals("not known yet reads as the close", ShippedCopy.render(figureMeaning(shut)), ShippedCopy.render(figureMeaning(null)))
    }

    private fun watched(poolUsd: Double?, price: Double? = 232.54, reference: Double? = 232.52) = WatchedTicker(
        ticker = "META",
        symbol = "METAx",
        company = "Meta Platforms, Inc.",
        mint = "mint-META",
        analyzed = true,
        nextReport = LocalDate.of(2026, 10, 27),
        priceUsd = price,
        referencePriceUsd = reference,
        poolUsd = poolUsd,
    )

    @Test
    fun `a watched row above the floor draws one figure, the premium, and no pool note`() {
        val row = todayWatchRow(watched(poolUsd = 250_000.0, price = 100.15, reference = 100.0))
        assertEquals("+0.15%", row.figure)
        assertEquals("Reports Oct 27", ShippedCopy.render(row.report))
        assertNull(row.poolNote)
        assertEquals("METAx", row.symbol)
    }

    @Test
    fun `a thin pool states itself instead of a figure`() {
        val row = todayWatchRow(watched(poolUsd = 2_700.0))
        assertNull("no figure off a dead pool", row.figure)
        assertEquals("$2.7k behind, too thin", ShippedCopy.render(row.poolNote!!))
    }

    @Test
    fun `a pool with no reported depth says so, and an unpriced token says nothing at all`() {
        assertEquals("Depth not reported", ShippedCopy.render(todayWatchRow(watched(poolUsd = null)).poolNote!!))
        val unpriced = todayWatchRow(watched(poolUsd = null, price = null, reference = null))
        assertNull(unpriced.figure)
        assertNull(unpriced.poolNote)
    }

    /**
     * QA 2026-09-26, D3. `/summary` failing to answer at all leaves every row's [WatchedTicker.analyzed]
     * false, the same shape a genuine drop from the leaderboard leaves it in; `todayWatchRow`'s own
     * [Boolean] parameter is how Today's Watched card tells "the network failed" apart from "not in
     * the list," reading `WatchlistUiState.analysisUnavailable` the way `TodayScreen.kt` threads it.
     */
    @Test
    fun `offline, a dropped-looking row says the network failed rather than that it was delisted`() {
        val unserved = watched(poolUsd = 250_000.0).copy(analyzed = false)
        val offline = todayWatchRow(unserved, analysisUnavailable = true)
        assertEquals("Couldn't load right now", ShippedCopy.render(offline.report))

        // The same shape, once /summary has actually answered: a real drop reads as one.
        val backOnline = todayWatchRow(unserved, analysisUnavailable = false)
        assertEquals("Not in the analysis list", ShippedCopy.render(backOnline.report))
    }

    // ---- Digest ------------------------------------------------------------------------------------------

    @Test
    fun `the digest line says when the last one landed, in the reader's own time`() {
        val record = DigestRecord(text = "1 stock watched.", producedAtMillis = utc("2026-09-24T06:49:00Z"))
        assertEquals("Last digest today at 09:49.", ShippedCopy.render(digestLink(record, utc("2026-09-24T19:22:00Z"), kyiv)))
        assertEquals("Last digest Thursday at 09:49.", ShippedCopy.render(digestLink(record, utc("2026-09-25T19:22:00Z"), kyiv)))
        assertTrue(digestReadable(record))
    }

    @Test
    fun `before the first digest the line says when it lands, and offers nothing to read`() {
        assertEquals(
            "The first digest lands about twelve hours after you watch a stock.",
            ShippedCopy.render(digestLink(DigestRecord.NONE, utc("2026-09-24T19:22:00Z"), kyiv)),
        )
        assertFalse(digestReadable(DigestRecord.NONE))
    }

    // ---- Tracked today ------------------------------------------------------------------------------------

    private fun tracked(ticker: String, pool: Double) = TrackedRow(ticker, "${ticker}x", "$ticker Inc.", 0.1, pool)

    @Test
    fun `tracked shows three rows and never a ticker the reader already watches`() {
        val all = listOf(tracked("NVDA", 9e6), tracked("META", 8e6), tracked("TSLA", 7e6), tracked("COIN", 6e6), tracked("HOOD", 5e6))
        val preview = trackedPreview(all, watched = setOf("meta ", "TSLA"))
        assertEquals(listOf("NVDA", "COIN", "HOOD"), preview.map { it.ticker })
        assertEquals(3, TrackedPreviewCount)
        assertEquals("deepest first, capped", listOf("NVDA", "META", "TSLA"), trackedPreview(all, emptySet()).map { it.ticker })
    }

    @Test
    fun `all-tracked hands the rest to Stocks with the whole tracked total`() {
        assertEquals("All 20 in Stocks", ShippedCopy.render(trackedAllCopy(20)))
        assertEquals("All 1 in Stocks", ShippedCopy.render(trackedAllCopy(1)))
    }

    @Test
    fun `a tracked row without a reference price draws the missing placeholder, not a crash`() {
        val row = TrackedRow(ticker = "AAPL", symbol = "AAPLx", company = "Apple Inc.", premiumPct = null, poolUsd = 250_000.0)
        assertEquals("-", row.figure)
        assertEquals("AAPLx", row.display)
    }

    @Test
    fun `a tracked row with a premium formats it the same way every other screen's premium reads`() {
        val row = TrackedRow(ticker = "TSLA", symbol = "TSLAx", company = "Tesla, Inc.", premiumPct = 0.09, poolUsd = 500_000.0)
        assertEquals("+0.09%", row.figure)
    }

    @Test
    fun `a token the catalog has not named yet falls back to the underlying ticker`() {
        val row = TrackedRow(ticker = "MCD", symbol = null, company = null, premiumPct = -1.01, poolUsd = 26_205.0)
        assertEquals("MCD", row.display)
    }

    /**
     * The clipping rule (DESIGN.md 5.4) for the one new pairing on a first open: a Tracked row's
     * meta line carries its figure and a Watch action, with no context. Measured with fontTools on
     * 2026-09-24 against the bundled fonts: the widest real figure "+100.00%" at `figureRow`
     * (Bricolage 600, 18sp, tnum) is 85.662dp; "Watch" at Outfit SemiBold 14sp is 42.322dp, drawn
     * through TextAction with 16dp of start padding. AmberTickerRow's content width is 336dp (the
     * 400dp frame less the group's 16dp and the row's own 16dp on each side). sp scales by the raw
     * factor at 1.3x and dp padding does not, the same conservative assumption AmberTickerRowTest
     * makes.
     */
    @Test
    fun `a first-open tracked row's figure and Watch clear the row's meta line at 1_0x and 1_3x`() {
        val content = 336.0
        val figure = 85.662
        val watch = 42.322
        val gap = 16.0
        assertTrue(figure + gap + watch <= content)
        assertEquals(192.016, content - (figure + gap + watch), 0.01)
        val at13 = figure * 1.3 + gap + watch * 1.3
        assertTrue(at13 <= content)
        assertEquals(153.621, content - at13, 0.01)
    }

    // ---- Next up ------------------------------------------------------------------------------------------

    @Test
    fun `the round lede is undrawn when there is no round, or the round names no close`() {
        assertNull(nextUpLede(null, kyiv))
        assertNull(nextUpLede(VoteRound(id = 2, opensAt = "2026-09-21T00:00:00Z", closesAt = "not a date"), kyiv))
    }

    @Test
    fun `the round lede names the close in the reader's own time, not UTC`() {
        val round = VoteRound(id = 2, opensAt = "2026-09-21T00:00:00Z", closesAt = "2026-09-28T00:00:00.000Z")
        assertEquals("Chosen by staked SKR. Round closes Monday at 03:00 your time.", ShippedCopy.render(nextUpLede(round, kyiv)!!))
        assertEquals("Chosen by staked SKR. Round closes Sunday at 20:00 your time.", ShippedCopy.render(nextUpLede(round, newYork)!!))
    }

    @Test
    fun `the leader's weight and voters read the same shipped copy the List's own leaders read`() {
        val leader = TodayLeader(
            ticker = "AMD",
            symbol = "AMDx",
            company = "AMD xStock",
            weightRaw = BigInteger.valueOf(6_719_000_000L), // 6 SKR decimals: 6,719 SKR exactly
            voters = 1,
        )
        assertEquals("AMDx", leader.display)
        assertEquals("6,719 SKR", ShippedCopy.render(leader.weight))
        assertEquals("1 voter", ShippedCopy.render(leader.votersContext))
        assertEquals("3 voters", ShippedCopy.render(leader.copy(voters = 3).votersContext))
    }

    @Test
    fun `a leader the catalog has not named yet falls back to the underlying ticker`() {
        val leader = TodayLeader(ticker = "AMD", symbol = null, company = null, weightRaw = BigInteger.ZERO, voters = 0)
        assertEquals("AMD", leader.display)
    }
}
