package com.plainticker.mobile.data.receipts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The app's own record of the pass payments it landed (task A6 review), so a payment survives
 * process death exactly as [VoteReceiptStore] lets a vote survive it. A third, separate interface
 * rather than a shared one: a pass and a vote are different facts recorded by different features,
 * and one file failing to parse must never cost the other its receipts.
 */
interface PassReceiptStore {
    val receipts: StateFlow<List<PassReceipt>>

    /**
     * Records a landed payment. The signature is the key, so recording the same landing twice
     * changes nothing and returns false.
     */
    fun record(receipt: PassReceipt): Boolean

    /** Marks [signature]'s own receipt confirmed; does nothing if no receipt carries it. */
    fun markConfirmed(signature: String)

    fun clear()
}

/**
 * Pass receipts as one serialized file, on the same design as [FileVoteReceiptStore]: append-only,
 * read whole by one screen, at most a handful of rows, and never queried, joined or migrated.
 *
 * The whole list is rewritten on every write, through a temp file and a rename, so a process that
 * dies mid-write leaves the previous file intact rather than a truncated one. A file that cannot be
 * parsed reads as no receipts, the same choice [FileVoteReceiptStore] makes: a lost record is worse
 * than a crash only if it is silent, and the next write replaces the unreadable file.
 */
class FilePassReceiptStore(
    private val file: File,
    private val max: Int = MAX_RECEIPTS,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : PassReceiptStore {

    /** True when the file existed and could not be read. */
    var readFailed: Boolean = false
        private set

    private val _receipts = MutableStateFlow(load())
    override val receipts: StateFlow<List<PassReceipt>> = _receipts.asStateFlow()

    override fun record(receipt: PassReceipt): Boolean {
        if (_receipts.value.any { it.signature == receipt.signature }) return false
        _receipts.value = (listOf(receipt) + _receipts.value)
            .sortedByDescending { it.landedAtMillis }
            .take(max)
        save()
        return true
    }

    override fun markConfirmed(signature: String) {
        val current = _receipts.value
        val at = current.indexOfFirst { it.signature == signature }
        if (at < 0 || current[at].confirmed) return
        _receipts.value = current.toMutableList().also { it[at] = it[at].copy(confirmed = true) }
        save()
    }

    override fun clear() {
        _receipts.value = emptyList()
        save()
    }

    private fun load(): List<PassReceipt> {
        if (!file.isFile) return emptyList()
        return runCatching { json.decodeFromString(ListSerializer, file.readText()) }
            .onFailure { readFailed = true }
            .getOrDefault(emptyList())
            .sortedByDescending { it.landedAtMillis }
            .take(max)
    }

    private fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(json.encodeToString(ListSerializer, _receipts.value))
            if (!temp.renameTo(file)) {
                file.writeText(temp.readText())
                temp.delete()
            }
            readFailed = false
        }
    }

    companion object {
        const val MAX_RECEIPTS = 200
        const val FILE_NAME = "pass-receipts.json"
        private val ListSerializer = kotlinx.serialization.builtins.ListSerializer(PassReceipt.serializer())
    }
}
