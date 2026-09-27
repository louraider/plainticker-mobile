package com.plainticker.mobile.lint

import com.plainticker.mobile.data.KnownMints
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.ShippedCopy
import com.plainticker.mobile.ui.portfolio.PortfolioPosition
import com.plainticker.mobile.ui.portfolio.PortfolioUiState
import com.plainticker.mobile.ui.portfolio.WalletPhase
import com.plainticker.mobile.ui.portfolio.totalBlock
import com.plainticker.mobile.ui.swap.SwapAmount
import com.plainticker.mobile.ui.swap.SwapFunds
import com.plainticker.mobile.ui.swap.SwapLeg
import com.plainticker.mobile.ui.swap.SwapState
import com.plainticker.mobile.ui.swap.SwapToken
import com.plainticker.mobile.ui.swap.sheet
import com.plainticker.mobile.wallet.testAccount
import com.plainticker.mobile.watchlist.DigestInput
import com.plainticker.mobile.watchlist.RealStrings
import com.plainticker.mobile.watchlist.digest
import com.plainticker.mobile.watchlist.watched
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Counted copy: one reads as one, and many reads as many.
 *
 * The Portfolio drew "1 xStocks, priced by Jupiter" on the Seeker on 2026-09-13, on the screen a
 * reader reaches straight after a swap (docs/data-map.md, "What the first real swap taught us").
 * A count of one reading as a plural is the kind of sloppiness this product is against, so this
 * file holds the whole class rather than that one sentence:
 *
 * 1. **Every counted sentence is a `plurals`**, rendered here at one and at many out of the
 *    shipped strings.xml through [ShippedCopy], so the assertion is the sentence a reader sees.
 * 2. **Every model that counts is asked twice**, once with one and once with many, so the count
 *    that chose the form and the numeral the sentence prints cannot drift apart.
 * 3. **A whole number may not reach a plain string.** `Fmt.count` is the one way an integer
 *    becomes copy in this app, so the lint below follows it: a count handed to `stringResource`,
 *    `words` or `getString` is a finding, and a future count cannot regress without failing here.
 */
class CountCopyTest {

    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    // ---- The copy itself ----------------------------------------------------------------------

    @Test
    fun `every counted sentence has a one and an other, and they differ`() {
        assertTrue("strings.xml declares no counted copy at all", ShippedCopy.plurals.isNotEmpty())
        ShippedCopy.plurals.forEach { (name, forms) ->
            assertEquals("$name does not declare exactly one and other", setOf("one", "other"), forms.keys)
            forms.forEach { (quantity, text) -> assertTrue("$name/$quantity is blank", text.isNotBlank()) }
            assertNotEquals(
                "$name says the same thing at one and at many, so it is not counted copy",
                forms["one"],
                forms["other"],
            )
        }
    }

