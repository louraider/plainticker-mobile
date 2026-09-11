package com.myapp.data.xstocks

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * xStocks public API v2 (https://api.xstocks.fi/api/v2/public), modelled from its OpenAPI
 * document and live answers on 2026-09-10. Every field is optional upstream, so defaults
 * keep one odd row from failing a whole page.
 */

@Serializable
data class PageInfo(
    val currentPage: Int = 0,
    val hasNextPage: Boolean = false,
    // Only some endpoints send these.
    val pageSize: Int? = null,
    val totalPages: Int? = null,
    val totalNodes: Int? = null,
)

/** One page of GET /public/assets. */
@Serializable
data class AssetsPage(
    val nodes: List<XStockAsset> = emptyList(),
    val page: PageInfo? = null,
)

@Serializable
data class XStockAsset(
    val id: String? = null,
    val name: String = "",
    /** Token symbol, e.g. "TSLAx". */
    val symbol: String = "",
    val isin: String? = null,
    /** Underlying equity ticker, e.g. "TSLA". Joins to PlainTicker. */
    val underlyingSymbol: String? = null,
    val underlyingIsin: String? = null,
    val underlying: Underlying? = null,
    val description: String? = null,
    val logo: String? = null,
    val isTradingHalted: Boolean = false,
    val trading: Trading? = null,
    val deployments: List<Deployment> = emptyList(),
) {
    val solanaDeployment: Deployment?
        get() = deployments.firstOrNull { it.network.equals(NETWORK_SOLANA, ignoreCase = true) }

    /** The Solana mint, or null when this asset is not deployed there. */
    val solanaMint: String? get() = solanaDeployment?.address

    /** Best available underlying ticker; falls back to the symbol minus its "x". */
    val underlyingTicker: String
        get() = underlyingSymbol ?: underlying?.symbol ?: symbol.removeSuffix("x")

    companion object {
        const val NETWORK_SOLANA = "Solana"
    }
}

@Serializable
data class Underlying(
    val symbol: String? = null,
    val isin: String? = null,
    /** "Equity" or "ETF" upstream; kept as text so a new kind does not null the row. */
    val type: String? = null,
    val listingCountry: String? = null,
)

/** Where the issuer's own venue is in its day. Drives the NAV-vs-price wording. */
@Serializable
enum class TradingPeriod {
    @SerialName("market") MARKET,
    @SerialName("extended") EXTENDED,
    @SerialName("overnight") OVERNIGHT,
    @SerialName("closed") CLOSED,
}

/** The `trading.*` block: venue state and per-period order limits. */
@Serializable
data class Trading(
    val currency: String? = null,
    /** "Regular" | "MarketHours" | "TwentyFourFive" | "Always". */
    val tradingHoursMode: String? = null,
    val isTradingHalted: Boolean = false,
    val currentPeriod: TradingPeriod? = null,
    val openNow: Boolean = false,
    /** ISO-8601 instant of the next period change, or null. */
    val nextChangeAt: String? = null,
    val exchange: Exchange? = null,
    val limitsPerPeriod: LimitsPerPeriod? = null,
)

@Serializable
data class Exchange(
    val mic: String? = null,
    val abbreviation: String? = null,
    val name: String? = null,
    val timezone: String? = null,
)

@Serializable
data class LimitsPerPeriod(
    val market: OrderLimits? = null,
    val extended: OrderLimits? = null,
    val overnight: OrderLimits? = null,
    val closed: OrderLimits? = null,
)

/** Fiat order bounds in the asset's trading currency. A max of 0 means no orders. */
@Serializable
data class OrderLimits(
    val minOrderFiatValue: Double? = null,
    val maxOrderFiatValue: Double? = null,
)

@Serializable
data class Deployment(
    val address: String = "",
    /** Network name as xStocks spells it: "Solana", "Ethereum", ... */
    val network: String = "",
    val supportsAtomicSwaps: Boolean = false,
    val stablecoins: List<Stablecoin> = emptyList(),
)

@Serializable
data class Stablecoin(
    val symbol: String = "",
    val currency: String? = null,
    val network: String? = null,
    val address: String? = null,
    val decimals: Int? = null,
    val issuance: Boolean = false,
    val redemption: Boolean = false,
    val supportsAtomicSwaps: Boolean = false,
    /** "TokenProgram" | "Token2022Program", Solana only. */
    val solanaTokenProgram: String? = null,
)

/**
 * GET /public/assets/{symbol}/multiplier?network=Solana
 *
 * The Token-2022 scaledUiAmount multiplier. Raw on-chain balances are multiplied by
 * [currentMultiplier] to get the share count a holder actually owns; a split shows up
 * here before the wallet's UI amount catches up.
 */
@Serializable
data class Multiplier(
    val currentMultiplier: Double = 1.0,
    /** The scheduled next value, 0 when none is scheduled. */
    val newMultiplier: Double = 0.0,
    /** Epoch of the scheduled change as the API sends it, 0 when none. */
    val activationDateTime: Long = 0L,
    /** "FeeAccrual" | "Dividend" | "Split" | "ReverseSplit" | "Administrative", or null. */
    val reason: String? = null,
) {
    val hasScheduledChange: Boolean get() = newMultiplier > 0.0 && activationDateTime > 0L
}

/** One page of GET /public/proof-of-reserves. */
@Serializable
data class ProofOfReservesPage(
    val nodes: List<ProofOfReserves> = emptyList(),
    val page: PageInfo? = null,
)

/** GET /public/proof-of-reserves/{symbol}: shares held by the custodian vs tokens outstanding. */
@Serializable
data class ProofOfReserves(
    val symbol: String = "",
    val timestamp: String? = null,
    /** Decimal string, e.g. "196869". */
    val sharesHeld: String? = null,
    /** Decimal string, e.g. "196340.26085951860836". */
    val circulatingSupply: String? = null,
    val holdings: List<Holding> = emptyList(),
) {
    val sharesHeldValue: Double? get() = sharesHeld?.toDoubleOrNull()
    val circulatingSupplyValue: Double? get() = circulatingSupply?.toDoubleOrNull()

    /** sharesHeld / circulatingSupply; null when either side is missing or supply is zero. */
    val coverageRatio: Double?
        get() {
            val held = sharesHeldValue ?: return null
            val supply = circulatingSupplyValue ?: return null
            return if (supply > 0.0) held / supply else null
        }
}

@Serializable
data class Holding(
    val provider: String? = null,
    val quantity: String? = null,
    val symbol: String? = null,
)
