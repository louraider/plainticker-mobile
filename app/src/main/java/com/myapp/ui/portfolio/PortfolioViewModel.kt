package com.myapp.ui.portfolio

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.R
import com.myapp.data.SplitMultiplier
import com.myapp.data.jupiter.PriceEntry
import com.myapp.data.jupiter.PriceFetch
import com.myapp.data.jupiter.TrackingQuality
import com.myapp.data.receipts.ReceiptStore
import com.myapp.data.receipts.SwapReceipt
import com.myapp.data.rpc.MintFacts
import com.myapp.data.rpc.TokenBalance
import com.myapp.data.xstocks.XStockAsset
import com.myapp.repo.CatalogRepository
import com.myapp.repo.MintRepository
import com.myapp.repo.PriceRepository
import com.myapp.repo.RpcRepository
import com.myapp.wallet.WalletAccount
import com.myapp.wallet.WalletOutcome
import com.myapp.wallet.WalletSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal

/**
 * One xStock the connected wallet holds, as the chain describes it.
 *
 * The quantity is the whole point of the screen and it is not the raw balance: an xStock mint
 * carries the Token-2022 `scaledUiAmountConfig` extension, and the shares a holder owns are the
 * raw amount scaled by that multiplier and by the mint's own decimals (docs/data-map.md,
 * Portfolio (T11): "token account amount x multiplier as quantity"). Both numbers come off the
 * mint account, never off a constant and never off the issuer's endpoint.
 *
 * [decimals] and [multiplier] are null together, for one reason: the mint could not be read. That
 * is not a multiplier of one. A mint this app cannot read may have been split yesterday, and
 * printing the raw balance as a share count would be a wrong number that reads as a measured one,
 * so such a position states that the mint was not read and carries no quantity and no value.
 */
data class PortfolioPosition(
    /** Token symbol, e.g. "TSLAx". */
    val symbol: String,
    /** Underlying equity ticker, e.g. "TSLA": the Detail route and the join key. */
    val ticker: String,
    val company: String,
    val mint: String,
    /** The token account balance in base units, exactly as the node sent it. */
    val amountRaw: Long,
    /** On-chain decimals from the mint; null when the mint could not be read. */
    val decimals: Int?,
    /**
     * The mint's scaled UI amount multiplier. 1.0 is a fact, not a default: it is what a mint
     * carrying no `scaledUiAmountConfig` says about itself. Null when the mint was not read.
     */
    val multiplier: Double?,
    val priceUsd: Double?,
    /** The underlying share's reference price from Price v3 `stockData`, when Jupiter has one. */
    val referencePriceUsd: Double?,
    /** The pool behind the quote in USD, from Price v3 `liquidity`. Jupiter may omit it. */
    val poolUsd: Double?,
) {
    /** False when the mint account could not be read, the one state that has no quantity. */
    val mintRead: Boolean get() = decimals != null && multiplier != null

    /** Shares held: raw over 10^decimals, scaled by the mint's multiplier. Null when the mint was not read. */
    val quantity: BigDecimal?
        get() {
            val places = decimals ?: return null
            val scale = multiplier ?: return null
            return BigDecimal.valueOf(amountRaw).movePointLeft(places).multiply(BigDecimal.valueOf(scale))
        }

    /** Quantity times price; null when either half is missing, and never a guess at the other. */
    val valueUsd: Double?
        get() {
            val held = quantity ?: return null
            val price = priceUsd ?: return null
            return held.toDouble() * price
        }

    /**
     * How far this position's quote can be trusted, from the one rule in [TrackingQuality]. The
     * liquidity floor applies here exactly as it does on the list: a pool of $34 makes a premium
     * arithmetic rather than a price, so the row states the pool instead.
     */
    val tracking: TrackingQuality? get() = TrackingQuality.of(priceUsd, referencePriceUsd, poolUsd)
}

/** Where the wallet round-trip is. [CONNECTING] is the wallet's own screen being open. */
enum class WalletPhase { DISCONNECTED, CONNECTING, CONNECTED }

/**
 * What a round-trip that did not connect tells the reader. The adapter's own message never
 * reaches a screen: it is a developer string, so the swap sheet logs it and says one of these
 * instead, and this screen keeps the same rule.
 */
enum class WalletNote(@StringRes val text: Int) {
    NONE_ON_DEVICE(R.string.swap_failed_no_wallet),
    CANCELLED(R.string.portfolio_wallet_cancelled),
    REFUSED(R.string.portfolio_wallet_refused),
}

/**
 * The one banner slot, in the DESIGN.md section 4 order: what could not be read at all first,
 * then what is only partly missing, then the note from the last wallet round-trip.
 */
sealed interface PortfolioBanner {
    /** The forwarder did not answer, so this wallet's holdings could not be read. */
    data object ChainUnavailable : PortfolioBanner

