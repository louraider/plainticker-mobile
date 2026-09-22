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

    /** System program: lamport transfers, including the 0-lamport reference a vote or pass carries. */
    const val SYSTEM = "11111111111111111111111111111111"

    /** Compute Budget: unit limit and unit price, which together set a priority fee. */
    const val COMPUTE_BUDGET = "ComputeBudget111111111111111111111111111111"

    /** Associated Token Account program. */
    const val ASSOCIATED_TOKEN = "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL"

    /** SPL Memo v2: the `PT-VOTE:` and `PT-PASS:` memos the tally reads. */
    const val MEMO = "MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr"

    /** Jupiter Aggregator v6: every Metis-routed Ultra order (`route_v2`), seen in real /order answers. */
    const val JUPITER_AGGREGATOR_V6 = "JUP6LkbZbjS1jKKwapdHNy74zcZ3tLUZoi5QNyVTaV4"

    /** JupiterZ, Jupiter's RFQ program: every `jupiterz`-routed Ultra order (`fill`), seen in real /order answers. */
    const val JUPITER_RFQ = "61DFfeTKM7trxYcPQCM78bJ794ddZprZpAwAnLiwTpYH"
}
