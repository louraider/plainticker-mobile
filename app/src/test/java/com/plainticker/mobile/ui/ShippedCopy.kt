package com.plainticker.mobile.ui

import java.io.File

/**
 * The shipped copy, resolved without a device.
 *
 * Unit tests here run on a plain JVM, so `getString` and `getQuantityString` do not exist and a
 * test that wanted to assert a sentence had to spell the sentence itself. That is the drift this
 * object removes: the formats come off the shipped strings.xml, the ids come from the generated
 * `R` class by reflection, and [render] resolves a [Copy] exactly the way a screen will.
 *
 * The plural rule is English's, and it is the one Android applies for en: `one` when the quantity
 * is exactly one, `other` for everything else, zero and negatives included.
 */
object ShippedCopy {

    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    val xml: String by lazy { File(module, "src/main/res/values/strings.xml").readText() }

    /** Every `<string>`, by name. */
    val strings: Map<String, String> by lazy { parseStrings(xml) }

    /** Every `<plurals>`, by name, each as its quantity keyword to its format. */
    val plurals: Map<String, Map<String, String>> by lazy { parsePlurals(xml) }

    private val stringNames: Map<Int, String> by lazy { ids("string") }
    private val pluralNames: Map<Int, String> by lazy { ids("plurals") }

    /** The one place a [Copy] becomes a string off a device. */
    fun render(copy: Copy): String = when (copy) {
        is Copy.Words -> format(stringFormat(nameOf(stringNames, copy.id, "string")), copy.args)
        is Copy.Counted ->
            format(pluralFormat(nameOf(pluralNames, copy.id, "plurals"), copy.quantity), copy.args)
        is Copy.Raw -> copy.text
    }

    /** What one `<string>` says, with its arguments filled in. */
    fun string(name: String, vararg args: String): String = format(stringFormat(name), args.toList())

    /** What one `<plurals>` says at [quantity], with its arguments filled in. */
    fun plural(name: String, quantity: Int, vararg args: String): String =
        format(pluralFormat(name, quantity), args.toList())

    /** The form [quantity] selects, as Android selects it for English. */
    fun quantityKeyword(quantity: Int): String = if (quantity == 1) "one" else "other"

    private fun stringFormat(name: String): String =
        requireNotNull(strings[name]) { "strings.xml has no string named $name" }

    private fun pluralFormat(name: String, quantity: Int): String {
        val forms = requireNotNull(plurals[name]) { "strings.xml has no plurals named $name" }
        val keyword = quantityKeyword(quantity)
        return requireNotNull(forms[keyword]) { "the plurals $name has no $keyword form" }
    }

    private fun nameOf(names: Map<Int, String>, id: Int, kind: String): String =
        requireNotNull(names[id]) { "no $kind resource carries the id $id" }

    private fun format(pattern: String, args: List<String>): String =
        String.format(pattern, *args.toTypedArray())

    private fun ids(kind: String): Map<Int, String> = runCatching {
        Class.forName("com.plainticker.mobile.R\$$kind").fields.associate { it.getInt(null) to it.name }
    }.getOrElse { emptyMap() }

    // ---- The file ---------------------------------------------------------------------------

    private val stringElement = Regex("""<string\b([^>]*)>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    private val pluralsElement = Regex("""<plurals\b([^>]*)>(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
    private val itemElement = Regex("""<item\b([^>]*)>(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
    private val nameAttribute = Regex("""\bname="([^"]*)"""")
    private val quantityAttribute = Regex("""\bquantity="([^"]*)"""")

    private fun parseStrings(xml: String): Map<String, String> =
        stringElement.findAll(xml).mapNotNull { match ->
            val name = nameAttribute.find(match.groupValues[1])?.groupValues?.get(1) ?: return@mapNotNull null
            name to decode(match.groupValues[2])
        }.toMap()

    private fun parsePlurals(xml: String): Map<String, Map<String, String>> =
        pluralsElement.findAll(xml).mapNotNull { match ->
            val name = nameAttribute.find(match.groupValues[1])?.groupValues?.get(1) ?: return@mapNotNull null
            val forms = itemElement.findAll(match.groupValues[2]).mapNotNull { item ->
                val quantity = quantityAttribute.find(item.groupValues[1])?.groupValues?.get(1)
                    ?: return@mapNotNull null
                quantity to decode(item.groupValues[2])
            }.toMap()
            name to forms
        }.toMap()

    /** XML entities and the Android escapes strings.xml carries, decoded the way aapt would. */
    fun decode(raw: String): String {
        var text = raw
        text = Regex("&#x([0-9A-Fa-f]+);").replace(text) { String(Character.toChars(it.groupValues[1].toInt(16))) }
        text = Regex("&#([0-9]+);").replace(text) { String(Character.toChars(it.groupValues[1].toInt())) }
        text = text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace("&apos;", "'").replace("&amp;", "&")
        return Regex("""\\(.)""").replace(text) {
            when (it.groupValues[1]) {
                "n" -> "\n"
                "t" -> "\t"
                else -> it.groupValues[1]
            }
        }
    }
}
