package com.plainticker.mobile.ui.swap

import com.plainticker.mobile.lint.KotlinScan
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DT7's contract for the composition itself, read from source the way DetailScreenTest and
 * OnboardingScreenTest read theirs. What the sheet *says* is [SwapSheetModelTest]'s job; this
 * file pins what composing it on a device would prove and a later edit could quietly undo:
 *
 * 1. there is no slippage control, and the sheet computes no number of its own;
 * 2. the receipt's bar is static and the Confirm haptic fires once per landing, keyed on the
 *    signature, so a recomposition cannot buzz twice;
 * 3. the accessibility affordances Pass 6 names are actually passed: focus on open, a polite
 *    live region for the phase, a labelled field, 48dp targets;
 * 4. a submission in flight cannot be dismissed out from under itself.
 */
class SwapSheetTest {

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private val sheetFile = File(module, "src/main/java/com/plainticker/mobile/ui/swap/SwapSheet.kt")
    private val modelFile = File(module, "src/main/java/com/plainticker/mobile/ui/swap/SwapSheetModel.kt")

    private val scan: KotlinScan by lazy {
        assertTrue("SwapSheet.kt is missing", sheetFile.isFile)
        KotlinScan(sheetFile.readText())
    }

    private val modelScan: KotlinScan by lazy {
        assertTrue("SwapSheetModel.kt is missing", modelFile.isFile)
        KotlinScan(modelFile.readText())
    }

    private val stringsXml: String by lazy { File(module, "src/main/res/values/strings.xml").readText() }

    private fun count(marker: String): Int = scan.code.split(marker).size - 1

    /**
     * The file without its previews. The preview data is the 2026-09-12 order written out field
     * for field, `slippageBps` and `rentFeeLamports` included, so a rule about what the
     * composition may mention has to stop where the sample data begins.
     */
    private val composition: String by lazy {
        val end = scan.code.indexOf("private val PreviewActions")
        assertTrue("SwapSheet.kt has no previews section", end > 0)
        scan.code.substring(0, end)
    }

    private fun inComposition(marker: String): Int = composition.split(marker).size - 1

    private fun body(function: String, until: String): String {
        val start = scan.code.indexOf(function)
        assertTrue("SwapSheet.kt has no $function", start >= 0)
        val end = scan.code.indexOf(until, start)
        assertTrue("SwapSheet.kt has no $until after $function", end > start)
        return scan.code.substring(start, end)
    }

    private fun assertOrder(where: String, source: String, markers: List<String>) {
        val indices = markers.map { marker ->
            val index = source.indexOf(marker)
            assertTrue("$where never calls $marker", index >= 0)
            index
        }
        indices.zipWithNext().forEachIndexed { i, (first, second) ->
            assertTrue("in $where, ${markers[i]} must come before ${markers[i + 1]}", first < second)
        }
    }

    // ---- Anatomy -----------------------------------------------------------------------------

    @Test
    fun `the sheet is drawn in the order the canvas fixes`() {
        assertOrder(
            "SwapSheetBody",
            body("internal fun ColumnScope.SwapSheetBody(", "private fun SwapActions.of("),
            listOf(
                "ConfirmOnLanded(",
                "content.debug",
                "content.result",
                "Title(content",
                "content.field",
                "Field(",
                "field.balance",
                "content.notice",
                "content.costNotice",
                "AmberFactRows(",
                "content.primary",
                "content.secondary",
                "content.footnote",
            ),
        )
    }

    @Test
    fun `a finished attempt leads with its result and a sheet in progress leads with the pair`() {
        // 2026-09-24: the receipt led with a small static "Landed" live bar and a monospace
        // figure, and the founder could not tell whether the swap had worked. Every finished
        // attempt (landed, failed, not yet known) now leads with one result block, which takes
        // the focus the sheet asks for on open; a sheet still in progress leads with the pair.
        val branch = body("val result = content.result", "content.field?.let")
        assertOrder(
            "the lead branch",
            branch,
            listOf("if (result != null)", "ResultBlock(result = result, pair = content.title, lead = lead, colors = colors)"),
        )
        assertOrder("the sheet branch", branch, listOf("Title(content, lead, actions, colors)", "Phase(it, Modifier)"))
        assertEquals("the old receipt headline is gone", 0, count("Received("))
    }

