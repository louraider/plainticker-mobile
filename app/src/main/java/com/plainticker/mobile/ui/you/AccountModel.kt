package com.plainticker.mobile.ui.you

import androidx.annotation.StringRes
import com.plainticker.mobile.R
import com.plainticker.mobile.data.auth.DeviceCodeStatus
import com.plainticker.mobile.prefs.SignedInAccount
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.raw
import com.plainticker.mobile.ui.words

/**
 * What the Account section says, decided away from the composition like the rest of You
 * (YouModel.kt). Every sentence is a strings.xml resource; the only raw text is what the server
 * returned about the reader (their email or name) and their wallets' short keys.
 */

/** The one line each way a sign-in can end is named in. Exhaustive: a new case will not compile unnamed. */
@StringRes
fun accountMessageRes(message: AccountMessage): Int = when (message) {
    AccountMessage.CANCELLED -> R.string.account_msg_cancelled
    AccountMessage.NO_ACCOUNT -> R.string.account_msg_no_account
    AccountMessage.NO_PLAY_SERVICES -> R.string.account_msg_no_play_services
    AccountMessage.CREDENTIAL_FAILED -> R.string.account_msg_credential_failed
    AccountMessage.SETUP_PROBLEM -> R.string.account_msg_setup_problem
    AccountMessage.INTERRUPTED -> R.string.account_msg_interrupted
    AccountMessage.NONCE_MISMATCH -> R.string.account_msg_nonce_mismatch
    AccountMessage.NETWORK -> R.string.account_msg_network
    AccountMessage.BAD_REQUEST -> R.string.account_msg_bad_request
    AccountMessage.BAD_DEVICE_CODE -> R.string.account_msg_bad_device_code
    AccountMessage.INVALID_TOKEN -> R.string.account_msg_invalid_token
    AccountMessage.EXPIRED_TOKEN -> R.string.account_msg_expired_token
    AccountMessage.WRONG_AUDIENCE -> R.string.account_msg_wrong_audience
    AccountMessage.NONCE_INVALID -> R.string.account_msg_nonce_invalid
    AccountMessage.NONCE_EXPIRED -> R.string.account_msg_nonce_expired
    AccountMessage.NONCE_UNAVAILABLE -> R.string.account_msg_nonce_unavailable
    AccountMessage.EMAIL_NOT_VERIFIED -> R.string.account_msg_email_not_verified
    AccountMessage.RATE_LIMITED -> R.string.account_msg_rate_limited
    AccountMessage.INTERNAL -> R.string.account_msg_internal
    AccountMessage.AUTH_DISABLED -> R.string.account_msg_auth_disabled
    AccountMessage.NOT_CONFIGURED -> R.string.account_msg_not_configured
    AccountMessage.JWKS_UNAVAILABLE -> R.string.account_msg_jwks_unavailable
    AccountMessage.NOT_OPEN -> R.string.account_msg_not_open
    AccountMessage.UNKNOWN -> R.string.account_msg_unknown
    AccountMessage.LINK_ON_WEB -> R.string.account_msg_link_on_web
    AccountMessage.REKEY_PENDING -> R.string.account_msg_rekey_pending
    AccountMessage.CODE_RETIRED -> R.string.account_msg_code_retired
    AccountMessage.SIGN_OUT_UNCONFIRMED -> R.string.account_msg_sign_out_unconfirmed
}

/** Whether [message] comes with the text action that opens [AccountMessage.LINK_ON_WEB_URL]. */
fun accountMessageOpensWeb(message: AccountMessage?): Boolean = message == AccountMessage.LINK_ON_WEB

/** The line You draws about this phone's own code, or null when there is nothing to say. */
@StringRes
fun deviceCodeNoticeRes(status: DeviceCodeStatus): Int? = when (status) {
    DeviceCodeStatus.OK -> null
    DeviceCodeStatus.SIGN_IN_AGAIN -> R.string.device_code_sign_in_again
    DeviceCodeStatus.REPLACED -> R.string.device_code_replaced
    DeviceCodeStatus.RETIRED -> R.string.device_code_retired
}

/** Who is signed in: the email, else the name, else a plain "Google account". */
fun accountIdentity(account: SignedInAccount): Copy =
    account.email?.let(::raw) ?: account.name?.let(::raw) ?: words(R.string.account_identity_fallback)

/** Each linked wallet as a short key (first four, last four), the form You already draws a wallet in. */
fun linkedWalletKeys(account: SignedInAccount): List<String> = account.linkedWallets.map { Fmt.shortKey(it) }

/** The one line each way an unlink can refuse is named in. Exhaustive: a new case will not compile unnamed. */
@StringRes
fun unlinkFailureRes(failure: UnlinkFailure): Int = when (failure) {
    UnlinkFailure.BAD_REQUEST -> R.string.account_unlink_error_bad_request
    UnlinkFailure.NOT_LINKED -> R.string.account_unlink_error_not_linked
    UnlinkFailure.LAST_METHOD -> R.string.account_unlink_error_last_method
    UnlinkFailure.RATE_LIMITED -> R.string.account_unlink_error_rate_limited
    UnlinkFailure.NOT_OPEN -> R.string.account_unlink_error_not_open
    UnlinkFailure.UNAVAILABLE -> R.string.account_unlink_error_unavailable
}
