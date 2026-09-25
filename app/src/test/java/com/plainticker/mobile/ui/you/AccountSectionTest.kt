package com.plainticker.mobile.ui.you

import com.plainticker.mobile.R
import com.plainticker.mobile.lint.KotlinScan
import com.plainticker.mobile.prefs.SignedInAccount
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.ShippedCopy
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Account section on You (docs/google-sign-in.md): where it sits, what it says, and the one
 * measured budget it has (DESIGN.md section 4's clipping rule).
 */
class AccountSectionTest {

    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private fun source(name: String): String = File(module, "src/main/java/com/plainticker/mobile/ui/you/$name").readText()

    private val sectionScan by lazy { KotlinScan(source("AccountSection.kt")) }
    private val screenScan by lazy { KotlinScan(source("YouScreen.kt")) }

    // ---- Placement ---------------------------------------------------------------------------

    @Test
    fun `the section sits after the Pro facts and their action, before On this device`() {
        val code = screenScan.code
        val body = code.substring(code.indexOf("internal fun YouContent("), code.indexOf("private fun YouAction.handler("))
        val action = body.indexOf("ActionButtons(")
        val account = body.indexOf("AccountSection(")
        val device = body.indexOf("R.string.you_heading_device")
        assertTrue("YouContent never draws the Account section", account >= 0)
        assertTrue("Account comes after the Pro action", action in 0 until account)
        assertTrue("Account comes before On this device", account < device)
    }

    @Test
    fun `a finished sign-in re-reads the entitlement through the shared refresh`() {
        assertTrue(
            "YouScreen must refresh the entitlement on each sign-in",
            "accountViewModel.signedIn.collect { passViewModel.refreshEntitlement() }" in screenScan.code,
        )
    }

    @Test
    fun `the sign-in button is never a second amber fill`() {
        assertTrue("AmberSecondaryAction(label = stringResource(R.string.account_sign_in)" in sectionScan.code)
        assertEquals("no filled action in the Account section", 0, sectionScan.code.split("AmberPrimaryAction(").size - 1)
    }

    @Test
    fun `the section never touches the device code`() {
        assertFalse(".code(" in sectionScan.code)
        assertFalse("DevicePassStore" in sectionScan.code)
    }

    @Test
    fun `every sentence in the section comes from strings xml`() {
        val previewsAt = source("AccountSection.kt").lines().indexOfFirst { "---- Previews" in it } + 1
        val sentences = sectionScan.literals
            .filter { it.line < previewsAt }
            .map { it.text }
            .filter { it.length > 12 && it.contains(' ') }
        assertTrue("copy spelled in Kotlin: $sentences", sentences.isEmpty())
    }

    // ---- What it says ------------------------------------------------------------------------

    @Test
    fun `the pitch says plainly what signing in gives`() {
        assertEquals(
            "Use the same account and Pro on plainticker.com and here.",
            ShippedCopy.strings.getValue("account_pitch"),
        )
        assertEquals("Sign in with Google", ShippedCopy.strings.getValue("account_sign_in"))
    }

    @Test
    fun `every message is one plain line, with no exclamation mark`() {
        AccountMessage.entries.forEach { message ->
            val text = ShippedCopy.render(Copy.Words(accountMessageRes(message)))
            assertFalse("$message: $text", '!' in text)
            assertFalse("$message is not one line: $text", '\n' in text)
            assertTrue("$message is too long to read as one line: $text", text.length <= 90)
        }
    }

    @Test
    fun `the identity is the email, then the name, then plain words`() {
        assertEquals("ann@example.com", ShippedCopy.render(accountIdentity(SignedInAccount("ann@example.com", "Ann", emptyList()))))
        assertEquals("Ann", ShippedCopy.render(accountIdentity(SignedInAccount(null, "Ann", emptyList()))))
        assertEquals(Copy.Words(R.string.account_identity_fallback), accountIdentity(SignedInAccount(null, null, emptyList())))
    }

    @Test
    fun `a linked wallet is drawn as its short key`() {
        val keys = linkedWalletKeys(SignedInAccount(null, null, listOf("4Nd1mBQtrMJVYVfKf2PJy9NZUZdTAsp7D4xWLs4gDB4T")))
        assertEquals(listOf("4Nd1…DB4T"), keys)
    }

    // ---- The clipping rule -------------------------------------------------------------------

    /**
     * A button label is the only slot here drawn on one line (`maxLines = 1`, AmberPrimaryAction.kt).
     * Its content width on the Seeker's 400dp frame: 400 minus You's 20dp side margins minus the
     * button's own 20dp horizontal padding each side = **320dp**.
     */
    private val buttonContentDp = 400.0 - 2 * 20.0 - 2 * 20.0

    /**
     * fontTools against `res/font/bricolage_grotesque.ttf`, instantiated at [AmberType.button]'s
     * exact coordinates (`wght` 600, `wdth` 100, `opsz` 16), 16sp, summed advance widths (no
     * kerning, which only narrows a real render), grown to 1.3x; 2026-09-25.
     */
    private val buttonLabelWidthAt13xDp = mapOf(
        "Sign in with Google" to 191.048,
        "Signing in" to 98.176,
    )

    @Test
    fun `both button labels fit the button at 1_3x, measured against the font file`() {
        listOf("account_sign_in", "account_signing_in").forEach { name ->
            val text = ShippedCopy.strings.getValue(name)
            val width = buttonLabelWidthAt13xDp[text] ?: error(
                "\"$text\" ($name) has no measured width; remeasure it with fontTools against " +
                    "res/font/bricolage_grotesque.ttf at wght 600, wdth 100, opsz 16 before shipping it",
            )
            assertTrue("\"$text\" is $width dp at 1.3x, past the $buttonContentDp dp button", width <= buttonContentDp)
        }
    }

    /**
     * A wallet's short key, JetBrains Mono Regular 15sp (`jetbrains_mono_regular.ttf`), nine
     * characters including the ellipsis: 81.0dp at 1.0x, 105.3dp at 1.3x (fontTools, 2026-09-25),
     * on a line of its own 360dp wide. Every short key is nine monospace characters, so this one
     * measurement covers every wallet.
     */
    @Test
    fun `a short wallet key fits its own line at 1_3x`() {
        val lineDp = 400.0 - 2 * 20.0
        assertTrue(105.3 <= lineDp)
        assertEquals(9, linkedWalletKeys(SignedInAccount(null, null, listOf("4Nd1mBQtrMJVYVfKf2PJy9NZUZdTAsp7D4xWLs4gDB4T"))).single().length)
    }
}