    @Test
    fun `the result is one merged heading, announced once as a polite live region`() {
        val block = body("private fun ResultBlock(", "private fun ResultMark(")
        assertTrue("the block is not one node", "semantics(mergeDescendants = true)" in block)
        assertTrue("the result is not a heading", "heading()" in block)
        assertTrue("the result is never announced", "liveRegion = LiveRegionMode.Polite" in block)
        assertTrue("TalkBack hears the model's words, not the drawing", "contentDescription = announcement" in block)
        assertTrue("the result does not take the sheet's focus", "modifier = lead" in block)
    }

    @Test
    fun `the result's motion is Amber's own tokens, and gated so it can be read without it`() {
        val block = body("private fun ResultBlock(", "private fun ResultMark(")
        assertTrue("motion is not gated", "rememberMotionEnabled()" in block)
        // With animator scale 0 the first frame is the settled one, not a snap a frame later.
        assertTrue("the settled state is not the starting state without motion", "mutableStateOf(!motion)" in block)
        assertEquals("both animations snap without motion", 2, block.split("else snap()").size - 1)
        assertTrue("the ring is not on the settle spring", "if (motion) RingSettle" in block)
        assertTrue(
            "the settle spring is not Amber's no-bounce medium-low one",
            "spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)" in scan.code,
        )
        assertTrue("the figure is not on the quick token", "tween(durationMillis = QUICK_MILLIS, easing = LinearOutSlowInEasing)" in block)
        assertTrue("the quick token is not 150ms", "private const val QUICK_MILLIS = 150" in scan.code)
    }

    @Test
    fun `failure is drawn in the caution colour and never with a glyph`() {
        val block = body("private fun ResultBlock(", "private fun ResultMark(")
        assertTrue(
            "a failure's headline is not the caution colour",
            "if (result.tone == ResultTone.Failed) colors.stateCaution else colors.textPrimary" in block,
        )
        val mark = body("private fun ResultMark(", "private fun DebugBand(")
        assertTrue("the mark is not drawn", "Canvas(" in mark)
        assertEquals("the mark has no text of its own", 0, mark.split("Text(").size - 1)
        assertTrue("the failure mark is not the caution colour", "color = caution" in mark)
        assertTrue("the landing ring does not close with the settle", "sweepAngle = 360f * progress" in mark)
        // QA of 1.3.20: a static open ring beside "No wallet connected" read as a frozen spinner.
        val failed = mark.substringAfter("ResultTone.Failed ->").substringBefore("ResultTone.Pending ->")
        assertTrue("the failure ring is not closed", "sweepAngle = 360f," in failed)
        assertTrue("the failure mark carries no caution sign", "drawLine(" in failed && "drawCircle(" in failed)
    }

    @Test
    fun `no figure on the sheet is set in monospace any more`() {
        // DESIGN.md section 3: Amber's numbers are Bricolage with tnum. The Instrument styles this
        // sheet used (bigValue, sheetTitle, factValueAt, the mono meta) are gone from it entirely;
        // the one mono face left is AmberFactRows' own, for the signature, an on-chain key.
        listOf("PlainTickerType", "JetBrainsMono", "bigValue", "sheetTitle", "FactGrid(").forEach {
            assertEquals("SwapSheet.kt still draws $it", 0, count(it))
        }
        assertTrue("the hero figure is not Amber's large figure", "style = AmberType.figureLarge" in scan.code)
        assertTrue("only the copied cell is an identifier", "identifier = copied != null" in scan.code)
    }

    @Test
    fun `the sheet has one surface, one grid, one field and two buttons`() {
        assertEquals("one modal surface", 1, count("onDismissRequest ="))
        assertEquals("one run of Amber rows", 1, count("AmberFactRows("))
        assertEquals("one text field", 1, count("Field("))
        // Instrument's PrimaryButton/SecondaryButton retired in this pass in favour of Amber's own
        // anatomy (docs/design-research-2026-09-21.md section 5.5), the same restyle VoteSheet.kt
        // and PassSheet.kt already went through: the markers below name the components actually on
        // screen now, not the ones this sheet drew before this fix.
        assertEquals("one primary and one secondary", 1, count("AmberPrimaryAction("))
        assertEquals(1, count("AmberSecondaryAction("))
        // The receipt's "Swap back" is drawn through that same one call site, never a second
        // primary: a sheet asks for one decision at a time.
        assertTrue("listOfNotNull(content.secondary, content.extra).forEach" in scan.code)
        // The direction, and on a receipt "View on Solscan" (judges' review, 2026-09-27).
        assertEquals("two text actions: the direction and the explorer link", 2, count("TextAction("))
    }

