package com.plainticker.mobile.ui.you

import com.plainticker.mobile.ui.ShippedCopy
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The clipping rule (DESIGN.md section 4) for the You cabinet (2026-09-25), measured rather than
 * assumed, the same way `AmberTickerRowTest` and `SwapResultFitTest` prove their rows.
 *
 * Every one-line slot the cabinet draws, and nothing else: the hero's buttons, every text action a
 * [CabinetRow] or the hero can carry, the device rows' figure, and the wallet key. Every other text
 * on You (identity, headline, lines, labels, values, subs, messages) carries no `maxLines` and
 * wraps, so it cannot clip.
 *
 * **Method.** fontTools 4.63 against the bundled files themselves, 2026-09-25, summed advance
 * widths (no kerning, which only ever narrows a real render, so every number here is the safe
 * direction), grown to 1.3x for the larger text setting. dp budgets do not grow with the text
 * setting, only the glyphs do, so 1.3x is the harder case and each table states both.
 * - Buttons: `res/font/bricolage_grotesque.ttf` instantiated at `AmberType.button`'s coordinates,
 *   `wght` 600, `wdth` 100, `opsz` 16, at 16sp.
 * - Text actions: `res/font/outfit_semibold.ttf` at 14sp, the face `TextAction` actually draws in
 *   (`PlainTickerType.textAction`, Outfit 14/600; DESIGN.md 4.3 keeps it on purpose), not Bricolage.
 * - The device figure: Bricolage at `AmberType.figureRow`'s `wght` 600, `opsz` 18, 18sp, with the
 *   font's own `tnum` substitutions applied.
 * - The wallet key: `res/font/jetbrains_mono_regular.ttf` at 15sp.
 *
 * **Geometry, on the Seeker's 400dp frame.**
 * - Hero button content: 400 − 2×16 (group inset) − 2×20 (card padding) − 2×20 (button padding) = 288dp.
 * - A row's content: 400 − 2×16 (group inset) − 2×16 (row padding) = 336dp.
 * - A text action takes its label plus `TextActionPadding`'s 16dp start inset.
 */
class CabinetFitTest {

    private val heroButtonDp = 400.0 - 2 * 16.0 - 2 * 20.0 - 2 * 20.0
    private val rowContentDp = 400.0 - 2 * 16.0 - 2 * 16.0
    private val textActionInsetDp = 16.0

    /** Hero button labels, Bricolage 600 opsz 16 at 16sp: (1.0x, 1.3x) in dp. */
    private val buttonWidths = mapOf(
        "Get Pro" to (57.168 to 74.318),
        "Extend Pro" to (84.224 to 109.491),
        "Sign in with Google" to (146.960 to 191.048),
        "Signing in" to (75.520 to 98.176),
    )

    /** Text action labels, Outfit SemiBold at 14sp: (1.0x, 1.3x) in dp. */
    private val textActionWidths = mapOf(
        "Connect wallet" to (94.878 to 123.341),
        "Sign in" to (41.482 to 53.927),
        "Sign out" to (50.946 to 66.230),
        "Cancel" to (43.624 to 56.711),
        "Disconnect" to (70.644 to 91.837),
        "Connect" to (53.214 to 69.178),
        "Copy" to (33.166 to 43.116),
        "Copied" to (45.080 to 58.604),
        "Refresh" to (50.134 to 65.174),
        "Enable" to (43.792 to 56.930),
        "Get Pro" to (49.308 to 64.100),
        "Extend Pro" to (70.266 to 91.346),
        "Show" to (34.566 to 44.936),
        "Hide" to (29.372 to 38.184),
        "Read license" to (78.778 to 102.411),
        "Hide license" to (75.152 to 97.698),
        // Promo redeem (task promo-redeem, 2026-09-26): "Have a code?" and "Apply" alongside You's
        // own Cancel, measured the same way as every row above.
        "Have a code?" to (84.616 to 110.001),
        "Apply" to (37.296 to 48.485),
    )

