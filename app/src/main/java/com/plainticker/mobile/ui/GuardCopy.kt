package com.plainticker.mobile.ui

import com.plainticker.mobile.R
import com.plainticker.mobile.wallet.TransactionGuard

/**
 * What a screen says when [TransactionGuard] refused a transaction before the wallet saw it
 * (mock judges' review, 2026-09-27). A refused pass used to read "The server did not build this
 * payment", which was not what happened: the server built one, and this phone read it and would
 * not hand it on. Every flow that runs the guard (pass, vote, swap) says it the same way, with
 * the reason in the plain category [TransactionGuard.Why] carries; the guard's own reason, which
 * names programs and base units, stays in the debug log.
 */
fun TransactionGuard.Why.refusal(): Copy.Words = when (this) {
    TransactionGuard.Why.UNREADABLE -> words(R.string.guard_refused_unreadable)
    TransactionGuard.Why.NOT_YOUR_WALLET -> words(R.string.guard_refused_not_your_wallet)
    TransactionGuard.Why.WRONG_RECIPIENT -> words(R.string.guard_refused_wrong_recipient)
    TransactionGuard.Why.WRONG_AMOUNT -> words(R.string.guard_refused_wrong_amount)
    TransactionGuard.Why.HANDS_OVER_CONTROL -> words(R.string.guard_refused_hands_over_control)
    TransactionGuard.Why.UNKNOWN_PROGRAM -> words(R.string.guard_refused_unknown_program)
    TransactionGuard.Why.COSTS_MORE_THAN_SHOWN -> words(R.string.guard_refused_costs_more)
    TransactionGuard.Why.ABOVE_PASS_PRICE -> words(
        R.string.guard_refused_above_pass_price,
        Fmt.tokenAmount(TransactionGuard.PASS_PRICE_CEILING_RAW, STABLECOIN_DECIMALS),
    )
    TransactionGuard.Why.SLIPPAGE_TOO_WIDE -> words(R.string.guard_refused_slippage)
    TransactionGuard.Why.STRANGE_TOKEN_ACCOUNT -> words(R.string.guard_refused_token_account)
    TransactionGuard.Why.NOT_THIS_REQUEST -> words(R.string.guard_refused_not_this_request)
}

/** USDC and USDT both count six decimals: the unit [TransactionGuard.PASS_PRICE_CEILING_RAW] is in. */
private const val STABLECOIN_DECIMALS = 6

/**
 * What a swap sheet says when the transaction the wallet signed failed the guard
 * ([TransactionGuard.readSigned]): what the wallet changed, plainly enough for the founder to
 * report it, and that nothing was sent.
 */
fun TransactionGuard.SignedReading.Refused.sentence(): Copy.Words = when (change) {
    TransactionGuard.WalletChange.UNREADABLE -> words(R.string.swap_signed_unreadable)
    TransactionGuard.WalletChange.NOT_SIGNED -> words(R.string.swap_signed_not_signed)
    TransactionGuard.WalletChange.FEE_PAYER -> words(R.string.swap_signed_fee_payer)
    TransactionGuard.WalletChange.SIGNERS -> words(R.string.swap_signed_signers)
    TransactionGuard.WalletChange.PROGRAM -> words(R.string.swap_signed_program, Fmt.shortKey(program.orEmpty()))
    TransactionGuard.WalletChange.FEE_ABOVE_CEILING -> words(
        R.string.swap_signed_fee_ceiling,
        Fmt.tokenAmount(TransactionGuard.MAX_WALLET_FEE_LAMPORTS, LAMPORT_DECIMALS),
    )
    TransactionGuard.WalletChange.RECIPIENT -> words(R.string.swap_signed_recipient)
    TransactionGuard.WalletChange.AMOUNT -> words(R.string.swap_signed_amount)
    TransactionGuard.WalletChange.PAYS_LESS -> words(R.string.swap_signed_pays_less)
    TransactionGuard.WalletChange.CONTROL -> words(R.string.swap_signed_control)
    TransactionGuard.WalletChange.OTHER -> words(R.string.swap_failed_signed_mismatch)
}

/** A lamport counts nine decimals of a SOL. */
private const val LAMPORT_DECIMALS = 9
