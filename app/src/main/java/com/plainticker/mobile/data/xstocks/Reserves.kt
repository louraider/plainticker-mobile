package com.plainticker.mobile.data.xstocks

/**
 * The proof-of-reserves answer for one symbol, reduced to what the Detail screen states: how many
 * shares the custodian holds against how many tokens are in circulation.
 *
 * The absence of this object is meaningful and is never a zero. xStocks answers 200 with a JSON
 * `null` for a symbol it publishes no reserves for, and a row that drew that as "0 shares held"
 * would be making the strongest possible claim out of no data at all. The mapper therefore returns
 * null for a missing answer, a missing figure or an unparseable one, and the caller renders it as
 * unavailable.
 *
 * Pure: a mapper over [ProofOfReserves]. No Android, no formatting.
 */
data class Reserves(
    /** Shares the custodian reports holding. */
    val sharesHeld: Double,
    /** Tokens outstanding on chain, as xStocks reports the circulating supply. */
    val tokensInCirculation: Double,
    /** Custodians named in the holdings, in the order they were reported; often exactly one. */
    val custodians: List<String>,
    /** The timestamp xStocks stamped the attestation with, as it sent it. */
    val asOf: String?,
) {
    /** The single named custodian, or null when none was named or several were. */
    val custodian: String? get() = custodians.singleOrNull()

    /** sharesHeld over tokensInCirculation. Null when no token is outstanding to cover. */
    val coverage: Double?
        get() = if (tokensInCirculation > 0.0) sharesHeld / tokensInCirculation else null
}

/**
 * The reserves a screen can state, or null when xStocks published none for this symbol: a JSON
 * `null` answer, a missing figure, or one that is not a finite number.
 */
fun ProofOfReserves?.toReserves(): Reserves? {
    val por = this ?: return null
    val held = por.sharesHeldValue?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
    val supply = por.circulatingSupplyValue?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
    // Zero shares for zero tokens with no custodian named is xStocks' shape for "nothing published
    // yet", not an attestation (device QA of 1.3.17: JEFx and AALx answered exactly that while
    // their mints held 94,230 and 230,622 tokens, and the row read "0 shares held for 0 tokens").
    if (held == 0.0 && supply == 0.0 && por.holdings.none { !it.provider.isNullOrBlank() }) return null
    return Reserves(
        sharesHeld = held,
        tokensInCirculation = supply,
        custodians = por.holdings.mapNotNull { it.provider?.takeIf(String::isNotBlank) }.distinct(),
        asOf = por.timestamp,
    )
}