    /** The wallet's short key, nine monospace characters, JetBrains Mono Regular 15sp. */
    private val keyWidth = 81.0 to 105.3

    /** Device row figures, Bricolage 600 opsz 18 tnum at 18sp. "999,999" is the synthetic ceiling. */
    private val figureWidths = mapOf(
        "0" to (11.034 to 14.344),
        "200" to (33.102 to 43.033),
        "9,999" to (47.376 to 61.589),
        "999,999" to (69.444 to 90.277),
    )

    private fun width(table: Map<String, Pair<Double, Double>>, text: String, what: String): Pair<Double, Double> =
        table[text] ?: error(
            "\"$text\" ($what) has no measured width; remeasure it with fontTools against the bundled " +
                "font at this slot's exact instance before shipping it",
        )

    @Test
    fun `every hero button label fits the button, at 1_0x and 1_3x`() {
        listOf("you_action_get_pro", "you_action_extend", "account_sign_in", "account_signing_in").forEach { name ->
            val text = ShippedCopy.strings.getValue(name)
            val (at10, at13) = width(buttonWidths, text, name)
            assertTrue("\"$text\" is $at10 dp at 1.0x, past the $heroButtonDp dp button", at10 <= heroButtonDp)
            assertTrue("\"$text\" is $at13 dp at 1.3x, past the $heroButtonDp dp button", at13 <= heroButtonDp)
        }
    }

    /** Every text action label You can draw, by resource name. */
    private val textActionNames = listOf(
        "action_connect_wallet", "you_action_sign_in", "account_sign_out", "you_action_cancel",
        "action_disconnect", "you_action_connect", "you_action_copy", "you_action_copied",
        "action_refresh", "action_enable", "you_action_get_pro", "you_action_extend",
        "you_action_show", "you_action_hide", "action_read_license", "action_hide_license",
        "promo_action_have_code", "promo_action_apply",
    )

    @Test
    fun `every text action leaves the key its own line beside it, at 1_3x`() {
        // The tightest column a row can have is the one beside its widest inline action; the one
        // unbreakable thing a column holds is the wallet's short key (an email wraps by character
        // and is never clipped). So the proof is: the widest inline action plus its inset, plus the
        // key at 1.3x, fits the row's content width.
        textActionNames.forEach { name ->
            val text = ShippedCopy.strings.getValue(name)
            val (at10, at13) = width(textActionWidths, text, name)
            assertTrue("\"$text\" at 1.0x leaves no line for the key", at10 + textActionInsetDp + keyWidth.first <= rowContentDp)
            assertTrue("\"$text\" at 1.3x leaves no line for the key", at13 + textActionInsetDp + keyWidth.second <= rowContentDp)
        }
    }

    @Test
    fun `two actions on their own line fit the row, at 1_3x`() {
        // Copy (or Copied) with Disconnect; Sign out with Cancel.
        listOf(
            listOf("Copied", "Disconnect"), listOf("Copy", "Disconnect"), listOf("Sign out", "Cancel"),
            listOf("Apply", "Cancel"),
        ).forEach { pair ->
            val total = pair.sumOf { width(textActionWidths, it, it).second + textActionInsetDp }
            assertTrue("$pair need $total dp at 1.3x, past the row's $rowContentDp dp", total <= rowContentDp)
        }
    }

    @Test
    fun `connect wallet fits under the hero's button, at 1_3x`() {
        val (_, at13) = width(textActionWidths, ShippedCopy.strings.getValue("action_connect_wallet"), "hero")
        assertTrue(at13 + 2 * textActionInsetDp <= heroButtonDp + 2 * 20.0)
    }

