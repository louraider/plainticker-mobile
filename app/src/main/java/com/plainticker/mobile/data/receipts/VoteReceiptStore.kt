package com.plainticker.mobile.data.receipts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The app's own record of the votes it landed (task A3), so "Your votes" survives process death
 * exactly as [ReceiptStore] lets the swap receipts survive it. The two are separate files and
 * separate interfaces, not because the shapes differ much, but because a vote and a swap are
 * different facts recorded by different features, and one file failing to parse must never cost
 * the other its receipts.
 */
interface VoteReceiptStore {
    val receipts: StateFlow<List<VoteReceipt>>

    /**
     * Records a landed vote. The signature is the key, so recording the same landing twice
     * changes nothing and returns false: a retry, a rotation or a recomposition cannot turn one
     * vote into two rows.
     */
    fun record(receipt: VoteReceipt): Boolean

    fun clear()
}

/**
 * Vote receipts as one serialized file, on the same design as [FileReceiptStore]: append-only,
 * read whole by one screen, a few hundred rows at the very most, and never queried, joined or
 * migrated, so a JSON file behind this interface costs nothing Room would not, and is unit
 * testable on the JVM against a temp file with no Robolectric and no instrumentation.
 *
 * The whole list is rewritten on every record, through a temp file and a rename, so a process
 * that dies mid-write leaves the previous file intact rather than a truncated one. A file that
 * cannot be parsed reads as no receipts: a lost record is worse than a crash only if it is
 * silent, so [readFailed] says one was lost and the next write replaces the unreadable file.
 */
class FileVoteReceiptStore(
    private val file: File,
    private val max: Int = MAX_RECEIPTS,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : VoteReceiptStore {

    /** True when the file existed and could not be read, so the caller can say so instead of showing an empty list as "none". */
    var readFailed: Boolean = false
        private set

    private val _receipts = MutableStateFlow(load())
    override val receipts: StateFlow<List<VoteReceipt>> = _receipts.asStateFlow()

    override fun record(receipt: VoteReceipt): Boolean {
        if (_receipts.value.any { it.signature == receipt.signature }) return false
        _receipts.value = (listOf(receipt) + _receipts.value)
            .sortedByDescending { it.landedAtMillis }
            .take(max)
        save()
        return true
    }

    override fun clear() {
        _receipts.value = emptyList()
        save()
    }

    private fun load(): List<VoteReceipt> {
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
        /** The record a person can act on is the recent one; older rows are dropped rather than grown forever. */
        const val MAX_RECEIPTS = 200
        const val FILE_NAME = "vote-receipts.json"
        private val ListSerializer = kotlinx.serialization.builtins.ListSerializer(VoteReceipt.serializer())
    }
}
