package com.plainticker.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Mock judges' review 2026-09-27: explorer links, and nothing but a signature or an address in them. */
class ExplorerTest {

    private val signature = "4xQm7gW2kP9vB1cD3eF5hJ6kL7mN8pQ9rS1tU2vW3xY4zA5bC6dE7fG8hJ9kL1mN2pQ3rS4tU5vW6xY7z5oVtHe"
    private val address = "E1STBTGEYpHanVG4HWUJHfzEu6eGbnE9mnGX9KnAmJdL"

    @Test
    fun `a signature opens its transaction page on Solscan`() {
        assertEquals("https://solscan.io/tx/$signature", Explorer.transaction(signature))
        assertEquals("surrounding space is not part of it", "https://solscan.io/tx/$signature", Explorer.transaction(" $signature "))
    }

    @Test
    fun `an address opens its account page`() {
        assertEquals("https://solscan.io/account/$address", Explorer.account(address))
    }

    @Test
    fun `anything that is not plain base58 of the right length makes no link`() {
        assertNull(Explorer.transaction(""))
        assertNull("an address is not a signature", Explorer.transaction(address))
        assertNull("0, O, I and l are not base58", Explorer.transaction(signature.replaceRange(0, 1, "0")))
        assertNull("no path or query can ride along", Explorer.transaction("$signature/../x"))
        assertNull(Explorer.transaction("$signature?code=ABCDEFGHJK"))
        // A device code (a bearer credential, 26 symbols) is too short to pass as either.
        assertNull(Explorer.transaction("ABCDEFGHJKMNPQRSTVWXYZ2345"))
        assertNull(Explorer.account("ABCDEFGHJKMNPQRSTVWXYZ2345"))
    }
}
