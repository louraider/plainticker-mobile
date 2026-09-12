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
import com.myapp.data.rpc.RpcAccount
import com.myapp.data.rpc.RpcEncoding
import com.myapp.data.rpc.SkrStake
import com.myapp.data.rpc.TokenBalance
import com.myapp.data.xstocks.Deployment
import com.myapp.data.xstocks.Underlying
import com.myapp.data.xstocks.XStockAsset
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
) : CatalogRepository {
    var catalogCalls = 0
        private set

    override suspend fun catalog(): List<XStockAsset> {
        catalogCalls++
        return assets.getOrThrow()
    }

    override suspend fun multiplier(symbol: String): Double = multipliers[symbol] ?: 1.0
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
) : RpcRepository {
    val balanceOwners = mutableListOf<String>()

    override suspend fun lamports(owner: String): Long = lamports.getOrThrow()

    override suspend fun tokenBalances(owner: String): List<TokenBalance> {
        balanceOwners += owner
        return balances.getOrThrow()
    }

    override suspend fun accountInfo(pubkey: String, encoding: RpcEncoding): RpcAccount? = null

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