    /**
     * The device rows' label words, `AmberType.body` (Bricolage 400, `opsz` 15, 15sp), 1.3x, dp.
     * The label wraps word by word, so each word only has to fit the column on its own.
     */
    private val deviceLabelWordAt13 = mapOf(
        "Swaps" to 61.133, "recorded" to 83.577, "Votes" to 52.904, "cast" to 39.117, "Stocks" to 62.985, "watched" to 78.975,
    )

    @Test
    fun `the device figure leaves every label word a whole line, at 1_3x`() {
        // The figure is unweighted and one line, 12dp after the weighted label column.
        val widestFigure = figureWidths.values.maxOf { it.second }
        val column = rowContentDp - 12.0 - widestFigure
        listOf("you_fact_swaps", "you_fact_votes", "you_fact_watched").forEach { name ->
            ShippedCopy.strings.getValue(name).split(" ").forEach { word ->
                val w = deviceLabelWordAt13[word] ?: error("\"$word\" ($name) has no measured width")
                assertTrue("\"$word\" ($w dp) does not fit the $column dp column", w <= column)
            }
        }
        listOf(0, 200, 9_999, 999_999).forEach { n ->
            val facts = deviceFacts(YouUiState(swapsRecorded = n))
            width(figureWidths, facts.swaps, "Fmt.count($n)")
        }
    }

    @Test
    fun `the wallet key is nine characters, the width measured above`() {
        assertEquals(9, linkedWalletKeys(com.plainticker.mobile.prefs.SignedInAccount(null, null, listOf("4Nd1mBQtrMJVYVfKf2PJy9NZUZdTAsp7D4xWLs4gDB4T"))).single().length)
    }

    /**
     * The headline wraps, so it is not a clip risk; this pins the one-line claim YouScreen.kt makes
     * for it at 1.0x. Measured at `AmberType.figureLarge`'s own instance (`wght` 700, `opsz` 34)
     * drawn at 28sp, which is what `HeroHeadlineStyle` renders: "Pro until 30 May 2030" is the
     * widest date the headline can print (every day, month and year to 2039), 292.992dp at 1.0x
     * and 380.890dp at 1.3x, where it wraps to a second line.
     */
    @Test
    fun `the widest headline is one line at 1_0x and wraps at 1_3x`() {
        val cardContent = 400.0 - 2 * 16.0 - 2 * 20.0
        assertTrue(292.992 <= cardContent)
        assertTrue(380.890 > cardContent)
        assertTrue(ShippedCopy.strings.getValue("you_hero_pro_until").startsWith("Pro until "))
    }

    @Test
    fun `nothing on You draws a card grid or a one-line value beside a sibling other than the measured slots`() {
        val module = listOf(".", "app").map(::File).first { File(it, "src/main/AndroidManifest.xml").isFile }.canonicalFile
        val screen = File(module, "src/main/java/com/plainticker/mobile/ui/you/YouScreen.kt").readText()
        assertEquals("no fact grid on You", 0, Regex("""\bFactGrid\(""").findAll(screen).count())
        assertEquals(
            "the only softWrap = false on You is the device figure",
            1,
            Regex("""softWrap = false""").findAll(screen).count(),
        )
    }

    // ---- CabinetRow's own vertical budget (QA 2026-09-26, D4: "Sign out" drew as "Sian out") -----

