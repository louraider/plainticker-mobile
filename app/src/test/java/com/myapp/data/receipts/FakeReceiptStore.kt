package com.myapp.data.receipts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The receipts store without a file, so a ViewModel test can count what was written. */
class FakeReceiptStore : ReceiptStore {
    private val _receipts = MutableStateFlow<List<SwapReceipt>>(emptyList())
    override val receipts: StateFlow<List<SwapReceipt>> = _receipts.asStateFlow()

    /** Every call to [record], duplicates included, so a test can prove a landing wrote once. */
    val writes = mutableListOf<SwapReceipt>()

    override fun record(receipt: SwapReceipt): Boolean {
        writes += receipt
        if (_receipts.value.any { it.signature == receipt.signature }) return false
        _receipts.value = listOf(receipt) + _receipts.value
        return true
    }

    override fun clear() {
        _receipts.value = emptyList()
    }
}
