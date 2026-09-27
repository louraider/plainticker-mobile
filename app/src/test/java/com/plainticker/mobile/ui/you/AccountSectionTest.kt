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
    private val walletScan by lazy { KotlinScan(source("WalletSection.kt")) }
    private val screenScan by lazy { KotlinScan(source("YouScreen.kt")) }

    // ---- Placement ---------------------------------------------------------------------------

    @Test
    fun `the section sits after the plan, before On this device`() {
        // Updated for the cabinet pass (2026-09-25): the Account section became the cabinet's
        // "Sign-in methods" group, which follows the hero and the Plan group (the web cabinet's own
        // order) rather than the retired Pro fact cards and their ActionButtons block.
        val code = screenScan.code
        val body = code.substring(code.indexOf("internal fun YouContent("), code.indexOf("private fun amberColors("))
        val plan = body.indexOf("PlanGroup(")
        val account = body.indexOf("AccountSection(")
        val device = body.indexOf("DeviceGroup(")
        assertTrue("YouContent never draws the Account section", account >= 0)
        assertTrue("Account comes after the Plan group", plan in 0 until account)
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
    fun `the section draws no amber fill, and every action reaches its handler`() {
        // Was "the sign-in button is never a second amber fill", pinned on an AmberSecondaryAction
        // in this file. The rule holds more strictly now: the hero is the only place You draws a
        // fill (Sign in with Google is its AmberPrimaryAction when there is no identity), and this
        // group's Sign in and Sign out, and the Wallet group's Connect and Disconnect (their own
        // group since the judges' round 2), are all text actions.
        val code = sectionScan.code
        assertEquals("no filled action in the section", 0, code.split("AmberPrimaryAction(").size - 1)
        assertEquals("no filled action in the wallet group", 0, walletScan.code.split("AmberPrimaryAction(").size - 1)
        assertTrue("RowAction(stringResource(R.string.you_action_sign_in), onSignIn)" in code)
        assertTrue("RowAction(stringResource(R.string.you_action_connect), onConnect)" in walletScan.code)
        assertTrue("RowAction(stringResource(R.string.action_disconnect), onDisconnect)" in walletScan.code)
        assertTrue("sign-in messages are drawn from the one mapping", "accountMessageRes(it)" in code)
        assertTrue("the hero is handed the message when it offers Sign in", "showMessage = heroMessage == null" in screenScan.code)
    }

    @Test
    fun `sign out is a two-step inline confirm, never a one-tap loss`() {
        val code = sectionScan.code
        val row = code.substring(code.indexOf("private fun SignedInRow("), code.indexOf("private const val CopiedMillis"))
        assertTrue("the first tap only asks", "RowAction(signOut, { confirming = true })" in row)
        assertTrue("the second tap signs out", "RowAction(signOut, { confirming = false; onSignOut() })" in row)
        assertTrue("Cancel folds the question away", "R.string.you_action_cancel), { confirming = false })" in row)
        assertTrue("the question says what happens", "R.string.you_sign_out_confirm" in row)
        assertEquals("onSignOut is called in exactly one place", 1, code.split("onSignOut()").size - 1)
    }

    @Test
    fun `linked wallets draw only when the server returned some`() {
        assertTrue("if (state.account.linkedWallets.isEmpty()) return" in sectionScan.code)
    }

    @Test
    fun `each linked wallet is its own row, not one row joined by newlines`() {
        val code = sectionScan.code
        assertTrue("one row per wallet", "state.account.linkedWallets.forEach { wallet ->" in code)
        assertFalse("no more joining wallets into one value", "joinToString(\"\\n\")" in code)
    }

    @Test
    fun `the wallet copies its full address, never the short key`() {
        assertTrue("ClipData.newPlainText(clipLabel, wallet.address)" in walletScan.code)
        assertTrue("a linked wallet copies its own full address the same way", "ClipData.newPlainText(clipLabel, wallet))" in sectionScan.code)
    }

    @Test
    fun `unlink is a two-step inline confirm, the same pattern as sign out`() {
        val raw = source("AccountSection.kt")
        val row = raw.substring(raw.indexOf("private fun LinkedWalletRow("), raw.indexOf("// ---- Previews"))
        assertTrue("the first tap only asks", "RowAction(unlink, { confirming = true })" in row)
        assertTrue("the second tap unlinks", "RowAction(unlink, { confirming = false; onUnlink() })" in row)
        assertTrue("Keep folds the question away", "R.string.you_action_keep), { confirming = false })" in row)
        assertTrue("the question says what happens", "R.string.account_unlink_confirm" in row)
        assertTrue("a busy row hides its own actions", "busy -> emptyList()" in row)
        assertTrue("a refused unlink shows one plain line", "unlinkFailureRes(failure)" in row)
        assertEquals("onUnlink is called in exactly one place", 1, sectionScan.code.split("onUnlink()").size - 1)
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

    // ---- The Wallet group (judges' round 2) --------------------------------------------------

    @Test
    fun `the phone's wallet connection is its own group, not a sign-in method`() {
        assertFalse("the wallet row came back under Sign-in methods", "private fun WalletRow(" in sectionScan.code || " WalletRow(wallet" in sectionScan.code)
        assertFalse("R.string.you_wallet_method" in sectionScan.code)
        assertTrue("R.string.you_heading_wallet" in walletScan.code)
        assertTrue("WalletRow(wallet = wallet, onConnect = onConnect, onDisconnect = onDisconnect, colors = colors)" in walletScan.code)
        val screen = screenScan.code
        val body = screen.substring(screen.indexOf("internal fun YouContent("), screen.indexOf("private fun amberColors("))
        val account = body.indexOf("AccountSection(")
        val wallet = body.indexOf("WalletSection(")
        assertTrue("YouContent never draws the Wallet group", wallet >= 0)
        assertTrue("Wallet follows Sign-in methods", account in 0 until wallet)
        assertTrue("Wallet comes before On this device", wallet < body.indexOf("DeviceGroup("))
        assertTrue("linked wallets stay with the account", "LinkedWalletsGroup(state = signedIn, onUnlink = onUnlink, colors = colors)" in sectionScan.code)
    }

    @Test
    fun `the wallet row's note is one short line, the full note behind Show`() {
        val short = ShippedCopy.strings.getValue("you_wallet_note_short")
        assertTrue("\"$short\" is longer than one line", short.length <= 70)
        assertTrue("sub = note" in walletScan.code)
        assertTrue("R.string.you_wallet_note_short" in walletScan.code)
        assertTrue("the full note is behind a tap", "if (open) {" in walletScan.code)
        assertTrue("R.string.you_wallet_note)" in walletScan.code)
    }

    @Test
    fun `the wallet group never touches the device code and spells no copy in Kotlin`() {
        assertFalse(".code(" in walletScan.code)
        assertFalse("DevicePassStore" in walletScan.code)
        val previewsAt = source("WalletSection.kt").lines().indexOfFirst { "---- Previews" in it } + 1
        val sentences = walletScan.literals.filter { it.line < previewsAt }.map { it.text }.filter { it.length > 12 && it.contains(' ') }
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

    // The clipping measurements this file used to carry (the sign-in button labels and the wallet
    // key) moved to CabinetFitTest with every other one-line slot on You, re-derived for the
    // hero's own button width.
}
