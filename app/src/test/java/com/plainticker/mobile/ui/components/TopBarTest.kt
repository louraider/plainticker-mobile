package com.plainticker.mobile.ui.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one-line clipping rule (the same rule [AmberTickerRowTest] proves for [AmberTickerRow]):
 * [TopBar]'s title carries `maxLines = 1` with no `overflow`, so anything past what its own
 * unweighted [androidx.compose.foundation.layout.Row] leaves it clips against whatever sits on the
 * right rather than wrapping or ellipsizing. Moving
 * [com.plainticker.mobile.ui.theme.PlainTickerType.wordmark] off Outfit SemiBold 15sp onto
 * Bricolage 700 (2026-09-24, DESIGN.md section 9, "Two corners, refit") changes the glyphs that
 * row has to fit, so the budget is redone here from the real font files rather than assumed
 * unchanged, the same method [com.plainticker.mobile.ui.you.YouModelTest]'s own fact-cell budget
 * and [AmberTickerRowTest]'s own arithmetic proofs use.
 *
 * **Measured against the real font files**, 2026-09-24:
 * - `res/font/bricolage_grotesque.ttf` (fontTools), instantiated at `wght` 700 `wdth` 100 `opsz`
 *   15, the exact variation coordinates [com.plainticker.mobile.ui.theme.PlainTickerType.wordmark]
 *   builds (`bricolage()`, `Type.kt`): "PlainTicker" measures 81.390dp.
 * - the same file at `wght` 600 `opsz` 14, for the widest real action label [TopBar] ever draws
 *   beside the wordmark (`action_watching`, "Watching", `DetailScreen.kt`, drawn through
 *   [com.plainticker.mobile.ui.theme.AmberType.textAction]): 64.372dp. Re-measured 2026-09-26,
 *   when `TextAction` left Outfit SemiBold (61.768dp there). "Watch" (5 characters) and "You" (3
 *   characters), the two other real actions [TopBar] ever draws, are both shorter.
 */
class TopBarTest {

    /**
     * [TopBar]'s own [androidx.compose.foundation.layout.Row] is `fillMaxWidth()` with 20dp of
     * horizontal padding on each side (`.padding(horizontal = 20.dp)`) and
     * `Arrangement.SpaceBetween`; on the Seeker's 400dp frame ([AmberTickerRowTest]'s and
     * [com.plainticker.mobile.ui.you.YouModelTest]'s own frame) that is 360dp of content width,
     * split unweighted between the wordmark and whatever action or meta fragment sits on the
     * right. Neither child wraps or ellipsizes, so the two must never sum past this budget, or
     * they overlap: the clipping bug this rule exists to catch before a device does.
     */
    private val contentWidthDp = 360.0

    private val wordmarkWidthDp = 81.390 // "PlainTicker" at Bricolage 700, opsz 15, wdth 100.
    private val watchingWidthDp = 64.372 // "Watching" (action_watching) at Bricolage 600 opsz 14, 14sp.

    @Test
    fun `the wordmark and the widest real action clear the TopBar's own budget at 1_0x`() {
        assertTrue(
            "\"PlainTicker\" ($wordmarkWidthDp dp) plus \"Watching\" ($watchingWidthDp dp) must " +
                "fit the TopBar's own $contentWidthDp dp content width, or the wordmark clips " +
                "against the action beside it",
            wordmarkWidthDp + watchingWidthDp <= contentWidthDp,
        )
    }

    /**
     * sp text scales by the raw font-scale factor, dp padding does not, the same conservative
     * assumption [AmberTickerRowTest]'s own 1.3x proofs use: the real Android curve compresses
     * small text below the nominal multiplier, so this is worse than the real device, not better.
     */
    @Test
    fun `the same pair still clears the budget at 1_3x, where the clipping rule actually bites`() {
        val wordmarkAt13xDp = wordmarkWidthDp * 1.3
        val watchingAt13xDp = watchingWidthDp * 1.3
        assertEquals(105.807, wordmarkAt13xDp, 0.01)
        assertEquals(83.684, watchingAt13xDp, 0.01)
        assertTrue(
            "grown to 1.3x, \"PlainTicker\" ($wordmarkAt13xDp dp) plus \"Watching\" " +
                "($watchingAt13xDp dp) must still fit the TopBar's own $contentWidthDp dp content " +
                "width",
            wordmarkAt13xDp + watchingAt13xDp <= contentWidthDp,
        )
    }

    // ---- The lockup: the two-corners mark before the wordmark (2026-09-24) ---------------------------

    /**
     * The mark is drawn at the wordmark's cap height. Measured with fontTools against
     * `res/font/bricolage_grotesque.ttf` instantiated at `wght` 700, `wdth` 100, `opsz` 15, the
     * wordmark's own coordinates: `OS/2.sCapHeight` is 660 of 1000 units, and the "P" glyph's own
     * `yMax` is also 660, so the cap height at 15sp is 9.9sp. The mark is sized in sp, so it grows
     * with font scale exactly as the wordmark does; the 6dp gap does not.
     */
    private val markDp = 9.9
    private val gapDp = 6.0

    @Test
    fun `the mark is the wordmark's cap height and sits a fixed gap before it`() {
        assertEquals(9.9f, WordmarkCapHeight.value, 0.001f)
        assertEquals(6f, MarkGap.value, 0.0f)
        assertEquals(markDp, WordmarkCapHeight.value.toDouble(), 0.001)
        assertEquals(gapDp, MarkGap.value.toDouble(), 0.0)
    }

    @Test
    fun `mark, gap, wordmark and the widest real action clear the TopBar's budget at 1_0x`() {
        val used = markDp + gapDp + wordmarkWidthDp + watchingWidthDp
        assertEquals(161.662, used, 0.01)
        assertTrue("the lockup plus \"Watching\" must fit $contentWidthDp dp", used <= contentWidthDp)
        assertEquals("the margin left", 198.338, contentWidthDp - used, 0.01)
    }

    @Test
    fun `mark, gap, wordmark and the widest real action still clear it at 1_3x`() {
        val used = markDp * 1.3 + gapDp + wordmarkWidthDp * 1.3 + watchingWidthDp * 1.3
        assertEquals(208.361, used, 0.01)
        assertTrue("grown to 1.3x the lockup plus \"Watching\" must still fit $contentWidthDp dp", used <= contentWidthDp)
        assertEquals("the margin left", 151.639, contentWidthDp - used, 0.01)
    }

    @Test
    fun `the mark is drawn from the tight crop, tinted with the action token, and says nothing to a screen reader`() {
        val module = listOf(".", "app").map(::File).first { File(it, "src/main/AndroidManifest.xml").isFile }
        val source = com.plainticker.mobile.lint.KotlinScan(
            File(module, "src/main/java/com/plainticker/mobile/ui/components/TopBar.kt").readText(),
        ).code
        assertTrue("the tight crop, never the 108 launcher grid", "R.drawable.ic_brand_mark_tight" in source)
        assertFalse("R.drawable.ic_brand_mark)" in source)
        assertTrue("decorative beside the wordmark", "contentDescription = null" in source)
        assertTrue("follows dark and light through the action token", "tint = colors.actionText" in source)
        assertTrue("the mark comes before the wordmark", source.indexOf("ic_brand_mark_tight") < source.indexOf("text = title"))
    }
}
