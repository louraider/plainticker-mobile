package com.plainticker.mobile.ui.today

import com.plainticker.mobile.data.plainticker.VoteRound
import com.plainticker.mobile.data.xstocks.MarketHours
import com.plainticker.mobile.data.xstocks.MarketSource
import com.plainticker.mobile.data.xstocks.MarketState
import com.plainticker.mobile.data.xstocks.MarketStatus
import com.plainticker.mobile.data.xstocks.Trading
import com.plainticker.mobile.data.xstocks.TradingPeriod
import com.plainticker.mobile.ui.ShippedCopy
import com.plainticker.mobile.ui.components.LongestSectorName
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
        // Day first since 2026-09-26: one short date format app-wide, matching "Monday 28 Sep".
        assertEquals("Reports 27 Oct", ShippedCopy.render(row.report))
        assertNull(row.poolNote)
        assertEquals("METAx", row.symbol)
    }

    @Test
    fun `a thin pool states itself instead of a figure`() {
        val row = todayWatchRow(watched(poolUsd = 2_700.0))
        assertNull("no figure off a dead pool", row.figure)
        assertEquals("Depth $2.7k, too thin", ShippedCopy.render(row.poolNote!!))
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

    // ---- Reports this week --------------------------------------------------------------------------------

    private fun reportRow(ticker: String, date: LocalDate, confirmed: Boolean? = true) =
        ReportRow(ticker, "${ticker}x", "$ticker Inc.", date, confirmed)

    private val monday = LocalDate.of(2026, 9, 28) // a Monday
    private val comingSunday = LocalDate.of(2026, 10, 4) // the coming Sunday

    // The real device's own weekend (task brief: "On the real device on Saturday 26 Sep 2026").
    // Its next Monday and next Sunday are exactly [monday] and [comingSunday] above.
    private val friday = LocalDate.of(2026, 9, 25)
    private val saturday = LocalDate.of(2026, 9, 26)
    private val sunday = LocalDate.of(2026, 9, 27)

    @Test
    fun `weekEnd is the coming Sunday, inclusive of today when today already is one`() {
        assertEquals(comingSunday, weekEnd(monday))
        assertEquals("Sunday itself is its own week end", comingSunday, weekEnd(comingSunday))
        assertEquals("Saturday's coming Sunday is tomorrow", LocalDate.of(2026, 9, 27), weekEnd(LocalDate.of(2026, 9, 26)))
    }

    @Test
    fun `this week keeps today through the coming Sunday and excludes both boundaries outside it`() {
        val rows = listOf(
            reportRow("YESTERDAY", monday.minusDays(1)),
            reportRow("TODAY", monday),
            reportRow("MIDWEEK", LocalDate.of(2026, 9, 30)),
            reportRow("SUN", comingSunday),
            reportRow("NEXTMON", comingSunday.plusDays(1)),
        )
        val week = reportsThisWeek(rows, monday)
        assertEquals(listOf("TODAY", "MIDWEEK", "SUN"), week.map { it.ticker })
    }

    @Test
    fun `this week sorts by date then ticker, a tie broken alphabetically`() {
        val rows = listOf(
            reportRow("NKE", LocalDate.of(2026, 9, 29)),
            reportRow("CCL", LocalDate.of(2026, 9, 29)),
            reportRow("PEP", LocalDate.of(2026, 10, 1)),
        )
        assertEquals(listOf("CCL", "NKE", "PEP"), reportsThisWeek(rows, monday).map { it.ticker })
    }

    // ---- The weekend window (this PR: Saturday 26 Sep 2026 read empty every weekend) -----------

    @Test
    fun `reportsIsNextWeek is true only on the reader's own Saturday and Sunday`() {
        assertTrue(reportsIsNextWeek(saturday))
        assertTrue(reportsIsNextWeek(sunday))
        assertFalse("Friday is still this week", reportsIsNextWeek(friday))
        assertFalse("Monday is still this week", reportsIsNextWeek(monday))
        assertFalse("a midweek day is still this week", reportsIsNextWeek(LocalDate.of(2026, 10, 1)))
    }

    @Test
    fun `Saturday and Sunday both move the window to next Monday through next Sunday`() {
        val rows = listOf(
            reportRow("SAT", saturday), // this weekend itself: dropped, not the window any more
            reportRow("SUN", sunday),
            reportRow("NEXTMON", monday), // next week's Monday: kept
            reportRow("NEXTMID", LocalDate.of(2026, 9, 30)),
            reportRow("NEXTSUN", comingSunday), // next week's Sunday: kept
            reportRow("PASTNEXT", comingSunday.plusDays(1)), // the week after that: dropped
        )
        assertEquals(
            "Saturday reads next Monday through next Sunday, not the two weekend days themselves",
            listOf("NEXTMON", "NEXTMID", "NEXTSUN"),
            reportsThisWeek(rows, saturday).map { it.ticker },
        )
        assertEquals(
            "Sunday reads the exact same window Saturday does",
            listOf("NEXTMON", "NEXTMID", "NEXTSUN"),
            reportsThisWeek(rows, sunday).map { it.ticker },
        )
    }

    @Test
    fun `Friday still reads this week, today through the coming Sunday`() {
        val rows = listOf(
            reportRow("FRI", friday),
            reportRow("SAT", saturday),
            reportRow("SUN", sunday),
            reportRow("NEXTMON", monday),
        )
        assertEquals(
            "no late-Friday roll-forward: Friday's own weekend is still this week, not next",
            listOf("FRI", "SAT", "SUN"),
            reportsThisWeek(rows, friday).map { it.ticker },
        )
    }

    @Test
    fun `an empty next week still names the next known report, off the weekend's own window end`() {
        val rows = listOf(reportRow("LATE", comingSunday.plusDays(3)))
        assertTrue("next week itself is quiet in this fixture", reportsThisWeek(rows, saturday).isEmpty())
        val next = nextReportAfterThisWeek(rows, saturday)
        assertEquals("LATE", next?.ticker)
        assertEquals(
            "No covered company reports next week. The next one is LATE Inc., on ${next!!.dateLabel}.",
            ShippedCopy.render(reportsEmptyCopy(next, isNextWeek = true)),
        )
    }

    @Test
    fun `next week's empty state with nothing known at all still names next week, not this week`() {
        assertEquals(
            "No covered company reports next week.",
            ShippedCopy.render(reportsEmptyCopy(null, isNextWeek = true)),
        )
        assertEquals(
            "isNextWeek defaults to false, so every weekday caller reads exactly as it always has",
            "No covered company reports this week.",
            ShippedCopy.render(reportsEmptyCopy(null)),
        )
    }

    @Test
    fun `the weekend window turns on right at the reader's own local midnight, Friday into Saturday`() {
        // Kyiv is UTC+3 in September (EEST): 23:59:59 Friday and 00:00:00 Saturday.
        val beforeMidnight = Instant.ofEpochMilli(utc("2026-09-25T20:59:59Z")).atZone(kyiv).toLocalDate()
        val afterMidnight = Instant.ofEpochMilli(utc("2026-09-25T21:00:00Z")).atZone(kyiv).toLocalDate()
        assertEquals(friday, beforeMidnight)
        assertEquals(saturday, afterMidnight)
        assertFalse("one second before local midnight, Friday still reads this week", reportsIsNextWeek(beforeMidnight))
        assertTrue("at local midnight, Saturday already reads next week", reportsIsNextWeek(afterMidnight))

        val rows = listOf(reportRow("SAT", saturday), reportRow("NEXTMON", monday))
        assertEquals(listOf("SAT"), reportsThisWeek(rows, beforeMidnight).map { it.ticker })
        assertEquals(listOf("NEXTMON"), reportsThisWeek(rows, afterMidnight).map { it.ticker })
    }

    @Test
    fun `the cap is about five, and the preview is the first five of a longer week`() {
        assertEquals(5, ReportsPreviewCount)
        val week = (0..6).map { reportRow("T$it", monday.plusDays(it.toLong())) }
        val all = reportsThisWeek(week, monday)
        assertEquals(7, all.size)
        assertEquals(listOf("T0", "T1", "T2", "T3", "T4"), all.take(ReportsPreviewCount).map { it.ticker })
    }

    /**
     * A US date read late at night in Kyiv still prints as exactly what the calendar says:
     * [ReportRow.date] is a bare [LocalDate], and [Fmt.weekday]/[Fmt.dayMonth] on a date take no
     * zone at all, so there is no conversion left that could move it. Before this rule, the same
     * fact off an [Instant] re-read in Kyiv (UTC+3 in September, hours ahead of New York) could
     * print a different calendar day depending on the hour, exactly the bug [reportsThisWeek]'s own
     * doc comment refuses to reintroduce.
     */
    @Test
    fun `a report's calendar day is never re-zoned, whatever the reader's own time is`() {
        val row = reportRow("NKE", LocalDate.of(2026, 9, 29))
        assertEquals("Tuesday 29 Sep", row.dateLabel)
    }

    @Test
    fun `only an explicit false reads as estimated, confirmed and unknown do not`() {
        assertFalse(reportRow("NKE", monday, confirmed = true).estimated)
        assertTrue(reportRow("NKE", monday, confirmed = false).estimated)
        assertFalse("an unknown confirmation is not stated as a guess", reportRow("NKE", monday, confirmed = null).estimated)
    }

    @Test
    fun `a watched ticker is matched case- and whitespace-insensitively, the same key every set here uses`() {
        assertTrue(reportRowWatched("meta", setOf(" META ")))
        assertTrue(reportRowWatched(" TSLA", setOf("tsla")))
        assertFalse(reportRowWatched("NVDA", setOf("META", "TSLA")))
    }

    @Test
    fun `the empty state says a quiet week plainly, or names the next known report after it`() {
        assertEquals("No covered company reports this week.", ShippedCopy.render(reportsEmptyCopy(null)))
        val next = reportRow("MU", LocalDate.of(2026, 10, 7))
        assertEquals(
            "No covered company reports this week. The next one is MU Inc., on Wednesday 7 Oct.",
            ShippedCopy.render(reportsEmptyCopy(next)),
        )
    }

    @Test
    fun `the next known report after this week ignores a company this week already carries`() {
        val rows = listOf(reportRow("NKE", LocalDate.of(2026, 9, 29)), reportRow("MU", LocalDate.of(2026, 10, 7)))
        assertEquals("MU", nextReportAfterThisWeek(rows, monday)?.ticker)
        assertNull("nothing known past this week", nextReportAfterThisWeek(listOf(reportRow("NKE", LocalDate.of(2026, 9, 29))), monday))
    }

    @Test
    fun `the link to Stocks names no filtered count, since Stocks cannot sort or filter by report date`() {
        assertEquals("See every covered company in Stocks", ShippedCopy.render(reportsAllCopy()))
    }

    /**
     * The clipping rule (DESIGN.md 5.4), measured with fontTools on 2026-09-26 against the bundled
     * fonts, the same way [com.plainticker.mobile.ui.components.AmberTickerRowTest] measures
     * [com.plainticker.mobile.ui.components.AmberTickerRow]'s own budgets: `context` at 14sp/400
     * (opsz 14), `figureRow` at 18sp/600 tnum (opsz 18, though tnum never touches a word), and
     * `TextAction`'s label at Bricolage 600 opsz 14 (`AmberType.textAction`, re-measured 2026-09-26
     * when it left Outfit SemiBold). Content width is 336dp (a 400dp frame less
     * `AmberTickerRowGroup`'s 16dp and the row's own 16dp on each side, [AmberTickerRow]'s own doc
     * comment). The widest realistic date sentence is any weekday paired with any day and any
     * three-letter month abbreviation, brute-forced against the real font rather than assumed:
     * "Wednesday 30 May", 130.676dp; joined with "estimated" through `list_row_meta_join`'s own
     * pattern (one middle dot), "Wednesday 30 May (middle dot) estimated", 206.164dp.
     *
     * **The watched marker ("Watched", drawn in the [figure] slot the way the Pro-numbers lock's
     * own "Pro" marker already does) never narrows an already-proven budget.** At 77.418dp it is
     * shorter than the app's own widest real figure ("38,406.2 SKR", 113.220dp,
     * `AmberTickerRowTest`'s own pinned number), so the context budget beside it (250.582dp at
     * 1.0x, 227.357dp at 1.3x) is wider than the 214.780dp that figure already proves clear.
     */
    @Test
    fun `the watched marker is comfortably the shortest content this slot ever draws, at 1_0x and 1_3x`() {
        val watchedMarkerWidthDp = 77.418
        val worstRealFigureWidthDp = 113.220
        assertTrue(watchedMarkerWidthDp <= worstRealFigureWidthDp)
        assertTrue(watchedMarkerWidthDp * 1.3 <= worstRealFigureWidthDp * 1.3)
    }

    @Test
    fun `a watched row's date, plain or estimated, clears the context budget beside the Watched marker at 1_0x, and wraps rather than clips at 1_3x`() {
        val contentWidthDp = 336.0
        val gapDp = 8.0
        val watchedMarkerWidthDp = 77.418
        val budget10x = contentWidthDp - watchedMarkerWidthDp - gapDp
        assertEquals(250.582, budget10x, 0.01)
        val budget13x = contentWidthDp - watchedMarkerWidthDp * 1.3 - gapDp
        assertEquals(227.357, budget13x, 0.01)

        val datePlainDp = 130.676
        val dateEstimatedDp = 206.164
        assertTrue("the plain date clears one line at 1.0x", datePlainDp <= budget10x)
        assertTrue("the plain date clears one line even at 1.3x", datePlainDp * 1.3 <= budget13x)
        assertTrue("the worst estimated date clears one line at 1.0x", dateEstimatedDp <= budget10x)
        assertFalse(
            "the worst estimated date, regrown to 1.3x, is expected to miss this shrunk budget and " +
                "wrap rather than clip: context keeps maxLines = 2",
            dateEstimatedDp * 1.3 <= budget13x,
        )
        // The backstop actually backstops: two lines' own combined capacity at 1.3x comfortably
        // exceeds even this worst case, so maxLines = 2 is a real ceiling here.
        assertTrue(dateEstimatedDp * 1.3 <= budget13x * 2)
    }

    @Test
    fun `a first-open row's date, plain or estimated, clears the context budget beside Watch at 1_0x, and wraps rather than clips at 1_3x`() {
        val contentWidthDp = 336.0
        val actionStartPaddingDp = 16.0
        val watchLabelWidthDp = 43.624 // "Watch" (action_watch) at Bricolage 600 opsz 14, 14sp.
        val budget10x = contentWidthDp - (actionStartPaddingDp + watchLabelWidthDp)
        assertEquals(276.376, budget10x, 0.01)
        val budget13x = contentWidthDp - (actionStartPaddingDp + watchLabelWidthDp * 1.3)
        assertEquals(263.289, budget13x, 0.01)

        val datePlainDp = 130.676
        val dateEstimatedDp = 206.164
        assertTrue(datePlainDp <= budget10x)
        assertTrue(datePlainDp * 1.3 <= budget13x)
        assertTrue(dateEstimatedDp <= budget10x)
        assertFalse(
            "the worst estimated date regrown to 1.3x wraps rather than clips here too",
            dateEstimatedDp * 1.3 <= budget13x,
        )
        assertTrue(dateEstimatedDp * 1.3 <= budget13x * 2)
    }

    /**
     * The new "Reports next week" heading (this PR, the weekend fix), measured the same way as
     * the watched-marker tests above: fontTools 4.63.0 against `res/font/bricolage_grotesque.ttf`
     * at `AmberSectionHead`'s own `sectionHead` instance (`wght` 700, `wdth` 100, `opsz` 22, 22sp),
     * 2026-09-26. "Reports this week" and "Reports next week" are both 17 characters ("this" and
     * "next" are the same length), 191.994dp and 199.232dp.
     *
     * `AmberSectionHead`'s title has nowhere to clip in the first place (DESIGN.md 4.2: it owns a
     * `weight(1f)` column and wraps to two lines rather than squeezing), so this is the same
     * confirmation-not-assumption every other new slot in this file already gets, not a new
     * budget: the new heading sits far under the widest real content this exact slot already draws
     * safely, the tied-longest GICS sector name ([LongestSectorName], "Communication Services,"
     * 266.090dp, proven safe by `AmberSectionHeadTest`), at 1.0x and at 1.3x alike.
     */
    @Test
    fun `the next-week heading clears the widest real content this section head slot already draws safely, at 1_0x and 1_3x`() {
        val reportsThisWeekWidthDp = 191.994
        val reportsNextWeekWidthDp = 199.232
        val provenSafeWidthDp = 266.090 // LongestSectorName, "Communication Services".
        assertEquals(17, "Reports this week".length)
        assertEquals(17, "Reports next week".length)
        assertEquals(22, LongestSectorName.length)
        assertTrue("this week's own heading already sits under the proven-safe width", reportsThisWeekWidthDp <= provenSafeWidthDp)
        assertTrue("next week's heading does too, at 1.0x", reportsNextWeekWidthDp <= provenSafeWidthDp)
        assertTrue("...and at 1.3x", reportsNextWeekWidthDp * 1.3 <= provenSafeWidthDp * 1.3)
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

    // ---- Next up with nobody voted yet ------------------------------------------------------------

    @Test
    fun `an open round with no votes says so by its number, and that one vote decides it`() {
        val round = VoteRound(id = 3, opensAt = "2026-09-21T00:00:00.000Z", closesAt = "2026-09-28T00:00:00.000Z")
        assertEquals("Round 3 is open. No votes yet: one vote decides it.", ShippedCopy.render(nextUpEmpty(round)))
    }

    // ---- While New York is closed ---------------------------------------------------------------

    private fun quote(ticker: String, premiumPct: Double, pool: Double? = 250_000.0) = CoveredQuote(
        ticker = ticker,
        symbol = "${ticker}x",
        company = "$ticker Inc.",
        price = com.plainticker.mobile.repo.price(usd = 100.0 * (1.0 + premiumPct / 100.0), reference = 100.0, liquidity = pool),
    )

    @Test
    fun `the movers are the furthest from the close, at most three, furthest first`() {
        val movers = closedMovers(
            listOf(quote("AAPL", 0.9), quote("NVDA", -2.4), quote("TSLA", 1.6), quote("META", 3.1), quote("JEF", 0.7)),
        )
        assertEquals(listOf("METAx", "NVDAx", "TSLAx"), movers.map { it.symbol })
        assertEquals(-2.4, movers[1].premiumPct, 1e-9)
    }

    @Test
    fun `a thin pool or an unreported depth is never a mover, however far its quote sits`() {
        val movers = closedMovers(
            listOf(
                quote("APP", 89.34, pool = 34.0),
                quote("JPM", 37.98, pool = null),
                quote("AAPL", 0.9),
            ),
        )
        assertEquals(listOf("AAPLx"), movers.map { it.symbol })
    }

    @Test
    fun `a gap inside the half point band is not a move`() {
        assertTrue(closedMovers(listOf(quote("AAPL", 0.3), quote("NVDA", -0.49))).isEmpty())
    }

    @Test
    fun `the block shows only while the exchange is known to be closed`() {
        val movers = closedMovers(listOf(quote("META", 3.1)))
        val closed = MarketStatus(MarketState.CLOSED, MarketSource.VENUE, venueOpen = false, nextChangeAtMillis = null)
        val open = MarketStatus(MarketState.REGULAR, MarketSource.VENUE, venueOpen = true, nextChangeAtMillis = null)
        assertTrue(closedMoversShown(closed, movers))
        assertFalse("the session's live figure is on the watched rows already", closedMoversShown(open, movers))
        assertFalse("an unknown venue is not a closed one", closedMoversShown(null, movers))
        assertFalse("nothing moved, nothing drawn", closedMoversShown(closed, emptyList()))
    }

    // ---- Next up's "Voted" (device QA of 1.3.18) ------------------------------------------------

    @Test
    fun `next up's leader reads Voted by the shared round rule, whatever the case`() {
        val leader = TodayLeader(ticker = "AAL", symbol = "AALx", company = "American Airlines", weightRaw = java.math.BigInteger.ONE, voters = 1)
        assertTrue(nextUpVoted(leader, setOf("AAL")))
        assertTrue(nextUpVoted(leader.copy(ticker = "aal "), setOf("AAL")))
        assertFalse(nextUpVoted(leader, setOf("TSLA")))
        assertFalse(nextUpVoted(leader, emptySet()))
    }
}