    /** Every counted sentence, at one and at many, as a reader reads it. */
    private val expected: Map<String, List<Case>> = mapOf(
        // "12 days old", not "12 d old" (audit 2026-09-26): the analysis age on a Stocks row and
        // in the stale banner. list_today_watched left with the Stocks Today strip it drew, and
        // list_today with the onboarding backdrop's copy of that strip (2026-09-26).
        "list_row_age_days" to listOf(
            Case(1, listOf("1"), "1 day old"),
            Case(12, listOf("12"), "12 days old"),
            Case(1_204, listOf("1,204"), "1,204 days old"),
        ),
        "next_up_voters" to listOf(
            Case(1, listOf("1"), "1 voter"),
            Case(3, listOf("3"), "3 voters"),
        ),
        "next_up_detail_weight" to listOf(
            Case(1, listOf("38,406.2", "1"), "38,406.2 SKR from 1 voter"),
            Case(3, listOf("38,406.2", "3"), "38,406.2 SKR from 3 voters"),
        ),
        "detail_fscore_of" to listOf(
            Case(1, listOf("1"), "of 1 signal"),
            Case(9, listOf("9"), "of 9 signals"),
        ),
        "detail_fscore_a11y" to listOf(
            Case(1, listOf("1", "1"), "F-Score 1 of 1 signal"),
            Case(9, listOf("8", "9"), "F-Score 8 of 9 signals"),
        ),
        "swap_amount_too_precise" to listOf(
            Case(1, listOf("CENTx", "1"), "CENTx counts 1 decimal"),
            Case(6, listOf("USDC", "6"), "USDC counts 6 decimals"),
        ),
        "portfolio_priced_by" to listOf(
            Case(1, listOf("1"), "1 xStock, priced by Jupiter"),
            Case(4, listOf("4"), "4 xStocks, priced by Jupiter"),
        ),
        "portfolio_priced_partial" to listOf(
            Case(1, listOf("1", "1"), "1 of 1 xStock priced by Jupiter, the total covers what is priced"),
            Case(3, listOf("1", "3"), "1 of 3 xStocks priced by Jupiter, the total covers what is priced"),
        ),
        "portfolio_priced_none" to listOf(
            Case(1, listOf("1"), "1 xStock, not priced by Jupiter"),
            Case(3, listOf("3"), "3 xStocks, none of them priced by Jupiter"),
        ),
        "digest_reports_in_days" to listOf(
            Case(1, listOf("TSLAx", "1"), "TSLAx reports in 1 day."),
            Case(4, listOf("TSLAx", "4"), "TSLAx reports in 4 days."),
        ),
        "digest_week_reports" to listOf(
            Case(1, listOf("1"), "1 covered company reports this week."),
            Case(3, listOf("3"), "3 covered companies report this week."),
        ),
        // You's hero and Plan "Valid until" row (ui/you/YouModel.kt's daysLeft): a pass or a
        // subscription's days left, "day" singular at one.
        "you_days_left" to listOf(
            Case(1, listOf("1"), "1 day left."),
            Case(26, listOf("26"), "26 days left."),
        ),
    )

    private data class Case(val quantity: Int, val args: List<String>, val reads: String)

    @Test
    fun `the shipped counted sentences read correctly at one and at many`() {
        expected.forEach { (name, cases) ->
            cases.forEach { case ->
                assertEquals(
                    "$name at ${case.quantity}",
                    case.reads,
                    ShippedCopy.plural(name, case.quantity, *case.args.toTypedArray()),
                )
            }
        }
    }

    @Test
    fun `every counted sentence is asserted at one and at many`() {
        assertEquals(
            "a counted sentence nobody asserts can regress silently",
            ShippedCopy.plurals.keys.sorted(),
            expected.keys.sorted(),
        )
    }

    /**
     * The one sentence that carries two counts, and the half a plurals cannot agree with.
     *
     * "%1$s of %2$s xStocks priced by Jupiter, the total covers ..." says two numbers out loud.
     * A plurals selects on one of them, and it has to be the one that governs "xStocks", so the
     * clause about the priced ones has to read the same at one as at many. It did not: the wallet
     * that held one priced token beside two unpriced ones read "the total covers those", a plural
     * over a count of one, which is the defect of 2026-09-13 again in the sentence beside it.
     */
    @Test
    fun `the partial total names the priced ones without agreeing with how many they are`() {
        val agrees = setOf("those", "these", "them", "they", "ones")
        ShippedCopy.plurals.getValue("portfolio_priced_partial").forEach { (form, text) ->
            val clause = text.substringAfter("priced by Jupiter, ")
            assertFalse(
                "portfolio_priced_partial/$form says \"$clause\" over a count it cannot select on",
                clause.split(' ', ',', '.').any { it in agrees },
            )
        }
    }

    // ---- The models that count ------------------------------------------------------------------

    private val mint = "TSLAxMint".padEnd(44, '1')

    private fun position(
        symbol: String = "TSLAx",
        ticker: String = "TSLA",
        priceUsd: Double? = 366.17,
    ) = PortfolioPosition(
        symbol = symbol,
        ticker = ticker,
        company = "$ticker xStock",
        mint = mint,
        amountRaw = 201_364_000L,
        decimals = 8,
        multiplier = 1.0,
        priceUsd = priceUsd,
        referencePriceUsd = 365.84,
        poolUsd = 250_000.0,
    )

