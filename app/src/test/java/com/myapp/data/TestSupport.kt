package com.myapp.data

import com.myapp.data.net.HttpClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf

/** Recorded API answers under src/test/resources. */
object Fixtures {
    fun read(path: String): String =
        checkNotNull(Fixtures::class.java.classLoader?.getResourceAsStream(path)) { "missing fixture: $path" }
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
}

/** A real [HttpClientFactory] client over a [MockEngine], with the request log exposed. */
class MockApi(handler: MockRequestHandler) {
    val engine: MockEngine = MockEngine(handler)
    val client: HttpClient = HttpClientFactory.create(engine)
    val requests: List<HttpRequestData> get() = engine.requestHistory
    val lastRequest: HttpRequestData get() = engine.requestHistory.last()
}

fun MockRequestHandleScope.respondJson(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
    vararg extraHeaders: Pair<String, String>,
): HttpResponseData = respond(
    content = body,
    status = status,
    headers = Headers.build {
        append(HttpHeaders.ContentType, "application/json")
        extraHeaders.forEach { (name, value) -> append(name, value) }
    },
)

fun MockRequestHandleScope.respondHtml(body: String, status: HttpStatusCode): HttpResponseData =
    respond(body, status, headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"))

suspend fun HttpRequestData.bodyText(): String = when (val content = body) {
    is TextContent -> content.text
    is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
    else -> ""
}

/**
 * `assertThrows` for suspend code. Inline, so the block may suspend inside a `runTest`
 * body without nesting a second `runTest`.
 */
inline fun <reified T : Throwable> expectThrows(block: () -> Unit): T {
    try {
        block()
    } catch (e: Throwable) {
        if (e is T) return e
        throw AssertionError("expected ${T::class.simpleName} but got ${e::class.simpleName}: ${e.message}", e)
    }
    throw AssertionError("expected ${T::class.simpleName} but nothing was thrown")
}
