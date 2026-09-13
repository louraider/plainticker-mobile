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
    private val viewModel by lazy { source("ui/vote/VoteViewModel.kt") }
    private val api by lazy { source("data/plainticker/VoteApi.kt") }

    // ---- Where the action is --------------------------------------------------------------------

    @Test
    fun `the list offers the vote on a row without analysis, and on no other row`() {
        assertTrue(
            "the price-only row is the one that takes the vote",
            "trailingAction = if (onVote == null) null else stringResource(R.string.vote_action_row)" in listScreen,
        )
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
        // The ticker it is about, what a reader calls it, and one of this app's own reasons.
        // There is no fourth field, so there is nowhere for a server sentence to travel.
        val fields = Regex("""val (\w+):\s*(\w+)""").findAll(refused).map { it.groupValues[1] to it.groupValues[2] }
        assertEquals(
            listOf("ticker" to "String", "symbol" to "String", "reason" to "VoteRefusal"),
            fields.toList(),
        )

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
            sheet,
            listScreen,
            detailScreen,
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

    /** Everything from [function] to the line that closes it at column zero. */
    private fun body(source: String, function: String): String {
        val start = source.indexOf(function)
        assertTrue("no $function in the source", start >= 0)
        val end = source.indexOf("\n}", start)
        assertTrue("$function never closes", end > start)
        return source.substring(start, end)
    }
}
