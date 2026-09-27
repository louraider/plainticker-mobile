package com.plainticker.mobile.ui.pass

import androidx.annotation.StringRes
import com.plainticker.mobile.R

/**
 * The "Have a code?" machine in You's Plan group: the founder's hackathon judge codes, each worth
 * 30 days of Pro (`POST /api/v1/promo/redeem`).
 *
 *     Idle --open()--> Editing --apply()--> Applying --200--> Success
 *      ^                  ^  ^                  |
 *      |                  |  +---apply() again--+---4xx/5xx--> Failed
 *      +------dismiss()---+----------------------------------------+
 *
 * [Editing] and [Failed] both carry the normalized text the field shows, so a reader who typed a
 * bad code keeps it on screen to fix rather than losing it; [inputChanged] moves a [Failed] state
 * back to [Editing], clearing the error the instant they touch the field again.
 */
sealed interface PromoState {

    /** The field is closed; only the "Have a code?" text action shows. */
    data object Idle : PromoState

    /** The field is open. [input] is already normalized (uppercase, no spaces or dashes). */
    data class Editing(val input: String) : PromoState

    /** The redeem call is in flight. [input] is what was sent, so the field can stay disabled on it. */
    data class Applying(val input: String) : PromoState

    /** Redeemed. [untilMillis] is when this device's Pro (through this code) ends, if the server said. */
    data class Success(val untilMillis: Long?) : PromoState

    /** The call was refused, or could not be read. [input] is kept so editing resumes on it. */
    data class Failed(val input: String, val reason: PromoRefusal) : PromoState
}

/**
 * Every way `promo/redeem` can refuse, mapped to one of this app's own sentences (server/promo's
 * contract, section "Errors"). The server's own `error` sentence never reaches a screen.
 */
enum class PromoRefusal(@StringRes val text: Int) {
    /** 400 `bad_request`: the code sent was empty or not shaped like one. */
    BAD_REQUEST(R.string.promo_error_bad_request),

    /** 400 `invalid_code`: well-formed, but not a code the server recognizes. */
    INVALID_CODE(R.string.promo_error_invalid_code),

    /** 410 `expired_code`: a real code, past its own window. */
    EXPIRED_CODE(R.string.promo_error_expired_code),

    /** 409 `already_redeemed`: this code has already been used. */
    ALREADY_REDEEMED(R.string.promo_error_already_redeemed),

    /** 409 `already_applied`: this device already carries a promo entitlement. */
    ALREADY_APPLIED(R.string.promo_error_already_applied),

    RATE_LIMITED(R.string.promo_error_rate_limited),

    /** 401 `rekey_required` still, after the one rekey and retry: the device code is mid-update. */
    REKEY_PENDING(R.string.promo_error_rekey_pending),

    /** 401 `code_retired`: the server no longer accepts this device's code. */
    CODE_RETIRED(R.string.promo_error_code_retired),

    /** 404: the route is not deployed yet. */
    NOT_OPEN(R.string.promo_error_not_open),

    /** A 5xx, or a 200 this app could not read. */
    UNAVAILABLE(R.string.promo_error_unavailable),
}