    /**
     * Not a re-run of the width tests above: a text action can clip vertically too, if the row that
     * hosts it ever gave its content less height than the real font needs. `CabinetRow`'s own doc
     * comment has the story: the actual cause of D4 was one level up (`YouContent`'s `LazyColumn`
     * clipping a partially visible last row at its own viewport edge, in the one state whose `Plan`
     * group is short enough to put "Sign out" near that edge), but this pins that the row's own
     * budget is not also part of the problem, and that a future edit cannot quietly shrink it back
     * to something that would be.
     *
     * fontTools 4.63 against `res/font/outfit_semibold.ttf`, 2026-09-26, `hhea`/`OS2` (typo metrics
     * govern here: `fsSelection`'s `USE_TYPO_METRICS` bit is set, and typo and hhea agree) at 14sp,
     * the exact instance [PlainTickerType.textAction] draws: ascent 14.0dp, descent 3.64dp, so a
     * natural (ascent + descent) line height of 17.64dp. The "g" glyph's own bounding box reaches
     * 2.996dp below the baseline, less than the font's own 3.64dp descent metric.
     */
    private val outfitSemiBoldAscent14Sp = 14.0
    private val outfitSemiBoldDescent14Sp = 3.64
    private val outfitSemiBoldNaturalLineHeight14Sp = outfitSemiBoldAscent14Sp + outfitSemiBoldDescent14Sp
    private val gGlyphBelowBaseline14Sp = 2.996

    @Test
    fun `the text action's own declared line height already clears the real descender, before any row padding`() {
        // PlainTickerType.textAction: 14sp, 20sp line height, LineHeightStyle(Center, Trim.None), so
        // the (20 - 17.64) = 2.36dp of extra space this style adds over the font's own natural line
        // height splits evenly above and below the natural ascent/descent box.
        val declaredLineHeight = 20.0
        assertTrue(
            "the declared line height must exceed the font's own natural one, or centring it adds " +
                "no margin at all",
            declaredLineHeight > outfitSemiBoldNaturalLineHeight14Sp,
        )
        val halfLeading = (declaredLineHeight - outfitSemiBoldNaturalLineHeight14Sp) / 2
        // Margin from the real "g" ink to the bottom of the declared 20sp line box: the half-leading
        // below the natural descent line, plus the slack the font's own descent metric already
        // carries past this particular glyph's real ink.
        val margin = halfLeading + (outfitSemiBoldDescent14Sp - gGlyphBelowBaseline14Sp)
        assertTrue("\"g\" must not reach the line box's own bottom edge", margin > 0.0)
        assertEquals(1.824, margin, 0.01)
    }

    @Test
    fun `CabinetRow's own padding was widened from 10dp, and TextAction's box adds real margin on top of it`() {
        val module = listOf(".", "app").map(::File).first { File(it, "src/main/AndroidManifest.xml").isFile }.canonicalFile
        val source = File(module, "src/main/java/com/plainticker/mobile/ui/you/YouScreen.kt").readText()
        assertTrue(
            "RowVerticalPadding must be declared and used for both the row's own top and bottom " +
                "padding, not a literal that could drift from it",
            "private val RowVerticalPadding = 12.dp" in source,
        )
        assertTrue(
            "the row must apply RowVerticalPadding on both edges, not a stray literal",
            ".padding(start = RowPadding, end = RowPadding, top = RowVerticalPadding, bottom = RowVerticalPadding)" in source,
        )
        val rowVerticalPaddingDp = 12.0
        assertTrue("this must be wider than the 10dp this row shipped D4 with", rowVerticalPaddingDp > 10.0)

        // TextAction's own trailing padding below its 20sp line box (TextActionPadding's bottom).
        val textActionBottomPaddingDp = 14.0
        // From the fontTools measurement above: the real "g" ink already sits 1.824dp inside the
        // declared line box's own bottom edge before either padding is even counted.
        val inkToLineBoxMarginDp = 1.824
        val totalMarginBelowInkDp = inkToLineBoxMarginDp + textActionBottomPaddingDp + rowVerticalPaddingDp
        // A comfortable, explicit budget: this is what a clip landing at this row's own trailing
        // edge would have to cross before it could ever reach real ink, which CabinetRow's own doc
        // comment is careful not to claim as a guarantee against a clip that lands inside the row
        // instead of at its edge (the actual mechanism behind D4, owned by the LazyColumn above it).
        assertEquals(27.824, totalMarginBelowInkDp, 0.01)
        assertTrue("the widened padding must not have narrowed this margin", totalMarginBelowInkDp > 25.824)
    }
}
