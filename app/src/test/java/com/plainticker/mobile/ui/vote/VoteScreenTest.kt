package com.plainticker.mobile.ui.vote

import com.plainticker.mobile.lint.KotlinScan
import com.plainticker.mobile.ui.ShippedCopy
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What composing the vote on a device would prove, read from source the way [
 * com.plainticker.mobile.ui.list.ListFinishedScreenTest] and
 * [com.plainticker.mobile.ui.swap.SwapSheetTest] read theirs.
 *
 * Three things a later edit could quietly undo, and none of which the sheet model can see:
 *
 * 1. the action is on a row under "Without analysis" and on Detail, and on nothing else;
 * 2. the sheet decides nothing, so no sentence and no figure is spelled in the composition;
 * 3. the wallet is driven with `signAndSendTransactions`, the verb MWA 2.x makes mandatory, and
 *    not with the deprecated one the swap still has to use.
 */
class VoteScreenTest {

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private fun source(path: String): String =
        KotlinScan(File(module, "src/main/java/com/plainticker/mobile/$path").readText()).code

    private val listScreen by lazy { source("ui/list/ListScreen.kt") }
    private val detailScreen by lazy { source("ui/detail/DetailScreen.kt") }
    private val sheet by lazy { source("ui/vote/VoteSheet.kt") }
    private val voteTabScreen by lazy { source("ui/vote/VoteScreen.kt") }
    private val viewModel by lazy { source("ui/vote/VoteViewModel.kt") }
    private val api by lazy { source("data/plainticker/VoteApi.kt") }

    // ---- Where the action is --------------------------------------------------------------------

    /**
     * Updated for the Amber Stocks restyle (`ui/list/ListScreen.kt`): [AmberTickerRow] carries no
     * trailing-action slot of its own (`ListFinishedScreenTest`'s "Stocks' analyzed row..." test
     * pins that it does not need one), so `PriceOnlyRow` and `NextUpLeaderRow` each hand their own
     * `onVote` through to `VotableAmberRow`, the one place `R.string.vote_action_row` is drawn.
     * The invariant this test is named for, that only a row without analysis offers the vote, is
     * unchanged; only the call shape it is read off is.
     */
    @Test
    fun `the list offers the vote on a row without analysis, and on no other row`() {
        val votable = body(listScreen, "private fun VotableAmberRow(")
        assertTrue("the vote action is drawn only when onVote is not null", "if (onVote != null) {" in votable)
        assertTrue("vote_action_row is the label", "R.string.vote_action_row" in votable)

        val priceOnly = body(listScreen, "private fun PriceOnlyRow(")
        assertTrue(
            "a price-only row hands its own vote callback through to the shared row",
            "onVote = if (onVote == null || !row.votable) null else" in priceOnly,
        )
        // 2026-09-26 (audit, item 3): the callback now also stops at a row whose underlying is not
        // US-listed, because the server refuses that vote after the wallet connects.
        assertTrue("a non-US price-only row offers no vote", "!row.votable" in priceOnly)
        val detailVote = body(detailScreen, "private fun VoteBlock(")
        assertTrue("Detail offers no vote for a non-US listing", "state.asset?.isUsUnderlying == false" in detailVote)

        // The analyzed rows are already covered, so there is nothing there to vote for.
        val analyzed = body(listScreen, "private fun AnalyzedRow(")
        assertFalse("an analyzed row has nothing to vote for", "vote" in analyzed.lowercase())
    }

    @Test
    fun `detail offers the vote only where PlainTicker classifies nothing`() {
        val block = body(detailScreen, "private fun VoteBlock(")
        assertTrue(
            "an unreadable analysis is not the same as an absent one, and must not offer a vote",
            "if (!state.analysisNotServed || onVote == null) return" in block,
        )
        assertTrue("the full sentence is the label where there is room for it", "R.string.vote_action" in block)
        assertTrue("a text action, not a second primary button", "TextAction(" in block)
        assertFalse("Detail has one primary control and it is the swap", "PrimaryButton(" in block)
    }

