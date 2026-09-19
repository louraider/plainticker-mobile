package com.plainticker.mobile.repo

import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.data.MockApi
import com.plainticker.mobile.data.expectThrows
import com.plainticker.mobile.data.net.ApiException
import com.plainticker.mobile.data.plainticker.NextUpApi
import com.plainticker.mobile.data.respondHtml
import com.plainticker.mobile.data.respondJson
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one rule of the cache: for the edge's five minutes the List and a Detail opened from it
 * cost one request between them, and an answer already held outlives a request that failed.
 */
class CachedNextUpRepositoryTest {

    private val body = """{"schema":"v1.1","generated_at":"2026-09-18T12:34:56.000Z","rows":[""" +
        """{"ticker":"NFLX","weight":"123456000000","voters":3}]}"""

    private var now = 1_789_394_400_000L
    private val clock = Clock { now }

    /** A server that answers [body] until [status] is changed, counting what it was asked. */
    private inner class Server {
        var status = HttpStatusCode.OK
        var calls = 0
        val mock = MockApi {
            calls++
            respondJson(if (status == HttpStatusCode.OK) body else """{"error":"internal","code":500}""", status)
        }
        val repository = CachedNextUpRepository(NextUpApi(mock.client), clock)
    }

    @Test
    fun `two asks inside the edge window cost one request`() = runTest {
        val server = Server()
        assertEquals(listOf("NFLX"), server.repository.nextUp().map { it.ticker })
        now += CachedNextUpRepository.TTL_MS - 1L
        assertEquals(listOf("NFLX"), server.repository.nextUp().map { it.ticker })
        assertEquals(1, server.calls)
    }

    @Test
    fun `an ask after the window fetches again`() = runTest {
        val server = Server()
        server.repository.nextUp()
        now += CachedNextUpRepository.TTL_MS
        server.repository.nextUp()
        assertEquals(2, server.calls)
    }

    @Test
    fun `a failed fetch hands back the answer already held rather than nothing`() = runTest {
        val server = Server()
        server.repository.nextUp()
        now += CachedNextUpRepository.TTL_MS
        server.status = HttpStatusCode.InternalServerError
        assertEquals("the leaders move slowly, and a held answer beats a blank strip", listOf("NFLX"), server.repository.nextUp().map { it.ticker })
        assertEquals(2, server.calls)
    }

    @Test
    fun `a failed fetch with nothing held throws, so the caller draws nothing`() = runTest {
        val server = Server()
        server.status = HttpStatusCode.InternalServerError
        expectThrows<ApiException> { server.repository.nextUp() }
        assertEquals(1, server.calls)
    }

    // ---- current(): the whole answer the Vote tab needs (task A2) ----------------------------

    @Test
    fun `current caches the round and the previous round beside the rows`() = runTest {
        val withRounds = """{"schema":"v1.1","generated_at":"2026-09-18T12:34:56.000Z","rows":[""" +
            """{"ticker":"NFLX","weight":"123456000000","voters":3}],""" +
            """"round":{"id":2,"opens_at":"2026-09-22T00:00:00.000Z","closes_at":"2026-09-29T00:00:00.000Z"}}"""
        val mock = MockApi { respondJson(withRounds) }
        val repository = CachedNextUpRepository(NextUpApi(mock.client), clock)

        val answer = repository.current() as NextUpAnswer.Open
        assertEquals(2, answer.round?.id)
        assertEquals(listOf("NFLX"), answer.rows.map { it.ticker })
        // The same cached fetch, not a second request: nextUp() and current() never disagree.
        assertEquals(listOf("NFLX"), repository.nextUp().map { it.ticker })
        assertEquals(1, mock.requests.size)
    }

    @Test
    fun `a 404 caches as NotOpen, not as a failure`() = runTest {
        val mock = MockApi { respondHtml("<html>404</html>", HttpStatusCode.NotFound) }
        val repository = CachedNextUpRepository(NextUpApi(mock.client), clock)

        assertEquals(NextUpAnswer.NotOpen, repository.current())
        assertTrue("a quiet source, exactly like a real failure would leave it", repository.nextUp().isEmpty())

        // Cached for the TTL: a second ask inside it costs nothing more.
        now += CachedNextUpRepository.TTL_MS - 1L
        assertEquals(NextUpAnswer.NotOpen, repository.current())
        assertEquals(1, mock.requests.size)
    }

    @Test
    fun `a 503 vote_not_configured caches as NotOpen the same way`() = runTest {
        val mock = MockApi { respondJson("""{"error":"vote_not_configured","code":503}""", HttpStatusCode.ServiceUnavailable) }
        val repository = CachedNextUpRepository(NextUpApi(mock.client), clock)
        assertEquals(NextUpAnswer.NotOpen, repository.current())
    }

    @Test
    fun `not-open does not survive a later fetch that finds the route open`() = runTest {
        val server = object {
            var notOpen = true
            var calls = 0
            val mock = MockApi {
                calls++
                if (notOpen) respondHtml("<html>404</html>", HttpStatusCode.NotFound) else respondJson(body)
            }
        }
        val repository = CachedNextUpRepository(NextUpApi(server.mock.client), clock)
        assertEquals(NextUpAnswer.NotOpen, repository.current())

        now += CachedNextUpRepository.TTL_MS
        server.notOpen = false
        val opened = repository.current() as NextUpAnswer.Open
        assertEquals(listOf("NFLX"), opened.rows.map { it.ticker })
    }
}
