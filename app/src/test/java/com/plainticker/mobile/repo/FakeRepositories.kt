package com.plainticker.mobile.repo

import com.plainticker.mobile.data.jupiter.PriceEntry
import com.plainticker.mobile.data.jupiter.PriceFetch
import com.plainticker.mobile.data.jupiter.TrackingQuality
import com.plainticker.mobile.data.net.ApiException
import com.plainticker.mobile.data.plainticker.AnalysisPayload
import com.plainticker.mobile.data.plainticker.NextUpRow
import com.plainticker.mobile.data.plainticker.SummaryResponse
import com.plainticker.mobile.data.snapshot.ListSnapshot
import com.plainticker.mobile.data.snapshot.SnapshotAsset
import com.plainticker.mobile.data.snapshot.SnapshotRow
import com.plainticker.mobile.data.rpc.ContextValue
import com.plainticker.mobile.data.rpc.DefaultAccountState
import com.plainticker.mobile.data.rpc.MintFacts
import com.plainticker.mobile.data.rpc.PausableConfig
import com.plainticker.mobile.data.rpc.PermanentDelegate
import com.plainticker.mobile.data.rpc.ScaledUiAmountConfig
import com.plainticker.mobile.data.rpc.TransferHookConfig
import com.plainticker.mobile.data.rpc.RpcAccount
import com.plainticker.mobile.data.rpc.RpcContext
import com.plainticker.mobile.data.rpc.RpcEncoding
import com.plainticker.mobile.data.rpc.SkrStake
import com.plainticker.mobile.data.rpc.TokenBalance
import com.plainticker.mobile.data.xstocks.Deployment
import com.plainticker.mobile.data.xstocks.Holding
import com.plainticker.mobile.data.xstocks.Multiplier
import com.plainticker.mobile.data.xstocks.ProofOfReserves
import com.plainticker.mobile.data.xstocks.Trading
import com.plainticker.mobile.data.xstocks.Underlying
import com.plainticker.mobile.data.xstocks.XStockAsset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.yield
import java.math.BigInteger
import java.time.LocalDate

class FakeSummaryRepository(
    var summaryResult: Result<SummaryResponse> = Result.success(SummaryResponse("v1.1", "2026-09-10T18:00:00.000Z")),
    var analyses: Map<String, Result<AnalysisPayload>> = emptyMap(),
) : SummaryRepository {
    var summaryCalls = 0
        private set

    /** The code the most recent [analysis] call carried, so a test can assert it was sent at all. */
    var lastAnalysisCode: String? = null
        private set

    override suspend fun summary(): SummaryResponse {
        summaryCalls++
        return summaryResult.getOrThrow()
    }

    override suspend fun analysis(ticker: String, code: String?): AnalysisPayload {
        lastAnalysisCode = code
        return analyses[ticker.uppercase()]?.getOrThrow()
            ?: throw ApiException(404, "www.plainticker.com/api/v1/$ticker", "not_available", null)
    }
}

