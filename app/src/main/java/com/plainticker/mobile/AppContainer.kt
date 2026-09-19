package com.plainticker.mobile

import android.content.Context
import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.core.WallClock
import com.plainticker.mobile.data.jupiter.JupiterPriceApi
import com.plainticker.mobile.data.jupiter.JupiterSwapApi
import com.plainticker.mobile.data.net.HttpClientFactory
import com.plainticker.mobile.data.plainticker.NextUpApi
import com.plainticker.mobile.data.plainticker.PlainTickerApi
import com.plainticker.mobile.data.plainticker.VoteApi
import com.plainticker.mobile.data.receipts.FileReceiptStore
import com.plainticker.mobile.data.receipts.FileVoteReceiptStore
import com.plainticker.mobile.data.receipts.ReceiptStore
import com.plainticker.mobile.data.receipts.VoteReceiptStore
import com.plainticker.mobile.data.rpc.SolanaRpcApi
import com.plainticker.mobile.data.xstocks.CatalogCache
import com.plainticker.mobile.data.xstocks.FileCatalogCache
import com.plainticker.mobile.data.xstocks.XStocksApi
import com.plainticker.mobile.prefs.NotificationPromptStore
import com.plainticker.mobile.prefs.OnboardingStore
import com.plainticker.mobile.prefs.SharedPrefsNotificationPromptStore
import com.plainticker.mobile.prefs.SharedPrefsOnboardingStore
import com.plainticker.mobile.prefs.SharedPrefsWatchlistStore
import com.plainticker.mobile.prefs.WatchlistStore
import com.plainticker.mobile.repo.CachedCatalogRepository
import com.plainticker.mobile.repo.CachedNextUpRepository
import com.plainticker.mobile.repo.CachedPriceRepository
import com.plainticker.mobile.repo.CatalogRepository
import com.plainticker.mobile.repo.ForwarderMintRepository
import com.plainticker.mobile.repo.ForwarderRpcRepository
import com.plainticker.mobile.repo.MintRepository
import com.plainticker.mobile.repo.NextUpRepository
import com.plainticker.mobile.repo.PlainTickerSummaryRepository
import com.plainticker.mobile.repo.AssetSource
import com.plainticker.mobile.repo.BundledSnapshotRepository
import com.plainticker.mobile.repo.PriceRepository
import com.plainticker.mobile.repo.RpcRepository
import com.plainticker.mobile.repo.SnapshotRepository
import com.plainticker.mobile.repo.SummaryRepository
import com.plainticker.mobile.wallet.MwaWalletSession
import com.plainticker.mobile.watchlist.DigestNotifier
import com.plainticker.mobile.watchlist.DigestStore
import com.plainticker.mobile.watchlist.DigestStrings
import com.plainticker.mobile.watchlist.SharedPrefsDigestStore
import com.plainticker.mobile.watchlist.WatchlistCheck
import com.plainticker.mobile.watchlist.WatchlistFacts
import com.plainticker.mobile.watchlist.WatchlistNotifications
import com.plainticker.mobile.watchlist.WatchlistScheduler
import com.plainticker.mobile.watchlist.WorkManagerWatchlistScheduler
import com.plainticker.mobile.wallet.WalletSessionHolder
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import io.ktor.client.HttpClient

/**
 * Manual dependency graph (plan D9: no Hilt). One instance per process, owned by
 * [PlainTickerApp]; ViewModels receive what they need through
 * [com.plainticker.mobile.ui.appViewModelFactory].
 */
interface AppContainer {
    val clock: Clock
    val httpClient: HttpClient

    val plainTickerApi: PlainTickerApi

    /** The one call SKR-weighted coverage curation makes; the app cannot build the transaction. */
    val voteApi: VoteApi

    /** The read half of curation: which uncovered tickers staked SKR has chosen, and by how much. */
    val nextUpApi: NextUpApi
    val xStocksApi: XStocksApi
    val jupiterPriceApi: JupiterPriceApi
    val jupiterSwapApi: JupiterSwapApi
    val rpcApi: SolanaRpcApi

    val summaryRepository: SummaryRepository

    /** The leaders, held for the edge's five minutes so the List and a Detail share one answer. */
    val nextUpRepository: NextUpRepository
    val catalogRepository: CatalogRepository
    val priceRepository: PriceRepository
    val rpcRepository: RpcRepository
    val mintRepository: MintRepository
    val snapshotRepository: SnapshotRepository

