package com.plainticker.mobile.ui.components

import com.plainticker.mobile.lint.KotlinScan
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The primary action (docs/design-research-2026-09-21.md section 5.5): a real control, never the
 * only forward action drawn as a text link, the one rule this task's brief says survives every
 * pass of the redesign because it was never aesthetic. Read from source the way every other Amber
 * component test in this package is: nothing here can render on a plain JVM.
 */
class AmberPrimaryActionTest {

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private val source: String by lazy {
        KotlinScan(File(module, "src/main/java/com/plainticker/mobile/ui/components/AmberPrimaryAction.kt").readText()).code
    }

    @Test
    fun `it is a real Material Button, not TextAction wearing a button's clothes`() {
        assertTrue("AmberPrimaryAction(" in source)
        assertTrue("Button(" in source)
        assertFalse(
            "the rule this task's brief states is that no state's only forward action is a text link",
            "TextAction(" in source,
        )
    }

    @Test
    fun `enabled fills with actionFill and reads in actionOnFill, matching the anatomy table`() {
        assertTrue("containerColor = colors.actionFill" in source)
        assertTrue("contentColor = colors.actionOnFill" in source)
    }

    @Test
    fun `disabled never falls back to a text link either, it stays a bordered button`() {
        assertTrue("fun AmberDisabledAction(" in source)
        val disabled = source.substring(source.indexOf("fun AmberDisabledAction("))
        assertTrue("Button(" in disabled)
        assertTrue("BorderStroke(1.dp, colors.border)" in disabled)
        assertTrue("enabled = false" in disabled)
    }

    @Test
    fun `56dp height and 16dp radius match research 5-5's anatomy table for Amber specifically`() {
        assertTrue(".height(56.dp)" in source)
        assertTrue("RoundedCornerShape(16.dp)" in source)
    }

    @Test
    fun `the label never wraps unbounded and always has an overflow strategy`() {
        assertTrue(source.count { it == '\n' } > 0)
        val calls = Regex("""Text\(([^)]*)\)""").findAll(source).map { it.groupValues[1] }.toList()
        assertTrue("expected at least the enabled and disabled label Text calls", calls.size >= 2)
        calls.forEach { call ->
            assertTrue("maxLines = 1" in call)
            assertTrue("TextOverflow.Ellipsis" in call)
        }
    }
}