    // ---- No slippage, and no arithmetic ------------------------------------------------------

    @Test
    fun `the sheet has no slippage control of any kind`() {
        listOf("Slider", "slippage", "Slippage", "Bps", "Stepper", "Checkbox", "Switch", "RadioButton")
            .forEach { assertEquals("the sheet draws a $it", 0, inComposition(it)) }
        // The only slippage in the whole product's copy is the worst case under the estimate,
        // which is a consequence stated, not a control offered.
        val mentions = Regex("""<string name="(\w+)">[^<]*slippage[^<]*</string>""")
            .findAll(stringsXml).map { it.groupValues[1] }.toList()
        assertEquals(listOf("swap_worst_case"), mentions)
    }

    @Test
    fun `no number is computed in the composition`() {
        listOf("Fmt.", "toDouble()", "/ 100", "* 100", "BigDecimal", "outAmountRaw", "allInCostPct", "lamports")
            .forEach {
                assertEquals("SwapSheet.kt computes $it; that belongs in SwapSheetModel.kt", 0, inComposition(it))
            }
        // The one thing it reads that the model cannot is the wall clock, which is not a claim
        // about money, and it is read in exactly one function.
        val clock = body("private fun swapClock(", "private const val TICK_MILLIS")
        assertEquals(
            "the wall clock is read outside swapClock",
            count("System.currentTimeMillis()"),
            clock.split("System.currentTimeMillis()").size - 1,
        )
    }

