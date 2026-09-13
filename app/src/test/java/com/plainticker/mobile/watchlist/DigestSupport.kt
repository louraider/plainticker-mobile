package com.plainticker.mobile.watchlist

import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The real copy, without a device.
 *
 * The digest is words as much as it is rules, and a fake that spells its own wording would let the
 * two drift until the only place the real sentence existed was a phone. So this resolves a
 * [DigestStrings] out of the shipped strings.xml: the resource ids come from the generated `R`
 * class by reflection, the formats come off disk, and a test can therefore assert the exact string
 * the notification will carry.
 */
object RealStrings {

    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private val formats: Map<Int, String> by lazy {
        val byName = parse(File(module, "src/main/res/values/strings.xml").readText())
        val r = Class.forName("com.plainticker.mobile.R\$string")
        r.fields.mapNotNull { field ->
            byName[field.name]?.let { field.getInt(null) to it }
        }.toMap()
    }

    val strings: DigestStrings = DigestStrings { id, args ->
        val format = requireNotNull(formats[id]) { "no string resource for id $id" }
        String.format(format, *args.toTypedArray())
    }

    /** What the shipped copy says for one resource, so a test can name it rather than repeat it. */
    fun of(name: String): String = requireNotNull(parse(File(module, "src/main/res/values/strings.xml").readText())[name]) {
        "strings.xml has no $name"
    }

    private val element = Regex("""<string\b([^>]*)>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    private val nameAttribute = Regex("""\bname="([^"]*)"""")

    private fun parse(xml: String): Map<String, String> = element.findAll(xml).mapNotNull { match ->
        val name = nameAttribute.find(match.groupValues[1])?.groupValues?.get(1) ?: return@mapNotNull null
        name to decode(match.groupValues[2])
    }.toMap()

    /** XML entities and the Android escapes strings.xml carries, decoded the way aapt would. */
    private fun decode(raw: String): String {
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

class InMemoryDigestStore(initial: DigestRecord = DigestRecord.NONE) : DigestStore {
    private val _record = MutableStateFlow(initial)
    override val record: StateFlow<DigestRecord> = _record.asStateFlow()

    var writes = 0
        private set

    override fun save(record: DigestRecord) {
        writes++
        _record.value = record
    }
}

class FakeDigestNotifier(var on: Boolean = true) : DigestNotifier {
    val posted = mutableListOf<String>()

    override fun enabled(): Boolean = on

    override fun post(text: String) {
        posted += text
    }
}

/** A watched ticker with everything a test does not care about already filled in. */
fun watched(
    ticker: String,
    symbol: String? = "${ticker}x",
    company: String? = "$ticker Inc.",
    mint: String? = "mint-$ticker",
    analyzed: Boolean = true,
    nextReport: LocalDate? = null,
    priceUsd: Double? = null,
    referencePriceUsd: Double? = null,
    poolUsd: Double? = 250_000.0,
): WatchedTicker = WatchedTicker(
    ticker = ticker,
    symbol = symbol,
    company = company,
    mint = mint,
    analyzed = analyzed,
    nextReport = nextReport,
    priceUsd = priceUsd,
    referencePriceUsd = referencePriceUsd,
    poolUsd = poolUsd,
)

/** A watched ticker whose quote sits a given percentage off the NYSE close. */
fun watchedAt(
    ticker: String,
    premiumPct: Double,
    nextReport: LocalDate? = null,
    poolUsd: Double = 250_000.0,
): WatchedTicker = watched(
    ticker = ticker,
    nextReport = nextReport,
    priceUsd = 100.0 * (1.0 + premiumPct / 100.0),
    referencePriceUsd = 100.0,
    poolUsd = poolUsd,
)
