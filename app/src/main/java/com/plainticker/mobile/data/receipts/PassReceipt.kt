package com.plainticker.mobile.data.receipts

import kotlinx.serialization.Serializable

/**
 * One pass payment this app signed and sent, as this app saw it (task A6 review: process death
 * between the wallet returning a signature and `POST /api/v1/pass/confirm` resolving is two
 * network round trips apart, and the ten-minute cron is the only other thing that would ever
 * record it).
 *
 * Written the moment `signAndSendTransactions` hands back a signature, before the confirm call is
 * even made: `signAndSendTransactions` both signs and submits, so the payment is on the chain from
 * that instant, and a record of it must survive whether or not this app is still open to hear the
 * confirm call's own answer. [confirmed] flips to true once `pass/confirm` (or the cron, read back
 * through a later `entitlement` poll) has been seen to succeed for this signature; until then the
 * Portfolio's Pro block reads this receipt as a pending payment and refuses to offer a second one.
 */
@Serializable
data class PassReceipt(
    /** The transaction signature. Also the identity of the receipt: one payment, one row. */
    val signature: String,
    /** The wallet that signed, base58. */
    val payer: String,
    /** Wall clock when the wallet handed back the signature, epoch millis. */
    val landedAtMillis: Long,
    /** True once this device has seen the server confirm the payment. */
    val confirmed: Boolean = false,
)