    @Test
    fun `every sentence comes from the model, and the two the screen names are fixed`() {
        val named = Regex("""R\.string\.(\w+)""").findAll(scan.code).map { it.groupValues[1] }.toSet()
        assertEquals(
            "the sheet may name only the label of the one action a composable owns",
            setOf("receipt_copy_signature"),
            named,
        )
        named.forEach { assertTrue("R.string.$it is not declared", """name="$it"""" in stringsXml) }
        // Everything else arrives as a CostNotice or a Copy the model picked.
        assertTrue("the cost placeholder carries its own string", "CostNotice.AtTap.text" in scan.code)
        assertTrue("the skeleton is announced as loading", "CostNotice.Loading.text" in scan.code)
    }

    @Test
    fun `every string the model names is declared in strings xml`() {
        val named = Regex("""R\.string\.(\w+)""").findAll(modelScan.code).map { it.groupValues[1] }.distinct().toList()
        assertTrue("the model should name every sentence the sheet draws, found ${named.size}", named.size >= 25)
        named.forEach { assertTrue("R.string.$it is not declared", """name="$it"""" in stringsXml) }
    }

    // ---- The receipt ---------------------------------------------------------------------------

    @Test
    fun `exactly one Confirm haptic, keyed on the signature so it cannot fire twice`() {
        assertEquals("one haptic on the whole surface", 1, count("performHapticFeedback("))
        assertEquals("and it is Confirm", 1, count("HapticFeedbackType.Confirm"))
        val effect = body("private fun ConfirmOnLanded(", "private fun leadFocus(")
        assertTrue("the effect is not keyed on the signature", "LaunchedEffect(signature)" in effect)
        assertTrue("a null signature must not buzz", "if (signature != null)" in effect)
        assertEquals("nothing on this screen makes a sound", 0, count("SoundEffect"))
    }

    @Test
    fun `the receipt's bar is static because the model says so, not because the sheet decided`() {
        assertTrue("the bar is not handed the model's answer", "live = phase.live" in scan.code)
        assertEquals("the sheet never sets live itself", 0, count("live = true"))
        assertTrue("the receipt is not marked static in the model", "live = false" in modelScan.code)
    }

    @Test
    fun `only the signature is copied, and it is copied whole`() {
        assertEquals(1, count("setClipEntry("))
        assertTrue("the cell copies whatever the model named", "copies" in scan.code)
        assertTrue("the model hands over the whole signature", "copies = fill.signature" in modelScan.code)
        assertEquals("the fragment on screen is not what reaches the clipboard", 0, count("shortKey"))
    }

    // ---- Accessibility, plan section 13 Pass 6 ---------------------------------------------------

    @Test
    fun `the sheet takes focus when it opens`() {
        val focus = body("private fun leadFocus(", "private fun SheetCell.fact(")
        assertTrue("no focus requester", "FocusRequester()" in focus)
        assertTrue("the requester is never attached to a node", "focusRequester(opened)" in focus)
        assertTrue("a node that is not focusable cannot take focus", ".focusable()" in focus)
        assertTrue("focus is never asked for", "opened.requestFocus()" in focus)
        assertTrue("a lost requester must not crash a screen with money on it", "runCatching" in focus)
        assertEquals("the sheet leads with exactly one node", 1, count("val lead = leadFocus(takeFocus)"))
        assertTrue(
            "the gallery draws the receipt inline and must not pull focus to it",
            "takeFocus: Boolean = true" in scan.code,
        )
    }

    @Test
    fun `the phase is a polite live region announced by what it is`() {
        assertTrue("the phase is not drawn as the live bar", "LiveBar(" in scan.code)
        assertTrue(
            "the region is announced by the ticking label, so a reader is interrupted every second",
            "announcement = phase.announcement.text()" in scan.code,
        )
        // The announcement the model gives is the bare phase, with no seconds in it.
        listOf("swap_a11y_opening", "swap_a11y_quoting", "swap_a11y_wallet", "swap_a11y_landing").forEach {
            assertTrue("R.string.$it is not what the model announces", "R.string.$it" in modelScan.code)
            val text = Regex("""<string name="$it">([^<]*)</string>""").find(stringsXml)?.groupValues?.get(1)
            assertTrue("$it carries an argument, so it changes every second", text != null && "%" !in text)
        }
    }

    @Test
    fun `the amount field is labelled and asks for a decimal keyboard`() {
        val field = body("content.field?.let { field ->", "content.notice?.let")
        assertTrue("the field has no label", "label = field.label.text()" in field)
        assertTrue("the Max action is not wired", "onAction = actions.onMax" in field)
        assertTrue("a money field on a words keyboard", "KeyboardType.Decimal" in field)
    }

    @Test
    fun `every target is a component that is already 48dp`() {
        // No raw clickable anywhere: taps go through TextAction, the buttons and the one cell
        // that names what it copies, each of which carries its own role and minimum size.
        assertEquals("a raw clickable has no role and no minimum size", 0, inComposition(".clickable("))
        assertEquals(0, inComposition("onLongClick"))
    }

    // ---- What cannot be taken back -----------------------------------------------------------------

    @Test
    fun `a submission in flight cannot be dismissed`() {
        val host = body("fun SwapSheet(", "internal fun ColumnScope.SwapSheetBody(")
        assertTrue(
            "dismissing while landing would cancel the call carrying the transaction",
            "if (state !is SwapState.Landing) actions.onClose()" in host,
        )
        // A modal sheet hides first and asks afterwards, so refusing the request alone would
        // still leave the sheet gone with the machine running and the receipt out of reach.
        assertTrue("the drag itself has to be refused", "confirmValueChange" in host)
        assertTrue(
            "and refused only while landing",
            "target != SheetValue.Hidden || !landing.value" in host,
        )
        assertTrue("the lock reads the current state", "rememberUpdatedState(state is SwapState.Landing)" in host)
    }

    @Test
    fun `the debug band is drawn from the model's flag and in every state`() {
        // DebugBand(it, colors): the band now also reads the theme-following AmberColors every
        // other piece of this sheet does (money must read correctly in light too).
        assertTrue("the band is not the model's", "content.debug?.let { DebugBand(it, colors) }" in scan.code)
        assertEquals("one band, above both anatomies", 1, count("DebugBand(it, colors)"))
        assertTrue("the default is the build flag", "submitSwaps: Boolean = BuildConfig.SUBMIT_SWAPS" in scan.code)
        assertEquals("the sheet never reads the flag itself", 1, count("BuildConfig.SUBMIT_SWAPS"))
    }

    @Test
    fun `the sheet previews at the three frames the design pass reads`() {
        assertTrue("no previews on the finished sheet", "@InstrumentPreviews" in scan.code)
        listOf(
            "SwapAmountPreview", "SwapWalletPreview", "SwapShortfallPreview",
            "SwapReceiptPreview", "SwapDebugSignedPreview",
        ).forEach { assertTrue("$it is missing", it in scan.code) }
    }
}
