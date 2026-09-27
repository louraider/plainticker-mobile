package com.plainticker.mobile.lint

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Copy and accessibility lint, task DT4 (plan section 13 Pass 4 and Pass 6, DESIGN.md sections 3,
 * 7 and 8). Reads app/src/main/res/values/strings.xml and every Kotlin file under
 * app/src/main/java/com/plainticker/mobile/ui straight from disk, so a violation fails the unit test gate
 * before anything reaches a device. A finding prints as `file:line [rule] snippet`.
 *
 * Kotlin sources go through [KotlinScan], a small lexer: string literals come out with their
 * escapes decoded and their templates followed (a word inside "${...}" is scanned as code, a
 * literal nested in a template is scanned as a literal), comments are dropped. So a hex color in
 * a string is a finding and one in a comment is not. The verdict rule is the exception: the
 * repository goes public, so it also reads every Kotlin source under src/main and src/test and
 * the whole strings.xml raw, comments included.
 *
 * Rules that are meant for lookup keys and not for display carry a per-line opt out: a
 * `.uppercase()` on a line whose comment says `lint-allow uppercase` is a key, not a transform.
 */
class CopyLintTest {

    // ---- Inputs -----------------------------------------------------------------------------

    private data class Text(val file: String, val line: Int, val text: String)

    private data class Resource(val name: String, val line: Int, val text: String)

    private class KtFile(val path: String, val source: String, val scan: KotlinScan) {
        fun sourceLine(index: Int): String = source.split('\n').getOrElse(index) { "" }.trim()
    }

    private data class Finding(val rule: String, val file: String, val line: Int, val snippet: String) {
        override fun toString(): String = "$file:$line [$rule] $snippet"
    }

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private val stringsXml = File(module, "src/main/res/values/strings.xml")
    private val uiRoot = File(module, "src/main/java/com/plainticker/mobile/ui")
    private val tokensPath = "app/src/main/java/com/plainticker/mobile/ui/theme/Tokens.kt"
    private val componentsPrefix = "app/src/main/java/com/plainticker/mobile/ui/components/"

    private fun display(file: File): String =
        "app/" + file.canonicalFile.relativeTo(module).path.replace(File.separatorChar, '/')

