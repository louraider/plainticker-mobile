package com.plainticker.mobile.ui.swap

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * The typed amount, converted and refused, with no network call and no floating point.
 *
 * A swap amount is money in base units. Going through a Double loses the exact value on the way
 * (20.2 USDC is not representable in binary, and 20.2 * 1e6 is 20199999.999999996), and the
 * rounding that repairs that lands on the chain, so every conversion here is
 * [BigDecimal.movePointRight] and an exact integer or nothing.
 *
 * Every refusal is decided here, before [SwapViewModel] makes any request: an empty field, a
 * value that is not a number, more decimals than the token counts, zero or less, and more than
 * the wallet has. That is the whole point of validating against a balance read from the chain
 * rather than against whatever Jupiter says when the order is refused.
 */
object SwapAmount {

    /**
     * Parses what the user typed. Grouping commas and spaces are forgiven, a bare leading or
     * trailing decimal point is completed, and everything else that is not a plain decimal is
     * [AmountProblem.NOT_A_NUMBER].
     */
    fun parse(
        text: String,
        decimals: Int,
        balanceRaw: Long,
        /**
         * The token's scaled UI multiplier ([SwapToken.multiplier]). What a person types is what
         * their wallet shows, raw / 10^decimals x multiplier, so the raw amount sent is the typed
         * value / multiplier x 10^decimals. One, for USDC and every unsplit xStock, keeps the exact
         * integer rule above; any other value rounds the base units DOWN, so this app never sends
         * more than was typed, by less than one base unit.
         */
        multiplier: BigDecimal = BigDecimal.ONE,
    ): AmountInput {
        val cleaned = clean(text)
        if (cleaned.isEmpty()) return AmountInput(text, 0L, AmountProblem.EMPTY)

        val value = runCatching { BigDecimal(cleaned) }.getOrNull()
            ?: return AmountInput(text, 0L, AmountProblem.NOT_A_NUMBER)
        if (value.signum() <= 0) return AmountInput(text, 0L, AmountProblem.NOT_ABOVE_ZERO)

        val exact: BigInteger = if (multiplier.compareTo(BigDecimal.ONE) == 0) {
            runCatching { value.movePointRight(decimals).toBigIntegerExact() }.getOrNull()
                ?: return AmountInput(text, 0L, AmountProblem.TOO_PRECISE)
        } else {
            if (multiplier.signum() <= 0) return AmountInput(text, 0L, AmountProblem.NOT_A_NUMBER)
            val units = value.movePointRight(decimals).divide(multiplier, 0, RoundingMode.DOWN).toBigInteger()
            if (units.signum() <= 0) return AmountInput(text, 0L, AmountProblem.BELOW_ONE_UNIT)
            units
        }

        // Past Long.MAX_VALUE there is no balance that could cover it, so it is over the balance
        // rather than unreadable; the raw value stays 0 so nothing downstream can use it.
        val raw = runCatching { exact.longValueExact() }.getOrNull()
            ?: return AmountInput(text, 0L, AmountProblem.ABOVE_BALANCE)
        if (raw > balanceRaw) return AmountInput(text, raw, AmountProblem.ABOVE_BALANCE)
        return AmountInput(text, raw, null)
    }

    /**
     * What the Max action puts in the field: the whole balance, written so that [parse] reads it
     * back to exactly the same base units. No grouping commas, so this is not [com.plainticker.mobile.ui.Fmt];
     * Fmt writes numbers for reading, this one writes a number for typing.
     */
    fun maxText(balanceRaw: Long, decimals: Int, multiplier: BigDecimal = BigDecimal.ONE): String {
        if (balanceRaw <= 0L) return "0"
        return BigDecimal.valueOf(balanceRaw).movePointLeft(decimals).multiply(multiplier)
            .stripTrailingZeros().toPlainString()
    }

    private fun clean(text: String): String {
        var s = text.filterNot { it.isWhitespace() || it == ',' }
        if (s.startsWith(".")) s = "0$s"
        if (s.endsWith(".")) s = s + "0"
        return s
    }
}
