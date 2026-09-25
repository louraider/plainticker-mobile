package com.plainticker.mobile.auth

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInNonceTest {

    private fun jwt(payload: String): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        return enc.encodeToString("""{"alg":"RS256"}""".toByteArray()) + "." +
            enc.encodeToString(payload.toByteArray()) + ".sig"
    }

    @Test
    fun `a nonce is 256 random bits in base64url, fresh every time`() {
        val a = SignInNonce.create()
        val b = SignInNonce.create()
        assertNotEquals(a, b)
        assertEquals(32, Base64.getUrlDecoder().decode(a).size)
        assertTrue("url-safe, no padding", a.matches(Regex("[A-Za-z0-9_-]+")))
    }

    @Test
    fun `the claim is read from the payload, and matches only exactly`() {
        val token = jwt("""{"sub":"1","nonce":"abc_DEF-123"}""")
        assertEquals("abc_DEF-123", SignInNonce.claimOf(token))
        assertTrue(SignInNonce.matches(token, "abc_DEF-123"))
        assertFalse(SignInNonce.matches(token, "abc_DEF-12"))
    }

    @Test
    fun `a token without a nonce, or not a JWT at all, never matches`() {
        assertNull(SignInNonce.claimOf(jwt("""{"sub":"1"}""")))
        assertNull(SignInNonce.claimOf("not-a-jwt"))
        assertNull(SignInNonce.claimOf("a.%%%.c"))
        assertNull(SignInNonce.claimOf(jwt("[1,2,3]")))
        assertFalse(SignInNonce.matches("not-a-jwt", "x"))
    }
}