    val walletAdapter: MobileWalletAdapter
    val walletSession: WalletSessionHolder

    val onboardingStore: OnboardingStore
    val watchlistStore: WatchlistStore

    /** Whether this device has already been asked to allow notifications. Asked once, ever. */
    val notificationPromptStore: NotificationPromptStore

    /** The app's own record of the swaps it landed; Portfolio (T11) reads it. */
    val receiptStore: ReceiptStore

    /** The app's own record of the votes it landed (task A3); the Vote tab's "Your votes" reads it. */
    val voteReceiptStore: VoteReceiptStore

    /** The last digest the daily check produced: the Watchlist draws it, the check writes it. */
    val digestStore: DigestStore

    /** The join the Watchlist screen and the daily check both read the watched tickers through. */
    val watchlistFacts: WatchlistFacts

    /** Where a produced digest goes besides the screen. */
    val digestNotifier: DigestNotifier

    /** One run of the daily check. The worker and the debug entry point share this instance. */
    val watchlistCheck: WatchlistCheck

    /** The daily job itself, and the way to fire it now. */
    val watchlistScheduler: WatchlistScheduler
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val app: Context = context.applicationContext
    private val prefs by lazy { app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    override val clock: Clock = WallClock
    override val httpClient: HttpClient by lazy { HttpClientFactory.create() }

    override val plainTickerApi: PlainTickerApi by lazy { PlainTickerApi(httpClient) }
    override val voteApi: VoteApi by lazy { VoteApi(httpClient) }
    override val nextUpApi: NextUpApi by lazy { NextUpApi(httpClient) }
    override val xStocksApi: XStocksApi by lazy { XStocksApi(httpClient) }
    override val jupiterPriceApi: JupiterPriceApi by lazy { JupiterPriceApi(httpClient) }
    override val jupiterSwapApi: JupiterSwapApi by lazy { JupiterSwapApi(httpClient) }
    override val rpcApi: SolanaRpcApi by lazy { SolanaRpcApi(httpClient) }

    override val summaryRepository: SummaryRepository by lazy { PlainTickerSummaryRepository(plainTickerApi) }
    override val nextUpRepository: NextUpRepository by lazy { CachedNextUpRepository(nextUpApi, clock) }
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
    override val notificationPromptStore: NotificationPromptStore by lazy {
        SharedPrefsNotificationPromptStore(prefs)
    }

    // filesDir, not cache: a receipt is the only record of what a swap cost and must survive
    // the system reclaiming space.
    override val receiptStore: ReceiptStore by lazy {
        FileReceiptStore(java.io.File(app.filesDir, FileReceiptStore.FILE_NAME))
    }

    // filesDir, not cache: same reasoning as receiptStore above, a vote receipt is the only
    // record of what a wallet signed and must survive the system reclaiming space.
    override val voteReceiptStore: VoteReceiptStore by lazy {
        FileVoteReceiptStore(java.io.File(app.filesDir, FileVoteReceiptStore.FILE_NAME))
    }

    // The digest is a handful of fields written once a day, so it rides the same preferences
    // file as the watchlist it describes rather than paying for one of its own.
    override val digestStore: DigestStore by lazy { SharedPrefsDigestStore(prefs) }

    override val watchlistFacts: WatchlistFacts by lazy {
        WatchlistFacts(summaryRepository, catalogRepository, priceRepository)
    }

    override val digestNotifier: DigestNotifier by lazy { WatchlistNotifications(app) }

    // The digest is words as much as rules, and the words are in strings.xml: the check resolves
    // them through the application context, so the notification and the screen read one file.
    override val watchlistCheck: WatchlistCheck by lazy {
        WatchlistCheck(
            watchlist = watchlistStore,
            facts = watchlistFacts,
            digests = digestStore,
            strings = object : DigestStrings {
                override fun get(id: Int, args: List<String>): String =
                    app.getString(id, *args.toTypedArray())

                override fun quantity(id: Int, quantity: Int, args: List<String>): String =
                    app.resources.getQuantityString(id, quantity, *args.toTypedArray())
            },
            notifier = digestNotifier,
            clock = clock,
        )
    }

    override val watchlistScheduler: WatchlistScheduler by lazy { WorkManagerWatchlistScheduler(app) }

    companion object {
        const val PREFS_NAME = "plainticker"
    }
}
