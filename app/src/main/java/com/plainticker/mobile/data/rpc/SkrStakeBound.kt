package com.plainticker.mobile.data.rpc

/**
 * The most staked SKR this app will believe one wallet holds, and the reason it needs a ceiling
 * at all.
 *
 * One `getProgramAccounts` over the whole SKR staking program on 2026-09-13, with the same
 * 8-byte slice at offset 105 the forwarder pins, answered in 1.0 s with 47,965 accounts, 46,436
 * of them carrying a non-zero principal. Summed with one account excluded, the program holds
 * **4,389,047,810 SKR**, and the median stake in it is 6,719 SKR.
 *
 * The excluded account is the reason for this file. Its eight bytes decode to
 * 11,452,317,590,968,446,072 raw, which is about **1.145 x 10^13 SKR**, or 2,609 times everything
 * that is staked. It is almost certainly not a stake account at all but another account of the
 * same length in the same program whose bytes at 105 mean something else, and the naive read
 * misinterprets it. Two things follow, and both are bounded here:
 *
 * 1. **A principal above the staked supply is not a large stake, it is a misread.** No wallet can
 *    have staked more than the whole of what is staked, so [STAKED_SUPPLY_RAW] is the ceiling.
 * 2. **A negative principal is the same fault seen from the other side.** The field is an
 *    unsigned 64-bit integer and [SkrStakeAccount.principalRaw] is a signed one, so that account
 *    does not even fit: 1.145 x 10^19 against a signed maximum of 9.223 x 10^18, and the decode
 *    lands at -6,994,426,482,741,105,544. A principal below zero is therefore refused too, and
 *    refusing it is what keeps a wallet from voting with a negative weight.
 *
 * A read outside the bound yields null and the surface says the stake could not be read. It does
 * not print the figure and then hedge it: an app whose claim is that it withholds numbers it
 * cannot stand behind does not get to make an exception for its own feature.
 *
 * The ceiling is a measurement and it will drift as SKR is staked and unstaked. It is deliberately
 * not tightened to the measurement: it is here to catch a figure wrong by nine orders of
 * magnitude, not to adjudicate a large staker, and a bound that had to be re-measured to stay
 * correct would be the wrong kind of bound.
 */
object SkrStakeBound {

    /** Total staked in the program on 2026-09-13, the one misreading account excluded. */
    const val STAKED_SUPPLY_SKR: Long = 4_389_047_810L

    /** [STAKED_SUPPLY_SKR] in the program's own base units; SKR carries 6 decimals. */
    const val STAKED_SUPPLY_RAW: Long = STAKED_SUPPLY_SKR * 1_000_000L

    /** Decimals the SKR mint carries, and the scale every principal in this file is counted in. */
    const val SKR_DECIMALS: Int = 6

    /** True when one account's principal is a figure this app will stand behind. */
    fun isPlausible(principalRaw: Long): Boolean = principalRaw in 0L..STAKED_SUPPLY_RAW

    /**
     * The staked principal [stake] carries in base units, or null when any part of it is outside
     * the bound. Null is "not read", never zero: a wallet that stakes nothing and a wallet whose
     * account did not decode are different facts and get different sentences.
     *
     * Every account is checked before the sum, so one misreading account refuses the whole read
     * rather than being added into a total that would then look merely large. The total is
     * checked again afterwards, which is what keeps many plausible accounts from summing past
     * the supply, or past a signed 64-bit integer.
     */
    fun principalOf(stake: SkrStake): Long? {
        if (stake.accounts.any { !isPlausible(it.principalRaw) }) return null
        val total = stake.accounts.fold(0L) { running, account ->
            val next = running + account.principalRaw
            // A sum that went backwards has overflowed, and an overflowed total is not a stake.
            if (next < running) return null else next
        }
        return total.takeIf { isPlausible(it) }
    }
}
