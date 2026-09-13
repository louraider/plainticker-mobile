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
                "content.isReceipt",
                "Title(content",
                "content.field",
                "Field(",
                "field.balance",
                "content.notice",
                "content.costNotice",
                "FactGrid(",
                "content.primary",
                "content.secondary",
                "content.footnote",
            ),
        )
    }

    @Test
    fun `the receipt leads with what happened and the sheet leads with the pair`() {
        val receiptBranch = body("if (content.isReceipt) {", "content.field?.let")
        assertOrder("the receipt branch", receiptBranch, listOf("Phase(it, lead)", "Received(it)"))
        assertOrder("the sheet branch", receiptBranch, listOf("Title(content, lead, actions)", "Phase(it, Modifier)"))
    }

    @Test
    fun `the sheet has one surface, one grid, one field and two buttons`() {
        assertEquals("one modal surface", 1, count("onDismissRequest ="))
        assertEquals("one grid", 1, count("FactGrid("))
        assertEquals("one text field", 1, count("Field("))
        assertEquals("one primary and one secondary", 1, count("PrimaryButton("))
        assertEquals(1, count("SecondaryButton("))
        assertEquals("the direction is the only text action", 1, count("TextAction("))
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
        val focus = body("private fun leadFocus(", "private fun SheetCell.factCell(")
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
        assertTrue("the band is not the model's", "content.debug?.let { DebugBand(it) }" in scan.code)
        assertEquals("one band, above both anatomies", 1, count("DebugBand(it)"))
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
