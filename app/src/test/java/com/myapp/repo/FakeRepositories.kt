package com.myapp.repo

import com.myapp.data.jupiter.PriceEntry
import com.myapp.data.jupiter.PriceFetch
import com.myapp.data.jupiter.TrackingQuality
import com.myapp.data.net.ApiException
import com.myapp.data.plainticker.AnalysisPayload
import com.myapp.data.plainticker.SummaryResponse
import com.myapp.data.snapshot.ListSnapshot
import com.myapp.data.snapshot.SnapshotAsset
import com.myapp.data.snapshot.SnapshotRow
import com.myapp.data.rpc.ContextValue
import com.myapp.data.rpc.DefaultAccountState
import com.myapp.data.rpc.MintFacts
import com.myapp.data.rpc.PausableConfig
import com.myapp.data.rpc.PermanentDelegate
import com.myapp.data.rpc.ScaledUiAmountConfig
import com.myapp.data.rpc.TransferHookConfig
import com.myapp.data.rpc.RpcAccount
import com.myapp.data.rpc.RpcContext
import com.myapp.data.rpc.RpcEncoding
import com.myapp.data.rpc.SkrStake
import com.myapp.data.rpc.TokenBalance
import com.myapp.data.xstocks.Deployment
import com.myapp.data.xstocks.Holding
import com.myapp.data.xstocks.Multiplier
import com.myapp.data.xstocks.ProofOfReserves
import com.myapp.data.xstocks.Trading
import com.myapp.data.xstocks.Underlying
import com.myapp.data.xstocks.XStockAsset
import kotlinx.coroutines.CompletableDeferred
import java.math.BigInteger
import java.time.LocalDate

class FakeSummaryRepository(
    var summaryResult: Result<SummaryResponse> = Result.success(SummaryResponse("v1.1", "2026-09-10T18:00:00.000Z")),
    var analyses: Map<String, Result<AnalysisPayload>> = emptyMap(),
) : SummaryRepository {
    var summaryCalls = 0
        private set

    override suspend fun summary(): SummaryResponse {
        summaryCalls++
        return summaryResult.getOrThrow()
    }

    override suspend fun analysis(ticker: String): AnalysisPayload =
        analyses[ticker.uppercase()]?.getOrThrow()
            ?: throw ApiException(404, "www.plainticker.com/api/v1/$ticker", "not_available", null)
}

class FakeCatalogRepository(
    var assets: Result<List<XStockAsset>> = Result.success(emptyList()),
    var multipliers: Map<String, Double> = emptyMap(),
    /** Full multiplier records by symbol; a symbol with none falls back to [multipliers]. */
    var multiplierRecords: Map<String, Result<Multiplier>> = emptyMap(),
    /**
     * Proof of reserves by symbol. A symbol that is absent from the map answers null, which is
     * what xStocks does for a token it publishes no reserves for.
     */
    var reserves: Map<String, Result<ProofOfReserves?>> = emptyMap(),
) : CatalogRepository {
    var catalogCalls = 0
        private set
    val reservesAsked = mutableListOf<String>()

    override suspend fun catalog(): List<XStockAsset> {
        catalogCalls++
        return assets.getOrThrow()
    }

    override suspend fun multiplier(symbol: String): Double =
        multiplierRecord(symbol).currentMultiplier.takeIf { it > 0.0 } ?: 1.0

    override suspend fun multiplierRecord(symbol: String): Multiplier =
        multiplierRecords[symbol]?.getOrThrow() ?: Multiplier(currentMultiplier = multipliers[symbol] ?: 1.0)

    override suspend fun proofOfReserves(symbol: String): ProofOfReserves? {
        reservesAsked += symbol
        return reserves[symbol]?.getOrThrow()
    }
}

class FakePriceRepository(
    var result: Result<Map<String, PriceEntry>> = Result.success(emptyMap()),
    /** Mints Jupiter never answered about, reported by [pricesFirst] as unfetched. */
    var unfetched: Set<String> = emptySet(),
) : PriceRepository {
    val requested = mutableListOf<List<String>>()

    override suspend fun prices(mints: Collection<String>): Map<String, PriceEntry> {
        requested += mints.toList()
        return result.getOrThrow()
    }

    override suspend fun pricesFirst(mints: List<String>, limit: Int): PriceFetch {
        val window = if (limit >= 0) mints.take(limit) else mints
        requested += window
        val inWindow = window.toSet()
        return PriceFetch(
            priced = result.getOrNull().orEmpty().filterKeys { it in inWindow },
            unfetched = unfetched.intersect(inWindow),
            failure = result.exceptionOrNull(),
        )
    }
}

