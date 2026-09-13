package com.plainticker.mobile.data.rpc

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import com.plainticker.mobile.data.KnownPrograms
import java.math.BigDecimal
import java.math.BigInteger

/**
 * What one Token-2022 mint says about itself, read from `getAccountInfo(mint, jsonParsed)`.
 *
 * This is the evidence half of the trust layer on Detail (docs/data-map.md, "Detail (T9)"): the
 * screen does not ask the issuer what the token is, it reads the mint. Each extension fact is
 * nullable, and null means one thing only: the extension is not on this mint. A present extension
 * that happens to be quiet says so with a value, so "no permanent delegate" and "a delegate exists
 * and the issuer has not used it" can never be drawn the same way, and neither can be confused
 * with "the chain was not read".
 *
 * Verified live against the TSLAx mint through the production forwarder on 2026-09-12
 * (app/src/test/resources/rpc/mint-tslax.json): xStock mints are owned by the Token-2022 program
 * [KnownPrograms.TOKEN_2022], not the classic one, and their decimals are [XSTOCK_DECIMALS].
 *
 * [from] is therefore strict on purpose. An account owned by the classic token program, an account
 * that is not a mint, or a payload whose shape does not match reads as null, which every caller
 * renders as unknown. It never degrades into a [MintFacts] with absent extensions, because "the
 * mint could not be read" and "the mint grants the issuer nothing" are opposite answers and only
 * one of them is safe to show.
 *
 * Pure: a mapper over [RpcAccount] and the raw kotlinx [JsonObject] under it. No Android, no
 * networking, no formatting.
 */
data class MintFacts(
    /** On-chain decimals. 8 on every xStock ([XSTOCK_DECIMALS]); read, never assumed. */
    val decimals: Int,
    /** Total supply in base units, as the u64 the node sent. */
    val supplyRaw: BigInteger,
    /** Null once the issuer has revoked it: no new tokens of this mint can be created. */
    val mintAuthority: String?,
    /** Null when no account of this mint can be frozen. */
    val freezeAuthority: String?,
    /** The `permanentDelegate` extension, or null when the mint carries none. */
    val permanentDelegate: PermanentDelegate?,
    /** The `pausableConfig` extension, or null when transfers cannot be paused at all. */
    val pausable: PausableConfig?,
    /** The `scaledUiAmountConfig` extension, or null when balances are never rescaled. */
    val scaledUiAmount: ScaledUiAmountConfig?,
    /** The `transferHook` extension, or null when the mint carries none. */
    val transferHook: TransferHookConfig?,
    /** The `defaultAccountState` extension, or null when the mint carries none. */
    val defaultAccountState: DefaultAccountState?,
) {
    /** Supply in whole tokens, before any scaled UI amount multiplier is applied. */
    fun supplyTokens(): BigDecimal = BigDecimal(supplyRaw).movePointLeft(decimals)

    /**
     * Whether a token account created for this mint today starts frozen, so a first-time buyer can
     * be told before the swap. Null when the mint says nothing either way: no `defaultAccountState`
     * extension, or a state word this app does not recognise.
     */
    val freshAccountFrozen: Boolean? get() = defaultAccountState?.frozen

    companion object {
        /** Every xStock mint is owned by this program, verified live on 2026-09-12. */
        const val PROGRAM = KnownPrograms.TOKEN_2022

        /** Every xStock mint carries 8 decimals, verified live on 2026-09-12. */
        const val XSTOCK_DECIMALS = 8

        /** What jsonParsed calls the owning program of a Token-2022 account. */
        const val PARSED_PROGRAM = "spl-token-2022"

        /** What jsonParsed calls a mint account, as opposed to "account" for a token account. */
        const val PARSED_TYPE = "mint"

        const val EXT_PERMANENT_DELEGATE = "permanentDelegate"
        const val EXT_PAUSABLE = "pausableConfig"
        const val EXT_SCALED_UI_AMOUNT = "scaledUiAmountConfig"
        const val EXT_TRANSFER_HOOK = "transferHook"
        const val EXT_DEFAULT_ACCOUNT_STATE = "defaultAccountState"

        /**
         * The facts, or null when [account] is not a Token-2022 mint this app can read: a missing
         * account, the classic token program, a token account rather than a mint, a node that
         * answered base64 instead of jsonParsed, or a mint payload carrying no decimals or no
         * supply. Every one of those is unknown, never "no risk".
         */
        fun from(account: RpcAccount?): MintFacts? {
            if (account == null) return null
            if (account.owner != PROGRAM) return null
            if (account.parsedProgram != PARSED_PROGRAM) return null
            if (account.parsedType != PARSED_TYPE) return null
            val info = account.parsedInfo ?: return null
            val decimals = int(info["decimals"]) ?: return null
            val supply = bigInteger(info["supply"]) ?: return null

            val extensions = extensions(info)
            return MintFacts(
                decimals = decimals,
                supplyRaw = supply,
                mintAuthority = text(info["mintAuthority"]),
                freezeAuthority = text(info["freezeAuthority"]),
                permanentDelegate = extensions[EXT_PERMANENT_DELEGATE]?.let {
                    PermanentDelegate(delegate = text(it["delegate"]))
                },
                pausable = extensions[EXT_PAUSABLE]?.let {
                    PausableConfig(paused = bool(it["paused"]) ?: false, authority = text(it["authority"]))
                },
                scaledUiAmount = extensions[EXT_SCALED_UI_AMOUNT]?.let(::scaledUiAmount),
                transferHook = extensions[EXT_TRANSFER_HOOK]?.let {
                    TransferHookConfig(programId = text(it["programId"]), authority = text(it["authority"]))
                },
                defaultAccountState = extensions[EXT_DEFAULT_ACCOUNT_STATE]?.let { state ->
                    text(state["accountState"])?.let { DefaultAccountState(it) }
                },
            )
        }

        /** `info.extensions` as a map of extension name to its `state` object; empty when absent. */
        private fun extensions(info: JsonObject): Map<String, JsonObject> {
            val list = info["extensions"] as? JsonArray ?: return emptyMap()
            val out = LinkedHashMap<String, JsonObject>(list.size)
            for (element in list) {
                val entry = element as? JsonObject ?: continue
                val name = text(entry["extension"]) ?: continue
                // An extension with no state object still counts as present; it maps to an empty
                // one so a mint that carries it is never read as a mint that does not.
                out[name] = entry["state"] as? JsonObject ?: JsonObject(emptyMap())
            }
            return out
        }

        /**
         * The scaled UI amount, or null when the extension carries no readable multiplier. The node
         * sends the multipliers as quoted decimal strings and the activation as a bare number, so
         * both are read through the primitive's text.
         */
        private fun scaledUiAmount(state: JsonObject): ScaledUiAmountConfig? {
            val multiplier = double(state["multiplier"]) ?: return null
            return ScaledUiAmountConfig(
                multiplier = multiplier,
                newMultiplier = double(state["newMultiplier"]) ?: multiplier,
                newMultiplierEffectiveAtEpochSeconds = long(state["newMultiplierEffectiveTimestamp"]) ?: 0L,
                authority = text(state["authority"]),
            )
        }

        /** The content of a JSON string or number; null for JSON null, an object, or an array. */
        private fun text(element: JsonElement?): String? = (element as? JsonPrimitive)?.contentOrNull

        private fun int(element: JsonElement?): Int? = text(element)?.toIntOrNull()

        private fun long(element: JsonElement?): Long? = text(element)?.toLongOrNull()

        private fun double(element: JsonElement?): Double? =
            text(element)?.toDoubleOrNull()?.takeIf { it.isFinite() }

        private fun bool(element: JsonElement?): Boolean? = (element as? JsonPrimitive)?.booleanOrNull

        private fun bigInteger(element: JsonElement?): BigInteger? =
            text(element)?.let { runCatching { BigInteger(it) }.getOrNull() }
    }
}

