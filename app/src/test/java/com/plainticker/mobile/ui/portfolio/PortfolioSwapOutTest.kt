package com.plainticker.mobile.ui.portfolio

import app.cash.turbine.test
import com.plainticker.mobile.MainDispatcherRule
import com.plainticker.mobile.awaitUntil
import com.plainticker.mobile.data.KnownMints
import com.plainticker.mobile.data.KnownPrograms
import com.plainticker.mobile.data.receipts.FakeReceiptStore
import com.plainticker.mobile.data.receipts.SwapReceipt
import com.plainticker.mobile.data.rpc.TokenBalance
import com.plainticker.mobile.repo.FakeCatalogRepository
import com.plainticker.mobile.repo.FakeMintRepository
import com.plainticker.mobile.repo.FakePriceRepository
import com.plainticker.mobile.repo.FakeRpcRepository
import com.plainticker.mobile.repo.mintFacts
import com.plainticker.mobile.repo.mintReading
import com.plainticker.mobile.repo.xStock
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.wallet.FakeWalletSession
import com.plainticker.mobile.wallet.testAccount
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.math.BigDecimal

/**
 * "Swap to USDC" from Portfolio (2026-09-24): which holdings offer it, the token it opens with
 * (decimals and multiplier from the mint the chain read found, never a guess), the balance read
 * again after a landing no older than its slot, and the app's own record showing split tokens
 * the way the wallet does.
 */
class PortfolioSwapOutTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val seeker = testAccount()
    private val nflxMint = "NFLXxMint".padEnd(44, '1')

    private fun position(
        amountRaw: Long = 137_000_000L,
        decimals: Int? = 8,
        multiplier: Double? = 1.0,
    ) = PortfolioPosition(
        symbol = "TSLAx",
        ticker = "TSLA",
        company = "Tesla xStock",
        mint = KnownMints.TSLAX,
        amountRaw = amountRaw,
        decimals = decimals,
        multiplier = multiplier,
        priceUsd = 363.4,
        referencePriceUsd = 363.0,
        poolUsd = 250_000.0,
    )

    private val connected = PortfolioUiState(phase = WalletPhase.CONNECTED, account = seeker, settled = true)

    @Test
    fun `a held position the chain read found offers the swap, with the mint's own decimals and multiplier`() {
        val token = requireNotNull(swapOutToken(position(multiplier = 10.0), connected))
        assertEquals(KnownMints.TSLAX, token.mint)
        assertEquals("TSLAx", token.symbol)
        assertEquals(8, token.decimals)
        assertEquals(0, BigDecimal.TEN.compareTo(token.multiplier))
    }

    @Test
    fun `no swap is offered without a wallet, without a balance, or without a readable mint`() {
        assertNull("no wallet session", swapOutToken(position(), PortfolioUiState()))
        assertNull("nothing held", swapOutToken(position(amountRaw = 0L), connected))
        assertNull("mint not read: no decimals", swapOutToken(position(decimals = null, multiplier = null), connected))
        assertNull("mint not read: no multiplier", swapOutToken(position(multiplier = null), connected))
    }

    @Test
    fun `the app's own record never offers the swap, its rows are not a chain read`() {
        val source = screenSource()
        val recorded = source.substringAfter("private fun Recorded(").substringBefore("private fun Swap(")
        assertTrue("the recorded row must not offer a swap", "SwapOutLine" !in recorded && "swapOut" !in recorded)
        val holding = source.substringAfter("private fun Holding(").substringBefore("private fun SwapOutLine(")
        assertTrue("the holding row offers it on its own line", "SwapOutLine(" in holding)
        assertTrue("never on the meta line, where it would starve the quantity", "trailingAction" !in holding)
    }

    @Test
    fun `a landing re-reads the wallet, asking for a balance no older than the landing's slot`() = runTest {
        val receipts = FakeReceiptStore()
        val rpc = FakeRpcRepository(
            balances = Result.success(listOf(balance(KnownMints.TSLAX, 137_000_000L))),
        )
        val vm = PortfolioViewModel(
            FakeWalletSession().apply { connectedAs(seeker) },
            rpc,
            FakeCatalogRepository(Result.success(listOf(xStock("TSLAx", "TSLA", KnownMints.TSLAX, "Tesla xStock")))),
            FakePriceRepository(),
            FakeMintRepository(reading = Result.success(mintReading(mintFacts(decimals = 8)))),
            receipts,
        )
        vm.state.test {
            awaitUntil { it.connected && it.settled && !it.isLoading }
            assertEquals("the first read names no slot", listOf<Long?>(null), rpc.balanceSlots)

            rpc.balances = Result.success(listOf(balance(KnownMints.TSLAX, 0L + 1L)))
            receipts.record(receipt(slot = 450_068_700L))
            val after = awaitUntil { s -> s.positions.singleOrNull()?.amountRaw == 1L }
            assertEquals(1L, after.positions.single().amountRaw)
            assertEquals("the read after the landing asks for its slot", 450_068_700L, rpc.balanceSlots.last())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a Portfolio opened after a landing asks for its slot on the very first read`() = runTest {
        // "View in Portfolio" from a fresh receipt replaces home, so this is a new ViewModel whose
        // store already holds the receipt: the first read must not be the cached pre-swap one.
        val receipts = FakeReceiptStore().apply { record(receipt(slot = 450_068_700L)) }
        val rpc = FakeRpcRepository(balances = Result.success(listOf(balance(KnownMints.TSLAX, 264_600L))))
        val vm = PortfolioViewModel(
            FakeWalletSession().apply { connectedAs(seeker) },
            rpc,
            FakeCatalogRepository(Result.success(listOf(xStock("TSLAx", "TSLA", KnownMints.TSLAX, "Tesla xStock")))),
            FakePriceRepository(),
            FakeMintRepository(reading = Result.success(mintReading(mintFacts(decimals = 8)))),
            receipts,
        )
        vm.state.test {
            awaitUntil { it.connected && it.settled && !it.isLoading }
            assertEquals(listOf<Long?>(450_068_700L), rpc.balanceSlots)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a landing re-read that the node cannot serve yet falls back rather than failing the screen`() = runTest {
        val receipts = FakeReceiptStore()
        val rpc = FakeRpcRepository(balances = Result.success(listOf(balance(KnownMints.TSLAX, 137_000_000L))))
        val vm = PortfolioViewModel(
            FakeWalletSession().apply { connectedAs(seeker) },
            rpc,
            FakeCatalogRepository(Result.success(listOf(xStock("TSLAx", "TSLA", KnownMints.TSLAX, "Tesla xStock")))),
            FakePriceRepository(),
            FakeMintRepository(reading = Result.success(mintReading(mintFacts(decimals = 8)))),
            receipts,
        )
        vm.state.test {
            awaitUntil { it.connected && it.settled && !it.isLoading }
            val notYet = Result.failure<List<TokenBalance>>(java.io.IOException("Minimum context slot has not been reached"))
            rpc.balanceQueue.addAll(listOf(notYet, notYet))
            receipts.record(receipt(slot = 450_068_700L))
            val after = awaitUntil { s -> rpc.balanceSlots.size == 4 && !s.isLoading }
            assertEquals(listOf(null, 450_068_700L, 450_068_700L, null), rpc.balanceSlots)
            assertTrue("a balance a minute old beats a banner", !after.chainUnavailable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a split xStock in the app's own swap rows reads as the wallet shows it`() {
        val nflx = SwapReceipt(
            signature = "sig",
            inputMint = KnownMints.USDC,
            inputSymbol = "USDC",
            inputAmountRaw = 5_000_000L,
            inputDecimals = 6,
            outputMint = nflxMint,
            outputSymbol = "NFLXx",
            outputAmountRaw = 5_000_000L,
            outputDecimals = 8,
            route = "Metis",
            landedAtMillis = 1L,
            outputMultiplier = 10.0,
        )
        // 5,000,000 raw at 8 decimals is 0.05 tokens; at the recorded multiplier of 10, 0.5 shares.
        assertEquals("to 0.5 NFLXx", com.plainticker.mobile.ui.ShippedCopy.render(swapRow(nflx).received))
        // A receipt written before the field existed reads as one, as those swaps were.
        assertEquals("0.05", Fmt.tokenAmount(nflx.copy(outputMultiplier = 1.0).outputUi()!!))
    }

    private fun balance(mint: String, raw: Long) = TokenBalance(
        tokenAccount = "TokenAccount".padEnd(44, '1'),
        mint = mint,
        owner = seeker.address,
        amountRaw = raw,
        decimals = 8,
        uiAmountString = null,
        programId = KnownPrograms.TOKEN_2022,
    )

    private fun receipt(slot: Long) = SwapReceipt(
        signature = "landed-$slot",
        inputMint = KnownMints.USDC,
        inputSymbol = "USDC",
        inputAmountRaw = 999_000L,
        inputDecimals = 6,
        outputMint = KnownMints.TSLAX,
        outputSymbol = "TSLAx",
        outputAmountRaw = 264_600L,
        outputDecimals = 8,
        route = "Metis",
        landedAtMillis = 2L,
        slot = slot,
    )

    private fun screenSource(): String {
        val module = listOf(".", "app").map(::File).first { File(it, "src/main/AndroidManifest.xml").isFile }
        return File(module, "src/main/java/com/plainticker/mobile/ui/portfolio/PortfolioScreen.kt").readText()
    }
}