    /** The xStocks catalog did not answer, so no balance on the chain can be named as an xStock. */
    data object CatalogUnavailable : PortfolioBanner

    /** Jupiter priced nothing: the positions stand, the total does not. */
    data object PricesUnavailable : PortfolioBanner

    /** Jupiter priced some of them; the total says how many it covers. */
    data object PricesPartial : PortfolioBanner

    /** The wallet did not connect, and why. */
    data class Wallet(val note: WalletNote) : PortfolioBanner
}

data class PortfolioUiState(
    val phase: WalletPhase = WalletPhase.DISCONNECTED,
    val isLoading: Boolean = false,
    val account: WalletAccount? = null,
    val positions: List<PortfolioPosition> = emptyList(),
    /** The app's own record of the swaps it landed, newest first. Read with or without a chain read. */
    val receipts: List<SwapReceipt> = emptyList(),
    /** Sum of the positions that could be valued; null when none of them could. */
    val totalUsd: Double? = null,
    val chainUnavailable: Boolean = false,
    val catalogUnavailable: Boolean = false,
    val pricesUnavailable: Boolean = false,
    val pricesPartial: Boolean = false,
    val note: WalletNote? = null,
    /** True once one load has finished, so an empty wallet is an answer and not a screen yet to ask. */
    val settled: Boolean = false,
) {
    val connected: Boolean get() = phase == WalletPhase.CONNECTED && account != null

    /** How many positions carry a value, which is exactly what the total covers. */
    val valuedCount: Int get() = positions.count { it.valueUsd != null }

    /** A connected wallet that holds no xStock at all: the common case for a fresh wallet. */
    val isEmpty: Boolean
        get() = connected && settled && positions.isEmpty() && !chainUnavailable && !catalogUnavailable

    /** Skeletons are only for a screen with nothing on it; a refresh over drawn rows keeps them. */
    val isCold: Boolean get() = connected && isLoading && positions.isEmpty() && !settled

    val banner: PortfolioBanner?
        get() = when {
            chainUnavailable -> PortfolioBanner.ChainUnavailable
            catalogUnavailable -> PortfolioBanner.CatalogUnavailable
            pricesUnavailable -> PortfolioBanner.PricesUnavailable
            pricesPartial -> PortfolioBanner.PricesPartial
            note != null -> PortfolioBanner.Wallet(note)
            else -> null
        }
}

/**
 * The xStocks the connected wallet holds (task T11), read through the bounded forwarder: both
 * token programs, only mints the xStocks catalog knows, the quantity scaled by the mint's own
 * Token-2022 extension, and the app's own receipts beside them.
 *
 * Three rules this class exists to keep:
 *
 * 1. **The mint decides the quantity.** [MintRepository] reads `scaledUiAmountConfig` and the
 *    decimals off each mint. A mint that could not be read leaves its position without a quantity
 *    rather than with a multiplier of one (see [PortfolioPosition]).
 * 2. **No cost basis, anywhere.** The chain does not carry one and this app does not invent one:
 *    there is no profit, no loss and no change-since figure in this state at all, and the screen
 *    says so in a footnote (plan section 13 Pass 8, docs/data-map.md Portfolio (T11)).
 * 3. **A refresh never walks the screen backwards.** A load that fails leaves the rows that are
 *    drawn where they are and raises a banner; only a load that answers replaces them. That is the
 *    rule the list learned on 2026-09-13 and it costs nothing to keep here.
 */
