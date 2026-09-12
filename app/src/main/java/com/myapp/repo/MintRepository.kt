package com.myapp.repo

import com.myapp.core.Clock
import com.myapp.data.rpc.MintFacts
import com.myapp.data.rpc.RpcEncoding

/**
 * One read of a Token-2022 mint: what it says, the slot it said it at, and when this app asked.
 *
 * The slot and the wall clock are part of the reading rather than of the screen because the live
 * bar's whole claim is that the number beside it came off the chain a moment ago: "slot N, 2 s
 * ago" (docs/data-map.md, Detail "Live bar meta"). A time taken later, at draw, would be a
 * different claim.
 */
data class MintReading(
    /**
     * The mint's facts, or null when the account could not be read as a Token-2022 mint: it does
     * not exist, it belongs to the classic token program, or its shape did not match. The call
     * succeeded and the answer is still unknown, which is not the same as no risk.
     */
    val facts: MintFacts?,
    /** The slot the node answered at. */
    val slot: Long,
    /** Wall clock at the moment of the read, in epoch millis. */
    val readAtMillis: Long,
) {
    val readable: Boolean get() = facts != null
}

/** The Token-2022 mint behind one xStock, read through the bounded forwarder. */
interface MintRepository {
    /** Throws when the chain could not be reached at all; answers with null facts when it could. */
    suspend fun mint(mint: String): MintReading
}

/**
 * Straight through the forwarder, which already caches 60 s per (method, params) server-side, so
 * this adds no cache of its own: a second open of the same Detail screen costs an edge hit, and
 * the slot it reports stays the slot the node actually answered at.
 */
class ForwarderMintRepository(
    private val rpc: RpcRepository,
    private val clock: Clock,
) : MintRepository {

    override suspend fun mint(mint: String): MintReading {
        val answer = rpc.accountAt(mint, RpcEncoding.JSON_PARSED)
        return MintReading(
            facts = MintFacts.from(answer.value),
            slot = answer.context?.slot ?: 0L,
            readAtMillis = clock.nowMillis(),
        )
    }
}
