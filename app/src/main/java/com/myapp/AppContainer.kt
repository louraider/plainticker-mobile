package com.myapp

import android.content.Context
import com.myapp.core.Clock
import com.myapp.core.WallClock
import com.myapp.data.jupiter.JupiterPriceApi
import com.myapp.data.jupiter.JupiterSwapApi
import com.myapp.data.net.HttpClientFactory
import com.myapp.data.plainticker.PlainTickerApi
import com.myapp.data.receipts.FileReceiptStore
import com.myapp.data.receipts.ReceiptStore
import com.myapp.data.rpc.SolanaRpcApi
import com.myapp.data.xstocks.CatalogCache
import com.myapp.data.xstocks.FileCatalogCache
import com.myapp.data.xstocks.XStocksApi
import com.myapp.prefs.OnboardingStore
import com.myapp.prefs.SharedPrefsOnboardingStore
import com.myapp.prefs.SharedPrefsWatchlistStore
import com.myapp.prefs.WatchlistStore
import com.myapp.repo.CachedCatalogRepository
import com.myapp.repo.CachedPriceRepository
import com.myapp.repo.CatalogRepository
import com.myapp.repo.ForwarderMintRepository
import com.myapp.repo.ForwarderRpcRepository
import com.myapp.repo.MintRepository
import com.myapp.repo.PlainTickerSummaryRepository
import com.myapp.repo.AssetSource
import com.myapp.repo.BundledSnapshotRepository
import com.myapp.repo.PriceRepository
import com.myapp.repo.RpcRepository
import com.myapp.repo.SnapshotRepository
import com.myapp.repo.SummaryRepository
import com.myapp.wallet.MwaWalletSession
import com.myapp.watchlist.DigestNotifier
import com.myapp.watchlist.DigestStore
import com.myapp.watchlist.SharedPrefsDigestStore
import com.myapp.watchlist.WatchlistFacts
import com.myapp.watchlist.WatchlistNotifications
import com.myapp.wallet.WalletSessionHolder
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import io.ktor.client.HttpClient

/**
 * Manual dependency graph (plan D9: no Hilt). One instance per process, owned by
 * [PlainTickerApp]; ViewModels receive what they need through
 * [com.myapp.ui.appViewModelFactory].
 */
interface AppContainer {
    val clock: Clock
    val httpClient: HttpClient

    val plainTickerApi: PlainTickerApi
    val xStocksApi: XStocksApi
    val jupiterPriceApi: JupiterPriceApi
    val jupiterSwapApi: JupiterSwapApi
    val rpcApi: SolanaRpcApi

    val summaryRepository: SummaryRepository
    val catalogRepository: CatalogRepository
    val priceRepository: PriceRepository
    val rpcRepository: RpcRepository
    val mintRepository: MintRepository
    val snapshotRepository: SnapshotRepository

    val walletAdapter: MobileWalletAdapter
    val walletSession: WalletSessionHolder

    val onboardingStore: OnboardingStore
    val watchlistStore: WatchlistStore

    /** The app's own record of the swaps it landed; Portfolio (T11) reads it. */
    val receiptStore: ReceiptStore

    /** The last digest the daily check produced: the Watchlist draws it, the check writes it. */
    val digestStore: DigestStore

    /** The join the Watchlist screen and the daily check both read the watched tickers through. */
    val watchlistFacts: WatchlistFacts

    /** Where a produced digest goes besides the screen. */
    val digestNotifier: DigestNotifier
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val app: Context = context.applicationContext
    private val prefs by lazy { app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    override val clock: Clock = WallClock
    override val httpClient: HttpClient by lazy { HttpClientFactory.create() }

    override val plainTickerApi: PlainTickerApi by lazy { PlainTickerApi(httpClient) }
    override val xStocksApi: XStocksApi by lazy { XStocksApi(httpClient) }
    override val jupiterPriceApi: JupiterPriceApi by lazy { JupiterPriceApi(httpClient) }
    override val jupiterSwapApi: JupiterSwapApi by lazy { JupiterSwapApi(httpClient) }
    override val rpcApi: SolanaRpcApi by lazy { SolanaRpcApi(httpClient) }

    override val summaryRepository: SummaryRepository by lazy { PlainTickerSummaryRepository(plainTickerApi) }
    // cacheDir, not filesDir: the catalog is a copy of something the network can always serve
    // again, so the system is welcome to reclaim it. Losing it costs one refetch.
    private val catalogCache: CatalogCache by lazy {
        FileCatalogCache(java.io.File(app.cacheDir, CatalogCache.FILE_NAME))
    }

    override val catalogRepository: CatalogRepository by lazy {
        CachedCatalogRepository(xStocksApi, clock, catalogCache)
    }
    override val priceRepository: PriceRepository by lazy { CachedPriceRepository(jupiterPriceApi, clock) }
    override val rpcRepository: RpcRepository by lazy { ForwarderRpcRepository(rpcApi) }
    override val mintRepository: MintRepository by lazy { ForwarderMintRepository(rpcRepository, clock) }

    // The bundled outage snapshot lives in assets; a missing one simply means no fallback.
    override val snapshotRepository: SnapshotRepository by lazy {
        BundledSnapshotRepository(AssetSource { path -> runCatching { app.assets.open(path) }.getOrNull() })
    }

    override val walletAdapter: MobileWalletAdapter by lazy { MwaWalletSession.defaultAdapter() }
    override val walletSession: WalletSessionHolder by lazy { WalletSessionHolder(walletAdapter) }

    override val onboardingStore: OnboardingStore by lazy { SharedPrefsOnboardingStore(prefs) }
    override val watchlistStore: WatchlistStore by lazy { SharedPrefsWatchlistStore(prefs) }

    // filesDir, not cache: a receipt is the only record of what a swap cost and must survive
    // the system reclaiming space.
    override val receiptStore: ReceiptStore by lazy {
        FileReceiptStore(java.io.File(app.filesDir, FileReceiptStore.FILE_NAME))
    }

    // The digest is a handful of fields written once a day, so it rides the same preferences
    // file as the watchlist it describes rather than paying for one of its own.
    override val digestStore: DigestStore by lazy { SharedPrefsDigestStore(prefs) }

    override val watchlistFacts: WatchlistFacts by lazy {
        WatchlistFacts(summaryRepository, catalogRepository, priceRepository)
    }

    override val digestNotifier: DigestNotifier by lazy { WatchlistNotifications(app) }

    companion object {
        const val PREFS_NAME = "plainticker"
    }
}
