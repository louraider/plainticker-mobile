package com.myapp.ui.portfolio

import app.cash.turbine.test
import com.myapp.MainDispatcherRule
import com.myapp.awaitUntil
import com.myapp.data.KnownMints
import com.myapp.data.KnownPrograms
import com.myapp.data.jupiter.TrackingQuality
import com.myapp.data.receipts.FakeReceiptStore
import com.myapp.data.receipts.SwapReceipt
import com.myapp.data.rpc.TokenBalance
import com.myapp.repo.FakeCatalogRepository
import com.myapp.repo.FakeMintRepository
import com.myapp.repo.FakePriceRepository
import com.myapp.repo.FakeRpcRepository
import com.myapp.repo.mintFacts
import com.myapp.repo.mintReading
import com.myapp.repo.price
import com.myapp.repo.scaled
import com.myapp.repo.xStock
import com.myapp.ui.Fmt
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

/**
 * Task T11. Every state of the screen, against fakes: the multiplier applied to a real raw
 * amount, a mint that could not be read, a pool under the liquidity floor, a position nobody can
 * price, the chain out, the wallet's four outcomes, and the receipts standing on their own.
 */
class PortfolioViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val seeker = testAccount()
    private val tslaMint = KnownMints.TSLAX
    private val nflxMint = "NFLXxMint".padEnd(44, '1')
    private val appMint = "APPxMint".padEnd(44, '1')
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
        Result.success(
            listOf(
                xStock("TSLAx", "TSLA", tslaMint, "Tesla xStock"),
                xStock("NFLXx", "NFLX", nflxMint, "Netflix xStock"),
                xStock("APPx", "APP", appMint, "AppLovin xStock"),
            ),
        ),
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
    )

    /**
     * Both mints readable: TSLAx is not rescaled, NFLXx carries a 10x scaled amount, which is the
     * extension this product exists to read.
     */
    private fun readableMints() = FakeMintRepository(
        readings = mapOf(
            tslaMint to Result.success(mintReading(mintFacts(decimals = 8))),
            nflxMint to Result.success(mintReading(mintFacts(decimals = 8, scaledUiAmount = scaled(multiplier = 10.0)))),
            appMint to Result.success(mintReading(mintFacts(decimals = 8))),
        ),
    )

    private fun receipt(
        signature: String,
        landedAtMillis: Long,
        outputAmountRaw: Long? = 1_364_000L,
        allInCostPct: Double? = 0.09,
    ) = SwapReceipt(
        signature = signature,
        inputMint = KnownMints.USDC,
        inputSymbol = "USDC",
        inputAmountRaw = 5_000_000L,
        inputDecimals = 6,
        outputMint = tslaMint,
        outputSymbol = "TSLAx",
        outputAmountRaw = outputAmountRaw,
        outputDecimals = 8,
        allInCostPct = allInCostPct,
        route = "Metis",
        landedAtMillis = landedAtMillis,
    )

    private fun viewModel(
        wallet: FakeWalletSession = FakeWalletSession().apply { connectedAs(seeker) },
        rpc: FakeRpcRepository = chain(),
        catalog: FakeCatalogRepository = catalog(),
        prices: FakePriceRepository = FakePriceRepository(),
        mints: FakeMintRepository = readableMints(),
        receipts: FakeReceiptStore = FakeReceiptStore(),
    ) = PortfolioViewModel(wallet, rpc, catalog, prices, mints, receipts)

    // ---- Holdings from the chain -----------------------------------------------------------

    @Test
    fun `the quantity is the raw amount scaled by the mint's own multiplier`() = runTest {
        val prices = FakePriceRepository(
            Result.success(mapOf(tslaMint to price(363.4, reference = 363.0), nflxMint to price(120.0))),
        )
        val mints = readableMints()
        val rpc = chain()
        val vm = viewModel(rpc = rpc, prices = prices, mints = mints)

        vm.state.test {
            val loaded = awaitUntil { it.connected && it.settled && !it.isLoading }

            // USDC and the mint no xStock claims are never listed; the rest sort by value.
            assertEquals(listOf("NFLXx", "TSLAx"), loaded.positions.map { it.symbol })

            val tsla = loaded.positions.single { it.symbol == "TSLAx" }
            assertEquals(137_000_000L, tsla.amountRaw)
            assertEquals(8, tsla.decimals)
            // No scaledUiAmount extension on this mint is a multiplier of one read off the chain.
            assertEquals(1.0, tsla.multiplier!!, 0.0)
            assertEquals("1.37", Fmt.tokenAmount(tsla.quantity!!))
            assertEquals(1.37 * 363.4, tsla.valueUsd!!, 1e-6)

            // 50_000_000 raw at 8 decimals is 0.5 tokens; the mint's 10x makes it 5 shares.
            val nflx = loaded.positions.single { it.symbol == "NFLXx" }
            assertEquals(10.0, nflx.multiplier!!, 0.0)
            assertEquals("5", Fmt.tokenAmount(nflx.quantity!!))
            assertEquals(600.0, nflx.valueUsd!!, 1e-6)

            assertEquals(600.0 + 1.37 * 363.4, loaded.totalUsd!!, 1e-6)
            assertEquals(2, loaded.valuedCount)
            // Both token programs are read by the repository; only held mints are asked for.
            assertEquals(listOf(seeker.address), rpc.balanceOwners)
            assertEquals(listOf(tslaMint, nflxMint), mints.asked)
            assertEquals(listOf(listOf(tslaMint, nflxMint)), prices.requested)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a mint that could not be read has no quantity and no value, and never a multiplier of one`() = runTest {
        val mints = FakeMintRepository(
            reading = Result.failure(IOException("502")),
            readings = mapOf(tslaMint to Result.success(mintReading(mintFacts(decimals = 8)))),
        )
        val prices = FakePriceRepository(
            Result.success(mapOf(tslaMint to price(363.4), nflxMint to price(120.0))),
        )
        val vm = viewModel(prices = prices, mints = mints)

        vm.state.test {
            val loaded = awaitUntil { it.connected && it.settled && !it.isLoading }

            val nflx = loaded.positions.single { it.symbol == "NFLXx" }
            assertFalse(nflx.mintRead)
            assertNull(nflx.multiplier)
            assertNull(nflx.decimals)
            assertNull(nflx.quantity)
            assertNull(nflx.valueUsd)
            // It is still a held position and is still listed, priced or not.
            assertEquals(50_000_000L, nflx.amountRaw)

            // The total covers the one position that could be valued, and says so by its count.
            assertEquals(1.37 * 363.4, loaded.totalUsd!!, 1e-6)
            assertEquals(1, loaded.valuedCount)
            assertEquals(2, loaded.positions.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a mint that answers with something that is not a Token-2022 mint reads the same as unread`() = runTest {
        val mints = FakeMintRepository(
            readings = mapOf(
                tslaMint to Result.success(mintReading(facts = null)),
                nflxMint to Result.success(mintReading(mintFacts(decimals = 8))),
            ),
        )
        val vm = viewModel(prices = FakePriceRepository(Result.success(mapOf(tslaMint to price(363.4)))), mints = mints)

        vm.state.test {
            val loaded = awaitUntil { it.connected && it.settled && !it.isLoading }
            val tsla = loaded.positions.single { it.symbol == "TSLAx" }
            assertFalse(tsla.mintRead)
            assertNull(tsla.valueUsd)
            assertNull(loaded.totalUsd)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `decimals come from the mint, not from the token account`() = runTest {
        // The node's token account says 6 decimals; the mint says 8. The mint decides.
        val rpc = FakeRpcRepository(balances = Result.success(listOf(balance(tslaMint, 137_000_000L, decimals = 6))))
        val vm = viewModel(rpc = rpc, prices = FakePriceRepository(Result.success(mapOf(tslaMint to price(1.0)))))

        vm.state.test {
            val loaded = awaitUntil { it.connected && it.settled && !it.isLoading }
            val tsla = loaded.positions.single()
            assertEquals(8, tsla.decimals)
            assertEquals("1.37", Fmt.tokenAmount(tsla.quantity!!))
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- Value and tracking ----------------------------------------------------------------

    @Test
    fun `a pool under the liquidity floor states its size instead of a premium`() = runTest {
        // APPx as it read live on 2026-09-12: +89.34 percent quoted off a pool holding $34.
        val rpc = FakeRpcRepository(balances = Result.success(listOf(balance(appMint, 50_000_000L, 8))))
        val prices = FakePriceRepository(
            Result.success(mapOf(appMint to price(1_158.76, reference = 612.00, liquidity = 34.0))),
        )
        val vm = viewModel(rpc = rpc, prices = prices)

        vm.state.test {
            val loaded = awaitUntil { it.connected && it.settled && !it.isLoading }
            val app = loaded.positions.single()
            val tracking = app.tracking
            assertTrue("expected a thin pool, was $tracking", tracking is TrackingQuality.Thin)
            assertEquals(34.0, (tracking as TrackingQuality.Thin).poolUsd, 0.0)
            assertNull("no premium may be drawn off a dead pool", tracking.premiumPct)
            // The value is unaffected: the holding is still worth what Jupiter quotes.
            assertEquals(0.5 * 1_158.76, app.valueUsd!!, 1e-6)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a token priced without a reported depth is untracked rather than deep`() = runTest {
        val rpc = FakeRpcRepository(balances = Result.success(listOf(balance(tslaMint, 137_000_000L, 8))))
        val prices = FakePriceRepository(
            Result.success(mapOf(tslaMint to price(363.4, reference = 363.0, liquidity = null))),
        )
        val vm = viewModel(rpc = rpc, prices = prices)

        vm.state.test {
            val loaded = awaitUntil { it.connected && it.settled && !it.isLoading }
            assertEquals(TrackingQuality.Untracked, loaded.positions.single().tracking)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a position nobody can price keeps its quantity and carries no value`() = runTest {
        val prices = FakePriceRepository(Result.success(mapOf(tslaMint to price(363.4))))
        val vm = viewModel(prices = prices)

        vm.state.test {
            val loaded = awaitUntil { it.connected && it.settled && !it.isLoading }
            val nflx = loaded.positions.single { it.symbol == "NFLXx" }
            assertNull(nflx.priceUsd)
            assertNull(nflx.valueUsd)
            assertNull(nflx.tracking)
            assertEquals("5", Fmt.tokenAmount(nflx.quantity!!))

            // The total covers the priced one only, and the count on screen says so.
            assertEquals(1.37 * 363.4, loaded.totalUsd!!, 1e-6)
            assertEquals(1, loaded.valuedCount)
            assertEquals(2, loaded.positions.size)
            // Jupiter answered about every mint, so nothing is reported as missing.
            assertFalse(loaded.pricesPartial)
            assertFalse(loaded.pricesUnavailable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Jupiter refusing every chunk leaves the positions standing and the total absent`() = runTest {
        val prices = FakePriceRepository(Result.failure(IOException("429")))
        val vm = viewModel(prices = prices)

        vm.state.test {
            val loaded = awaitUntil { it.connected && it.settled && !it.isLoading }
            assertTrue(loaded.pricesUnavailable)
            assertEquals(PortfolioBanner.PricesUnavailable, loaded.banner)
            assertEquals(2, loaded.positions.size)
            assertTrue(loaded.positions.all { it.valueUsd == null })
            assertNull(loaded.totalUsd)
            // Unvalued positions fall back to symbol order.
            assertEquals(listOf("NFLXx", "TSLAx"), loaded.positions.map { it.symbol })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Jupiter refusing one chunk is partial, not unavailable`() = runTest {
        val prices = FakePriceRepository(
            Result.success(mapOf(tslaMint to price(363.4))),
            unfetched = setOf(nflxMint),
        )
        val vm = viewModel(prices = prices)

        vm.state.test {
            val loaded = awaitUntil { it.connected && it.settled && !it.isLoading }
            assertTrue(loaded.pricesPartial)
            assertFalse(loaded.pricesUnavailable)
            assertEquals(PortfolioBanner.PricesPartial, loaded.banner)
            assertEquals(1, loaded.valuedCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- States ----------------------------------------------------------------------------

    @Test
    fun `disconnected until connect succeeds`() = runTest {
        val wallet = FakeWalletSession().apply { enqueue(WalletOutcome.Success(seeker)) }
        val rpc = chain()
        val vm = viewModel(wallet = wallet, rpc = rpc, prices = FakePriceRepository(Result.success(mapOf(tslaMint to price(363.4)))))

        vm.state.test {
            val initial = awaitItem()
            assertEquals(WalletPhase.DISCONNECTED, initial.phase)
            assertFalse(initial.connected)
            assertFalse(initial.isEmpty)
            assertTrue(rpc.balanceOwners.isEmpty())

            vm.connect()
            val loaded = awaitUntil { it.connected && it.settled && !it.isLoading }
            assertEquals(seeker, loaded.account)
            assertNull(loaded.banner)
            assertEquals(listOf(seeker.address), rpc.balanceOwners)
            assertEquals(1, wallet.connectCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancelling in the wallet leaves the screen disconnected with a neutral note`() = runTest {
        val wallet = FakeWalletSession().apply { enqueue(WalletOutcome.Cancelled) }
        val rpc = chain()
        val vm = viewModel(wallet = wallet, rpc = rpc)

        vm.state.test {
            awaitItem()
            vm.connect()
            val state = awaitUntil { it.note != null }
            assertEquals(WalletNote.CANCELLED, state.note)
            assertEquals(PortfolioBanner.Wallet(WalletNote.CANCELLED), state.banner)
            assertEquals(WalletPhase.DISCONNECTED, state.phase)
            assertTrue(rpc.balanceOwners.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no wallet on the device, and a wallet that refuses, each have their own sentence`() = runTest {
        val wallet = FakeWalletSession().apply {
            enqueue(WalletOutcome.NoWallet, WalletOutcome.Error("Timed out while waiting for result"))
        }
        val vm = viewModel(wallet = wallet)

        vm.state.test {
            awaitItem()
            vm.connect()
            assertEquals(WalletNote.NONE_ON_DEVICE, awaitUntil { it.note != null }.note)
            vm.connect()
            assertEquals(WalletNote.REFUSED, awaitUntil { it.note == WalletNote.REFUSED }.note)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an already authorized wallet loads with no round trip`() = runTest {
        val wallet = FakeWalletSession().apply { connectedAs(seeker) }
        val vm = viewModel(wallet = wallet, prices = FakePriceRepository(Result.success(mapOf(tslaMint to price(363.4)))))

        vm.state.test {
            val loaded = awaitUntil { it.connected && it.settled && !it.isLoading }
            assertEquals(2, loaded.positions.size)
            assertEquals(0, wallet.connectCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a wallet with no xStocks is empty, not an error, and asks Jupiter nothing`() = runTest {
        val rpc = FakeRpcRepository(
            balances = Result.success(listOf(balance(KnownMints.USDC, 10_000_000L, 6, KnownPrograms.TOKEN))),
        )
        val prices = FakePriceRepository()
        val mints = readableMints()
        val vm = viewModel(rpc = rpc, prices = prices, mints = mints)

        vm.state.test {
            val state = awaitUntil { it.connected && it.settled && !it.isLoading }
            assertTrue(state.isEmpty)
            assertNull(state.banner)
            assertTrue(state.positions.isEmpty())
            assertTrue("no mints to price, so Jupiter is not called", prices.requested.isEmpty())
            assertTrue(mints.asked.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the forwarder failing raises the chain banner and the wallet stays connected`() = runTest {
        val rpc = FakeRpcRepository(balances = Result.failure(IOException("502")))
        val vm = viewModel(rpc = rpc)

        vm.state.test {
            val state = awaitUntil { it.connected && !it.isLoading && it.chainUnavailable }
            assertEquals(PortfolioBanner.ChainUnavailable, state.banner)
            assertTrue(state.positions.isEmpty())
            // Not "this wallet holds nothing": the screen never read it.
            assertFalse(state.isEmpty)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the catalog failing is its own banner, because no balance can be named without it`() = runTest {
        val catalog = FakeCatalogRepository(Result.failure(IOException("503")))
        val vm = viewModel(catalog = catalog)

        vm.state.test {
            val state = awaitUntil { it.connected && !it.isLoading && it.catalogUnavailable }
            assertEquals(PortfolioBanner.CatalogUnavailable, state.banner)
            assertFalse(state.isEmpty)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a refresh whose chain read fails keeps the rows that are drawn`() = runTest {
        val rpc = chain()
        val vm = viewModel(rpc = rpc, prices = FakePriceRepository(Result.success(mapOf(tslaMint to price(363.4)))))

        vm.state.test {
            val loaded = awaitUntil { it.connected && it.settled && !it.isLoading }
            assertEquals(2, loaded.positions.size)

            rpc.balances = Result.failure(IOException("502"))
            vm.refresh()

            val after = awaitUntil { it.chainUnavailable }
            assertEquals("the last true answer stays on screen", 2, after.positions.size)
            assertEquals(loaded.positions.map { it.symbol }, after.positions.map { it.symbol })
            assertEquals(loaded.totalUsd!!, after.totalUsd!!, 1e-9)
            assertEquals(PortfolioBanner.ChainUnavailable, after.banner)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a refresh that answers clears the banner it raised`() = runTest {
        val rpc = FakeRpcRepository(balances = Result.failure(IOException("502")))
        val vm = viewModel(rpc = rpc, prices = FakePriceRepository(Result.success(mapOf(tslaMint to price(363.4)))))

        vm.state.test {
            awaitUntil { it.chainUnavailable }
            rpc.balances = Result.success(listOf(balance(tslaMint, 137_000_000L, 8)))
            vm.refresh()

            val after = awaitUntil { it.settled && !it.isLoading && !it.chainUnavailable }
            assertEquals(1, after.positions.size)
            assertNull(after.banner)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a refresh with no wallet connected asks the chain nothing`() = runTest {
        val rpc = chain()
        val vm = viewModel(wallet = FakeWalletSession(), rpc = rpc)

        vm.state.test {
            awaitItem()
            vm.refresh()
            assertTrue(rpc.balanceOwners.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `disconnecting clears the holdings and keeps this device's receipts`() = runTest {
        val receipts = FakeReceiptStore().apply { record(receipt("5xY1", landedAtMillis = 1_789_045_020_000L)) }
        val wallet = FakeWalletSession().apply {
            connectedAs(seeker)
            enqueue(WalletOutcome.Success(Unit))
        }
        val vm = viewModel(wallet = wallet, prices = FakePriceRepository(Result.success(mapOf(tslaMint to price(363.4)))), receipts = receipts)

        vm.state.test {
            awaitUntil { it.connected && it.settled && it.positions.isNotEmpty() }
            vm.disconnect()
            val state = awaitUntil { !it.connected }
            assertTrue(state.positions.isEmpty())
            assertNull(state.totalUsd)
            assertNull(state.banner)
            assertEquals(listOf("5xY1"), state.receipts.map { it.signature })
            assertEquals(1, wallet.disconnectCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- Receipts --------------------------------------------------------------------------

    @Test
    fun `the receipts are read with no chain read at all, newest first`() = runTest {
        val receipts = FakeReceiptStore().apply {
            record(receipt("older", landedAtMillis = 1_788_440_220_000L))
            record(receipt("newer", landedAtMillis = 1_789_045_020_000L))
        }
        val rpc = FakeRpcRepository(balances = Result.failure(IOException("502")))
        // No wallet is connected, so nothing on this screen touches the chain.
        val vm = viewModel(wallet = FakeWalletSession(), rpc = rpc, receipts = receipts)

        vm.state.test {
            val state = awaitUntil { it.receipts.isNotEmpty() }
            assertEquals(listOf("newer", "older"), state.receipts.map { it.signature })
            assertFalse(state.connected)
            assertTrue(rpc.balanceOwners.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a swap landing while the screen is open adds its row`() = runTest {
        val receipts = FakeReceiptStore()
        val vm = viewModel(receipts = receipts, prices = FakePriceRepository(Result.success(mapOf(tslaMint to price(363.4)))))

        vm.state.test {
            val loaded = awaitUntil { it.connected && it.settled }
            assertTrue(loaded.receipts.isEmpty())

            receipts.record(receipt("5xY1", landedAtMillis = 1_789_045_020_000L))
            val state = awaitUntil { it.receipts.isNotEmpty() }
            assertEquals("5xY1", state.receipts.single().signature)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
