package com.myapp.data.receipts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The local record T11 reads: written once per landing, newest first, and it survives a restart. */
class FileReceiptStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun file(): File = File(temp.root, FileReceiptStore.FILE_NAME)

    private fun receipt(
        signature: String,
        landedAtMillis: Long = 1_757_600_000_000L,
        outputAmountRaw: Long = 1_366_141L,
    ) = SwapReceipt(
        signature = signature,
        inputMint = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
        inputSymbol = "USDC",
        inputAmountRaw = 5_000_000L,
        inputDecimals = 6,
        outputMint = "XsDoVfqeBukxuZHWhdvWHBhgEHjGNst4MLodqsJHzoB",
        outputSymbol = "TSLAx",
        outputAmountRaw = outputAmountRaw,
        outputDecimals = 8,
        allInCostPct = 0.586,
        route = "Metis",
        landedAtMillis = landedAtMillis,
        slot = 367_000_000L,
    )

    @Test
    fun `a fresh store is empty and reports no read failure`() {
        val store = FileReceiptStore(file())
        assertEquals(emptyList<SwapReceipt>(), store.receipts.value)
        assertFalse(store.readFailed)
    }

    @Test
    fun `a recorded receipt survives a new store over the same file`() {
        val store = FileReceiptStore(file())
        assertTrue(store.record(receipt("sig-one")))

        val reopened = FileReceiptStore(file())
        assertEquals(listOf("sig-one"), reopened.receipts.value.map { it.signature })
        val row = reopened.receipts.value.single()
        assertEquals(5_000_000L, row.inputAmountRaw)
        assertEquals(6, row.inputDecimals)
        assertEquals(1_366_141L, row.outputAmountRaw)
        assertEquals(8, row.outputDecimals)
        assertEquals(0.586, row.allInCostPct, 1e-9)
        assertEquals("Metis", row.route)
        assertEquals(367_000_000L, row.slot)
    }

    @Test
    fun `the same signature is recorded once`() {
        val store = FileReceiptStore(file())
        assertTrue(store.record(receipt("sig-one")))
        assertFalse(store.record(receipt("sig-one", landedAtMillis = 1_757_600_500_000L)))
        assertEquals(1, store.receipts.value.size)
        assertEquals(1_757_600_000_000L, store.receipts.value.single().landedAtMillis)
    }

    @Test
    fun `receipts read back newest first`() {
        val store = FileReceiptStore(file())
        store.record(receipt("older", landedAtMillis = 1_000L))
        store.record(receipt("newest", landedAtMillis = 3_000L))
        store.record(receipt("middle", landedAtMillis = 2_000L))
        assertEquals(listOf("newest", "middle", "older"), store.receipts.value.map { it.signature })
    }

    @Test
    fun `the store keeps at most its cap, dropping the oldest`() {
        val store = FileReceiptStore(file(), max = 3)
        (1..5).forEach { store.record(receipt("sig-$it", landedAtMillis = it * 1_000L)) }
        assertEquals(listOf("sig-5", "sig-4", "sig-3"), store.receipts.value.map { it.signature })
        assertEquals(3, FileReceiptStore(file(), max = 3).receipts.value.size)
    }

    @Test
    fun `an unreadable file reads as empty and says a record was lost`() {
        file().writeText("{ this is not a receipt list")
        val store = FileReceiptStore(file())
        assertTrue(store.readFailed)
        assertEquals(emptyList<SwapReceipt>(), store.receipts.value)

        // The next write replaces the unreadable file rather than refusing forever.
        assertTrue(store.record(receipt("sig-one")))
        assertFalse(store.readFailed)
        assertEquals(listOf("sig-one"), FileReceiptStore(file()).receipts.value.map { it.signature })
    }

    @Test
    fun `clear empties the file`() {
        val store = FileReceiptStore(file())
        store.record(receipt("sig-one"))
        store.clear()
        assertEquals(emptyList<SwapReceipt>(), FileReceiptStore(file()).receipts.value)
    }

    @Test
    fun `the store writes into a directory that does not exist yet`() {
        val nested = File(File(temp.root, "receipts"), FileReceiptStore.FILE_NAME)
        val store = FileReceiptStore(nested)
        assertTrue(store.record(receipt("sig-one")))
        assertTrue(nested.isFile)
    }
}