/**
 * The `permanentDelegate` extension. Its presence is the fact that matters: the issuer may move
 * tokens out of any account of this mint without the holder signing.
 */
data class PermanentDelegate(
    /** The delegate address, or null when the extension is present with the delegate revoked. */
    val delegate: String?,
) {
    /** True while an address can actually exercise the delegation. */
    val active: Boolean get() = delegate != null
}

/** The `pausableConfig` extension: the issuer can stop every transfer of this mint. */
data class PausableConfig(
    /** Whether transfers are stopped at the slot this was read. */
    val paused: Boolean,
    val authority: String?,
)

/**
 * The `scaledUiAmountConfig` extension: every raw balance is multiplied by [multiplier] to give the
 * share count a holder owns. A split changes it, so the pending pair is carried exactly as the mint
 * sends it and is interpreted by [com.plainticker.mobile.data.SplitMultiplier].
 */
data class ScaledUiAmountConfig(
    val multiplier: Double,
    /** The scheduled next multiplier; equal to [multiplier] when nothing is scheduled. */
    val newMultiplier: Double,
    /** Unix seconds the scheduled change activates; 0 when nothing is scheduled. */
    val newMultiplierEffectiveAtEpochSeconds: Long,
    val authority: String?,
)

/**
 * The `transferHook` extension. Present means the mint is configured to call a program on every
 * transfer, but [programId] may still be JSON null, which means the hook slot is empty and no
 * program runs. Absent (a null [MintFacts.transferHook]) means the mint cannot have one at all.
 */
data class TransferHookConfig(
    val programId: String?,
    val authority: String?,
) {
    /** True only when a program actually runs on every transfer. */
    val runs: Boolean get() = programId != null
}

/**
 * The `defaultAccountState` extension: the state a token account of this mint is created in, which
 * decides whether a first-time buyer's fresh account can receive anything at all.
 */
data class DefaultAccountState(
    /** The word the node sent, kept as it came so an unrecognised state is never read as safe. */
    val state: String,
) {
    /** True, false, or null when the word is one this app does not recognise. */
    val frozen: Boolean?
        get() = when (state.lowercase()) {
            FROZEN -> true
            INITIALIZED, UNINITIALIZED -> false
            else -> null
        }

    companion object {
        const val FROZEN = "frozen"
        const val INITIALIZED = "initialized"
        const val UNINITIALIZED = "uninitialized"
    }
}
