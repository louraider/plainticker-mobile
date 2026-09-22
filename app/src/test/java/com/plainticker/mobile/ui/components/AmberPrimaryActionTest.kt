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
        assertTrue("expected at least the enabled, disabled and secondary label Text calls", calls.size >= 3)
        calls.forEach { call ->
            assertTrue("maxLines = 1" in call)
            assertTrue("TextOverflow.Ellipsis" in call)
        }
    }

    // ---- AmberSecondaryAction, retiring Instrument's SecondaryButton --------------------------

    @Test
    fun `AmberSecondaryAction is a real bordered button, transparent fill, textPrimary content`() {
        assertTrue("fun AmberSecondaryAction(" in source)
        val fn = source.substring(source.indexOf("fun AmberSecondaryAction("))
        assertTrue("OutlinedButton(" in fn)
        assertTrue("containerColor = Color.Transparent" in fn)
        assertTrue("contentColor = colors.textPrimary" in fn)
        assertTrue("BorderStroke(1.dp, colors.border)" in fn)
        assertFalse("a secondary action is a real button, not a text link", "TextAction(" in fn)
    }

    @Test
    fun `AmberSecondaryAction shares the primary action's 56dp, 16dp-radius frame`() {
        val fn = source.substring(source.indexOf("fun AmberSecondaryAction("), source.indexOf("fun AmberActionFrame("))
        assertTrue("AmberActionFrame(" in fn)
        assertTrue("shape = AmberActionShape" in fn)
    }

    // ---- The focus ring every enabled action keeps, the one thing the retired ButtonFrame drew --

    @Test
    fun `AmberPrimaryAction and AmberSecondaryAction both wrap their Button in the shared focus frame`() {
        val primary = source.substring(source.indexOf("fun AmberPrimaryAction("), source.indexOf("fun AmberDisabledAction("))
        val secondary = source.substring(source.indexOf("fun AmberSecondaryAction("), source.indexOf("fun AmberActionFrame("))
        listOf(primary, secondary).forEach { fn ->
            assertTrue("AmberActionFrame(" in fn)
            assertTrue("focusColor = colors.actionText" in fn)
        }
    }

    @Test
    fun `the shared frame draws a 2dp ring on the button's own radius, and AmberDisabledAction never takes it`() {
        val frame = source.substring(source.indexOf("fun AmberActionFrame("))
        assertTrue("the ring must follow the 16dp radius, not a plain rectangle", ".border(2.dp, focusColor, AmberActionShape)" in frame)
        val disabled = source.substring(source.indexOf("fun AmberDisabledAction("), source.indexOf("fun AmberSecondaryAction("))
        assertFalse(
            "a disabled Button takes no focus, same as the retired Instrument DisabledButton",
            "AmberActionFrame(" in disabled,
        )
    }
}