    private val uiFiles: List<KtFile> by lazy {
        uiRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.path }
            .map { f -> val src = f.readText(); KtFile(display(f), src, KotlinScan(src)) }
            .toList()
    }

    private val componentFiles: List<KtFile> get() = uiFiles.filter { it.path.startsWith(componentsPrefix) }

    private val uiLiterals: List<Text>
        get() = uiFiles.flatMap { f -> f.scan.literals.map { Text(f.path, it.line, it.text) } }

    private val stringsRaw: String by lazy { stringsXml.readText() }

    /** strings.xml line by line, comments included. */
    private val stringsRawLines: List<Text>
        get() = rawLines(stringsXml, stringsRaw)

    /** Every Kotlin source under src/main and src/test, line by line, comments included. */
    private val rawKotlinLines: List<Text> by lazy {
        listOf("src/main/java", "src/test/java").map { File(module, it) }
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.sortedBy { it.path }.toList() }
            .flatMap { f -> rawLines(f, f.readText()) }
    }

    private fun rawLines(file: File, text: String): List<Text> =
        text.split('\n').mapIndexed { i, line -> Text(display(file), i + 1, line.trimEnd('\r')) }

    private val resources: List<Resource> by lazy { parseResources(stringsRaw) }

    private val resourceTexts: List<Text>
        get() = resources.map { Text(display(stringsXml), it.line, it.text) }

    // ---- strings.xml ------------------------------------------------------------------------

    private val stringElement = Regex("""<string\b([^>]*)>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    private val pluralsElement = Regex("""<plurals\b([^>]*)>(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
    private val itemElement = Regex("""<item\b([^>]*)>(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
    private val nameAttribute = Regex("""\bname="([^"]*)"""")
    private val quantityAttribute = Regex("""\bquantity="([^"]*)"""")

    /**
     * Every sentence in the file, counted copy included: a `<plurals>` contributes one resource
     * per form, named `list_today/one`, so both forms go through every rule below and a duplicate
     * name still means a duplicate name.
     */
    private fun parseResources(xml: String): List<Resource> {
        val plain = stringElement.findAll(xml).map { m ->
            val name = nameAttribute.find(m.groupValues[1])?.groupValues?.get(1) ?: "(string)"
            Resource(name, lineAt(xml, m.range.first), decodeResource(m.groupValues[2]))
        }
        val counted = pluralsElement.findAll(xml).flatMap { block ->
            val name = nameAttribute.find(block.groupValues[1])?.groupValues?.get(1) ?: "(plurals)"
            val at = block.groups[2]!!.range.first
            itemElement.findAll(block.groupValues[2]).map { item ->
                val quantity = quantityAttribute.find(item.groupValues[1])?.groupValues?.get(1) ?: "(item)"
                Resource("$name/$quantity", lineAt(xml, at + item.range.first), decodeResource(item.groupValues[2]))
            }
        }
        return (plain + counted).toList()
    }

    /** Inline markup dropped, XML entities and Android escapes decoded, so `&#8212;` and `\u2014` are seen. */
    private fun decodeResource(raw: String): String {
        var t = raw.replace(Regex("<[^>]+>"), "")
        t = Regex("&#x([0-9A-Fa-f]+);").replace(t) { String(Character.toChars(it.groupValues[1].toInt(16))) }
        t = Regex("&#([0-9]+);").replace(t) { String(Character.toChars(it.groupValues[1].toInt())) }
        t = t.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
        t = Regex("""\\u([0-9A-Fa-f]{4})""").replace(t) { it.groupValues[1].toInt(16).toChar().toString() }
        t = Regex("""\\(.)""").replace(t) {
            when (it.groupValues[1]) {
                "n" -> "\n"
                "t" -> "\t"
                else -> it.groupValues[1]
            }
        }
        return t
    }

    // ---- Rules -----------------------------------------------------------------------------

    /** The four verdict words, assembled so a whole-word grep of the repository stays clean. */
    private val verdicts: List<String> = listOf("b" + "uy", "s" + "ell", "h" + "old", "av" + "oid")
    private val verdictWord = Regex("""\b(${verdicts.joinToString("|")})\b""", RegexOption.IGNORE_CASE)

    private fun verdictFindings(texts: List<Text>): List<Finding> = texts.flatMap { t ->
        verdictWord.findAll(t.text).map { Finding("verdict word", t.file, t.line, "${quote(t.text)} <- ${it.value}") }.toList()
    }

    /** Figure dash, en dash, em dash, horizontal bar. */
    private val dashes = setOf('\u2012', '\u2013', '\u2014', '\u2015')

    private fun dashFindings(texts: List<Text>): List<Finding> = texts
        .filter { t -> t.text.any { it in dashes } }
        .map { Finding("em or en dash", it.file, it.line, quote(it.text)) }

    private fun exclamationFindings(texts: List<Text>): List<Finding> = texts
        .filter { '!' in it.text }
        .map { Finding("exclamation mark", it.file, it.line, quote(it.text)) }

    /**
     * Emoji and the pictographs a designer reaches for instead: arrows, dingbats (check marks,
     * stars), geometric shapes (dots), misc symbols, variation selector, ZWJ, keycap, tags.
     */
    private fun isPictograph(cp: Int): Boolean =
        cp in 0x1F000..0x1FAFF || cp in 0x2190..0x21FF || cp in 0x2300..0x23FF || cp in 0x25A0..0x25FF ||
            cp in 0x2600..0x27BF || cp in 0x2900..0x297F || cp in 0x2B00..0x2BFF ||
            cp == 0xFE0F || cp == 0x200D || cp == 0x20E3 || cp in 0xE0000..0xE007F

    private fun pictographFindings(texts: List<Text>): List<Finding> = texts.mapNotNull { t ->
        val hits = t.text.codePoints().toArray().filter(::isPictograph)
        if (hits.isEmpty()) null
        else Finding("emoji or pictograph", t.file, t.line, "${quote(t.text)} <- ${hits.joinToString { "U+%04X".format(it) }}")
    }

    private val bannedWord = Regex(
        """\b(seamless\w*|powerful\w*|unlock\w*|empower\w*|journey\w*|insights?|supercharge\w*|effortless\w*|all-in-one|welcome to)\b""",
        RegexOption.IGNORE_CASE,
    )

    private fun bannedWordFindings(texts: List<Text>): List<Finding> = texts.flatMap { t ->
        bannedWord.findAll(t.text).map { Finding("banned word", t.file, t.line, "${quote(t.text)} <- ${it.value}") }.toList()
    }

    private fun middleDotFindings(texts: List<Text>): List<Finding> = texts
        .filter { t -> t.text.count { it == '\u00B7' } > 1 }
        .map { Finding("more than one middle dot", it.file, it.line, quote(it.text)) }

    /**
     * Sentence case (DESIGN.md section 3, no uppercase tracked eyebrows): a word of four or more
     * capitals is a finding unless it is one of the initialisms the copy needs. Tickers such as
     * TSLAx end in a lowercase letter and pass; SEC, SOL, UTC and EV are too short to match.
     */
    private val capsWord = Regex("""\b[A-Z]{4,}\b""")
    private val initialisms = setOf("NYSE", "NASDAQ", "USDC", "EDGAR", "XBRL")

    private fun capsFindings(texts: List<Text>): List<Finding> = texts.flatMap { t ->
        capsWord.findAll(t.text).filter { it.value !in initialisms }
            .map { Finding("all-caps word", t.file, t.line, "${quote(t.text)} <- ${it.value}") }.toList()
    }

    private val uppercaseCall = Regex("""\.(uppercase|uppercaseChar|toUpperCase)\s*\(|::(uppercase|toUpperCase)\b|\bTextTransform\b""")
    private val smallCapsFeature = Regex("""\b(smcp|c2sc|pcap|c2pc)\b""")
    private val allowUppercase = "lint-allow uppercase"

    private fun uppercaseFindings(files: List<KtFile>): List<Finding> = files.flatMap { f ->
        val code = f.scan.codeLines().withIndex()
            .filter { (i, line) -> uppercaseCall.containsMatchIn(line) && allowUppercase !in f.sourceLine(i) }
            .map { (i, _) -> Finding("uppercase transform", f.path, i + 1, f.sourceLine(i)) }
        val features = f.scan.literals
            .filter { smallCapsFeature.containsMatchIn(it.text) }
            .map { Finding("small caps font feature", f.path, it.line, quote(it.text)) }
        code + features
    }

    private val hexLiteral = Regex("""\b0[xX]([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})\b""")
    private val cssHex = Regex("""#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})\b""")
    private val namedColor = Regex("""\bColor\.(Black|White|Red|Green|Blue|Yellow|Cyan|Magenta|Gray|LightGray|DarkGray)\b""")
    private val numericColor = Regex("""\bColor\(\s*(?:\d[\d_]*|\d*\.\d+f?|\d+f)\s*,""")

    private fun argb(hex: String): String = (if (hex.length == 6) "FF" + hex else hex).uppercase()

    /** The opaque ARGB values declared in Tokens.kt (Line and Line strong derive from Ink by alpha). */
    private fun tokenColors(): Set<String> {
        val tokens = uiFiles.single { it.path == tokensPath }
        return hexLiteral.findAll(tokens.scan.code).map { argb(it.groupValues[1]) }.toSet()
    }

    private fun colorFindings(files: List<KtFile>, allowed: Set<String>): List<Finding> = files.flatMap { f ->
        val out = mutableListOf<Finding>()
        fun offToken(hex: String) = argb(hex) !in allowed
        f.scan.codeLines().forEachIndexed { i, line ->
            hexLiteral.findAll(line).filter { offToken(it.groupValues[1]) }
                .forEach { out += Finding("color not a token", f.path, i + 1, "${it.value} in ${f.sourceLine(i)}") }
            namedColor.findAll(line)
                .forEach { out += Finding("color not a token", f.path, i + 1, "${it.value} in ${f.sourceLine(i)}") }
            numericColor.findAll(line)
                .forEach { out += Finding("color not a token", f.path, i + 1, "${it.value}...) in ${f.sourceLine(i)}") }
        }
        f.scan.literals.forEach { lit ->
            (hexLiteral.findAll(lit.text) + cssHex.findAll(lit.text)).filter { offToken(it.groupValues[1]) }
                .forEach { out += Finding("color not a token", f.path, lit.line, "${it.value} in ${quote(lit.text)}") }
        }
        out
    }

    // ---- Accessibility ----------------------------------------------------------------------

    private val composableFun = Regex("""@Composable\s+((?:\w+\s+)*)fun\s+(?:[\w.<>?]+\.)?(\w+)\s*\(""")
    private val clickHandlerParam = Regex("""^on[A-Z]\w*\s*:.*->""", RegexOption.DOT_MATCHES_ALL)

    /**
     * Material buttons apply Role.Button and ModalBottomSheet labels its own dismiss, expand and
     * collapse actions; every other component must say so in its file.
     */
    private val exposesLabel = Regex(
        """Role\.|contentDescription|[sS]emantics|\b(Button|OutlinedButton|TextButton|FilledTonalButton|ElevatedButton|IconButton|ModalBottomSheet)\s*\(""",
    )

    private val clickableModifier = Regex("""\.(clickable|combinedClickable|selectable|toggleable)\s*([({])""")
    private val roleArgument = Regex("""\brole\s*=""")

    /** Every clickable, combinedClickable, selectable or toggleable modifier in [files] must pass `role =`. */
    private fun clickableRoleFindings(files: List<KtFile>): List<Finding> = files.flatMap { f ->
        val code = f.scan.code
        clickableModifier.findAll(code).mapNotNull { m ->
            val line = lineAt(code, m.range.first)
            val name = m.groupValues[1]
            if (m.groupValues[2] == "{") {
                Finding("clickable without a role", f.path, line, "$name { } cannot name a role; pass role = Role.Button")
            } else {
                val open = m.range.last
                val args = code.substring(open + 1, closingParen(code, open))
                if (roleArgument.containsMatchIn(args)) null
                else Finding("clickable without a role", f.path, line, "$name(...) without role =")
            }
        }.toList()
    }

    /** Names of the composables in [code] that take a function-typed `on*` parameter, with their line. */
    private fun clickHandlers(code: String): List<Triple<String, String, Int>> =
        composableFun.findAll(code).mapNotNull { m ->
            val open = m.range.last
            val params = splitParams(code.substring(open + 1, closingParen(code, open)))
            val handler = params.firstOrNull { clickHandlerParam.containsMatchIn(it) } ?: return@mapNotNull null
            Triple(m.groupValues[2], handler.substringBefore(':').trim(), lineAt(code, m.range.first))
        }.toList()

    // ---- Tests: copy -----------------------------------------------------------------------

    @Test
    fun `no verdict words in strings xml or any kotlin source, comments included`() {
        assertTrue("expected the module's Kotlin sources", rawKotlinLines.size > 1_000)
        assertClean(verdictFindings(stringsRawLines + resourceTexts + uiLiterals + rawKotlinLines))
    }

    @Test
    fun `no em or en dash in strings xml or ui literals`() {
        assertClean(dashFindings(stringsRawLines + resourceTexts + uiLiterals))
    }

    @Test
    fun `no all-caps words in strings xml`() {
        assertClean(capsFindings(resourceTexts))
    }

    @Test
    fun `no exclamation marks in strings xml`() {
        assertClean(exclamationFindings(resourceTexts))
    }

    @Test
    fun `no emoji or pictographs in strings xml or ui literals`() {
        assertClean(pictographFindings(resourceTexts + uiLiterals))
    }

    @Test
    fun `no marketing words in strings xml or ui literals`() {
        assertClean(bannedWordFindings(resourceTexts + uiLiterals))
    }

    @Test
    fun `at most one middle dot per string`() {
        assertClean(middleDotFindings(resourceTexts + uiLiterals))
    }

    @Test
    fun `no uppercase transforms under ui`() {
        assertClean(uppercaseFindings(uiFiles))
    }

    @Test
    fun `every literal color under ui is a token`() {
        val allowed = tokenColors()
        // 7 Instrument (DESIGN.md's old palette, still read by every existing composable) plus
        // Amber's 19 (10 dark, 9 light; docs/design-research-2026-09-21.md section 5.3): 26.
        assertEquals("Tokens.kt declares 26 opaque colors", 26, allowed.size)
        assertClean(colorFindings(uiFiles.filter { it.path != tokensPath }, allowed))
    }

    @Test
    fun `strings xml carries the instrument copy`() {
        val byName = resources.associate { it.name to it.text }
        assertEquals("resource names are unique", resources.size, byName.size)
        assertTrue("expected the seeded copy, found ${resources.size} strings", resources.size >= 120)
        resources.forEach {
            // Counted copy carries its quantity keyword after a slash: "portfolio_priced_by/one".
            assertTrue("${it.name} is not snake_case", it.name.matches(Regex("[a-z][a-z0-9_]*(/(one|other))?")))
            assertTrue("${it.name} is blank", it.text.isNotBlank())
        }
        assertEquals("PlainTicker", byName["app_name"])
        assertEquals("Tokenized stocks, read before you swap.", byName["onboarding_headline"])
        // "Read the list" until 2026-09-26: there is no List to read any more, and the gate now
        // opens on Today (audit, item 1).
        assertEquals("Open Today", byName["onboarding_continue"])
        assertEquals("Live from the mint", byName["detail_live_label"])
        assertEquals("Backing and controls", byName["detail_heading_backing"])
        assertEquals("Against the sector", byName["detail_heading_sector"])
        assertEquals("Swap %1\$s to %2\$s", byName["swap_button"])
        assertEquals("%1\$s to %2\$s", byName["swap_direction"])
        assertEquals("View in Portfolio", byName["receipt_view_portfolio"])
        assertEquals("Cost basis is not read from the chain.", byName["portfolio_cost_basis"])
        // The liquidity floor (DESIGN.md section 1, docs/data-map.md): the row and the gauge say
        // what the pool is worth in place of a premium nothing backs, in plain words and no flag.
        // Device QA of 1.3.16: "$17 behind, too thin" read as a sum owed; the plainer word leads, and "depth" keeps it Jupiter's figure rather than a pool size claimed in the app's own voice.
        assertEquals("Depth %1\$s, too thin", byName["list_row_meta_thin"])
        assertEquals("Depth %1\$s behind this price", byName["detail_liquidity_line"])
        assertEquals("Depth not reported", byName["list_row_meta_pool_unknown"])
        assertEquals(
            "Jupiter reports %1\$s behind this price. That is too little for the token to follow the NYSE close, " +
                "so the premium is left out.",
            byName["detail_gauge_thin"],
        )
        assertEquals(
            "Jupiter priced this token but did not report how much stands behind the price, so whether it " +
                "follows the NYSE close cannot be checked.",
            byName["detail_gauge_pool_unknown"],
        )
        // The wrong truth the Seeker drew on 2026-09-13 (docs/data-map.md): a wallet sheet closed
        // without an approval is not a swap that failed, and the app cannot tell a decline from a
        // sheet that went away, so the sentence it gets names neither and claims no fault.
        assertEquals(
            "No signature came back, so nothing was sent. The amount is still here.",
            byName["swap_not_approved"],
        )
        assertTrue(
            "a wallet that signed nothing is no longer a failure, so it has no failure sentence",
            "swap_failed_nothing_signed" !in byName && "swap_failed_wallet_refused" !in byName,
        )
        assertEquals("The wallet did not answer, so nothing was connected", byName["swap_failed_connect_refused"])
        // "The swap did not land" belongs to an execute that refused, and to nothing else.
        assertEquals("The swap did not land. Nothing was swapped.", byName["swap_failed_swap_refused"])
    }

    // ---- Tests: the lint itself -----------------------------------------------------------

    @Test
    fun `lint catches seeded violations`() {
        fun seed(text: String) = listOf(Text("seed.kt", 1, text))
        verdicts.forEach { word ->
            assertEquals(word, 1, verdictFindings(seed("Time to $word now")).size)
            assertEquals(word.uppercase(), 1, verdictFindings(seed("${word.uppercase()} rating")).size)
        }
        assertEquals(1, dashFindings(seed("Live \u2014 from the mint")).size)
        assertEquals(1, dashFindings(seed("Sep 4\u201322")).size)
        assertEquals(1, exclamationFindings(seed("Landed!")).size)
        assertEquals(1, pictographFindings(seed("Landed \uD83D\uDE80")).size)
        assertEquals(1, pictographFindings(seed("Swap USDC \u2192 TSLAx")).size)
        assertEquals(1, pictographFindings(seed("\u2713 yes")).size)
        assertEquals(1, pictographFindings(seed("\u25CF live")).size)
        listOf(
            "Seamless swaps", "A powerful tool", "Unlock more", "Fresh insights", "Your journey", "Empowered",
            "Welcome to PlainTicker", "All-in-one wallet", "Effortlessly", "Supercharged",
        ).forEach { assertEquals(it, 1, bannedWordFindings(seed(it)).size) }
        assertEquals("two banned words, two findings", 2, bannedWordFindings(seed("Unlock insights")).size)
        assertEquals(1, middleDotFindings(seed("a \u00B7 b \u00B7 c")).size)
        assertEquals(1, capsFindings(seed("01 TRUST")).size)
        assertEquals(1, capsFindings(seed("PLAINTICKER")).size)
        assertEquals(0, capsFindings(seed("TSLAx vs NYSE close, in USDC, from SEC EDGAR XBRL, EV to sales")).size)

        val gestures = "val a = Modifier.combinedClickable(onLongClick = x, onClick = {})\n" +
            "val b = Modifier.clickable { }\n" +
            "val c = Modifier.clickable(role = Role.Button, onClick = {})\n" +
            "val d = Modifier.toggleable(value = on, onValueChange = {})"
        assertEquals(3, clickableRoleFindings(listOf(KtFile("g.kt", gestures, KotlinScan(gestures)))).size)

        val bad = KtFile("seed.kt", "val x = label.uppercase()\nval y = Color(0xFF6750A4)\nval z = Color.Red\nval f = \"smcp\"", KotlinScan("val x = label.uppercase()\nval y = Color(0xFF6750A4)\nval z = Color.Red\nval f = \"smcp\""))
        assertEquals(2, uppercaseFindings(listOf(bad)).size)
        assertEquals(2, colorFindings(listOf(bad), tokenColors()).size)
        val allowed = KtFile("ok.kt", "val k = t.uppercase() // lint-allow uppercase: key", KotlinScan("val k = t.uppercase() // lint-allow uppercase: key"))
        assertEquals(0, uppercaseFindings(listOf(allowed)).size)
        assertEquals(1, uppercaseFindings(listOf(KtFile("t.kt", "val k = \"\${t.uppercase()}\"", KotlinScan("val k = \"\${t.uppercase()}\"")))).size)

        val encoded = parseResources("<resources><string name=\"a\">A &#8212; B</string><string name=\"b\">C \\u2013 D</string></resources>")
        assertEquals(2, dashFindings(encoded.map { Text("seed.xml", it.line, it.text) }).size)
    }

    @Test
    fun `lint accepts the canvas copy`() {
        val ok = listOf(
            "Swap USDC to TSLAx", "TSLAx to USDC", "Holdings", "26,101 shares held for 25,924 tokens", "All-in cost",
            "est. all-in cost 0.09% \u00B7 liquidity \$1.3M", "Not a price forecast. Not investment advice.",
            "3kF9\u2026Qm2v", "Read the list", "Debug build \u00B7 signed, not submitted", "52-week position",
        ).map { Text("seed", 1, it) }
        assertClean(
            verdictFindings(ok) + dashFindings(ok) + exclamationFindings(ok) + pictographFindings(ok) +
                bannedWordFindings(ok) + middleDotFindings(ok) + capsFindings(ok),
        )
    }

    @Test
    fun `kotlin scan reads literals and skips comments`() {
        val src = listOf(
            "// \"not a literal\" in a comment",
            "/* \"nor this one\" */",
            "val a = \"plain\"",
            "val b = \"with \${x.trim()} template and \${\"nested\"} inside\"",
            "val c = \"\"\"raw \"quoted\" text\"\"\"",
            "val d = '\"'",
            "val e = \"esc\\u2014aped \\\"q\\\" \\\$5\"",
        ).joinToString("\n")
        val scan = KotlinScan(src)
        assertEquals(
            setOf(
                3 to "plain",
                4 to "nested",
                4 to "with \${x.trim()} template and \${\"nested\"} inside",
                5 to "raw \"quoted\" text",
                7 to "esc\u2014aped \"q\" \$5",
            ),
            scan.literals.map { it.line to it.text }.toSet(),
        )
        assertTrue(scan.code.contains("x.trim()"))
        assertTrue(!scan.code.contains("not a literal") && !scan.code.contains("nor this") && !scan.code.contains("plain"))
        assertEquals(src.length, scan.code.length)
        assertEquals(src.count { it == '\n' }, scan.code.count { it == '\n' })
    }

    // ---- Tests: accessibility --------------------------------------------------------------

    @Test
    fun `every component with a click handler exposes a role or a label`() {
        var seen = 0
        val findings = componentFiles.flatMap { f ->
            val handlers = clickHandlers(f.scan.code)
            seen += handlers.size
            if (handlers.isEmpty() || exposesLabel.containsMatchIn(f.scan.code)) emptyList()
            else handlers.map { (fn, param, line) ->
                Finding(
                    "click handler without role or label", f.path, line,
                    "fun $fn($param) but the file has no Role., contentDescription, semantics or Material button",
                )
            }
        }
        assertClean(findings)
        assertTrue("the heuristic saw only $seen click handlers under ui/components", seen >= 8)
    }

    @Test
    fun `every clickable modifier in the components names a role`() {
        assertClean(clickableRoleFindings(componentFiles))
    }

    // ---- Support ----------------------------------------------------------------------------

    private fun assertClean(findings: List<Finding>) {
        if (findings.isEmpty()) return
        val sorted = findings.sortedWith(compareBy({ it.file }, { it.line }))
        fail("${findings.size} copy lint finding(s):\n" + sorted.joinToString("\n") { "  $it" })
    }

    private fun quote(text: String): String = "\"" + text.replace("\n", "\\n").take(90) + "\""

    private fun lineAt(text: String, index: Int): Int = text.substring(0, index).count { it == '\n' } + 1

    private fun closingParen(code: String, open: Int): Int {
        var depth = 0
        for (j in open until code.length) {
            when (code[j]) {
                '(' -> depth++
                ')' -> if (--depth == 0) return j
            }
        }
        return code.length - 1
    }

    /** Splits a parameter list on top-level commas; `->` is not a closing angle bracket. */
    private fun splitParams(params: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        for (j in params.indices) {
            val c = params[j]
            when (c) {
                '(', '<', '[' -> depth++
                ')', ']' -> depth--
                '>' -> if (j == 0 || params[j - 1] != '-') depth--
            }
            if (c == ',' && depth == 0) {
                out += current.toString()
                current.clear()
            } else {
                current.append(c)
            }
        }
        out += current.toString()
        return out.map { it.trim() }.filter { it.isNotEmpty() }
    }
}

