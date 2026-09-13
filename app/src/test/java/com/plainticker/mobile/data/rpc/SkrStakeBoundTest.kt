package com.plainticker.mobile.data.rpc

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bound on what this app will print as a staked principal, written from the 2026-09-13
 * measurement of the whole SKR staking program.
 *
 * The account that made the bound necessary is the subject of half this file. It is not a
 * hypothetical: 11,452,317,590,968,446,072 is what the eight bytes at offset 105 of one real
 * account in the program decode to, and there is no reading of it that is a stake.
 */
class SkrStakeBoundTest {

    /** The raw value one account in the program actually carries, as an unsigned 64-bit integer. */
    private val impossibleUnsigned = BigInteger("11452317590968446072")

    private fun stake(vararg principals: Long) =
        SkrStake(principals.mapIndexed { i, raw -> SkrStakeAccount("stake$i".padEnd(44, '1'), raw) })

    // ---- What the bound is ---------------------------------------------------------------------

    @Test
    fun `the ceiling is the staked supply measured on 2026-09-13, in base units`() {
        assertEquals(4_389_047_810L, SkrStakeBound.STAKED_SUPPLY_SKR)
        assertEquals(4_389_047_810_000_000L, SkrStakeBound.STAKED_SUPPLY_RAW)
        assertEquals(6, SkrStakeBound.SKR_DECIMALS)
        // The ceiling itself has to fit the type it bounds, or the bound could never be applied.
        assertTrue(SkrStakeBound.STAKED_SUPPLY_RAW < Long.MAX_VALUE)
    }

    // ---- The account the bound exists for -------------------------------------------------------

    @Test
    fun `the impossible principal does not even fit a signed long, and is refused as read`() {
        // 1.145 x 10^19 against a signed maximum of 9.223 x 10^18: the field is unsigned and
        // SkrStakeAccount.principalRaw is not, so the decode lands on the far side of zero.
        assertTrue(impossibleUnsigned > BigInteger.valueOf(Long.MAX_VALUE))
        val asDecoded = impossibleUnsigned.subtract(BigInteger.ONE.shiftLeft(64)).toLong()
        assertEquals(-6_994_426_482_741_105_544L, asDecoded)

        assertNull("a negative principal is a misread, not a stake", SkrStakeBound.principalOf(stake(asDecoded)))
        assertTrue(!SkrStakeBound.isPlausible(asDecoded))
    }

    @Test
    fun `the same account read as unsigned is thousands of times the whole staked supply`() {
        // The figure the plan quotes, 1.145 x 10^13 SKR, against 4.389 x 10^9 SKR that is staked.
        val asSkr = impossibleUnsigned.toBigDecimal().movePointLeft(SkrStakeBound.SKR_DECIMALS).toDouble()
        assertEquals(2_609.0, asSkr / SkrStakeBound.STAKED_SUPPLY_SKR, 1.0)
        // Clamped into the type, it is still refused: the bound is the supply and not the type.
        assertNull(SkrStakeBound.principalOf(stake(Long.MAX_VALUE)))
        assertNull(SkrStakeBound.principalOf(stake(SkrStakeBound.STAKED_SUPPLY_RAW + 1L)))
    }

    // ---- What the bound lets through -------------------------------------------------------------

    @Test
    fun `the wallet the forwarder was proved with reads exactly as the device measured it`() {
        // 31,209.870777 SKR, HTTP 200 in 3.6 s through the production forwarder, 2026-09-13.
        assertEquals(31_209_870_777L, SkrStakeBound.principalOf(stake(31_209_870_777L)))
    }

    @Test
    fun `a wallet with nothing staked reads as zero, which is not the same as unread`() {
        assertEquals(0L, SkrStakeBound.principalOf(SkrStake.NONE))
        assertEquals(0L, SkrStakeBound.principalOf(stake(0L)))
    }

    @Test
    fun `several stake accounts are summed`() {
        assertEquals(9_000_000L, SkrStakeBound.principalOf(stake(9_000_000L, 0L)))
        assertEquals(41_209_870_777L, SkrStakeBound.principalOf(stake(31_209_870_777L, 10_000_000_000L)))
    }

    @Test
    fun `the median and the threshold the plan measured both pass`() {
        assertEquals(6_719_000_000L, SkrStakeBound.principalOf(stake(6_719_000_000L)))
        assertEquals(10_000_000_000L, SkrStakeBound.principalOf(stake(10_000_000_000L)))
        // Every real stake in the program, including the largest, is under the supply it sums into.
        assertEquals(SkrStakeBound.STAKED_SUPPLY_RAW, SkrStakeBound.principalOf(stake(SkrStakeBound.STAKED_SUPPLY_RAW)))
    }

    // ---- One bad account poisons the read, rather than inflating the total -----------------------

    @Test
    fun `one account outside the bound refuses the whole read`() {
        assertNull(SkrStakeBound.principalOf(stake(31_209_870_777L, -6_994_426_482_741_105_544L)))
        assertNull(SkrStakeBound.principalOf(stake(31_209_870_777L, SkrStakeBound.STAKED_SUPPLY_RAW + 1L)))
    }

    @Test
    fun `plausible accounts that sum past the supply are refused too`() {
        val half = SkrStakeBound.STAKED_SUPPLY_RAW / 2L + 1L
        assertNull(SkrStakeBound.principalOf(stake(half, half)))
    }

    @Test
    fun `a sum that would overflow the type is refused rather than wrapping`() {
        // Not reachable through the bound above, and asserted anyway: the fold is what stands
        // between a wrapped total and a negative figure on a screen.
        assertNull(SkrStakeBound.principalOf(stake(Long.MAX_VALUE, Long.MAX_VALUE)))
    }
}