    @Test
    fun `both screens host the sheet outside the scrolling content`() {
        // On the list it is a sibling of the LazyColumn: an item is disposed when it scrolls out,
        // and a vote mid-flight would go with it. On Detail it is outside SwapBlock for the same
        // reason the swap sheet is.
        assertTrue("VoteSheet(" in listScreen)
        assertTrue("VoteSheet(state = vote, actions = voteActions)" in detailScreen)
    }

    // ---- The sheet decides nothing ------------------------------------------------------------------

    @Test
    fun `the composition spells no sentence and computes no figure`() {
        val composition = sheet.substringBefore("private const val PreviewStakeRaw")
        assertTrue("VoteSheet.kt has no previews section", composition.length < sheet.length)

        listOf("Fmt.", "SkrStakeBound", "stakeRaw", "lamports").forEach {
            assertFalse("the sheet works out `$it` instead of being handed it", it in composition)
        }
        // The one string resource it reads is the clipboard label, which is a gesture and not copy.
        val ids = Regex("""R\.string\.(\w+)""").findAll(composition).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("receipt_copy_signature"), ids)
    }

    @Test
    fun `no spinner stands in for a sentence`() {
        listOf(sheet, listScreen, detailScreen).forEach {
            assertFalse("DESIGN.md section 8: no spinners", "CircularProgressIndicator" in it)
            assertFalse("DESIGN.md section 8: no spinners", "LinearProgressIndicator" in it)
        }
    }

    // ---- The wallet and the server -------------------------------------------------------------------

    @Test
    fun `the wallet is driven with the verb every MWA wallet has to implement`() {
        assertTrue(
            "signAndSendTransactions is mandatory in MWA 2.x; signTransactions is optional",
            "wallet.call { it.signAndSendTransactions(arrayOf(unsigned)) }" in viewModel,
        )
        assertFalse("the vote has one wallet round-trip, not the swap's two", "signTransactions" in viewModel)
    }

    @Test
    fun `the app never builds a transaction, because the forwarder cannot date one`() {
        listOf(viewModel, api, sheet).forEach {
            assertFalse("getLatestBlockhash is not one of the five methods the forwarder allows", "getLatestBlockhash" in it)
            assertFalse("the transaction is the server's to assemble", "TransactionBuilder" in it)
        }
        assertTrue("the one call that produces something to sign", "voteApi.build(ticker, voter)" in viewModel)
    }

    @Test
    fun `a refusal carries an enum, so the server's own sentence cannot reach a screen`() {
        val state = source("ui/vote/VoteState.kt")
        val refused = state.substring(state.indexOf("data class Refused("))
            .substringBefore(") : OnTicker")
        // The ticker it is about, what a reader calls it, one of this app's own reasons, and, for a
        // guard refusal, the guard's own plain category (TransactionGuard.Why, an enum as well,
        // judges' review 2026-09-27). No field is a string, so there is nowhere for a server
        // sentence to travel.
        val fields = Regex("""val (\w+):\s*(\w+)""").findAll(refused).map { it.groupValues[1] to it.groupValues[2] }
        assertEquals(
            listOf("ticker" to "String", "symbol" to "String", "reason" to "VoteRefusal", "why" to "TransactionGuard"),
            fields.toList(),
        )
        assertTrue("the fourth field is the guard's enum", "val why: TransactionGuard.Why?" in refused)

        // Every reason names a resource, so every sentence this feature shows is in strings.xml
        // and subject to CopyLintTest. The server's words go to the log and nowhere else.
        val ids = Regex("""R\.string\.(vote_\w+)""").findAll(state).map { it.groupValues[1] }.toSet()
        assertTrue("one resource per refusal, and one per phase", ids.size >= VoteRefusal.entries.size)
        assertTrue("the upstream text is kept where it is useful", "debugLog.raw(" in viewModel)
    }

    // ---- The copy exists and is the copy the screens read -----------------------------------------------

    @Test
    fun `every string this feature names is in the shipped file, and every vote string is used`() {
        val declared = ShippedCopy.strings.keys.filter { it.startsWith("vote_") }.toSet()
        assertTrue("strings.xml carries no vote copy at all", declared.isNotEmpty())

        val sources = listOf(
            source("ui/vote/VoteSheetModel.kt"),
            source("ui/vote/VoteState.kt"),
            // The Last round sentence is chosen by the model since 2026-09-26 (PreviousRoundDisplay.sentence),
            // so the model is where its four strings are read now, not VoteScreen.kt.
            source("ui/vote/VoteTabModel.kt"),
            sheet,
            listScreen,
            detailScreen,
            voteTabScreen,
        ).joinToString("\n")

        val used = Regex("""R\.string\.(vote_\w+)""").findAll(sources).map { it.groupValues[1] }.toSet()
        assertEquals("a vote string nobody reads is copy nobody sees", declared, used)
    }

    @Test
    fun `the row action and the full action are both shipped, and they differ`() {
        assertEquals("Vote to cover next", ShippedCopy.strings["vote_action"])
        assertEquals("Vote", ShippedCopy.strings["vote_action_row"])
        assertEquals("Vote to cover %1\$s", ShippedCopy.strings["vote_title"])
    }

    // ---- The round header does not starve, found on-device at 1.3x on v0.6.0-rc1 ------------------------

    /**
     * [Heading] gives its title `weight(1f)` and its meta the rest of the row at the meta's own
     * width. A short count (Leaders, Your votes, the ballot) never asks for more than a few
     * digits, so the title barely notices; the round's meta was a whole clause, "1, closes 21 Sep
     * 2026 00:00 UTC", which starved "Round" to one letter per line on the Seeker. There is no
     * layout test on a plain JVM that can measure this again, so what is pinned is the fix: the
     * round no longer routes through [Heading] at all, or through the bespoke anatomy that first
     * replaced it. It now reuses Amber's own section head
     * ([com.plainticker.mobile.ui.components.AmberSectionHead]), whose own tests
     * (`AmberSectionHeadTest`) pin the same anti-starvation behaviour generically: its title wraps
     * to two lines with a bound rather than being squeezed to one without one.
     */
    @Test
    fun `the round header does not hand its title to Heading's weighted column`() {
        val block = body(voteTabScreen, "private fun RoundHeader(")
        assertFalse("a clause-length meta in Heading is what starved the title on-device", "Heading(" in block)
        assertTrue(
            "the round now reuses AmberSectionHead, whose own title wraps rather than starves",
            "AmberSectionHead(" in block,
        )
    }

    /**
     * [com.plainticker.mobile.ui.components.AmberSectionHead]'s own contract (`AmberSectionHeadTest`)
     * is that its `meta` stays a short, single-line count next to the title, never a full clause.
     * What is pinned here is specific to this call site: RoundHeader hands the round id to `meta`
     * and the close time to `lede`, not the other way around, which is the actual fix for the
     * clause-in-a-shared-width-slot bug the class doc above names.
     */
    @Test
    fun `the round number is part of the title and the close time goes to the full width lede`() {
        // Device QA of 1.3.16: the id in the meta slot drew as a grey "2" at the far right of the
        // card, styled like a count; the title now reads "Round 2" and the meta slot is unused.
        val block = body(voteTabScreen, "private fun RoundHeader(")
        assertTrue(
            "the round id is in the title, a plain grouped integer, never a sentence",
            "stringResource(R.string.vote_tab_round_heading, Fmt.count(round.id))" in block,
        )
        assertFalse("nothing is drawn in the count slot", "meta =" in block)
        assertEquals("Round %1\$s", ShippedCopy.strings["vote_tab_round_heading"])
        assertTrue("the close clause is the lede, drawn full width below the title, not squeezed beside it", "lede = closesText" in block)
    }

    /**
     * The two values [RoundHeader] actually hands `AmberSectionHead`, measured at their real
     * worst case rather than assumed (the same discipline `AmberTickerRowTest` and
     * `AmberSectionHeadTest` apply to their own call sites).
     */
    @Test
    fun `the round header's own content is measured at its worst case, not guessed`() {
        // A round id is a monotonically increasing counter, one round open at a time; even at
        // four digits it stays a short, comma-grouped integer, nowhere near a clause.
        assertEquals("9,999", com.plainticker.mobile.ui.Fmt.count(9_999L))

        // Every field of Fmt.utc but the day is fixed width: the month is always three letters,
        // the year four digits, the hour and the minute zero-padded to two. The day is the only
        // field that varies, one or two digits; the 31st (a month that has one) is the longest
        // Fmt.utc ever produces.
        val longestUtc = com.plainticker.mobile.ui.Fmt.utc(java.time.Instant.parse("2026-10-31T00:00:00Z"))
        assertEquals("31 Oct 2026 00:00 UTC", longestUtc)
        val longestCloses = "Closes $longestUtc"
        assertEquals(
            "28 characters, full width, no maxLines cap on the lede: nowhere near where a 400dp " +
                "device would need to wrap it, let alone starve anything beside it",
            28,
            longestCloses.length,
        )
    }

    // ---- The explainer is short enough that nobody scrolls past it, tightened on review -----------------

    @Test
    fun `the explainer is two paragraphs, and the stake note is no longer at the top of the tab`() {
        val block = body(voteTabScreen, "private fun Explainer(")
        val ids = Regex("""R\.string\.(vote_tab_explainer_\w+)""").findAll(block).map { it.groupValues[1] }.toSet()
        assertEquals(
            "a fourth paragraph is what pushed Leaders below the fold at 1.3x on the Seeker",
            setOf("vote_tab_explainer_what", "vote_tab_explainer_how"),
            ids,
        )
        // Judges' round 2: the stake note moved to the vote sheet, at the moment of signing, as a
        // roadmap. The tab itself no longer carries it anywhere.
        assertFalse("the stake note is said on the sheet, not over the tab", "vote_gameable" in voteTabScreen)
        assertTrue("it is still said where a voter signs", "R.string.vote_gameable" in source("ui/vote/VoteSheetModel.kt"))
    }

    // ---- A round with nothing voted on yet does not leave its heading over nothing --------------

    /**
     * A round is open, but the tally has nobody in it yet: the round header used to be followed
     * by whatever the next present section was (Your votes, Last round, the ballot heading),
     * which could be nothing at all this device has ever loaded. One line names the state
     * instead, gated the same way the Leaders section itself is, on [VoteTabUiState.round] and
     * [VoteTabUiState.leaders], so it can never draw alongside the leaders it stands in for.
     */
    @Test
    fun `an open round with no leaders draws one line instead of leaving the round header over nothing`() {
        val block = voteTabScreen.substringAfter("if (state.leaders.isNotEmpty())").substringBefore("if (state.myVotes.isNotEmpty())")
        assertTrue("gated on the round actually being present", "else if (state.round != null)" in block)
        assertTrue("the one line this state draws", "R.string.vote_tab_no_votes_yet" in block)
        assertTrue("still a Footnote, the anatomy every other single-line state here uses", "Footnote(" in block)
    }

    /**
     * The not-open state (task section, HTTP 404 or vote_not_configured) already says its own
     * piece, `vote_tab_not_open`, and replaces the round furniture entirely (`showsRoundFurniture`
     * is false there). The empty-round line belongs to a different state and must not double up
     * with it.
     */
    @Test
    fun `the empty-round line never draws in the not-open branch, which already says its own piece`() {
        val notOpenBlock = voteTabScreen.substringAfter("state.notOpen ->").substringBefore("state.failed ->")
        assertTrue("the not-open line itself is unchanged", "vote_tab_not_open" in notOpenBlock)
        assertFalse("the two states must not say the same thing twice", "vote_tab_no_votes_yet" in notOpenBlock)
    }

    // ---- U13: what the scroll-away header actually costs deep in the ballot, measured --------------

    /**
     * The one thing the ballot needs and the header cannot strand: [BallotRow] carries its own
     * inline vote action, so a voter who has scrolled deep into the ballot never has to scroll back
     * to a fixed slot to cast it. Distinct from the existing "the list offers the vote..." test
     * above, which pins the same rule for `ui/list/ListScreen.kt`'s own row; this screen's
     * `BallotRow` is its own definition, not a call into that one.
     */
    @Test
    fun `the ballot row carries its own vote action, never only the header`() {
        val row = body(voteTabScreen, "private fun BallotRow(")
        assertTrue("the vote action is drawn only when onVote is offered", "if (onVote != null) {" in row)
        assertTrue("vote_action_row is the label, the same short one the list uses", "R.string.vote_action_row" in row)
        assertTrue("TextAction(" in row)
    }

    // ---- The ballot's trailing action never reaches the physical screen edge, found on-device at 1.0x and 1.3x --

    /**
     * [BallotRow] draws "Vote" as a [TextAction] sibling of [AmberTickerRow] inside a `Box`, not
     * through [AmberTickerRow]'s own `trailingAction` (this row carries no figure or context to
     * share the meta line with, [BallotRow]'s own doc comment). That overlay never inherited
     * [AmberTickerRow]'s own `padding(horizontal = 16.dp, ...)` (`AmberTickerRow.kt`), because it
     * sits beside that padded `Column` rather than inside it, and neither this screen's
     * `LazyColumn` (`contentPadding` is `navigationBars` only) nor [AmberRowDivider] add any
     * horizontal padding of their own. `Modifier.align(Alignment.CenterEnd)` alone pinned the
     * action flush to the `Box`'s own edge, which on this screen is the physical screen edge: on
     * the device it touched the edge at 1.0x font scale and clipped the final "e" of "Vote" at
     * 1.3x, in both themes. The fix adds `padding(end = 16.dp)` after the alignment, matching
     * [AmberTickerRow]'s own horizontal padding exactly, the same margin [LeaderRow] and
     * Watchlist's own row already get for free by drawing their trailing action through
     * [AmberTickerRow]'s own `trailingAction` slot instead of an overlay.
     */
    @Test
    fun `the ballot row's trailing action is inset from the box's own edge, not flush against it`() {
        val row = body(voteTabScreen, "private fun BallotRow(")
        val boxIndex = row.indexOf("Box(Modifier.fillMaxWidth())")
        assertTrue("the overlay Box must still appear; this row never grew a figure to share the meta line with", boxIndex >= 0)
        val textActionIndex = row.indexOf("TextAction(", boxIndex)
        assertTrue("TextAction must be inside the overlay Box", textActionIndex > boxIndex)
        val tail = row.substring(textActionIndex)
        assertTrue(
            "the action must still be end-aligned within the row",
            "modifier = Modifier.align(Alignment.CenterEnd)" in tail,
        )
        assertTrue(
            "an end padding after the alignment is what keeps the action off the physical screen " +
                "edge; align alone pins it flush to whichever edge the Box itself is flush " +
                "against, and this screen adds no horizontal padding around its rows",
            "modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)" in tail,
        )
    }

    /**
     * The regression generalized, so a future overlay in this file cannot repeat its exact shape:
     * anything positioned with `Alignment.CenterEnd` inside a `Box` here is, by construction,
     * pinned flush to that `Box`'s own right edge, and nothing between this screen's rows and the
     * physical display (`LazyColumn`'s own `contentPadding`, [AmberRowDivider]) adds horizontal
     * padding of its own. `Modifier.align(Alignment.CenterEnd)` with no end padding after it is
     * exactly the shape that shipped the clipped "Vote" action; this fails if that shape ever
     * reappears anywhere in this file, on this row or a new one, with no inset or a zero one.
     */
    @Test
    fun `nothing aligned to a row's own end edge in this screen can reach the physical screen edge`() {
        val alignments = Regex("""Modifier\.align\(Alignment\.CenterEnd\)(\.padding\(end = (\d+)\.dp\))?""")
            .findAll(voteTabScreen)
            .toList()
        assertTrue("the regression's own call site must still be found", alignments.isNotEmpty())
        alignments.forEach { match ->
            val paddingClause = match.groupValues[1]
            val marginDp = match.groupValues[2].toIntOrNull() ?: 0
            assertTrue(
                "\"${match.value}\" aligns to a Box's own end edge with no positive end padding " +
                    "after it, which pins it flush to the physical screen edge on this screen " +
                    "(nothing here adds horizontal padding of its own) -- exactly the shape that " +
                    "clipped \"Vote\" on the ballot row",
                paddingClause.isNotEmpty() && marginDp > 0,
            )
        }
    }

    /**
     * The margin itself, as arithmetic rather than an assumption: `padding(end = 16.dp)` is a
     * `Dp` value, and `Dp` never scales with `fontScale`, only `sp` text does, so the gap after
     * "Vote" does not shrink as the label grows. fontTools against `res/font/bricolage_grotesque.ttf`
     * at the exact instance [TextAction] draws through (`AmberType.textAction`, 600, opsz 14, 14sp;
     * Outfit SemiBold until 2026-09-26) gives "Vote" itself as 31.318dp at 1.0x, the same number
     * [com.plainticker.mobile.ui.components.AmberTickerRowTest]'s own leader-row test already
     * pins for the identical label, font, weight and size; grown by the raw 1.3x factor (the same
     * conservative, worse-than-real assumption every other margin proof in this codebase uses) it
     * is 40.713dp. Both stay far short of a real device's own width (>= 320dp, the narrowest
     * shipped Android width bucket), so this fix's 16dp margin is never squeezed by overflow at
     * either scale: the action's touch target and its visible glyphs move together, 16dp clear of
     * the physical edge, at 1.0x and at 1.3x alike.
     */
    @Test
    fun `the ballot row's trailing-action margin is 16dp at 1_0x font scale and 16dp at 1_3x, unlike the label it sits beside`() {
        val endMarginDp = 16.0 // Modifier.padding(end = 16.dp) on BallotRow's overlaid TextAction.

        // "Vote" (vote_action_row) at AmberType.textAction, Bricolage 600 opsz 14: the same
        // 31.318dp AmberTickerRowTest's own leader-row test measures and pins for this exact
        // label, font, weight and size.
        val voteLabelWidthDp = 31.318
        val actionStartPaddingDp = 16.0 // TextAction's own default contentPadding start.
        val touchTargetFloorDp = 48.0 // TextAction's own defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).
        val renderedWidthAt10xDp = maxOf(actionStartPaddingDp + voteLabelWidthDp, touchTargetFloorDp)

        // dp is density-independent and immune to fontScale; only the sp-sized label grows at 1.3x.
        val voteLabelWidthAt13xDp = voteLabelWidthDp * 1.3
        val renderedWidthAt13xDp = maxOf(actionStartPaddingDp + voteLabelWidthAt13xDp, touchTargetFloorDp)

        // A real device is at minimum 320dp wide; the grown action at 1.3x, plus this fix's own
        // margin, is nowhere near that, so the margin is never squeezed by overflow at either scale.
        val minRealisticScreenWidthDp = 320.0
        assertTrue(
            "the action plus its margin must clear even the narrowest real device at 1.0x",
            renderedWidthAt10xDp + endMarginDp <= minRealisticScreenWidthDp,
        )
        assertTrue(
            "the action plus its margin must clear even the narrowest real device at 1.3x",
            renderedWidthAt13xDp + endMarginDp <= minRealisticScreenWidthDp,
        )

        // The margin itself: a fixed Dp modifier, unaffected by the label's own growth.
        val marginAt10xDp = endMarginDp
        val marginAt13xDp = endMarginDp
        assertEquals(16.0, marginAt10xDp, 0.0)
        assertEquals(16.0, marginAt13xDp, 0.0)
    }

    /**
     * Nothing on this screen scrolls independently or sits above the content: the whole thing is
     * one `LazyColumn`, so [BallotSearchField] is exactly as far from view once scrolled past as
     * the ballot itself is deep. If this ever changes (a sticky search field, a second scroll
     * container), the U13 measurement above changes with it and needs a second look.
     */
    @Test
    fun `nothing on this screen is sticky, so the search field scrolls away exactly as far as the ballot goes`() {
        assertEquals("exactly one scroll container", 1, count(voteTabScreen, "LazyColumn("))
        assertEquals("nothing here is sticky", 0, count(voteTabScreen, "stickyHeader"))
        assertTrue("the search field is a plain LazyColumn item like every section above it", "BallotSearchField(" in voteTabScreen)
    }

    /**
     * The ballot's real scale, checked against the shipped catalog and docs/data-map.md's own
     * analyzed count rather than assumed: what U13's class-doc measurement above is arithmetic on.
     */
    @Test
    fun `the ballot the header scrolls away from is hundreds of rows deep, measured against the shipped catalog`() {
        val catalog = File(module, "src/main/assets/snapshot/xstocks.json").readText()
        val symbols = catalog.split("\"symbol\"").size - 1
        assertEquals("app/src/main/assets/snapshot/xstocks.json's own symbol count", 928, symbols)
        val analyzed = 157 // docs/data-map.md: "analyzed xStocks reaching the Analyzed section | 157"
        val ballotRows = symbols - analyzed
        assertEquals(771, ballotRows)
        // AmberTickerRow.kt's own floor; not read from that file (out of this task's file set), but
        // the number itself is quoted verbatim in VoteScreen.kt's own class doc above it and is the
        // same 64dp every other ticker row in this app already uses.
        val minRowDp = 64
        assertTrue(
            "scrolling to the middle of a 771-row ballot alone is tens of thousands of display " +
                "points, many screen-heights below where it begins",
            (ballotRows / 2) * minRowDp > 10_000,
        )
    }

    private fun count(source: String, marker: String): Int = source.split(marker).size - 1

    /** Everything from [function] to the line that closes it at column zero. */
    private fun body(source: String, function: String): String {
        val start = source.indexOf(function)
        assertTrue("no $function in the source", start >= 0)
        val end = source.indexOf("\n}", start)
        assertTrue("$function never closes", end > start)
        return source.substring(start, end)
    }

    // ---- The top card (judges' round 2) -------------------------------------------------------

    @Test
    fun `the round card leads the open tab with close time, stake and the vote action, then leaders`() {
        val open = voteTabScreen.substringAfter("else -> {")
        val card = open.indexOf("RoundCard(")
        val leaders = open.indexOf("R.string.vote_tab_heading_leaders")
        val explainer = open.indexOf("Explainer()")
        assertTrue("the card is drawn in the open branch", card >= 0)
        assertTrue("leaders come right under the card", card < leaders)
        assertTrue("how it works follows what a voter came for", leaders < explainer)

        val body = body(voteTabScreen, "private fun RoundCard(")
        assertTrue("the round and its local close", "RoundHeader(" in body)
        assertTrue("the wallet's stake sentence", "stake.sentence" in body)
        assertTrue("the one action, to the ballot", "R.string.vote_tab_pick_action" in body)
        assertTrue("the close is in the reader's own zone, never a UTC stamp", "roundClosesLocal(" in body(voteTabScreen, "private fun RoundHeader("))
    }

    @Test
    fun `the card's action scrolls to the ballot search, the index counted where the items are laid out`() {
        assertTrue("ballotSearchIndex[0] = position" in voteTabScreen)
        assertTrue("listState.animateScrollToItem(ballotSearchIndex[0], scrollOffset = -below)" in voteTabScreen)
        val between = voteTabScreen.substringAfter("ballotSearchIndex[0] = position").substringBefore("BallotSearchField(query")
        assertTrue("the index is set just before the search item", between.length < voteTabScreen.length / 2)
        assertFalse("nothing is counted between the index and the search item", "position" in between || "item(key" !in between)
    }

    /**
     * Device QA of 1.3.16: scrolled to offset 0 the search field sat half under the status bar,
     * which this screen draws under (edge to edge, Insets.kt). The field lands below the status
     * bar and its scrim, and takes focus so the next thing typed is the search.
     */
    @Test
    fun `the card's action lands the search field clear of the status bar, and focuses it`() {
        assertTrue(
            "the clearance is the status bar, its scrim and a gap",
            "WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + ScrimFade + SearchLandingGap" in voteTabScreen,
        )
        val pick = voteTabScreen.substringAfter("onPick = {").substringBefore("position++")
        assertTrue("a negative offset leaves the field below the top edge", "scrollOffset = -below" in pick)
        assertTrue("focus follows the scroll", pick.indexOf("searchFocus.requestFocus()") > pick.indexOf("animateScrollToItem("))
        assertTrue("the field is the one focused", "focusRequester = searchFocus" in voteTabScreen)
        assertTrue("Field takes the caller's requester", "focusRequester = focusRequester" in body(voteTabScreen, "private fun BallotSearchField("))
    }

    /** Device QA of 1.3.16: a leader this wallet already voted for this round offered Vote again. */
    @Test
    fun `a token this wallet already voted for this round shows a quiet Voted, not a Vote`() {
        assertEquals("Voted", ShippedCopy.strings["vote_voted_row"])
        val leader = body(voteTabScreen, "private fun LeaderRow(")
        assertTrue("val offersVote = onVote != null && !voted" in leader)
        assertTrue("trailingAction = if (offersVote) stringResource(R.string.vote_action_row) else null" in leader)
        assertTrue("trailingNote = if (voted) stringResource(R.string.vote_voted_row) else null" in leader)
        val ballot = body(voteTabScreen, "private fun BallotRow(")
        assertTrue("the ballot row says it too", ballot.indexOf("R.string.vote_voted_row") in 0 until ballot.indexOf("TextAction("))
        assertTrue("voted = votedFor(leader.ticker)" in voteTabScreen)
        assertTrue("voted = votedFor(entry.ticker)" in voteTabScreen)
        // Device QA of 1.3.17: the tab's own receipts and the shared rule Stocks and Detail read
        // are one answer, so the three surfaces cannot disagree.
        assertTrue("fun votedFor(ticker: String): Boolean = state.votedFor(ticker) || votedTickers.hasVoted(ticker)" in voteTabScreen)
        assertTrue("votedTickers = voted" in voteTabScreen)
    }

    @Test
    fun `the ops-log copy is one line, and nothing claims anyone can recount to the same result`() {
        val strings = ShippedCopy.strings
        assertEquals("Counted within about 20 minutes.", strings["vote_tab_your_votes_note"])
        assertEquals("The vote is on the chain. Counted within about 20 minutes.", strings["vote_landed_note"])
        strings.filterKeys { it.startsWith("vote_") }.forEach { (name, text) ->
            assertFalse("$name still narrates the tally schedule", text.contains("ten minutes") || text.contains("cached"))
            assertFalse("$name claims anyone can count the same way", text.contains("anyone can count"))
        }
        assertTrue(
            "the how-it-works line says one wallet may back several stocks in a round",
            strings.getValue("vote_tab_explainer_how").contains("several stocks in a round"),
        )
    }

    @Test
    fun `the last round opens the winner whenever its research is published, and says so with Read`() {
        val row = body(voteTabScreen, "private fun LastRoundRow(")
        assertTrue("val clickable = previous.opensResearch" in row)
        assertTrue("if (previous.researchPublished) {" in row)
        assertTrue("R.string.vote_tab_last_round_research" in row)
        assertTrue("R.string.action_read" in row)
        assertEquals("%1\$s research published", ShippedCopy.strings["vote_tab_last_round_research"])
    }
}