/**
 * A small Kotlin lexer for lint. [literals] are the string literals in order of completion
 * (escapes decoded, template expressions kept verbatim, literals nested inside templates
 * collected as well). [code] is the source with comments, literal contents and char literals
 * blanked to spaces, same length and same line breaks, so code-only rules keep their line
 * numbers; the code inside "${...}" stays.
 */
internal class KotlinScan(private val s: String) {

    data class Literal(val line: Int, val text: String)

    private val out = s.toCharArray()
    private val found = mutableListOf<Literal>()

    val literals: List<Literal>
    val code: String

    init {
        scanCode(0, s.length)
        literals = found.toList()
        code = String(out)
    }

    fun codeLines(): List<String> = code.split('\n')

    private fun lineOf(index: Int): Int {
        var n = 1
        for (j in 0 until index) if (s[j] == '\n') n++
        return n
    }

    private fun blank(from: Int, to: Int) {
        for (j in from until minOf(to, out.size)) if (out[j] != '\n' && out[j] != '\r') out[j] = ' '
    }

    private fun scanCode(from: Int, to: Int) {
        var i = from
        while (i < to) {
            i = when {
                s.startsWith("//", i) -> lineComment(i, to)
                s.startsWith("/*", i) -> blockComment(i, to)
                s[i] == '"' -> string(i, to, collect = true)
                s[i] == '\'' -> charLiteral(i, to)
                else -> i + 1
            }
        }
    }

