package com.plainticker.mobile.ui.you

import com.plainticker.mobile.lock.AppLockState
import com.plainticker.mobile.lock.LockAvailability
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * You's Security row: what it offers in each availability state, that the switch shows the stored
 * setting and never the tap, and the words the founder asked for, from strings.xml.
 */
class SecuritySectionTest {

    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private val strings: String by lazy { File(module, "src/main/res/values/strings.xml").readText() }
    private val source: String by lazy {
        File(module, "src/main/java/com/plainticker/mobile/ui/you/SecuritySection.kt").readText()
    }

    private fun string(name: String): String =
        Regex("""<string name="$name">(.*?)</string>""").find(strings)?.groupValues?.get(1)
            ?: error("$name is missing from strings.xml")

    @Test
    fun `a fingerprint or a screen lock offers the switch, nothing to confirm with does not`() {
        val fingerprint = lockRow(AppLockState(availability = LockAvailability.BIOMETRIC))
        assertTrue(fingerprint.offered)
        assertFalse("off by default", fingerprint.on)

        assertTrue(lockRow(AppLockState(availability = LockAvailability.SCREEN_LOCK)).offered)

        val none = lockRow(AppLockState(availability = LockAvailability.NONE, enabled = true, message = "x"))
        assertFalse(none.offered)
        assertFalse("never drawn on where it cannot lock", none.on)
        assertNull(none.message)
    }

    @Test
    fun `the switch shows the stored setting and waits while a prompt is up`() {
        val asking = lockRow(AppLockState(availability = LockAvailability.BIOMETRIC, enabled = false, authenticating = true))
        assertFalse("a tap does not move the switch; a success does", asking.on)
        assertTrue(asking.busy)

        val on = lockRow(AppLockState(availability = LockAvailability.BIOMETRIC, enabled = true))
        assertTrue(on.on)
        assertFalse(on.busy)

        val refused = lockRow(AppLockState(availability = LockAvailability.SCREEN_LOCK, message = "Too many attempts."))
        assertEquals("Too many attempts.", refused.message)
    }

    @Test
    fun `the row says what the lock does, and what it does not`() {
        assertEquals("Security", string("you_heading_security"))
        assertEquals("Lock PlainTicker with fingerprint", string("you_lock_label"))
        assertEquals(
            "Asks for your fingerprint or screen lock when the app opens. The Seed Vault still signs every transaction.",
            string("you_lock_note"),
        )
        assertEquals("This phone has no fingerprint or screen lock set.", string("you_lock_unavailable"))
        assertEquals("PlainTicker is locked", string("lock_title"))
        assertEquals("Use screen lock", string("lock_prompt_screen_lock"))
    }

    @Test
    fun `the whole row is one switch target, and the switch itself takes no tap of its own`() {
        assertTrue("role = Role.Switch" in source)
        assertTrue("onValueChange = onSetLock" in source)
        assertTrue("onCheckedChange = null" in source)
        assertTrue("checked = row.on" in source)
        assertTrue("enabled = row.offered && !row.busy" in source)
    }
}
