package com.plainticker.mobile.ui.you

import com.plainticker.mobile.lint.KotlinScan
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * U1's contract for the You composition, read from source the way PortfolioScreenTest and
 * WatchlistScreenTest read theirs. What the screen *says* is [YouModelTest]'s job; this file pins
 * what a device walk would otherwise have to prove and a later edit could quietly undo:
 *
 * 1. the section order the cabinet fixes (hero, Plan, Sign-in methods, On this device,
 *    Notifications, About), which is also the traversal order because the screen is one list;
 * 2. no arithmetic in the composition: every numeral and every sentence arrives decided;
 * 3. every "On this device" fact opens the tab it belongs to, and is labelled for a screen reader;
 * 4. the device's own code never reaches this file at all, not even a hash of it drawn as text.
 */
class YouScreenTest {

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private val screenFile = File(module, "src/main/java/com/plainticker/mobile/ui/you/YouScreen.kt")
    private val modelFile = File(module, "src/main/java/com/plainticker/mobile/ui/you/YouModel.kt")
    private val viewModelFile = File(module, "src/main/java/com/plainticker/mobile/ui/you/YouViewModel.kt")

    private val source: String by lazy {
        assertTrue("YouScreen.kt is missing", screenFile.isFile)
        screenFile.readText()
    }

    private val scan: KotlinScan by lazy { KotlinScan(source) }

    private val modelScan: KotlinScan by lazy {
        assertTrue("YouModel.kt is missing", modelFile.isFile)
        KotlinScan(modelFile.readText())
    }

    private val viewModelScan: KotlinScan by lazy {
        assertTrue("YouViewModel.kt is missing", viewModelFile.isFile)
        KotlinScan(viewModelFile.readText())
    }

    private val stringsXml: String by lazy { File(module, "src/main/res/values/strings.xml").readText() }

    /** The first line of the preview block, which is sample data rather than the screen. */
    private val previewsAt: Int by lazy {
        val line = source.lines().indexOfFirst { "---- Previews" in it }
        assertTrue("YouScreen.kt has no previews", line >= 0)
        line + 1
    }

    private fun count(marker: String): Int = scan.code.split(marker).size - 1