    private fun lineComment(from: Int, to: Int): Int {
        var i = from
        while (i < to && s[i] != '\n') i++
        blank(from, i)
        return i
    }

    private fun blockComment(from: Int, to: Int): Int {
        var depth = 0
        var i = from
        while (i < to) {
            when {
                s.startsWith("/*", i) -> { depth++; i += 2 }
                s.startsWith("*/", i) -> { depth--; i += 2; if (depth == 0) break }
                else -> i++
            }
        }
        blank(from, i)
        return i
    }

    private fun charLiteral(from: Int, to: Int): Int {
        var i = from + 1
        while (i < to && s[i] != '\'' && s[i] != '\n') i += if (s[i] == '\\') 2 else 1
        val end = minOf(i + 1, to)
        blank(from, end)
        return end
    }

    /** [from] is the opening quote; returns the index after the closing quote. */
    private fun string(from: Int, to: Int, collect: Boolean): Int {
        val raw = s.startsWith("\"\"\"", from)
        val line = lineOf(from)
        val text = StringBuilder()
        var i = from + if (raw) 3 else 1
        blank(from, i)
        fun done(end: Int): Int {
            if (collect) found += Literal(line, text.toString())
            return end
        }
        while (i < to) {
            val c = s[i]
            if (raw && s.startsWith("\"\"\"", i)) {
                var end = i + 3
                while (end < to && s[end] == '"') { text.append('"'); end++ }
                blank(i, end)
                return done(end)
            }
            if (!raw && c == '"') {
                blank(i, i + 1)
                return done(i + 1)
            }
            if (!raw && c == '\n') return done(i) // unterminated: stop at the line end
            if (!raw && c == '\\' && i + 1 < to) {
                val n = s[i + 1]
                if (n == 'u' && i + 5 < to) {
                    val cp = s.substring(i + 2, i + 6).toIntOrNull(16)
                    if (cp != null) {
                        text.append(cp.toChar())
                        blank(i, i + 6)
                        i += 6
                        continue
                    }
                }
                text.append(
                    when (n) {
                        'n' -> '\n'
                        't' -> '\t'
                        'r' -> '\r'
                        'b' -> '\b'
                        else -> n
                    },
                )
                blank(i, i + 2)
                i += 2
                continue
            }
            if (c == '$' && i + 1 < to && s[i + 1] == '{') {
                val end = templateEnd(i + 1, to)
                text.append(s, i, end)
                blank(i, i + 2)
                blank(end - 1, end)
                if (collect) scanCode(i + 2, end - 1)
                i = end
                continue
            }
            text.append(c)
            blank(i, i + 1)
            i++
        }
        return done(to)
    }

    /** [from] is the '{' of a template; returns the index after its matching '}'. */
    private fun templateEnd(from: Int, to: Int): Int {
        var depth = 0
        var i = from
        while (i < to) {
            when {
                s.startsWith("//", i) -> while (i < to && s[i] != '\n') i++
                s.startsWith("/*", i) -> i = blockComment(i, to)
                s[i] == '"' -> i = string(i, to, collect = false)
                s[i] == '\'' -> i = charLiteral(i, to)
                s[i] == '{' -> { depth++; i++ }
                s[i] == '}' -> { depth--; i++; if (depth == 0) return i }
                else -> i++
            }
        }
        return to
    }
}