    private fun portfolio(vararg positions: PortfolioPosition) = PortfolioUiState(
        phase = WalletPhase.CONNECTED,
        account = testAccount(),
        settled = true,
        positions = positions.toList(),
        totalUsd = positions.mapNotNull { it.valueUsd }.takeIf { it.isNotEmpty() }?.sum(),
    )

    @Test
    fun `the portfolio total says one xStock and many xStocks`() {
        assertEquals(
            "the Seeker drew this as a plural over a count of one on 2026-09-13",
            "1 xStock, priced by Jupiter",
            ShippedCopy.render(totalBlock(portfolio(position())).sub),
        )
        assertEquals(
            "2 xStocks, priced by Jupiter",
            ShippedCopy.render(totalBlock(portfolio(position(), position("NVDAx", "NVDA", 182.11))).sub),
        )
    }

    @Test
    fun `the portfolio says how many it could not price, at one and at many`() {
        assertEquals(
            "1 xStock, not priced by Jupiter",
            ShippedCopy.render(totalBlock(portfolio(position(priceUsd = null))).sub),
        )
        assertEquals(
            "2 xStocks, none of them priced by Jupiter",
            ShippedCopy.render(
                totalBlock(portfolio(position(priceUsd = null), position("NVDAx", "NVDA", null))).sub,
            ),
        )
        assertEquals(
            "1 of 2 xStocks priced by Jupiter, the total covers what is priced",
            ShippedCopy.render(totalBlock(portfolio(position(), position("NVDAx", "NVDA", null))).sub),
        )
    }

    /**
     * The two sentences a composable assembles rather than a model.
     *
     * The F-Score caption and the Today strip are drawn straight from `stringResource`, which no
     * JVM test can call, so what is pinned here is the other half: that each reaches counted copy
     * and that the count it selects the form by is the same count it prints. What those sentences
     * then read is covered above, at one and at many, out of the shipped file.
     */
    @Test
    fun `the composed counters select their form by the number they print`() {
        listOf(
            "src/main/java/com/plainticker/mobile/ui/detail/DetailScreen.kt" to listOf(
                "pluralStringResource(R.plurals.detail_fscore_of, fscore.outOf, Fmt.count(fscore.outOf))",
                "pluralStringResource(R.plurals.detail_fscore_a11y, fscore.outOf, it, Fmt.count(fscore.outOf))",
            ),
            // The Stocks Today strip and its two counters left on 2026-09-26 (it repeated Today's own
            // screen); what ListScreen counts now is the analysis age, row and banner alike.
            "src/main/java/com/plainticker/mobile/ui/list/ListScreen.kt" to listOf(
                "pluralStringResource(R.plurals.list_row_age_days, it, Fmt.count(it))",
                "pluralStringResource(R.plurals.list_row_age_days, banner.newestDays, Fmt.count(banner.newestDays))",
            ),
        ).forEach { (path, fragments) ->
            val source = File(module, path).readText().replace("\r\n", "\n")
            fragments.forEach { fragment ->
                assertTrue("$path does not carry `$fragment`", fragment in source)
            }
        }
    }

    @Test
    fun `the digest counts how far off a report is, and no longer opens on what it watched`() {
        val today = LocalDate.of(2026, 9, 13)
        val one = digest(
            DigestInput(today = today, tickers = listOf(watched("TSLA", nextReport = today.plusDays(4)))),
        )
        assertEquals("TSLAx reports in 4 days.", one.text(RealStrings.strings))

        val many = digest(
            DigestInput(
                today = today,
                tickers = listOf(
                    watched("TSLA", nextReport = today.plusDays(2)),
                    watched("NVDA"),
                    watched("MCD"),
                ),
            ),
        )
        assertEquals("TSLAx reports in 2 days.", many.text(RealStrings.strings))
    }