class FakeSnapshotRepository(
    /** null is "no snapshot shipped with this build", the state that leaves the list empty. */
    var snapshot: ListSnapshot? = null,
) : SnapshotRepository {
    var calls = 0
        private set

    override suspend fun listSnapshot(): ListSnapshot? {
        calls++
        return snapshot
    }
}

class FakeRpcRepository(
    var balances: Result<List<TokenBalance>> = Result.success(emptyList()),
    var lamports: Result<Long> = Result.success(0L),
    var stake: Result<SkrStake> = Result.success(SkrStake.NONE),
    /** Answers to [accountAt] by pubkey; an unknown pubkey reads as an account that is not there. */
    var accounts: Map<String, Result<ContextValue<RpcAccount>>> = emptyMap(),
    var slot: Long = 0L,
) : RpcRepository {
    val balanceOwners = mutableListOf<String>()

    override suspend fun lamports(owner: String): Long = lamports.getOrThrow()

    override suspend fun tokenBalances(owner: String): List<TokenBalance> {
        balanceOwners += owner
        return balances.getOrThrow()
    }

    override suspend fun accountInfo(pubkey: String, encoding: RpcEncoding): RpcAccount? =
        accountAt(pubkey, encoding).value

    override suspend fun accountAt(pubkey: String, encoding: RpcEncoding): ContextValue<RpcAccount> =
        accounts[pubkey]?.getOrThrow() ?: ContextValue(RpcContext(slot = slot), null)

    override suspend fun accounts(pubkeys: List<String>, encoding: RpcEncoding): List<RpcAccount?> = pubkeys.map { null }

    override suspend fun skrStake(wallet: String): SkrStake = stake.getOrThrow()
}

/** An xStock catalog entry with one Solana deployment. */
fun xStock(symbol: String, ticker: String, mint: String, name: String = "$ticker xStock"): XStockAsset = XStockAsset(
    name = name,
    symbol = symbol,
    underlying = Underlying(symbol = ticker, type = "Equity", listingCountry = "US"),
    deployments = listOf(Deployment(address = mint, network = XStockAsset.NETWORK_SOLANA, supportsAtomicSwaps = true)),
)

/** A bundled snapshot of one analyzed row and its token, as the assets carry them. */
fun snapshot(
    capturedOn: LocalDate? = LocalDate.of(2026, 9, 12),
    rows: List<SnapshotRow> = emptyList(),
    assets: List<SnapshotAsset> = emptyList(),
): ListSnapshot = ListSnapshot(capturedOn = capturedOn, rows = rows, assets = assets)

/**
 * A Price v3 entry. [liquidity] defaults to a pool well above [TrackingQuality.MIN_POOL_USD], so
 * a test that says nothing about depth gets the deep pool it means; pass it explicitly to build a
 * thin one, and pass null for a token Jupiter priced without reporting any depth.
 */
fun price(usd: Double, reference: Double? = null, liquidity: Double? = 250_000.0): PriceEntry = PriceEntry(
    usdPrice = usd,
    decimals = 8,
    liquidity = liquidity,
    stockData = reference?.let { com.myapp.data.jupiter.StockData(id = "xstocks", price = it) },
)

/** Answers [MintRepository] from a fixed reading, or throws to stand in for a chain that is out. */
class FakeMintRepository(
    var reading: Result<MintReading> = Result.success(MintReading(facts = null, slot = 0L, readAtMillis = 0L)),
) : MintRepository {
    val asked = mutableListOf<String>()

    override suspend fun mint(mint: String): MintReading {
        asked += mint
        return reading.getOrThrow()
    }
}

