package com.plainticker.mobile.lint

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The device's own code ([com.plainticker.mobile.prefs.DevicePassStore.code]) is a bearer
 * credential: whoever holds it can sign it into a wallet's own memo and claim this device's
 * entitlement (DevicePassStore's own doc comment). The demo video for
 * docs/plan-app-uiux-2026-09-21.md is a screen recording, so a composable that ever drew the code
 * on screen would put a bearer credential into that recording.
 *
 * [DevicePassStore.codeHash] is fine: only its hash is meant to leave the device, into a memo or
 * the `X-PT-Code` header a read carries, and reading the raw code from a ViewModel to build that
 * network call is legitimate (PassViewModel, DetailViewModel). What this test refuses is a
 * `@Composable` function calling `.code()` at all, which is the one thing that could put it on a
 * screen.
 *
 * Kept in `lint`, beside CopyLintTest, so it runs as part of the same gate every other copy and
 * accessibility rule runs in, and deleting it means deleting a file named exactly for what it
 * protects rather than one line inside a larger test.
 */
class DeviceCodeNeverDrawnTest {

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private val uiRoot = File(module, "src/main/java/com/plainticker/mobile/ui")

    private fun display(file: File): String =
        "app/" + file.canonicalFile.relativeTo(module).path.replace(File.separatorChar, '/')

    private val composableFun = Regex("""@Composable\s+((?:\w+\s+)*)fun\s+(?:[\w.<>?]+\.)?(\w+)\s*\(""")

    /** `.code()`, never `.codeHash()`: the hash is the one form of it allowed to leave the device. */
    private val codeCall = Regex("""(?<!Hash)\.code\(\s*\)""")

    private fun closingBrace(code: String, open: Int): Int {
        var depth = 0
        for (j in open until code.length) {
            when (code[j]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return j + 1
                }
            }
        }
        return code.length
    }

    @Test
    fun `no composable under ui reads the device's own code`() {
        val files = uiRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.sortedBy { it.path }.toList()
        assertTrue("expected to scan the ui sources", files.size > 20)

        val findings = files.flatMap { file ->
            val scan = KotlinScan(file.readText())
            composableFun.findAll(scan.code).mapNotNull { m ->
                val open = scan.code.indexOf('{', m.range.last)
                if (open < 0) return@mapNotNull null
                val end = closingBrace(scan.code, open)
                val body = scan.code.substring(open, end)
                if (codeCall.containsMatchIn(body)) {
                    "${display(file)}: ${m.groupValues[2]}() calls .code(), a bearer credential that must never reach a composable"
                } else {
                    null
                }
            }
        }
        assertTrue(findings.joinToString("\n"), findings.isEmpty())
    }

    @Test
    fun `the lint catches a seeded violation and leaves codeHash alone`() {
        val bad = """
            @Composable
            fun Bad(store: com.plainticker.mobile.prefs.DevicePassStore) {
                Text(text = store.code())
            }
        """.trimIndent()
        val goodHash = """
            @Composable
            fun Good(store: com.plainticker.mobile.prefs.DevicePassStore) {
                val h = store.codeHash()
            }
        """.trimIndent()
        val badScan = KotlinScan(bad)
        val goodScan = KotlinScan(goodHash)
        assertTrue("the seeded violation is not caught", codeCall.containsMatchIn(badScan.code))
        assertTrue("codeHash must never be flagged", !codeCall.containsMatchIn(goodScan.code))
    }
}
