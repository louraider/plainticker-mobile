package com.plainticker.mobile.data

/** Mainnet mints the data layer talks about by name. Everything else comes from the xStocks catalog. */
object KnownMints {
    /** Circle USDC, 6 decimals, classic Token program. The swap sheet's input side. */
    const val USDC = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"

    /** Tether USDT, 6 decimals, classic Token program. A pass is accepted in it at the same amount as USDC. */
    const val USDT = "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB"

    /** Tesla xStock. Token-2022 with the scaledUiAmount extension; multiplier currently 1. */
    const val TSLAX = "XsDoVfqeBukxuZHWhdvWHBhgEHjGNst4MLodqsJHzoB"

    /**
     * Wrapped SOL, the native mint, classic Token program. A Metis route through SOL opens the
     * wallet's own wrapped-SOL account and closes it back to the wallet in the same transaction.
     */
    const val WSOL = "So11111111111111111111111111111111111111112"

    /** Seeker (SKR) governance token, 6 decimals. */
    const val SKR = "SKRbvo6Gf7GondiT3BbTfuRDPqLWei4j2Qy2NPGZhW3"
}
