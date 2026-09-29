package com.plainticker.mobile.wallet

import com.plainticker.mobile.data.Fixtures
import com.plainticker.mobile.data.KnownMints
import com.plainticker.mobile.data.MockApi
import com.plainticker.mobile.data.jupiter.JupiterSwapApi
import com.plainticker.mobile.data.respondJson
import com.plainticker.mobile.wallet.TransactionGuard.SwapReading
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * The four orders Jupiter built for the public demo wallet on 2026-09-29, while 1.3.25 on the
 * Seeker said "Swap unavailable: Jupiter has no route for this token right now" (NYSE shut,
 * pre-market Tuesday). Each one is read exactly as the swap machine reads it: parsed by
 * [JupiterSwapApi] (whose fee and rent field check throws InvalidOrder), then held by
 * [TransactionGuard.readSwap] to the request. All four must pass both: they are real, signable
 * swaps of the size the founder tried, and a guard rule that refused one would break every swap.
 *
 * They do: the report was not a guard refusal (which reads as its own sentence, never as no
 * route). What made it read as no route was the classification of /order's refusals; see
 * JupiterSwapApiTest and SwapViewModelTest for that.
 */
class LiveOrdersGuardTest {

    private val demo = "9g3mxMEfDhkX1VuNUgmuZFRj4RDiRt6CTvGUPPumUFoQ"
    private val aaplx = "XsbEhLAtcf6HdfpFZ5xEMdqW8nfAvcsP5bdudRLJzJp"

    private class Live(val file: String, val output: String, val amount: Long, val priority: Long, val rentUpperBound: Long, val creates: Int)

    private val live = listOf(
        // No AAPLx account yet: one wallet-funded create, declared 1,488,440, bounded at 2,672,640.
        Live("order-live-0929-usdc-aaplx-1.json", aaplx, 1_000_000L, 166L, 2_672_640L, 1),
        Live("order-live-0929-usdc-aaplx-5.json", aaplx, 5_000_000L, 77L, 2_672_640L, 1),
        // The (empty) TSLAx account exists from the 24 Sep swap back: no create, no rent.
        Live("order-live-0929-usdc-tslax-1.json", KnownMints.TSLAX, 1_000_000L, 103L, 0L, 0),
        Live("order-live-0929-usdc-tslax-5.json", KnownMints.TSLAX, 5_000_000L, 8L, 0L, 0),
    )

    @Test
    fun `every live order of 29 Sep parses and the guard allows it, with the bytes' own costs`() = runTest {
        for (case in live) {
            val api = JupiterSwapApi(MockApi { respondJson(Fixtures.read("jupiter/${case.file}")) }.client, retryDelayMs = 0L)
            val order = api.order(KnownMints.USDC, case.output, case.amount, demo)
            assertEquals(case.file, "metis", order.router)
            assertEquals(case.file, demo, order.taker)

            val reading = TransactionGuard.readSwap(
                Base64.getDecoder().decode(order.transaction!!), demo, order, KnownMints.USDC, case.output, case.amount,
            )

            assertTrue("${case.file}: $reading", reading is SwapReading.Allowed)
            val costs = (reading as SwapReading.Allowed).costs
            assertEquals(case.file, 5_000L, costs.signatureFeeLamports)
            assertEquals(case.file, case.priority, costs.priorityFeeLamports)
            assertEquals(case.file, case.rentUpperBound, costs.rentUpperBoundLamports)
            assertEquals(case.file, case.creates, costs.walletFundedCreates)
            assertEquals(case.file, order.rentFeeLamports, costs.rentDeclaredLamports)
        }
    }
}
