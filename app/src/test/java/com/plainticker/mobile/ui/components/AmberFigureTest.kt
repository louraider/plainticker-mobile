package com.plainticker.mobile.ui.components

import com.plainticker.mobile.lint.KotlinScan
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The number with its context (docs/design-research-2026-09-21.md section 5.5): the product's
 * whole argument, and the slot the old design got most wrong. Read from source the way
 * [AmberTickerRowTest] reads its own component: nothing here can render on a plain JVM.
 */
class AmberFigureTest {

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private val source: String by lazy {
        KotlinScan(File(module, "src/main/java/com/plainticker/mobile/ui/components/AmberFigure.kt").readText()).code
    }

    // ---- Colour is never a second direction cue (DESIGN.md section 7, research section 4) -----

    @Test
    fun `the figure is always actionText amber, never colour-coded by tone`() {
        // FactTone.Caution is allowed to colour the context sentence; it must never reach the
        // figure itself ("never on a number", research 5.3).
        val figureStart = source.indexOf("style = AmberType.figureLarge")
        assertTrue("no figureLarge style in the source", figureStart >= 0)
        val figureText = source.substring(figureStart, source.indexOf(")", figureStart))
        assertTrue("color = colors.actionText" in figureText)
        assertFalse("stateCaution" in figureText)
    }

    @Test
    fun `caution, when it applies, colours the context line and nothing else`() {
        assertTrue("colors.stateCaution" in source)
        val cautionUse = source.substring(source.indexOf("if (tone == FactTone.Caution)"))
        assertTrue(cautionUse.startsWith("if (tone == FactTone.Caution) colors.stateCaution else colors.textSecondary"))
    }

    // ---- No fixed width anywhere: the trap this task warns about does not apply here -----------

    @Test
    fun `nothing in this card is pinned to a literal dp width`() {
        assertFalse(".width(" in source)
    }

    @Test
    fun `the figure is single line and tabular, and the context line carries no maxLines cap so it wraps`() {
        val contextStart = source.indexOf("if (context != null)")
        assertTrue(contextStart >= 0)
        val figureBlock = source.substring(source.indexOf("text = figure,"), contextStart)
        assertTrue("maxLines = 1" in figureBlock)
        assertTrue("softWrap = false" in figureBlock)
        // Bounded by the next top-level branch (a literal, indentation-insensitive marker) rather
        // than by the block's own closing brace, so this does not depend on exact whitespace.
        val cardBranchStart = source.indexOf("if (card)", contextStart)
        assertTrue(cardBranchStart > contextStart)
        val contextCall = source.substring(contextStart, cardBranchStart)
        assertFalse(
            "a capped maxLines on context would force a full sentence like " +
                "\"22 of 160 analyzed can be tracked today\" to clip instead of wrap",
            "maxLines" in contextCall,
        )
    }

    // ---- The card is a fluid width, not a fixed one -----------------------------------------

    @Test
    fun `the card variant fills the available width rather than a literal size`() {
        assertTrue("fillMaxWidth()" in source)
        assertTrue("RoundedCornerShape(28.dp)" in source)
    }
}