class PortfolioViewModel(
    private val wallet: WalletSession,
    private val rpc: RpcRepository,
    private val catalog: CatalogRepository,
    private val prices: PriceRepository,
    private val mints: MintRepository,
    receipts: ReceiptStore,
) : ViewModel() {

    private val _state = MutableStateFlow(PortfolioUiState())
    val state: StateFlow<PortfolioUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            wallet.account.collect { account ->
                if (account == null) {
                    loadJob?.cancel()
                    // The receipts are this device's own record and belong to no wallet, so a
                    // disconnect takes the holdings off the screen and leaves them alone.
                    _state.update { PortfolioUiState(receipts = it.receipts) }
                } else {
                    start(account)
                }
            }
        }
        viewModelScope.launch {
            receipts.receipts.collect { landed ->
                val newestFirst = landed.sortedByDescending { it.landedAtMillis }
                _state.update { it.copy(receipts = newestFirst) }
            }
        }
    }

    fun connect() {
        viewModelScope.launch {
            _state.update { it.copy(phase = WalletPhase.CONNECTING, note = null) }
            val note = when (wallet.connect()) {
                // The account flow is what starts the load, here and after a process death alike.
                is WalletOutcome.Success -> return@launch
                is WalletOutcome.NoWallet -> WalletNote.NONE_ON_DEVICE
                is WalletOutcome.Cancelled -> WalletNote.CANCELLED
                is WalletOutcome.Error -> WalletNote.REFUSED
            }
            _state.update { it.copy(phase = WalletPhase.DISCONNECTED, note = note) }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            val note = when (wallet.disconnect()) {
                // The account flow resets the screen.
                is WalletOutcome.Success -> return@launch
                is WalletOutcome.NoWallet -> WalletNote.NONE_ON_DEVICE
                is WalletOutcome.Cancelled -> WalletNote.CANCELLED
                is WalletOutcome.Error -> WalletNote.REFUSED
            }
            _state.update { it.copy(note = note) }
        }
    }

    /** The reader asking for the chain again. With no connected wallet there is nothing to ask. */
    fun refresh() {
        val account = wallet.account.value ?: return
        start(account)
    }

    private fun start(account: WalletAccount) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { load(account) }
    }

    private suspend fun load(account: WalletAccount) {
        _state.update {
            it.copy(phase = WalletPhase.CONNECTED, account = account, isLoading = true, note = null)
        }

        val balances = runCatching { rpc.tokenBalances(account.address) }.getOrElse {
            // Nothing was read, so nothing on screen may be replaced: what is drawn is the last
            // true answer and the banner says the chain is out.
            _state.update { it.copy(isLoading = false, chainUnavailable = true) }
            return
        }
        val assets = runCatching { catalog.catalog() }.getOrElse {
            _state.update { it.copy(isLoading = false, catalogUnavailable = true) }
            return
        }

        val byMint = assets.mapNotNull { asset -> asset.solanaMint?.let { it to asset } }.toMap()
        // Only xStocks: a wallet's USDC, its SOL and every other token in it are not this
        // screen's subject and are never listed.
        val owned = balances.filter { it.mint in byMint }

        val facts = readMints(owned)
        // A wallet holding no xStock costs Jupiter nothing: there is no mint to price.
        val fetch = if (owned.isEmpty()) {
            PriceFetch.EMPTY
        } else {
            prices.pricesFirst(owned.map { it.mint }, PriceRepository.NO_LIMIT)
        }
        val missedAPrice = fetch.failure != null || fetch.unfetched.isNotEmpty()

        val positions = owned
            .map { balance -> position(balance, byMint.getValue(balance.mint), facts[balance.mint], fetch.priced[balance.mint]) }
            .sortedWith(compareBy<PortfolioPosition, Double?>(nullsLast(reverseOrder())) { it.valueUsd }.thenBy { it.symbol })

        _state.update {
            it.copy(
                isLoading = false,
                settled = true,
                positions = positions,
                totalUsd = positions.mapNotNull { p -> p.valueUsd }.takeIf { v -> v.isNotEmpty() }?.sum(),
                chainUnavailable = false,
                catalogUnavailable = false,
                pricesUnavailable = owned.isNotEmpty() && fetch.priced.isEmpty() && missedAPrice,
                pricesPartial = fetch.priced.isNotEmpty() && missedAPrice,
            )
        }
    }

    /**
     * One `getAccountInfo(mint, jsonParsed)` per held mint, in order.
     *
     * Sequential, and one call each on purpose. The forwarder rate-limits per IP and forwards the
     * methods this app already uses (docs/data-map.md, Sources); `getMultipleAccounts` would read
     * every mint in one call and is not one of them, so batching is a server change rather than a
     * client one. A wallet holds a handful of xStocks, and the forwarder caches each mint for 60 s
     * server-side, so opening a Detail screen afterwards pays nothing for the same read.
     *
     * A mint that throws and a mint that answers with something this app cannot read as a
     * Token-2022 mint are the same answer here: null, which the row states.
     */
    private suspend fun readMints(owned: List<TokenBalance>): Map<String, MintFacts?> {
        val out = LinkedHashMap<String, MintFacts?>(owned.size)
        for (balance in owned) {
            out[balance.mint] = runCatching { mints.mint(balance.mint) }.getOrNull()?.facts
        }
        return out
    }

    private fun position(
        balance: TokenBalance,
        asset: XStockAsset,
        facts: MintFacts?,
        entry: PriceEntry?,
    ): PortfolioPosition {
        // A mint with no scaled amount extension is not rescaled at all, which is a multiplier of
        // one read off the chain; a mint that was never read carries neither number.
        val multiplier = facts?.let {
            it.scaledUiAmount?.let(SplitMultiplier::ofMint)?.current ?: SplitMultiplier.NONE
        }
        return PortfolioPosition(
            symbol = asset.symbol,
            ticker = asset.underlyingTicker,
            company = asset.name,
            mint = balance.mint,
            amountRaw = balance.amountRaw,
            decimals = facts?.decimals,
            multiplier = multiplier,
            priceUsd = entry?.usdPrice,
            referencePriceUsd = entry?.stockData?.price,
            poolUsd = entry?.liquidity,
        )
    }
}
