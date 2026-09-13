package com.plainticker.mobile.data.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.appendIfNameAbsent
import kotlinx.serialization.json.Json

/**
 * The one HTTP client the app shares across PlainTicker, xStocks and Jupiter.
 *
 * Every API here is public JSON over TLS with no credentials, so one OkHttp-backed client
 * with one JSON configuration is enough. Tests pass a Ktor MockEngine through [create].
 */
object HttpClientFactory {

    /** Identifies the app to PlainTicker and Jupiter. */
    const val USER_AGENT = "PlainTicker-Mobile/0.1 (Android)"

    /**
     * xStocks' public API sits behind a filter that has been seen refusing non-browser agents.
     * [com.plainticker.mobile.data.xstocks.XStocksApi] sends this instead of [USER_AGENT].
     */
    const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

    const val TIMEOUT_MS = 15_000L

    /**
     * Lenient on the way in: unknown keys are ignored so upstream additions never break a
     * release, `null` for a non-null field falls back to the field's default, and absent
     * keys are not written back as `null`.
     */
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun create(engine: HttpClientEngine = OkHttp.create()): HttpClient {
        val serializer = json
        return HttpClient(engine) {
            // Non-2xx bodies carry the useful error, so callers read the status themselves
            // (see HttpResponse.bodyOrThrow) rather than getting a generic exception here.
            expectSuccess = false
            install(ContentNegotiation) { json(serializer) }
            install(HttpTimeout) {
                requestTimeoutMillis = TIMEOUT_MS
                connectTimeoutMillis = TIMEOUT_MS
                socketTimeoutMillis = TIMEOUT_MS
            }
            defaultRequest {
                // appendIfNameAbsent: a request that sets its own User-Agent (xStocks) wins,
                // and no second value is appended next to it.
                headers.appendIfNameAbsent(HttpHeaders.UserAgent, USER_AGENT)
            }
        }
    }
}