    @Test
    fun `the amount step counts the decimals a token has`() {
        val usdc = SwapToken(KnownMints.USDC, "USDC", 6)
        val tslax = SwapToken(KnownMints.TSLAX, "TSLAx", 8)
        val cent = SwapToken(KnownMints.TSLAX, "CENTx", 1)
        assertEquals("USDC counts 6 decimals", ShippedCopy.render(tooPrecise(SwapLeg(usdc, tslax), "1.0000001")))
        assertEquals("CENTx counts 1 decimal", ShippedCopy.render(tooPrecise(SwapLeg(cent, tslax), "1.02")))
    }

    private fun tooPrecise(leg: SwapLeg, text: String): Copy {
        val funds = SwapFunds(owner = "owner", lamports = 96_000_000L, usdcRaw = 20_200_000L, tokenRaw = 5L)
        val input = SwapAmount.parse(text, leg.input.decimals, 20_200_000L)
        val sheet = requireNotNull(SwapState.Amount(leg, funds, input).sheet(0L, submitSwaps = true))
        return requireNotNull(sheet.notice) { "the amount step said nothing about $text" }
    }

    // ---- The lint ---------------------------------------------------------------------------

    private val countCall = Regex("""\bFmt\.count\(""")
    private val sentenceCall = Regex("""\b(stringResource|words|getString)\s*\($""")
    private val allowCount = "lint-allow count"

    /**
     * A whole number may not be handed to a sentence that cannot agree with it.
     *
     * `Fmt.count` is the one way an integer becomes copy in this app, so a `Fmt.count(...)` sitting
     * directly inside a `stringResource(`, `words(` or `getString(` call is a count a plain string
     * will print beside a noun that was spelled once and cannot change. It belongs in a plurals,
     * reached through `counted(` or `pluralStringResource(`. A numeral with no noun after it is the
     * exception and says so on its own line with `lint-allow count`.
     */
    @Test
    fun `a whole number never reaches a sentence that cannot agree with it`() {
        val findings = sourceFiles().flatMap { (path, text) -> countFindings(path, text) }
        if (findings.isNotEmpty()) {
            fail("a count belongs in counted copy, not in a plain string:\n" + findings.joinToString("\n"))
        }
    }

    @Test
    fun `the count lint catches a seeded violation and honours its opt out`() {
        val bad = "val a = words(R.string.x, Fmt.count(n))\n" +
            "val b = stringResource(R.string.x, sym, Fmt.count(n))\n" +
            "val c = app.getString(id, Fmt.count(n))"
        assertEquals(3, countFindings("seed.kt", bad).size)
        val good = "val d = counted(R.plurals.x, n, Fmt.count(n))\n" +
            "val e = pluralStringResource(R.plurals.x, n, Fmt.count(n))\n" +
            "val f = words(R.string.x, Fmt.count(n)) // lint-allow count: no noun follows it\n" +
            "val g = Fmt.count(n)"
        assertEquals(0, countFindings("seed.kt", good).size)
    }

    private fun sourceFiles(): List<Pair<String, String>> =
        File(module, "src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.path }
            .map { display(it) to it.readText() }
            .toList()

    private fun display(file: File): String =
        "app/" + file.canonicalFile.relativeTo(module).path.replace(File.separatorChar, '/')

    private fun countFindings(path: String, text: String): List<String> {
        val lines = text.split('\n')
        return countCall.findAll(text).mapNotNull { hit ->
            val head = callHead(text, hit.range.first) ?: return@mapNotNull null
            if (!sentenceCall.containsMatchIn(head)) return@mapNotNull null
            val line = text.take(hit.range.first).count { it == '\n' } + 1
            if (allowCount in lines[line - 1]) return@mapNotNull null
            "$path:$line [count in a plain string] ${lines[line - 1].trim()}"
        }.toList()
    }

    /** Everything up to and including the nearest unclosed `(` before [at]: the call this sits in. */
    private fun callHead(text: String, at: Int): String? {
        var depth = 0
        var i = at - 1
        while (i >= 0) {
            when (text[i]) {
                ')' -> depth++
                '(' -> if (depth == 0) return text.substring(0, i + 1) else depth--
            }
            i--
        }
        return null
    }
}
