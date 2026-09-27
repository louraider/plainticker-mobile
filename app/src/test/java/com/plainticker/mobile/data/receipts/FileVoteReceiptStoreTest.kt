package com.plainticker.mobile.data.receipts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The local record task A3 asks for: written once per landing, newest first, and it survives a
 * restart, exactly on [FileReceiptStore]'s own design (the two files are deliberately not one,
 * see [VoteReceiptStore]'s doc).
 */
class FileVoteReceiptStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun file(): File = File(temp.root, FileVoteReceiptStore.FILE_NAME)

    private fun receipt(
        signature: String,
        landedAtMillis: Long = 1_757_600_000_000L,
        ticker: String = "NFLX",
        symbol: String = "NFLXx",
        weightRaw: Long = 38_406_150_222L,
        voter: String = "9g3mxMEfDhkX1VuNUgmuZFRj4RDiRt6CTvGUPPumUFoQ",
        round: Int? = 1,
    ) = VoteReceipt(
        signature = signature,
        ticker = ticker,
        symbol = symbol,
        weightRaw = weightRaw,
        landedAtMillis = landedAtMillis,
        voter = voter,
        round = round,
    )

    @Test
    fun `a fresh store is empty and reports no read failure`() {
        val store = FileVoteReceiptStore(file())
        assertEquals(emptyList<VoteReceipt>(), store.receipts.value)
        assertFalse(store.readFailed)
    }

    @Test
    fun `a recorded receipt survives a new store over the same file, which is process death`() {
        val store = FileVoteReceiptStore(file())
        assertTrue(store.record(receipt("sig-one")))

        val reopened = FileVoteReceiptStore(file())
        assertEquals(listOf("sig-one"), reopened.receipts.value.map { it.signature })
        val row = reopened.receipts.value.single()
        assertEquals("NFLX", row.ticker)
        assertEquals("NFLXx", row.symbol)
        assertEquals(38_406_150_222L, row.weightRaw)
        assertEquals("9g3mxMEfDhkX1VuNUgmuZFRj4RDiRt6CTvGUPPumUFoQ", row.voter)
        assertEquals(1, row.round)
    }

    @Test
    fun `a vote cast with no known round survives with a null round, not a guessed one`() {
        val store = FileVoteReceiptStore(file())
        store.record(receipt("sig-one", round = null))
        assertEquals(null, FileVoteReceiptStore(file()).receipts.value.single().round)
    }

    @Test
    fun `the same signature is recorded once`() {
        val store = FileVoteReceiptStore(file())
        assertTrue(store.record(receipt("sig-one")))
        assertFalse(store.record(receipt("sig-one", landedAtMillis = 1_757_600_500_000L)))
        assertEquals(1, store.receipts.value.size)
        assertEquals(1_757_600_000_000L, store.receipts.value.single().landedAtMillis)
    }

    @Test
    fun `receipts read back newest first`() {
        val store = FileVoteReceiptStore(file())
        store.record(receipt("older", landedAtMillis = 1_000L))
        store.record(receipt("newest", landedAtMillis = 3_000L))
        store.record(receipt("middle", landedAtMillis = 2_000L))
        assertEquals(listOf("newest", "middle", "older"), store.receipts.value.map { it.signature })
    }

    @Test
    fun `the store keeps at most its cap, dropping the oldest`() {
        val store = FileVoteReceiptStore(file(), max = 3)
        (1..5).forEach { store.record(receipt("sig-$it", landedAtMillis = it * 1_000L)) }
        assertEquals(listOf("sig-5", "sig-4", "sig-3"), store.receipts.value.map { it.signature })
        assertEquals(3, FileVoteReceiptStore(file(), max = 3).receipts.value.size)
    }

    @Test
    fun `an unreadable file reads as empty and says a record was lost`() {
        file().writeText("{ this is not a receipt list")
        val store = FileVoteReceiptStore(file())
        assertTrue(store.readFailed)
        assertEquals(emptyList<VoteReceipt>(), store.receipts.value)

        assertTrue(store.record(receipt("sig-one")))
        assertFalse(store.readFailed)
        assertEquals(listOf("sig-one"), FileVoteReceiptStore(file()).receipts.value.map { it.signature })
    }

    @Test
    fun `clear empties the file`() {
        val store = FileVoteReceiptStore(file())
        store.record(receipt("sig-one"))
        store.clear()
        assertEquals(emptyList<VoteReceipt>(), FileVoteReceiptStore(file()).receipts.value)
    }

    @Test
    fun `the store writes into a directory that does not exist yet`() {
        val nested = File(File(temp.root, "receipts"), FileVoteReceiptStore.FILE_NAME)
        val store = FileVoteReceiptStore(nested)
        assertTrue(store.record(receipt("sig-one")))
        assertTrue(nested.isFile)
    }
}
