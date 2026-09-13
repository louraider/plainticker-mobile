package com.myapp.watchlist

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * What the last daily check left behind.
 *
 * It is persisted rather than held in memory for the reason the whole feature exists: the
 * notification and the screen have to read the same sentence, and the notification outlives the
 * process that posted it. The screen therefore never re-derives the digest; it draws the one that
 * was actually sent.
 *
 * [premiums] and [nextReport] are the facts the run observed rather than anything it said. They
 * are updated by every run that reached the network, including one that had nothing to say, so
 * the next run compares against what was last seen and not against the last thing that was
 * announced.
 */
@Serializable
data class DigestRecord(
    /** The last digest produced, exactly as the notification carried it; null when never. */
    val text: String? = null,
    val producedAtMillis: Long? = null,
    /** When a check last ran to completion, whatever it found. Null until the first one has. */
    val lastCheckedAtMillis: Long? = null,
    /** The premiums the last completed check saw, by ticker. */
    val premiums: Map<String, Double> = emptyMap(),
    val nextReportSymbol: String? = null,
    /** The nearest report ahead as an ISO calendar day, e.g. "2026-10-28". */
    val nextReportOn: String? = null,
) {
    /** The nearest report the last check found, for the Today strip on the List. */
    val nextReport: WatchedReport?
        get() {
            val symbol = nextReportSymbol ?: return null
            val day = nextReportOn?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
            return WatchedReport(symbol, day)
        }

    /** What the run observed, kept when the run had nothing new to say. */
    fun observing(checkedAtMillis: Long, premiums: Map<String, Double>, report: WatchedReport?): DigestRecord =
        copy(
            lastCheckedAtMillis = checkedAtMillis,
            premiums = premiums,
            nextReportSymbol = report?.symbol,
            nextReportOn = report?.on?.toString(),
        )

    companion object {
        val NONE = DigestRecord()
    }
}

/** The last digest, readable by the screen and writable by the check. */
interface DigestStore {
    val record: StateFlow<DigestRecord>
    fun save(record: DigestRecord)
}

/**
 * One JSON document in the app's SharedPreferences. The record is a handful of fields written once
 * a day, so it costs less than a file of its own, and it rides the same backup rules.
 *
 * A stored document that cannot be parsed (an older shape, a half-written value) reads as no
 * digest at all rather than throwing in a worker: the worst that costs is one day of baseline.
 */
class SharedPrefsDigestStore(
    private val prefs: SharedPreferences,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : DigestStore {

    private val _record = MutableStateFlow(read())
    override val record: StateFlow<DigestRecord> = _record.asStateFlow()

    override fun save(record: DigestRecord) {
        _record.value = record
        prefs.edit().putString(KEY_DIGEST, json.encodeToString(DigestRecord.serializer(), record)).apply()
    }

    private fun read(): DigestRecord {
        val stored = prefs.getString(KEY_DIGEST, null) ?: return DigestRecord.NONE
        return runCatching { json.decodeFromString(DigestRecord.serializer(), stored) }.getOrDefault(DigestRecord.NONE)
    }

    companion object {
        const val KEY_DIGEST = "watchlist_digest"
    }
}
