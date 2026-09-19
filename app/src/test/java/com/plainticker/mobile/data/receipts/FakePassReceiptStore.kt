package com.plainticker.mobile.data.receipts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The pass receipts store without a file, so a ViewModel test can count what was written. */
class FakePassReceiptStore : PassReceiptStore {
    private val _receipts = MutableStateFlow<List<PassReceipt>>(emptyList())
    override val receipts: StateFlow<List<PassReceipt>> = _receipts.asStateFlow()

    /** Every call to [record], duplicates included, so a test can prove a landing wrote once. */
    val writes = mutableListOf<PassReceipt>()

    override fun record(receipt: PassReceipt): Boolean {
        writes += receipt
        if (_receipts.value.any { it.signature == receipt.signature }) return false
        _receipts.value = listOf(receipt) + _receipts.value
        return true
    }

    override fun markConfirmed(signature: String) {
        _receipts.value = _receipts.value.map { if (it.signature == signature) it.copy(confirmed = true) else it }
    }

    override fun clear() {
        _receipts.value = emptyList()
    }
}
