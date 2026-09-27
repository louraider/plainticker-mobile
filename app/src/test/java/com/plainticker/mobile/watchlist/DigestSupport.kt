package com.plainticker.mobile.watchlist

import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.ShippedCopy
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

    val strings: DigestStrings = object : DigestStrings {
        override fun get(id: Int, args: List<String>): String =
            ShippedCopy.render(Copy.Words(id, args))

        override fun quantity(id: Int, quantity: Int, args: List<String>): String =
            ShippedCopy.render(Copy.Counted(id, quantity, args))
    }

    /** What the shipped copy says for one resource, so a test can name it rather than repeat it. */
    fun of(name: String): String = requireNotNull(ShippedCopy.strings[name]) { "strings.xml has no $name" }
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
    /** Every notice posted, whole: title, body and where a tap goes. */
    val notices = mutableListOf<DigestNotice>()

    /** The titles, in order: what the shade would lead with. */
    val posted: List<String> get() = notices.map { it.title }

    override fun enabled(): Boolean = on

    override fun post(notice: DigestNotice) {
        notices += notice
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

/** The pending vote picks, in memory. */
class InMemoryPendingWatchStore(initial: Set<String> = emptySet()) : PendingWatchStore {
    private var current = initial
    override val tickers: Set<String> get() = current
    override fun add(ticker: String) { current = current + ticker }
    override fun remove(ticker: String) { current = current - ticker }
}
