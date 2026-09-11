package com.myapp.ui.portfolio

import app.cash.turbine.test
import com.myapp.MainDispatcherRule
import com.myapp.awaitUntil
import com.myapp.data.KnownMints
import com.myapp.data.KnownPrograms
import com.myapp.data.rpc.TokenBalance
import com.myapp.repo.FakeCatalogRepository
import com.myapp.repo.FakePriceRepository
import com.myapp.repo.FakeRpcRepository
import com.myapp.repo.price
import com.myapp.repo.xStock
import com.myapp.wallet.FakeWalletSession
import com.myapp.wallet.WalletOutcome
import com.myapp.wallet.testAccount
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class PortfolioViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val seeker = testAccount()
    private val tslaMint = KnownMints.TSLAX
    private val nflxMint = "NFLXxMint".padEnd(44, '1')
    private val unknownMint = "SomeOtherMint".padEnd(44, '1')

    private fun balance(mint: String, raw: Long, decimals: Int, program: String = KnownPrograms.TOKEN_2022) = TokenBalance(
        tokenAccount = "TokenAccount".padEnd(44, '1'),
        mint = mint,
        owner = seeker.address,
        amountRaw = raw,
        decimals = decimals,
        uiAmountString = null,
        programId = program,
    )

    private fun catalog() = FakeCatalogRepository(
        Result.success(listOf(xStock("TSLAx", "TSLA", tslaMint, "Tesla xStock"), xStock("NFLXx", "NFLX", nflxMint, "Netflix xStock"))),
        multipliers = mapOf("TSLAx" to 1.0, "NFLXx" to 10.0),
    )

    private fun chain() = FakeRpcRepository(
        balances = Result.success(
            listOf(
                balance(tslaMint, 137_000_000L, 8),
                balance(nflxMint, 50_000_000L, 8),
                balance(unknownMint, 5L, 0),
                balance(KnownMints.USDC, 10_000_000L, 6, KnownPrograms.TOKEN),
            ),
        ),
        lamports = Result.success(2_400_000L),
    )

    @Test
    fun `disconnected until connect succeeds, then only catalog mints become positions`() = runTest {
        val wallet = FakeWalletSession().apply { enqueue(WalletOutcome.Success(seeker)) }
        val rpc = chain()
        val prices = FakePriceRepository(Result.success(mapOf(tslaMint to price(363.4), nflxMint to price(120.0))))
        val vm = PortfolioViewModel(wallet, rpc, catalog(), prices)

        vm.state.test {
            val initial = awaitItem()
            assertFalse(initial.isConnected)
            assertFalse(initial.isLoading)

            vm.connect()
            val loaded = awaitUntil { it.isConnected && !it.isLoading }

            assertEquals(seeker, loaded.account)
            assertNull(loaded.error)
            assertFalse(loaded.pricesUnavailable)
            assertEquals(2_400_000L, loaded.lamports)

            // USDC and the unknown mint are never listed; xStocks sort by value descending.
            assertEquals(listOf("NFLXx", "TSLAx"), loaded.positions.map { it.symbol })

            val tsla = loaded.positions[1]
            assertEquals("TSLA", tsla.ticker)
            assertEquals(137_000_000L, tsla.amountRaw)
            assertEquals(1.37, tsla.quantity, 1e-9)
            assertEquals(1.37 * 363.4, tsla.valueUsd!!, 1e-6)

            // quantity = raw x multiplier / 10^decimals
            val nflx = loaded.positions[0]
            assertEquals(10.0, nflx.multiplier, 0.0)
            assertEquals(5.0, nflx.quantity, 1e-9)
            assertEquals(600.0, nflx.valueUsd!!, 1e-6)

            assertEquals(600.0 + 1.37 * 363.4, loaded.totalUsd!!, 1e-6)
            assertEquals(listOf(seeker.address), rpc.balanceOwners)
            assertEquals(listOf(listOf(tslaMint, nflxMint)), prices.requested)
            assertEquals(1, wallet.connectCount)
            assertEquals(0, wallet.remaining)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancelling in the wallet leaves the screen disconnected with a neutral note`() = runTest {
        val wallet = FakeWalletSession().apply { enqueue(WalletOutcome.Cancelled) }
        val rpc = chain()
        val vm = PortfolioViewModel(wallet, rpc, catalog(), FakePriceRepository())

        vm.state.test {
            awaitItem()
            vm.connect()
            val state = awaitUntil { it.message != null }
            assertEquals("Cancelled in wallet", state.message)
            assertFalse(state.isConnected)
            assertTrue(rpc.balanceOwners.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no wallet on the device is said plainly`() = runTest {
        val wallet = FakeWalletSession().apply { enqueue(WalletOutcome.NoWallet) }
        val vm = PortfolioViewModel(wallet, chain(), catalog(), FakePriceRepository())

        vm.state.test {
            awaitItem()
            vm.connect()
            assertEquals("No compatible wallet found", awaitUntil { it.message != null }.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a wallet error carries its message`() = runTest {
        val wallet = FakeWalletSession().apply { enqueue(WalletOutcome.Error("Timed out while waiting for result")) }
        val vm = PortfolioViewModel(wallet, chain(), catalog(), FakePriceRepository())

        vm.state.test {
            awaitItem()
            vm.connect()
            assertEquals("Timed out while waiting for result", awaitUntil { it.message != null }.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an already authorized seeker loads without a wallet round-trip`() = runTest {
        val wallet = FakeWalletSession().apply { connectedAs(seeker) }
        val vm = PortfolioViewModel(wallet, chain(), catalog(), FakePriceRepository(Result.success(mapOf(tslaMint to price(363.4)))))

        vm.state.test {
            val loaded = awaitUntil { it.isConnected && !it.isLoading }
            assertEquals(2, loaded.positions.size)
            assertEquals(0, wallet.connectCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the forwarder failing is an error, the wallet stays connected`() = runTest {
        val wallet = FakeWalletSession().apply { connectedAs(seeker) }
        val rpc = FakeRpcRepository(balances = Result.failure(IOException("502")), lamports = Result.failure(IOException("502")))
        val vm = PortfolioViewModel(wallet, rpc, catalog(), FakePriceRepository())

        vm.state.test {
            val state = awaitUntil { it.isConnected && !it.isLoading }
            assertEquals("On-chain data unavailable", state.error)
            assertTrue(state.positions.isEmpty())
            assertNull(state.lamports)
            assertFalse(state.isEmpty)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `prices down - positions render unvalued with a partial flag`() = runTest {
        val wallet = FakeWalletSession().apply { connectedAs(seeker) }
        val vm = PortfolioViewModel(wallet, chain(), catalog(), FakePriceRepository(Result.failure(IOException("429"))))

        vm.state.test {
            val state = awaitUntil { it.isConnected && !it.isLoading }
            assertTrue(state.pricesUnavailable)
            assertEquals(2, state.positions.size)
            assertTrue(state.positions.all { it.valueUsd == null && it.priceUsd == null })
            assertNull(state.totalUsd)
            // Unvalued positions fall back to symbol order.
            assertEquals(listOf("NFLXx", "TSLAx"), state.positions.map { it.symbol })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a wallet with no xStocks is empty, not an error`() = runTest {
        val wallet = FakeWalletSession().apply { connectedAs(seeker) }
        val rpc = FakeRpcRepository(balances = Result.success(listOf(balance(KnownMints.USDC, 10_000_000L, 6, KnownPrograms.TOKEN))))
        val prices = FakePriceRepository()
        val vm = PortfolioViewModel(wallet, rpc, catalog(), prices)

        vm.state.test {
            val state = awaitUntil { it.isConnected && !it.isLoading }
            assertTrue(state.isEmpty)
            assertNull(state.error)
            assertTrue(prices.requested.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `disconnecting resets to the disconnected state`() = runTest {
        val wallet = FakeWalletSession().apply {
            connectedAs(seeker)
            enqueue(WalletOutcome.Success(Unit))
        }
        val vm = PortfolioViewModel(wallet, chain(), catalog(), FakePriceRepository())

        vm.state.test {
            awaitUntil { it.isConnected && !it.isLoading }
            vm.disconnect()
            val state = awaitUntil { !it.isConnected }
            assertTrue(state.positions.isEmpty())
            assertNull(state.error)
            assertEquals(1, wallet.disconnectCount)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