/** A readable mint whose extensions are all absent unless a test names one. */
fun mintFacts(
    decimals: Int = MintFacts.XSTOCK_DECIMALS,
    supplyRaw: BigInteger = BigInteger.valueOf(22_963_733_950_050L),
    permanentDelegate: PermanentDelegate? = null,
    pausable: PausableConfig? = null,
    scaledUiAmount: ScaledUiAmountConfig? = null,
    transferHook: TransferHookConfig? = null,
    defaultAccountState: DefaultAccountState? = null,
    mintAuthority: String? = null,
    freezeAuthority: String? = null,
): MintFacts = MintFacts(
    decimals = decimals,
    supplyRaw = supplyRaw,
    mintAuthority = mintAuthority,
    freezeAuthority = freezeAuthority,
    permanentDelegate = permanentDelegate,
    pausable = pausable,
    scaledUiAmount = scaledUiAmount,
    transferHook = transferHook,
    defaultAccountState = defaultAccountState,
)

/** A scaled UI amount with nothing scheduled, or a pending change when both extras are given. */
fun scaled(
    multiplier: Double = 1.0,
    newMultiplier: Double = multiplier,
    effectiveAtEpochSeconds: Long = 0L,
): ScaledUiAmountConfig = ScaledUiAmountConfig(
    multiplier = multiplier,
    newMultiplier = newMultiplier,
    newMultiplierEffectiveAtEpochSeconds = effectiveAtEpochSeconds,
    authority = null,
)

/** A proof-of-reserves answer as xStocks sends it: decimal strings and named custodians. */
fun proofOfReserves(
    symbol: String,
    sharesHeld: String? = "196869",
    circulatingSupply: String? = "196340.26085951860836",
    custodian: String? = "Alpaca",
    timestamp: String? = "2026-09-12T20:51:24.651Z",
): ProofOfReserves = ProofOfReserves(
    symbol = symbol,
    timestamp = timestamp,
    sharesHeld = sharesHeld,
    circulatingSupply = circulatingSupply,
    holdings = custodian?.let { listOf(Holding(provider = it, quantity = sharesHeld, symbol = symbol.removeSuffix("x"))) }
        .orEmpty(),
)

/** An xStock whose catalog entry carries a `trading` block, so market state comes from the venue. */
fun xStockTrading(
    symbol: String,
    ticker: String,
    mint: String,
    trading: Trading,
    name: String = "$ticker xStock",
): XStockAsset = xStock(symbol, ticker, mint, name).copy(trading = trading)

/**
 * A latch a test opens when it wants the network to answer. Every held repository below waits on
 * one, so a test can assert what the screen draws while a source is still out, which is the whole
 * point of painting the bundled snapshot first.
 */
class Gate {
    private val opened = CompletableDeferred<Unit>()

    fun release() {
        opened.complete(Unit)
    }

    suspend fun await() {
        opened.await()
    }
}

/** `/summary` that does not answer until [gate] is released. */
class HeldSummaryRepository(
    private val gate: Gate,
    private val inner: FakeSummaryRepository = FakeSummaryRepository(),
) : SummaryRepository by inner {
    val summaryCalls: Int get() = inner.summaryCalls

    override suspend fun summary(): SummaryResponse {
        gate.await()
        return inner.summary()
    }
}

/** The catalog that does not answer until [gate] is released. */
class HeldCatalogRepository(
    private val gate: Gate,
    private val inner: FakeCatalogRepository = FakeCatalogRepository(),
) : CatalogRepository by inner {
    override suspend fun catalog(): List<XStockAsset> {
        gate.await()
        return inner.catalog()
    }
}

/**
 * Jupiter that does not answer until [gate] is released, and that records how many price calls
 * were ever inside it at once. [maxInFlight] above one is a screen that started a second price
 * run while the first was still out.
 */
class HeldPriceRepository(
    private val gate: Gate,
    private val inner: FakePriceRepository = FakePriceRepository(),
) : PriceRepository by inner {
    val requested: List<List<String>> get() = inner.requested
    var calls = 0
        private set
    var maxInFlight = 0
        private set
    private var inFlight = 0

    override suspend fun pricesFirst(mints: List<String>, limit: Int): PriceFetch {
        calls++
        inFlight++
        maxInFlight = maxOf(maxInFlight, inFlight)
        try {
            gate.await()
            return inner.pricesFirst(mints, limit)
        } finally {
            inFlight--
        }
    }
}