class FakeCatalogRepository(
    var assets: Result<List<XStockAsset>> = Result.success(emptyList()),
    /**
     * How many assets [catalogUpdates] publishes per emission. Zero, the default, publishes the
     * whole catalog at once, which is what a test that is not about paging wants.
     */
    var pageSize: Int = 0,
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

    /** What every [catalogUpdates] collection asked for, so a test can tell a screen from a reader. */
    val userAsked = mutableListOf<Boolean>()

    override suspend fun catalog(): List<XStockAsset> {
        catalogCalls++
        return assets.getOrThrow()
    }

    /** Emissions in page order, each carrying everything published so far. */
    override fun catalogUpdates(userAsked: Boolean): Flow<CatalogUpdate> = flow {
        catalogCalls++
        this@FakeCatalogRepository.userAsked += userAsked
        val all = assets.getOrThrow()
        if (pageSize <= 0 || all.size <= pageSize) {
            emit(CatalogUpdate(all, whole = true))
            return@flow
        }
        val published = ArrayList<XStockAsset>(all.size)
        all.chunked(pageSize).forEachIndexed { index, page ->
            // A real page is a network call, so it is not free: without this, every page would
            // land inside one dispatch and nothing downstream could ever observe a partial one.
            if (index > 0) yield()
            published += page
            emit(CatalogUpdate(published.toList(), whole = published.size == all.size))
        }
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

    /** Every answer lands here too, the way [CachedPriceRepository] publishes it to every screen. */
    val published = kotlinx.coroutines.flow.MutableStateFlow<Map<String, PriceEntry>>(emptyMap())

    override val latest: kotlinx.coroutines.flow.StateFlow<Map<String, PriceEntry>> get() = published

    override suspend fun prices(mints: Collection<String>): Map<String, PriceEntry> {
        requested += mints.toList()
        val answer = result.getOrThrow()
        publish(mints.toSet(), answer.filterKeys { it in mints })
        return answer
    }

    /** How many times a pull asked the cache to forget, and what (null is every mint). */
    val forgotten = mutableListOf<Collection<String>?>()

    override suspend fun forget(mints: Collection<String>?) {
        forgotten += mints
    }

    override suspend fun pricesFirst(mints: List<String>, limit: Int): PriceFetch {
        val window = if (limit >= 0) mints.take(limit) else mints
        requested += window
        val inWindow = window.toSet()
        val fetch = PriceFetch(
            priced = result.getOrNull().orEmpty().filterKeys { it in inWindow },
            unfetched = unfetched.intersect(inWindow),
            failure = result.exceptionOrNull(),
        )
        if (result.isSuccess) publish(inWindow - fetch.unfetched, fetch.priced)
        return fetch
    }

    private fun publish(answered: Set<String>, priced: Map<String, PriceEntry>) {
        published.value = (published.value - (answered - priced.keys)) + priced
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

    /** The minContextSlot each balance read asked for, in order; null for a read that named none. */
    val balanceSlots = mutableListOf<Long?>()

    /** Answers in order to successive [tokenBalances] calls; [balances] once it runs out. */
    val balanceQueue = ArrayDeque<Result<List<TokenBalance>>>()

    override suspend fun lamports(owner: String, minContextSlot: Long?): Long = lamports.getOrThrow()

    override suspend fun tokenBalances(owner: String, minContextSlot: Long?): List<TokenBalance> {
        balanceOwners += owner
        balanceSlots += minContextSlot
        return (balanceQueue.removeFirstOrNull() ?: balances).getOrThrow()
    }

    override suspend fun accountInfo(pubkey: String, encoding: RpcEncoding): RpcAccount? =
        accountAt(pubkey, encoding).value

    override suspend fun accountAt(pubkey: String, encoding: RpcEncoding): ContextValue<RpcAccount> =
        accounts[pubkey]?.getOrThrow() ?: ContextValue(RpcContext(slot = slot), null)

    override suspend fun accounts(pubkeys: List<String>, encoding: RpcEncoding): List<RpcAccount?> = pubkeys.map { null }

    override suspend fun skrStake(wallet: String): SkrStake = stake.getOrThrow()
}

/** The leaders of coverage curation, or a call that failed; [calls] says whether a screen asked. */
class FakeNextUpRepository(
    var result: Result<List<NextUpRow>> = Result.success(emptyList()),
    /** [current]'s own answer, configured independently of [result] so a test can set round and previous. */
    var answer: Result<NextUpAnswer> = Result.success(NextUpAnswer.Open(rows = emptyList(), round = null, previous = null)),
) : NextUpRepository {
    var calls = 0
        private set
    var currentCalls = 0
        private set

    override suspend fun nextUp(): List<NextUpRow> {
        calls++
        return result.getOrThrow()
    }

    override suspend fun current(): NextUpAnswer {
        currentCalls++
        return answer.getOrThrow()
    }
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
    stockData = reference?.let { com.plainticker.mobile.data.jupiter.StockData(id = "xstocks", price = it) },
)

/**
 * Answers [MintRepository] from a fixed reading, or throws to stand in for a chain that is out.
 *
 * [readings] answers per mint, which is what a screen holding several tokens needs: one mint can
 * carry a split multiplier while the one beside it cannot be read at all. A mint that is not in
 * the map falls back to [reading].
 */
class FakeMintRepository(
    var reading: Result<MintReading> = Result.success(MintReading(facts = null, slot = 0L, readAtMillis = 0L)),
    var readings: Map<String, Result<MintReading>> = emptyMap(),
) : MintRepository {
    val asked = mutableListOf<String>()

    override suspend fun mint(mint: String): MintReading {
        asked += mint
        return (readings[mint] ?: reading).getOrThrow()
    }
}

/** A mint reading that answered, carrying [facts]; the slot and the clock are a Detail concern. */
fun mintReading(facts: MintFacts?, slot: Long = 445_912_118L, readAtMillis: Long = 0L): MintReading =
    MintReading(facts = facts, slot = slot, readAtMillis = readAtMillis)

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

    override fun catalogUpdates(userAsked: Boolean): Flow<CatalogUpdate> = flow {
        gate.await()
        inner.catalogUpdates(userAsked).collect { emit(it) }
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
    /** What each call asked for, recorded before the gate, so a held call is visible too. */
    val asked = mutableListOf<List<String>>()
    val calls: Int get() = asked.size
    var maxInFlight = 0
        private set
    private var inFlight = 0

    override suspend fun pricesFirst(mints: List<String>, limit: Int): PriceFetch {
        asked += if (limit >= 0) mints.take(limit) else mints
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

/**
 * Answers [SecondSource] from a fixed reading, or throws to stand in for a public node that is out.
 *
 * The default agrees with anything an honest forwarder says about an unsplit xStock: 8 decimals,
 * no scaled amount (a multiplier of one), and a balance no smaller than any the forwarder could
 * report, so the cap stays the forwarder's own. A test about the check names what disagrees.
 */
class FakeSecondSource(
    var result: Result<SecondRead> = Result.success(agreeingSecondRead()),
) : SecondSource {
    /** Each read as (owner, mint), in order. */
    val asked = mutableListOf<Pair<String, String>>()

    /** The minContextSlot each read asked for, in order; null for a read that named none. */
    val slots = mutableListOf<Long?>()

    override suspend fun read(owner: String, mint: String, minContextSlot: Long?): SecondRead {
        asked += owner to mint
        slots += minContextSlot
        return result.getOrThrow()
    }
}

/** A second read that agrees: [facts] as given, a balance that never caps, read at [readAtMillis]. */
fun agreeingSecondRead(
    facts: MintFacts? = mintFacts(),
    spendableRaw: Long = Long.MAX_VALUE,
    readAtMillis: Long = 0L,
): SecondRead = SecondRead(facts = facts, spendableRaw = spendableRaw, readAtMillis = readAtMillis)
