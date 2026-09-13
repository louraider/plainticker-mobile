package com.plainticker.mobile.wallet

import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.mobilewalletadapter.clientlib.protocol.JsonRpc20Client
import com.solana.mobilewalletadapter.common.ProtocolContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException

class WalletOutcomeMappingTest {

    private fun failure(message: String, e: Exception): TransactionResult<Unit> = TransactionResult.Failure(message, e)

    @Test
    fun `success carries the payload`() {
        val outcome = TransactionResult.Success("signed").toWalletOutcome()
        assertEquals(WalletOutcome.Success("signed"), outcome)
        assertEquals("signed", outcome.valueOrNull)
    }

    @Test
    fun `no wallet on the device`() {
        val outcome = TransactionResult.NoWalletFound<Unit>("No compatible wallet found.").toWalletOutcome()
        assertSame(WalletOutcome.NoWallet, outcome)
        assertNull(outcome.valueOrNull)
    }

    @Test
    fun `backing out of the wallet activity is Cancelled`() {
        // MobileWalletAdapter: RESULT_CANCELED -> "Request was interrupted" + InterruptedException
        assertSame(WalletOutcome.Cancelled, failure("Request was interrupted", InterruptedException()).toWalletOutcome())
    }

    @Test
    fun `declining to sign is Cancelled`() {
        val declined = JsonRpc20Client.JsonRpc20RemoteException(ProtocolContract.ERROR_NOT_SIGNED, "not signed", null)
        assertSame(WalletOutcome.Cancelled, failure("User did not authorize signing", declined).toWalletOutcome())
    }

    @Test
    fun `declining authorization is Cancelled`() {
        val declined = JsonRpc20Client.JsonRpc20RemoteException(ProtocolContract.ERROR_AUTHORIZATION_FAILED, "declined", null)
        assertSame(WalletOutcome.Cancelled, failure("Auth token invalid", declined).toWalletOutcome())
    }

    @Test
    fun `a wrapped ExecutionException is unwrapped before deciding`() {
        val declined = JsonRpc20Client.JsonRpc20RemoteException(ProtocolContract.ERROR_NOT_SIGNED, "not signed", null)
        assertSame(WalletOutcome.Cancelled, failure("Execution exception", ExecutionException(declined)).toWalletOutcome())

        val io = failure("Execution exception", ExecutionException(IOException("socket closed"))).toWalletOutcome()
        assertTrue(io is WalletOutcome.Error)
    }

    @Test
    fun `a cancelled coroutine is Cancelled`() {
        assertSame(WalletOutcome.Cancelled, failure("Request was cancelled", CancellationException()).toWalletOutcome())
    }

    @Test
    fun `other remote codes are errors that keep message and cause`() {
        val tooMany = JsonRpc20Client.JsonRpc20RemoteException(ProtocolContract.ERROR_TOO_MANY_PAYLOADS, "too many", null)
        val outcome = failure("Too many payloads to sign", tooMany).toWalletOutcome()
        assertTrue(outcome is WalletOutcome.Error)
        outcome as WalletOutcome.Error
        assertEquals("Too many payloads to sign", outcome.message)
        assertSame(tooMany, outcome.cause)
    }

    @Test
    fun `timeouts and IO failures are errors, not cancellations`() {
        assertTrue(failure("Timed out while waiting for result", TimeoutException()).toWalletOutcome() is WalletOutcome.Error)
        assertTrue(failure("IO error while sending operation", IOException()).toWalletOutcome() is WalletOutcome.Error)
        assertFalse(isUserCancel(IllegalStateException()))
    }

    @Test
    fun `map transforms only success`() {
        assertEquals(WalletOutcome.Success(4), WalletOutcome.Success("four").map { it.length })
        assertSame(WalletOutcome.Cancelled, WalletOutcome.Cancelled.map { 1 })
        val error = WalletOutcome.Error("x")
        assertSame(error, error.map { 1 })
    }

    @Test
    fun `WalletAccount compares by key bytes and derives a base58 address`() {
        val a = testAccount(fill = 0, label = null)
        val b = testAccount(fill = 0, label = null)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals("11111111111111111111111111111111", a.address)
        assertFalse(a == testAccount(fill = 1, label = null))
    }
}
