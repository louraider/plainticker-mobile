package com.plainticker.mobile.data.receipts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

/** The app's own record of the swaps it landed, newest first. Portfolio (T11) reads it. */
interface ReceiptStore {
    val receipts: StateFlow<List<SwapReceipt>>

    /**
     * Records a landed swap. The signature is the key, so recording the same landing twice
     * changes nothing and returns false: a retry, a rotation or a recomposition cannot turn one
     * swap into two rows.
     */
    fun record(receipt: SwapReceipt): Boolean

    fun clear()
}

/**
 * Receipts as one serialized file.
 *
 * Room was the alternative and was not taken. This table is append-only, is read whole by one
 * screen, holds a few hundred rows at the very most, and is never queried, joined or migrated in
 * anger. Room would bring indexes and migrations that nothing here needs, in exchange for an
 * annotation processor in the module's build graph: ksp, a schema directory, generated code, and
 * a compile step on a machine whose whole build budget is measured (docs/build-notes.md, T17).
 * A JSON file behind this interface costs one dependency the module already has, is read and
 * written in whole rows so there is no partial-update class of bug, and is unit testable on the
 * JVM against a temp file with no Robolectric and no instrumentation. If the record ever grows
 * a query, the interface is the seam that lets Room replace this class without a caller changing.
 *
 * The whole list is rewritten on every record, through a temp file and a rename, so a process
 * that dies mid-write leaves the previous file intact rather than a truncated one. A file that
 * cannot be parsed reads as no receipts: a lost record is worse than a crash only if it is
 * silent, so [readFailed] says one was lost and the next write replaces the unreadable file.
 */
class FileReceiptStore(
    private val file: File,
    private val max: Int = MAX_RECEIPTS,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : ReceiptStore {

    /** True when the file existed and could not be read, so the caller can say so instead of showing an empty list as "none". */
    var readFailed: Boolean = false
        private set

    private val _receipts = MutableStateFlow(load())
    override val receipts: StateFlow<List<SwapReceipt>> = _receipts.asStateFlow()

    override fun record(receipt: SwapReceipt): Boolean {
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

    private fun load(): List<SwapReceipt> {
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
        const val FILE_NAME = "swap-receipts.json"
        private val ListSerializer = kotlinx.serialization.builtins.ListSerializer(SwapReceipt.serializer())
    }
}
