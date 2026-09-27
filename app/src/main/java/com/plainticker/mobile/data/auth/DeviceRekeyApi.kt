package com.plainticker.mobile.data.auth

import com.plainticker.mobile.data.net.HttpClientFactory
import com.plainticker.mobile.data.plainticker.EntitlementApi
import com.plainticker.mobile.data.plainticker.PlainTickerApi
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * `POST /api/v1/device/rekey` (the pack's shared server contract, item 1, 2026-09-27): trades this
 * device's legacy 10-symbol code for a 26-symbol one, carrying every entitlement and binding of
 * the old code across server side.
 *
 * ```
 * POST /api/v1/device/rekey
 * X-PT-Code: <the current, legacy code>
 * Content-Type: application/json
 * { "new_code": "<26-symbol code>" }
 * 200 { "ok": true }   (idempotent: the same old and new pair answers 200 again)
 * ```
 *
 * Both codes are bearer credentials: the old one rides in `X-PT-Code` like every other call, the
 * new one in the body because that is what this call exists to carry. Neither ever reaches a URL,
 * a log line or an exception message.
 */
class DeviceRekeyApi(
    private val client: HttpClient,
    private val baseUrl: String = PlainTickerApi.BASE_URL,
    private val json: Json = HttpClientFactory.json,
) {
    /**
     * Returns only on a 200 whose body is exactly the contract's `{"ok":true}`: this answer is
     * what lets the device drop its old code, so anything less is [DeviceRekeyError.Unavailable]
     * and the same pair is simply sent again later.
     *
     * @throws DeviceRekeyError one case per contract error code, see there
     * @throws java.io.IOException when the network itself fails (no route, a timeout)
     */
    suspend fun rekey(currentCode: String, newCode: String) {
        val response = client.post("$baseUrl$PATH") {
            contentType(ContentType.Application.Json)
            header(HEADER_CODE, currentCode)
            setBody(DeviceRekeyRequest(newCode))
        }
        val text = runCatching { response.bodyAsText() }.getOrNull()
        val status = response.status.value
        if (!response.status.isSuccess()) throw DeviceRekeyError.fromErrorBody(status, text, json)
        val ok = text
            ?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() as? JsonObject }
            ?.let { (it["ok"] as? JsonPrimitive)?.booleanOrNull }
        if (ok != true) throw DeviceRekeyError.Unavailable(status)
    }

    companion object {
        const val PATH = "/device/rekey"
        const val HEADER_CODE = EntitlementApi.HEADER_CODE
    }
}

@Serializable
internal data class DeviceRekeyRequest(
    @kotlinx.serialization.SerialName("new_code") val newCode: String,
)

/**
 * Every way `POST /api/v1/device/rekey` can refuse. The message carries the status and the error
 * code only, never a code, never the body.
 */
sealed class DeviceRekeyError(val status: Int?, val code: String?) :
    IOException("device rekey refused: ${code ?: "-"}${status?.let { " (HTTP $it)" } ?: ""}") {

    /** 400 `invalid_new_code`: the server did not accept the new code's format. */
    class InvalidNewCode : DeviceRekeyError(400, CODE_INVALID_NEW_CODE)

    /** 400 `not_legacy`: the current code is already a 26-symbol one. */
    class NotLegacy : DeviceRekeyError(400, CODE_NOT_LEGACY)

    /** 409 `already_rekeyed`: the old code was already moved onto a DIFFERENT new code. */
    class AlreadyRekeyed : DeviceRekeyError(409, CODE_ALREADY_REKEYED)

    /** 409 `new_code_in_use`: the new code already belongs to another device. */
    class NewCodeInUse : DeviceRekeyError(409, CODE_NEW_CODE_IN_USE)

    /** 401 `code_retired`: the old code has already been retired. */
    class CodeRetired : DeviceRekeyError(401, CODE_CODE_RETIRED)

    class RateLimited : DeviceRekeyError(429, CODE_RATE_LIMITED)

    /** A 404: the route is not deployed yet, the convention every other `/api/v1` route reads. */
    class NotOpen : DeviceRekeyError(404, null)

    /** A 5xx, any other code, or a 2xx that is not `{"ok":true}`. */
    class Unavailable(status: Int?, code: String? = null) : DeviceRekeyError(status, code)

    companion object {
        const val CODE_INVALID_NEW_CODE = "invalid_new_code"
        const val CODE_NOT_LEGACY = "not_legacy"
        const val CODE_ALREADY_REKEYED = "already_rekeyed"
        const val CODE_NEW_CODE_IN_USE = "new_code_in_use"
        const val CODE_CODE_RETIRED = "code_retired"
        const val CODE_RATE_LIMITED = "rate_limited"

        fun fromErrorBody(status: Int, body: String?, json: Json): DeviceRekeyError {
            val obj = body?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() } as? JsonObject
            return when (val code = (obj?.get("error") as? JsonPrimitive)?.contentOrNull) {
                CODE_INVALID_NEW_CODE -> InvalidNewCode()
                CODE_NOT_LEGACY -> NotLegacy()
                CODE_ALREADY_REKEYED -> AlreadyRekeyed()
                CODE_NEW_CODE_IN_USE -> NewCodeInUse()
                CODE_CODE_RETIRED -> CodeRetired()
                CODE_RATE_LIMITED -> RateLimited()
                else -> when (status) {
                    429 -> RateLimited()
                    404 -> NotOpen()
                    // Only a known code may end the rekey for good; an unknown 409 or 401 is
                    // retried later, never read as a conflict.
                    else -> Unavailable(status, code?.take(CODE_MAX))
                }
            }
        }

        private const val CODE_MAX = 40
    }
}
