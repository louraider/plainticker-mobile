package com.myapp.ui.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.repo.CatalogRepository
import com.myapp.repo.PriceRepository
import com.myapp.repo.RpcRepository
import com.myapp.wallet.WalletAccount
import com.myapp.wallet.WalletOutcome
import com.myapp.wallet.WalletSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One xStock the connected wallet owns. Quantity = raw * multiplier / 10^decimals; value = quantity * price. */
data class PortfolioPosition(
    val symbol: String,
    val ticker: String,
    val name: String,
    val mint: String,
    val amountRaw: Long,
    val decimals: Int,
    val multiplier: Double,
    val quantity: Double,
    val priceUsd: Double?,
    val valueUsd: Double?,
)

data class PortfolioUiState(
    val isLoading: Boolean = false,
    val account: WalletAccount? = null,
    val positions: List<PortfolioPosition> = emptyList(),
    /** Sum of the priced positions; null when nothing could be priced. */
    val totalUsd: Double? = null,
    val lamports: Long? = null,
    /** The chain read failed: nothing to show for this wallet. */
    val error: String? = null,
    val pricesUnavailable: Boolean = false,
    /** A neutral note from the last wallet round-trip, e.g. a cancellation. */
    val message: String? = null,
) {
    val isConnected: Boolean get() = account != null
    val isEmpty: Boolean get() = isConnected && !isLoading && error == null && positions.isEmpty()
}

/**
 * xStocks in the connected wallet via the forwarder (plan T11): both token programs are
 * read, only mints in the xStocks catalog are listed, no cost basis is invented.
 */
class PortfolioViewModel(
    private val wallet: WalletSession,
    private val rpc: RpcRepository,
    private val catalog: CatalogRepository,
    private val prices: PriceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PortfolioUiState())
    val state: StateFlow<PortfolioUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            wallet.account.collect { account ->
                if (account == null) {
                    _state.update { PortfolioUiState(message = it.message) }
                } else {
                    load(account)
                }
            }
        }
    }

    fun connect() {
        viewModelScope.launch {
            _state.update { it.copy(message = null) }
            when (val outcome = wallet.connect()) {
                is WalletOutcome.Success -> Unit // the account flow triggers the load
                is WalletOutcome.NoWallet -> _state.update { it.copy(message = "No compatible wallet found") }
                is WalletOutcome.Cancelled -> _state.update { it.copy(message = "Cancelled in wallet") }
                is WalletOutcome.Error -> _state.update { it.copy(message = outcome.message) }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            when (val outcome = wallet.disconnect()) {
                is WalletOutcome.Success -> Unit // the account flow resets the state
                is WalletOutcome.NoWallet -> _state.update { it.copy(message = "No compatible wallet found") }
                is WalletOutcome.Cancelled -> _state.update { it.copy(message = "Cancelled in wallet") }
                is WalletOutcome.Error -> _state.update { it.copy(message = outcome.message) }
            }
        }
    }

    fun refresh() {
        val account = wallet.account.value ?: return
        viewModelScope.launch { load(account) }
    }

    private suspend fun load(account: WalletAccount) {
        _state.update { it.copy(isLoading = true, account = account, error = null) }

        val balances = runCatching { rpc.tokenBalances(account.address) }
        val assets = runCatching { catalog.catalog() }
        val lamports = runCatching { rpc.lamports(account.address) }.getOrNull()
        if (balances.isFailure || assets.isFailure) {
            _state.update {
                it.copy(
                    isLoading = false,
                    lamports = lamports,
                    error = if (balances.isFailure) "On-chain data unavailable" else "Catalog unavailable",
                )
            }
            return
        }

        val byMint = assets.getOrThrow().mapNotNull { asset -> asset.solanaMint?.let { it to asset } }.toMap()
        val owned = balances.getOrThrow().filter { it.mint in byMint }
        val priced = runCatching { if (owned.isEmpty()) emptyMap() else prices.prices(owned.map { it.mint }) }
        val priceMap = priced.getOrNull().orEmpty()

        val positions = owned.map { balance ->
            val asset = byMint.getValue(balance.mint)
            val multiplier = runCatching { catalog.multiplier(asset.symbol) }.getOrDefault(1.0)
            val quantity = balance.quantity(multiplier)
            val price = priceMap[balance.mint]?.usdPrice
            PortfolioPosition(
                symbol = asset.symbol,
                ticker = asset.underlyingTicker,
                name = asset.name,
                mint = balance.mint,
                amountRaw = balance.amountRaw,
                decimals = balance.decimals,
                multiplier = multiplier,
                quantity = quantity,
                priceUsd = price,
                valueUsd = price?.let { quantity * it },
            )
        }.sortedWith(compareBy<PortfolioPosition, Double?>(nullsLast(reverseOrder())) { it.valueUsd }.thenBy { it.symbol })

        _state.update {
            it.copy(
                isLoading = false,
                account = account,
                positions = positions,
                totalUsd = positions.mapNotNull { p -> p.valueUsd }.takeIf { v -> v.isNotEmpty() }?.sum(),
                lamports = lamports,
                error = null,
                pricesUnavailable = priced.isFailure,
            )
        }
    }
}
