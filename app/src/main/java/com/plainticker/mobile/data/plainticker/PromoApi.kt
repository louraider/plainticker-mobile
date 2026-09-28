package com.plainticker.mobile.data.plainticker

import com.plainticker.mobile.data.net.HttpClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

/**
 * `POST /api/v1/promo/redeem`: the founder's hackathon judge codes, each worth 30 days of Pro.
 *
 * ```
 * POST /api/v1/promo/redeem
 * Content-Type: application/json
 * X-PT-Code: <the device's own code, in the clear>
 *
 * { "code": "PTXXXXXXXXXXXX" }
 *
 * 200 { "pro": true, "source": "promo", "until": "2026-10-26T00:00:00.000Z" }
 * ```
 *
 * The device's own code rides in `X-PT-Code` only, the same header [EntitlementApi] and
 * [com.plainticker.mobile.data.auth.GoogleAuthApi] send: never the URL, never the body, never a
 * log line. The promo code itself is the opposite: it is what this call's body exists to carry,
 * and it is never a secret this device holds, so it is fine to send, unlike the device code.
 *
 * @throws PromoError.BadRequest on 400 `bad_request`
 * @throws PromoError.InvalidCode on 400 `invalid_code`
 * @throws PromoError.ExpiredCode on 410 `expired_code`
 * @throws PromoError.AlreadyRedeemed on 409 `already_redeemed`
 * @throws PromoError.AlreadyApplied on 409 `already_applied`
 * @throws PromoError.RekeyRequired on 401 `rekey_required` (a legacy device code, not yet rekeyed)
 * @throws PromoError.CodeRetired on 401 `code_retired`
 * @throws PromoError.RateLimited on 429
 * @throws PromoError.NotOpen on 404, the route not deployed yet
 * @throws PromoError.Unavailable on anything else, a 200 this app cannot parse included
 */
class PromoApi(
    private val client: HttpClient,
    private val baseUrl: String = PlainTickerApi.BASE_URL,
    private val json: Json = HttpClientFactory.json,
) {
    suspend fun redeem(code: String, deviceCode: String?): PromoRedeemResponse {
        val response = client.post("$baseUrl$PATH") {
            contentType(ContentType.Application.Json)
            if (!deviceCode.isNullOrBlank()) header(HEADER_CODE, deviceCode)
            setBody(PromoRedeemRequest(code))
        }
        val text = runCatching { response.bodyAsText() }.getOrNull()
        if (!response.status.isSuccess()) {
            throw PromoError.fromErrorBody(response.status.value, text, json)
        }
        return text
            ?.let { runCatching { json.decodeFromString(PromoRedeemResponse.serializer(), it) }.getOrNull() }
            ?: throw PromoError.Unavailable(response.status.value, text?.trim()?.take(EXCERPT))
    }

    companion object {
        const val PATH = "/promo/redeem"
        const val HEADER_CODE = EntitlementApi.HEADER_CODE
        private const val EXCERPT = 200

        /**
         * Uppercase, strip spaces and dashes: the same normalization the server applies before it
         * checks a code, so `pt-xxxx xxxx-xxxx`, `PT-XXXX-XXXX-XXXX` and `PTXXXXXXXXXXXX` all reach
         * `redeem` as the identical string, and the field can show the reader whichever shape they
         * pasted without this app silently redeeming a differently-cased duplicate.
         *
         * Applied once, to what is sent, never to the field as it is typed (fresh-device QA of
         * 1.3.23: rewriting the field on every keystroke raced the keyboard and dropped characters
         * from a fast `PT-AAAA-BBBB-CCCC`). Tolerant of what a paste brings along: any whitespace,
         * a line break, a no-break space, and the typographic dashes a chat or a note turns a
         * hyphen into.
         */
        fun normalize(raw: String): String =
            raw.uppercase().filterNot { it.isWhitespace() || it in DASHES }

        /** The hyphen, and every dash a paste can carry in its place. */
        private const val DASHES = "-_\u2010\u2011\u2012\u2013\u2014\u2015\u2212\uFE58\uFE63\uFF0D"
    }
}