    /** One composable's body, so an order is read where the calls happen and not where they live. */
    private fun body(function: String, until: String): String {
        val start = scan.code.indexOf(function)
        assertTrue("YouScreen.kt has no $function", start >= 0)
        val end = scan.code.indexOf(until, start)
        assertTrue("YouScreen.kt has no $until after $function", end > start)
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

    @Test
    fun `the sections are drawn in the order the cabinet fixes`() {
        // Updated for the cabinet pass (2026-09-25): the founder asked for the app's You to match
        // plainticker.com's new account cabinet, so the old order (wallet block, Pro/stake cards,
        // action buttons, device trio, notifications line, digest link, footer, licenses) is
        // replaced by the cabinet's: one hero card, then Plan, Sign-in methods, On this device,
        // Notifications and About. KotlinScan blanks string literals (item keys included), so the
        // order is read off the calls each item wraps, as before.
        assertOrder(
            "YouContent",
            body("internal fun YouContent(", "private fun amberColors("),
            listOf(
                "header()",
                "R.string.you_heading)",
                "Hero(hero = hero",
                "PlanGroup(",
                "AccountSection(",
                "WalletSection(",
                "DeviceGroup(",
                "NotificationsGroup(",
                "AboutGroup(",
            ),
        )
    }

    @Test
    fun `about keeps the version, the disclaimer and every license, licenses behind one row`() {
        val about = body("private fun AboutGroup(", "private fun LicenseRow(")
        assertOrder(
            "AboutGroup",
            about,
            listOf(
                "R.string.you_version",
                "R.string.onboarding_body_disclaimer",
                "R.string.you_about_privacy",
                "R.string.you_about_terms",
                "R.string.you_about_delete_account",
                "R.string.you_heading_licenses",
                "LicenseRow(",
            ),
        )
        assertTrue("AboutLinks.PRIVACY" in about)
        assertTrue("AboutLinks.TERMS" in about)
        assertTrue("AboutLinks.DELETE_ACCOUNT" in about)
        assertTrue("the licenses are collapsed until asked for", "if (licensesOpen)" in about)
        assertTrue("each license still reads its shipped OFL text", "context.assets.open(license.assetPath)" in scan.code)
    }

    @Test
    fun `delete account reads in a neutral colour, not the action amber`() {
        // Device QA of 1.3.16.
        val about = body("private fun AboutGroup(", "private fun LicenseRow(")
        val delete = about.substring(about.indexOf("R.string.you_about_delete_account,"), about.indexOf("R.string.you_heading_licenses"))
        assertTrue("valueKind = RowValueKind.WORDS" in delete)
        assertTrue("the other links keep the link colour by default", "valueKind: RowValueKind = RowValueKind.LINK" in about)
    }

    @Test
    fun `about's links open the browser, and a phone without one does not crash`() {
        val row = body("private fun AboutLinkRow(", "private fun LicenseRow(")
        assertTrue("the whole row is the tap target", "onTap = { onOpenLink(url) }" in row)
        assertTrue("and it is labelled", "R.string.you_about_open_link" in row)
        assertTrue("Intent(Intent.ACTION_VIEW, Uri.parse(url))" in scan.code)
        assertTrue("runCatching {" in body("fun YouScreen(", "internal fun YouContent("))
    }

    // ---- Have a code? (mock judges' round 2) ---------------------------------------------------------

    @Test
    fun `have a code is the plan group's first row, in full action colour`() {
        val plan = body("private fun PlanGroup(", "private fun planRowAction(")
        assertTrue("PromoRow must come before the plan rows", plan.indexOf("PromoRow(") in 0 until plan.indexOf("rows.forEach"))
        val promo = body("private fun PromoRow(", "private fun PromoEditingRow(")
        assertTrue("RowAction(stringResource(R.string.promo_action_have_code), onOpen)" in promo)
        assertTrue("no dimmed colour on the action", "textSecondary" !in promo)
    }

    @Test
    fun `the code field sits right under its label, with Apply and Cancel after it`() {
        // Device QA of 1.3.16: as a CabinetRow the actions drew between the label and the input.
        val row = body("private fun PromoEditingRow(", "private fun PromoField(")
        assertOrder(
            "PromoEditingRow",
            row,
            listOf("R.string.promo_field_label", "PromoField(", "it.text", "R.string.promo_action_apply", "R.string.you_action_cancel"),
        )
        assertFalse("no longer a CabinetRow whose action row precedes its extra slot", "CabinetRow(" in row)
        val field = body("private fun PromoField(", "private fun DeviceGroup(")
        assertTrue("KeyboardCapitalization.Characters" in field)
        assertTrue("autoCorrectEnabled = false" in field)
        assertTrue("keyboardType = KeyboardType.Ascii" in field)
    }

    @Test
    fun `the code field takes focus as it appears`() {
        val field = body("private fun PromoField(", "private fun DeviceGroup(")
        assertTrue("LaunchedEffect(focus) { runCatching { focus.requestFocus() } }" in field)
        assertTrue(".focusRequester(focus)" in field)
    }

    @Test
    fun `detail's request opens the field, returns to the top, then clears itself`() {
        val content = body("internal fun YouContent(", "private fun amberColors(")
        val effect = content.substring(content.indexOf("LaunchedEffect(openPromo)"), content.indexOf("LazyColumn("))
        assertOrder("the promo request", effect, listOf("onOpenPromo()", "scrollToItem(0)", "onPromoOpened()"))
        assertTrue("a finished redeem folds first so the field can open", "if (promo is PromoState.Success) onDismissPromo()" in effect)
        assertTrue("openPromo = openPromo" in body("fun YouScreen(", "internal fun YouContent("))
    }

    @Test
    fun `the pay sheet's have a code closes the sheet, opens the field and brings it into view`() {
        // Fresh-device QA of 1.3.24 (B1): from the sheet the field and hint sat above the top edge.
        val screen = body("fun YouScreen(", "internal fun YouContent(")
        val haveCode = screen.substring(screen.indexOf("onHaveCode = {"))
        assertOrder("the sheet's Have a code", haveCode, listOf("passViewModel.close()", "passViewModel.openPromo()", "promoReveal++"))
        assertTrue("promoReveal = promoReveal," in screen)

        val content = body("internal fun YouContent(", "private fun amberColors(")
        val reveal = content.substring(content.indexOf("LaunchedEffect(promoReveal, revealRequest)"), content.indexOf("LazyColumn("))
        assertOrder(
            "the reveal waits for the keyboard, then measures, then scrolls",
            reveal,
            listOf("withTimeoutOrNull(ImeSettleMillis)", "imeTarget.getBottom(density)", "withFrameNanos", "promoRevealScroll(", "scrollBy(delta)"),
        )
        assertTrue("clear of the status bar and its scrim", "statusBars.getTop(density)" in reveal && "ScrimFade" in reveal)
        assertTrue("Detail's request ends in the same reveal", "revealRequest++" in content)
        val plan = body("private fun PlanGroup(", "private fun planRowAction(")
        assertTrue("the promo row reports where it stands", "promoBounds.top = top" in plan)
    }

    @Test
    fun `the closed promo row reads the entitlement and always offers have a code`() {
        val promo = body("private fun PromoRow(", "private fun PromoEditingRow(")
        assertTrue("promoLine(promo, pro, signedOut = keepNote)" in promo)
        assertEquals("one Have a code? action, drawn for every closed state", 1, Regex("""R\.string\.promo_action_have_code""").findAll(promo).count())
        assertTrue("pro = pro," in body("private fun PlanGroup(", "private fun planRowAction("))
    }

    @Test
    fun `the notifications group keeps enable and the digest`() {
        val group = body("private fun NotificationsGroup(", "private fun AboutGroup(")
        assertTrue("notificationLine(notificationsOn)" in group)
        assertTrue("Enable is offered only while notifications are off", "takeIf { !notificationsOn }" in group)
        assertTrue("R.string.action_enable" in group)
        assertTrue("the digest opens from here", "onOpenDigest?.let { open ->" in group)
    }

    @Test
    fun `the screen is one list, so the traversal order is the visual order`() {
        assertEquals("exactly one scroll container", 1, count("LazyColumn("))
        assertEquals("nothing is sticky and nothing overlaps", 0, count("stickyHeader"))
        assertEquals(0, count("zIndex("))
        assertEquals("no reordering of the reading order", 0, count("traversalIndex"))
        assertEquals("the navigation inset is part of the scrolled content", 1, count("WindowInsets.navigationBars"))
    }

    @Test
    fun `the device code never reaches this file`() {
        assertEquals("YouScreen.kt must never read the device's own code", 0, count(".code("))
        assertEquals("YouModel.kt must never read the device's own code", 0, modelScan.code.split(".code(").size - 1)
        assertEquals("YouViewModel.kt must never read the device's own code", 0, viewModelScan.code.split(".code(").size - 1)
        assertEquals(0, count("DevicePassStore"))
    }

    @Test
    fun `every on this device fact opens the tab it belongs to, and is labelled`() {
        listOf("R.string.you_fact_swaps", "R.string.you_fact_votes", "R.string.you_fact_watched").forEach {
            assertTrue("the screen has no $it", it in scan.code)
        }
        assertTrue("a tapped fact must open a tab", "onTap = { onOpenTab(" in scan.code)
        assertTrue("a tapped fact must be labelled for a screen reader", "tapLabel = " in scan.code)
        assertTrue("R.string.you_open_tab" in scan.code)
    }

    @Test
    fun `only the hero draws an amber fill, and every hero action has its own label`() {
        // Was "the button matrix never draws two accent fills", pinned against the retired
        // ActionButtons block. The rule is the same (never two amber fills); the one place a fill
        // can appear is now the hero's own button, and every other action on You is a TextAction.
        val hero = body("private fun HeroButton(", "private fun PlanGroup(")
        assertEquals("every AmberPrimaryAction on You lives in HeroButton", count("AmberPrimaryAction("), hero.split("AmberPrimaryAction(").size - 1)
        listOf(
            "HeroAction.SIGN_IN ->",
            "HeroAction.SIGNING_IN ->",
            "HeroAction.GET_PRO ->",
            "HeroAction.EXTEND ->",
        ).forEach { assertTrue("HeroButton does not handle $it", it in hero) }
        assertTrue("a sign-in in flight is a disabled button, never a missing one", "AmberDisabledAction(label = stringResource(R.string.account_signing_in)" in hero)
        assertEquals("Get Pro and Extend Pro both open the pass flow", 2, hero.split("onClick = onPay").size - 1)
        assertTrue("Sign in with Google runs the sign-in", "stringResource(R.string.account_sign_in), onClick = onSignIn" in hero)
        assertTrue("Connect wallet sits under Sign in as a text action", "R.string.action_connect_wallet" in body("private fun Hero(", "private fun HeroButton("))
    }

    @Test
    fun `the plan group's pay entry and refresh reach the real handlers`() {
        val action = body("private fun planRowAction(", "private fun DeviceGroup(")
        assertTrue("PlanAction.GET_PRO -> RowAction(stringResource(R.string.you_action_get_pro), onPay)" in action)
        assertTrue("PlanAction.EXTEND -> RowAction(stringResource(R.string.you_action_extend), onPay)" in action)
        assertTrue("PlanAction.REFRESH -> RowAction(stringResource(R.string.action_refresh), onRefresh)" in action)
        assertTrue("the Plan group refreshes through the shared entitlement refresh", "onRefresh = onRefreshEntitlement" in scan.code)
    }

    @Test
    fun `a finished pay sheet still sits beside the list`() {
        assertTrue("PassSheet(" in scan.code)
        assertTrue("onPay = passViewModel::pay" in scan.code)
        assertTrue("onConnect = viewModel::connect" in scan.code)
        assertTrue("onDisconnect = viewModel::disconnect" in scan.code)
    }

    @Test
    fun `every sentence on the screen comes from strings xml`() {
        val resourceValues = Regex("""<string\b[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(stringsXml).map { it.groupValues[1] }.toSet()
        val sentences = scan.literals
            .filter { it.line < previewsAt }
            .map { it.text }
            .filter { it.length > 12 && it.contains(' ') && it.any { c -> c.isLowerCase() } }
            .filterNot { it in resourceValues }
        assertTrue("copy spelled in Kotlin: $sentences", sentences.isEmpty())
    }

    /** Device QA of 1.3.17: the promo code field had no visible edge. */
    @Test
    fun `the promo field draws the same underline Field does, amber while focused`() {
        val start = source.indexOf("private fun PromoField(")
        val field = source.substring(start, source.indexOf("private fun DeviceGroup(", start))
        assertTrue("color = if (focused) colors.actionText else colors.border" in field)
        assertTrue("interactionSource = interaction" in field)
    }
}
