package com.myapp.data.net

import com.myapp.data.MockApi
import com.myapp.data.respondJson
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HttpClientFactoryTest {

    @Serializable
    private data class Probe(val a: Int = -1, val b: String? = "default", val c: Boolean = true)

    @Test
    fun `default user agent is sent exactly once`() = runTest {
        val api = MockApi { respondJson("{}") }
        api.client.get("https://example.test/x")
        assertEquals(listOf(HttpClientFactory.USER_AGENT), api.lastRequest.headers.getAll(HttpHeaders.UserAgent))
    }

    @Test
    fun `a request-level user agent replaces the default instead of joining it`() = runTest {
        val api = MockApi { respondJson("{}") }
        api.client.get("https://example.test/x") { header(HttpHeaders.UserAgent, "Something/1.0") }
        assertEquals(listOf("Something/1.0"), api.lastRequest.headers.getAll(HttpHeaders.UserAgent))
    }

    @Test
    fun `json ignores unknown keys, coerces null to defaults and treats absent as default`() {
        val probe = HttpClientFactory.json.decodeFromString(
            Probe.serializer(),
            """{"a": 7, "c": null, "neverSeen": {"deep": [1, 2, 3]}}""",
        )
        assertEquals(7, probe.a)
        assertEquals("default", probe.b)
        assertEquals(true, probe.c)
    }

    @Test
    fun `json does not write nulls back out`() {
        // encodeDefaults stays false, so the untouched default c is omitted too.
        val encoded = HttpClientFactory.json.encodeToString(Probe.serializer(), Probe(a = 1, b = null, c = false))
        assertEquals("""{"a":1,"c":false}""", encoded)
    }

    @Test
    fun `lenient long accepts numbers and numeric strings`() {
        @Serializable
        data class Holder(@Serializable(with = LenientLongSerializer::class) val v: Long? = null)
        val json = HttpClientFactory.json
        assertEquals(42L, json.decodeFromString(Holder.serializer(), """{"v": 42}""").v)
        assertEquals(42L, json.decodeFromString(Holder.serializer(), """{"v": "42"}""").v)
        assertNull(json.decodeFromString(Holder.serializer(), """{"v": null}""").v)
        assertNull(json.decodeFromString(Holder.serializer(), """{}""").v)
    }

    @Test
    fun `lenient double accepts numbers and numeric strings`() {
        @Serializable
        data class Holder(@Serializable(with = LenientDoubleSerializer::class) val v: Double = 0.0)
        val json = HttpClientFactory.json
        assertEquals(0.5, json.decodeFromString(Holder.serializer(), """{"v": 0.5}""").v, 0.0)
        assertEquals(0.5, json.decodeFromString(Holder.serializer(), """{"v": "0.5"}""").v, 0.0)
        assertEquals(0.0, json.decodeFromString(Holder.serializer(), """{"v": null}""").v, 0.0)
    }
}
