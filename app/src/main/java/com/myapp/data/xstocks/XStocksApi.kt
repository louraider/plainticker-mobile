package com.myapp.data.xstocks

import com.myapp.data.net.HttpClientFactory
import com.myapp.data.net.bodyOrThrow
import com.myapp.data.net.nullableBodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList

/**
 * xStocks public API v2. No key; endpoints and shapes per its OpenAPI document
 * (https://api.xstocks.fi/api-docs/v2/openapi.json) and live answers on 2026-09-10.
 *
 * The catalog is ~830 assets served 100 per page at roughly 560 KB a page (every asset
 * lists its deployments on ten networks), so [catalogPages] streams pages as they land and
 * callers cache the result rather than refetching per screen.
 *
 * Symbols go into URL paths, so every symbol is checked against [SYMBOL] first: a value
 * that is not a plain token symbol (a stray "../", a "?network=") is refused with an
 * [IllegalArgumentException] before any request is built.
 */
class XStocksApi(
    private val client: HttpClient,
    private val baseUrl: String = BASE_URL,
) {
    /** One page of the asset catalog. [pageSize] is capped at 100 by the API. */
    suspend fun assetsPage(
        page: Int = 0,
        pageSize: Int = MAX_PAGE_SIZE,
        network: String = XStockAsset.NETWORK_SOLANA,
    ): AssetsPage = client.get("$baseUrl/assets") {
        browserIdentity()
        parameter("network", network)
        parameter("page", page)
        parameter("pageSize", pageSize.coerceIn(1, MAX_PAGE_SIZE))
    }.bodyOrThrow()

    /** Every catalog page in order, following `page.hasNextPage` until it is false. */
    fun catalogPages(network: String = XStockAsset.NETWORK_SOLANA): Flow<AssetsPage> = flow {
        var page = 0
        while (page < MAX_PAGES) {
            val current = assetsPage(page = page, network = network)
            emit(current)
            if (current.page?.hasNextPage != true || current.nodes.isEmpty()) break
            page++
        }
    }

    /** The whole catalog, minus rows with no symbol. */
    suspend fun catalog(network: String = XStockAsset.NETWORK_SOLANA): List<XStockAsset> =
        catalogPages(network).toList().flatMap { it.nodes }.filter { it.symbol.isNotBlank() }

    /** One asset by its token symbol, e.g. "TSLAx". */
    suspend fun asset(symbol: String): XStockAsset =
        client.get("$baseUrl/assets/${symbolPath(symbol)}") { browserIdentity() }.bodyOrThrow()

    /** Current scaledUiAmount multiplier for [symbol] on [network]. */
    suspend fun multiplier(symbol: String, network: String = XStockAsset.NETWORK_SOLANA): Multiplier =
        client.get("$baseUrl/assets/${symbolPath(symbol)}/multiplier") {
            browserIdentity()
            parameter("network", network)
        }.bodyOrThrow()

    /**
     * Proof of reserves for one symbol, or null: the API answers 200 with a JSON `null`
     * for a symbol it has no reserves data for.
     */
    suspend fun proofOfReserves(symbol: String): ProofOfReserves? =
        client.get("$baseUrl/proof-of-reserves/${symbolPath(symbol)}") { browserIdentity() }.nullableBodyOrThrow()

    /** One page of proof-of-reserves rows. */
    suspend fun proofOfReservesPage(page: Int = 0, pageSize: Int = MAX_PAGE_SIZE): ProofOfReservesPage =
        client.get("$baseUrl/proof-of-reserves") {
            browserIdentity()
            parameter("page", page)
            parameter("pageSize", pageSize.coerceIn(1, MAX_PAGE_SIZE))
        }.bodyOrThrow()

    /** Proof of reserves for every asset (about nine small pages). */
    suspend fun proofOfReserves(): List<ProofOfReserves> {
        val out = ArrayList<ProofOfReserves>()
        var page = 0
        while (page < MAX_PAGES) {
            val current = proofOfReservesPage(page)
            out += current.nodes
            if (current.page?.hasNextPage != true || current.nodes.isEmpty()) break
            page++
        }
        return out
    }

    private fun HttpRequestBuilder.browserIdentity() {
        header(HttpHeaders.UserAgent, HttpClientFactory.BROWSER_USER_AGENT)
    }

    /** The trimmed symbol, or an [IllegalArgumentException] when it could not be a token symbol. */
    private fun symbolPath(symbol: String): String {
        val trimmed = symbol.trim()
        require(SYMBOL.matches(trimmed)) { "not an xStock symbol: '$symbol'" }
        return trimmed
    }

    companion object {
        const val BASE_URL = "https://api.xstocks.fi/api/v2/public"
        const val MAX_PAGE_SIZE = 100

        /** Hard stop on pagination: ~830 assets today; 50 pages is five times that. */
        const val MAX_PAGES = 50

        /** Token symbols as xStocks spells them ("TSLAx", "SPYx"): letters, digits, '.', '-', '_'. */
        private val SYMBOL = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,19}")
    }
}
