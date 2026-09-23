package com.plainticker.mobile.data

/**
 * The two PlainTicker addresses a signed transaction may send anything to. Both are public keys,
 * pinned here so that what the wallet is handed is checked against this app's own copy rather than
 * against whatever the server's JSON says ([com.plainticker.mobile.wallet.TransactionGuard]).
 *
 * Where each was verified (2026-09-23):
 * - [TREASURY]: the web repo's `server/vote/README.md` ("Treasury `E1ST…mJdL`", introduced in
 *   commit 437e461), the same address in its `lib/billing` tests, and the mainnet pass of
 *   2026-09-20 that landed on it. Its keypair is kept offline by the founder; the server holds no
 *   key for it. Its USDC token account, [TREASURY_USDC_ACCOUNT], is the associated token address
 *   derived from it, and the same address Jupiter itself placed in a real /order answer for it.
 * - [VOTE_COLLECTOR]: the web repo's `server/vote/README.md`, and this repo's
 *   `docs/submission-answers.md` and `docs/plan-2026-09-10.md`.
 *
 * Changing either is a release, never a server setting: an address the server could change is not
 * a pin.
 */
object PinnedAddresses {
    /** Where a pass is paid: the owner of the token account the 12 USDC lands in. */
    const val TREASURY = "E1STBTGEYpHanVG4HWUJHfzEu6eGbnE9mnGX9KnAmJdL"

    /** The treasury's associated token account for USDC. A test pins the derivation to this value. */
    const val TREASURY_USDC_ACCOUNT = "G3wiPxjNtaKEtpAZ1zfvq3yCGDrUtaBXNCVbb6eAui74"

    /** Where a vote's 0-lamport transfer goes, so every vote can be found from the chain. Never spent from. */
    const val VOTE_COLLECTOR = "2KyWZetthwBAXji88M2Fz5ob4RPbYxCCBBKXueSoS7tJ"
}
