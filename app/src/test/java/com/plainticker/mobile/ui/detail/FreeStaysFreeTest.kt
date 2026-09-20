package com.plainticker.mobile.ui.detail

import app.cash.turbine.test
import com.plainticker.mobile.MainDispatcherRule
import com.plainticker.mobile.awaitUntil
import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.data.MockApi
import com.plainticker.mobile.data.plainticker.AnalysisPayload
import com.plainticker.mobile.data.plainticker.Axes
import com.plainticker.mobile.data.plainticker.Axis
import com.plainticker.mobile.data.plainticker.FScore
import com.plainticker.mobile.data.plainticker.ReadApi
import com.plainticker.mobile.data.plainticker.Tone
import com.plainticker.mobile.data.plainticker.Verdict
import com.plainticker.mobile.data.rpc.PausableConfig
import com.plainticker.mobile.data.rpc.PermanentDelegate
import com.plainticker.mobile.data.rpc.TransferHookConfig
import com.plainticker.mobile.data.respondHtml
import com.plainticker.mobile.data.respondJson
import com.plainticker.mobile.prefs.InMemoryDevicePassStore
import com.plainticker.mobile.prefs.InMemoryNotificationPromptStore
import com.plainticker.mobile.prefs.InMemoryWatchlistStore
import com.plainticker.mobile.repo.FakeCatalogRepository
import com.plainticker.mobile.repo.FakeMintRepository
import com.plainticker.mobile.repo.FakeNextUpRepository
import com.plainticker.mobile.repo.FakePriceRepository
import com.plainticker.mobile.repo.FakeSummaryRepository
import com.plainticker.mobile.repo.MintReading
import com.plainticker.mobile.repo.mintFacts
import com.plainticker.mobile.repo.price
import com.plainticker.mobile.repo.proofOfReserves
import com.plainticker.mobile.repo.xStock
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * The hard invariant task A6 states in one sentence: nothing that is free today may become
 * locked. Every block this screen drew before task A6 has to keep drawing exactly as it did,
 * whichever of the six ways "The read" and "What to check next" can resolve.
 *
 * If this repository could keep only one test from task A6, the plan names this one: not a test
 * of what the new blocks say (that is [DetailReadModelTest]'s job), but proof that their own
 * six failure and success modes never reach back into the six sources this screen already
 * carried. The technique is structural rather than field-by-field: [DetailUiState] is a data
 * class, so `state.copy(read = ReadState.Loading)` erases only the one field task A6 added, and
 * comparing that snapshot across all six scenarios with plain `equals` catches a regression in
 * any field this file does not even know to name yet, not only the ones enumerated below.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FreeStaysFreeTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val mint = "AAPLxMint".padEnd(44, '1')
    private val now = Instant.parse("2026-09-16T16:00:00Z").toEpochMilli()
    private val clock = Clock { now }

    private fun analysis() = AnalysisPayload(
        ticker = "AAPL",
        company = "Apple Inc.",
        sector = "Technology",
        asOf = "2026-09-10T13:25:30.278Z",
        axes = Axes(
            quality = Axis(value = 8.0, scale = "0-9", position = 0.889, labelEn = "Strong"),
            valuation = Axis(value = 51.0, scale = "0-100", position = 0.51, labelEn = "Moderate"),
            momentum = Axis(value = 0.79, scale = "0-1", position = 0.79, labelEn = "Near 52-week high"),
        ),
        fscore = FScore(score = 8, scale = "0-9", signals = listOf(true, true, true, false, true, true, true, true, true)),
        compositePercentile = 71.0,
    )

    private fun summaries() = FakeSummaryRepository(analyses = mapOf("AAPL" to Result.success(analysis())))

    private fun catalog() = FakeCatalogRepository(
        assets = Result.success(listOf(xStock("AAPLx", "AAPL", mint, "Apple Inc."))),
        reserves = mapOf("AAPLx" to Result.success(proofOfReserves("AAPLx", sharesHeld = "26101", circulatingSupply = "25924"))),
    )

    private fun readableMint() = FakeMintRepository(
        Result.success(
            MintReading(
                facts = mintFacts(
                    permanentDelegate = PermanentDelegate("5aMNNLQJwAEeoemTEMkv5NVjqKwvvefRYCQ5Z67HFvEq"),
                    pausable = PausableConfig(paused = false, authority = null),
                    scaledUiAmount = null,
                    transferHook = TransferHookConfig(programId = null, authority = null),
                    defaultAccountState = null,
                ),
                slot = 446_503_662L,
                readAtMillis = now - 2_000L,
            ),
        ),
    )

    private fun prices() = FakePriceRepository(Result.success(mapOf(mint to price(232.5, reference = 232.4))))

    private fun viewModel(readApi: ReadApi?, summaryRepo: FakeSummaryRepository = summaries()) = DetailViewModel(
        "AAPL",
        summaryRepo,
        catalog(),
        prices(),
        readableMint(),
        FakeNextUpRepository(),
        InMemoryWatchlistStore(),
        InMemoryNotificationPromptStore(),
        clock,
        readApi,
        InMemoryDevicePassStore(),
    )

    private val peekBody = """
        {"ticker":"AAPL","pro":false,
         "narrative":{"excerptEn":"First sentence.","fullEn":null},
         "nextSteps":{"titlesEn":["Check margins"],"stepsEn":null}}
    """.trimIndent()

    private val fullBody = """
        {"ticker":"AAPL","pro":true,
         "narrative":{"excerptEn":"First sentence.","fullEn":"First sentence. Second one."},
         "nextSteps":{"titlesEn":["Check margins"],"stepsEn":[{"title":"Check margins","body":"Margins rose again."}]}}
    """.trimIndent()

    /** One way task A6's call can resolve, and what [DetailUiState.read] must settle to for it. */
    private data class Scenario(val label: String, val readApi: ReadApi?, val resolves: (ReadState) -> Boolean)

    private fun scenarios(): List<Scenario> = listOf(
        Scenario("no read api wired at all", null) { it is ReadState.Loading },
        Scenario(
            "503 monetization_disabled",
            ReadApi(
                MockApi {
                    respondJson(
                        """{"error":"This endpoint is not enabled on this server.","code":"monetization_disabled"}""",
                        HttpStatusCode.ServiceUnavailable,
                    )
                }.client,
            ),
        ) { it is ReadState.Disabled },
        Scenario(
            "422 unsupported_ticker",
            ReadApi(MockApi { respondJson("""{"error":"x","code":"unsupported_ticker"}""", HttpStatusCode.UnprocessableEntity) }.client),
        ) { it is ReadState.NotServed },
        Scenario(
            "a 500 this app cannot read",
            ReadApi(MockApi { respondHtml("<html>down</html>", HttpStatusCode.InternalServerError) }.client),
        ) { it is ReadState.Failed },
        Scenario("the peek, no code presented", ReadApi(MockApi { respondJson(peekBody) }.client)) { it is ReadState.Ready },
        Scenario("the full text, an entitled code", ReadApi(MockApi { respondJson(fullBody) }.client)) { it is ReadState.Ready },
    )

    @Test
    fun `every free block is unchanged whichever of the six ways the read call resolves`() = runTest {
        var reference: DetailUiState? = null

        scenarios().forEach { scenario ->
            val vm = viewModel(scenario.readApi)
            vm.state.test {
                val settled = awaitUntil { !it.isLoading && scenario.resolves(it.read) }
                assertTrue(
                    "${scenario.label}: DetailUiState.read must never gate isLoading",
                    !settled.isLoading,
                )

                // The one field task A6 added, erased, so the comparison below is exactly the six
                // sources this screen carried before task A6 existed, and every flat field derived
                // from them (heroTicker, priceRow, trustFacts, tracks, fScore, method, and so on).
                val snapshot = settled.copy(read = ReadState.Loading)
                val expected = reference
                if (expected == null) {
                    reference = snapshot
                } else {
                    assertEquals(
                        "${scenario.label}: a block that used to be free moved because of the read call",
                        expected,
                        snapshot,
                    )
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

        // Not a vacuous pass: the fixture actually exercises the six sources this invariant is
        // about, every one of them present, before the six-way comparison above ever ran.
        val proof = checkNotNull(reference)
        assertEquals("AAPL", proof.ticker)
        assertEquals("AAPLx", proof.symbol)
        assertTrue(proof.analysisState is AnalysisState.Served)
        assertEquals(8.0, proof.analysis!!.axes.quality!!.value!!, 0.0)
        assertNotNull("the chain read must stand regardless of the read call", proof.chain.valueOrNull)
        assertTrue(proof.reserves is Piece.Ready)
        assertNotNull(proof.price)
        assertEquals(71.0, proof.analysis!!.compositePercentile!!, 0.0)
    }

    /**
     * The verdict is new, so nothing that was free before it existed loses anything just by
     * carrying one: the invariant is exactly the one this file already proves for the read call,
     * applied to the field task app-verdict added. [Verdict] lives inside [AnalysisPayload] rather
     * than behind its own call, so the erasure targets that one field inside the served payload
     * instead of the whole [DetailUiState.read], the same structural technique this file's own
     * doc comment names: erase only the field under test, compare the rest with plain `equals`.
     */
    @Test
    fun `the verdict never moves a field this screen carried before it existed, absent, unlocked or locked`() = runTest {
        var reference: DetailUiState? = null

        val shapes: List<Pair<String, Verdict?>> = listOf(
            "no verdict at all, a server that predates task app-verdict" to null,
            "unlocked, an entitled caller or monetization off" to
                Verdict(code = "quality_compounder", labelUk = "Компаундер якості", labelEn = "Quality compounder", tone = Tone.POSITIVE),
            "locked, monetization on and no code presented" to Verdict(locked = true),
        )

        shapes.forEach { (label, verdict) ->
            val payload = analysis().copy(verdict = verdict)
            val repo = FakeSummaryRepository(analyses = mapOf("AAPL" to Result.success(payload)))
            val vm = viewModel(readApi = null, summaryRepo = repo)

            vm.state.test {
                val settled = awaitUntil { !it.isLoading }
                val served = settled.analysisState as AnalysisState.Served

                // The one field task app-verdict added, erased, so the comparison below is
                // exactly the fields this screen carried before the verdict existed.
                val snapshot = settled.copy(analysisState = served.copy(payload = served.payload.copy(verdict = null)))
                val expected = reference
                if (expected == null) {
                    reference = snapshot
                } else {
                    assertEquals("$label: a field that used to be free moved because of the verdict", expected, snapshot)
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

        // Not a vacuous pass: the fixture actually carries an analysis, so the comparison above
        // ran over the six sources this invariant is about, not over three empty states.
        val proof = checkNotNull(reference)
        assertEquals("AAPL", proof.ticker)
        assertTrue(proof.analysisState is AnalysisState.Served)
        assertEquals(8.0, proof.analysis!!.axes.quality!!.value!!, 0.0)
    }
}
