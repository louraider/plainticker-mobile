package com.myapp.data

/** Mainnet mints the data layer talks about by name. Everything else comes from the xStocks catalog. */
object KnownMints {
    /** Circle USDC, 6 decimals, classic Token program. The swap sheet's input side. */
    const val USDC = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"

    /** Tesla xStock. Token-2022 with the scaledUiAmount extension; multiplier currently 1. */
    const val TSLAX = "XsDoVfqeBukxuZHWhdvWHBhgEHjGNst4MLodqsJHzoB"

    /** Seeker (SKR) governance token, 6 decimals. */
    const val SKR = "SKRbvo6Gf7GondiT3BbTfuRDPqLWei4j2Qy2NPGZhW3"
}
