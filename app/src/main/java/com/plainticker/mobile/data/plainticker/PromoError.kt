package com.plainticker.mobile.data.plainticker

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.IOException

/**
 * Why `POST /api/v1/promo/redeem` did not hand back a usable answer. One case per `code` the
 * server contract names, plus [NotOpen] for the 404 that means the route is not deployed yet (the
 * same convention [EntitlementError.Disabled] and [PassError.Disabled] already read) and
 * [Unavailable] for a 5xx or a 200 this app cannot parse. The server's own `error` sentence never
 * reaches a screen; it goes to the debug log, and the reader gets one of this app's own sentences.
 */
sealed class PromoError(val status: Int?, val code: String?, val detail: String?, message: String) :
    IOException(message) {

    /** 400 `bad_request`: the request itself was malformed (an empty or unshaped code). */
    class BadRequest(detail: String?) :
        PromoError(400, CODE_BAD_REQUEST, detail, "promo redeem: bad request: ${detail ?: "-"}")

    /** 400 `invalid_code`: well-formed, but not a code the server recognizes. */
    class InvalidCode(detail: String?) :
        PromoError(400, CODE_INVALID_CODE, detail, "promo redeem: invalid code: ${detail ?: "-"}")

    /** 410 `expired_code`: a real code, past its own window. Not a retry. */
    class ExpiredCode(detail: String?) :
        PromoError(410, CODE_EXPIRED_CODE, detail, "promo redeem: expired code: ${detail ?: "-"}")

    /** 409 `already_redeemed`: this code has already been used, by this device or another. */
    class AlreadyRedeemed(detail: String?) :
        PromoError(409, CODE_ALREADY_REDEEMED, detail, "promo redeem: already redeemed: ${detail ?: "-"}")

    /** 409 `already_applied`: this device already carries a promo entitlement. */
    class AlreadyApplied(detail: String?) :
        PromoError(409, CODE_ALREADY_APPLIED, detail, "promo redeem: already applied: ${detail ?: "-"}")

    /** 401 `rekey_required`: this device still carries a legacy 10-symbol code (DeviceRekeyer). */
    class RekeyRequired : PromoError(401, CODE_REKEY_REQUIRED, null, "promo redeem: rekey required")

    /** 401 `code_retired`: the server no longer accepts this device's code anywhere. */
    class CodeRetired : PromoError(401, CODE_CODE_RETIRED, null, "promo redeem: code retired")

    class RateLimited(detail: String?) :
        PromoError(429, CODE_RATE_LIMITED, detail, "promo redeem: rate limited: ${detail ?: "-"}")

    /** The route is not deployed yet: a 404, the same convention every other `/api/v1` route reads. */
    class NotOpen(status: Int?) : PromoError(status, null, null, "promo redeem: not open ($status)")

    /** A 5xx, a gateway page, or a 200 this app could not parse as the contract. */
    class Unavailable(status: Int?, detail: String?) :
        PromoError(status, null, detail, "promo redeem unavailable${status?.let { " (HTTP $it)" } ?: ""}: ${detail ?: "-"}")

    companion object {
        const val CODE_BAD_REQUEST = "bad_request"
        const val CODE_INVALID_CODE = "invalid_code"
        const val CODE_EXPIRED_CODE = "expired_code"
        const val CODE_ALREADY_REDEEMED = "already_redeemed"
        const val CODE_ALREADY_APPLIED = "already_applied"
        const val CODE_RATE_LIMITED = "rate_limited"
        const val CODE_REKEY_REQUIRED = "rekey_required"
        const val CODE_CODE_RETIRED = "code_retired"
        private const val EXCERPT = 200

        /** Maps a non-2xx `/promo/redeem` answer onto one of the states above. */
        fun fromErrorBody(status: Int, body: String?, json: Json): PromoError {
            val obj = body?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() } as? JsonObject
            val detail = obj.str("error") ?: body?.trim()?.take(EXCERPT)?.takeIf { it.isNotEmpty() }
            val code = obj.str("code")
            // The pack's shared contract (2026-09-27) sends the device-code answers as
            // {"error":"<code>"}; read either field so neither shape is missed.
            val deviceCode = setOfNotNull(code, obj.str("error"))
            return when {
                status == 401 && CODE_REKEY_REQUIRED in deviceCode -> RekeyRequired()
                status == 401 && CODE_CODE_RETIRED in deviceCode -> CodeRetired()
                status == 400 && code == CODE_INVALID_CODE -> InvalidCode(detail)
                status == 400 -> BadRequest(detail)
                status == 410 -> ExpiredCode(detail)
                status == 409 && code == CODE_ALREADY_APPLIED -> AlreadyApplied(detail)
                status == 409 -> AlreadyRedeemed(detail)
                status == 429 -> RateLimited(detail)
                status == 404 -> NotOpen(status)
                else -> Unavailable(status, detail)
            }
        }

        private fun JsonObject?.str(key: String): String? = (this?.get(key) as? JsonPrimitive)?.contentOrNull
    }
}
