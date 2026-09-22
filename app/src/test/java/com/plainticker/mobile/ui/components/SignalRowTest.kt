package com.plainticker.mobile.ui.components

import com.plainticker.mobile.lint.KotlinScan
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One F-Score signal. "Primary right" was always a colour statement (`colors.textPrimary` against
 * `colors.textSecondary`/tertiary), not a size one, and this restyle keeps it that way rather than
 * reaching for a tabular numeral style on a word that is never a number. Nothing here can render
 * on a plain JVM, so what is pinned is source, the way [AmberTickerRowTest] and [AmberFigureTest]
 * read their own components.
 */
class SignalRowTest {

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private val source: String by lazy {
        KotlinScan(File(module, "src/main/java/com/plainticker/mobile/ui/components/SignalRow.kt").readText()).code
    }

    // ---- Amber's own type, not Instrument's -----------------------------------------------------

    @Test
    fun `name and word read AmberType, never PlainTickerType`() {
        assertTrue("style = AmberType.body" in source)
        assertTrue("style = AmberType.context" in source)
        assertFalse("PlainTickerType" in source)
    }

    @Test
    fun `the answer word never turns on tnum, since yes, no and n_a are words, not numerals`() {
        assertFalse("AmberType.figureRow" in source)
        assertFalse("AmberType.figureInline" in source)
        assertFalse("AmberType.figureLarge" in source)
    }

    // ---- Colour still carries the pass, fail and n/a distinction -----------------------------------

    @Test
    fun `passed drives both colours the same way it always did`() {
        assertTrue("if (passed) colors.textSecondary else muted" in source)
        assertTrue("if (passed) colors.textPrimary else muted" in source)
    }

    @Test
    fun `nothing here is pinned to a literal dp width`() {
        assertFalse(".width(" in source)
    }

    // ---- Measured, not guessed: fontTools against the real font file, 2026-09-22 ------------------

    /**
     * `name` already carried no clipping risk (`weight(1f)`, no `maxLines`), so this proves the
     * larger 15sp `body` face does not reopen it. `body` and `context`, each at their own
     * `wght`/`wdth`/`opsz`. Content width on a 400dp frame, this row's own 20dp side padding
     * twice, is 360dp, split by a 12dp `Arrangement.spacedBy`.
     */
    @Test
    fun `name keeps a comfortable single-line margin even against the F-Score's longest real signal`() {
        val contentWidthDp = 360.0
        val gapDp = 12.0
        val widestWordWidthDp = 23.506 // "yes" at context's 14sp/400, the widest of the three answers.
        val nameBudgetDp = contentWidthDp - gapDp - widestWordWidthDp
        assertEquals(324.494, nameBudgetDp, 0.01)

        // signal_cfo_positive's English string, "Operating cash flow positive" (29 characters),
        // at body's 15sp/400: the longest of the nine F-Score signal names.
        val widestNameWidthDp = 200.295
        assertTrue(
            "the longest real signal name ($widestNameWidthDp dp) must clear the worst-case " +
                "budget ($nameBudgetDp dp) on one line",
            widestNameWidthDp <= nameBudgetDp,
        )

        val scale = 1.3
        assertTrue(
            "the longest real signal name must still clear the budget at 1.3x font scale",
            widestNameWidthDp * scale <= contentWidthDp - gapDp - widestWordWidthDp * scale,
        )
    }
}
