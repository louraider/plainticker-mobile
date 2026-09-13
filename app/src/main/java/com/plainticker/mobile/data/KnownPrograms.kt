package com.plainticker.mobile.data

/** Mainnet program ids the RPC layer pins its requests to. */
object KnownPrograms {
    /** SPL Token (classic): USDC and most mints. */
    const val TOKEN = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"

    /** Token-2022: every xStock mint (scaledUiAmount extension). */
    const val TOKEN_2022 = "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb"

    /**
     * SKR staking program. A stake account carries the staker's wallet at byte offset 41
     * (packed struct, not 40) and the principal as u64 little-endian, 6 decimals, at offset 105.
     */
    const val SKR_STAKING = "SKRskrmtL83pcL4YqLWt6iPefDqwXQWHSw9S9vz94BZ"
}
